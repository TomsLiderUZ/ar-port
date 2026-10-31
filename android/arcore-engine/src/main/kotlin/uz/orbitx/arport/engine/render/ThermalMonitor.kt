package uz.orbitx.arport.engine.render

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Thermal and Hardware Resource Monitor (TZ 14 & QADAM 18).
 * Tracks device thermal headroom and PowerManager thermal status listeners (API 29+),
 * alerting user and triggering AdaptiveQualityController to prevent device overheating.
 */
class ThermalMonitor(
    private val context: Context?
) {
    var currentThermalStatus: Int = 0 // 0 = THERMAL_STATUS_NONE
        private set

    var onThermalStatusChanged: ((Int, String) -> Unit)? = null
    var onThermalWarning: ((String) -> Unit)? = null

    private var powerManager: PowerManager? = null
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    init {
        try {
            powerManager = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
            registerListener()
        } catch (_: Exception) {
            // Safe fallback for tests / headless environments
        }
    }

    private fun registerListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && context != null) {
            try {
                val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                    handleThermalStatusUpdate(status)
                }
                listener = thermalListener
                powerManager?.addThermalStatusListener(context.mainExecutor, thermalListener)
                currentThermalStatus = powerManager?.currentThermalStatus ?: 0
            } catch (_: Exception) {
                // Ignore if not supported on emulator/device
            }
        }
    }

    /**
     * Internal handler when thermal status changes from OS or simulator.
     */
    fun handleThermalStatusUpdate(status: Int) {
        currentThermalStatus = status
        val label = getThermalStatusLabel(status)
        onThermalStatusChanged?.invoke(status, label)

        if (status >= 2) { // MODERATE (2) or SEVERE (3)
            val warningMsg = "Qurilma qizimoqda: Samaradorlik avtomatik moslandi ($label)"
            onThermalWarning?.invoke(warningMsg)
        }
    }

    /**
     * Translates Android thermal status to human-readable label.
     */
    fun getThermalStatusLabel(status: Int): String {
        return when (status) {
            0 -> "Normal"
            1 -> "Yengil isish (Light)"
            2 -> "O'rtacha isish (Moderate)"
            3 -> "Kuchli isish (Severe)"
            4 -> "Kritik isish (Critical)"
            5 -> "Favqulodda isish (Emergency)"
            6 -> "O'chirish darajasi (Shutdown)"
            else -> "Noma'lum ($status)"
        }
    }

    /**
     * Returns thermal headroom (0.0 to 1.0+ where 1.0 = thermal throttling threshold).
     * Available on Android 11+ (API 30).
     */
    fun getThermalHeadroom(forecastSeconds: Int = 10): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                return powerManager?.getThermalHeadroom(forecastSeconds) ?: 0.0f
            } catch (_: Exception) {}
        }
        return 0.0f
    }

    /**
     * Simulates thermal status for testing and performance verification.
     */
    fun simulateThermalStatus(status: Int) {
        handleThermalStatusUpdate(status)
    }

    /**
     * Cleans up listeners to prevent activity leaks.
     */
    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && listener != null) {
            try {
                powerManager?.removeThermalStatusListener(listener!!)
            } catch (_: Exception) {}
            listener = null
        }
    }
}

