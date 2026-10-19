package com.kindle.pageturner.diagnostics

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe Diagnostic Logger with ring buffer and reactive StateFlow for live UI observation.
 */
object DiagnosticLogger {

    private const val TAG = "KindleTurnerDiag"
    private const val MAX_LOG_CAPACITY = 600

    private val idCounter = AtomicLong(0)
    private val lock = Any()
    private val buffer = ArrayList<LogEntry>(MAX_LOG_CAPACITY)

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    @Volatile
    var minLogLevel: LogLevel = LogLevel.DEBUG

    fun log(level: LogLevel, category: LogCategory, message: String, details: String? = null) {
        if (level.ordinal < minLogLevel.ordinal) {
            return
        }
        val entry = LogEntry(
            id = idCounter.incrementAndGet(),
            level = level,
            category = category,
            message = message,
            details = details
        )

        // Write to Android logcat (with fallback for pure JVM tests)
        val line = "[${category.name}] $message${if (details != null) " ($details)" else ""}"
        try {
            when (level) {
                LogLevel.DEBUG -> Log.d(TAG, line)
                LogLevel.INFO -> Log.i(TAG, line)
                LogLevel.WARN -> Log.w(TAG, line)
                LogLevel.ERROR -> Log.e(TAG, line)
            }
        } catch (_: RuntimeException) {
            println(line)
        }

        // Add to ring buffer and update StateFlow
        val updatedList: List<LogEntry>
        synchronized(lock) {
            if (buffer.size >= MAX_LOG_CAPACITY) {
                buffer.removeAt(0)
            }
            buffer.add(entry)
            updatedList = ArrayList(buffer)
        }
        _logsFlow.value = updatedList
    }

    fun d(category: LogCategory, message: String, details: String? = null) =
        log(LogLevel.DEBUG, category, message, details)

    fun i(category: LogCategory, message: String, details: String? = null) =
        log(LogLevel.INFO, category, message, details)

    fun w(category: LogCategory, message: String, details: String? = null) =
        log(LogLevel.WARN, category, message, details)

    fun e(category: LogCategory, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, category, message, throwable?.stackTraceToString())

    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
        _logsFlow.value = emptyList()
    }

    fun exportAsText(): String {
        val list: List<LogEntry>
        synchronized(lock) {
            list = ArrayList(buffer)
        }
        val sb = StringBuilder()
        sb.append("=== Kindle Page Turner Diagnostic Log ===\n")
        sb.append("Export Time: ${java.util.Date()}\n")
        sb.append("Total Entries: ${list.size}\n\n")
        for (entry in list) {
            sb.append(entry.toFormattedLine()).append("\n")
        }
        return sb.toString()
    }
}
