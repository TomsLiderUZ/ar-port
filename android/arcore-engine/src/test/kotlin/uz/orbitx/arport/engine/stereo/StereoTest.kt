package uz.orbitx.arport.engine.stereo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoTest {

    private val epsilon = 0.0001f

    @Test
    fun testLensProfiles() {
        val v2 = LensProfile.fromId("cardboard_v2")
        assertEquals(0.34f, v2.k1, epsilon)
        assertEquals(0.55f, v2.k2, epsilon)

        val v1 = LensProfile.fromId("cardboard_v1")
        assertEquals(0.441f, v1.k1, epsilon)
        assertEquals(0.156f, v1.k2, epsilon)

        val none = LensProfile.fromId("none")
        assertEquals(0.0f, none.k1, epsilon)
        assertEquals(0.0f, none.k2, epsilon)

        // Fallback default
        val unknown = LensProfile.fromId("unknown_glasses")
        assertEquals(v2.k1, unknown.k1, epsilon)
    }

    @Test
    fun testEyeOffsetCalculation() {
        val renderer63 = StereoRenderer(ipdMm = 63.0)
        assertEquals(0.0315f, renderer63.getEyeOffsetMeters(), epsilon)

        val renderer70 = StereoRenderer(ipdMm = 70.0)
        assertEquals(0.035f, renderer70.getEyeOffsetMeters(), epsilon)

        val renderer55 = StereoRenderer(ipdMm = 55.0)
        assertEquals(0.0275f, renderer55.getEyeOffsetMeters(), epsilon)
    }

    @Test
    fun testBarrelDistortionFactor() {
        val profile = LensProfile.CARDBOARD_V2
        val r = 0.3f
        val r2 = r * r
        val r4 = r2 * r2
        val factor = 1.0f + profile.k1 * r2 + profile.k2 * r4

        // Factor must be > 1.0 for positive radius to expand edges outward
        assertTrue(factor > 1.0f)
        val distortedR = r * factor
        assertTrue(distortedR > r)
    }
}

