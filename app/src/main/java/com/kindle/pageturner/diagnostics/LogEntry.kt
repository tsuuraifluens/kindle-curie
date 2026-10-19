package com.kindle.pageturner.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

enum class LogCategory {
    BLE_ADV,
    GATT_SERVER,
    HID_REPORT,
    BATTERY,
    IDENTITY,
    SYSTEM,
    PERMISSIONS
}

data class LogEntry(
    val id: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    fun toFormattedLine(): String {
        val levelChar = when (level) {
            LogLevel.DEBUG -> "D"
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
        }
        val detailPart = if (details != null) " | $details" else ""
        return "[$formattedTime] [$levelChar] [${category.name}] $message$detailPart"
    }
}
