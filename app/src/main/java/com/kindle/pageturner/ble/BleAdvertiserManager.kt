package com.kindle.pageturner.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AdvertisingState {
    STOPPED,
    STARTING,
    ADVERTISING,
    FAILED
}

/**
 * BLE Advertising Manager for Kindle Remote emulation.
 *
 * Evidence Mapping:
 * - hid_protocol_spec.md Section 2 (GAP advertising parameters & payload)
 * - EVID-08: acsbtfd BtAceAdapter::AceDeviceDiscoveredCb
 */
class BleAdvertiserManager(
    private val bluetoothAdapterProvider: () -> BluetoothAdapter?
) {
    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    private val _state = MutableStateFlow(AdvertisingState.STOPPED)
    val state: StateFlow<AdvertisingState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    var deviceName: String = BleConstants.DEFAULT_DEVICE_NAME
    var includeBatteryService: Boolean = true

    var isFallbackMode: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun startAdvertising(fallbackMode: Boolean = false): Boolean {
        isFallbackMode = fallbackMode
        val adapter = bluetoothAdapterProvider()
        if (adapter == null || !adapter.isEnabled) {
            _lastError.value = "Bluetooth is disabled or not available"
            DiagnosticLogger.e(LogCategory.BLE_ADV, "Cannot start advertising: Bluetooth disabled")
            _state.value = AdvertisingState.FAILED
            return false
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            _lastError.value = "BLE Peripheral mode (BluetoothLeAdvertiser) is not supported on this device hardware"
            DiagnosticLogger.e(LogCategory.BLE_ADV, "Device hardware lacks BluetoothLeAdvertiser capability")
            _state.value = AdvertisingState.FAILED
            return false
        }

        // Set adapter name to deviceName if possible
        try {
            if (adapter.name != deviceName) {
                adapter.name = deviceName
                DiagnosticLogger.i(LogCategory.BLE_ADV, "Requested adapter name change to '$deviceName'")
            }
        } catch (t: Throwable) {
            DiagnosticLogger.w(LogCategory.BLE_ADV, "Could not set adapter name: ${t.message}")
        }

        stopAdvertising() // Clean up any existing session

        _state.value = AdvertisingState.STARTING
        _lastError.value = null

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely until connected/stopped
            .build()

        val dataBuilder = AdvertiseData.Builder()
        val scanResponseBuilder = AdvertiseData.Builder()

        if (!fallbackMode) {
            // Standard advertisement: device name in primary packet
            dataBuilder.setIncludeDeviceName(true)
            dataBuilder.setIncludeTxPowerLevel(false)
            dataBuilder.addServiceUuid(ParcelUuid(BleConstants.UUID_SERVICE_HIDS))

            if (includeBatteryService) {
                dataBuilder.addServiceUuid(ParcelUuid(BleConstants.UUID_SERVICE_BAS))
            }
            dataBuilder.addServiceUuid(ParcelUuid(BleConstants.UUID_SERVICE_DIS))

            scanResponseBuilder.setIncludeTxPowerLevel(true)
            scanResponseBuilder.addManufacturerData(
                BleConstants.VENDOR_ID_AMAZON,
                byteArrayOf(0x01.toByte(), 0x00.toByte())
            )
        } else {
            // Fallback compact layout: Service UUIDs in primary, Device Name + Manufacturer in Scan Response
            // Guarantees primary packet <= 11 bytes, avoiding 31-byte overflow on any Android OEM
            dataBuilder.setIncludeDeviceName(false)
            dataBuilder.setIncludeTxPowerLevel(false)
            dataBuilder.addServiceUuid(ParcelUuid(BleConstants.UUID_SERVICE_HIDS))

            if (includeBatteryService) {
                dataBuilder.addServiceUuid(ParcelUuid(BleConstants.UUID_SERVICE_BAS))
            }
            dataBuilder.addServiceUuid(ParcelUuid(BleConstants.UUID_SERVICE_DIS))

            scanResponseBuilder.setIncludeDeviceName(true)
            scanResponseBuilder.setIncludeTxPowerLevel(true)
            scanResponseBuilder.addManufacturerData(
                BleConstants.VENDOR_ID_AMAZON,
                byteArrayOf(0x01.toByte(), 0x00.toByte())
            )
            DiagnosticLogger.i(LogCategory.BLE_ADV, "Using compact advertising fallback (Name moved to Scan Response)")
        }

        val advertiseData = dataBuilder.build()
        val scanResponseData = scanResponseBuilder.build()

        val newCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _state.value = AdvertisingState.ADVERTISING
                _lastError.value = null
                DiagnosticLogger.i(
                    LogCategory.BLE_ADV,
                    "BLE Advertising STARTED successfully as '$deviceName' ${if (fallbackMode) "(Compact Fallback)" else "(Standard)"}",
                    "TxPower=${settingsInEffect?.txPowerLevel}, Mode=${settingsInEffect?.mode}, Connectable=${settingsInEffect?.isConnectable}"
                )
            }

            override fun onStartFailure(errorCode: Int) {
                if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && !fallbackMode) {
                    DiagnosticLogger.w(
                        LogCategory.BLE_ADV,
                        "Primary advertise packet exceeded 31 bytes. Automatically retrying with compact layout (Name in Scan Response)..."
                    )
                    startAdvertising(fallbackMode = true)
                    return
                }

                val errorMsg = when (errorCode) {
                    ADVERTISE_FAILED_ALREADY_STARTED -> "ADVERTISE_FAILED_ALREADY_STARTED (1)"
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "ADVERTISE_FAILED_DATA_TOO_LARGE (2) - Packet exceeds 31 bytes limit"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS (3) - Hardware resource exhausted"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "ADVERTISE_FAILED_INTERNAL_ERROR (4) - Bluetooth controller error"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "ADVERTISE_FAILED_FEATURE_UNSUPPORTED (5) - Hardware does not support BLE Peripheral advertising"
                    else -> "Unknown error code: $errorCode"
                }
                _state.value = AdvertisingState.FAILED
                _lastError.value = errorMsg
                DiagnosticLogger.e(LogCategory.BLE_ADV, "BLE Advertising FAILED: $errorMsg")
            }
        }
        callback = newCallback

        try {
            advertiser?.startAdvertising(settings, advertiseData, scanResponseData, newCallback)
            DiagnosticLogger.d(LogCategory.BLE_ADV, "Invoked startAdvertising API call (fallbackMode=$fallbackMode)")
            return true
        } catch (t: Throwable) {
            _state.value = AdvertisingState.FAILED
            _lastError.value = t.message
            DiagnosticLogger.e(LogCategory.BLE_ADV, "Exception starting advertising", t)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        val cb = callback
        if (cb != null) {
            try {
                advertiser?.stopAdvertising(cb)
                DiagnosticLogger.i(LogCategory.BLE_ADV, "BLE Advertising STOPPED")
            } catch (t: Throwable) {
                DiagnosticLogger.w(LogCategory.BLE_ADV, "Exception stopping advertising: ${t.message}")
            } finally {
                callback = null
            }
        }
        _state.value = AdvertisingState.STOPPED
    }
}
