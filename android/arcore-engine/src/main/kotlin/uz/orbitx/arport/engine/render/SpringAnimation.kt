package uz.orbitx.arport.engine.render

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision Apple-style physics spring animation.
 * Implements a damped harmonic oscillator:
 * x''(t) + 2*zeta*omega*x'(t) + omega^2*(x(t) - target) = 0
 * Standard parameters: zeta = 0.82 (damping ratio), omega = 24.0 rad/s.
 */
class SpringAnimation(
    var currentValue: Float = 0.0f,
    var targetValue: Float = 1.0f,
    val dampingRatio: Float = 0.82f,
    val angularFrequency: Float = 24.0f
) {
    var velocity: Float = 0.0f
    private val epsilon = 0.001f

    val isFinished: Boolean
        get() = abs(currentValue - targetValue) < epsilon && abs(velocity) < epsilon

    /**
     * Steps the spring physics simulation forward by dt seconds.
     */
    fun update(dtSec: Float): Float {
        if (isFinished) {
            currentValue = targetValue
            velocity = 0.0f
            return currentValue
        }

        val dt = dtSec.coerceIn(0.001f, 0.05f)
        val displacement = currentValue - targetValue
        val zeta = dampingRatio
        val omega = angularFrequency

        if (zeta < 1.0f) {
            // Under-damped (characteristic spring bounce with gentle decay)
            val omegaD = omega * sqrt(1.0f - zeta * zeta)
            val decay = exp(-zeta * omega * dt)
            val c1 = displacement
            val c2 = (velocity + zeta * omega * displacement) / omegaD

            val cosVal = cos(omegaD * dt)
            val sinVal = sin(omegaD * dt)

            val newDisplacement = decay * (c1 * cosVal + c2 * sinVal)
            val newVelocity = decay * (
                (-zeta * omega * (c1 * cosVal + c2 * sinVal)) +
                (omegaD * (-c1 * sinVal + c2 * cosVal))
            )

            currentValue = targetValue + newDisplacement
            velocity = newVelocity
        } else {
            // Critically damped or over-damped
            val decay = exp(-omega * dt)
            val c1 = displacement
            val c2 = velocity + omega * displacement

            currentValue = targetValue + (c1 + c2 * dt) * decay
            velocity = (velocity - omega * (c1 + c2 * dt)) * decay
        }

        if (abs(currentValue - targetValue) < epsilon && abs(velocity) < epsilon) {
            currentValue = targetValue
            velocity = 0.0f
        }

        return currentValue
    }

    fun snapTo(value: Float) {
        currentValue = value
        targetValue = value
        velocity = 0.0f
    }
}

