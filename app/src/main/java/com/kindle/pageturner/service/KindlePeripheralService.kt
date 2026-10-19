package com.kindle.pageturner.service

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kindle.pageturner.ble.BleConstants
import com.kindle.pageturner.ble.BlePeripheralController
import com.kindle.pageturner.ble.ConnectionState
import com.kindle.pageturner.classic.ClassicHidDeviceManager
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import com.kindle.pageturner.diagnostics.LogLevel
import com.kindle.pageturner.hid.KeySendResult
import com.kindle.pageturner.ui.MainActivity
import com.kindle.pageturner.ui.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service maintaining BLE Peripheral and Classic HID Device lifecycle in background.
 */
class KindlePeripheralService : Service() {

    private val binder = LocalBinder()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    lateinit var controller: BlePeripheralController
        private set

    lateinit var classicHidManager: ClassicHidDeviceManager
        private set

    private val _activeMode = MutableStateFlow(EmulationMode.BLE_PERIPHERAL)
    val activeMode: StateFlow<EmulationMode> = _activeMode.asStateFlow()

    private var isBluetoothReceiverRegistered = false

    inner class LocalBinder : Binder() {
        fun getService(): KindlePeripheralService = this@KindlePeripheralService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.i(LogCategory.SYSTEM, "KindlePeripheralService onCreate")

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        controller = BlePeripheralController(
            context = applicationContext,
            scope = serviceScope,
            bluetoothAdapterProvider = { bluetoothManager?.adapter }
        )

        classicHidManager = ClassicHidDeviceManager(
            context = applicationContext,
            scope = serviceScope,
            bluetoothAdapterProvider = { bluetoothManager?.adapter }
        )

        reloadSettings()
        registerBluetoothReceiver()
        createNotificationChannel()

        // Observe connection state to update notification
        serviceScope.launch {
            controller.gattServerManager.connectionState.collect {
                if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
                    updateNotification()
                }
            }
        }

        serviceScope.launch {
            classicHidManager.isConnected.collect {
                if (_activeMode.value == EmulationMode.CLASSIC_HID) {
                    updateNotification()
                }
            }
        }
    }

    fun reloadSettings() {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(SettingsActivity.KEY_DEVICE_NAME, BleConstants.DEFAULT_DEVICE_NAME) ?: BleConstants.DEFAULT_DEVICE_NAME
        val batteryEnabled = prefs.getBoolean(SettingsActivity.KEY_BATTERY_ENABLED, true)
        val batteryLevel = prefs.getInt(SettingsActivity.KEY_BATTERY_LEVEL, BleConstants.BATTERY_LEVEL_DEFAULT)
        val autoResume = prefs.getBoolean(SettingsActivity.KEY_AUTO_RESUME, true)
        val includeReportId = prefs.getBoolean(SettingsActivity.KEY_REPORT_ID_ENABLED, true)
        val logLevelStr = prefs.getString(SettingsActivity.KEY_LOG_LEVEL, "DEBUG") ?: "DEBUG"
        val modeStr = prefs.getString(SettingsActivity.KEY_EMULATION_MODE, EmulationMode.BLE_PERIPHERAL.name) ?: EmulationMode.BLE_PERIPHERAL.name

        DiagnosticLogger.minLogLevel = try {
            LogLevel.valueOf(logLevelStr)
        } catch (_: Throwable) {
            LogLevel.DEBUG
        }

        controller.applySettings(
            deviceName = name,
            batteryEnabled = batteryEnabled,
            batteryLevel = batteryLevel,
            autoResume = autoResume,
            includeReportId = includeReportId
        )

        val targetMode = if (modeStr == EmulationMode.CLASSIC_HID.name) {
            EmulationMode.CLASSIC_HID
        } else {
            EmulationMode.BLE_PERIPHERAL
        }
        if (_activeMode.value != targetMode) {
            _activeMode.value = targetMode
        }
        DiagnosticLogger.d(LogCategory.SYSTEM, "Reloaded settings: mode=${targetMode.name}, name='$name'")
    }

    fun setMode(newMode: EmulationMode) {
        val oldMode = _activeMode.value
        if (oldMode == newMode) return

        DiagnosticLogger.i(LogCategory.SYSTEM, "Switching emulation mode: ${oldMode.name} -> ${newMode.name}")
        if (oldMode == EmulationMode.BLE_PERIPHERAL) {
            controller.stopEmulation()
        } else {
            classicHidManager.unregisterHidProfile()
        }

        _activeMode.value = newMode
        getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SettingsActivity.KEY_EMULATION_MODE, newMode.name)
            .apply()

        updateNotification()
    }

    fun startEmulation(): Boolean {
        return if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
            controller.startEmulation()
        } else {
            classicHidManager.registerHidProfile()
        }
    }

    fun stopEmulation() {
        if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
            controller.stopEmulation()
        } else {
            classicHidManager.unregisterHidProfile()
        }
    }

    fun disconnect() {
        if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
            controller.disconnect()
        } else {
            classicHidManager.disconnect()
        }
    }

    fun sendPageForward(onResult: ((KeySendResult) -> Unit)? = null) {
        if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
            controller.sendPageForward(onResult)
        } else {
            classicHidManager.sendPageForward(onResult)
        }
    }

    fun sendPageBackward(onResult: ((KeySendResult) -> Unit)? = null) {
        if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
            controller.sendPageBackward(onResult)
        } else {
            classicHidManager.sendPageBackward(onResult)
        }
    }

    fun sendBrightnessUp(onResult: ((KeySendResult) -> Unit)? = null) {
        if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
            controller.sendBrightnessUp(onResult)
        } else {
            classicHidManager.sendBrightnessUp(onResult)
        }
    }

    fun sendBrightnessDown(onResult: ((KeySendResult) -> Unit)? = null) {
        if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
            controller.sendBrightnessDown(onResult)
        } else {
            classicHidManager.sendBrightnessDown(onResult)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                startEmulation()
            }
            ACTION_STOP -> {
                stopEmulation()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PAGE_FORWARD -> {
                sendPageForward()
            }
            ACTION_PAGE_BACKWARD -> {
                sendPageBackward()
            }
            ACTION_DISCONNECT -> {
                disconnect()
            }
            ACTION_RELOAD_SETTINGS -> {
                reloadSettings()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Kindle Remote Emulation Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val statusText = if (_activeMode.value == EmulationMode.BLE_PERIPHERAL) {
            when (controller.gattServerManager.connectionState.value) {
                ConnectionState.CONNECTED -> "Kindle Connected (Ready - BLE)"
                ConnectionState.CONNECTING -> "Connecting (BLE)..."
                ConnectionState.DISCONNECTED -> "Advertising as '${controller.advertiserManager.deviceName}'..."
            }
        } else {
            if (classicHidManager.isConnected.value) {
                if (classicHidManager.isFeatureSupported.value) {
                    "Kindle Connected (Ready - Classic HID)"
                } else {
                    "Classic HID Connected (Identity Unverified)"
                }
            } else if (classicHidManager.isRegistered.value) {
                "Classic HID Registered (Identity Unverified - Waiting connection)..."
            } else {
                "Classic HID Idle (Identity Unverified)"
            }
        }
        val notification = buildNotification(statusText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pageFwdIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, KindlePeripheralService::class.java).apply { action = ACTION_PAGE_FORWARD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pageBackIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, KindlePeripheralService::class.java).apply { action = ACTION_PAGE_BACKWARD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, KindlePeripheralService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kindle Page Turner")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_previous, "上一页", pageBackIntent)
            .addAction(android.R.drawable.ic_media_next, "下一页", pageFwdIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Kindle Page Turner Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "BLE Kindle Remote background emulator status"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, filter)
        isBluetoothReceiverRegistered = true
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                        DiagnosticLogger.w(LogCategory.BLE_ADV, "Bluetooth turned off while service active")
                        stopEmulation()
                        updateNotification()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        DiagnosticLogger.i(LogCategory.BLE_ADV, "Bluetooth turned on, resuming emulation")
                        startEmulation()
                        updateNotification()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        DiagnosticLogger.i(LogCategory.SYSTEM, "KindlePeripheralService onDestroy")
        stopEmulation()
        if (isBluetoothReceiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver)
            } catch (_: Throwable) {}
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "kindle_page_turner_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.kindle.pageturner.START"
        const val ACTION_STOP = "com.kindle.pageturner.STOP"
        const val ACTION_PAGE_FORWARD = "com.kindle.pageturner.PAGE_FORWARD"
        const val ACTION_PAGE_BACKWARD = "com.kindle.pageturner.PAGE_BACKWARD"
        const val ACTION_DISCONNECT = "com.kindle.pageturner.DISCONNECT"
        const val ACTION_RELOAD_SETTINGS = "com.kindle.pageturner.RELOAD_SETTINGS"
    }
}
