package com.kindle.pageturner.hid

import com.kindle.pageturner.ble.BleConstants

/**
 * HID Keyboard Input Report Builder
 *
 * Evidence Mapping:
 * - hid_protocol_spec.md Section 5.1 & 6.1
 * - android_app_implementation_spec.md Section 4.2.2
 *
 * Report Structure (8 bytes data frame):
 * - Byte 0: Modifier keys bitmap (Ctrl, Shift, Alt, GUI) -> 0x00
 * - Byte 1: Reserved byte -> 0x00
 * - Byte 2: KeyCode 1 (Usage ID from Keyboard Page 0x07)
 * - Byte 3..7: KeyCode 2..6 -> 0x00 (up to 6 concurrent keys)
 *
 * When Report ID 1 is prefixed (9 bytes total):
 * - Byte 0: Report ID (0x01)
 * - Byte 1: Modifier keys -> 0x00
 * - Byte 2: Reserved byte -> 0x00
 * - Byte 3: KeyCode 1
 * - Byte 4..8: KeyCode 2..6 -> 0x00
 */
object HidReportBuilder {

    /**
     * Builds standard 8-byte keyboard report payload (used by BluetoothHidDevice API)
     */
    fun build8ByteReport(modifier: Byte = 0x00, usageCode: Byte = 0x00): ByteArray {
        val payload = ByteArray(8)
        payload[0] = modifier
        payload[1] = 0x00 // Reserved
        payload[2] = usageCode
        return payload
    }

    /**
     * Builds 9-byte keyboard report payload with Report ID 1 (used by BLE GATT Server notification)
     * Exact hex:
     * - Next page (PageDown 0x4E): 01 00 00 4E 00 00 00 00 00
     * - Prev page (PageUp 0x4B):   01 00 00 4B 00 00 00 00 00
     * - Release:                   01 00 00 00 00 00 00 00 00
     */
    fun build9ByteReport(modifier: Byte = 0x00, usageCode: Byte = 0x00): ByteArray {
        val payload = ByteArray(9)
        payload[0] = BleConstants.REPORT_ID_KEYBOARD // 0x01
        payload[1] = modifier
        payload[2] = 0x00 // Reserved
        payload[3] = usageCode
        return payload
    }

    /**
     * Creates press report for given action
     */
    fun createPressReport(action: HidKeyAction, includeReportId: Boolean): ByteArray {
        return if (includeReportId) {
            build9ByteReport(usageCode = action.usageCode)
        } else {
            build8ByteReport(usageCode = action.usageCode)
        }
    }

    /**
     * Creates all-zero release report
     */
    fun createReleaseReport(includeReportId: Boolean): ByteArray {
        return if (includeReportId) {
            build9ByteReport(usageCode = HidKeyAction.USAGE_NONE)
        } else {
            build8ByteReport(usageCode = HidKeyAction.USAGE_NONE)
        }
    }

    /**
     * Formats byte array as readable hex string
     */
    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}
