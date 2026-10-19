package com.kindle.pageturner.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.kindle.pageturner.R
import com.kindle.pageturner.ble.AdvertisingState
import com.kindle.pageturner.ble.ConnectionState
import com.kindle.pageturner.databinding.ActivityMainBinding
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import com.kindle.pageturner.service.EmulationMode
import com.kindle.pageturner.service.KindlePeripheralService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var peripheralService: KindlePeripheralService? = null
    private var isBound = false

    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        DiagnosticLogger.i(
            LogCategory.PERMISSIONS,
            "Permission request result: ${if (allGranted) "ALL GRANTED" else "SOME DENIED"}"
        )
        refreshHardwareAndPermissionStatus()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as KindlePeripheralService.LocalBinder
            peripheralService = binder.getService()
            isBound = true
            DiagnosticLogger.i(LogCategory.SYSTEM, "Bound to KindlePeripheralService")
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            peripheralService = null
            isBound = false
            DiagnosticLogger.w(LogCategory.SYSTEM, "Disconnected from KindlePeripheralService")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        checkAndRequestPermissions()
        bindPeripheralService()
    }

    override fun onResume() {
        super.onResume()
        refreshHardwareAndPermissionStatus()
        peripheralService?.reloadSettings()
    }

    private fun setupListeners() {
        // Mode switching RadioGroup
        binding.rgEmulationMode.setOnCheckedChangeListener { _, checkedId ->
            val targetMode = if (checkedId == R.id.rbModeClassic) {
                EmulationMode.CLASSIC_HID
            } else {
                EmulationMode.BLE_PERIPHERAL
            }
            peripheralService?.setMode(targetMode)
            updateModeUi(targetMode)
        }

        // Start Emulation
        binding.btnStartEmulation.setOnClickListener {
            if (!hasRequiredPermissions()) {
                checkAndRequestPermissions()
                return@setOnClickListener
            }
            val intent = Intent(this, KindlePeripheralService::class.java).apply {
                action = KindlePeripheralService.ACTION_START
            }
            startForegroundService(intent)
            bindPeripheralService()
            peripheralService?.startEmulation()
        }

        // Stop Emulation
        binding.btnStopEmulation.setOnClickListener {
            peripheralService?.stopEmulation()
            val intent = Intent(this, KindlePeripheralService::class.java).apply {
                action = KindlePeripheralService.ACTION_STOP
            }
            startService(intent)
            binding.tvAdvertisingState.text = "已停止"
            binding.tvAdvertisingState.setTextColor(getColor(R.color.status_red))
            binding.tvConnectionState.text = "未连接"
            binding.tvConnectionState.setTextColor(getColor(R.color.status_red))
        }

        // Disconnect
        binding.btnDisconnect.setOnClickListener {
            peripheralService?.disconnect()
            Toast.makeText(this, "正在断开与当前设备的连接...", Toast.LENGTH_SHORT).show()
        }

        // Page Forward (Next Page / Page Down)
        binding.btnPageForward.setOnClickListener {
            triggerPageForward()
        }

        // Page Backward (Prev Page / Page Up)
        binding.btnPageBackward.setOnClickListener {
            triggerPageBackward()
        }

        // Brightness Up
        binding.btnBrightUp.setOnClickListener {
            peripheralService?.sendBrightnessUp { result ->
                runOnUiThread {
                    binding.tvLastResult.text = "亮度增: $result"
                }
            }
        }

        // Brightness Down
        binding.btnBrightDown.setOnClickListener {
            peripheralService?.sendBrightnessDown { result ->
                runOnUiThread {
                    binding.tvLastResult.text = "亮度减: $result"
                }
            }
        }

        // Resume Advertising
        binding.btnResumeAdv.setOnClickListener {
            val service = peripheralService
            if (service != null && service.activeMode.value == EmulationMode.BLE_PERIPHERAL) {
                val ok = service.controller.resumeAdvertising()
                if (ok) {
                    Toast.makeText(this, "正在重新发起 BLE 广播...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "发起广播失败，请检查蓝牙状态", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "仅在 BLE 外设模式下支持恢复广播", Toast.LENGTH_SHORT).show()
            }
        }

        // Settings Button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Diagnostics Button
        binding.btnDiagnostics.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        // Root & System File Management Button
        binding.btnRootManager.setOnClickListener {
            startActivity(Intent(this, RootManagementActivity::class.java))
        }

        // Observe startup crash/reboot recovery status
        lifecycleScope.launch {
            com.kindle.pageturner.root.RootHelper.lastStartupRecoveryReport.collect { report ->
                if (report != null && report.hasPendingRecovery) {
                    binding.tvLastResult.text = "[警告: 存在未完成的 Root 文件事务] 请点击下方「Root 与系统文件安全管理」恢复"
                }
            }
        }

        // Observe latest Root environment inspection report
        lifecycleScope.launch {
            com.kindle.pageturner.root.RootHelper.lastEnvironmentReport.collect { envReport ->
                if (envReport != null) {
                    peripheralService?.classicHidManager?.evaluateFeatureAssessment(envReport)
                    val currentMode = peripheralService?.activeMode?.value
                    if (currentMode == EmulationMode.CLASSIC_HID) {
                        updateModeUi(currentMode)
                    }
                }
            }
        }
    }

    private fun triggerPageForward() {
        val service = peripheralService
        if (service == null) {
            Toast.makeText(this, "模拟服务尚未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        service.sendPageForward { result ->
            runOnUiThread {
                binding.tvLastResult.text = "下一页操作: $result"
            }
        }
    }

    private fun triggerPageBackward() {
        val service = peripheralService
        if (service == null) {
            Toast.makeText(this, "模拟服务尚未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        service.sendPageBackward { result ->
            runOnUiThread {
                binding.tvLastResult.text = "上一页操作: $result"
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isVolumePagingEnabled = prefs.getBoolean(SettingsActivity.KEY_VOLUME_KEY_PAGING, true)
        if (isVolumePagingEnabled) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    triggerPageForward()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    triggerPageBackward()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun bindPeripheralService() {
        if (!isBound) {
            val intent = Intent(this, KindlePeripheralService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun updateModeUi(mode: EmulationMode) {
        binding.tvMode.text = mode.displayName
        if (mode == EmulationMode.BLE_PERIPHERAL) {
            binding.rbModeBle.isChecked = true
            binding.btnResumeAdv.isEnabled = true
            binding.tvIdentityStatus.text = "VID:0x0171 PID:0x0443 (BLE DIS 0x2A50 免 Root)"
            binding.tvIdentityStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.rbModeClassic.isChecked = true
            binding.btnResumeAdv.isEnabled = false
            val envReport = com.kindle.pageturner.root.RootHelper.lastEnvironmentReport.value
            val assessment = peripheralService?.classicHidManager?.evaluateFeatureAssessment(envReport)
                ?: envReport?.toFeatureAssessment()
            if (assessment != null) {
                binding.tvIdentityStatus.text =
                    "${assessment.deviceIdentityVerificationState.displayName} | ${assessment.dynamicStatusMessage}"
            } else {
                binding.tvIdentityStatus.text = "身份未验证 (公开 API 无法设置 0x0171:0x0443，修改方案待验证)"
            }
            binding.tvIdentityStatus.setTextColor(getColor(R.color.status_orange))
            val regState = peripheralService?.classicHidManager?.registrationState?.value
            binding.tvAdvertisingState.text = regState?.displayName ?: "Classic HID 未注册 (VID/PID 修改暂不可用/待验证)"
            binding.tvAdvertisingState.setTextColor(getColor(R.color.status_orange))
        }
    }

    private fun observeServiceState() {
        val service = peripheralService ?: return

        lifecycleScope.launch {
            service.activeMode.collect { mode ->
                updateModeUi(mode)
            }
        }

        // BLE Mode: Advertising State
        lifecycleScope.launch {
            service.controller.advertiserManager.state.collect { advState ->
                if (service.activeMode.value == EmulationMode.BLE_PERIPHERAL) {
                    when (advState) {
                        AdvertisingState.ADVERTISING -> {
                            val name = service.controller.advertiserManager.deviceName
                            val fallback = if (service.controller.advertiserManager.isFallbackMode) " (Compact)" else ""
                            binding.tvAdvertisingState.text = "正在广播 ('$name')$fallback"
                            binding.tvAdvertisingState.setTextColor(getColor(R.color.status_green))
                        }
                        AdvertisingState.STARTING -> {
                            binding.tvAdvertisingState.text = "正在启动广播..."
                            binding.tvAdvertisingState.setTextColor(getColor(R.color.status_orange))
                        }
                        AdvertisingState.STOPPED -> {
                            binding.tvAdvertisingState.text = "已停止"
                            binding.tvAdvertisingState.setTextColor(getColor(R.color.status_red))
                        }
                        AdvertisingState.FAILED -> {
                            val err = service.controller.advertiserManager.lastError.value ?: "失败"
                            binding.tvAdvertisingState.text = "失败: $err"
                            binding.tvAdvertisingState.setTextColor(getColor(R.color.status_red))
                        }
                    }
                }
            }
        }

        // BLE Mode: Connection State
        lifecycleScope.launch {
            service.controller.gattServerManager.connectionState.collect { connState ->
                if (service.activeMode.value == EmulationMode.BLE_PERIPHERAL) {
                    when (connState) {
                        ConnectionState.CONNECTED -> {
                            val dev = service.controller.gattServerManager.connectedDevice.value
                            val devName = try { dev?.name } catch (_: SecurityException) { null } ?: "Kindle"
                            binding.tvConnectionState.text = "已连接: $devName (${dev?.address})"
                            binding.tvConnectionState.setTextColor(getColor(R.color.status_green))
                            binding.btnDisconnect.isEnabled = true
                        }
                        ConnectionState.CONNECTING -> {
                            binding.tvConnectionState.text = "正在建立连接..."
                            binding.tvConnectionState.setTextColor(getColor(R.color.status_orange))
                            binding.btnDisconnect.isEnabled = false
                        }
                        ConnectionState.DISCONNECTED -> {
                            binding.tvConnectionState.text = "未连接 (等待 Kindle 发现并连接)"
                            binding.tvConnectionState.setTextColor(getColor(R.color.status_red))
                            binding.btnDisconnect.isEnabled = false
                        }
                    }
                }
            }
        }

        // Classic HID Mode: Registration & Identity Verification State
        lifecycleScope.launch {
            service.classicHidManager.registrationState.collect { regState ->
                if (service.activeMode.value == EmulationMode.CLASSIC_HID) {
                    binding.tvAdvertisingState.text = regState.displayName
                    binding.tvAdvertisingState.setTextColor(getColor(R.color.status_orange))
                }
            }
        }

        // Classic HID Mode: Connection State
        lifecycleScope.launch {
            service.classicHidManager.isConnected.collect { connected ->
                if (service.activeMode.value == EmulationMode.CLASSIC_HID) {
                    if (connected) {
                        binding.tvConnectionState.text = "已连接 (Classic HID - 身份未验证，Kindle 可能拒绝)"
                        binding.tvConnectionState.setTextColor(getColor(R.color.status_orange))
                        binding.btnDisconnect.isEnabled = true
                    } else {
                        binding.tvConnectionState.text = "未连接 (Classic HID 身份修改方案待验证)"
                        binding.tvConnectionState.setTextColor(getColor(R.color.status_red))
                        binding.btnDisconnect.isEnabled = false
                    }
                }
            }
        }

        // Last Action Result
        lifecycleScope.launch {
            service.controller.lastActionResult.collect { actionResult ->
                if (service.activeMode.value == EmulationMode.BLE_PERIPHERAL) {
                    binding.tvLastResult.text = actionResult
                }
            }
        }

        lifecycleScope.launch {
            service.classicHidManager.lastResult.collect { actionResult ->
                if (service.activeMode.value == EmulationMode.CLASSIC_HID) {
                    binding.tvLastResult.text = actionResult
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshHardwareAndPermissionStatus() {
        // 1. Bluetooth enabled
        val adapter = bluetoothAdapter
        if (adapter == null) {
            binding.tvBluetoothStatus.text = "硬件不支持蓝牙"
            binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_red))
        } else if (!adapter.isEnabled) {
            binding.tvBluetoothStatus.text = "未开启 (请在系统设置中打开)"
            binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_red))
        } else {
            binding.tvBluetoothStatus.text = "已开启"
            binding.tvBluetoothStatus.setTextColor(getColor(R.color.status_green))
        }

        // 2. Peripheral capability
        val hasAdv = adapter?.isMultipleAdvertisementSupported ?: false
        if (hasAdv) {
            binding.tvPeripheralCap.text = "支持 (MultipleAdvertisementSupported)"
            binding.tvPeripheralCap.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvPeripheralCap.text = "当前控制器不支持 BLE 外设广播"
            binding.tvPeripheralCap.setTextColor(getColor(R.color.status_red))
        }

        // 3. Permissions
        if (hasRequiredPermissions()) {
            binding.tvPermissions.text = "全部授权完毕"
            binding.tvPermissions.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvPermissions.text = "权限缺失 (点击启动模拟进行授权)"
            binding.tvPermissions.setTextColor(getColor(R.color.status_red))
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        val ungranted = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            list.add(Manifest.permission.BLUETOOTH)
            list.add(Manifest.permission.BLUETOOTH_ADMIN)
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}
