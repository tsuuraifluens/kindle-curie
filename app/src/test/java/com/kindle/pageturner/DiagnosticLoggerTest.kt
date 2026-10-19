package com.kindle.pageturner

import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import com.kindle.pageturner.diagnostics.LogLevel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying DiagnosticLogger functionality.
 */
class DiagnosticLoggerTest {

    @Before
    fun setup() {
        DiagnosticLogger.clear()
        DiagnosticLogger.minLogLevel = LogLevel.DEBUG
    }

    @Test
    fun testLogAdditionAndExport() {
        DiagnosticLogger.i(LogCategory.BLE_ADV, "Advertising started", "Device: Kindle Remote")
        DiagnosticLogger.w(LogCategory.HID_REPORT, "Debounced key")

        val logs = DiagnosticLogger.logsFlow.value
        assertEquals(2, logs.size)

        assertEquals(LogLevel.INFO, logs[0].level)
        assertEquals(LogCategory.BLE_ADV, logs[0].category)
        assertEquals("Advertising started", logs[0].message)
        assertEquals("Device: Kindle Remote", logs[0].details)

        val exported = DiagnosticLogger.exportAsText()
        assertTrue(exported.contains("Advertising started"))
        assertTrue(exported.contains("Debounced key"))
    }

    @Test
    fun testClear() {
        DiagnosticLogger.i(LogCategory.SYSTEM, "Test message")
        assertEquals(1, DiagnosticLogger.logsFlow.value.size)

        DiagnosticLogger.clear()
        assertEquals(0, DiagnosticLogger.logsFlow.value.size)
    }

    @Test
    fun testLogLevelFiltering() {
        DiagnosticLogger.minLogLevel = LogLevel.WARN

        DiagnosticLogger.d(LogCategory.SYSTEM, "Debug message")
        DiagnosticLogger.i(LogCategory.BLE_ADV, "Info message")
        DiagnosticLogger.w(LogCategory.HID_REPORT, "Warn message")
        DiagnosticLogger.e(LogCategory.SYSTEM, "Error message")

        val logs = DiagnosticLogger.logsFlow.value
        assertEquals(2, logs.size)
        assertEquals(LogLevel.WARN, logs[0].level)
        assertEquals("Warn message", logs[0].message)
        assertEquals(LogLevel.ERROR, logs[1].level)
        assertEquals("Error message", logs[1].message)
    }
}
