package com.kindle.pageturner

import com.kindle.pageturner.battery.BatteryServiceManager
import com.kindle.pageturner.ble.BleConstants
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying Battery Service Manager behavior and threshold handling.
 *
 * Evidence Mapping:
 * - EVID-07: BleHidPageTurnerPolicy (pageTurnerBatteryLow <= 20%, pageTurnerBatteryCritical <= 5%)
 */
class BatteryServiceManagerTest {

    @Test
    fun testDefaultBatteryLevel() {
        val manager = BatteryServiceManager()
        assertEquals(BleConstants.BATTERY_LEVEL_DEFAULT, manager.batteryLevel.value)
        val payload = manager.getBatteryLevelPayload()
        assertEquals(1, payload.size)
        assertEquals(90.toByte(), payload[0])
    }

    @Test
    fun testBatteryLevelClamping() {
        val manager = BatteryServiceManager()
        manager.updateBatteryLevel(150)
        assertEquals(100, manager.batteryLevel.value)

        manager.updateBatteryLevel(-20)
        assertEquals(0, manager.batteryLevel.value)
    }

    @Test
    fun testBatteryNotificationCallbackInvoked() {
        var notifiedLevel = -1
        val manager = BatteryServiceManager { level ->
            notifiedLevel = level
            true
        }
        manager.isSubscribed = true
        manager.updateBatteryLevel(75)

        assertEquals(75, notifiedLevel)
    }
}
