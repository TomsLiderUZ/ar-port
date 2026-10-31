package uz.orbitx.arport.engine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import java.nio.FloatBuffer

/**
 * High-performance Zero-Copy GPU Camera Passthrough Renderer using OpenGL ES 3.0 and OES Textures.
 */
class PassthroughRenderer(
    private val onFrameAvailableListener: () -> Unit
) : SurfaceTexture.OnFrameAvailableListener {

    companion object {
        private const val TAG = "ARPort_Passthrough"

        private const val VERTEX_SHADER = """#version 300 es
            layout(location = 0) in vec4 aPosition;
            layout(location = 1) in vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            uniform mat4 uCropMatrix;
            out vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vec4 transformedTex = uTexMatrix * aTexCoord;
                vTexCoord = (uCropMatrix * vec4(transformedTex.xy, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            in vec2 vTexCoord;
            out vec4 fragColor;
            void main() {
                fragColor = texture(sTexture, vTexCoord);
            }
        """

        // Fullscreen quad covering NDC [-1, 1]
        private val QUAD_COORDS = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        )

        // Texture coordinates
        private val TEX_COORDS = floatArrayOf(
            0.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f
        )
    }

    private var program: Int = 0
    private var uTexMatrixLoc: Int = -1
    private var uCropMatrixLoc: Int = -1

    private val vertexBuffer: FloatBuffer = GLHelper.createFloatBuffer(QUAD_COORDS)
    private val texCoordBuffer: FloatBuffer = GLHelper.createFloatBuffer(TEX_COORDS)

    private val texMatrix = FloatArray(16)
    private val cropMatrix = FloatArray(16)

    var textureId: Int = 0
        private set
    var surfaceTexture: SurfaceTexture? = null
        private set

    val fpsTracker = FpsTracker()

    @Volatile
    private var isFrameAvailable = false

    private var cameraWidth: Int = 1920
    private var cameraHeight: Int = 1080
    private var screenWidth: Int = 1920
    private var screenHeight: Int = 1080

    fun onSurfaceCreated() {
        program = GLHelper.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uTexMatrixLoc = GLES30.glGetUniformLocation(program, "uTexMatrix")
        uCropMatrixLoc = GLES30.glGetUniformLocation(program, "uCropMatrix")

        textureId = GLHelper.createOESTexture()
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@PassthroughRenderer)
        }

        Matrix.setIdentityM(texMatrix, 0)
        Matrix.setIdentityM(cropMatrix, 0)
        Log.i(TAG, "PassthroughRenderer surface created. Texture ID: $textureId")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        updateCropMatrix()
    }

    fun setCameraPreviewSize(width: Int, height: Int) {
        cameraWidth = width
        cameraHeight = height
        updateCropMatrix()
    }

    private fun updateCropMatrix() {
        Matrix.setIdentityM(cropMatrix, 0)
        if (screenWidth <= 0 || screenHeight <= 0 || cameraWidth <= 0 || cameraHeight <= 0) return

        val screenAspect = screenWidth.toFloat() / screenHeight.toFloat()
        val cameraAspect = cameraWidth.toFloat() / cameraHeight.toFloat()

        var scaleX = 1.0f
        var scaleY = 1.0f

        // Center-crop calculation: fill screen without stretching
        if (screenAspect > cameraAspect) {
            scaleY = cameraAspect / screenAspect
        } else {
            scaleX = screenAspect / cameraAspect
        }

        // Apply scale centered around (0.5, 0.5)
        Matrix.translateM(cropMatrix, 0, 0.5f, 0.5f, 0.0f)
        Matrix.scaleM(cropMatrix, 0, scaleX, scaleY, 1.0f)
        Matrix.translateM(cropMatrix, 0, -0.5f, -0.5f, 0.0f)
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        isFrameAvailable = true
        onFrameAvailableListener()
    }

    /**
     * Updates and renders the latest camera passthrough frame into the specified viewport.
     */
    fun draw(x: Int, y: Int, width: Int, height: Int) {
        val st = surfaceTexture ?: return
        GLES30.glViewport(x, y, width, height)

        if (isFrameAvailable) {
            try {
                st.updateTexImage()
                st.getTransformMatrix(texMatrix)
                isFrameAvailable = false
                fpsTracker.onFrameRendered(st.timestamp)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to updateTexImage: ${e.message}")
            }
        }

        GLES30.glUseProgram(program)

        // Pass matrices
        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        GLES30.glUniformMatrix4fv(uCropMatrixLoc, 1, false, cropMatrix, 0)

        // Bind OES texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        // Vertex positions
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        // Texture coordinates
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        // Draw quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glUseProgram(0)
    }

    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
        if (textureId != 0) {
            val textures = intArrayOf(textureId)
            GLES30.glDeleteTextures(1, textures, 0)
            textureId = 0
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }
}

