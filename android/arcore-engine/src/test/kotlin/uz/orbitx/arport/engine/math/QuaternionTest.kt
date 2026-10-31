package uz.orbitx.arport.engine.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class QuaternionTest {

    private val epsilon = 0.001f

    @Test
    fun testIdentity() {
        val q = Quaternion.identity()
        assertEquals(0f, q.x, epsilon)
        assertEquals(0f, q.y, epsilon)
        assertEquals(0f, q.z, epsilon)
        assertEquals(1f, q.w, epsilon)
        assertEquals(1f, q.length(), epsilon)
    }

    @Test
    fun testNormalize() {
        val q = Quaternion(2f, 0f, 0f, 0f).normalize()
        assertEquals(1f, q.length(), epsilon)
        assertEquals(1f, q.x, epsilon)
    }

    @Test
    fun testMultiplication() {
        // 90 deg around X and 90 deg around Y
        val qX = Quaternion.fromAxisAngle(1f, 0f, 0f, (PI / 2).toFloat())
        val qY = Quaternion.fromAxisAngle(0f, 1f, 0f, (PI / 2).toFloat())
        val result = qX * qY

        assertTrue(result.length() > 0.99f && result.length() < 1.01f)
    }

    @Test
    fun testInverseAndConjugate() {
        val q = Quaternion.fromAxisAngle(0f, 1f, 0f, 1.2f)
        val inv = q.inverse()
        val identity = q * inv

        assertEquals(0f, identity.x, epsilon)
        assertEquals(0f, identity.y, epsilon)
        assertEquals(0f, identity.z, epsilon)
        assertEquals(1f, identity.w, epsilon)
    }

    @Test
    fun testSlerp() {
        val q1 = Quaternion.identity()
        val q2 = Quaternion.fromAxisAngle(0f, 1f, 0f, (PI / 2).toFloat())

        val half = Quaternion.slerp(q1, q2, 0.5f)
        val expectedYaw = (PI / 4).toFloat()

        assertEquals(expectedYaw, half.getYaw(), epsilon)
    }

    @Test
    fun testYawAndRecenter() {
        val yawAngle = (PI / 3).toFloat() // 60 degrees
        val q = Quaternion.fromAxisAngle(0f, 1f, 0f, yawAngle)

        assertEquals(yawAngle, q.getYaw(), epsilon)

        // Test recenter offset: Q_recenter.inverse() * Q should yield yaw = 0
        val recenterOffset = Quaternion.fromAxisAngle(0f, 1f, 0f, q.getYaw())
        val corrected = recenterOffset.inverse() * q

        assertEquals(0f, corrected.getYaw(), epsilon)
    }

    @Test
    fun testToRotationMatrix() {
        val q = Quaternion.fromAxisAngle(0f, 0f, 1f, (PI / 2).toFloat())
        val m = FloatArray(16)
        q.toRotationMatrix(m)

        // For a 90 deg rotation around Z:
        // (1, 0, 0) -> (0, 1, 0)
        assertEquals(0f, m[0], epsilon)
        assertEquals(1f, m[1], epsilon)
        assertEquals(-1f, m[4], epsilon)
        assertEquals(0f, m[5], epsilon)
    }

    @Test
    fun testFromRotationMatrixRoundtrip() {
        val original = Quaternion.fromAxisAngle(0f, 1f, 0f, 0.75f)
        val m = FloatArray(16)
        original.toRotationMatrix(m)

        val restored = Quaternion.fromRotationMatrix(m)

        assertEquals(original.x, restored.x, epsilon)
        assertEquals(original.y, restored.y, epsilon)
        assertEquals(original.z, restored.z, epsilon)
        assertEquals(original.w, restored.w, epsilon)
    }
}

