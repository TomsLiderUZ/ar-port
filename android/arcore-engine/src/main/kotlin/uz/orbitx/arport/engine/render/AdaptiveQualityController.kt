package uz.orbitx.arport.engine.render

import uz.orbitx.arport.engine.window.WindowManager3D

/**
 * Spatial OS Quality Tiers for dynamically adapting visual fidelity
 * based on live rendering frame rates and hardware thermal status.
 */
enum class QualityTier(val level: Int, val label: String) {
    ULTRA(0, "Ultra"),       // 60 FPS: Full 3-pass Kawase blur, 0.5x FBO, full particle density (1.0x), full WebView FPS (60)
    BALANCED(1, "Balanced"), // <45 FPS: 2-pass Kawase blur, 0.25x FBO, medium particles (0.6x), 30 FPS background
    ECONOMY(2, "Economy"),   // <30 FPS: Frosted solid tint (blur disabled), reduced particles (0.3x), 15 FPS background
    CRITICAL(3, "Critical")  // <20 FPS / Thermal Severe: Minimal effects (0.1x), background windows sleeping, 10 FPS
}

/**
 * Adaptive Quality Controller (TZ 14 & QADAM 18).
 * Monitors frame rates and device thermal headroom to gracefully step down / recover
 * rendering load, preventing stutter, thermal runaway, and battery drain.
 */
class AdaptiveQualityController(
    private val glassRenderer: GlassRenderer? = null,
    private val windowManager: WindowManager3D? = null,
    private val fpsTracker: FpsTracker? = null
) {
    var currentTier: QualityTier = QualityTier.ULTRA
        private set

    var isThermalThrottled: Boolean = false
        private set

    var particleMultiplier: Float = 1.0f
        private set

    var backgroundFpsThrottle: Int = 60
        private set

    var onQualityTierChanged: ((QualityTier, String) -> Unit)? = null
    var onPerformanceWarning: ((String) -> Unit)? = null

    // Hysteresis counters
    var lowFpsCounter = 0
        private set
    var highFpsCounter = 0
        private set

    companion object {
        const val LOW_FPS_THRESHOLD = 45.0f
        const val CRITICAL_FPS_THRESHOLD = 25.0f
        const val HIGH_FPS_THRESHOLD = 55.0f

        // Minimum frames before tier shift to prevent fluttering
        const val DOWNGRADE_FRAMES_REQUIRED = 45 // ~0.75-1.0s
        const val UPGRADE_FRAMES_REQUIRED = 90   // ~1.5-2.0s
    }

    /**
     * Called every render frame with live measured FPS.
     */
    fun onFrame(fps: Float = fpsTracker?.getFps() ?: 60.0f) {
        if (isThermalThrottled) {
            // Thermal condition takes precedence; do not auto-upgrade past clamped tier
            return
        }

        if (fps < CRITICAL_FPS_THRESHOLD) {
            lowFpsCounter += 3 // Rapid degrade on severe drops
            highFpsCounter = 0
        } else if (fps < LOW_FPS_THRESHOLD) {
            lowFpsCounter++
            highFpsCounter = 0
        } else if (fps >= HIGH_FPS_THRESHOLD) {
            highFpsCounter++
            lowFpsCounter = 0
        } else {
            // In stable neutral range [45..55), decay counters
            if (lowFpsCounter > 0) lowFpsCounter--
            if (highFpsCounter > 0) highFpsCounter--
        }

        if (lowFpsCounter >= DOWNGRADE_FRAMES_REQUIRED) {
            lowFpsCounter = 0
            downgradeTier("Kadrlar soni pasaydi (${fps.toInt()} FPS)")
        } else if (highFpsCounter >= UPGRADE_FRAMES_REQUIRED) {
            highFpsCounter = 0
            upgradeTier("Kadrlar soni barqarorlashdi (${fps.toInt()} FPS)")
        }
    }

    /**
     * Steps down to lower quality tier.
     */
    fun downgradeTier(reason: String = "Past FPS"): Boolean {
        val nextTier = when (currentTier) {
            QualityTier.ULTRA -> QualityTier.BALANCED
            QualityTier.BALANCED -> QualityTier.ECONOMY
            QualityTier.ECONOMY -> QualityTier.CRITICAL
            QualityTier.CRITICAL -> return false
        }
        applyTier(nextTier, reason)
        return true
    }

    /**
     * Steps up to higher quality tier if not thermally throttled.
     */
    fun upgradeTier(reason: String = "FPS barqaror"): Boolean {
        if (isThermalThrottled) return false

        val prevTier = when (currentTier) {
            QualityTier.CRITICAL -> QualityTier.ECONOMY
            QualityTier.ECONOMY -> QualityTier.BALANCED
            QualityTier.BALANCED -> QualityTier.ULTRA
            QualityTier.ULTRA -> return false
        }
        applyTier(prevTier, reason)
        return true
    }

    /**
     * Directly force or apply a quality tier.
     */
    fun applyTier(tier: QualityTier, reason: String = "Moslashtirildi") {
        currentTier = tier

        when (tier) {
            QualityTier.ULTRA -> {
                glassRenderer?.isLowSpecMode = false
                glassRenderer?.isReduceTransparency = false
                glassRenderer?.blurRadius = 2.5f
                particleMultiplier = 1.0f
                backgroundFpsThrottle = 60
            }
            QualityTier.BALANCED -> {
                glassRenderer?.isLowSpecMode = true
                glassRenderer?.isReduceTransparency = false
                glassRenderer?.blurRadius = 1.5f
                particleMultiplier = 0.6f
                backgroundFpsThrottle = 30
            }
            QualityTier.ECONOMY -> {
                glassRenderer?.isLowSpecMode = true
                glassRenderer?.isReduceTransparency = true
                glassRenderer?.blurRadius = 0.5f
                particleMultiplier = 0.3f
                backgroundFpsThrottle = 15
            }
            QualityTier.CRITICAL -> {
                glassRenderer?.isLowSpecMode = true
                glassRenderer?.isReduceTransparency = true
                glassRenderer?.blurRadius = 0.0f
                particleMultiplier = 0.1f
                backgroundFpsThrottle = 10
                windowManager?.enforceSleepPolicy()
            }
        }

        onQualityTierChanged?.invoke(tier, reason)
    }

    /**
     * Reacts to thermal status from ThermalMonitor.
     * Thermal status values: 0=NONE, 1=LIGHT, 2=MODERATE, 3=SEVERE, 4=CRITICAL
     */
    fun onThermalStatusChanged(thermalStatus: Int) {
        if (thermalStatus >= 3) { // SEVERE or higher
            isThermalThrottled = true
            applyTier(QualityTier.CRITICAL, "Qurilma qizimoqda (Severe Thermal)")
            onPerformanceWarning?.invoke("Qurilma qizimoqda: Samaradorlik avtomatik moslandi")
        } else if (thermalStatus >= 2) { // MODERATE
            isThermalThrottled = true
            if (currentTier.level < QualityTier.ECONOMY.level) {
                applyTier(QualityTier.ECONOMY, "Qurilma qizimoqda (Moderate Thermal)")
            }
            onPerformanceWarning?.invoke("Qurilma qizimoqda: Samaradorlik avtomatik moslandi")
        } else {
            // Thermal status returned to normal
            if (isThermalThrottled) {
                isThermalThrottled = false
                applyTier(QualityTier.BALANCED, "Qurilma harorati pasaydi")
            }
        }
    }

    /**
     * Reset counters (e.g. after mode switch).
     */
    fun resetCounters() {
        lowFpsCounter = 0
        highFpsCounter = 0
    }
}

