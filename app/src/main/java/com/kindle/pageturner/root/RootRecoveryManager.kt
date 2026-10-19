package com.kindle.pageturner.root

enum class TargetStateDiagnosis(val displayName: String) {
    MATCHES_ORIGINAL_BACKUP("目标文件处于原始状态 (与修改前备份完全一致)"),
    MATCHES_EXPECTED_MODIFIED("目标文件处于预期修改后状态 (写入已完成，仅事务提交中断)"),
    UNCERTAIN_INTERMEDIATE_OR_EXTERNAL("目标文件处于不确定的中间状态或已被外部修改"),
    ROOT_UNAVAILABLE_UNVERIFIED("当前未获取 Root 权限，无法读取目标文件确认状态")
}

data class PendingRecoveryItem(
    val transaction: RootTransactionRecord,
    val interruptedAtPhase: TransactionPhase,
    val backupValid: Boolean,
    val backupRecord: RootBackupRecord?,
    val currentMetadata: RootFileMetadata?,
    val diagnosis: TargetStateDiagnosis,
    val recommendedNextAction: String
)

data class StartupRecoveryReport(
    val inspectedAtEpochMs: Long,
    val orphanTempFilesCleaned: Int,
    val pendingItems: List<PendingRecoveryItem>,
    val hasPendingRecovery: Boolean
) {
    fun toSummaryText(): String {
        if (!hasPendingRecovery) {
            return "正常：未发现中断或待恢复的 Root 文件事务" +
                if (orphanTempFilesCleaned > 0) " (已清理 $orphanTempFilesCleaned 个未完成临时碎片文件)" else ""
        }
        val sb = StringBuilder()
        sb.append("警告：发现 ${pendingItems.size} 个中断或待恢复的事务！已锁定写入保护，请检查并执行恢复：\n")
        for ((idx, item) in pendingItems.withIndex()) {
            sb.append("${idx + 1}. 事务 ${item.transaction.transactionId} (中断阶段: ${item.interruptedAtPhase.name})\n")
            sb.append("   • 目标路径: ${item.transaction.targetPath}\n")
            sb.append("   • 原始备份 (${item.transaction.backupId}): ${if (item.backupValid) "有效" else "损坏/缺失"}\n")
            sb.append("   • 当前文件诊断: ${item.diagnosis.displayName}\n")
            sb.append("   • 建议下一步操作: ${item.recommendedNextAction}\n")
        }
        return sb.toString().trimEnd()
    }
}

/**
 * Inspects persistent transaction logs on application startup or after crashes,
 * force-stops, and device reboots.
 *
 * Strictly adheres to Phase 6 rules:
 * - Never silently overwrites or auto-restores target system files during startup.
 * - Compares actual file hash against original backup and expected target state.
 * - Transitions interrupted transactions to `RECOVERY_REQUIRED` to block unsafe concurrent writes.
 */
class RootRecoveryManager(
    private val executor: RootCommandExecutor,
    private val inspector: RootFileInspector,
    private val backupManager: RootBackupManager,
    private val transactionManager: RootFileTransactionManager,
    private val auditLogger: RootAuditLogger
) {

    /**
     * Scans for orphan `.tmp` files and unfinished transactions, diagnoses the actual system file state,
     * and transitions interrupted transactions to `RECOVERY_REQUIRED`.
     */
    fun inspectStartupRecovery(): StartupRecoveryReport {
        val opInProgress = transactionManager.isOperationInProgress()
        val inFlightTxId = transactionManager.activeTransactionId
        val cleanedLocalTmpCount = if (opInProgress) 0 else cleanOrphanTempFiles()
        val unfinished = transactionManager.getUnfinishedTransactions()
            .filter { !opInProgress || it.transactionId != inFlightTxId }

        if (unfinished.isEmpty()) {
            if (cleanedLocalTmpCount > 0) {
                auditLogger.record(
                    action = "RECOVERY_STARTUP_SCAN",
                    outcome = "INFO",
                    summary = "启动恢复检查完成：无未完成事务，已清理 $cleanedLocalTmpCount 个未提交备份/日志临时文件"
                )
            }
            return StartupRecoveryReport(
                inspectedAtEpochMs = System.currentTimeMillis(),
                orphanTempFilesCleaned = cleanedLocalTmpCount,
                pendingItems = emptyList(),
                hasPendingRecovery = false
            )
        }

        val suCheck = executor.execute(RootShellCommand.CheckSuId)
        val hasRoot = suCheck.isSuccess && suCheck.stdout.contains("uid=0")

        var totalCleanedTmpCount = cleanedLocalTmpCount
        val items = mutableListOf<PendingRecoveryItem>()

        for (tx in unfinished) {
            if (hasRoot) {
                totalCleanedTmpCount += cleanOrphanRootStagingFiles(tx)
            }

            val originalInterruptedPhase = tx.phase
            val backupVer = backupManager.verifyBackup(tx.backupId)
            val backupValid = backupVer is BackupVerificationResult.Valid
            val backupRec = (backupVer as? BackupVerificationResult.Valid)?.record

            val currentMeta = if (hasRoot) {
                inspector.inspectFile(tx.targetPath).getOrNull()
            } else {
                null
            }

            val diagnosis = diagnoseTargetState(
                hasRoot = hasRoot,
                currentMeta = currentMeta,
                backup = backupRec,
                tx = tx
            )

            val recommendation = buildRecommendation(diagnosis, backupValid, originalInterruptedPhase)

            // Transition interrupted transaction to RECOVERY_REQUIRED if not already there
            val updatedTx = if (tx.phase != TransactionPhase.RECOVERY_REQUIRED) {
                val note = "检测到进程在阶段 ${originalInterruptedPhase.name} 中断；当前诊断: ${diagnosis.displayName}"
                val newTx = tx.copy(
                    phase = TransactionPhase.RECOVERY_REQUIRED,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    actualAfterMetadata = currentMeta ?: tx.actualAfterMetadata,
                    errorMessage = if (tx.errorMessage.isNullOrBlank()) note else "${tx.errorMessage} | $note"
                ).withComputedChecksum()
                try {
                    transactionManager.persistTransactionToDisk(newTx)
                } catch (_: Throwable) {}
                newTx
            } else {
                tx
            }

            auditLogger.record(
                action = "RECOVERY_PENDING_DETECTED",
                outcome = "WARNING",
                summary = "发现中断事务 ${tx.transactionId} (原阶段=${originalInterruptedPhase.name}, 诊断=${diagnosis.name})",
                targetPath = tx.targetPath,
                transactionId = tx.transactionId,
                backupId = tx.backupId,
                details = recommendation
            )

            items.add(
                PendingRecoveryItem(
                    transaction = updatedTx,
                    interruptedAtPhase = originalInterruptedPhase,
                    backupValid = backupValid,
                    backupRecord = backupRec,
                    currentMetadata = currentMeta,
                    diagnosis = diagnosis,
                    recommendedNextAction = recommendation
                )
            )
        }

        return StartupRecoveryReport(
            inspectedAtEpochMs = System.currentTimeMillis(),
            orphanTempFilesCleaned = totalCleanedTmpCount,
            pendingItems = items,
            hasPendingRecovery = items.isNotEmpty()
        )
    }

    private fun diagnoseTargetState(
        hasRoot: Boolean,
        currentMeta: RootFileMetadata?,
        backup: RootBackupRecord?,
        tx: RootTransactionRecord
    ): TargetStateDiagnosis {
        if (!hasRoot || currentMeta == null) {
            return TargetStateDiagnosis.ROOT_UNAVAILABLE_UNVERIFIED
        }

        // 1. Check if current state matches the original backup (or preModifyMetadata if interrupted in PREPARED before backup completed)
        if (backup != null) {
            if (!backup.originalExisted && !currentMeta.exists) {
                return TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP
            }
            if (backup.originalExisted && currentMeta.exists &&
                currentMeta.fileType == FileType.REGULAR_FILE &&
                currentMeta.sha256 != null &&
                currentMeta.sha256 == backup.contentSha256 &&
                metadataMatchesExpected(currentMeta, backup.originalMetadata)
            ) {
                return TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP
            }
        } else {
            val preMeta = tx.preModifyMetadata
            if (!preMeta.exists && !currentMeta.exists) {
                return TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP
            }
            if (preMeta.exists && currentMeta.exists &&
                currentMeta.fileType == FileType.REGULAR_FILE &&
                currentMeta.sha256 != null &&
                currentMeta.sha256 == preMeta.sha256 &&
                metadataMatchesExpected(currentMeta, preMeta)
            ) {
                return TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP
            }
        }

        // 2. Check if current state matches the expected post-modification state (both content SHA-256 and expected metadata)
        if (!tx.expectedExistAfter && !currentMeta.exists) {
            return TargetStateDiagnosis.MATCHES_EXPECTED_MODIFIED
        }
        val expectedPostMeta = resolveExpectedPostMetadata(backup, tx)
        if (tx.expectedExistAfter && currentMeta.exists &&
            currentMeta.fileType == FileType.REGULAR_FILE &&
            currentMeta.sha256 != null &&
            currentMeta.sha256 == tx.expectedAfterSha256 &&
            (expectedPostMeta == null || metadataMatchesExpected(currentMeta, expectedPostMeta))
        ) {
            return TargetStateDiagnosis.MATCHES_EXPECTED_MODIFIED
        }

        // 3. Otherwise it is in an uncertain intermediate or externally modified state (including metadata corruption)
        return TargetStateDiagnosis.UNCERTAIN_INTERMEDIATE_OR_EXTERNAL
    }

    private fun metadataMatchesExpected(actual: RootFileMetadata, expected: RootFileMetadata): Boolean {
        val modeOk = expected.permissionsOctal == null || actual.permissionsOctal == expected.permissionsOctal
        val uidOk = expected.ownerUid == null || actual.ownerUid == expected.ownerUid
        val gidOk = expected.groupGid == null || actual.groupGid == expected.groupGid
        val selinuxOk = expected.selinuxContext.isNullOrBlank() || actual.selinuxContext == expected.selinuxContext
        return modeOk && uidOk && gidOk && selinuxOk
    }

    private fun resolveExpectedPostMetadata(
        backup: RootBackupRecord?,
        tx: RootTransactionRecord
    ): RootFileMetadata? {
        val refMeta = if (tx.operationType == TransactionOperationType.RESTORE_BACKUP && backup != null) {
            backup.originalMetadata
        } else {
            backup?.originalMetadata ?: tx.preModifyMetadata
        }
        val managedTarget = RootPathValidator.validatePath(tx.targetPath, requireWritable = false).getOrNull()
        return RootFileMetadata(
            path = tx.targetPath,
            exists = true,
            fileType = FileType.REGULAR_FILE,
            sizeBytes = 0L,
            permissionsOctal = refMeta.permissionsOctal ?: managedTarget?.defaultModeOctal,
            ownerUid = refMeta.ownerUid ?: managedTarget?.defaultOwnerUid,
            groupGid = refMeta.groupGid ?: managedTarget?.defaultGroupGid,
            selinuxContext = refMeta.selinuxContext?.takeIf { it.isNotBlank() } ?: managedTarget?.defaultSelinuxContext
        )
    }

    private fun buildRecommendation(
        diagnosis: TargetStateDiagnosis,
        backupValid: Boolean,
        interruptedPhase: TransactionPhase
    ): String {
        return when (diagnosis) {
            TargetStateDiagnosis.MATCHES_ORIGINAL_BACKUP -> {
                "当前系统文件与修改前原始状态完全一致（未发生实质改动或已回退）。您可以点击“确认并标记已处理”解除锁定，或重新执行修改。"
            }
            TargetStateDiagnosis.MATCHES_EXPECTED_MODIFIED -> {
                if (backupValid) {
                    "文件写入在崩溃前已完成且哈希匹配预期结果（中断于 ${interruptedPhase.name}）。建议点击“恢复原始文件”还原，或在验证元数据无误后点击“确认并标记已处理”。"
                } else {
                    "文件已处于修改后状态，但关联备份校验异常。请先检查目标文件内容并创建新快照。"
                }
            }
            TargetStateDiagnosis.UNCERTAIN_INTERMEDIATE_OR_EXTERNAL -> {
                if (backupValid) {
                    "目标文件处于不确定的中间状态或被外部修改！已禁止自动写入。请检查文件状态，并点击“恢复原始文件”从有效备份还原。"
                } else {
                    "目标文件处于不确定状态且备份不可用，严禁自动覆盖！请人工核查系统文件。"
                }
            }
            TargetStateDiagnosis.ROOT_UNAVAILABLE_UNVERIFIED -> {
                "请先授予 Root 权限后点击“检查 Root 环境”，以便系统读取并核实目标文件的真实状态。"
            }
        }
    }

    private fun cleanOrphanRootStagingFiles(tx: RootTransactionRecord): Int {
        val managed = RootPathValidator.validatePath(tx.targetPath, requireWritable = true).getOrNull() ?: return 0
        val parentDir = managed.parentDir
        val candidateStagingPaths = listOf(
            "$parentDir/.bt_did.conf.tmp.${tx.transactionId}",
            "$parentDir/.bt_did.conf.restore.${tx.transactionId}",
            "$parentDir/.bt_did.conf.rb.${tx.transactionId}"
        )
        var cleaned = 0
        for (stagingPath in candidateStagingPaths) {
            try {
                val meta = inspector.inspectFile(stagingPath, allowStagingInDir = parentDir).getOrNull()
                if (meta != null && meta.exists) {
                    val delRes = executor.execute(
                        RootShellCommand.DeleteFile(
                            path = stagingPath,
                            isStaging = true,
                            expectedParentDir = parentDir
                        )
                    )
                    if (delRes.isSuccess) {
                        cleaned++
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return cleaned
    }

    private fun cleanOrphanTempFiles(): Int {
        return synchronized(backupManager.operationLock) {
            if (transactionManager.isOperationInProgress()) {
                return@synchronized 0
            }
            var count = 0
            val dirs = listOf(
                backupManager.recordsDir,
                backupManager.blobsDir,
                transactionManager.transactionsDir
            )
            for (dir in dirs) {
                val tmpFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".tmp") } ?: continue
                for (tmp in tmpFiles) {
                    if (tmp.delete()) {
                        count++
                    }
                }
            }
            count
        }
    }
}
