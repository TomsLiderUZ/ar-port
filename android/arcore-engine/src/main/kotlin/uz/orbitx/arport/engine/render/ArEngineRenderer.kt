package uz.orbitx.arport.engine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import uz.orbitx.arport.engine.tracking.TrackingManager
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Main OpenGL ES 3.0 renderer orchestrating CameraX Passthrough
 * and 3D world-locked spatial objects across Stereo and Normal viewports.
 */
class ArEngineRenderer(
    private val viewMode: String, // "stereo" or "normal"
    val trackingManager: TrackingManager,
    private val ipdMm: Double = 63.0,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    private val requestRender: () -> Unit
) : GLSurfaceView.Renderer {

    val passthroughRenderer = PassthroughRenderer {
        requestRender()
    }

    val worldObjectRenderer = WorldObjectRenderer()

    private var screenWidth: Int = 1920
    private var screenHeight: Int = 1080

    // Cached matrix buffers for GL thread
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glClearDepthf(1.0f)

        passthroughRenderer.onSurfaceCreated()
        worldObjectRenderer.onSurfaceCreated()

        passthroughRenderer.surfaceTexture?.let { st ->
            onSurfaceTextureReady(st)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        passthroughRenderer.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (viewMode == "stereo") {
            val halfWidth = screenWidth / 2
            val eyeOffsetM = (ipdMm.toFloat() / 1000.0f) * 0.5f

            // 1. Draw Passthrough Background (Left Eye)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            passthroughRenderer.draw(0, 0, halfWidth, screenHeight)

            // 2. Draw Passthrough Background (Right Eye)
            passthroughRenderer.draw(halfWidth, 0, halfWidth, screenHeight)

            // 3. Draw 3D World-Locked Objects with Depth Buffer
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            trackingManager.getProjectionMatrix(projectionMatrix, halfWidth, screenHeight, fovYDeg = 65f)

            // Left Eye 3D Pass
            GLES30.glViewport(0, 0, halfWidth, screenHeight)
            trackingManager.getViewMatrix(viewMatrix, -eyeOffsetM)
            worldObjectRenderer.draw(viewMatrix, projectionMatrix)

            // Right Eye 3D Pass
            GLES30.glViewport(halfWidth, 0, halfWidth, screenHeight)
            trackingManager.getViewMatrix(viewMatrix, eyeOffsetM)
            worldObjectRenderer.draw(viewMatrix, projectionMatrix)

        } else {
            // Normal (Fullscreen) Mode
            // 1. Draw Passthrough Background
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            passthroughRenderer.draw(0, 0, screenWidth, screenHeight)

            // 2. Draw 3D World-Locked Objects
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            GLES30.glViewport(0, 0, screenWidth, screenHeight)

            trackingManager.getProjectionMatrix(projectionMatrix, screenWidth, screenHeight, fovYDeg = 65f)
            trackingManager.getViewMatrix(viewMatrix, 0f)
            worldObjectRenderer.draw(viewMatrix, projectionMatrix)
        }
    }

    fun recenter() {
        trackingManager.recenter()
    }

    fun setCameraPreviewSize(width: Int, height: Int) {
        passthroughRenderer.setCameraPreviewSize(width, height)
    }

    fun getFps(): Float = passthroughRenderer.fpsTracker.getFps()

    fun getLatencyMs(): Float = passthroughRenderer.fpsTracker.getLatencyMs()

    fun release() {
        passthroughRenderer.release()
        worldObjectRenderer.release()
    }
}
