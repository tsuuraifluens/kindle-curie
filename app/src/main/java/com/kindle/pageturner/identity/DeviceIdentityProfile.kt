package com.kindle.pageturner.identity

import com.kindle.pageturner.ble.BleConstants

/**
 * Kindle Remote Device Identity Profile
 *
 * Evidence Mapping:
 * - EVID-01: /etc/input_manager/devices.json (name="Amazon Kindle Remote", vid="0x0171", pid="0x0443")
 * - EVID-07: acsbtfd BleHidPageTurnerPolicy (category "pageturner")
 * - EVID-08: acsbtfd BtDeviceManager::OnUnknownConnect (category BleHidPageTurner)
 * - EVID-09: acsbtfd BtGattClient::ReadChar (Firmware revision string probed: DIS 0x2A26)
 * - hid_protocol_spec.md Section 3, 4.1
 */
data class DeviceIdentityProfile(
    val deviceName: String = BleConstants.DEFAULT_DEVICE_NAME,
    val manufacturerName: String = "Amazon",
    val modelNumber: String = "Kindle Remote",
    val serialNumber: String = "G000WM0000000001",
    val firmwareRevision: String = "1.0.0", // Probed by acsbtfd (EVID-09)
    val hardwareRevision: String = "1.0",
    val softwareRevision: String = "1.0.0",
    val pnpId: PnpIdPayload = PnpIdPayload.OFFICIAL_KINDLE_REMOTE,
    val appearance: Int = BleConstants.APPEARANCE_KEYBOARD
) {
    companion object {
        val DEFAULT = DeviceIdentityProfile()
    }
}
