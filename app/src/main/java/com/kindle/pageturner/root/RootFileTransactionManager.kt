package com.kindle.pageturner.root

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed class ModificationOutcome {
    data class Committed(
        val transaction: RootTransactionRecord,
        val backup: RootBackupRecord,
        val afterMetadata: RootFileMetadata
    ) : ModificationOutcome()

    data class RolledBack(
        val transaction: RootTransactionRecord,
        val backup: RootBackupRecord,
        val reason: String
    ) : ModificationOutcome()

    data class RecoveryRequired(
        val transaction: RootTransactionRecord,
        val backup: RootBackupRecord?,
        val reason: String
    ) : ModificationOutcome()

    data class RejectedPrecheck(
        val reason: String
    ) : ModificationOutcome()
}

sealed class RestoreOutcome {
    data class VerifiedSuccess(
        val transaction: RootTransactionRecord,
        val restoredBackup: RootBackupRecord,
        val preRestoreSnapshot: RootBackupRecord?,
        val afterMetadata: RootFileMetadata
    ) : RestoreOutcome()

    data class PartialRestoreMetadataWarning(
        val transaction: RootTransactionRecord,
        val restoredBackup: RootBackupRecord,
        val preRestoreSnapshot: RootBackupRecord?,
        val afterMetadata: RootFileMetadata,
        val metadataWarning: String
    ) : RestoreOutcome()

    data class RestoredWithLogWriteFailure(
        val transaction: RootTransactionRecord,
        val restoredBackup: RootBackupRecord,
        val preRestoreSnapshot: RootBackupRecord?,
        val afterMetadata: RootFileMetadata,
        val logWriteError: String
    ) : RestoreOutcome()

    data class ConflictDetected(
        val targetPath: String,
        val backup: RootBackupRecord,
        val currentMetadata: RootFileMetadata,
        val lastKnownExpectedSha256: String?,
        val conflictExplanation: String
    ) : RestoreOutcome()

    data class RejectedPrecheck(
        val reason: String
    ) : RestoreOutcome()

    data class FailedRecoveryRequired(
        val transaction: RootTransactionRecord?,
        val restoredBackup: RootBackupRecord,
        val preRestoreSnapshot: RootBackupRecord?,
        val reason: String
    ) : RestoreOutcome()
}

/**
 * Unified, transactional Root system file modification and restoration manager.
 *
 * Guarantees:
 * - No business module can modify managed system files without going through this manager.
 * - Mandatory verified pre-modification backup before any write.
 * - Persistent phase-by-phase transaction logging synced to disk before each transition.
 * - Staging file write + hash verification + atomic move (`mv -f`) + post-write verification.
 * - Automatic rollback on failure and full conflict-aware restoration.
 */
class RootFileTransactionManager(
    storageRootDir: File,
    private val executor: RootCommandExecutor,
    private val inspector: RootFileInspector,
    private val backupManager: RootBackupManager,
    private val auditLogger: RootAuditLogger
) {
    val transactionsDir = File(storageRootDir, "root_transactions")
    private val activeOperationGuard = AtomicBoolean(false)
    private val txSeqCounter = AtomicLong(0L)

    @Volatile
    var activeTransactionId: String? = null
        private set

    fun isOperationInProgress(): Boolean = activeOperationGuard.get()

    /**
     * Optional hook invoked immediately after a transaction phase is persisted to disk.
     * Used by UI for live stage display and by fault-injection tests to simulate process kills/crashes
     * at any exact transaction phase.
     */
    var onPhasePersistedHook: ((RootTransactionRecord) -> Unit)? = null

    /**
     * Optional fault-injection flag to simulate transaction/audit log write failure on final commit.
     */
    var simulateFinalLogWriteFailure: Boolean = false

    init {
        transactionsDir.mkdirs()
    }

    /**
     * Executes a managed modification of [targetPath] with [newContentBytes].
     */
    fun executeModification(
        targetPath: String,
        newContentBytes: ByteArray,
        userConfirmed: Boolean,
        expectedBeforeSha256: String? = null,
        expectedBeforeExists: Boolean? = null,
        @Suppress("UNUSED_PARAMETER") allowCreateWhenTemplateMissing: Boolean = false,
        allowCreateFromVerifiedTemplate: Boolean = false,
        plannedActionSummary: String = "更新 bt_did.conf 为 Kindle Remote DID (0x0171:0x0443)"
    ): ModificationOutcome {
        if (!activeOperationGuard.compareAndSet(false, true)) {
            val msg = "检测到并发修改请求：当前已有另一个 Root 文件事务正在执行，请等待完成"
            auditLogger.record("TX_MODIFY", "FAILURE", msg, targetPath)
            return ModificationOutcome.RejectedPrecheck(msg)
        }

        try {
            // 1. User confirmation check
            if (!userConfirmed) {
                return rejectModify(targetPath, "操作未获得用户明确确认，已取消执行")
            }

            // 2. Path whitelist & writable check
            val managedTarget = RootPathValidator.validatePath(targetPath, requireWritable = true)
                .getOrElse { return rejectModify(targetPath, it.message ?: "目标路径校验失败") }

            // 3. Unfinished transaction check
            val unfinished = getUnfinishedTransactions(targetPath)
            if (unfinished.isNotEmpty()) {
                val txIds = unfinished.joinToString { "${it.transactionId}(${it.phase.name})" }
                return rejectModify(
                    targetPath,
                    "存在未完成或待恢复的事务 [$txIds]，禁止在不确定状态下再次写入。请先执行恢复检查或手动恢复。"
                )
            }

            // 4. Root availability check
            val suRes = executor.execute(RootShellCommand.CheckSuId)
            if (!suRes.isSuccess || !suRes.stdout.contains("uid=0")) {
                return rejectModify(
                    targetPath,
                    "Root 权限不可用或未获授权 (输出: ${suRes.stdout.ifBlank { suRes.stderr }})"
                )
            }

            // 5. Inspect current target file
            val currentMeta = inspector.inspectFile(targetPath)
                .getOrElse { return rejectModify(targetPath, "无法检查目标文件当前状态: ${it.message}") }

            if (currentMeta.fileType != FileType.REGULAR_FILE && currentMeta.fileType != FileType.NOT_EXIST) {
                return rejectModify(
                    targetPath,
                    "目标文件类型不受支持 (${currentMeta.fileType.displayName})，严禁将其当作普通文件覆盖"
                )
            }

            // 6. Optimistic concurrency check: verify target file did not change since inspection
            if (expectedBeforeExists != null && currentMeta.exists != expectedBeforeExists) {
                return rejectModify(
                    targetPath,
                    "目标文件存在状态在操作前已发生变化 (预期存在=$expectedBeforeExists, 实际存在=${currentMeta.exists})"
                )
            }
            if (expectedBeforeSha256 != null && currentMeta.sha256 != expectedBeforeSha256) {
                return rejectModify(
                    targetPath,
                    "目标文件内容在操作前已被外部修改 (预期哈希=${expectedBeforeSha256.take(16)}..., 实际哈希=${currentMeta.sha256?.take(16) ?: "NONE"}...)"
                )
            }

            // 7. Special bt_did.conf protection check:
            //    - If file exists, verify existing format is valid INI.
            //    - Never allow creating an unverified bt_did.conf when neither /data nor /system bt_did.conf exists
            //      (even if allowCreateWhenTemplateMissing was requested).
            //    - Even if /system/etc/bluetooth/bt_did.conf exists, a non-existent /data/misc/bluetooth/bt_did.conf
            //      is NOT automatically included in the modifiable file list; creating it is rejected by default unless
            //      allowCreateFromVerifiedTemplate is explicitly enabled after device override verification.
            if (currentMeta.exists) {
                val existingBytes = inspector.readFileBytes(targetPath)
                    .getOrElse { return rejectModify(targetPath, "无法读取现有目标文件内容以验证格式: ${it.message}") }
                when (val fmtCheck = BtDidConfValidator.validateExistingBytes(existingBytes)) {
                    is BtDidConfValidator.FormatValidationResult.Invalid -> {
                        return rejectModify(
                            targetPath,
                            "现有文件格式未知或损坏 (行 ${fmtCheck.lineNumber}: ${fmtCheck.reason})，禁止直接覆盖"
                        )
                    }
                    BtDidConfValidator.FormatValidationResult.Valid -> {}
                }
            } else {
                val sysTemplatePath = ManagedTargetPath.SYSTEM_BT_DID_CONF.canonicalPath
                val sysTemplateMeta = inspector.inspectFile(sysTemplatePath).getOrNull()
                if (sysTemplateMeta == null || !sysTemplateMeta.exists || sysTemplateMeta.fileType != FileType.REGULAR_FILE) {
                    return rejectModify(
                        targetPath,
                        "目标文件 $targetPath 与系统模板 $sysTemplatePath 均不存在；当前系统不支持或待验证，禁止自行创建未经证实的系统配置"
                    )
                }
                val sysTemplateBytes = inspector.readFileBytes(sysTemplatePath)
                    .getOrElse { return rejectModify(targetPath, "无法读取系统模板文件内容以验证格式: ${it.message}") }
                when (val sysFmtCheck = BtDidConfValidator.validateExistingBytes(sysTemplateBytes)) {
                    is BtDidConfValidator.FormatValidationResult.Invalid -> {
                        return rejectModify(
                            targetPath,
                            "系统模板文件 $sysTemplatePath 格式损坏或无法识别 (行 ${sysFmtCheck.lineNumber}: ${sysFmtCheck.reason})，禁止基于损坏模板创建配置"
                        )
                    }
                    BtDidConfValidator.FormatValidationResult.Valid -> {}
                }
                if (!allowCreateFromVerifiedTemplate) {
                    return rejectModify(
                        targetPath,
                        "目标文件 $targetPath 当前不存在（未自动纳入可修改文件列表）；仅检测到系统模板 $sysTemplatePath，覆盖机制与设备兼容性待验证，禁止自动新建配置文件"
                    )
                }
            }

            // 8. Validate new content format
            when (val newFmt = BtDidConfValidator.validateExistingBytes(newContentBytes)) {
                is BtDidConfValidator.FormatValidationResult.Invalid -> {
                    return rejectModify(
                        targetPath,
                        "待写入的新配置内容格式不合法 (行 ${newFmt.lineNumber}: ${newFmt.reason})"
                    )
                }
                BtDidConfValidator.FormatValidationResult.Valid -> {}
            }

            // 9. Check target directory writability
            val dirStatus = inspector.checkDirectoryWritable(managedTarget.parentDir)
            if (dirStatus != DirectoryWriteStatus.WRITABLE) {
                return rejectModify(
                    targetPath,
                    "目标目录 '${managedTarget.parentDir}' 不可写 (状态: $dirStatus)，拒绝执行修改"
                )
            }

            // 10. Persist PREPARED transaction journal first, then create & verify mandatory pre-modification backup
            val txId = generateTransactionId()
            activeTransactionId = txId
            val opId = "op_mod_${System.currentTimeMillis()}"
            val expectedAfterSha256 = Sha256Utils.sha256Hex(newContentBytes)
            val preAllocatedBackupId = backupManager.allocateBackupId()

            // Phase: PREPARED
            var txRecord = RootTransactionRecord(
                transactionId = txId,
                operationId = opId,
                operationType = TransactionOperationType.MODIFY_FILE,
                targetPath = targetPath,
                backupId = preAllocatedBackupId,
                phase = TransactionPhase.PREPARED,
                startedAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
                plannedActionSummary = plannedActionSummary,
                preModifyMetadata = currentMeta,
                expectedExistAfter = true,
                expectedAfterSha256 = expectedAfterSha256
            ).withComputedChecksum()
            persistTransactionAndNotify(txRecord)

            val backupRecord = backupManager.createAndVerifyBackup(
                targetPath = targetPath,
                transactionId = txId,
                operationId = opId,
                isPreRestoreSnapshot = false,
                note = "Pre-modification mandatory backup for $txId",
                customBackupId = preAllocatedBackupId
            ).getOrElse {
                val abortReason = "修改前强制备份创建或校验失败，已中止修改: ${it.message}"
                val abortedTx = txRecord.copy(
                    phase = TransactionPhase.ROLLED_BACK,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    completedAtEpochMs = System.currentTimeMillis(),
                    errorMessage = abortReason,
                    recoveryResolution = "ABORTED_BEFORE_BACKUP_COMPLETED"
                ).withComputedChecksum()
                try {
                    persistTransactionToDisk(abortedTx)
                } catch (_: Throwable) {}
                return rejectModify(targetPath, abortReason)
            }

            // TOCTOU check: verify target file did not change between initial inspection and backup creation
            if (backupRecord.originalMetadata.exists != currentMeta.exists ||
                backupRecord.originalMetadata.sha256 != currentMeta.sha256
            ) {
                val toctouMsg = "目标文件在预检与备份期间被外部并发修改 (TOCTOU 冲突)，已中止本次写入"
                val abortedTx = txRecord.copy(
                    phase = TransactionPhase.ROLLED_BACK,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    completedAtEpochMs = System.currentTimeMillis(),
                    errorMessage = toctouMsg,
                    recoveryResolution = "ABORTED_TOCTOU_BEFORE_WRITE"
                ).withComputedChecksum()
                try {
                    persistTransactionToDisk(abortedTx)
                } catch (_: Throwable) {}
                return rejectModify(targetPath, toctouMsg)
            }

            // Phase: BACKUP_VERIFIED (double-check backup record on disk before touching target)
            val reVerifyBackup = backupManager.verifyBackup(backupRecord.backupId)
            if (reVerifyBackup !is BackupVerificationResult.Valid) {
                val reason = (reVerifyBackup as? BackupVerificationResult.Invalid)?.reason ?: "备份复核失败"
                txRecord = updatePhase(txRecord, TransactionPhase.ROLLED_BACK, errorMessage = "修改前备份复核未通过: $reason", completed = true)
                return ModificationOutcome.RolledBack(txRecord, backupRecord, "备份复核未通过: $reason")
            }
            txRecord = updatePhase(txRecord, TransactionPhase.BACKUP_VERIFIED)

            // Determine desired metadata for the written file
            val targetOwnerUid = currentMeta.ownerUid ?: managedTarget.defaultOwnerUid
            val targetGroupGid = currentMeta.groupGid ?: managedTarget.defaultGroupGid
            val targetModeOctal = currentMeta.permissionsOctal ?: managedTarget.defaultModeOctal
            val targetSelinux = currentMeta.selinuxContext ?: managedTarget.defaultSelinuxContext
            val stagingPath = "${managedTarget.parentDir}/.bt_did.conf.tmp.$txId"

            // Phase: APPLYING
            txRecord = updatePhase(txRecord, TransactionPhase.APPLYING)

            val writeStageRes = executor.execute(
                RootShellCommand.WriteStagingFile(
                    stagingPath = stagingPath,
                    expectedParentDir = managedTarget.parentDir,
                    content = newContentBytes
                )
            )
            if (!writeStageRes.isSuccess) {
                cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                val err = "写入临时文件失败: ${writeStageRes.exceptionMessage ?: writeStageRes.stderr}"
                return handleApplyOrVerifyFailure(txRecord, backupRecord, managedTarget, err)
            }

            // Verify staging file hash and size before atomic rename
            val stagingMetaRes = inspector.inspectFile(stagingPath, allowStagingInDir = managedTarget.parentDir)
            val stagingMeta = stagingMetaRes.getOrNull()
            if (stagingMeta == null || !stagingMeta.exists ||
                stagingMeta.sha256 != expectedAfterSha256 ||
                stagingMeta.sizeBytes != newContentBytes.size.toLong()
            ) {
                cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                val err = "临时文件写后校验失败 (预期 SHA=$expectedAfterSha256, 实际=${stagingMeta?.sha256})"
                return handleApplyOrVerifyFailure(txRecord, backupRecord, managedTarget, err)
            }

            // Apply ownership, permissions, and SELinux context to staging file before atomic move
            val metaRes = executor.execute(
                RootShellCommand.ApplyMetadata(
                    path = stagingPath,
                    expectedParentDir = managedTarget.parentDir,
                    ownerUid = targetOwnerUid,
                    groupGid = targetGroupGid,
                    modeOctal = targetModeOctal,
                    selinuxContext = targetSelinux
                )
            )
            if (!metaRes.isSuccess) {
                cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                val err = "设置临时文件权限/属主/SELinux 上下文失败: ${metaRes.exceptionMessage ?: metaRes.stderr}"
                return handleApplyOrVerifyFailure(txRecord, backupRecord, managedTarget, err)
            }

            // Atomic move staging file over target file (`mv -f`)
            val renameRes = executor.execute(
                RootShellCommand.AtomicRename(
                    stagingPath = stagingPath,
                    targetPath = targetPath
                )
            )
            if (!renameRes.isSuccess) {
                cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                val err = "原子替换目标文件失败: ${renameRes.exceptionMessage ?: renameRes.stderr}"
                return handleApplyOrVerifyFailure(txRecord, backupRecord, managedTarget, err)
            }

            // Phase: APPLIED
            txRecord = updatePhase(txRecord, TransactionPhase.APPLIED)

            // Phase: VERIFYING
            txRecord = updatePhase(txRecord, TransactionPhase.VERIFYING)

            val afterMeta = inspector.inspectFile(targetPath).getOrElse {
                return handleApplyOrVerifyFailure(
                    txRecord,
                    backupRecord,
                    managedTarget,
                    "修改后重新读取目标文件元数据失败: ${it.message}"
                )
            }

            if (!afterMeta.exists || afterMeta.fileType != FileType.REGULAR_FILE) {
                return handleApplyOrVerifyFailure(
                    txRecord,
                    backupRecord,
                    managedTarget,
                    "修改后目标文件不存在或类型异常 (${afterMeta.fileType})"
                )
            }

            val reReadBytes = inspector.readFileBytes(targetPath).getOrElse {
                return handleApplyOrVerifyFailure(
                    txRecord,
                    backupRecord,
                    managedTarget,
                    "修改后重新读取目标文件内容失败: ${it.message}"
                )
            }
            val reReadSha = Sha256Utils.sha256Hex(reReadBytes)

            if (afterMeta.sha256 != expectedAfterSha256 || reReadSha != expectedAfterSha256) {
                return handleApplyOrVerifyFailure(
                    txRecord,
                    backupRecord,
                    managedTarget,
                    "修改后文件 SHA-256 校验不匹配 (预期=$expectedAfterSha256, stat=${afterMeta.sha256}, read=$reReadSha)"
                )
            }

            if (afterMeta.permissionsOctal != targetModeOctal ||
                afterMeta.ownerUid != targetOwnerUid ||
                afterMeta.groupGid != targetGroupGid ||
                (!targetSelinux.isNullOrBlank() && afterMeta.selinuxContext != targetSelinux)
            ) {
                return handleApplyOrVerifyFailure(
                    txRecord,
                    backupRecord,
                    managedTarget,
                    "修改后文件元数据不符合预期 (期望=${targetOwnerUid}:${targetGroupGid} $targetModeOctal $targetSelinux, " +
                        "实际=${afterMeta.ownerUid}:${afterMeta.groupGid} ${afterMeta.permissionsOctal} ${afterMeta.selinuxContext})"
                )
            }

            // Phase: COMMITTED
            txRecord = txRecord.copy(
                phase = TransactionPhase.COMMITTED,
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis(),
                actualAfterMetadata = afterMeta,
                verificationPassed = true,
                errorMessage = null
            ).withComputedChecksum()
            persistTransactionAndNotify(txRecord)

            auditLogger.record(
                action = "TX_MODIFY_COMMITTED",
                outcome = "SUCCESS",
                summary = "系统文件修改成功并已完成 SHA-256 与元数据多重校验",
                targetPath = targetPath,
                transactionId = txId,
                backupId = backupRecord.backupId,
                details = "afterSha256=$expectedAfterSha256, mode=${afterMeta.permissionsOctal}, selinux=${afterMeta.selinuxContext}"
            )

            return ModificationOutcome.Committed(txRecord, backupRecord, afterMeta)
        } finally {
            activeTransactionId = null
            activeOperationGuard.set(false)
        }
    }

    /**
     * Restores [backupId] to its target system path with conflict detection, pre-restore snapshot,
     * metadata restoration, and post-restore verification.
     */
    fun restoreFromBackup(
        backupId: String,
        userConfirmed: Boolean,
        forceOverwriteConflict: Boolean = false
    ): RestoreOutcome {
        if (!activeOperationGuard.compareAndSet(false, true)) {
            val msg = "检测到并发操作：当前已有另一个 Root 文件事务正在执行，请稍后再试"
            auditLogger.record("TX_RESTORE", "FAILURE", msg, backupId = backupId)
            return RestoreOutcome.RejectedPrecheck(msg)
        }

        try {
            if (!userConfirmed) {
                return RestoreOutcome.RejectedPrecheck("恢复操作未经用户确认，已取消")
            }

            // 1. Verify backup format & integrity
            val verifiedBackup = when (val v = backupManager.verifyBackup(backupId)) {
                is BackupVerificationResult.Invalid -> {
                    val msg = "备份校验未通过，拒绝执行恢复: ${v.reason}"
                    auditLogger.record("TX_RESTORE", "FAILURE", msg, backupId = backupId)
                    return RestoreOutcome.RejectedPrecheck(msg)
                }
                is BackupVerificationResult.Valid -> v
            }
            val backup = verifiedBackup.record
            val targetPath = backup.targetPath

            // 2. Confirm target path is in writable whitelist
            val managedTarget = RootPathValidator.validatePath(targetPath, requireWritable = true)
                .getOrElse {
                    val msg = "备份目标路径不在可写白名单中: ${it.message}"
                    auditLogger.record("TX_RESTORE", "FAILURE", msg, targetPath, backupId = backupId)
                    return RestoreOutcome.RejectedPrecheck(msg)
                }

            // 3. Check Root availability & directory status
            val suRes = executor.execute(RootShellCommand.CheckSuId)
            if (!suRes.isSuccess || !suRes.stdout.contains("uid=0")) {
                val msg = "Root 权限不可用，无法执行恢复操作"
                auditLogger.record("TX_RESTORE", "FAILURE", msg, targetPath, backupId = backupId)
                return RestoreOutcome.RejectedPrecheck(msg)
            }

            val dirStatus = inspector.checkDirectoryWritable(managedTarget.parentDir)
            if (dirStatus != DirectoryWriteStatus.WRITABLE) {
                val msg = "目标目录 '${managedTarget.parentDir}' 不可写 ($dirStatus)，无法执行恢复"
                auditLogger.record("TX_RESTORE", "FAILURE", msg, targetPath, backupId = backupId)
                return RestoreOutcome.RejectedPrecheck(msg)
            }

            // 4. Inspect current file state
            val currentMeta = inspector.inspectFile(targetPath).getOrElse {
                val msg = "无法读取目标文件当前状态: ${it.message}"
                auditLogger.record("TX_RESTORE", "FAILURE", msg, targetPath, backupId = backupId)
                return RestoreOutcome.RejectedPrecheck(msg)
            }

            if (currentMeta.fileType != FileType.REGULAR_FILE && currentMeta.fileType != FileType.NOT_EXIST) {
                val msg = "当前目标路径为特殊类型 (${currentMeta.fileType.displayName})，拒绝覆盖或删除"
                auditLogger.record("TX_RESTORE", "FAILURE", msg, targetPath, backupId = backupId)
                return RestoreOutcome.RejectedPrecheck(msg)
            }

            // 5. Conflict detection: check if current file was modified by an external third-party program
            val allTransactions = listTransactions(targetPath)
            val knownManagedHashes = mutableSetOf<String>()
            backup.contentSha256?.let { knownManagedHashes.add(it) }
            backupManager.listBackups(targetPath)
                .filter { !it.isPreRestoreSnapshot }
                .mapNotNull { it.contentSha256 }
                .forEach { knownManagedHashes.add(it) }
            allTransactions
                .filter {
                    it.phase == TransactionPhase.APPLIED ||
                        it.phase == TransactionPhase.VERIFYING ||
                        it.phase == TransactionPhase.COMMITTED ||
                        it.phase == TransactionPhase.RECOVERY_REQUIRED
                }
                .mapNotNull { it.expectedAfterSha256 }
                .forEach { knownManagedHashes.add(it) }

            val lastCommittedTx = allTransactions.firstOrNull {
                it.operationType == TransactionOperationType.MODIFY_FILE && it.phase == TransactionPhase.COMMITTED
            }
            val lastExpectedSha = lastCommittedTx?.expectedAfterSha256 ?: allTransactions.firstOrNull()?.expectedAfterSha256

            if (currentMeta.exists) {
                val currentSha = currentMeta.sha256
                val isKnownState = currentSha != null && currentSha in knownManagedHashes
                if (!isKnownState && !forceOverwriteConflict) {
                    val explanation = buildString {
                        append("检测到目标文件已被外部程序修改！\n")
                        append("• 目标路径: $targetPath\n")
                        append("• 原始备份哈希: ${backup.contentSha256 ?: "(原始文件不存在)"}\n")
                        append("• 本应用最近修改哈希: ${lastExpectedSha ?: "(无)"}\n")
                        append("• 当前文件实际哈希: ${currentSha ?: "未知"}\n")
                        append("为防止误覆盖第三方修改，请确认是否强制还原至原始备份状态。")
                    }
                    auditLogger.record(
                        action = "TX_RESTORE_CONFLICT",
                        outcome = "WARNING",
                        summary = "检测到外部修改冲突，等待用户二次确认",
                        targetPath = targetPath,
                        backupId = backupId,
                        details = "currentSha=$currentSha, backupSha=${backup.contentSha256}, lastExpectedSha=$lastExpectedSha"
                    )
                    return RestoreOutcome.ConflictDetected(
                        targetPath = targetPath,
                        backup = backup,
                        currentMetadata = currentMeta,
                        lastKnownExpectedSha256 = lastExpectedSha,
                        conflictExplanation = explanation
                    )
                }
            }

            // Extra guard when original state was NOT_EXIST: ensure current file was created by a managed transaction
            if (!backup.originalExisted && currentMeta.exists) {
                val createdByManagedTx = allTransactions.any {
                    it.operationType == TransactionOperationType.MODIFY_FILE &&
                        it.expectedExistAfter &&
                        (it.expectedAfterSha256 == currentMeta.sha256 || forceOverwriteConflict)
                }
                if (!createdByManagedTx && !forceOverwriteConflict) {
                    val explanation = "原始状态为“文件不存在”，但当前存在的文件 (SHA-256=${currentMeta.sha256?.take(16)}...) " +
                        "无法确认为本应用受管事务创建。为安全起见，需用户明确确认后才允许删除该文件。"
                    return RestoreOutcome.ConflictDetected(
                        targetPath = targetPath,
                        backup = backup,
                        currentMetadata = currentMeta,
                        lastKnownExpectedSha256 = lastExpectedSha,
                        conflictExplanation = explanation
                    )
                }
            }

            // 6. Create pre-restore protection snapshot if current file exists and is readable
            val txId = generateTransactionId(prefix = "tx_restore")
            activeTransactionId = txId
            val opId = "op_restore_${System.currentTimeMillis()}"
            var preRestoreSnapshot: RootBackupRecord? = null

            if (currentMeta.exists && currentMeta.fileType == FileType.REGULAR_FILE) {
                val snapRes = backupManager.createAndVerifyBackup(
                    targetPath = targetPath,
                    transactionId = txId,
                    operationId = opId,
                    isPreRestoreSnapshot = true,
                    note = "恢复前保护快照 (target backup=$backupId)"
                )
                if (snapRes.isFailure) {
                    val msg = "创建恢复前保护快照失败，为安全起见中止恢复: ${snapRes.exceptionOrNull()?.message}"
                    auditLogger.record("TX_RESTORE", "FAILURE", msg, targetPath, txId, backupId)
                    return RestoreOutcome.RejectedPrecheck(msg)
                }
                preRestoreSnapshot = snapRes.getOrThrow()
            }

            // 7. Create persistent restore transaction record
            var txRecord = RootTransactionRecord(
                transactionId = txId,
                operationId = opId,
                operationType = TransactionOperationType.RESTORE_BACKUP,
                targetPath = targetPath,
                backupId = backup.backupId,
                preRestoreSnapshotId = preRestoreSnapshot?.backupId,
                phase = TransactionPhase.PREPARED,
                startedAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
                plannedActionSummary = if (backup.originalExisted) {
                    "从备份 ${backup.backupId} 恢复原始文件内容与元数据"
                } else {
                    "按备份 ${backup.backupId} 恢复原始“文件不存在”状态 (删除受管创建的文件)"
                },
                preModifyMetadata = currentMeta,
                expectedExistAfter = backup.originalExisted,
                expectedAfterSha256 = backup.contentSha256
            ).withComputedChecksum()
            persistTransactionAndNotify(txRecord)

            // Phase: BACKUP_VERIFIED
            txRecord = updatePhase(txRecord, TransactionPhase.BACKUP_VERIFIED)

            // Phase: APPLYING
            txRecord = updatePhase(txRecord, TransactionPhase.APPLYING)

            var metadataRestoreError: String? = null

            if (backup.originalExisted) {
                val backupBytes = verifiedBackup.contentBytes
                    ?: return markRestoreFailed(txRecord, backup, preRestoreSnapshot, "备份字节数据为空")

                val stagingPath = "${managedTarget.parentDir}/.bt_did.conf.restore.$txId"
                val writeRes = executor.execute(
                    RootShellCommand.WriteStagingFile(
                        stagingPath = stagingPath,
                        expectedParentDir = managedTarget.parentDir,
                        content = backupBytes
                    )
                )
                if (!writeRes.isSuccess) {
                    cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                    return markRestoreFailed(
                        txRecord,
                        backup,
                        preRestoreSnapshot,
                        "恢复期间写入临时文件失败: ${writeRes.exceptionMessage ?: writeRes.stderr}"
                    )
                }

                // Verify staging file hash before moving
                val stageMeta = inspector.inspectFile(stagingPath, allowStagingInDir = managedTarget.parentDir).getOrNull()
                if (stageMeta == null || !stageMeta.exists || stageMeta.sha256 != backup.contentSha256) {
                    cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                    return markRestoreFailed(
                        txRecord,
                        backup,
                        preRestoreSnapshot,
                        "恢复临时文件哈希校验失败 (预期=${backup.contentSha256}, 实际=${stageMeta?.sha256})"
                    )
                }

                // Apply original metadata to staging file BEFORE atomic rename so targetPath never has a 0600 root:root window
                val origMeta = backup.originalMetadata
                val stageMetaApply = executor.execute(
                    RootShellCommand.ApplyMetadata(
                        path = stagingPath,
                        expectedParentDir = managedTarget.parentDir,
                        ownerUid = origMeta.ownerUid ?: managedTarget.defaultOwnerUid,
                        groupGid = origMeta.groupGid ?: managedTarget.defaultGroupGid,
                        modeOctal = origMeta.permissionsOctal ?: managedTarget.defaultModeOctal,
                        selinuxContext = origMeta.selinuxContext ?: managedTarget.defaultSelinuxContext
                    )
                )
                if (!stageMetaApply.isSuccess) {
                    metadataRestoreError = "恢复临时文件元数据返回错误 (exit=${stageMetaApply.exitCode}): ${stageMetaApply.exceptionMessage ?: stageMetaApply.stderr}"
                }

                // Atomic move staging file to target path
                val moveRes = executor.execute(
                    RootShellCommand.AtomicRename(
                        stagingPath = stagingPath,
                        targetPath = targetPath
                    )
                )
                if (!moveRes.isSuccess) {
                    cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                    return markRestoreFailed(
                        txRecord,
                        backup,
                        preRestoreSnapshot,
                        "原子替换恢复原始文件失败: ${moveRes.exceptionMessage ?: moveRes.stderr}"
                    )
                }

                // Confirm/apply original metadata on targetPath as well (ensures final filename SELinux context is enforced)
                val metaRes = executor.execute(
                    RootShellCommand.ApplyMetadata(
                        path = targetPath,
                        expectedParentDir = managedTarget.parentDir,
                        ownerUid = origMeta.ownerUid ?: managedTarget.defaultOwnerUid,
                        groupGid = origMeta.groupGid ?: managedTarget.defaultGroupGid,
                        modeOctal = origMeta.permissionsOctal ?: managedTarget.defaultModeOctal,
                        selinuxContext = origMeta.selinuxContext ?: managedTarget.defaultSelinuxContext
                    )
                )
                if (!metaRes.isSuccess) {
                    metadataRestoreError = "恢复元数据命令返回错误 (exit=${metaRes.exitCode}): ${metaRes.exceptionMessage ?: metaRes.stderr}"
                }
            } else {
                // Original state was NOT_EXIST -> safely delete the file if it currently exists
                if (currentMeta.exists) {
                    val delRes = executor.execute(
                        RootShellCommand.DeleteFile(
                            path = targetPath,
                            isStaging = false,
                            expectedParentDir = managedTarget.parentDir
                        )
                    )
                    if (!delRes.isSuccess) {
                        return markRestoreFailed(
                            txRecord,
                            backup,
                            preRestoreSnapshot,
                            "删除事务新建的文件以恢复原始不存在状态失败: ${delRes.exceptionMessage ?: delRes.stderr}"
                        )
                    }
                }
            }

            // Phase: APPLIED
            txRecord = updatePhase(txRecord, TransactionPhase.APPLIED)

            // Phase: VERIFYING
            txRecord = updatePhase(txRecord, TransactionPhase.VERIFYING)

            val afterMeta = inspector.inspectFile(targetPath).getOrElse {
                return markRestoreFailed(
                    txRecord,
                    backup,
                    preRestoreSnapshot,
                    "恢复后无法读取目标文件进行校验: ${it.message}"
                )
            }

            if (!backup.originalExisted) {
                if (afterMeta.exists || afterMeta.fileType != FileType.NOT_EXIST) {
                    return markRestoreFailed(
                        txRecord,
                        backup,
                        preRestoreSnapshot,
                        "恢复后校验失败：原始状态为不存在，但目标文件仍然存在"
                    )
                }
            } else {
                if (!afterMeta.exists || afterMeta.fileType != FileType.REGULAR_FILE) {
                    return markRestoreFailed(
                        txRecord,
                        backup,
                        preRestoreSnapshot,
                        "恢复后校验失败：目标文件不存在或类型异常 (${afterMeta.fileType})"
                    )
                }
                val reReadBytes = inspector.readFileBytes(targetPath).getOrElse {
                    return markRestoreFailed(
                        txRecord,
                        backup,
                        preRestoreSnapshot,
                        "恢复后重新读取文件内容失败: ${it.message}"
                    )
                }
                val reReadSha = Sha256Utils.sha256Hex(reReadBytes)
                if (afterMeta.sha256 != backup.contentSha256 || reReadSha != backup.contentSha256) {
                    return markRestoreFailed(
                        txRecord,
                        backup,
                        preRestoreSnapshot,
                        "恢复后文件 SHA-256 不匹配 (预期=${backup.contentSha256}, stat=${afterMeta.sha256}, read=$reReadSha)"
                    )
                }

                // Check metadata restoration completeness
                val origMeta = backup.originalMetadata
                val metadataMismatches = mutableListOf<String>()
                if (metadataRestoreError != null) {
                    metadataMismatches.add(metadataRestoreError)
                }
                if (origMeta.permissionsOctal != null && afterMeta.permissionsOctal != origMeta.permissionsOctal) {
                    metadataMismatches.add("权限模式不匹配 (预期=${origMeta.permissionsOctal}, 实际=${afterMeta.permissionsOctal})")
                }
                if (origMeta.ownerUid != null && afterMeta.ownerUid != origMeta.ownerUid) {
                    metadataMismatches.add("属主 UID 不匹配 (预期=${origMeta.ownerUid}, 实际=${afterMeta.ownerUid})")
                }
                if (origMeta.groupGid != null && afterMeta.groupGid != origMeta.groupGid) {
                    metadataMismatches.add("属组 GID 不匹配 (预期=${origMeta.groupGid}, 实际=${afterMeta.groupGid})")
                }
                if (!origMeta.selinuxContext.isNullOrBlank() && afterMeta.selinuxContext != origMeta.selinuxContext) {
                    metadataMismatches.add("SELinux 上下文不匹配 (预期=${origMeta.selinuxContext}, 实际=${afterMeta.selinuxContext})")
                }

                if (metadataMismatches.isNotEmpty()) {
                    val warningText = "部分恢复：文件内容与 SHA-256 已还原一致，但部分元数据未能完全还原 -> " +
                        metadataMismatches.joinToString("; ")
                    // Keep recoveryResolution = null so getUnfinishedTransactions() retains the RECOVERY_REQUIRED lock until resolved
                    txRecord = txRecord.copy(
                        phase = TransactionPhase.RECOVERY_REQUIRED,
                        updatedAtEpochMs = System.currentTimeMillis(),
                        actualAfterMetadata = afterMeta,
                        verificationPassed = false,
                        partialRestoreWarning = warningText,
                        errorMessage = warningText,
                        recoveryResolution = null
                    ).withComputedChecksum()
                    persistTransactionAndNotify(txRecord)
                    auditLogger.record(
                        action = "TX_RESTORE_PARTIAL",
                        outcome = "WARNING",
                        summary = warningText,
                        targetPath = targetPath,
                        transactionId = txId,
                        backupId = backupId
                    )
                    return RestoreOutcome.PartialRestoreMetadataWarning(
                        transaction = txRecord,
                        restoredBackup = backup,
                        preRestoreSnapshot = preRestoreSnapshot,
                        afterMetadata = afterMeta,
                        metadataWarning = warningText
                    )
                }
            }

            // All content and metadata checks passed!
            txRecord = txRecord.copy(
                phase = TransactionPhase.COMMITTED,
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis(),
                actualAfterMetadata = afterMeta,
                verificationPassed = true,
                partialRestoreWarning = null,
                errorMessage = null,
                recoveryResolution = "RESTORE_VERIFIED"
            ).withComputedChecksum()

            return try {
                if (simulateFinalLogWriteFailure) {
                    throw IOException("模拟恢复后日志写入磁盘失败 (simulateFinalLogWriteFailure)")
                }
                persistTransactionAndNotify(txRecord)
                resolvePendingTransactionsAfterVerifiedRestore(targetPath, txId)
                val logRes = auditLogger.record(
                    action = "TX_RESTORE_VERIFIED",
                    outcome = "SUCCESS",
                    summary = "恢复已验证 (原始状态已 100% 还原并通过 SHA-256 与元数据校验)",
                    targetPath = targetPath,
                    transactionId = txId,
                    backupId = backupId
                )
                if (logRes.isFailure) {
                    throw logRes.exceptionOrNull() ?: IOException("写入审计日志失败")
                }
                RestoreOutcome.VerifiedSuccess(
                    transaction = txRecord,
                    restoredBackup = backup,
                    preRestoreSnapshot = preRestoreSnapshot,
                    afterMetadata = afterMeta
                )
            } catch (logErr: Throwable) {
                RestoreOutcome.RestoredWithLogWriteFailure(
                    transaction = txRecord,
                    restoredBackup = backup,
                    preRestoreSnapshot = preRestoreSnapshot,
                    afterMetadata = afterMeta,
                    logWriteError = "原始文件已成功恢复并通过验证，但持久化日志写入失败: ${logErr.message}"
                )
            }
        } finally {
            activeTransactionId = null
            activeOperationGuard.set(false)
        }
    }

    /**
     * Lists all persisted transaction records, newest first.
     * Any corrupted or tampered `.json` file that fails SHA-256 checksum verification is surfaced
     * as a `RECOVERY_REQUIRED` record rather than silently ignored.
     */
    fun listTransactions(targetPath: String? = null): List<RootTransactionRecord> {
        val files = transactionsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        val list = mutableListOf<RootTransactionRecord>()
        for (f in files) {
            val rawText = try {
                f.readText(Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
            val verifiedRec = RootTransactionRecord.fromJsonString(rawText, verifyChecksum = true)
            val rec = if (verifiedRec != null) {
                verifiedRec
            } else {
                val unverified = RootTransactionRecord.fromJsonString(rawText, verifyChecksum = false)
                val fallbackTxId = unverified?.transactionId ?: f.nameWithoutExtension
                val fallbackPath = unverified?.targetPath ?: ManagedTargetPath.DATA_BT_DID_CONF.canonicalPath
                RootTransactionRecord(
                    transactionId = fallbackTxId,
                    operationId = unverified?.operationId ?: "corrupted_op",
                    operationType = unverified?.operationType ?: TransactionOperationType.MODIFY_FILE,
                    targetPath = fallbackPath,
                    backupId = unverified?.backupId ?: "UNKNOWN_CORRUPTED",
                    preRestoreSnapshotId = unverified?.preRestoreSnapshotId,
                    phase = TransactionPhase.RECOVERY_REQUIRED,
                    startedAtEpochMs = unverified?.startedAtEpochMs ?: f.lastModified(),
                    updatedAtEpochMs = f.lastModified(),
                    completedAtEpochMs = null,
                    plannedActionSummary = unverified?.plannedActionSummary ?: "事务记录损坏或校验和被篡改",
                    preModifyMetadata = unverified?.preModifyMetadata ?: RootFileMetadata(
                        path = fallbackPath,
                        exists = false,
                        fileType = FileType.NOT_EXIST
                    ),
                    expectedExistAfter = unverified?.expectedExistAfter ?: true,
                    expectedAfterSha256 = unverified?.expectedAfterSha256,
                    actualAfterMetadata = unverified?.actualAfterMetadata,
                    verificationPassed = false,
                    partialRestoreWarning = unverified?.partialRestoreWarning,
                    errorMessage = "事务日志文件损坏或校验和不匹配 (${f.name})，需执行恢复检查",
                    recoveryResolution = null,
                    recordChecksumSha256 = "INVALID_CHECKSUM"
                )
            }
            if (targetPath == null || rec.targetPath == targetPath) {
                list.add(rec)
            }
        }
        return list.sortedWith(
            compareByDescending<RootTransactionRecord> { it.startedAtEpochMs }
                .thenByDescending { extractTransactionSequence(it.transactionId) }
                .thenByDescending { it.transactionId }
        )
    }

    fun getUnfinishedTransactions(targetPath: String? = null): List<RootTransactionRecord> {
        return listTransactions(targetPath).filter { !it.phase.isTerminal && it.recoveryResolution == null }
    }

    fun getActiveProtectedBackupIds(): Set<String> {
        val ids = mutableSetOf<String>()
        for (tx in getUnfinishedTransactions()) {
            ids.add(tx.backupId)
            tx.preRestoreSnapshotId?.let { ids.add(it) }
        }
        return ids
    }

    /**
     * Allows user to explicitly acknowledge and resolve a pending transaction when the file state
     * has been inspected and confirmed.
     */
    fun markTransactionResolvedByUser(transactionId: String, resolutionNote: String): Result<RootTransactionRecord> {
        val file = File(transactionsDir, "$transactionId.json")
        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("找不到事务记录: $transactionId"))
        }
        val rawText = try {
            file.readText(Charsets.UTF_8)
        } catch (t: Throwable) {
            return Result.failure(IllegalStateException("无法读取事务记录 ($transactionId): ${t.message}"))
        }
        val tx = RootTransactionRecord.fromJsonString(rawText, verifyChecksum = true)
            ?: RootTransactionRecord.fromJsonString(rawText, verifyChecksum = false)
            ?: listTransactions().firstOrNull { it.transactionId == transactionId }
            ?: return Result.failure(IllegalStateException("事务记录格式损坏: $transactionId"))
        val updated = tx.copy(
            updatedAtEpochMs = System.currentTimeMillis(),
            completedAtEpochMs = System.currentTimeMillis(),
            recoveryResolution = resolutionNote
        ).withComputedChecksum()
        persistTransactionToDisk(updated)
        auditLogger.record(
            action = "TX_RECOVERY_ACKNOWLEDGED",
            outcome = "INFO",
            summary = "用户已确认并处理待恢复事务: $resolutionNote",
            targetPath = tx.targetPath,
            transactionId = transactionId,
            backupId = tx.backupId
        )
        return Result.success(updated)
    }

    internal fun persistTransactionToDisk(record: RootTransactionRecord) {
        if (!transactionsDir.exists() && !transactionsDir.mkdirs()) {
            throw IOException("无法创建事务日志目录: ${transactionsDir.absolutePath}")
        }
        val tmpFile = File(transactionsDir, "${record.transactionId}.json.tmp")
        val finalFile = File(transactionsDir, "${record.transactionId}.json")
        val bytes = record.toJsonString().toByteArray(Charsets.UTF_8)
        FileOutputStream(tmpFile).use { fos ->
            fos.write(bytes)
            fos.flush()
            try {
                fos.fd.sync()
            } catch (_: Throwable) {}
        }
        if (finalFile.exists()) {
            finalFile.delete()
        }
        if (!tmpFile.renameTo(finalFile)) {
            throw IOException("无法原子提交事务日志文件: ${finalFile.name}")
        }
    }

    private fun persistTransactionAndNotify(record: RootTransactionRecord) {
        persistTransactionToDisk(record)
        onPhasePersistedHook?.invoke(record)
    }

    private fun updatePhase(
        record: RootTransactionRecord,
        newPhase: TransactionPhase,
        errorMessage: String? = record.errorMessage,
        completed: Boolean = false
    ): RootTransactionRecord {
        val now = System.currentTimeMillis()
        val updated = record.copy(
            phase = newPhase,
            updatedAtEpochMs = now,
            completedAtEpochMs = if (completed) now else record.completedAtEpochMs,
            errorMessage = errorMessage
        ).withComputedChecksum()
        persistTransactionAndNotify(updated)
        return updated
    }

    private fun handleApplyOrVerifyFailure(
        currentTx: RootTransactionRecord,
        backupRecord: RootBackupRecord,
        managedTarget: ManagedTargetPath,
        failureReason: String
    ): ModificationOutcome {
        auditLogger.record(
            action = "TX_MODIFY_FAILED",
            outcome = "FAILURE",
            summary = "修改或写后验证失败，启动自动回滚: $failureReason",
            targetPath = currentTx.targetPath,
            transactionId = currentTx.transactionId,
            backupId = backupRecord.backupId
        )

        var tx = updatePhase(currentTx, TransactionPhase.ROLLBACK_REQUIRED, errorMessage = failureReason)
        tx = updatePhase(tx, TransactionPhase.ROLLING_BACK, errorMessage = failureReason)

        val rollbackOk = performInternalRollback(tx.transactionId, backupRecord, managedTarget)
        return if (rollbackOk) {
            val afterRollbackMeta = inspector.inspectFile(currentTx.targetPath).getOrNull()
            tx = tx.copy(
                phase = TransactionPhase.ROLLED_BACK,
                updatedAtEpochMs = System.currentTimeMillis(),
                completedAtEpochMs = System.currentTimeMillis(),
                actualAfterMetadata = afterRollbackMeta,
                errorMessage = failureReason,
                recoveryResolution = "AUTO_ROLLED_BACK_VERIFIED"
            ).withComputedChecksum()
            persistTransactionAndNotify(tx)
            auditLogger.record(
                action = "TX_ROLLBACK_SUCCESS",
                outcome = "SUCCESS",
                summary = "事务已自动回滚并恢复至修改前原始备份状态",
                targetPath = currentTx.targetPath,
                transactionId = currentTx.transactionId,
                backupId = backupRecord.backupId
            )
            ModificationOutcome.RolledBack(tx, backupRecord, failureReason)
        } else {
            tx = tx.copy(
                phase = TransactionPhase.RECOVERY_REQUIRED,
                updatedAtEpochMs = System.currentTimeMillis(),
                errorMessage = "$failureReason (自动回滚未能确认成功，已进入待恢复状态)"
            ).withComputedChecksum()
            persistTransactionAndNotify(tx)
            auditLogger.record(
                action = "TX_RECOVERY_REQUIRED",
                outcome = "FAILURE",
                summary = "自动回滚未能确认完成，事务标记为 RECOVERY_REQUIRED",
                targetPath = currentTx.targetPath,
                transactionId = currentTx.transactionId,
                backupId = backupRecord.backupId
            )
            ModificationOutcome.RecoveryRequired(tx, backupRecord, tx.errorMessage ?: failureReason)
        }
    }

    private fun performInternalRollback(
        txId: String,
        backupRecord: RootBackupRecord,
        managedTarget: ManagedTargetPath
    ): Boolean {
        val verified = backupManager.verifyBackup(backupRecord.backupId)
        if (verified !is BackupVerificationResult.Valid) return false

        val targetPath = backupRecord.targetPath
        if (!backupRecord.originalExisted) {
            val delRes = executor.execute(
                RootShellCommand.DeleteFile(
                    path = targetPath,
                    isStaging = false,
                    expectedParentDir = managedTarget.parentDir
                )
            )
            if (!delRes.isSuccess) return false
            val checkMeta = inspector.inspectFile(targetPath).getOrNull() ?: return false
            return !checkMeta.exists && checkMeta.fileType == FileType.NOT_EXIST
        } else {
            val bytes = verified.contentBytes ?: return false
            val stagingPath = "${managedTarget.parentDir}/.bt_did.conf.rb.$txId"
            val writeRes = executor.execute(
                RootShellCommand.WriteStagingFile(stagingPath, managedTarget.parentDir, bytes)
            )
            if (!writeRes.isSuccess) {
                cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                return false
            }
            val stageMeta = inspector.inspectFile(stagingPath, allowStagingInDir = managedTarget.parentDir).getOrNull()
            if (stageMeta == null || !stageMeta.exists || stageMeta.sha256 != backupRecord.contentSha256) {
                cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                return false
            }
            val origMeta = backupRecord.originalMetadata
            val metaRes = executor.execute(
                RootShellCommand.ApplyMetadata(
                    path = stagingPath,
                    expectedParentDir = managedTarget.parentDir,
                    ownerUid = origMeta.ownerUid ?: managedTarget.defaultOwnerUid,
                    groupGid = origMeta.groupGid ?: managedTarget.defaultGroupGid,
                    modeOctal = origMeta.permissionsOctal ?: managedTarget.defaultModeOctal,
                    selinuxContext = origMeta.selinuxContext ?: managedTarget.defaultSelinuxContext
                )
            )
            if (!metaRes.isSuccess) {
                cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                return false
            }
            val moveRes = executor.execute(RootShellCommand.AtomicRename(stagingPath, targetPath))
            if (!moveRes.isSuccess) {
                cleanupStagingQuietly(stagingPath, managedTarget.parentDir)
                return false
            }
            executor.execute(
                RootShellCommand.ApplyMetadata(
                    path = targetPath,
                    expectedParentDir = managedTarget.parentDir,
                    ownerUid = origMeta.ownerUid ?: managedTarget.defaultOwnerUid,
                    groupGid = origMeta.groupGid ?: managedTarget.defaultGroupGid,
                    modeOctal = origMeta.permissionsOctal ?: managedTarget.defaultModeOctal,
                    selinuxContext = origMeta.selinuxContext ?: managedTarget.defaultSelinuxContext
                )
            )
            val afterMeta = inspector.inspectFile(targetPath).getOrNull() ?: return false
            val reReadBytes = inspector.readFileBytes(targetPath).getOrNull() ?: return false
            val reReadSha = Sha256Utils.sha256Hex(reReadBytes)
            val contentOk = afterMeta.exists &&
                afterMeta.fileType == FileType.REGULAR_FILE &&
                afterMeta.sha256 == backupRecord.contentSha256 &&
                reReadSha == backupRecord.contentSha256
            val metaOk = (origMeta.permissionsOctal == null || afterMeta.permissionsOctal == origMeta.permissionsOctal) &&
                (origMeta.ownerUid == null || afterMeta.ownerUid == origMeta.ownerUid) &&
                (origMeta.groupGid == null || afterMeta.groupGid == origMeta.groupGid) &&
                (origMeta.selinuxContext.isNullOrBlank() || afterMeta.selinuxContext == origMeta.selinuxContext)
            return contentOk && metaOk
        }
    }

    private fun markRestoreFailed(
        txRecord: RootTransactionRecord,
        backup: RootBackupRecord,
        preRestoreSnapshot: RootBackupRecord?,
        reason: String
    ): RestoreOutcome.FailedRecoveryRequired {
        val updated = txRecord.copy(
            phase = TransactionPhase.RECOVERY_REQUIRED,
            updatedAtEpochMs = System.currentTimeMillis(),
            errorMessage = reason
        ).withComputedChecksum()
        try {
            persistTransactionAndNotify(updated)
        } catch (_: Throwable) {}
        auditLogger.record(
            action = "TX_RESTORE_FAILED",
            outcome = "FAILURE",
            summary = "恢复过程失败，保留原始备份与恢复前快照: $reason",
            targetPath = backup.targetPath,
            transactionId = txRecord.transactionId,
            backupId = backup.backupId
        )
        return RestoreOutcome.FailedRecoveryRequired(
            transaction = updated,
            restoredBackup = backup,
            preRestoreSnapshot = preRestoreSnapshot,
            reason = reason
        )
    }

    private fun resolvePendingTransactionsAfterVerifiedRestore(targetPath: String, restoreTxId: String) {
        for (pending in getUnfinishedTransactions(targetPath)) {
            if (pending.transactionId != restoreTxId) {
                val resolved = pending.copy(
                    updatedAtEpochMs = System.currentTimeMillis(),
                    completedAtEpochMs = System.currentTimeMillis(),
                    recoveryResolution = "RESOLVED_BY_RESTORE_$restoreTxId"
                ).withComputedChecksum()
                try {
                    persistTransactionToDisk(resolved)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun cleanupStagingQuietly(stagingPath: String, parentDir: String) {
        try {
            executor.execute(RootShellCommand.DeleteFile(stagingPath, isStaging = true, expectedParentDir = parentDir))
        } catch (_: Throwable) {}
    }

    private fun rejectModify(targetPath: String, reason: String): ModificationOutcome.RejectedPrecheck {
        auditLogger.record("TX_MODIFY_PRECHECK", "FAILURE", reason, targetPath)
        return ModificationOutcome.RejectedPrecheck(reason)
    }

    private fun generateTransactionId(prefix: String = "tx_mod"): String {
        val ts = System.currentTimeMillis()
        val seq = String.format(java.util.Locale.US, "%06d", txSeqCounter.incrementAndGet())
        val shortUuid = UUID.randomUUID().toString().replace("-", "").take(8)
        return "${prefix}_${ts}_${seq}_$shortUuid"
    }

    private fun extractTransactionSequence(txId: String): Long {
        val parts = txId.split('_')
        if (parts.size < 4) return 0L
        return parts[parts.size - 2].toLongOrNull() ?: 0L
    }
}
