package com.kindle.pageturner.root

import java.security.MessageDigest

/**
 * Strictly whitelisted system paths managed by the Root security module.
 */
enum class ManagedTargetPath(
    val canonicalPath: String,
    val parentDir: String,
    val writable: Boolean,
    val defaultOwnerUid: Int,
    val defaultOwnerUser: String,
    val defaultGroupGid: Int,
    val defaultGroupName: String,
    val defaultModeOctal: String,
    val defaultSelinuxContext: String,
    val description: String
) {
    DATA_BT_DID_CONF(
        canonicalPath = "/data/misc/bluetooth/bt_did.conf",
        parentDir = "/data/misc/bluetooth",
        writable = true,
        defaultOwnerUid = 1002,
        defaultOwnerUser = "bluetooth",
        defaultGroupGid = 1002,
        defaultGroupName = "bluetooth",
        defaultModeOctal = "0660",
        defaultSelinuxContext = "u:object_r:bluetooth_data_file:s0",
        description = "Android 蓝牙运行时 DID 配置文件 (/data 分区)"
    ),
    SYSTEM_BT_DID_CONF(
        canonicalPath = "/system/etc/bluetooth/bt_did.conf",
        parentDir = "/system/etc/bluetooth",
        writable = false,
        defaultOwnerUid = 0,
        defaultOwnerUser = "root",
        defaultGroupGid = 0,
        defaultGroupName = "root",
        defaultModeOctal = "0644",
        defaultSelinuxContext = "u:object_r:system_file:s0",
        description = "Android 系统只读蓝牙 DID 模板文件 (/system 分区，禁止直接写入)"
    );

    companion object {
        fun fromPath(path: String): ManagedTargetPath? {
            return entries.firstOrNull { it.canonicalPath == path }
        }
    }
}

/**
 * Validates and normalizes target file paths against traversal, injection, and whitelist rules.
 */
object RootPathValidator {

    private val FORBIDDEN_CHARS = charArrayOf(
        '\u0000', '\n', '\r', '\t', ';', '|', '&', '$', '`', '\'', '"', '\\', '<', '>', '(', ')', '{', '}', '*', '?', '[', ']', '!'
    )

    fun validatePath(rawPath: String, requireWritable: Boolean = true): Result<ManagedTargetPath> {
        if (rawPath.isBlank()) {
            return Result.failure(IllegalArgumentException("路径不能为空"))
        }
        if (rawPath != rawPath.trim()) {
            return Result.failure(IllegalArgumentException("路径包含首尾空白字符: '$rawPath'"))
        }
        for (ch in FORBIDDEN_CHARS) {
            if (rawPath.indexOf(ch) >= 0) {
                return Result.failure(IllegalArgumentException("路径包含非法或危险特殊字符: '$ch'"))
            }
        }
        if (!rawPath.startsWith("/")) {
            return Result.failure(IllegalArgumentException("禁止使用相对路径，必须为绝对路径: '$rawPath'"))
        }
        if (rawPath.contains("//")) {
            return Result.failure(IllegalArgumentException("路径未规范化 (包含连续斜杠): '$rawPath'"))
        }
        val segments = rawPath.split("/").filter { it.isNotEmpty() }
        if (segments.any { it == "." || it == ".." }) {
            return Result.failure(IllegalArgumentException("检测到路径穿越风险 (包含 '.' 或 '..'): '$rawPath'"))
        }
        val normalized = "/" + segments.joinToString("/")
        if (normalized != rawPath) {
            return Result.failure(IllegalArgumentException("路径未处于规范化形式: '$rawPath'"))
        }

        val managed = ManagedTargetPath.fromPath(normalized)
            ?: return Result.failure(SecurityException("目标路径不在受管理的白名单中: '$rawPath'"))

        if (requireWritable && !managed.writable) {
            return Result.failure(
                SecurityException("目标路径 '$rawPath' 位于只读系统分区 (/system)，禁止直接修改或重挂载分区；请使用受管理的 /data/misc/bluetooth/bt_did.conf")
            )
        }

        return Result.success(managed)
    }

    fun validateStagingPath(stagingPath: String, expectedParentDir: String): Result<String> {
        if (stagingPath.isBlank() || !stagingPath.startsWith("$expectedParentDir/.")) {
            return Result.failure(IllegalArgumentException("临时文件路径必须位于目标目录 '$expectedParentDir' 下并以 '.' 开头: '$stagingPath'"))
        }
        for (ch in FORBIDDEN_CHARS) {
            if (stagingPath.indexOf(ch) >= 0) {
                return Result.failure(IllegalArgumentException("临时文件路径包含非法特殊字符: '$ch'"))
            }
        }
        val segments = stagingPath.split("/").filter { it.isNotEmpty() }
        if (segments.any { it == "." || it == ".." }) {
            return Result.failure(IllegalArgumentException("临时文件路径包含路径穿越段: '$stagingPath'"))
        }
        val fileName = segments.lastOrNull() ?: ""
        if (!fileName.matches(Regex("^\\.[a-zA-Z0-9._-]+$"))) {
            return Result.failure(IllegalArgumentException("临时文件名格式不合法: '$fileName'"))
        }
        return Result.success(stagingPath)
    }
}

enum class FileType(val displayName: String) {
    NOT_EXIST("不存在"),
    REGULAR_FILE("普通文件 (Regular File)"),
    SYMLINK("符号链接 (Symlink - 不支持修改)"),
    DIRECTORY("目录 (Directory - 不支持修改)"),
    OTHER_SPECIAL("特殊文件 (设备/FIFO/Socket - 不支持修改)"),
    UNKNOWN("未知类型")
}

/**
 * Complete snapshot of a system file's metadata.
 */
data class RootFileMetadata(
    val path: String,
    val exists: Boolean,
    val fileType: FileType,
    val sizeBytes: Long = 0L,
    val sha256: String? = null,
    val ownerUid: Int? = null,
    val ownerUser: String? = null,
    val groupGid: Int? = null,
    val groupName: String? = null,
    val permissionsOctal: String? = null,
    val selinuxContext: String? = null,
    val lastModifiedEpochSec: Long? = null,
    val aclAndXattrNote: String = "SELinux security.selinux captured via stat/ls -Z; POSIX ACLs not applicable on Android /data/misc/bluetooth"
) {
    fun toSummaryString(): String {
        if (!exists) return "状态: 不存在 (NOT_EXIST)"
        return "类型: ${fileType.displayName}, 大小: ${sizeBytes}B, 权限: ${permissionsOctal ?: "?"}, " +
            "属主: ${ownerUser ?: ownerUid ?: "?"}:${groupName ?: groupGid ?: "?"}, " +
            "SELinux: ${selinuxContext ?: "?"}, SHA-256: ${sha256?.take(16) ?: "?"}..."
    }

    fun toMap(): Map<String, String> = mapOf(
        "path" to path,
        "exists" to exists.toString(),
        "fileType" to fileType.name,
        "sizeBytes" to sizeBytes.toString(),
        "sha256" to (sha256 ?: ""),
        "ownerUid" to (ownerUid?.toString() ?: ""),
        "ownerUser" to (ownerUser ?: ""),
        "groupGid" to (groupGid?.toString() ?: ""),
        "groupName" to (groupName ?: ""),
        "permissionsOctal" to (permissionsOctal ?: ""),
        "selinuxContext" to (selinuxContext ?: ""),
        "lastModifiedEpochSec" to (lastModifiedEpochSec?.toString() ?: ""),
        "aclAndXattrNote" to aclAndXattrNote
    )

    companion object {
        fun notExist(path: String): RootFileMetadata = RootFileMetadata(
            path = path,
            exists = false,
            fileType = FileType.NOT_EXIST
        )

        fun fromMap(map: Map<String, String>, prefix: String = ""): RootFileMetadata? {
            val path = map["${prefix}path"] ?: return null
            val exists = map["${prefix}exists"]?.toBooleanStrictOrNull() ?: return null
            val fileTypeStr = map["${prefix}fileType"] ?: return null
            val fileType = try { FileType.valueOf(fileTypeStr) } catch (_: Throwable) { return null }
            val sizeBytes = map["${prefix}sizeBytes"]?.toLongOrNull() ?: 0L
            val sha256 = map["${prefix}sha256"]?.takeIf { it.isNotEmpty() }
            val ownerUid = map["${prefix}ownerUid"]?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            val ownerUser = map["${prefix}ownerUser"]?.takeIf { it.isNotEmpty() }
            val groupGid = map["${prefix}groupGid"]?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            val groupName = map["${prefix}groupName"]?.takeIf { it.isNotEmpty() }
            val permissionsOctal = map["${prefix}permissionsOctal"]?.takeIf { it.isNotEmpty() }
            val selinuxContext = map["${prefix}selinuxContext"]?.takeIf { it.isNotEmpty() }
            val lastModifiedEpochSec = map["${prefix}lastModifiedEpochSec"]?.takeIf { it.isNotEmpty() }?.toLongOrNull()
            val aclNote = map["${prefix}aclAndXattrNote"] ?: ""
            return RootFileMetadata(
                path = path,
                exists = exists,
                fileType = fileType,
                sizeBytes = sizeBytes,
                sha256 = sha256,
                ownerUid = ownerUid,
                ownerUser = ownerUser,
                groupGid = groupGid,
                groupName = groupName,
                permissionsOctal = permissionsOctal,
                selinuxContext = selinuxContext,
                lastModifiedEpochSec = lastModifiedEpochSec,
                aclAndXattrNote = aclNote
            )
        }
    }
}

/**
 * Versioned, checksum-protected backup record stored in internal private storage.
 */
data class RootBackupRecord(
    val backupId: String,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val transactionId: String,
    val operationId: String,
    val createdAtEpochMs: Long,
    val appVersion: String,
    val targetPath: String,
    val originalExisted: Boolean,
    val originalMetadata: RootFileMetadata,
    val contentFileName: String?,
    val contentSha256: String?,
    val isPreRestoreSnapshot: Boolean = false,
    val note: String = "",
    val recordChecksumSha256: String = ""
) {
    fun computeChecksum(): String {
        val canonicalPayload = buildString {
            append("v=").append(schemaVersion).append("|")
            append("id=").append(backupId).append("|")
            append("tx=").append(transactionId).append("|")
            append("op=").append(operationId).append("|")
            append("ts=").append(createdAtEpochMs).append("|")
            append("app=").append(appVersion).append("|")
            append("path=").append(targetPath).append("|")
            append("existed=").append(originalExisted).append("|")
            append("metaType=").append(originalMetadata.fileType.name).append("|")
            append("metaSize=").append(originalMetadata.sizeBytes).append("|")
            append("metaSha=").append(originalMetadata.sha256 ?: "").append("|")
            append("metaUid=").append(originalMetadata.ownerUid ?: "").append("|")
            append("metaGid=").append(originalMetadata.groupGid ?: "").append("|")
            append("metaPerm=").append(originalMetadata.permissionsOctal ?: "").append("|")
            append("metaSelinux=").append(originalMetadata.selinuxContext ?: "").append("|")
            append("blob=").append(contentFileName ?: "").append("|")
            append("sha=").append(contentSha256 ?: "").append("|")
            append("snap=").append(isPreRestoreSnapshot)
        }
        return Sha256Utils.sha256Hex(canonicalPayload)
    }

    fun withComputedChecksum(): RootBackupRecord = copy(recordChecksumSha256 = computeChecksum())

    fun toJsonString(): String {
        val map = linkedMapOf<String, String>(
            "backupId" to backupId,
            "schemaVersion" to schemaVersion.toString(),
            "transactionId" to transactionId,
            "operationId" to operationId,
            "createdAtEpochMs" to createdAtEpochMs.toString(),
            "appVersion" to appVersion,
            "targetPath" to targetPath,
            "originalExisted" to originalExisted.toString(),
            "contentFileName" to (contentFileName ?: ""),
            "contentSha256" to (contentSha256 ?: ""),
            "isPreRestoreSnapshot" to isPreRestoreSnapshot.toString(),
            "note" to note,
            "recordChecksumSha256" to recordChecksumSha256
        )
        for ((k, v) in originalMetadata.toMap()) {
            map["meta_$k"] = v
        }
        return SimpleJsonCodec.encodeStringMap(map)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun fromJsonString(json: String): RootBackupRecord? {
            val map = SimpleJsonCodec.decodeStringMap(json) ?: return null
            val backupId = map["backupId"]?.takeIf { it.isNotEmpty() } ?: return null
            val schemaVersion = map["schemaVersion"]?.toIntOrNull() ?: return null
            val transactionId = map["transactionId"] ?: return null
            val operationId = map["operationId"] ?: return null
            val createdAtEpochMs = map["createdAtEpochMs"]?.toLongOrNull() ?: return null
            val appVersion = map["appVersion"] ?: return null
            val targetPath = map["targetPath"]?.takeIf { it.isNotEmpty() } ?: return null
            val originalExisted = map["originalExisted"]?.toBooleanStrictOrNull() ?: return null
            val meta = RootFileMetadata.fromMap(map, prefix = "meta_") ?: return null
            val contentFileName = map["contentFileName"]?.takeIf { it.isNotEmpty() }
            val contentSha256 = map["contentSha256"]?.takeIf { it.isNotEmpty() }
            val isPreRestoreSnapshot = map["isPreRestoreSnapshot"]?.toBooleanStrictOrNull() ?: false
            val note = map["note"] ?: ""
            val recordChecksumSha256 = map["recordChecksumSha256"] ?: return null

            return RootBackupRecord(
                backupId = backupId,
                schemaVersion = schemaVersion,
                transactionId = transactionId,
                operationId = operationId,
                createdAtEpochMs = createdAtEpochMs,
                appVersion = appVersion,
                targetPath = targetPath,
                originalExisted = originalExisted,
                originalMetadata = meta,
                contentFileName = contentFileName,
                contentSha256 = contentSha256,
                isPreRestoreSnapshot = isPreRestoreSnapshot,
                note = note,
                recordChecksumSha256 = recordChecksumSha256
            )
        }
    }
}

/**
 * Transaction lifecycle states covering every critical execution stage.
 */
enum class TransactionPhase(val displayName: String, val isTerminal: Boolean) {
    PREPARED("已准备 (PREPARED)", false),
    BACKUP_VERIFIED("备份已校验 (BACKUP_VERIFIED)", false),
    APPLYING("正在写入 (APPLYING)", false),
    APPLIED("已写入待校验 (APPLIED)", false),
    VERIFYING("正在校验结果 (VERIFYING)", false),
    COMMITTED("已提交完成 (COMMITTED)", true),
    ROLLBACK_REQUIRED("需执行回滚 (ROLLBACK_REQUIRED)", false),
    ROLLING_BACK("正在回滚 (ROLLING_BACK)", false),
    ROLLED_BACK("已安全回滚 (ROLLED_BACK)", true),
    RECOVERY_REQUIRED("待恢复处理 (RECOVERY_REQUIRED)", false)
}

enum class TransactionOperationType(val displayName: String) {
    MODIFY_FILE("修改系统文件"),
    RESTORE_BACKUP("恢复备份文件")
}

/**
 * Persistent transaction journal record.
 */
data class RootTransactionRecord(
    val transactionId: String,
    val operationId: String,
    val operationType: TransactionOperationType,
    val targetPath: String,
    val backupId: String,
    val preRestoreSnapshotId: String? = null,
    val phase: TransactionPhase,
    val startedAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val plannedActionSummary: String,
    val preModifyMetadata: RootFileMetadata,
    val expectedExistAfter: Boolean,
    val expectedAfterSha256: String?,
    val actualAfterMetadata: RootFileMetadata? = null,
    val verificationPassed: Boolean = false,
    val partialRestoreWarning: String? = null,
    val errorMessage: String? = null,
    val recoveryResolution: String? = null,
    val recordChecksumSha256: String = ""
) {
    fun computeChecksum(): String {
        val payload = buildString {
            append("tx=").append(transactionId).append("|")
            append("op=").append(operationId).append("|")
            append("type=").append(operationType.name).append("|")
            append("path=").append(targetPath).append("|")
            append("bak=").append(backupId).append("|")
            append("snap=").append(preRestoreSnapshotId ?: "").append("|")
            append("phase=").append(phase.name).append("|")
            append("start=").append(startedAtEpochMs).append("|")
            append("expExist=").append(expectedExistAfter).append("|")
            append("expSha=").append(expectedAfterSha256 ?: "").append("|")
            append("afterExist=").append(actualAfterMetadata?.exists ?: "").append("|")
            append("afterSha=").append(actualAfterMetadata?.sha256 ?: "").append("|")
            append("ver=").append(verificationPassed).append("|")
            append("warn=").append(partialRestoreWarning ?: "").append("|")
            append("err=").append(errorMessage ?: "").append("|")
            append("res=").append(recoveryResolution ?: "")
        }
        return Sha256Utils.sha256Hex(payload)
    }

    fun hasValidChecksum(): Boolean {
        return recordChecksumSha256.isNotEmpty() && computeChecksum() == recordChecksumSha256
    }

    fun withComputedChecksum(): RootTransactionRecord = copy(recordChecksumSha256 = computeChecksum())

    fun toJsonString(): String {
        val map = linkedMapOf<String, String>(
            "transactionId" to transactionId,
            "operationId" to operationId,
            "operationType" to operationType.name,
            "targetPath" to targetPath,
            "backupId" to backupId,
            "preRestoreSnapshotId" to (preRestoreSnapshotId ?: ""),
            "phase" to phase.name,
            "startedAtEpochMs" to startedAtEpochMs.toString(),
            "updatedAtEpochMs" to updatedAtEpochMs.toString(),
            "completedAtEpochMs" to (completedAtEpochMs?.toString() ?: ""),
            "plannedActionSummary" to plannedActionSummary,
            "expectedExistAfter" to expectedExistAfter.toString(),
            "expectedAfterSha256" to (expectedAfterSha256 ?: ""),
            "verificationPassed" to verificationPassed.toString(),
            "partialRestoreWarning" to (partialRestoreWarning ?: ""),
            "errorMessage" to (errorMessage ?: ""),
            "recoveryResolution" to (recoveryResolution ?: ""),
            "hasActualAfter" to (actualAfterMetadata != null).toString(),
            "recordChecksumSha256" to recordChecksumSha256
        )
        for ((k, v) in preModifyMetadata.toMap()) {
            map["pre_$k"] = v
        }
        if (actualAfterMetadata != null) {
            for ((k, v) in actualAfterMetadata.toMap()) {
                map["after_$k"] = v
            }
        }
        return SimpleJsonCodec.encodeStringMap(map)
    }

    companion object {
        fun fromJsonString(json: String, verifyChecksum: Boolean = true): RootTransactionRecord? {
            val map = SimpleJsonCodec.decodeStringMap(json) ?: return null
            val transactionId = map["transactionId"]?.takeIf { it.isNotEmpty() } ?: return null
            val operationId = map["operationId"] ?: return null
            val opTypeStr = map["operationType"] ?: return null
            val opType = try { TransactionOperationType.valueOf(opTypeStr) } catch (_: Throwable) { return null }
            val targetPath = map["targetPath"]?.takeIf { it.isNotEmpty() } ?: return null
            val backupId = map["backupId"]?.takeIf { it.isNotEmpty() } ?: return null
            val preRestoreSnapshotId = map["preRestoreSnapshotId"]?.takeIf { it.isNotEmpty() }
            val phaseStr = map["phase"] ?: return null
            val phase = try { TransactionPhase.valueOf(phaseStr) } catch (_: Throwable) { return null }
            val startedAt = map["startedAtEpochMs"]?.toLongOrNull() ?: return null
            val updatedAt = map["updatedAtEpochMs"]?.toLongOrNull() ?: return null
            val completedAt = map["completedAtEpochMs"]?.takeIf { it.isNotEmpty() }?.toLongOrNull()
            val plannedActionSummary = map["plannedActionSummary"] ?: ""
            val preMeta = RootFileMetadata.fromMap(map, prefix = "pre_") ?: return null
            val expectedExistAfter = map["expectedExistAfter"]?.toBooleanStrictOrNull() ?: return null
            val expectedAfterSha256 = map["expectedAfterSha256"]?.takeIf { it.isNotEmpty() }
            val hasActualAfter = map["hasActualAfter"]?.toBooleanStrictOrNull() ?: false
            val afterMeta = if (hasActualAfter) RootFileMetadata.fromMap(map, prefix = "after_") else null
            val verificationPassed = map["verificationPassed"]?.toBooleanStrictOrNull() ?: false
            val partialRestoreWarning = map["partialRestoreWarning"]?.takeIf { it.isNotEmpty() }
            val errorMessage = map["errorMessage"]?.takeIf { it.isNotEmpty() }
            val recoveryResolution = map["recoveryResolution"]?.takeIf { it.isNotEmpty() }
            val checksum = map["recordChecksumSha256"]?.takeIf { it.isNotEmpty() } ?: return null

            val record = RootTransactionRecord(
                transactionId = transactionId,
                operationId = operationId,
                operationType = opType,
                targetPath = targetPath,
                backupId = backupId,
                preRestoreSnapshotId = preRestoreSnapshotId,
                phase = phase,
                startedAtEpochMs = startedAt,
                updatedAtEpochMs = updatedAt,
                completedAtEpochMs = completedAt,
                plannedActionSummary = plannedActionSummary,
                preModifyMetadata = preMeta,
                expectedExistAfter = expectedExistAfter,
                expectedAfterSha256 = expectedAfterSha256,
                actualAfterMetadata = afterMeta,
                verificationPassed = verificationPassed,
                partialRestoreWarning = partialRestoreWarning,
                errorMessage = errorMessage,
                recoveryResolution = recoveryResolution,
                recordChecksumSha256 = checksum
            )
            if (verifyChecksum && !record.hasValidChecksum()) {
                return null
            }
            return record
        }
    }
}

/**
 * Pure Kotlin SHA-256 helper.
 */
object Sha256Utils {
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))
}

/**
 * Pure-Kotlin deterministic JSON string-map encoder/decoder (zero external / Android stub dependencies).
 */
object SimpleJsonCodec {

    fun encodeStringMap(map: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        val entries = map.entries.toList()
        for (i in entries.indices) {
            val (k, v) = entries[i]
            sb.append("  \"").append(escapeJson(k)).append("\": \"").append(escapeJson(v)).append("\"")
            if (i < entries.size - 1) {
                sb.append(",")
            }
            sb.append("\n")
        }
        sb.append("}")
        return sb.toString()
    }

    fun decodeStringMap(json: String): Map<String, String>? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val result = linkedMapOf<String, String>()
        var idx = 1
        val end = trimmed.length - 1

        fun skipWhitespace() {
            while (idx < end && trimmed[idx].isWhitespace()) idx++
        }

        fun readQuotedString(): String? {
            skipWhitespace()
            if (idx >= end || trimmed[idx] != '"') return null
            idx++ // skip opening quote
            val sb = StringBuilder()
            while (idx < end) {
                val ch = trimmed[idx++]
                if (ch == '"') {
                    return sb.toString()
                } else if (ch == '\\') {
                    if (idx >= end) return null
                    when (val esc = trimmed[idx++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (idx + 4 > end) return null
                            val hex = trimmed.substring(idx, idx + 4)
                            val code = hex.toIntOrNull(16) ?: return null
                            sb.append(code.toChar())
                            idx += 4
                        }
                        else -> return null
                    }
                } else {
                    sb.append(ch)
                }
            }
            return null
        }

        skipWhitespace()
        if (idx >= end) return result

        while (idx < end) {
            val key = readQuotedString() ?: return null
            skipWhitespace()
            if (idx >= end || trimmed[idx] != ':') return null
            idx++ // skip ':'
            val value = readQuotedString() ?: return null
            result[key] = value
            skipWhitespace()
            if (idx < end) {
                if (trimmed[idx] == ',') {
                    idx++
                    skipWhitespace()
                    if (idx >= end) return null // trailing comma not allowed
                } else {
                    return null
                }
            }
        }
        return result
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append(String.format("\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }
}
