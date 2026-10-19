package com.kindle.pageturner

import com.kindle.pageturner.hid.HidKeyAction
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying HID key codes and translations against reverse engineering evidence:
 * - EVID-02: /etc/input_manager/categories.json (capability_table)
 * - EVID-06: /etc/upstart/makexconfig & /etc/udev/rules.d/99-kindle-input.rules
 * - EVID-10: libAccessories.so
 * - input_mapping.md Section 2 (Event Translation Matrix)
 */
class HidKeyCodesTest {

    @Test
    fun testPageDownMappingMatchesEvidence() {
        val action = HidKeyAction.PAGE_DOWN
        assertEquals("Usage 0x4E", 0x4E.toByte(), action.usageCode)
        assertEquals("Linux evdev 109", 109, action.linuxEvdevCode)
        assertEquals("X11 keycode 117 (evdev + 8)", 117, action.x11KeyCode)
        assertEquals("nav.page_forward", action.semanticName)
        assertEquals(action, HidKeyAction.findByUsage(0x4E.toByte()))
    }

    @Test
    fun testPageUpMappingMatchesEvidence() {
        val action = HidKeyAction.PAGE_UP
        assertEquals("Usage 0x4B", 0x4B.toByte(), action.usageCode)
        assertEquals("Linux evdev 104", 104, action.linuxEvdevCode)
        assertEquals("X11 keycode 112 (evdev + 8)", 112, action.x11KeyCode)
        assertEquals("nav.page_back", action.semanticName)
        assertEquals(action, HidKeyAction.findByUsage(0x4B.toByte()))
    }

    @Test
    fun testBrightnessMappingsMatchEvidence() {
        val bUp = HidKeyAction.BRIGHTNESS_UP
        assertEquals(0x6F.toByte(), bUp.usageCode)
        assertEquals(225, bUp.linuxEvdevCode)
        assertEquals(233, bUp.x11KeyCode)
        assertEquals("ui.bright_up", bUp.semanticName)
        assertEquals(bUp, HidKeyAction.findByUsage(0x6F.toByte()))

        val bDown = HidKeyAction.BRIGHTNESS_DOWN
        assertEquals(0x6E.toByte(), bDown.usageCode)
        assertEquals(224, bDown.linuxEvdevCode)
        assertEquals(232, bDown.x11KeyCode)
        assertEquals("ui.bright_down", bDown.semanticName)
        assertEquals(bDown, HidKeyAction.findByUsage(0x6E.toByte()))
    }

    @Test
    fun testPowerMappingMatchesEvidence() {
        val power = HidKeyAction.POWER
        assertEquals(0x66.toByte(), power.usageCode)
        assertEquals(116, power.linuxEvdevCode)
        assertEquals(124, power.x11KeyCode)
        assertEquals("sys.power", power.semanticName)
        assertEquals(power, HidKeyAction.findByUsage(0x66.toByte()))
    }

    @Test
    fun testUnknownUsageReturnsNull() {
        assertNull(HidKeyAction.findByUsage(0x99.toByte()))
    }
}
