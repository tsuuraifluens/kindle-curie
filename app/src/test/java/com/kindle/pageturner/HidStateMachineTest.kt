package com.kindle.pageturner

import com.kindle.pageturner.hid.HidKeyAction
import com.kindle.pageturner.hid.HidStateMachine
import com.kindle.pageturner.hid.KeySendResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying HID State Machine behavior:
 * - Connection guards
 * - CCCD readiness guards
 * - Debounce logic (<50ms vs >=80ms)
 * - Atomic Press -> Release sequence
 */
class HidStateMachineTest {

    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun testNotConnectedRejection() = runBlocking {
        var reportSent = false
        val stateMachine = HidStateMachine(
            scope = testScope,
            sendReportDelegate = {
                reportSent = true
                true
            },
            isConnectedProvider = { false },
            isNotificationReadyProvider = { true }
        )

        val result = stateMachine.sendKey(HidKeyAction.PAGE_DOWN)
        assertTrue(result is KeySendResult.NotConnected)
        assertFalse("Report must not be sent when not connected", reportSent)
    }

    @Test
    fun testNotSubscribedRejection() = runBlocking {
        var reportSent = false
        val stateMachine = HidStateMachine(
            scope = testScope,
            sendReportDelegate = {
                reportSent = true
                true
            },
            isConnectedProvider = { true },
            isNotificationReadyProvider = { false }
        )

        val result = stateMachine.sendKey(HidKeyAction.PAGE_DOWN)
        assertTrue(result is KeySendResult.NotSubscribed)
        assertFalse("Report must not be sent when CCCD is not subscribed", reportSent)
    }

    @Test
    fun testAtomicPressAndReleaseSequence() = runBlocking {
        val sentPayloads = mutableListOf<ByteArray>()
        val stateMachine = HidStateMachine(
            scope = testScope,
            sendReportDelegate = { payload ->
                sentPayloads.add(payload)
                true
            },
            isConnectedProvider = { true },
            isNotificationReadyProvider = { true },
            includeReportId = true
        )
        // Set short hold time for test
        stateMachine.holdDurationMs = 5L
        stateMachine.debounceIntervalMs = 0L

        val result = stateMachine.sendKey(HidKeyAction.PAGE_DOWN)
        assertTrue(result is KeySendResult.Success)

        // Must have sent exactly 2 packets: Press (0x4E) and Release (0x00)
        assertEquals(2, sentPayloads.size)

        // Press
        assertEquals(0x4E.toByte(), sentPayloads[0][3])
        // Release
        assertEquals(0x00.toByte(), sentPayloads[1][3])
    }

    @Test
    fun testDebouncePreventsRapidFire() = runBlocking {
        val sentPayloads = mutableListOf<ByteArray>()
        val stateMachine = HidStateMachine(
            scope = testScope,
            sendReportDelegate = { payload ->
                sentPayloads.add(payload)
                true
            },
            isConnectedProvider = { true },
            isNotificationReadyProvider = { true }
        )
        stateMachine.holdDurationMs = 5L
        stateMachine.debounceIntervalMs = 200L // 200ms debounce

        val result1 = stateMachine.sendKey(HidKeyAction.PAGE_DOWN)
        assertTrue(result1 is KeySendResult.Success)

        // Immediate next send without waiting debounce interval
        val result2 = stateMachine.sendKey(HidKeyAction.PAGE_DOWN)
        assertTrue(result2 is KeySendResult.Debounced)

        // Only the first press/release sequence went through (2 packets)
        assertEquals(2, sentPayloads.size)
    }

    @Test
    fun testRateLimiterBlocksExcessiveEvents() = runBlocking {
        val sentPayloads = mutableListOf<ByteArray>()
        val stateMachine = HidStateMachine(
            scope = testScope,
            sendReportDelegate = { payload ->
                sentPayloads.add(payload)
                true
            },
            isConnectedProvider = { true },
            isNotificationReadyProvider = { true }
        )
        stateMachine.holdDurationMs = 1L
        stateMachine.debounceIntervalMs = 0L // Disable debounce to test rate limiter

        // BleConstants.MAX_EVENTS_PER_SEC = 5
        for (i in 1..5) {
            val res = stateMachine.sendKey(HidKeyAction.PAGE_DOWN)
            assertTrue("Event $i should succeed", res is KeySendResult.Success)
        }

        // 6th event within 1 sec should be rate limited
        val res6 = stateMachine.sendKey(HidKeyAction.PAGE_DOWN)
        assertTrue("6th event within window should be rate limited", res6 is KeySendResult.RateLimited)
        assertEquals(5, (res6 as KeySendResult.RateLimited).eventsInWindow)

        // 5 successful events * 2 packets (press + release) = 10 packets sent
        assertEquals(10, sentPayloads.size)
    }

    @Test
    fun testReportIdConfigurable() = runBlocking {
        val sentPayloads = mutableListOf<ByteArray>()
        val stateMachine = HidStateMachine(
            scope = testScope,
            sendReportDelegate = { payload ->
                sentPayloads.add(payload)
                true
            },
            isConnectedProvider = { true },
            isNotificationReadyProvider = { true },
            includeReportId = false // 8-byte format
        )
        stateMachine.holdDurationMs = 1L
        stateMachine.debounceIntervalMs = 0L

        val res = stateMachine.sendKey(HidKeyAction.PAGE_DOWN)
        assertTrue(res is KeySendResult.Success)
        assertEquals(2, sentPayloads.size)
        assertEquals(8, sentPayloads[0].size)
        assertEquals(8, sentPayloads[1].size)

        // Switch to 9-byte format
        stateMachine.includeReportId = true
        val res2 = stateMachine.sendKey(HidKeyAction.PAGE_UP)
        assertTrue(res2 is KeySendResult.Success)
        assertEquals(4, sentPayloads.size)
        assertEquals(9, sentPayloads[2].size)
        assertEquals(9, sentPayloads[3].size)
    }
}
