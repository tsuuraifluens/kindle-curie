package com.kindle.pageturner.ble

import java.util.UUID

/**
 * Kindle BLE HOGP Protocol Constants
 * Derived from reverse engineering findings:
 * - hid_protocol_spec.md
 * - evidence_index.md (EVID-01, EVID-03, EVID-04, EVID-07, EVID-09, EVID-11)
 * - device_identity_and_allowlist.md
 */
object BleConstants {

    // GAP & Advertising
    const val DEFAULT_DEVICE_NAME = "Kindle Remote"
    const val APPEARANCE_KEYBOARD = 0x03C1

    // Vendor and Product IDs (Amazon Lab126 Kindle Remote)
    // EVID-01: /etc/input_manager/devices.json (vid: 0x0171, pid: 0x0443)
    const val VENDOR_ID_AMAZON = 0x0171
    const val PRODUCT_ID_KINDLE_REMOTE = 0x0443
    const val VENDOR_ID_SOURCE_USB = 0x02
    const val PRODUCT_VERSION = 0x0100

    // Timing & Rate Limits (EVID-02: categories.json)
    const val KEY_HOLD_DURATION_MS = 40L
    const val MIN_DEBOUNCE_INTERVAL_MS = 50L
    const val RECOMMENDED_DEBOUNCE_INTERVAL_MS = 80L
    const val MAX_EVENTS_PER_SEC = 5 // User app throttle (Kindle max is 100Hz, burst 10)

    // GATT Standard Base UUID: 0000xxxx-0000-1000-8000-00805f9b34fb
    private fun fromShortUuid(uuid16: Int): UUID {
        return UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", uuid16))
    }

    // GATT Service UUIDs
    val UUID_SERVICE_GAP = fromShortUuid(0x1800)
    val UUID_SERVICE_GATT = fromShortUuid(0x1801)
    val UUID_SERVICE_DIS = fromShortUuid(0x180A)     // Device Information Service
    val UUID_SERVICE_BAS = fromShortUuid(0x180F)     // Battery Service
    val UUID_SERVICE_HIDS = fromShortUuid(0x1812)    // Human Interface Device Service

    // Device Information Service Characteristics (DIS 0x180A)
    val UUID_CHAR_SYSTEM_ID = fromShortUuid(0x2A23)
    val UUID_CHAR_MODEL_NUMBER = fromShortUuid(0x2A24)
    val UUID_CHAR_SERIAL_NUMBER = fromShortUuid(0x2A25)
    val UUID_CHAR_FIRMWARE_REVISION = fromShortUuid(0x2A26) // EVID-09: acsbtfd BtGattClient::ReadChar
    val UUID_CHAR_HARDWARE_REVISION = fromShortUuid(0x2A27)
    val UUID_CHAR_SOFTWARE_REVISION = fromShortUuid(0x2A28)
    val UUID_CHAR_MANUFACTURER_NAME = fromShortUuid(0x2A29)
    val UUID_CHAR_PNP_ID = fromShortUuid(0x2A50)            // EVID-01/04: 7 bytes PnP ID payload

    // Battery Service Characteristics (BAS 0x180F)
    val UUID_CHAR_BATTERY_LEVEL = fromShortUuid(0x2A19)     // EVID-07: BleHidPageTurnerPolicy

    // HID Service Characteristics (HIDS 0x1812)
    val UUID_CHAR_HID_INFO = fromShortUuid(0x2A4A)          // HID Information (4 bytes: 11 01 00 02)
    val UUID_CHAR_REPORT_MAP = fromShortUuid(0x2A4B)        // Report Map (Standard Keyboard Report Descriptor)
    val UUID_CHAR_HID_CONTROL_POINT = fromShortUuid(0x2A4C) // Control Point (Suspend / Exit Suspend)
    val UUID_CHAR_REPORT = fromShortUuid(0x2A4D)            // Report (Input/Output/Feature)
    val UUID_CHAR_PROTOCOL_MODE = fromShortUuid(0x2A4E)     // Protocol Mode (0x01 Report Mode)
    val UUID_CHAR_BOOT_KEY_INPUT = fromShortUuid(0x2A22)    // Boot Keyboard Input Report

    // Descriptors
    val UUID_DESC_CCCD = fromShortUuid(0x2902)              // Client Characteristic Configuration Descriptor
    val UUID_DESC_REPORT_REFERENCE = fromShortUuid(0x2908)  // Report Reference Descriptor (Report ID, Report Type)

    // HID Information Payload (4 bytes)
    // Byte 0..1: bcdHID = 0x0111 (v1.11 Little-Endian: 0x11, 0x01)
    // Byte 2: bCountryCode = 0x00
    // Byte 3: Flags = 0x02 (NormallyConnectable)
    val HID_INFO_PAYLOAD = byteArrayOf(0x11.toByte(), 0x01.toByte(), 0x00.toByte(), 0x02.toByte())

    // Report Reference Payload for Input Report (Report ID 1, Type 1 = Input)
    val REPORT_REFERENCE_INPUT_PAYLOAD = byteArrayOf(0x01.toByte(), 0x01.toByte())

    // Report ID
    const val REPORT_ID_KEYBOARD = 1.toByte()

    // Default Battery Levels (EVID-07)
    const val BATTERY_LEVEL_DEFAULT = 90
    const val BATTERY_LEVEL_LOW_THRESHOLD = 20      // pageTurnerBatteryLow alert threshold
    const val BATTERY_LEVEL_CRITICAL_THRESHOLD = 5  // pageTurnerBatteryCritical alert threshold
}
