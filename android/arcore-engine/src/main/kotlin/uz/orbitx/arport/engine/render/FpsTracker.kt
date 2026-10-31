package uz.orbitx.arport.engine.render

/**
 * Tracks rendering FPS and end-to-end camera passthrough delivery latency.
 */
class FpsTracker {
    private var frameCount: Int = 0
    private var lastFpsUpdateTime: Long = 0L
    private var currentFps: Float = 60.0f

    private var currentLatencyMs: Float = 0.0f
    private var averageLatencyMs: Float = 0.0f
    private var latencySampleCount: Int = 0

    @Synchronized
    fun onFrameRendered(sensorTimestampNs: Long = 0L) {
        val nowNs = System.nanoTime()

        // 1. Calculate camera delivery latency (motion-to-frame arrival on GPU)
        if (sensorTimestampNs > 0L) {
            val deltaNs = nowNs - sensorTimestampNs
            if (deltaNs in 1_000_000L..1_000_000_000L) { // 1ms .. 1000ms plausible window
                val sampleMs = deltaNs / 1_000_000f
                currentLatencyMs = sampleMs
                latencySampleCount++
                averageLatencyMs += (sampleMs - averageLatencyMs) * 0.1f // Exponential moving average
            }
        }

        // 2. Calculate FPS
        frameCount++
        val nowMs = System.currentTimeMillis()
        val elapsedMs = nowMs - lastFpsUpdateTime
        if (elapsedMs >= 500) { // Update twice a second
            currentFps = (frameCount * 1000f) / elapsedMs
            frameCount = 0
            lastFpsUpdateTime = nowMs
        }
    }

    @Synchronized
    fun getFps(): Float = currentFps

    @Synchronized
    fun getLatencyMs(): Float = if (averageLatencyMs > 0f) averageLatencyMs else currentLatencyMs
}

