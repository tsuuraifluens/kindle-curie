package com.kindle.pageturner.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kindle.pageturner.R
import com.kindle.pageturner.ble.BleConstants
import com.kindle.pageturner.databinding.ActivitySettingsBinding
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogLevel
import com.kindle.pageturner.root.RootHelper
import com.kindle.pageturner.service.KindlePeripheralService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load saved values
        val savedName = prefs.getString(KEY_DEVICE_NAME, BleConstants.DEFAULT_DEVICE_NAME) ?: BleConstants.DEFAULT_DEVICE_NAME
        binding.etDeviceName.setText(savedName)

        val savedBatteryEnabled = prefs.getBoolean(KEY_BATTERY_ENABLED, true)
        binding.switchBatteryService.isChecked = savedBatteryEnabled

        val savedBatteryLevel = prefs.getInt(KEY_BATTERY_LEVEL, BleConstants.BATTERY_LEVEL_DEFAULT)
        binding.sbBatteryLevel.progress = savedBatteryLevel
        binding.tvBatteryPercent.text = "$savedBatteryLevel%"

        val savedAutoResume = prefs.getBoolean(KEY_AUTO_RESUME, true)
        binding.switchAutoResumeAdv.isChecked = savedAutoResume

        val savedReportId = prefs.getBoolean(KEY_REPORT_ID_ENABLED, true)
        binding.switchReportId.isChecked = savedReportId

        val savedVolumePaging = prefs.getBoolean(KEY_VOLUME_KEY_PAGING, true)
        binding.switchVolumeKeyPaging.isChecked = savedVolumePaging

        val savedLogLevel = prefs.getString(KEY_LOG_LEVEL, "DEBUG") ?: "DEBUG"
        when (savedLogLevel) {
            "INFO" -> binding.rbLogInfo.isChecked = true
            "WARN" -> binding.rbLogWarn.isChecked = true
            "ERROR" -> binding.rbLogError.isChecked = true
            else -> binding.rbLogDebug.isChecked = true
        }

        // Battery seek bar listener
        binding.sbBatteryLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvBatteryPercent.text = "$progress%"
                if (progress <= BleConstants.BATTERY_LEVEL_CRITICAL_THRESHOLD) {
                    binding.tvBatteryPercent.setTextColor(android.graphics.Color.RED)
                } else if (progress <= BleConstants.BATTERY_LEVEL_LOW_THRESHOLD) {
                    binding.tvBatteryPercent.setTextColor(android.graphics.Color.rgb(239, 108, 0))
                } else {
                    binding.tvBatteryPercent.setTextColor(android.graphics.Color.BLACK)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Root inspection (on-demand only, never auto-triggering su on activity open)
        binding.btnCheckRoot.setOnClickListener {
            checkRootStateAsync()
        }
        binding.btnOpenRootManager.setOnClickListener {
            startActivity(Intent(this, RootManagementActivity::class.java))
        }

        // Save
        binding.btnSaveSettings.setOnClickListener {
            val name = binding.etDeviceName.text?.toString()?.trim()
            if (name.isNullOrEmpty()) {
                Toast.makeText(this, "设备名称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedLogLevel = when {
                binding.rbLogInfo.isChecked -> "INFO"
                binding.rbLogWarn.isChecked -> "WARN"
                binding.rbLogError.isChecked -> "ERROR"
                else -> "DEBUG"
            }

            prefs.edit()
                .putString(KEY_DEVICE_NAME, name)
                .putBoolean(KEY_BATTERY_ENABLED, binding.switchBatteryService.isChecked)
                .putInt(KEY_BATTERY_LEVEL, binding.sbBatteryLevel.progress)
                .putBoolean(KEY_AUTO_RESUME, binding.switchAutoResumeAdv.isChecked)
                .putBoolean(KEY_REPORT_ID_ENABLED, binding.switchReportId.isChecked)
                .putBoolean(KEY_VOLUME_KEY_PAGING, binding.switchVolumeKeyPaging.isChecked)
                .putString(KEY_LOG_LEVEL, selectedLogLevel)
                .apply()

            DiagnosticLogger.minLogLevel = try {
                LogLevel.valueOf(selectedLogLevel)
            } catch (_: Throwable) {
                LogLevel.DEBUG
            }

            // Notify running foreground service to reload settings dynamically
            val reloadIntent = Intent(this, KindlePeripheralService::class.java).apply {
                action = KindlePeripheralService.ACTION_RELOAD_SETTINGS
            }
            startService(reloadIntent)

            Toast.makeText(this, "配置已保存并同步至模拟服务", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun checkRootStateAsync() {
        binding.btnCheckRoot.isEnabled = false
        binding.tvRootStatus.text = "正在后台安全检测 Root 与系统文件状态..."
        lifecycleScope.launch {
            val rootResult = withContext(Dispatchers.IO) {
                RootHelper.inspectEnvironment(this@SettingsActivity)
            }
            val sb = StringBuilder()
            sb.append("• su 执行文件: ").append(if (rootResult.hasSuBinary) "存在" else "未检测到").append("\n")
            sb.append("• Root 权限状态: ").append(rootResult.rootPermissionState.displayName).append("\n")
            sb.append("• SELinux 状态: ").append(rootResult.selinuxState.displayName).append(" (").append(rootResult.selinuxMode).append(")\n")
            sb.append("• 目标文件存在状态: ").append(rootResult.targetFileExistenceState.displayName)
                .append(" (/system=").append(if (rootResult.systemBtDidExists) "存在" else "不存在")
                .append(", /data=").append(if (rootResult.dataBtDidExists) "存在" else "不存在").append(")\n")
            sb.append("• 目标路径验证状态: ").append(rootResult.targetPathVerificationState.displayName).append("\n")
            sb.append("• 当前系统兼容性: ").append(rootResult.systemCompatibilityState.displayName).append("\n")
            sb.append("• 经典蓝牙 HID 注册状态: ").append(rootResult.featureAssessment.classicHidRegistrationState.displayName).append("\n")
            sb.append("• 设备身份验证: ").append(rootResult.deviceIdentityVerificationState.displayName).append("\n")
            sb.append("• Kindle 识别与接受: ").append(rootResult.kindleAcceptanceState.displayName).append("\n")
            sb.append("• 环境评估结论: ").append(rootResult.summaryMessage)

            binding.tvRootStatus.text = sb.toString()
            binding.btnCheckRoot.isEnabled = true
        }
    }

    companion object {
        const val PREFS_NAME = "kindle_remote_prefs"
        const val KEY_DEVICE_NAME = "device_name"
        const val KEY_BATTERY_ENABLED = "battery_enabled"
        const val KEY_BATTERY_LEVEL = "battery_level"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_REPORT_ID_ENABLED = "report_id_enabled"
        const val KEY_VOLUME_KEY_PAGING = "volume_key_paging"
        const val KEY_LOG_LEVEL = "log_level"
        const val KEY_EMULATION_MODE = "emulation_mode"
    }
}
