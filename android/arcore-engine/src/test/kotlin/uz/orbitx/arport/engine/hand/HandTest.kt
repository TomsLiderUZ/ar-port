package uz.orbitx.arport.engine.hand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.orbitx.arport.engine.tracking.OneEuroFilter
import uz.orbitx.arport.engine.tracking.OneEuroFilter3D

class HandTest {

    private val epsilon = 0.001f

    @Test
    fun testOneEuroFilterSmoothing() {
        val filter = OneEuroFilter(minCutoff = 1.0f, beta = 0.007f)

        // Initial value
        val initial = filter.filter(10.0f, 0.0)
        assertEquals(10.0f, initial, epsilon)

        // Inject slight sensor noise around 10.0
        val noise1 = filter.filter(10.2f, 0.016)
        assertTrue(noise1 < 10.2f) // Filter dampens sudden micro jitter

        val noise2 = filter.filter(9.8f, 0.032)
        assertTrue(noise2 > 9.8f) // Filter dampens jitter
    }

    @Test
    fun testOneEuroFilter3D() {
        val filter3D = OneEuroFilter3D()
        val out1 = filter3D.filter(1f, 2f, 3f, 0.0)
        assertEquals(1f, out1[0], epsilon)
        assertEquals(2f, out1[1], epsilon)
        assertEquals(3f, out1[2], epsilon)

        val out2 = filter3D.filter(1.05f, 2.05f, 3.05f, 0.016)
        assertTrue(out2[0] < 1.05f)
        assertTrue(out2[1] < 2.05f)
        assertTrue(out2[2] < 3.05f)
    }

    @Test
    fun testHandLossPersistenceTimeout() {
        val hand = TrackedHand(0, isRightHand = true)
        val now = 10000L

        // Currently detected
        hand.isDetected = true
        hand.lastSeenTimestampMs = now
        assertTrue(hand.isVisible(now))

        // Lost for 100ms (< 200ms hold threshold)
        hand.isDetected = false
        assertTrue(hand.isVisible(now + 100L))

        // Lost for 250ms (> 200ms threshold -> hidden)
        assertFalse(hand.isVisible(now + 250L))
    }

    @Test
    fun testHandLandmarksAndBonesCount() {
        val hand = TrackedHand(0)
        assertEquals(21, hand.landmarks.size)
        assertEquals(21, TrackedHand.BONE_CONNECTIONS.size)

        // Verify key landmarks are distinct
        assertTrue(TrackedHand.WRIST == 0)
        assertTrue(TrackedHand.THUMB_TIP == 4)
        assertTrue(TrackedHand.INDEX_FINGER_TIP == 8)
        assertTrue(TrackedHand.PINKY_TIP == 20)
    }
}

