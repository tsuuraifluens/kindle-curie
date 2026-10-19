package com.kindle.pageturner

import com.kindle.pageturner.ble.BleConstants
import com.kindle.pageturner.identity.PnpIdPayload
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying PnP ID 7-byte encoding against reverse engineering evidence:
 * - EVID-01: /etc/input_manager/devices.json (vid: 0x0171, pid: 0x0443)
 * - EVID-03: DeviceClassifier::classify (cmp r2, r3)
 * - EVID-04: EvdevProvider::buildDeviceInfo (ioctl EVIOCGID)
 * - hid_protocol_spec.md Section 4.1
 */
class PnpIdPayloadTest {

    @Test
    fun testOfficialAmazonPnpIdPayloadBytes() {
        // Evidence: 02 71 01 43 04 00 01 (7 bytes)
        val expected = byteArrayOf(
            0x02, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01
        )
        val pnp = PnpIdPayload.OFFICIAL_KINDLE_REMOTE
        val actual = pnp.toByteArray()

        assertArrayEquals(expected, actual)
        assertEquals("02 71 01 43 04 00 01", pnp.toHexString())
    }

    @Test
    fun testLittleEndianSerializationAndParsing() {
        val original = PnpIdPayload(
            vendorIdSource = 0x02,
            vendorId = 0x0171,
            productId = 0x0443,
            productVersion = 0x0100
        )
        val bytes = original.toByteArray()
        val parsed = PnpIdPayload.fromByteArray(bytes)

        assertEquals(original.vendorIdSource, parsed.vendorIdSource)
        assertEquals(0x0171, parsed.vendorId)
        assertEquals(0x0443, parsed.productId)
        assertEquals(0x0100, parsed.productVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidByteArrayLengthThrows() {
        PnpIdPayload.fromByteArray(byteArrayOf(0x01, 0x02, 0x03))
    }
}
