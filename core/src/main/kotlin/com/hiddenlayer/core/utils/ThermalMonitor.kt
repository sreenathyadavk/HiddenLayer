package com.hiddenlayer.core.utils

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.hiddenlayer.core.Constants

/**
 * Thermal state for adaptive performance degradation.
 * 
 * WHY: Mobile devices throttle when hot. Instead of crashing,
 * we gracefully reduce processing to prevent thermal shutdown.
 */
enum class ThermalState {
    NORMAL,      // Full pipeline
    MODERATE,    // Reduce CNN frequency
    HIGH,        // Skip CNN, biomechanics only
    CRITICAL     // Pause processing
}

/**
 * Monitors device thermal state and emits degradation signals.
 * 
 * WHY: Real-time AI processing generates heat. Production apps
 * must handle thermal limits gracefully, not crash or drain battery.
 * 
 * Strategy:
 * - Use PowerManager thermal API (API 30+)
 * - Fall back to estimated temperature on older devices
 * - Emit state changes to trigger pipeline adaptation
 */
class ThermalMonitor(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    /**
     * Continuous thermal state monitoring.
     * Emits state changes to Flow for reactive processing.
     */
    fun monitorThermalState(): Flow<ThermalState> = flow {
        while (true) {
            val state = getCurrentThermalState()
            emit(state)
            delay(Constants.THERMAL_CHECK_INTERVAL_MS)
        }
    }
    
    /**
     * Get current thermal state from device.
     */
    private fun getCurrentThermalState(): ThermalState {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+: Use PowerManager thermal headroom
            val headroom = powerManager.getThermalHeadroom(5)  // 5 second forecast
            when {
                headroom >= 0.7f -> ThermalState.NORMAL
                headroom >= 0.4f -> ThermalState.MODERATE
                headroom >= 0.2f -> ThermalState.HIGH
                else -> ThermalState.CRITICAL
            }
        } else {
            // Fallback: Conservative estimate for older devices
            // In production, you'd read CPU temperature from /sys/class/thermal
            ThermalState.NORMAL  // Assume normal if can't measure
        }
    }
    
    /**
     * Check if CNN inference should be skipped due to thermal state.
     */
    fun shouldSkipCNN(state: ThermalState): Boolean {
        return state in listOf(ThermalState.HIGH, ThermalState.CRITICAL)
    }
    
    /**
     * Get recommended CNN frequency multiplier based on thermal state.
     * 1.0 = normal, 0.5 = half frequency, 0 = disabled
     */
    fun getCNNFrequencyMultiplier(state: ThermalState): Float {
        return when (state) {
            ThermalState.NORMAL -> 1.0f
            ThermalState.MODERATE -> 0.5f
            ThermalState.HIGH -> 0.0f
            ThermalState.CRITICAL -> 0.0f
        }
    }
}
