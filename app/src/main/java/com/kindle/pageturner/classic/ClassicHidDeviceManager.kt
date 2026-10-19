package com.kindle.pageturner.classic

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.bluetooth.*
import android.content.Context
import android.os.Build
import com.kindle.pageturner.ble.BleConstants
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import com.kindle.pageturner.hid.HidKeyAction
import com.kindle.pageturner.hid.HidReportMap
import com.kindle.pageturner.hid.HidStateMachine
import com.kindle.pageturner.hid.KeySendResult
import com.kindle.pageturner.root.ClassicBluetoothFeatureAssessment
import com.kindle.pageturner.root.ClassicHidRegistrationState
import com.kindle.pageturner.root.DeviceIdentityVerificationState
import com.kindle.pageturner.root.KindleAcceptanceState
import com.kindle.pageturner.root.RootEnvironmentReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Classic Bluetooth HID Device Manager (BluetoothHidDevice API, Android 9.0+ / API 28).
 *
 * Technical & Reverse-Engineering Boundary:
 * - Android's public [BluetoothHidDeviceAppSdpSettings] API only configures SDP Service Name,
 *   Description, Provider, Subclass, and HID Report Descriptor.
 * - It CANNOT set Bluetooth Device Identification (DI / Service 0x1200) VendorID (0x0171) or
 *   ProductID (0x0443). Those attributes are controlled globally by the Android system Bluetooth
 *   stack (`com.android.bluetooth`).
 * - Consequently, `registrationState == REGISTERED` ONLY means the local Android Bluetooth stack
 *   registered the HID SDP record; it does NOT mean the device identity is `0x0171:0x0443` or that
 *   Kindle's `btmgr` allowlist will accept the connection.
 */
@TargetApi(Build.VERSION_CODES.P)
class ClassicHidDeviceManager(
    private val context: Context?,
    private val scope: CoroutineScope,
    private val bluetoothAdapterProvider: () -> BluetoothAdapter?,
    private val sdkIntProvider: () -> Int = {
        val raw = Build.VERSION.SDK_INT
        if (raw == 0) 34 else raw
    }
) {
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _registrationState = MutableStateFlow(ClassicHidRegistrationState.NOT_REGISTERED)
    val registrationState: StateFlow<ClassicHidRegistrationState> = _registrationState.asStateFlow()

    private val _deviceIdentityVerificationState = MutableStateFlow(
        DeviceIdentityVerificationState.UNVERIFIED_DEFAULT_OEM_IDENTITY
    )
    val deviceIdentityVerificationState: StateFlow<DeviceIdentityVerificationState> =
        _deviceIdentityVerificationState.asStateFlow()

    private val _kindleAcceptanceState = MutableStateFlow(
        KindleAcceptanceState.REJECTED_OR_UNVERIFIED_CLASSIC_ALLOWLIST
    )
    val kindleAcceptanceState: StateFlow<KindleAcceptanceState> =
        _kindleAcceptanceState.asStateFlow()

    /**
     * Never true unless registration, device identity (0x0171:0x0443), and Kindle acceptance are all verified.
     */
    private val _isFeatureSupported = MutableStateFlow(false)
    val isFeatureSupported: StateFlow<Boolean> = _isFeatureSupported.asStateFlow()

    private val _lastResult = MutableStateFlow("None")
    val lastResult: StateFlow<String> = _lastResult.asStateFlow()

    val hidStateMachine = HidStateMachine(
        scope = scope,
        sendReportDelegate = { payload ->
            sendHidReportDirect(payload)
        },
        isConnectedProvider = {
            _isConnected.value && connectedDevice != null
        },
        isNotificationReadyProvider = {
            _isRegistered.value
        },
        includeReportId = false // Classic BluetoothHidDevice uses 8-byte payload + reportId parameter
    )

    private var lastEnvReport: RootEnvironmentReport? = null

    /**
     * Evaluates the full 8-dimension feature assessment combining [envReport] (or the cached
     * last environment report) with the current Classic Bluetooth HID registration and identity
     * verification state.
     */
    fun evaluateFeatureAssessment(envReport: RootEnvironmentReport? = null): ClassicBluetoothFeatureAssessment {
        if (envReport != null) {
            lastEnvReport = envReport
        }
        val effectiveReport = envReport ?: lastEnvReport ?: com.kindle.pageturner.root.RootHelper.lastEnvironmentReport.value
        if (effectiveReport == null) {
            return ClassicBluetoothFeatureAssessment.unchecked(
                registrationState = _registrationState.value
            )
        }
        val base = effectiveReport.toFeatureAssessment(registrationState = _registrationState.value)
        _deviceIdentityVerificationState.value = base.deviceIdentityVerificationState
        _kindleAcceptanceState.value = base.kindleAcceptanceState
        _isFeatureSupported.value = base.isClassicHidFeatureSupported
        return base
    }

    /**
     * Updates HID app registration state while strictly keeping device identity verification
     * separate from SDP registration success.
     */
    fun onRegistrationStatusUpdated(
        registered: Boolean,
        pluggedDeviceAddress: String? = null,
        envReport: RootEnvironmentReport? = null
    ) {
        _isRegistered.value = registered
        _registrationState.value = if (registered) {
            ClassicHidRegistrationState.REGISTERED
        } else {
            ClassicHidRegistrationState.NOT_REGISTERED
        }
        // Even when BluetoothHidDevice SDP registration succeeds, Android's public API does NOT set
        // VID=0x0171 / PID=0x0443, so device identity remains unverified unless proven otherwise.
        val assessment = evaluateFeatureAssessment(envReport)
        _isFeatureSupported.value = assessment.isClassicHidFeatureSupported
        if (registered && !assessment.deviceIdentityVerificationState.isIdentityVerified) {
            _lastResult.value =
                "Classic HID 已注册，但设备身份 (0x0171:0x0443) 未验证；功能未标记为已支持"
        }
        DiagnosticLogger.i(
            LogCategory.SYSTEM,
            "BluetoothHidDevice AppStatusChanged: registered=$registered, plugged=$pluggedDeviceAddress, " +
                "identityState=${_deviceIdentityVerificationState.value.name}, featureSupported=${_isFeatureSupported.value}"
        )
    }

    @SuppressLint("MissingPermission")
    fun registerHidProfile(): Boolean {
        if (sdkIntProvider() < Build.VERSION_CODES.P) {
            _registrationState.value = ClassicHidRegistrationState.UNSUPPORTED_API
            _isFeatureSupported.value = false
            DiagnosticLogger.w(LogCategory.SYSTEM, "BluetoothHidDevice requires Android 9.0+ (API 28)")
            return false
        }
        val ctx = context ?: run {
            _registrationState.value = ClassicHidRegistrationState.REGISTRATION_FAILED
            return false
        }
        val adapter = bluetoothAdapterProvider() ?: run {
            _registrationState.value = ClassicHidRegistrationState.REGISTRATION_FAILED
            return false
        }
        if (!adapter.isEnabled) {
            _registrationState.value = ClassicHidRegistrationState.REGISTRATION_FAILED
            return false
        }

        _registrationState.value = ClassicHidRegistrationState.REGISTERING
        DiagnosticLogger.i(LogCategory.SYSTEM, "Registering Classic Bluetooth HID Device Profile proxy...")

        val success = adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as? BluetoothHidDevice
                    DiagnosticLogger.i(LogCategory.SYSTEM, "BluetoothHidDevice proxy connected, registering app SDP...")
                    registerAppInternal()
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                    _isRegistered.value = false
                    _isConnected.value = false
                    _registrationState.value = ClassicHidRegistrationState.NOT_REGISTERED
                    _isFeatureSupported.value = false
                    DiagnosticLogger.i(LogCategory.SYSTEM, "BluetoothHidDevice proxy disconnected")
                }
            }
        }, BluetoothProfile.HID_DEVICE)

        if (!success) {
            _registrationState.value = ClassicHidRegistrationState.REGISTRATION_FAILED
            _isFeatureSupported.value = false
        }
        return success
    }

    @SuppressLint("MissingPermission")
    private fun registerAppInternal() {
        if (sdkIntProvider() < Build.VERSION_CODES.P) return
        val proxy = hidDevice ?: return

        val sdp = BluetoothHidDeviceAppSdpSettings(
            BleConstants.DEFAULT_DEVICE_NAME,
            "Kindle Page Turner Remote",
            "Amazon",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidReportMap.KEYBOARD_REPORT_DESCRIPTOR
        )

        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        val regInitiated = proxy.registerApp(sdp, qos, null, Runnable::run, getOrCreateHidCallback())
        if (!regInitiated) {
            _registrationState.value = ClassicHidRegistrationState.REGISTRATION_FAILED
            _isFeatureSupported.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun unregisterHidProfile() {
        if (sdkIntProvider() < Build.VERSION_CODES.P) return
        val proxy = hidDevice
        try {
            proxy?.unregisterApp()
            DiagnosticLogger.i(LogCategory.SYSTEM, "BluetoothHidDevice app unregistered")
        } catch (t: Throwable) {
            DiagnosticLogger.w(LogCategory.SYSTEM, "Error unregistering HID app: ${t.message}")
        }
        val adapter = bluetoothAdapterProvider()
        if (adapter != null && proxy != null) {
            try {
                adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, proxy)
            } catch (_: Throwable) {}
        }
        hidDevice = null
        _isRegistered.value = false
        _isConnected.value = false
        _registrationState.value = ClassicHidRegistrationState.NOT_REGISTERED
        _isFeatureSupported.value = false
        connectedDevice = null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (sdkIntProvider() < Build.VERSION_CODES.P) return
        val proxy = hidDevice ?: return
        val target = connectedDevice ?: return
        try {
            proxy.disconnect(target)
            DiagnosticLogger.i(LogCategory.SYSTEM, "BluetoothHidDevice requested disconnect from ${target.address}")
        } catch (t: Throwable) {
            DiagnosticLogger.w(LogCategory.SYSTEM, "Error disconnecting BluetoothHidDevice: ${t.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendHidReportDirect(payload: ByteArray): Boolean {
        if (sdkIntProvider() < Build.VERSION_CODES.P) return false
        val proxy = hidDevice ?: return false
        val target = connectedDevice ?: return false
        return proxy.sendReport(target, BleConstants.REPORT_ID_KEYBOARD.toInt(), payload)
    }

    fun sendPageForward(onResult: ((KeySendResult) -> Unit)? = null) {
        scope.launch {
            val result = hidStateMachine.sendKey(HidKeyAction.PAGE_DOWN)
            _lastResult.value = "Page Forward: $result"
            onResult?.invoke(result)
        }
    }

    fun sendPageBackward(onResult: ((KeySendResult) -> Unit)? = null) {
        scope.launch {
            val result = hidStateMachine.sendKey(HidKeyAction.PAGE_UP)
            _lastResult.value = "Page Backward: $result"
            onResult?.invoke(result)
        }
    }

    fun sendBrightnessUp(onResult: ((KeySendResult) -> Unit)? = null) {
        scope.launch {
            val result = hidStateMachine.sendKey(HidKeyAction.BRIGHTNESS_UP)
            _lastResult.value = "Brightness Up: $result"
            onResult?.invoke(result)
        }
    }

    fun sendBrightnessDown(onResult: ((KeySendResult) -> Unit)? = null) {
        scope.launch {
            val result = hidStateMachine.sendKey(HidKeyAction.BRIGHTNESS_DOWN)
            _lastResult.value = "Brightness Down: $result"
            onResult?.invoke(result)
        }
    }

    private var hidCallbackInstance: BluetoothHidDevice.Callback? = null

    private fun getOrCreateHidCallback(): BluetoothHidDevice.Callback {
        hidCallbackInstance?.let { return it }
        val callback = object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                onRegistrationStatusUpdated(
                    registered = registered,
                    pluggedDeviceAddress = pluggedDevice?.address
                )
            }

            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevice = device
                        _isConnected.value = true
                        DiagnosticLogger.i(LogCategory.SYSTEM, "BluetoothHidDevice CONNECTED: ${device?.address}")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevice = null
                        _isConnected.value = false
                        DiagnosticLogger.i(LogCategory.SYSTEM, "BluetoothHidDevice DISCONNECTED: ${device?.address}")
                    }
                }
            }
        }
        hidCallbackInstance = callback
        return callback
    }
}

