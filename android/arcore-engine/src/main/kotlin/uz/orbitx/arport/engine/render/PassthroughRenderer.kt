package uz.orbitx.arport.engine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import java.nio.FloatBuffer

/**
 * High-performance Zero-Copy GPU Camera Passthrough Renderer using OpenGL ES 3.0 and OES Textures.
 * Supports arbitrary viewport rendering (stereo eye & fullscreen), dynamic upright rotation,
 * and distortion-free center-crop aspect filling.
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
            uniform mat4 uTransformMatrix;
            out vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vec4 transformedUV = uTransformMatrix * aTexCoord;
                vTexCoord = (uTexMatrix * vec4(transformedUV.xy, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER = """#version 300 es
            #extension GL_OES_EGL_image_external_essl3 : enable
            #extension GL_OES_EGL_image_external : enable
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

        // Texture coordinates in [0, 1]
        private val TEX_COORDS = floatArrayOf(
            0.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f
        )
    }

    private var program: Int = 0
    private var uTexMatrixLoc: Int = -1
    private var uTransformMatrixLoc: Int = -1

    private val vertexBuffer: FloatBuffer = GLHelper.createFloatBuffer(QUAD_COORDS)
    private val texCoordBuffer: FloatBuffer = GLHelper.createFloatBuffer(TEX_COORDS)

    private val texMatrix = FloatArray(16)
    private val transformMatrix = FloatArray(16)

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

    // Standard phone back camera in landscape requires 90° rotation to be upright
    var cameraRotationDegrees: Int = 90
        private set

    fun onSurfaceCreated() {
        program = GLHelper.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uTexMatrixLoc = GLES30.glGetUniformLocation(program, "uTexMatrix")
        uTransformMatrixLoc = GLES30.glGetUniformLocation(program, "uTransformMatrix")

        textureId = GLHelper.createOESTexture()
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener(this@PassthroughRenderer)
        }

        Matrix.setIdentityM(texMatrix, 0)
        Matrix.setIdentityM(transformMatrix, 0)
        Log.i(TAG, "PassthroughRenderer surface created. Texture ID: $textureId")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    fun setCameraPreviewSize(width: Int, height: Int) {
        cameraWidth = width
        cameraHeight = height
    }

    fun setCameraRotation(rotation: Int) {
        cameraRotationDegrees = (rotation % 360 + 360) % 360
        Log.i(TAG, "Passthrough camera rotation set to: $cameraRotationDegrees")
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        isFrameAvailable = true
        onFrameAvailableListener()
    }

    /**
     * Dynamically computes the unstretched center-crop and upright rotation matrix for a specific viewport.
     */
    private fun updateTransformMatrix(vpWidth: Int, vpHeight: Int) {
        Matrix.setIdentityM(transformMatrix, 0)
        if (vpWidth <= 0 || vpHeight <= 0 || cameraWidth <= 0 || cameraHeight <= 0) return

        val vpAspect = vpWidth.toFloat() / vpHeight.toFloat()
        // If rotated by 90 or 270 degrees, effective camera width and height are swapped
        val isSwapped = (cameraRotationDegrees == 90 || cameraRotationDegrees == 270)
        val camW = if (isSwapped) cameraHeight.toFloat() else cameraWidth.toFloat()
        val camH = if (isSwapped) cameraWidth.toFloat() else cameraHeight.toFloat()
        val camAspect = camW / camH

        var scaleX = 1.0f
        var scaleY = 1.0f

        // Center-crop (aspect-fill) calculation: fills viewport completely without stretching
        if (vpAspect > camAspect) {
            scaleY = camAspect / vpAspect
        } else {
            scaleX = vpAspect / camAspect
        }

        // 1. Move UV origin to center (0.5, 0.5)
        Matrix.translateM(transformMatrix, 0, 0.5f, 0.5f, 0.0f)
        // 2. Rotate UV around center to make camera upright
        Matrix.rotateM(transformMatrix, 0, -cameraRotationDegrees.toFloat(), 0.0f, 0.0f, 1.0f)
        // 3. Apply aspect ratio crop scale
        Matrix.scaleM(transformMatrix, 0, scaleX, scaleY, 1.0f)
        // 4. Move UV origin back
        Matrix.translateM(transformMatrix, 0, -0.5f, -0.5f, 0.0f)
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

        updateTransformMatrix(width, height)

        GLES30.glUseProgram(program)

        // Pass matrices
        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        GLES30.glUniformMatrix4fv(uTransformMatrixLoc, 1, false, transformMatrix, 0)

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
