package uz.orbitx.arport.engine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Main OpenGL ES 3.0 renderer orchestrating Passthrough and spatial viewports.
 */
class ArEngineRenderer(
    private val viewMode: String, // "stereo" or "normal"
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    private val requestRender: () -> Unit
) : GLSurfaceView.Renderer {

    private val passthroughRenderer = PassthroughRenderer {
        requestRender()
    }

    private var screenWidth: Int = 1920
    private var screenHeight: Int = 1080

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        passthroughRenderer.onSurfaceCreated()

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
            // Stereo mode: Left Eye Viewport
            val halfWidth = screenWidth / 2
            passthroughRenderer.draw(0, 0, halfWidth, screenHeight)

            // Stereo mode: Right Eye Viewport
            passthroughRenderer.draw(halfWidth, 0, halfWidth, screenHeight)
        } else {
            // Normal Fullscreen Viewport
            passthroughRenderer.draw(0, 0, screenWidth, screenHeight)
        }
    }

    fun setCameraPreviewSize(width: Int, height: Int) {
        passthroughRenderer.setCameraPreviewSize(width, height)
    }

    fun getFps(): Float = passthroughRenderer.fpsTracker.getFps()

    fun getLatencyMs(): Float = passthroughRenderer.fpsTracker.getLatencyMs()

    fun release() {
        passthroughRenderer.release()
    }
}

