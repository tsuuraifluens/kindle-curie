package com.kindle.pageturner.root

import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class RootAuditEntry(
    val entryId: Long,
    val timestampEpochMs: Long,
    val action: String,
    val outcome: String,
    val targetPath: String? = null,
    val transactionId: String? = null,
    val backupId: String? = null,
    val summary: String,
    val details: String? = null
) {
    fun toJsonLine(): String {
        val map = linkedMapOf(
            "entryId" to entryId.toString(),
            "timestampEpochMs" to timestampEpochMs.toString(),
            "action" to action,
            "outcome" to outcome,
            "targetPath" to (targetPath ?: ""),
            "transactionId" to (transactionId ?: ""),
            "backupId" to (backupId ?: ""),
            "summary" to summary,
            "details" to (details ?: "")
        )
        return SimpleJsonCodec.encodeStringMap(map).replace("\n", " ")
    }

    fun toFormattedText(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val ts = sdf.format(Date(timestampEpochMs))
        val txPart = if (!transactionId.isNullOrEmpty()) " [tx=$transactionId]" else ""
        val bakPart = if (!backupId.isNullOrEmpty()) " [bak=$backupId]" else ""
        val pathPart = if (!targetPath.isNullOrEmpty()) " ($targetPath)" else ""
        val detailPart = if (!details.isNullOrEmpty()) " -> $details" else ""
        return "[$ts] [$outcome] [$action]$txPart$bakPart$pathPart: $summary$detailPart"
    }

    companion object {
        fun fromJsonLine(line: String): RootAuditEntry? {
            val map = SimpleJsonCodec.decodeStringMap(line) ?: return null
            val entryId = map["entryId"]?.toLongOrNull() ?: return null
            val ts = map["timestampEpochMs"]?.toLongOrNull() ?: return null
            val action = map["action"] ?: return null
            val outcome = map["outcome"] ?: return null
            val targetPath = map["targetPath"]?.takeIf { it.isNotEmpty() }
            val transactionId = map["transactionId"]?.takeIf { it.isNotEmpty() }
            val backupId = map["backupId"]?.takeIf { it.isNotEmpty() }
            val summary = map["summary"] ?: ""
            val details = map["details"]?.takeIf { it.isNotEmpty() }
            return RootAuditEntry(
                entryId = entryId,
                timestampEpochMs = ts,
                action = action,
                outcome = outcome,
                targetPath = targetPath,
                transactionId = transactionId,
                backupId = backupId,
                summary = summary,
                details = details
            )
        }
    }
}

/**
 * Persistent audit logger for Root file operations, backup checks, and recovery history.
 * Stored in `<storageRootDir>/root_audit/audit_log.jsonl` and separated from normal volatile log clearing.
 */
class RootAuditLogger(
    storageRootDir: File
) {
    private val auditDir = File(storageRootDir, "root_audit")
    private val auditFile = File(auditDir, "audit_log.jsonl")
    private val lock = Any()
    private val idCounter = AtomicLong(System.currentTimeMillis())

    init {
        if (!auditDir.exists()) {
            auditDir.mkdirs()
        }
    }

    fun record(
        action: String,
        outcome: String,
        summary: String,
        targetPath: String? = null,
        transactionId: String? = null,
        backupId: String? = null,
        details: String? = null
    ): Result<RootAuditEntry> {
        val entry = RootAuditEntry(
            entryId = idCounter.incrementAndGet(),
            timestampEpochMs = System.currentTimeMillis(),
            action = action,
            outcome = outcome,
            targetPath = targetPath,
            transactionId = transactionId,
            backupId = backupId,
            summary = summary,
            details = details
        )

        val diagMsg = "[RootAudit:$action:$outcome] $summary"
        when (outcome) {
            "FAILURE" -> DiagnosticLogger.w(LogCategory.SYSTEM, diagMsg, details)
            else -> DiagnosticLogger.i(LogCategory.SYSTEM, diagMsg, details)
        }

        return synchronized(lock) {
            try {
                if (!auditDir.exists() && !auditDir.mkdirs()) {
                    return@synchronized Result.failure(IllegalStateException("无法创建审计日志目录: ${auditDir.absolutePath}"))
                }
                val lineBytes = (entry.toJsonLine() + "\n").toByteArray(Charsets.UTF_8)
                FileOutputStream(auditFile, true).use { fos ->
                    fos.write(lineBytes)
                    fos.flush()
                    try {
                        fos.fd.sync()
                    } catch (_: Throwable) {}
                }
                Result.success(entry)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    fun readEntries(limit: Int = 200): List<RootAuditEntry> {
        return synchronized(lock) {
            if (!auditFile.exists() || !auditFile.isFile) return@synchronized emptyList()
            try {
                val lines = auditFile.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
                val parsed = lines.mapNotNull { RootAuditEntry.fromJsonLine(it) }
                if (parsed.size <= limit) parsed else parsed.takeLast(limit)
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    fun exportAuditReportText(): String {
        val entries = readEntries(500)
        val sb = StringBuilder()
        sb.append("=== Kindle Page Turner Root 安全审计与操作历史 ===\n")
        sb.append("导出时间: ${Date()}\n")
        sb.append("记录总数: ${entries.size}\n\n")
        if (entries.isEmpty()) {
            sb.append("(暂无 Root 操作记录)\n")
        } else {
            for (entry in entries) {
                sb.append(entry.toFormattedText()).append("\n")
            }
        }
        return sb.toString()
    }
}
