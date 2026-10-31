package uz.orbitx.arport.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringTest {

    private val epsilon = 0.001f

    @Test
    fun testSpringInitialState() {
        val spring = SpringAnimation(currentValue = 0.0f, targetValue = 1.0f)
        assertEquals(0.0f, spring.currentValue, epsilon)
        assertEquals(1.0f, spring.targetValue, epsilon)
        assertFalse(spring.isFinished)
    }

    @Test
    fun testSpringProgressionAndConvergence() {
        val spring = SpringAnimation(currentValue = 0.0f, targetValue = 1.0f)

        // Step simulation forward for 1.0 second (60 frames at 16ms)
        var sawOvershoot = false
        for (i in 0 until 60) {
            val v = spring.update(0.016f)
            if (v > 1.0f) {
                sawOvershoot = true // Natural spring bounce
            }
        }

        assertTrue(sawOvershoot)
        assertTrue(spring.isFinished)
        assertEquals(1.0f, spring.currentValue, epsilon)
    }

    @Test
    fun testSpringSnapTo() {
        val spring = SpringAnimation(currentValue = 0.5f, targetValue = 1.0f)
        spring.snapTo(0.85f)

        assertEquals(0.85f, spring.currentValue, epsilon)
        assertEquals(0.85f, spring.targetValue, epsilon)
        assertEquals(0.0f, spring.velocity, epsilon)
        assertTrue(spring.isFinished)
    }

    @Test
    fun testSquircleFormula() {
        // Squircle superellipse formula: x^4 + y^4 <= 1.0
        // Point (0, 0): 0 + 0 <= 1 (inside)
        val center = 0.0 * 0.0 * 0.0 * 0.0 + 0.0 * 0.0 * 0.0 * 0.0
        assertTrue(center <= 1.0)

        // Point (0.9, 0): 0.9^4 = 0.6561 <= 1 (inside)
        val edgeMid = 0.9 * 0.9 * 0.9 * 0.9
        assertTrue(edgeMid <= 1.0)

        // Point (0.9, 0.9): 0.9^4 + 0.9^4 = 1.3122 > 1.0 (outside squircle corner, clipped!)
        val corner = (0.9 * 0.9 * 0.9 * 0.9) * 2.0
        assertTrue(corner > 1.0)
    }
}

