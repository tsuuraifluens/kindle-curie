package com.kindle.pageturner.root

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

sealed class BackupVerificationResult {
    data class Valid(
        val record: RootBackupRecord,
        val contentBytes: ByteArray?
    ) : BackupVerificationResult()

    data class Invalid(
        val backupId: String,
        val reason: String
    ) : BackupVerificationResult()
}

data class BackupCleanupResult(
    val deletedBackupIds: List<String>,
    val retainedBackupIds: List<String>
)

/**
 * Manages creation, integrity verification, listing, export, and safe cleanup of
 * pre-modification system file backups in private persistent internal storage.
 */
class RootBackupManager(
    storageRootDir: File,
    private val inspector: RootFileInspector,
    private val auditLogger: RootAuditLogger,
    private val appVersion: String = "1.0.0"
) {
    val backupRootDir = File(storageRootDir, "root_backups")
    val recordsDir = File(backupRootDir, "records")
    val blobsDir = File(backupRootDir, "blobs")

    private val lock = Any()
    private val seqCounter = AtomicLong(0L)

    /**
     * Optional test hook allowing fault injection during backup blob writing.
     */
    var testBlobTransformHook: ((ByteArray) -> ByteArray)? = null

    init {
        recordsDir.mkdirs()
        blobsDir.mkdirs()
    }

    /**
     * Inspects [targetPath], reads its content and metadata, writes a backup to private storage,
     * and re-reads/verifies SHA-256 and checksum before marking the backup as valid.
     */
    fun createAndVerifyBackup(
        targetPath: String,
        transactionId: String,
        operationId: String,
        isPreRestoreSnapshot: Boolean = false,
        note: String = "",
        customBackupId: String? = null
    ): Result<RootBackupRecord> {
        synchronized(lock) {
            val pathCheck = RootPathValidator.validatePath(targetPath, requireWritable = false)
            if (pathCheck.isFailure) {
                val err = pathCheck.exceptionOrNull()?.message ?: "路径校验失败"
                auditLogger.record("BACKUP_CREATE", "FAILURE", "拒绝为非法路径创建备份: $err", targetPath, transactionId)
                return Result.failure(pathCheck.exceptionOrNull()!!)
            }

            if (!recordsDir.exists() && !recordsDir.mkdirs()) {
                return Result.failure(IOException("无法创建备份记录目录: ${recordsDir.absolutePath}"))
            }
            if (!blobsDir.exists() && !blobsDir.mkdirs()) {
                return Result.failure(IOException("无法创建备份数据目录: ${blobsDir.absolutePath}"))
            }
            if (!recordsDir.isDirectory || !blobsDir.isDirectory) {
                return Result.failure(IOException("备份存储路径不是有效目录"))
            }

            val backupId = customBackupId ?: generateUniqueBackupId()
            if (!backupId.matches(Regex("^[a-zA-Z0-9_-]{4,80}$"))) {
                return Result.failure(IllegalArgumentException("非法备份 ID 格式: '$backupId'"))
            }

            val finalRecordFile = File(recordsDir, "$backupId.json")
            val finalBlobFile = File(blobsDir, "$backupId.bin")
            if (finalRecordFile.exists() || finalBlobFile.exists()) {
                val msg = "备份 ID 冲突，禁止覆盖已有备份: $backupId"
                auditLogger.record("BACKUP_CREATE", "FAILURE", msg, targetPath, transactionId, backupId)
                return Result.failure(IllegalStateException(msg))
            }

            // 1. Inspect target file metadata
            val metaRes = inspector.inspectFile(targetPath)
            if (metaRes.isFailure) {
                val msg = "读取目标文件元数据失败: ${metaRes.exceptionOrNull()?.message}"
                auditLogger.record("BACKUP_CREATE", "FAILURE", msg, targetPath, transactionId, backupId)
                return Result.failure(IllegalStateException(msg, metaRes.exceptionOrNull()))
            }
            val meta = metaRes.getOrThrow()

            // 2. Reject unsupported file types (Symlink, Directory, Special device)
            if (meta.fileType != FileType.REGULAR_FILE && meta.fileType != FileType.NOT_EXIST) {
                val msg = "目标路径文件类型不受支持 (${meta.fileType.displayName})，拒绝备份或修改"
                auditLogger.record("BACKUP_CREATE", "FAILURE", msg, targetPath, transactionId, backupId)
                return Result.failure(IllegalStateException(msg))
            }

            var contentFileName: String? = null
            var contentSha256: String? = null

            try {
                if (meta.exists && meta.fileType == FileType.REGULAR_FILE) {
                    // Verify metadata completeness for existing file
                    if (meta.sha256.isNullOrBlank() ||
                        meta.permissionsOctal.isNullOrBlank() ||
                        meta.ownerUid == null ||
                        meta.groupGid == null
                    ) {
                        val msg = "目标文件元数据不完整 (缺失哈希/权限/UID/GID)，拒绝创建备份"
                        auditLogger.record("BACKUP_CREATE", "FAILURE", msg, targetPath, transactionId, backupId)
                        return Result.failure(IllegalStateException(msg))
                    }

                    // Read raw bytes of original file
                    val bytesRes = inspector.readFileBytes(targetPath)
                    if (bytesRes.isFailure) {
                        val msg = "读取原始文件内容失败: ${bytesRes.exceptionOrNull()?.message}"
                        auditLogger.record("BACKUP_CREATE", "FAILURE", msg, targetPath, transactionId, backupId)
                        return Result.failure(IllegalStateException(msg, bytesRes.exceptionOrNull()))
                    }
                    val rawBytes = bytesRes.getOrThrow()
                    val readSha256 = Sha256Utils.sha256Hex(rawBytes)
                    if (readSha256 != meta.sha256 || rawBytes.size.toLong() != meta.sizeBytes) {
                        val msg = "读取文件期间内容发生变化 (stat SHA=${meta.sha256}, read SHA=$readSha256)，请重试"
                        auditLogger.record("BACKUP_CREATE", "FAILURE", msg, targetPath, transactionId, backupId)
                        return Result.failure(IllegalStateException(msg))
                    }

                    val bytesToWrite = testBlobTransformHook?.invoke(rawBytes) ?: rawBytes
                    val tmpBlobFile = File(blobsDir, "$backupId.bin.tmp")
                    writeBytesAndSync(tmpBlobFile, bytesToWrite)

                    // Immediately re-read tmp blob and verify SHA-256 matches original file hash
                    val verifyBytes = tmpBlobFile.readBytes()
                    val verifySha = Sha256Utils.sha256Hex(verifyBytes)
                    if (verifySha != meta.sha256 || verifyBytes.size.toLong() != meta.sizeBytes) {
                        tmpBlobFile.delete()
                        val msg = "备份文件写入后完整性校验失败 (预期 SHA=${meta.sha256}, 实际=$verifySha)"
                        auditLogger.record("BACKUP_CREATE", "FAILURE", msg, targetPath, transactionId, backupId)
                        return Result.failure(IllegalStateException(msg))
                    }

                    if (!atomicRenameLocal(tmpBlobFile, finalBlobFile)) {
                        tmpBlobFile.delete()
                        return Result.failure(IOException("无法原子提交备份内容文件: ${finalBlobFile.name}"))
                    }

                    contentFileName = finalBlobFile.name
                    contentSha256 = meta.sha256
                }

                val record = RootBackupRecord(
                    backupId = backupId,
                    schemaVersion = RootBackupRecord.CURRENT_SCHEMA_VERSION,
                    transactionId = transactionId,
                    operationId = operationId,
                    createdAtEpochMs = System.currentTimeMillis(),
                    appVersion = appVersion,
                    targetPath = targetPath,
                    originalExisted = meta.exists,
                    originalMetadata = meta,
                    contentFileName = contentFileName,
                    contentSha256 = contentSha256,
                    isPreRestoreSnapshot = isPreRestoreSnapshot,
                    note = note
                ).withComputedChecksum()

                val tmpRecordFile = File(recordsDir, "$backupId.json.tmp")
                writeBytesAndSync(tmpRecordFile, record.toJsonString().toByteArray(Charsets.UTF_8))

                // Re-read and verify metadata record before committing
                val reReadRecord = RootBackupRecord.fromJsonString(tmpRecordFile.readText(Charsets.UTF_8))
                if (reReadRecord == null || reReadRecord.computeChecksum() != record.recordChecksumSha256) {
                    tmpRecordFile.delete()
                    finalBlobFile.delete()
                    return Result.failure(IllegalStateException("备份元数据记录写盘校验失败"))
                }

                if (!atomicRenameLocal(tmpRecordFile, finalRecordFile)) {
                    tmpRecordFile.delete()
                    finalBlobFile.delete()
                    return Result.failure(IOException("无法原子提交备份元数据文件: ${finalRecordFile.name}"))
                }

                // Final full verification pass
                when (val verification = verifyBackup(backupId)) {
                    is BackupVerificationResult.Valid -> {
                        auditLogger.record(
                            action = "BACKUP_VERIFIED",
                            outcome = "SUCCESS",
                            summary = "备份创建并验证成功 (originalExisted=${meta.exists}, sha256=${contentSha256 ?: "NOT_EXIST"})",
                            targetPath = targetPath,
                            transactionId = transactionId,
                            backupId = backupId
                        )
                        return Result.success(verification.record)
                    }
                    is BackupVerificationResult.Invalid -> {
                        finalRecordFile.delete()
                        finalBlobFile.delete()
                        auditLogger.record(
                            action = "BACKUP_VERIFY",
                            outcome = "FAILURE",
                            summary = "新创建的备份未通过完整性验证: ${verification.reason}",
                            targetPath = targetPath,
                            transactionId = transactionId,
                            backupId = backupId
                        )
                        return Result.failure(IllegalStateException("备份完整性校验未通过: ${verification.reason}"))
                    }
                }
            } catch (t: Throwable) {
                File(recordsDir, "$backupId.json.tmp").delete()
                File(blobsDir, "$backupId.bin.tmp").delete()
                finalRecordFile.delete()
                finalBlobFile.delete()
                auditLogger.record(
                    action = "BACKUP_CREATE",
                    outcome = "FAILURE",
                    summary = "备份创建异常: ${t.message}",
                    targetPath = targetPath,
                    transactionId = transactionId,
                    backupId = backupId
                )
                return Result.failure(t)
            }
        }
    }

    /**
     * Re-reads backup metadata and content bytes from disk, recomputes SHA-256 and record checksum,
     * and validates schema compatibility. Never overwrites stored hashes on mismatch.
     */
    fun verifyBackup(backupId: String): BackupVerificationResult {
        synchronized(lock) {
            if (!backupId.matches(Regex("^[a-zA-Z0-9_-]{4,80}$"))) {
                return BackupVerificationResult.Invalid(backupId, "非法备份 ID 格式")
            }
            val recordFile = File(recordsDir, "$backupId.json")
            if (!recordFile.exists() || !recordFile.isFile) {
                return BackupVerificationResult.Invalid(backupId, "备份元数据文件不存在: ${recordFile.name}")
            }

            val jsonText = try {
                recordFile.readText(Charsets.UTF_8)
            } catch (t: Throwable) {
                return BackupVerificationResult.Invalid(backupId, "读取备份元数据失败: ${t.message}")
            }

            val record = RootBackupRecord.fromJsonString(jsonText)
                ?: return BackupVerificationResult.Invalid(backupId, "备份元数据格式损坏，无法解析")

            if (record.backupId != backupId) {
                return BackupVerificationResult.Invalid(backupId, "备份记录内部 ID 与文件名不一致")
            }

            if (record.schemaVersion != RootBackupRecord.CURRENT_SCHEMA_VERSION) {
                return BackupVerificationResult.Invalid(
                    backupId,
                    "备份版本不兼容: 记录版本=${record.schemaVersion}, 当前支持版本=${RootBackupRecord.CURRENT_SCHEMA_VERSION}"
                )
            }

            val recomputedChecksum = record.computeChecksum()
            if (recomputedChecksum != record.recordChecksumSha256) {
                return BackupVerificationResult.Invalid(
                    backupId,
                    "备份元数据校验和 (Checksum) 不匹配，记录可能被篡改或损坏"
                )
            }

            val pathCheck = RootPathValidator.validatePath(record.targetPath, requireWritable = false)
            if (pathCheck.isFailure) {
                return BackupVerificationResult.Invalid(
                    backupId,
                    "备份目标路径不合法或不在白名单中: ${record.targetPath}"
                )
            }

            if (!record.originalExisted) {
                if (record.originalMetadata.exists || record.originalMetadata.fileType != FileType.NOT_EXIST) {
                    return BackupVerificationResult.Invalid(backupId, "不存在状态备份的元数据字段矛盾")
                }
                if (record.contentSha256 != null || record.contentFileName != null) {
                    return BackupVerificationResult.Invalid(backupId, "不存在状态备份不应包含内容文件引用")
                }
                return BackupVerificationResult.Valid(record, null)
            }

            // Original file existed -> verify metadata completeness and blob SHA-256
            if (record.originalMetadata.fileType != FileType.REGULAR_FILE) {
                return BackupVerificationResult.Invalid(backupId, "备份记录的文件类型不受支持: ${record.originalMetadata.fileType}")
            }
            val expectedSha = record.contentSha256
            if (expectedSha.isNullOrBlank() || expectedSha != record.originalMetadata.sha256) {
                return BackupVerificationResult.Invalid(backupId, "备份记录缺少有效的原始 SHA-256 或与元数据不一致")
            }
            if (record.originalMetadata.permissionsOctal.isNullOrBlank() ||
                record.originalMetadata.ownerUid == null ||
                record.originalMetadata.groupGid == null
            ) {
                return BackupVerificationResult.Invalid(backupId, "备份记录缺少必要的权限或属主元数据")
            }
            val blobName = record.contentFileName
            if (blobName.isNullOrBlank() || blobName.contains("/") || blobName.contains("\\") || blobName.contains("..")) {
                return BackupVerificationResult.Invalid(backupId, "备份内容文件名非法: '$blobName'")
            }

            val blobFile = File(blobsDir, blobName)
            if (!blobFile.exists() || !blobFile.isFile) {
                return BackupVerificationResult.Invalid(backupId, "备份数据文件缺失: $blobName")
            }

            val blobBytes = try {
                blobFile.readBytes()
            } catch (t: Throwable) {
                return BackupVerificationResult.Invalid(backupId, "读取备份数据文件失败: ${t.message}")
            }

            if (blobBytes.size.toLong() != record.originalMetadata.sizeBytes) {
                return BackupVerificationResult.Invalid(
                    backupId,
                    "备份数据大小不匹配: 预期 ${record.originalMetadata.sizeBytes} 字节, 实际 ${blobBytes.size} 字节"
                )
            }

            val actualSha = Sha256Utils.sha256Hex(blobBytes)
            if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                return BackupVerificationResult.Invalid(
                    backupId,
                    "备份内容 SHA-256 校验失败: 预期 $expectedSha, 实际 $actualSha (备份内容已被篡改或损坏)"
                )
            }

            return BackupVerificationResult.Valid(record, blobBytes)
        }
    }

    /**
     * Lists all parsed backup records, sorted newest first.
     */
    fun listBackups(targetPath: String? = null): List<RootBackupRecord> {
        synchronized(lock) {
            val files = recordsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
            val list = mutableListOf<RootBackupRecord>()
            for (f in files) {
                val rec = try {
                    RootBackupRecord.fromJsonString(f.readText(Charsets.UTF_8))
                } catch (_: Throwable) {
                    null
                }
                if (rec != null && (targetPath == null || rec.targetPath == targetPath)) {
                    list.add(rec)
                }
            }
            return list.sortedWith(compareByDescending<RootBackupRecord> { it.createdAtEpochMs }.thenByDescending { it.backupId })
        }
    }

    /**
     * Finds the earliest verified non-snapshot backup for [targetPath] (representing the baseline
     * original state before modifications), or falls back to the latest valid backup.
     */
    fun getBaselineOriginalBackup(targetPath: String): BackupVerificationResult.Valid? {
        synchronized(lock) {
            val allForTarget = listBackups(targetPath)
            val validNonSnapshots = allForTarget
                .filter { !it.isPreRestoreSnapshot }
                .sortedWith(compareBy<RootBackupRecord> { it.createdAtEpochMs }.thenBy { it.backupId })
                .mapNotNull { verifyBackup(it.backupId) as? BackupVerificationResult.Valid }

            if (validNonSnapshots.isNotEmpty()) {
                return validNonSnapshots.first()
            }
            return allForTarget
                .sortedWith(compareBy<RootBackupRecord> { it.createdAtEpochMs }.thenBy { it.backupId })
                .mapNotNull { verifyBackup(it.backupId) as? BackupVerificationResult.Valid }
                .firstOrNull()
        }
    }

    /**
     * Returns the most recent verified backup for [targetPath].
     */
    fun getLatestValidBackup(targetPath: String): BackupVerificationResult.Valid? {
        synchronized(lock) {
            for (rec in listBackups(targetPath)) {
                val res = verifyBackup(rec.backupId)
                if (res is BackupVerificationResult.Valid) {
                    return res
                }
            }
            return null
        }
    }

    /**
     * Safely cleans up old redundant backups while strictly preserving:
     * 1. Any backup referenced by an active/unfinished transaction ([protectedBackupIds]).
     * 2. The earliest valid baseline backup for each target path.
     * 3. The [keepRecentCount] most recent backups per target path.
     */
    fun cleanupBackups(
        protectedBackupIds: Set<String>,
        keepRecentCount: Int = 5
    ): BackupCleanupResult {
        synchronized(lock) {
            val allBackups = listBackups()
            val byPath = allBackups.groupBy { it.targetPath }
            val mustKeep = mutableSetOf<String>()
            mustKeep.addAll(protectedBackupIds)

            for ((path, records) in byPath) {
                val baseline = getBaselineOriginalBackup(path)
                if (baseline != null) {
                    mustKeep.add(baseline.record.backupId)
                }
                records.take(keepRecentCount.coerceAtLeast(1)).forEach {
                    mustKeep.add(it.backupId)
                }
            }

            val deleted = mutableListOf<String>()
            val retained = mutableListOf<String>()

            for (rec in allBackups) {
                if (rec.backupId in mustKeep) {
                    retained.add(rec.backupId)
                } else {
                    val recFile = File(recordsDir, "${rec.backupId}.json")
                    val blobFile = rec.contentFileName?.let { File(blobsDir, it) }
                    recFile.delete()
                    blobFile?.delete()
                    deleted.add(rec.backupId)
                }
            }

            if (deleted.isNotEmpty()) {
                auditLogger.record(
                    action = "BACKUP_CLEANUP",
                    outcome = "SUCCESS",
                    summary = "清理旧备份 ${deleted.size} 个，保留受保护及关键备份 ${retained.size} 个",
                    details = "deleted=$deleted, retained=$retained"
                )
            }
            return BackupCleanupResult(deletedBackupIds = deleted, retainedBackupIds = retained)
        }
    }

    /**
     * Exports a verified backup into a self-contained text bundle with security disclosure.
     */
    fun exportBackupBundle(backupId: String): Result<String> {
        synchronized(lock) {
            return when (val verification = verifyBackup(backupId)) {
                is BackupVerificationResult.Invalid -> {
                    Result.failure(IllegalStateException("无法导出无效或损坏的备份 ($backupId): ${verification.reason}"))
                }
                is BackupVerificationResult.Valid -> {
                    val rec = verification.record
                    val base64Content = verification.contentBytes?.let { Base64.getEncoder().encodeToString(it) } ?: ""
                    val exportMap = linkedMapOf(
                        "SECURITY_NOTICE" to "警告：此导出文件包含 Android 系统蓝牙配置文件备份及元数据。请妥善保管，切勿在不可信渠道公开分享或允许未授权应用篡改。",
                        "exportFormatVersion" to "1",
                        "exportedAtEpochMs" to System.currentTimeMillis().toString(),
                        "backupRecordJson" to rec.toJsonString(),
                        "contentBase64" to base64Content,
                        "contentSha256" to (rec.contentSha256 ?: "NOT_EXIST")
                    )
                    auditLogger.record(
                        action = "BACKUP_EXPORT",
                        outcome = "SUCCESS",
                        summary = "导出独立备份副本 ($backupId)",
                        targetPath = rec.targetPath,
                        backupId = backupId
                    )
                    Result.success(SimpleJsonCodec.encodeStringMap(exportMap))
                }
            }
        }
    }

    val operationLock: Any
        get() = lock

    fun allocateBackupId(): String = generateUniqueBackupId()

    private fun generateUniqueBackupId(): String {
        val ts = System.currentTimeMillis()
        val seq = String.format(java.util.Locale.US, "%06d", seqCounter.incrementAndGet())
        val shortUuid = UUID.randomUUID().toString().replace("-", "").take(8)
        return "bak_${ts}_${seq}_$shortUuid"
    }

    private fun writeBytesAndSync(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { fos ->
            fos.write(bytes)
            fos.flush()
            try {
                fos.fd.sync()
            } catch (_: Throwable) {}
        }
    }

    private fun atomicRenameLocal(from: File, to: File): Boolean {
        if (to.exists()) {
            to.delete()
        }
        return from.renameTo(to)
    }
}
