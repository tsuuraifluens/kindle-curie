package com.kindle.pageturner.hid

import com.kindle.pageturner.ble.BleConstants
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed class KeySendResult {
    data class Success(val action: HidKeyAction, val durationMs: Long) : KeySendResult()
    data class NotConnected(val reason: String) : KeySendResult()
    data class NotSubscribed(val reason: String) : KeySendResult()
    data class Debounced(val intervalMs: Long) : KeySendResult()
    data class RateLimited(val eventsInWindow: Int) : KeySendResult()
    data class Busy(val message: String) : KeySendResult()
    data class Failed(val error: String) : KeySendResult()
}

/**
 * HID Key Event State Machine
 *
 * Enforces:
 * - Connection and CCCD notification readiness checks
 * - Hardware debounce window (>= 50ms, recommended 80ms, EVID-02)
 * - Press -> Hold (40ms) -> Release atomic sequence
 * - Prevents stuck key states and overlapping concurrent dispatches
 * - Rate limiting (<= 5 events / sec)
 */
class HidStateMachine(
    private val scope: CoroutineScope,
    private val sendReportDelegate: suspend (reportPayload: ByteArray) -> Boolean,
    private val isConnectedProvider: () -> Boolean,
    private val isNotificationReadyProvider: () -> Boolean,
    var includeReportId: Boolean = true
) {
    private val mutex = Mutex()
    private val isBusy = AtomicBoolean(false)
    private val lastEventTime = AtomicLong(0)
    private val recentEventTimes = ArrayList<Long>()

    var holdDurationMs: Long = BleConstants.KEY_HOLD_DURATION_MS
    var debounceIntervalMs: Long = BleConstants.RECOMMENDED_DEBOUNCE_INTERVAL_MS

    suspend fun sendKey(action: HidKeyAction): KeySendResult = mutex.withLock {
        // 1. Connection check
        if (!isConnectedProvider()) {
            val res = KeySendResult.NotConnected("Kindle is not connected")
            DiagnosticLogger.w(LogCategory.HID_REPORT, "Key dispatch blocked: ${action.actionName} - not connected")
            return@withLock res
        }

        // 2. Notification subscription check (CCCD 0x2902)
        if (!isNotificationReadyProvider()) {
            val res = KeySendResult.NotSubscribed("Kindle has not subscribed to HID notifications (CCCD not enabled)")
            DiagnosticLogger.w(LogCategory.HID_REPORT, "Key dispatch blocked: ${action.actionName} - notifications not subscribed")
            return@withLock res
        }

        val now = System.currentTimeMillis()

        // 3. Debounce check (EVID-02: 50ms hardware debounce)
        val elapsedSinceLast = now - lastEventTime.get()
        if (elapsedSinceLast < debounceIntervalMs && lastEventTime.get() != 0L) {
            val res = KeySendResult.Debounced(elapsedSinceLast)
            DiagnosticLogger.w(
                LogCategory.HID_REPORT,
                "Key debounced: ${action.actionName} (elapsed: ${elapsedSinceLast}ms < ${debounceIntervalMs}ms)"
            )
            return@withLock res
        }

        // 4. Rate limiter check (Token bucket / Sliding window <= 5 events in 1000ms)
        cleanExpiredEvents(now)
        if (recentEventTimes.size >= BleConstants.MAX_EVENTS_PER_SEC) {
            val res = KeySendResult.RateLimited(recentEventTimes.size)
            DiagnosticLogger.w(
                LogCategory.HID_REPORT,
                "Rate limit exceeded: ${recentEventTimes.size} events in last 1000ms. Discarded: ${action.actionName}"
            )
            return@withLock res
        }

        // 5. Atomic Press -> Hold -> Release
        isBusy.set(true)
        val startTime = System.currentTimeMillis()
        try {
            val pressPayload = HidReportBuilder.createPressReport(action, includeReportId)
            DiagnosticLogger.i(
                LogCategory.HID_REPORT,
                "Sending PRESS: ${action.actionName} (Usage: 0x${String.format("%02X", action.usageCode)})",
                "Payload: [${HidReportBuilder.toHexString(pressPayload)}]"
            )

            val pressSuccess = sendReportDelegate(pressPayload)
            if (!pressSuccess) {
                // Ensure release is sent even if press failed
                sendReleaseSafely()
                return@withLock KeySendResult.Failed("Failed to send PRESS notification")
            }

            // Hold duration
            delay(holdDurationMs)

            // Send Release
            val releaseSuccess = sendReleaseSafely()
            if (!releaseSuccess) {
                return@withLock KeySendResult.Failed("Failed to send RELEASE notification")
            }

            lastEventTime.set(System.currentTimeMillis())
            recentEventTimes.add(System.currentTimeMillis())

            val totalTime = System.currentTimeMillis() - startTime
            DiagnosticLogger.i(
                LogCategory.HID_REPORT,
                "Key completed successfully: ${action.actionName} in ${totalTime}ms"
            )
            return@withLock KeySendResult.Success(action, totalTime)
        } catch (t: Throwable) {
            DiagnosticLogger.e(LogCategory.HID_REPORT, "Exception during key dispatch for ${action.actionName}", t)
            sendReleaseSafely()
            return@withLock KeySendResult.Failed("Exception: ${t.message}")
        } finally {
            isBusy.set(false)
        }
    }

    private suspend fun sendReleaseSafely(): Boolean {
        return try {
            val releasePayload = HidReportBuilder.createReleaseReport(includeReportId)
            DiagnosticLogger.d(
                LogCategory.HID_REPORT,
                "Sending RELEASE",
                "Payload: [${HidReportBuilder.toHexString(releasePayload)}]"
            )
            sendReportDelegate(releasePayload)
        } catch (t: Throwable) {
            DiagnosticLogger.e(LogCategory.HID_REPORT, "Failed to send emergency release", t)
            false
        }
    }

    private fun cleanExpiredEvents(now: Long) {
        val cutoff = now - 1000L
        recentEventTimes.removeAll { it < cutoff }
    }
}
