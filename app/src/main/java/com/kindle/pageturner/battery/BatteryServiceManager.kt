package com.kindle.pageturner.battery

import com.kindle.pageturner.ble.BleConstants
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battery Service Manager (BAS 0x180F, Characteristic 0x2A19)
 *
 * Evidence Mapping:
 * - EVID-07: acsbtfd BleHidPageTurnerPolicy (pageTurnerBatteryLow <= 20%, pageTurnerBatteryCritical <= 5%)
 * - hid_protocol_spec.md Section 4.2
 */
class BatteryServiceManager(
    var notifyBatteryDelegate: ((level: Int) -> Boolean)? = null
) {
    private val _batteryLevel = MutableStateFlow(BleConstants.BATTERY_LEVEL_DEFAULT)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    var isSubscribed: Boolean = false

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        DiagnosticLogger.i(
            LogCategory.BATTERY,
            "Battery service simulation ${if (enabled) "ENABLED" else "DISABLED"}"
        )
    }

    fun updateBatteryLevel(level: Int): Boolean {
        val clamped = level.coerceIn(0, 100)
        _batteryLevel.value = clamped

        if (clamped <= BleConstants.BATTERY_LEVEL_CRITICAL_THRESHOLD) {
            DiagnosticLogger.w(
                LogCategory.BATTERY,
                "Battery level CRITICAL: $clamped% (Kindle threshold <= 5%, EVID-07)"
            )
        } else if (clamped <= BleConstants.BATTERY_LEVEL_LOW_THRESHOLD) {
            DiagnosticLogger.w(
                LogCategory.BATTERY,
                "Battery level LOW: $clamped% (Kindle threshold <= 20%, EVID-07)"
            )
        } else {
            DiagnosticLogger.i(
                LogCategory.BATTERY,
                "Battery level updated: $clamped%"
            )
        }

        val delegate = notifyBatteryDelegate
        return if (isSubscribed && delegate != null) {
            delegate.invoke(clamped)
        } else {
            true
        }
    }

    fun getBatteryLevelPayload(): ByteArray {
        return byteArrayOf((_batteryLevel.value and 0xFF).toByte())
    }
}
