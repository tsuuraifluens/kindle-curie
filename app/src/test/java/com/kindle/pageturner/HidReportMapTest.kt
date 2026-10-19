package com.kindle.pageturner

import com.kindle.pageturner.hid.HidReportMap
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying HID Report Map (Descriptor) structure.
 *
 * Evidence Mapping:
 * - EVID-11: Dynamic Linux kernel hid-generic parser
 * - hid_protocol_spec.md Section 5
 */
class HidReportMapTest {

    @Test
    fun testKeyboardReportDescriptorLength() {
        val desc = HidReportMap.KEYBOARD_REPORT_DESCRIPTOR
        assertEquals("Standard Keyboard Input Descriptor must be exactly 47 bytes", 47, desc.size)
    }

    @Test
    fun testKeyboardReportDescriptorHeader() {
        val desc = HidReportMap.KEYBOARD_REPORT_DESCRIPTOR
        // USAGE_PAGE (Generic Desktop) -> 0x05, 0x01
        assertEquals(0x05.toByte(), desc[0])
        assertEquals(0x01.toByte(), desc[1])

        // USAGE (Keyboard) -> 0x09, 0x06
        assertEquals(0x09.toByte(), desc[2])
        assertEquals(0x06.toByte(), desc[3])

        // COLLECTION (Application) -> 0xA1, 0x01
        assertEquals(0xA1.toByte(), desc[4])
        assertEquals(0x01.toByte(), desc[5])

        // REPORT_ID (1) -> 0x85, 0x01
        assertEquals(0x85.toByte(), desc[6])
        assertEquals(0x01.toByte(), desc[7])

        // END_COLLECTION -> 0xC0 at the end
        assertEquals(0xC0.toByte(), desc[desc.size - 1])
    }

    @Test
    fun testCompactReportDescriptorLength() {
        val desc = HidReportMap.COMPACT_REPORT_DESCRIPTOR
        // Option B in hid_protocol_spec.md
        assertEquals(0x05.toByte(), desc[0])
        assertEquals(0x01.toByte(), desc[1])
        assertEquals(0xC0.toByte(), desc[desc.size - 1])
    }
}
