package com.kindle.pageturner

import com.kindle.pageturner.ble.BleConstants
import com.kindle.pageturner.hid.HidKeyAction
import com.kindle.pageturner.hid.HidReportBuilder
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying exact HID report encoding against reverse engineering evidence:
 * - hid_protocol_spec.md Section 6.1
 * - input_mapping.md Section 2
 */
class HidReportBuilderTest {

    @Test
    fun testPageDown9ByteReportMatchesEvidence() {
        // EVID-02 & hid_protocol_spec.md Sec 6.1:
        // PageDown Usage 0x4E -> "01 00 00 4E 00 00 00 00 00"
        val expected = byteArrayOf(
            0x01, 0x00, 0x00, 0x4E, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val actual = HidReportBuilder.createPressReport(HidKeyAction.PAGE_DOWN, includeReportId = true)
        assertArrayEquals(expected, actual)
        assertEquals("01 00 00 4E 00 00 00 00 00", HidReportBuilder.toHexString(actual))
    }

    @Test
    fun testPageUp9ByteReportMatchesEvidence() {
        // EVID-02 & hid_protocol_spec.md Sec 6.1:
        // PageUp Usage 0x4B -> "01 00 00 4B 00 00 00 00 00"
        val expected = byteArrayOf(
            0x01, 0x00, 0x00, 0x4B, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val actual = HidReportBuilder.createPressReport(HidKeyAction.PAGE_UP, includeReportId = true)
        assertArrayEquals(expected, actual)
        assertEquals("01 00 00 4B 00 00 00 00 00", HidReportBuilder.toHexString(actual))
    }

    @Test
    fun testRelease9ByteReportMatchesEvidence() {
        // hid_protocol_spec.md Sec 6.1:
        // Release -> "01 00 00 00 00 00 00 00 00"
        val expected = byteArrayOf(
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val actual = HidReportBuilder.createReleaseReport(includeReportId = true)
        assertArrayEquals(expected, actual)
        assertEquals("01 00 00 00 00 00 00 00 00", HidReportBuilder.toHexString(actual))
    }

    @Test
    fun testPageDown8ByteClassicReport() {
        val expected = byteArrayOf(
            0x00, 0x00, 0x4E, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val actual = HidReportBuilder.createPressReport(HidKeyAction.PAGE_DOWN, includeReportId = false)
        assertArrayEquals(expected, actual)
        assertEquals(8, actual.size)
    }

    @Test
    fun testPageUp8ByteClassicReport() {
        val expected = byteArrayOf(
            0x00, 0x00, 0x4B, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        val actual = HidReportBuilder.createPressReport(HidKeyAction.PAGE_UP, includeReportId = false)
        assertArrayEquals(expected, actual)
        assertEquals(8, actual.size)
    }

    @Test
    fun testBrightnessAndPowerReports() {
        // Brightness Up (F14 / 0x6F)
        val bUp = HidReportBuilder.createPressReport(HidKeyAction.BRIGHTNESS_UP, includeReportId = true)
        assertEquals(0x6F.toByte(), bUp[3])

        // Brightness Down (F15 / 0x6E)
        val bDown = HidReportBuilder.createPressReport(HidKeyAction.BRIGHTNESS_DOWN, includeReportId = true)
        assertEquals(0x6E.toByte(), bDown[3])

        // Power (0x66)
        val power = HidReportBuilder.createPressReport(HidKeyAction.POWER, includeReportId = true)
        assertEquals(0x66.toByte(), power[3])
    }
}
