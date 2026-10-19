package com.kindle.pageturner.hid

/**
 * Standard HID Report Descriptors (Report Map)
 *
 * Evidence Mapping:
 * - EVID-11: Linux kernel hid-generic parses exposed 0x2A4B report map dynamically
 * - hid_protocol_spec.md Section 5 (Option A: 63-byte standard keyboard descriptor)
 */
object HidReportMap {

    /**
     * Standard Keyboard Report Descriptor with Report ID 1 (63 bytes)
     * Compatible with standard Linux hid-generic and Android HOGP stack.
     */
    val KEYBOARD_REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),                    // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),                    // USAGE (Keyboard)
        0xA1.toByte(), 0x01.toByte(),                    // COLLECTION (Application)
        0x85.toByte(), 0x01.toByte(),                    //   REPORT_ID (1)
        0x05.toByte(), 0x07.toByte(),                    //   USAGE_PAGE (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),                    //   USAGE_MINIMUM (Keyboard LeftControl)
        0x29.toByte(), 0xE7.toByte(),                    //   USAGE_MAXIMUM (Keyboard Right GUI)
        0x15.toByte(), 0x00.toByte(),                    //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),                    //   LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(),                    //   REPORT_SIZE (1)
        0x95.toByte(), 0x08.toByte(),                    //   REPORT_COUNT (8)
        0x81.toByte(), 0x02.toByte(),                    //   INPUT (Data,Var,Abs) - Modifier Byte
        0x95.toByte(), 0x01.toByte(),                    //   REPORT_COUNT (1)
        0x75.toByte(), 0x08.toByte(),                    //   REPORT_SIZE (8)
        0x81.toByte(), 0x03.toByte(),                    //   INPUT (Cnst,Var,Abs) - Reserved Byte
        0x95.toByte(), 0x06.toByte(),                    //   REPORT_COUNT (6)
        0x75.toByte(), 0x08.toByte(),                    //   REPORT_SIZE (8)
        0x15.toByte(), 0x00.toByte(),                    //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x65.toByte(),                    //   LOGICAL_MAXIMUM (101)
        0x05.toByte(), 0x07.toByte(),                    //   USAGE_PAGE (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),                    //   USAGE_MINIMUM (0)
        0x29.toByte(), 0x65.toByte(),                    //   USAGE_MAXIMUM (101)
        0x81.toByte(), 0x00.toByte(),                    //   INPUT (Data,Ary,Abs) - Key Array (6 bytes)
        0xC0.toByte()                                    // END_COLLECTION
    )

    /**
     * Compact 3-byte Report Descriptor (without Report ID)
     * Defined in hid_protocol_spec.md Section 5 (Option B)
     */
    val COMPACT_REPORT_DESCRIPTOR: ByteArray = byteArrayOf(
        0x05.toByte(), 0x01.toByte(),                    // USAGE_PAGE (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),                    // USAGE (Keyboard)
        0xA1.toByte(), 0x01.toByte(),                    // COLLECTION (Application)
        0x05.toByte(), 0x07.toByte(),                    //   USAGE_PAGE (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(),                    //   USAGE_MINIMUM (224)
        0x29.toByte(), 0xE7.toByte(),                    //   USAGE_MAXIMUM (231)
        0x15.toByte(), 0x00.toByte(),                    //   LOGICAL_MINIMUM (0)
        0x25.toByte(), 0x01.toByte(),                    //   LOGICAL_MAXIMUM (1)
        0x75.toByte(), 0x01.toByte(),                    //   REPORT_SIZE (1)
        0x95.toByte(), 0x08.toByte(),                    //   REPORT_COUNT (8)
        0x81.toByte(), 0x02.toByte(),                    //   INPUT (Data,Var,Abs)
        0x05.toByte(), 0x07.toByte(),                    //   USAGE_PAGE (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(),                    //   USAGE_MINIMUM (0)
        0x2A.toByte(), 0xFF.toByte(), 0x00.toByte(),     //   USAGE_MAXIMUM (255)
        0x15.toByte(), 0x00.toByte(),                    //   LOGICAL_MINIMUM (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),     //   LOGICAL_MAXIMUM (255)
        0x75.toByte(), 0x08.toByte(),                    //   REPORT_SIZE (8)
        0x95.toByte(), 0x01.toByte(),                    //   REPORT_COUNT (1)
        0x81.toByte(), 0x00.toByte(),                    //   INPUT (Data,Ary,Abs)
        0xC0.toByte()                                    // END_COLLECTION
    )
}
