package uz.orbitx.arport.engine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import uz.orbitx.arport.engine.stereo.LensProfile
import uz.orbitx.arport.engine.stereo.StereoRenderer
import uz.orbitx.arport.engine.tracking.TrackingManager
import uz.orbitx.arport.engine.window.WindowManager3D
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Main OpenGL ES 3.0 renderer orchestrating CameraX Passthrough,
 * 3D spatial windows with WindowManager3D, and Stereoscopic barrel distortion post-processing.
 */
class ArEngineRenderer(
    var viewMode: String, // "stereo" or "normal"
    val trackingManager: TrackingManager,
    var ipdMm: Double = 63.0,
    var lensProfileId: String = "cardboard_v2",
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    private val requestRender: () -> Unit
) : GLSurfaceView.Renderer {

    val passthroughRenderer = PassthroughRenderer {
        requestRender()
    }

    val windowManager = WindowManager3D()
    val windowRenderer = WindowRenderer3D()

    val stereoRenderer = StereoRenderer(
        ipdMm = ipdMm,
        lensProfile = LensProfile.fromId(lensProfileId)
    )

    private var screenWidth: Int = 1920
    private var screenHeight: Int = 1080

    // Cached matrix buffers for GL thread
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    private var frameCounter = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glClearDepthf(1.0f)

        passthroughRenderer.onSurfaceCreated()
        stereoRenderer.onSurfaceCreated()
        windowRenderer.onSurfaceCreated()

        passthroughRenderer.surfaceTexture?.let { st ->
            onSurfaceTextureReady(st)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        passthroughRenderer.onSurfaceChanged(width, height)
        stereoRenderer.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        frameCounter++
        if (frameCounter % 30 == 0) {
            // Periodically refresh system monitor window with live metrics
            windowManager.allWindows.find { it.appId == "system_monitor" }?.let { win ->
                windowManager.renderSystemMonitorContent(win, getFps(), getLatencyMs())
            }
        }

        if (viewMode == "stereo") {
            val eyeW = stereoRenderer.eyeWidth
            val eyeH = stereoRenderer.eyeHeight
            val eyeOffsetM = stereoRenderer.getEyeOffsetMeters()

            trackingManager.getProjectionMatrix(projectionMatrix, eyeW, eyeH, fovYDeg = 65f)

            // 1. Left Eye Pass (Render to Left FBO)
            stereoRenderer.beginLeftEye()
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            passthroughRenderer.draw(0, 0, eyeW, eyeH)

            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            trackingManager.getViewMatrix(viewMatrix, -eyeOffsetM)
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            windowRenderer.drawWindows(windowManager, viewMatrix, projectionMatrix, vpMatrix)

            // 2. Right Eye Pass (Render to Right FBO)
            stereoRenderer.beginRightEye()
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            passthroughRenderer.draw(0, 0, eyeW, eyeH)

            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            trackingManager.getViewMatrix(viewMatrix, eyeOffsetM)
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            windowRenderer.drawWindows(windowManager, viewMatrix, projectionMatrix, vpMatrix)

            // 3. Post-processing Barrel Distortion Pass
            stereoRenderer.finishAndDistort()

        } else {
            // Normal (Fullscreen) Mode
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

            // Passthrough Background
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            passthroughRenderer.draw(0, 0, screenWidth, screenHeight)

            // 3D Spatial Windows
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            GLES30.glViewport(0, 0, screenWidth, screenHeight)

            trackingManager.getProjectionMatrix(projectionMatrix, screenWidth, screenHeight, fovYDeg = 65f)
            trackingManager.getViewMatrix(viewMatrix, 0f)
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            windowRenderer.drawWindows(windowManager, viewMatrix, projectionMatrix, vpMatrix)
        }
    }

    fun updateStereoSettings(newIpdMm: Double, newLensProfileId: String) {
        this.ipdMm = newIpdMm
        this.lensProfileId = newLensProfileId
        stereoRenderer.ipdMm = newIpdMm
        stereoRenderer.lensProfile = LensProfile.fromId(newLensProfileId)
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
        windowRenderer.release()
        stereoRenderer.release()
        windowManager.release()
    }
}
