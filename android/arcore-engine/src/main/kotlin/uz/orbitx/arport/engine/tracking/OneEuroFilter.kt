package uz.orbitx.arport.engine.tracking

import kotlin.math.PI
import kotlin.math.abs

/**
 * 1€ (One-Euro) Filter for low-latency, jitter-free hand tracking.
 * Dynamically adjusts cutoff frequency based on movement speed:
 * Low speed = heavy filtering to remove jitter.
 * High speed = high cutoff frequency to eliminate lag.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f
) {
    private var xPrev: Float = 0f
    private var dxPrev: Float = 0f
    private var lastTimestampSec: Double = -1.0
    private var isInitialized = false

    fun filter(x: Float, timestampSec: Double): Float {
        if (!isInitialized) {
            xPrev = x
            dxPrev = 0f
            lastTimestampSec = timestampSec
            isInitialized = true
            return x
        }

        val dt = (timestampSec - lastTimestampSec).toFloat().coerceIn(0.001f, 0.1f)
        lastTimestampSec = timestampSec

        // 1. Estimate derivative (velocity)
        val dx = (x - xPrev) / dt
        val edx = lowPassFilter(dx, dxPrev, alpha(dt, dCutoff))
        dxPrev = edx

        // 2. Compute dynamic cutoff frequency
        val cutoff = minCutoff + beta * abs(edx)

        // 3. Filter current value
        val filteredX = lowPassFilter(x, xPrev, alpha(dt, cutoff))
        xPrev = filteredX
        return filteredX
    }

    fun reset() {
        isInitialized = false
        lastTimestampSec = -1.0
    }

    private fun alpha(dt: Float, cutoff: Float): Float {
        val tau = 1.0f / (2.0f * PI.toFloat() * cutoff)
        return 1.0f / (1.0f + tau / dt)
    }

    private fun lowPassFilter(x: Float, xPrev: Float, alpha: Float): Float {
        return alpha * x + (1.0f - alpha) * xPrev
    }
}

/**
 * 3D vector smoother using 3 independent OneEuroFilters.
 */
class OneEuroFilter3D(
    minCutoff: Float = 1.0f,
    beta: Float = 0.007f,
    dCutoff: Float = 1.0f
) {
    private val filterX = OneEuroFilter(minCutoff, beta, dCutoff)
    private val filterY = OneEuroFilter(minCutoff, beta, dCutoff)
    private val filterZ = OneEuroFilter(minCutoff, beta, dCutoff)

    fun filter(x: Float, y: Float, z: Float, timestampSec: Double): FloatArray {
        return floatArrayOf(
            filterX.filter(x, timestampSec),
            filterY.filter(y, timestampSec),
            filterZ.filter(z, timestampSec)
        )
    }

    fun reset() {
        filterX.reset()
        filterY.reset()
        filterZ.reset()
    }
}

