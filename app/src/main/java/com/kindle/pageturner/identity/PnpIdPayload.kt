package com.kindle.pageturner.identity

import com.kindle.pageturner.ble.BleConstants

/**
 * PnP ID Characteristic Payload (UUID 0x2A50)
 *
 * 7-Byte Binary Structure (Little-Endian):
 * - Byte 0: Vendor ID Source (0x02 = USB Implementers Forum)
 * - Byte 1..2: Vendor ID (0x0171 = Lab126 / Amazon.com, Inc.) -> 0x71, 0x01
 * - Byte 3..4: Product ID (0x0443 = Kindle Remote / Kindle Click) -> 0x43, 0x04
 * - Byte 5..6: Product Version (0x0100 = v1.0.0) -> 0x00, 0x01
 *
 * Direct Firmware Evidence:
 * - EVID-01: /etc/input_manager/devices.json ("vid": "0x0171", "pid": "0x0443")
 * - EVID-03: input_manager::DeviceClassifier::classify (cmp r2, r3 strictly for 0x04430171)
 * - EVID-04: input_manager::EvdevProvider::buildDeviceInfo (ioctl EVIOCGID)
 * - hid_protocol_spec.md Section 4.1
 */
data class PnpIdPayload(
    val vendorIdSource: Int = BleConstants.VENDOR_ID_SOURCE_USB,
    val vendorId: Int = BleConstants.VENDOR_ID_AMAZON,
    val productId: Int = BleConstants.PRODUCT_ID_KINDLE_REMOTE,
    val productVersion: Int = BleConstants.PRODUCT_VERSION
) {
    /**
     * Serializes to 7-byte raw array according to Bluetooth SIG DIS PnP ID specification.
     */
    fun toByteArray(): ByteArray {
        val payload = ByteArray(7)
        payload[0] = (vendorIdSource and 0xFF).toByte()
        payload[1] = (vendorId and 0xFF).toByte()        // VID Low Byte
        payload[2] = ((vendorId shr 8) and 0xFF).toByte() // VID High Byte
        payload[3] = (productId and 0xFF).toByte()       // PID Low Byte
        payload[4] = ((productId shr 8) and 0xFF).toByte()// PID High Byte
        payload[5] = (productVersion and 0xFF).toByte()   // Version Low Byte
        payload[6] = ((productVersion shr 8) and 0xFF).toByte() // Version High Byte
        return payload
    }

    /**
     * Formats bytes as uppercase hex string.
     */
    fun toHexString(): String {
        return toByteArray().joinToString(" ") { String.format("%02X", it) }
    }

    companion object {
        /**
         * The standard 7-byte payload for Amazon Kindle Remote:
         * 02 71 01 43 04 00 01
         */
        val OFFICIAL_KINDLE_REMOTE = PnpIdPayload()

        /**
         * Parses a 7-byte array into PnpIdPayload.
         */
        fun fromByteArray(bytes: ByteArray): PnpIdPayload {
            require(bytes.size == 7) { "PnP ID payload must be exactly 7 bytes, but got ${bytes.size}" }
            val vidSource = bytes[0].toInt() and 0xFF
            val vid = (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
            val pid = (bytes[3].toInt() and 0xFF) or ((bytes[4].toInt() and 0xFF) shl 8)
            val ver = (bytes[5].toInt() and 0xFF) or ((bytes[6].toInt() and 0xFF) shl 8)
            return PnpIdPayload(vidSource, vid, pid, ver)
        }
    }
}
