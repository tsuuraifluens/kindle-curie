package com.kindle.pageturner.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import com.kindle.pageturner.battery.BatteryServiceManager
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import com.kindle.pageturner.hid.HidReportBuilder
import com.kindle.pageturner.hid.HidReportMap
import com.kindle.pageturner.identity.DeviceIdentityProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/**
 * BLE GATT Server Manager for Kindle HOGP Peripheral.
 *
 * Evidence Mapping:
 * - hid_protocol_spec.md Section 3 & 4
 * - EVID-01: devices.json (VID/PID 0x0171:0x0443)
 * - EVID-04: ioctl EVIOCGID (Linux UHID creates input_id from PnP ID 0x2A50)
 * - EVID-07: acsbtfd BleHidPageTurnerPolicy (Battery monitoring 0x180F)
 * - EVID-09: acsbtfd BtGattClient::ReadChar (Firmware revision string probed: DIS 0x2A26)
 * - EVID-11: Dynamic Report Map (0x2A4B parsed by kernel hid-generic)
 */
class BleGattServerManager(
    private val context: Context,
    private val identityProfile: DeviceIdentityProfile = DeviceIdentityProfile.DEFAULT,
    val batteryManager: BatteryServiceManager = BatteryServiceManager()
) {
    private var gattServer: BluetoothGattServer? = null
    private var bluetoothManager: BluetoothManager? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _isHidNotificationReady = MutableStateFlow(false)
    val isHidNotificationReady: StateFlow<Boolean> = _isHidNotificationReady.asStateFlow()

    // Service addition tracking
    val serviceStatusMap = ConcurrentHashMap<UUID, Boolean>()
    var hidServiceSupportedBySystem: Boolean = false
        private set

    // Characteristics cache
    private var hidInputReportChar: BluetoothGattCharacteristic? = null
    private var batteryLevelChar: BluetoothGattCharacteristic? = null

    // Subscriptions map: deviceAddress -> set of subscribed characteristic UUIDs
    private val deviceSubscriptions = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val pendingServicesQueue = java.util.ArrayDeque<BluetoothGattService>()

    @SuppressLint("MissingPermission")
    fun startGattServer(): Boolean {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothManager == null) {
            DiagnosticLogger.e(LogCategory.GATT_SERVER, "BluetoothManager not available")
            return false
        }

        stopGattServer()

        val server = bluetoothManager?.openGattServer(context, gattServerCallback)
        if (server == null) {
            DiagnosticLogger.e(LogCategory.GATT_SERVER, "Failed to open BluetoothGattServer")
            return false
        }
        gattServer = server

        DiagnosticLogger.i(LogCategory.GATT_SERVER, "BluetoothGattServer opened successfully")

        // Queue services to be added sequentially to avoid AOSP GATT concurrency rejection
        queueAndAddServices()

        return true
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val server = gattServer ?: return
        val device = _connectedDevice.value ?: return
        try {
            server.cancelConnection(device)
            DiagnosticLogger.i(LogCategory.GATT_SERVER, "Requested disconnect for ${device.address}")
        } catch (t: Throwable) {
            DiagnosticLogger.e(LogCategory.GATT_SERVER, "Failed to cancel connection", t)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopGattServer() {
        pendingServicesQueue.clear()
        val device = _connectedDevice.value
        if (device != null) {
            try {
                gattServer?.cancelConnection(device)
            } catch (_: Throwable) {}
        }
        gattServer?.let { server ->
            try {
                server.clearServices()
                server.close()
                DiagnosticLogger.i(LogCategory.GATT_SERVER, "BluetoothGattServer closed and services cleared")
            } catch (t: Throwable) {
                DiagnosticLogger.w(LogCategory.GATT_SERVER, "Error closing gattServer: ${t.message}")
            }
        }
        gattServer = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
        _isHidNotificationReady.value = false
        deviceSubscriptions.clear()
        serviceStatusMap.clear()
    }

    @SuppressLint("MissingPermission")
    private fun queueAndAddServices() {
        pendingServicesQueue.clear()
        pendingServicesQueue.add(buildGapService())
        pendingServicesQueue.add(buildDeviceInformationService())
        if (batteryManager.isEnabled.value) {
            pendingServicesQueue.add(buildBatteryService())
        }
        pendingServicesQueue.add(buildHidService())
        addNextService()
    }

    @SuppressLint("MissingPermission")
    private fun addNextService() {
        val next = pendingServicesQueue.poll() ?: return
        val invoked = gattServer?.addService(next) ?: false
        DiagnosticLogger.d(LogCategory.GATT_SERVER, "Invoked addService for ${next.uuid}: $invoked")
        if (!invoked) {
            serviceStatusMap[next.uuid] = false
            DiagnosticLogger.w(LogCategory.GATT_SERVER, "addService returned false immediately for ${next.uuid}, trying next")
            addNextService()
        }
    }

    private fun buildGapService(): BluetoothGattService {
        val gap = BluetoothGattService(BleConstants.UUID_SERVICE_GAP, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val nameChar = BluetoothGattCharacteristic(
            UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"),
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        nameChar.value = identityProfile.deviceName.toByteArray(Charsets.UTF_8)
        gap.addCharacteristic(nameChar)

        val appearanceChar = BluetoothGattCharacteristic(
            UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb"),
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        appearanceChar.value = byteArrayOf(0xC1.toByte(), 0x03.toByte()) // 0x03C1 Keyboard
        gap.addCharacteristic(appearanceChar)
        return gap
    }

    private fun buildDeviceInformationService(): BluetoothGattService {
        val dis = BluetoothGattService(BleConstants.UUID_SERVICE_DIS, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Manufacturer Name (0x2A29)
        val mfgChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_MANUFACTURER_NAME,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        mfgChar.value = identityProfile.manufacturerName.toByteArray(Charsets.UTF_8)
        dis.addCharacteristic(mfgChar)

        // Model Number (0x2A24)
        val modelChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_MODEL_NUMBER,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        modelChar.value = identityProfile.modelNumber.toByteArray(Charsets.UTF_8)
        dis.addCharacteristic(modelChar)

        // Serial Number (0x2A25)
        val serialChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_SERIAL_NUMBER,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        serialChar.value = identityProfile.serialNumber.toByteArray(Charsets.UTF_8)
        dis.addCharacteristic(serialChar)

        // Firmware Revision (0x2A26) - Probed by acsbtfd (EVID-09)
        val fwChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_FIRMWARE_REVISION,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        fwChar.value = identityProfile.firmwareRevision.toByteArray(Charsets.UTF_8)
        dis.addCharacteristic(fwChar)

        // Hardware Revision (0x2A27)
        val hwChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_HARDWARE_REVISION,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        hwChar.value = identityProfile.hardwareRevision.toByteArray(Charsets.UTF_8)
        dis.addCharacteristic(hwChar)

        // Software Revision (0x2A28)
        val swChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_SOFTWARE_REVISION,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        swChar.value = identityProfile.softwareRevision.toByteArray(Charsets.UTF_8)
        dis.addCharacteristic(swChar)

        // PnP ID (0x2A50) - 7 bytes [WHITELIST PASS KEY: EVID-01, EVID-03, EVID-04]
        val pnpChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_PNP_ID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        pnpChar.value = identityProfile.pnpId.toByteArray()
        dis.addCharacteristic(pnpChar)

        DiagnosticLogger.d(LogCategory.GATT_SERVER, "Built DIS Service (0x180A) with PnP ID=${identityProfile.pnpId.toHexString()}")
        return dis
    }

    private fun buildBatteryService(): BluetoothGattService {
        val bas = BluetoothGattService(BleConstants.UUID_SERVICE_BAS, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val battLevelChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_BATTERY_LEVEL,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        battLevelChar.value = batteryManager.getBatteryLevelPayload()

        val cccd = BluetoothGattDescriptor(
            BleConstants.UUID_DESC_CCCD,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        cccd.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        battLevelChar.addDescriptor(cccd)

        bas.addCharacteristic(battLevelChar)
        batteryLevelChar = battLevelChar

        DiagnosticLogger.d(LogCategory.GATT_SERVER, "Built Battery Service (0x180F)")
        return bas
    }

    private fun buildHidService(): BluetoothGattService {
        val hids = BluetoothGattService(BleConstants.UUID_SERVICE_HIDS, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // 1. HID Information (0x2A4A)
        val hidInfoChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_HID_INFO,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        hidInfoChar.value = BleConstants.HID_INFO_PAYLOAD
        hids.addCharacteristic(hidInfoChar)

        // 2. Report Map (0x2A4B) - Standard Keyboard Descriptor (EVID-11)
        val reportMapChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_REPORT_MAP,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        reportMapChar.value = HidReportMap.KEYBOARD_REPORT_DESCRIPTOR
        hids.addCharacteristic(reportMapChar)

        // 3. Input Report (0x2A4D) - Read, Notify
        val inputChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_REPORT,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        inputChar.value = HidReportBuilder.createReleaseReport(includeReportId = true)

        // CCCD Descriptor (0x2902)
        val cccd = BluetoothGattDescriptor(
            BleConstants.UUID_DESC_CCCD,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        cccd.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        inputChar.addDescriptor(cccd)

        // Report Reference Descriptor (0x2908) - [0x01, 0x01] (Report ID 1, Input)
        val reportRef = BluetoothGattDescriptor(
            BleConstants.UUID_DESC_REPORT_REFERENCE,
            BluetoothGattDescriptor.PERMISSION_READ
        )
        reportRef.value = BleConstants.REPORT_REFERENCE_INPUT_PAYLOAD
        inputChar.addDescriptor(reportRef)

        hids.addCharacteristic(inputChar)
        hidInputReportChar = inputChar

        // 4. Protocol Mode (0x2A4E)
        val protocolModeChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_PROTOCOL_MODE,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        protocolModeChar.value = byteArrayOf(0x01) // Report Mode
        hids.addCharacteristic(protocolModeChar)

        // 5. HID Control Point (0x2A4C)
        val ctrlPointChar = BluetoothGattCharacteristic(
            BleConstants.UUID_CHAR_HID_CONTROL_POINT,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        hids.addCharacteristic(ctrlPointChar)

        DiagnosticLogger.d(LogCategory.GATT_SERVER, "Built HID Service (0x1812)")
        return hids
    }

    @SuppressLint("MissingPermission")
    fun sendInputReport(payload: ByteArray): Boolean {
        val server = gattServer ?: return false
        val device = _connectedDevice.value ?: return false
        val char = hidInputReportChar ?: return false

        char.value = payload
        val sent = server.notifyCharacteristicChanged(device, char, false)
        if (!sent) {
            DiagnosticLogger.w(
                LogCategory.HID_REPORT,
                "Failed to send input report notification via GATT server"
            )
        }
        return sent
    }

    @SuppressLint("MissingPermission")
    fun sendBatteryNotification(level: Int): Boolean {
        val server = gattServer ?: return false
        val device = _connectedDevice.value ?: return false
        val char = batteryLevelChar ?: return false

        char.value = byteArrayOf((level and 0xFF).toByte())
        return server.notifyCharacteristicChanged(device, char, false)
    }

    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            val serviceUuid = service?.uuid ?: return
            val success = (status == BluetoothGatt.GATT_SUCCESS)
            serviceStatusMap[serviceUuid] = success

            if (serviceUuid == BleConstants.UUID_SERVICE_HIDS) {
                hidServiceSupportedBySystem = success
                if (success) {
                    DiagnosticLogger.i(
                        LogCategory.GATT_SERVER,
                        "HID Service (0x1812) added successfully to GATT Server"
                    )
                } else {
                    DiagnosticLogger.e(
                        LogCategory.GATT_SERVER,
                        "CRITICAL: System rejected HID Service (0x1812) with status=$status. " +
                                "Standard AOSP blocks non-system apps from creating 0x1812 (see android_app_implementation_spec.md Sec 1.1). " +
                                "Use Root DID mode or Classic HID mode."
                    )
                }
            } else {
                DiagnosticLogger.i(
                    LogCategory.GATT_SERVER,
                    "Service added: $serviceUuid, status=$status (${if (success) "SUCCESS" else "FAILED"})"
                )
            }
            // Proceed to add next service in queue
            addNextService()
        }

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val devAddress = device?.address ?: "Unknown"
            val devName = try { device?.name } catch (_: SecurityException) { null } ?: "Unknown"

            DiagnosticLogger.i(
                LogCategory.GATT_SERVER,
                "Connection state change: $devName ($devAddress), status=$status, newState=$newState"
            )

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectedDevice.value = device
                    _connectionState.value = ConnectionState.CONNECTED
                    DiagnosticLogger.i(LogCategory.GATT_SERVER, "Client CONNECTED: $devName ($devAddress)")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectedDevice.value = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _isHidNotificationReady.value = false
                    device?.address?.let { deviceSubscriptions.remove(it) }
                    DiagnosticLogger.i(LogCategory.GATT_SERVER, "Client DISCONNECTED: $devAddress")
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val charUuid = characteristic?.uuid ?: return

            if (charUuid == BleConstants.UUID_CHAR_BATTERY_LEVEL) {
                if (!batteryManager.isEnabled.value) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                    return
                }
                characteristic.value = batteryManager.getBatteryLevelPayload()
                DiagnosticLogger.i(
                    LogCategory.BATTERY,
                    "Kindle READ Battery Level (0x2A19): ${batteryManager.batteryLevel.value}%"
                )
            } else if (charUuid == BleConstants.UUID_CHAR_PNP_ID) {
                DiagnosticLogger.i(
                    LogCategory.IDENTITY,
                    "Kindle READ PnP ID (0x2A50)! Responding with Amazon 0x0171:0x0443 (EVID-04)"
                )
            } else if (charUuid == BleConstants.UUID_CHAR_FIRMWARE_REVISION) {
                DiagnosticLogger.i(
                    LogCategory.IDENTITY,
                    "Kindle acsbtfd READ Firmware Revision (0x2A26)! Probed by Kindle (EVID-09)"
                )
            }

            val value = characteristic.value ?: ByteArray(0)

            DiagnosticLogger.d(
                LogCategory.GATT_SERVER,
                "Read request for Characteristic: $charUuid (offset=$offset, totalLen=${value.size})"
            )

            if (offset < 0 || offset > value.size) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                return
            }

            val slice = if (offset == 0) value else value.copyOfRange(offset, value.size)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val charUuid = characteristic?.uuid ?: return
            val valHex = value?.let { HidReportBuilder.toHexString(it) } ?: "null"

            DiagnosticLogger.d(
                LogCategory.GATT_SERVER,
                "Write request on Char: $charUuid, value=[$valHex], responseNeeded=$responseNeeded"
            )

            characteristic.value = value

            if (charUuid == BleConstants.UUID_CHAR_HID_CONTROL_POINT) {
                val cmd = value?.firstOrNull()?.toInt() ?: -1
                val cmdName = when (cmd) {
                    0 -> "SUSPEND (0x00)"
                    1 -> "EXIT SUSPEND (0x01)"
                    else -> "UNKNOWN ($cmd)"
                }
                DiagnosticLogger.i(LogCategory.GATT_SERVER, "Kindle set HID Control Point: $cmdName")
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            val descUuid = descriptor?.uuid ?: return
            val value = descriptor.value ?: ByteArray(0)

            DiagnosticLogger.d(
                LogCategory.GATT_SERVER,
                "Read request for Descriptor: $descUuid (offset=$offset)"
            )

            if (offset < 0 || offset > value.size) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                return
            }

            val slice = if (offset == 0) value else value.copyOfRange(offset, value.size)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val descUuid = descriptor?.uuid ?: return
            val charUuid = descriptor.characteristic?.uuid
            val devAddr = device?.address ?: "Unknown"

            descriptor.value = value

            if (descUuid == BleConstants.UUID_DESC_CCCD) {
                val isNotifyEnabled = Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                        (value != null && value.isNotEmpty() && (value[0].toInt() and 0x01 != 0))
                val isIndicateEnabled = Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE) ||
                        (value != null && value.isNotEmpty() && (value[0].toInt() and 0x02 != 0))

                val subSet = deviceSubscriptions.getOrPut(devAddr) { ConcurrentHashMap.newKeySet() }
                if (isNotifyEnabled || isIndicateEnabled) {
                    if (charUuid != null) subSet.add(charUuid)
                    if (charUuid == BleConstants.UUID_CHAR_REPORT) {
                        _isHidNotificationReady.value = true
                        DiagnosticLogger.i(
                            LogCategory.HID_REPORT,
                            "Kindle ENABLED HID Input Report notifications! Device is READY to turn pages."
                        )
                    } else if (charUuid == BleConstants.UUID_CHAR_BATTERY_LEVEL) {
                        batteryManager.isSubscribed = true
                        DiagnosticLogger.i(
                            LogCategory.BATTERY,
                            "Kindle ENABLED Battery notifications."
                        )
                    }
                } else {
                    if (charUuid != null) subSet.remove(charUuid)
                    if (charUuid == BleConstants.UUID_CHAR_REPORT) {
                        _isHidNotificationReady.value = false
                        DiagnosticLogger.i(
                            LogCategory.HID_REPORT,
                            "Kindle DISABLED HID Input Report notifications."
                        )
                    } else if (charUuid == BleConstants.UUID_CHAR_BATTERY_LEVEL) {
                        batteryManager.isSubscribed = false
                    }
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            DiagnosticLogger.d(
                LogCategory.GATT_SERVER,
                "Notification sent to ${device?.address}, status=${if (status == BluetoothGatt.GATT_SUCCESS) "SUCCESS" else "FAILED($status)"}"
            )
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            DiagnosticLogger.i(LogCategory.GATT_SERVER, "MTU changed for ${device?.address}: $mtu")
        }
    }
}
