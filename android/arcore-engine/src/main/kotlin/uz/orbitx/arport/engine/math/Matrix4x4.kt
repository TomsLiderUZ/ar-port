package uz.orbitx.arport.engine.math

/**
 * Pure Kotlin 4x4 matrix utilities for 3D spatial transformations.
 * Compatible with OpenGL column-major ordering and runnable on both Android and host JVM.
 */
object Matrix4x4 {

    fun setIdentity(m: FloatArray, offset: Int = 0) {
        for (i in 0..15) {
            m[offset + i] = 0.0f
        }
        m[offset + 0] = 1.0f
        m[offset + 5] = 1.0f
        m[offset + 10] = 1.0f
        m[offset + 15] = 1.0f
    }

    fun translate(m: FloatArray, offset: Int, x: Float, y: Float, z: Float) {
        for (i in 0..3) {
            val idx = offset + i
            m[idx + 12] += m[idx] * x + m[idx + 4] * y + m[idx + 8] * z
        }
    }

    fun scale(m: FloatArray, offset: Int, x: Float, y: Float, z: Float) {
        for (i in 0..3) {
            val idx = offset + i
            m[idx] *= x
            m[idx + 4] *= y
            m[idx + 8] *= z
        }
    }

    fun multiply(result: FloatArray, resOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        val temp = FloatArray(16)
        for (i in 0..3) {
            val rhsI = rhsOffset + i * 4
            for (j in 0..3) {
                var sum = 0.0f
                for (k in 0..3) {
                    sum += lhs[lhsOffset + k * 4 + j] * rhs[rhsI + k]
                }
                temp[i * 4 + j] = sum
            }
        }
        System.arraycopy(temp, 0, result, resOffset, 16)
    }

    fun multiplyMV(result: FloatArray, resOffset: Int, lhs: FloatArray, lhsOffset: Int, rhs: FloatArray, rhsOffset: Int) {
        val x = rhs[rhsOffset + 0]
        val y = rhs[rhsOffset + 1]
        val z = rhs[rhsOffset + 2]
        val w = rhs[rhsOffset + 3]

        result[resOffset + 0] = lhs[lhsOffset + 0] * x + lhs[lhsOffset + 4] * y + lhs[lhsOffset + 8] * z + lhs[lhsOffset + 12] * w
        result[resOffset + 1] = lhs[lhsOffset + 1] * x + lhs[lhsOffset + 5] * y + lhs[lhsOffset + 9] * z + lhs[lhsOffset + 13] * w
        result[resOffset + 2] = lhs[lhsOffset + 2] * x + lhs[lhsOffset + 6] * y + lhs[lhsOffset + 10] * z + lhs[lhsOffset + 14] * w
        result[resOffset + 3] = lhs[lhsOffset + 3] * x + lhs[lhsOffset + 7] * y + lhs[lhsOffset + 11] * z + lhs[lhsOffset + 15] * w
    }
}

