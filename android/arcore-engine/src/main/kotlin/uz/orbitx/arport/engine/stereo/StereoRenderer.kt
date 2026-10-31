package uz.orbitx.arport.engine.stereo

import android.opengl.GLES30
import uz.orbitx.arport.engine.render.GLHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Manages stereoscopic dual-viewport rendering, offscreen FBOs,
 * Cardboard v2 barrel distortion post-processing, and center divider line.
 */
class StereoRenderer(
    var ipdMm: Double = 63.0,
    var lensProfile: LensProfile = LensProfile.CARDBOARD_V2
) {

    companion object {
        private const val DISTORTION_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

out vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

        private const val DISTORTION_FRAGMENT_SHADER = """#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uEyeTexture;
uniform vec2 uLensCenter;
uniform vec2 uDistortionCoeffs; // vec2(k1, k2)
uniform float uEnableDistortion;

void main() {
    if (uEnableDistortion < 0.5) {
        fragColor = texture(uEyeTexture, vTexCoord);
        return;
    }

    vec2 d = vTexCoord - uLensCenter;
    float r2 = dot(d, d);
    float r4 = r2 * r2;

    // Inverse barrel distortion to cancel optical pincushion distortion
    float factor = 1.0 + uDistortionCoeffs.x * r2 + uDistortionCoeffs.y * r4;
    vec2 distortedCoord = uLensCenter + d * factor;

    // Vignette black outside valid lens optical range
    if (distortedCoord.x < 0.0 || distortedCoord.x > 1.0 ||
        distortedCoord.y < 0.0 || distortedCoord.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
    } else {
        fragColor = texture(uEyeTexture, distortedCoord);
    }
}
"""

        private val QUAD_VERTICES = floatArrayOf(
            // x, y,   u, v
            -1.0f, -1.0f,  0.0f, 0.0f,
             1.0f, -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f,  1.0f, 1.0f,
            -1.0f,  1.0f,  0.0f, 1.0f
        )

        private val QUAD_INDICES = shortArrayOf(0, 1, 2, 0, 2, 3)
    }

    private var screenWidth: Int = 1920
    private var screenHeight: Int = 1080
    var eyeWidth: Int = 960
        private set
    var eyeHeight: Int = 1080
        private set

    // FBO handles for Left and Right eyes
    private val fboIds = IntArray(2)
    private val colorTexIds = IntArray(2)
    private val depthRbIds = IntArray(2)

    private var distortionProgram = 0
    private var uEyeTextureLoc = -1
    private var uLensCenterLoc = -1
    private var uDistortionCoeffsLoc = -1
    private var uEnableDistortionLoc = -1

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer

    var isDistortionEnabled: Boolean = true

    init {
        vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_VERTICES)
                position(0)
            }

        indexBuffer = ByteBuffer.allocateDirect(QUAD_INDICES.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(QUAD_INDICES)
                position(0)
            }
    }

    fun onSurfaceCreated() {
        distortionProgram = GLHelper.createProgram(DISTORTION_VERTEX_SHADER, DISTORTION_FRAGMENT_SHADER)
        uEyeTextureLoc = GLES30.glGetUniformLocation(distortionProgram, "uEyeTexture")
        uLensCenterLoc = GLES30.glGetUniformLocation(distortionProgram, "uLensCenter")
        uDistortionCoeffsLoc = GLES30.glGetUniformLocation(distortionProgram, "uDistortionCoeffs")
        uEnableDistortionLoc = GLES30.glGetUniformLocation(distortionProgram, "uEnableDistortion")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        eyeWidth = width / 2
        eyeHeight = height

        setupEyeFramebuffers()
    }

    private fun setupEyeFramebuffers() {
        releaseFramebuffers()

        // Generate FBOs, textures, and depth renderbuffers for Left [0] and Right [1]
        GLES30.glGenFramebuffers(2, fboIds, 0)
        GLES30.glGenTextures(2, colorTexIds, 0)
        GLES30.glGenRenderbuffers(2, depthRbIds, 0)

        for (i in 0..1) {
            // Color Texture
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexIds[i])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                eyeWidth, eyeHeight, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )

            // Depth Renderbuffer
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRbIds[i])
            GLES30.glRenderbufferStorage(
                GLES30.GL_RENDERBUFFER,
                GLES30.GL_DEPTH_COMPONENT24,
                eyeWidth,
                eyeHeight
            )

            // Attach to FBO
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                colorTexIds[i],
                0
            )
            GLES30.glFramebufferRenderbuffer(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT,
                GLES30.GL_RENDERBUFFER,
                depthRbIds[i]
            )

            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                android.util.Log.e("StereoRenderer", "FBO $i initialization failed with status $status")
            }
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /**
     * Binds Left Eye FBO and sets viewport for scene rendering.
     */
    fun beginLeftEye() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[0])
        GLES30.glViewport(0, 0, eyeWidth, eyeHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    /**
     * Binds Right Eye FBO and sets viewport for scene rendering.
     */
    fun beginRightEye() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[1])
        GLES30.glViewport(0, 0, eyeWidth, eyeHeight)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    /**
     * Unbinds FBOs and executes the barrel distortion shader pass
     * onto the main screen viewport for both left and right eyes.
     */
    fun finishAndDistort() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        if (distortionProgram == 0) return

        GLES30.glUseProgram(distortionProgram)

        // Setup vertex attributes
        val stride = 4 * 4 // 2 pos + 2 uv floats
        vertexBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        // Upload distortion parameters
        val enabledFloat = if (isDistortionEnabled && lensProfile.k1 > 0f) 1.0f else 0.0f
        GLES30.glUniform1f(uEnableDistortionLoc, enabledFloat)
        GLES30.glUniform2f(uDistortionCoeffsLoc, lensProfile.k1, lensProfile.k2)
        GLES30.glUniform1i(uEyeTextureLoc, 0)

        // 1. Draw Left Eye Viewport [0, 0, eyeWidth, eyeHeight]
        GLES30.glViewport(0, 0, eyeWidth, eyeHeight)
        // Adjust lens optical center with IPD offset
        val ipdShift = ((ipdMm.toFloat() - 63.0f) / 100.0f).coerceIn(-0.1f, 0.1f)
        GLES30.glUniform2f(uLensCenterLoc, 0.5f - ipdShift, 0.5f)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexIds[0])
        indexBuffer.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, QUAD_INDICES.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)

        // 2. Draw Right Eye Viewport [eyeWidth, 0, eyeWidth, eyeHeight]
        GLES30.glViewport(eyeWidth, 0, eyeWidth, eyeHeight)
        GLES30.glUniform2f(uLensCenterLoc, 0.5f + ipdShift, 0.5f)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTexIds[1])
        indexBuffer.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, QUAD_INDICES.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)

        // 3. Draw Center Optical Divider Line (Cardboard separator)
        drawCenterDivider()
    }

    private fun drawCenterDivider() {
        val dividerWidth = 4
        val x = eyeWidth - dividerWidth / 2
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
        GLES30.glScissor(x, 0, dividerWidth, screenHeight)
        GLES30.glClearColor(0.04f, 0.04f, 0.04f, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    }

    fun getEyeOffsetMeters(): Float {
        return (ipdMm.toFloat() / 1000.0f) * 0.5f
    }

    private fun releaseFramebuffers() {
        if (fboIds[0] != 0) {
            GLES30.glDeleteFramebuffers(2, fboIds, 0)
            fboIds[0] = 0
            fboIds[1] = 0
        }
        if (colorTexIds[0] != 0) {
            GLES30.glDeleteTextures(2, colorTexIds, 0)
            colorTexIds[0] = 0
            colorTexIds[1] = 0
        }
        if (depthRbIds[0] != 0) {
            GLES30.glDeleteRenderbuffers(2, depthRbIds, 0)
            depthRbIds[0] = 0
            depthRbIds[1] = 0
        }
    }

    fun release() {
        releaseFramebuffers()
        if (distortionProgram != 0) {
            GLES30.glDeleteProgram(distortionProgram)
            distortionProgram = 0
        }
    }
}

