package com.kindle.pageturner.service

/**
 * Supported Kindle emulation operating modes.
 *
 * Evidence Mapping:
 * - android_app_implementation_spec.md Section 2 (Scheme A/B/C)
 * - BLE_PERIPHERAL: Standard BLE HOGP Peripheral (GATT Server + Advertiser, Root-free, verified)
 * - CLASSIC_HID: Classic Bluetooth HID Device (BluetoothHidDevice API, VID/PID modification pending verification)
 */
enum class EmulationMode(val displayName: String) {
    BLE_PERIPHERAL("普通模式 (BLE HOGP 免 Root)"),
    CLASSIC_HID("高级模式 (Classic HID - 身份修改待验证)")
}

