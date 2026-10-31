package uz.orbitx.arport.engine.window

import uz.orbitx.arport.engine.math.Matrix4x4
import uz.orbitx.arport.engine.math.Quaternion
import java.util.UUID
import kotlin.math.abs

enum class WindowAspect {
    LOCKED_16_9,
    LOCKED_9_16,
    FREE
}

enum class WindowState {
    NORMAL,
    MINIMIZED,
    PINNED,
    FULLSCREEN
}

/**
 * Represents a 3D spatial floating window anchored in world space.
 */
class SpatialWindow(
    val id: String = UUID.randomUUID().toString(),
    val appId: String,
    var title: String,
    var posX: Float = 0.0f,
    var posY: Float = 0.0f,
    var posZ: Float = -1.6f,
    var widthMeters: Float = 1.0f,
    var heightMeters: Float = 0.5625f, // Default 16:9
    var aspect: WindowAspect = WindowAspect.LOCKED_16_9,
    var state: WindowState = WindowState.NORMAL
) {
    val orientationQ = Quaternion.identity()
    var isFocused: Boolean = false
    var zOrder: Int = 0
    var opacity: Float = 1.0f
    var isSleeping: Boolean = false

    // Offscreen content surface provider
    var contentSurface: ContentSurface? = null

    // Cached model matrix
    private val modelMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    private val rotMatrix = FloatArray(16)
    private var isModelMatrixDirty = true

    init {
        applyAspectDimensions()
        updateModelMatrix()
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        posX = x
        posY = y
        posZ = z
        isModelMatrixDirty = true
    }

    fun setSize(width: Float, height: Float) {
        widthMeters = width.coerceIn(0.3f, 4.0f)
        heightMeters = height.coerceIn(0.2f, 3.0f)
        isModelMatrixDirty = true
    }

    fun updateAspect(newAspect: WindowAspect) {
        aspect = newAspect
        applyAspectDimensions()
        isModelMatrixDirty = true
    }

    private fun applyAspectDimensions() {
        when (aspect) {
            WindowAspect.LOCKED_16_9 -> {
                heightMeters = widthMeters * (9.0f / 16.0f)
            }
            WindowAspect.LOCKED_9_16 -> {
                heightMeters = widthMeters * (16.0f / 9.0f)
            }
            WindowAspect.FREE -> {
                // Keep current dimensions
            }
        }
    }

    fun getModelMatrix(): FloatArray {
        if (isModelMatrixDirty) {
            updateModelMatrix()
        }
        return modelMatrix
    }

    private fun updateModelMatrix() {
        Matrix4x4.setIdentity(modelMatrix, 0)
        Matrix4x4.translate(modelMatrix, 0, posX, posY, posZ)

        orientationQ.toRotationMatrix(rotMatrix, 0)
        Matrix4x4.multiply(tempMatrix, 0, modelMatrix, 0, rotMatrix, 0)

        Matrix4x4.scale(tempMatrix, 0, widthMeters, heightMeters, 1.0f)
        System.arraycopy(tempMatrix, 0, modelMatrix, 0, 16)
        isModelMatrixDirty = false
    }

    /**
     * Tests if a 3D ray (origin + dir) intersects this window's plane.
     * Returns UV hit coordinates [0..1, 0..1] if hit, or null if missed.
     */
    fun hitTest(rayOriginX: Float, rayOriginY: Float, rayOriginZ: Float,
                rayDirX: Float, rayDirY: Float, rayDirZ: Float): HitResult? {
        // Plane normal from orientation (default looking forward: +Z in local coordinates)
        val normalX = 2f * (orientationQ.x * orientationQ.z + orientationQ.w * orientationQ.y)
        val normalY = 2f * (orientationQ.y * orientationQ.z - orientationQ.w * orientationQ.x)
        val normalZ = 1f - 2f * (orientationQ.x * orientationQ.x + orientationQ.y * orientationQ.y)

        val denom = normalX * rayDirX + normalY * rayDirY + normalZ * rayDirZ
        if (abs(denom) < 0.0001f) return null // Ray parallel to window plane

        val diffX = posX - rayOriginX
        val diffY = posY - rayOriginY
        val diffZ = posZ - rayOriginZ

        val t = (diffX * normalX + diffY * normalY + diffZ * normalZ) / denom
        if (t < 0.05f || t > 20.0f) return null // Hit behind ray or too far

        // Hit point in world space
        val hitX = rayOriginX + t * rayDirX
        val hitY = rayOriginY + t * rayDirY
        val hitZ = rayOriginZ + t * rayDirZ

        // Transform hit point into local window space [-0.5..0.5]
        val relX = hitX - posX
        val relY = hitY - posY

        val halfW = widthMeters * 0.5f
        val halfH = heightMeters * 0.5f

        if (abs(relX) <= halfW && abs(relY) <= halfH) {
            val u = (relX / widthMeters) + 0.5f
            val v = (relY / heightMeters) + 0.5f
            return HitResult(t, u, v, getTargetComponent(u, v))
        }

        return null
    }

    private fun getTargetComponent(u: Float, v: Float): WindowComponent {
        // Top header bar is top 12% of height
        if (v > 0.88f) {
            return when {
                u > 0.90f -> WindowComponent.CLOSE_BUTTON
                u > 0.82f -> WindowComponent.MINIMIZE_BUTTON
                u > 0.74f -> WindowComponent.PIN_BUTTON
                else -> WindowComponent.HEADER_TITLE
            }
        }
        // Bottom drag bar is bottom 8% of height
        if (v < 0.08f) {
            return WindowComponent.BOTTOM_DRAG_HANDLE
        }
        // Bottom-right corner resize handle
        if (v < 0.15f && u > 0.85f) {
            return WindowComponent.RESIZE_HANDLE
        }
        return WindowComponent.CONTENT_AREA
    }

    data class HitResult(
        val distance: Float,
        val u: Float,
        val v: Float,
        val component: WindowComponent
    )

    enum class WindowComponent {
        CONTENT_AREA,
        HEADER_TITLE,
        CLOSE_BUTTON,
        MINIMIZE_BUTTON,
        PIN_BUTTON,
        BOTTOM_DRAG_HANDLE,
        RESIZE_HANDLE
    }
}

