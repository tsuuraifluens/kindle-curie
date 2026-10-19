package com.kindle.pageturner.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.kindle.pageturner.battery.BatteryServiceManager
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import com.kindle.pageturner.hid.HidKeyAction
import com.kindle.pageturner.hid.HidStateMachine
import com.kindle.pageturner.hid.KeySendResult
import com.kindle.pageturner.identity.DeviceIdentityProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ControllerStatus {
    IDLE,
    STARTING,
    ADVERTISING_READY,
    CONNECTED,
    ERROR
}

/**
 * Main BLE Peripheral Controller orchestrating Advertising, GATT Server, Battery, and HID State Machine.
 */
class BlePeripheralController(
    private val context: Context,
    private val scope: CoroutineScope,
    val identityProfile: DeviceIdentityProfile = DeviceIdentityProfile.DEFAULT,
    bluetoothAdapterProvider: () -> BluetoothAdapter?
) {
    val batteryManager: BatteryServiceManager = BatteryServiceManager()
    val gattServerManager: BleGattServerManager = BleGattServerManager(context, identityProfile, batteryManager)
    val advertiserManager: BleAdvertiserManager = BleAdvertiserManager(bluetoothAdapterProvider)

    val hidStateMachine: HidStateMachine = HidStateMachine(
        scope = scope,
        sendReportDelegate = { payload: ByteArray ->
            gattServerManager.sendInputReport(payload)
        },
        isConnectedProvider = {
            gattServerManager.connectionState.value == ConnectionState.CONNECTED
        },
        isNotificationReadyProvider = {
            gattServerManager.isHidNotificationReady.value
        },
        includeReportId = true
    )

    var autoResumeAdvertising: Boolean = true

    private val _controllerStatus = MutableStateFlow(ControllerStatus.IDLE)
    val controllerStatus: StateFlow<ControllerStatus> = _controllerStatus.asStateFlow()

    private val _lastActionResult = MutableStateFlow<String>("None")
    val lastActionResult: StateFlow<String> = _lastActionResult.asStateFlow()

    init {
        batteryManager.notifyBatteryDelegate = { level ->
            gattServerManager.sendBatteryNotification(level)
        }

        // Observe connection state changes
        scope.launch {
            gattServerManager.connectionState.collect { connState ->
                when (connState) {
                    ConnectionState.CONNECTED -> {
                        _controllerStatus.value = ControllerStatus.CONNECTED
                    }
                    ConnectionState.DISCONNECTED -> {
                        if (_controllerStatus.value == ControllerStatus.CONNECTED) {
                            if (autoResumeAdvertising) {
                                _controllerStatus.value = ControllerStatus.ADVERTISING_READY
                                advertiserManager.startAdvertising()
                                DiagnosticLogger.i(LogCategory.BLE_ADV, "Client disconnected, auto-resumed BLE advertising")
                            } else {
                                _controllerStatus.value = ControllerStatus.IDLE
                                DiagnosticLogger.i(LogCategory.BLE_ADV, "Client disconnected, auto-resume is disabled")
                            }
                        }
                    }
                    ConnectionState.CONNECTING -> {}
                }
            }
        }
    }

    fun applySettings(
        deviceName: String,
        batteryEnabled: Boolean,
        batteryLevel: Int,
        autoResume: Boolean,
        includeReportId: Boolean
    ) {
        advertiserManager.deviceName = deviceName
        advertiserManager.includeBatteryService = batteryEnabled
        batteryManager.setEnabled(batteryEnabled)
        batteryManager.updateBatteryLevel(batteryLevel)
        autoResumeAdvertising = autoResume
        hidStateMachine.includeReportId = includeReportId
        DiagnosticLogger.i(
            LogCategory.SYSTEM,
            "Applied emulation settings: name='$deviceName', battery=$batteryEnabled($batteryLevel%), autoResume=$autoResume, reportId=$includeReportId"
        )
    }

    fun startEmulation(): Boolean {
        DiagnosticLogger.i(LogCategory.SYSTEM, "Starting BLE Kindle Remote Emulation")
        _controllerStatus.value = ControllerStatus.STARTING

        // 1. Open and setup GATT Server
        val gattOk = gattServerManager.startGattServer()
        if (!gattOk) {
            _controllerStatus.value = ControllerStatus.ERROR
            _lastActionResult.value = "Failed to start GATT Server"
            return false
        }

        // 2. Start Advertising
        val advOk = advertiserManager.startAdvertising()
        if (!advOk) {
            _controllerStatus.value = ControllerStatus.ERROR
            _lastActionResult.value = "Failed to start Advertising: ${advertiserManager.lastError.value}"
            return false
        }

        _controllerStatus.value = ControllerStatus.ADVERTISING_READY
        _lastActionResult.value = "Advertising started"
        return true
    }

    fun disconnect() {
        gattServerManager.disconnect()
        _lastActionResult.value = "Requested disconnect"
    }

    fun stopEmulation() {
        DiagnosticLogger.i(LogCategory.SYSTEM, "Stopping BLE Kindle Remote Emulation")
        // Set IDLE before stopping GATT server to avoid auto-resume race condition
        _controllerStatus.value = ControllerStatus.IDLE
        advertiserManager.stopAdvertising()
        gattServerManager.stopGattServer()
        _lastActionResult.value = "Emulation stopped"
    }

    fun resumeAdvertising(): Boolean {
        val ok = advertiserManager.startAdvertising()
        if (ok) {
            _controllerStatus.value = ControllerStatus.ADVERTISING_READY
        }
        return ok
    }

    fun sendPageForward(onResult: ((KeySendResult) -> Unit)? = null) {
        scope.launch {
            val result = hidStateMachine.sendKey(HidKeyAction.PAGE_DOWN)
            updateActionResult("Page Forward", result)
            onResult?.invoke(result)
        }
    }

    fun sendPageBackward(onResult: ((KeySendResult) -> Unit)? = null) {
        scope.launch {
            val result = hidStateMachine.sendKey(HidKeyAction.PAGE_UP)
            updateActionResult("Page Backward", result)
            onResult?.invoke(result)
        }
    }

    fun sendBrightnessUp(onResult: ((KeySendResult) -> Unit)? = null) {
        scope.launch {
            val result = hidStateMachine.sendKey(HidKeyAction.BRIGHTNESS_UP)
            updateActionResult("Brightness Up", result)
            onResult?.invoke(result)
        }
    }

    fun sendBrightnessDown(onResult: ((KeySendResult) -> Unit)? = null) {
        scope.launch {
            val result = hidStateMachine.sendKey(HidKeyAction.BRIGHTNESS_DOWN)
            updateActionResult("Brightness Down", result)
            onResult?.invoke(result)
        }
    }

    private fun updateActionResult(actionName: String, result: KeySendResult) {
        val summary = when (result) {
            is KeySendResult.Success -> "$actionName OK (${result.durationMs}ms)"
            is KeySendResult.NotConnected -> "$actionName FAILED: Kindle not connected"
            is KeySendResult.NotSubscribed -> "$actionName FAILED: Notifications not subscribed"
            is KeySendResult.Debounced -> "$actionName DEBOUNCED (${result.intervalMs}ms)"
            is KeySendResult.RateLimited -> "$actionName RATE LIMITED (${result.eventsInWindow}/s)"
            is KeySendResult.Busy -> "$actionName BUSY"
            is KeySendResult.Failed -> "$actionName ERROR: ${result.error}"
        }
        _lastActionResult.value = summary
    }
}
