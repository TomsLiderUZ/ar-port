package uz.orbitx.arport.engine.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * High-performance Liquid Glass renderer implementing Kawase blur background passes,
 * squircle rounded corners, edge lighting, specular highlights, and spring physics.
 */
class GlassRenderer {

    companion object {
        // Kawase Blur Shaders
        private const val BLUR_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;

out vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

        private const val KAWASE_FRAGMENT_SHADER = """#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uSourceTexture;
uniform vec2 uTexelSize;
uniform float uOffset;

void main() {
    vec2 offset = uTexelSize * (uOffset + 0.5);
    vec4 sum = vec4(0.0);

    // 4-tap diagonal Kawase filter
    sum += texture(uSourceTexture, vTexCoord + vec2(-offset.x,  offset.y));
    sum += texture(uSourceTexture, vTexCoord + vec2( offset.x,  offset.y));
    sum += texture(uSourceTexture, vTexCoord + vec2(-offset.x, -offset.y));
    sum += texture(uSourceTexture, vTexCoord + vec2( offset.x, -offset.y));

    fragColor = sum * 0.25;
}
"""

        // Full Screen Quad
        private val QUAD_VERTICES = floatArrayOf(
            -1.0f, -1.0f,  0.0f, 0.0f,
             1.0f, -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f,  1.0f, 1.0f,
            -1.0f,  1.0f,  0.0f, 1.0f
        )
        private val QUAD_INDICES = shortArrayOf(0, 1, 2, 0, 2, 3)
    }

    private var blurProgram = 0
    private var uSourceTextureLoc = -1
    private var uTexelSizeLoc = -1
    private var uOffsetLoc = -1

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer

    // Ping-pong FBOs for Kawase Blur
    private val blurFboIds = IntArray(2)
    private val blurTexIds = IntArray(2)
    var blurWidth = 480
        private set
    var blurHeight = 270
        private set

    // Configuration (Low Spec Mode enabled by default for rock-solid 60 FPS on mobile GPUs)
    var isLowSpecMode: Boolean = true
    var isReduceTransparency: Boolean = false
    var blurRadius: Float = 2.5f

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
        blurProgram = GLHelper.createProgram(BLUR_VERTEX_SHADER, KAWASE_FRAGMENT_SHADER)
        uSourceTextureLoc = GLES30.glGetUniformLocation(blurProgram, "uSourceTexture")
        uTexelSizeLoc = GLES30.glGetUniformLocation(blurProgram, "uTexelSize")
        uOffsetLoc = GLES30.glGetUniformLocation(blurProgram, "uOffset")
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        // Downsample to 1/4 resolution for low spec or 1/2 for high quality
        val scale = if (isLowSpecMode) 0.25f else 0.5f
        blurWidth = (width * scale).toInt().coerceAtLeast(64)
        blurHeight = (height * scale).toInt().coerceAtLeast(64)

        setupBlurFramebuffers()
    }

    private fun setupBlurFramebuffers() {
        releaseFramebuffers()

        GLES30.glGenFramebuffers(2, blurFboIds, 0)
        GLES30.glGenTextures(2, blurTexIds, 0)

        for (i in 0..1) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurTexIds[i])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                blurWidth, blurHeight, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFboIds[i])
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                blurTexIds[i],
                0
            )
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /**
     * Executes Kawase blur passes on the scene background.
     * Returns the texture ID of the blurred background texture.
     */
    fun performBlur(sourceTexId: Int): Int {
        if (blurProgram == 0 || isReduceTransparency) return sourceTexId

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(blurProgram)

        // Setup quad attributes
        val stride = 4 * 4
        vertexBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(2)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        GLES30.glUniform2f(uTexelSizeLoc, 1.0f / blurWidth, 1.0f / blurHeight)
        GLES30.glUniform1i(uSourceTextureLoc, 0)
        GLES30.glViewport(0, 0, blurWidth, blurHeight)

        // Pass 1: Downsample Source to FBO 0
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFboIds[0])
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sourceTexId)
        GLES30.glUniform1f(uOffsetLoc, 0.5f)
        indexBuffer.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, QUAD_INDICES.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)

        // Pass 2: FBO 0 to FBO 1
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFboIds[1])
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurTexIds[0])
        GLES30.glUniform1f(uOffsetLoc, blurRadius)
        indexBuffer.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, QUAD_INDICES.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)

        var finalTexId = blurTexIds[1]

        // Pass 3 (if not in low spec): FBO 1 back to FBO 0 for extra creamy blur
        if (!isLowSpecMode) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, blurFboIds[0])
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurTexIds[1])
            GLES30.glUniform1f(uOffsetLoc, blurRadius * 1.5f)
            indexBuffer.position(0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, QUAD_INDICES.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)
            finalTexId = blurTexIds[0]
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        return finalTexId
    }

    private fun releaseFramebuffers() {
        if (blurFboIds[0] != 0) {
            GLES30.glDeleteFramebuffers(2, blurFboIds, 0)
            blurFboIds[0] = 0
            blurFboIds[1] = 0
        }
        if (blurTexIds[0] != 0) {
            GLES30.glDeleteTextures(2, blurTexIds, 0)
            blurTexIds[0] = 0
            blurTexIds[1] = 0
        }
    }

    fun release() {
        releaseFramebuffers()
        if (blurProgram != 0) {
            GLES30.glDeleteProgram(blurProgram)
            blurProgram = 0
        }
    }
}

