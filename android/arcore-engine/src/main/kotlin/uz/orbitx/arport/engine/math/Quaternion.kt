package uz.orbitx.arport.engine.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance Quaternion math utility for 3DoF/6DoF spatial tracking.
 * Represents rotation as q = w + xi + yj + zk.
 */
data class Quaternion(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f
) {

    fun set(x: Float, y: Float, z: Float, w: Float): Quaternion {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun set(other: Quaternion): Quaternion {
        this.x = other.x
        this.y = other.y
        this.z = other.z
        this.w = other.w
        return this
    }

    fun length(): Float = sqrt(x * x + y * y + z * z + w * w)

    fun normalize(): Quaternion {
        val len = length()
        if (len > 0.00001f) {
            val inv = 1f / len
            x *= inv
            y *= inv
            z *= inv
            w *= inv
        } else {
            x = 0f
            y = 0f
            z = 0f
            w = 1f
        }
        return this
    }

    fun conjugate(): Quaternion = Quaternion(-x, -y, -z, w)

    fun inverse(): Quaternion {
        val normSq = x * x + y * y + z * z + w * w
        if (normSq > 0.00001f) {
            val inv = 1f / normSq
            return Quaternion(-x * inv, -y * inv, -z * inv, w * inv)
        }
        return Quaternion(0f, 0f, 0f, 1f)
    }

    operator fun times(q: Quaternion): Quaternion {
        return Quaternion(
            w * q.x + x * q.w + y * q.z - z * q.y,
            w * q.y - x * q.z + y * q.w + z * q.x,
            w * q.z + x * q.y - y * q.x + z * q.w,
            w * q.w - x * q.x - y * q.y - z * q.z
        )
    }

    /**
     * Converts quaternion to 4x4 OpenGL column-major rotation matrix.
     */
    fun toRotationMatrix(matrix: FloatArray, offset: Int = 0) {
        val xx = x * x
        val xy = x * y
        val xz = x * z
        val xw = x * w

        val yy = y * y
        val yz = y * z
        val yw = y * w

        val zz = z * z
        val zw = z * w

        // Column 0
        matrix[offset + 0] = 1f - 2f * (yy + zz)
        matrix[offset + 1] = 2f * (xy + zw)
        matrix[offset + 2] = 2f * (xz - yw)
        matrix[offset + 3] = 0f

        // Column 1
        matrix[offset + 4] = 2f * (xy - zw)
        matrix[offset + 5] = 1f - 2f * (xx + zz)
        matrix[offset + 6] = 2f * (yz + xw)
        matrix[offset + 7] = 0f

        // Column 2
        matrix[offset + 8] = 2f * (xz + yw)
        matrix[offset + 9] = 2f * (yz - xw)
        matrix[offset + 10] = 1f - 2f * (xx + yy)
        matrix[offset + 11] = 0f

        // Column 3
        matrix[offset + 12] = 0f
        matrix[offset + 13] = 0f
        matrix[offset + 14] = 0f
        matrix[offset + 15] = 1f
    }

    /**
     * Computes the Yaw angle around the Y (vertical) axis in radians.
     */
    fun getYaw(): Float {
        val sinyCosp = 2f * (w * y + z * x)
        val cosyCosp = 1f - 2f * (x * x + y * y)
        return atan2(sinyCosp, cosyCosp)
    }

    companion object {
        fun identity(): Quaternion = Quaternion(0f, 0f, 0f, 1f)

        /**
         * Creates quaternion from a 4x4 column-major rotation matrix.
         */
        fun fromRotationMatrix(m: FloatArray, offset: Int = 0): Quaternion {
            val m00 = m[offset + 0]
            val m10 = m[offset + 1]
            val m20 = m[offset + 2]

            val m01 = m[offset + 4]
            val m11 = m[offset + 5]
            val m21 = m[offset + 6]

            val m02 = m[offset + 8]
            val m12 = m[offset + 9]
            val m22 = m[offset + 10]

            val trace = m00 + m11 + m22
            val q = Quaternion()
            if (trace > 0f) {
                val s = 0.5f / sqrt(trace + 1.0f)
                q.w = 0.25f / s
                q.x = (m21 - m12) * s
                q.y = (m02 - m20) * s
                q.z = (m10 - m01) * s
            } else {
                if (m00 > m11 && m00 > m22) {
                    val s = 2.0f * sqrt(1.0f + m00 - m11 - m22)
                    q.w = (m21 - m12) / s
                    q.x = 0.25f * s
                    q.y = (m10 + m01) / s
                    q.z = (m02 + m20) / s
                } else if (m11 > m22) {
                    val s = 2.0f * sqrt(1.0f + m11 - m00 - m22)
                    q.w = (m02 - m20) / s
                    q.x = (m10 + m01) / s
                    q.y = 0.25f * s
                    q.z = (m21 + m12) / s
                } else {
                    val s = 2.0f * sqrt(1.0f + m22 - m00 - m11)
                    q.w = (m10 - m01) / s
                    q.x = (m02 + m20) / s
                    q.y = (m21 + m12) / s
                    q.z = 0.25f * s
                }
            }
            return q.normalize()
        }

        /**
         * Creates quaternion from rotation axis and angle (in radians).
         */
        fun fromAxisAngle(ax: Float, ay: Float, az: Float, angleRad: Float): Quaternion {
            val halfAngle = angleRad * 0.5f
            val s = sin(halfAngle)
            val len = sqrt(ax * ax + ay * ay + az * az)
            if (len < 0.0001f) return identity()
            val inv = 1f / len
            return Quaternion(
                ax * inv * s,
                ay * inv * s,
                az * inv * s,
                cos(halfAngle)
            ).normalize()
        }

        /**
         * Spherical Linear Interpolation (SLERP) between q1 and q2 with factor t [0, 1].
         */
        fun slerp(q1: Quaternion, q2: Quaternion, t: Float): Quaternion {
            var dot = q1.x * q2.x + q1.y * q2.y + q1.z * q2.z + q1.w * q2.w

            var targetX = q2.x
            var targetY = q2.y
            var targetZ = q2.z
            var targetW = q2.w

            // Take shortest path
            if (dot < 0f) {
                dot = -dot
                targetX = -targetX
                targetY = -targetY
                targetZ = -targetZ
                targetW = -targetW
            }

            if (dot > 0.9995f) {
                // If points are very close, perform linear interpolation to avoid division by zero
                val result = Quaternion(
                    q1.x + t * (targetX - q1.x),
                    q1.y + t * (targetY - q1.y),
                    q1.z + t * (targetZ - q1.z),
                    q1.w + t * (targetW - q1.w)
                )
                return result.normalize()
            }

            val theta0 = kotlin.math.acos(dot.coerceIn(-1f, 1f))
            val theta = theta0 * t
            val sinTheta = sin(theta)
            val sinTheta0 = sin(theta0)

            val s0 = cos(theta) - dot * sinTheta / sinTheta0
            val s1 = sinTheta / sinTheta0

            return Quaternion(
                (s0 * q1.x) + (s1 * targetX),
                (s0 * q1.y) + (s1 * targetY),
                (s0 * q1.z) + (s1 * targetZ),
                (s0 * q1.w) + (s1 * targetW)
            ).normalize()
        }
    }
}
