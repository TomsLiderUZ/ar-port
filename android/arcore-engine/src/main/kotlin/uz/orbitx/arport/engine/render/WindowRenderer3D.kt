package uz.orbitx.arport.engine.render

import android.opengl.GLES30
import android.opengl.Matrix
import uz.orbitx.arport.engine.window.SpatialWindow
import uz.orbitx.arport.engine.window.WindowManager3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * OpenGL ES 3.0 Liquid Glass renderer for 3D spatial windows.
 * Implements Kawase-blurred background refraction, squircle rounding,
 * specular reflection, edge highlights, and physical spring animations.
 */
class WindowRenderer3D {

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uMVPMatrix;
uniform float uScale;

out vec2 vTexCoord;
out vec3 vLocalPos;

void main() {
    vTexCoord = aTexCoord;
    vLocalPos = aPosition;
    vec3 scaledPos = aPosition * uScale;
    gl_Position = uMVPMatrix * vec4(scaledPos, 1.0);
}
"""

        private const val FRAGMENT_SHADER = """#version 300 es
precision mediump float;

in vec2 vTexCoord;
in vec3 vLocalPos;

out vec4 fragColor;

uniform sampler2D uBlurredBackground;
uniform sampler2D uContentTexture;
uniform vec2 uScreenSize;

uniform vec4 uBorderColor;
uniform vec4 uGlassTint;
uniform float uIsFocused;
uniform float uOpacity;
uniform float uReduceTransparency;
uniform float uSpecularIntensity;

void main() {
    // Squircle superellipse formula: |x|^4 + |y|^4 <= 1.0
    vec2 p = abs((vTexCoord - 0.5) * 2.0);
    float p4 = p.x * p.x * p.x * p.x + p.y * p.y * p.y * p.y;
    if (p4 > 1.0) {
        discard; // Pure iOS squircle edge clip
    }

    // Screen-space UV for background refraction
    vec2 screenUV = gl_FragCoord.xy / uScreenSize;
    // Slight edge refraction
    vec2 refractionOffset = (vTexCoord - 0.5) * 0.015;
    vec2 sampleUV = clamp(screenUV + refractionOffset, 0.0, 1.0);

    // 1. Blurred background pass or solid fallback
    vec4 backdrop;
    if (uReduceTransparency > 0.5) {
        backdrop = vec4(0.12, 0.12, 0.14, 0.95);
    } else {
        vec4 blurred = texture(uBlurredBackground, sampleUV);
        backdrop = mix(blurred, uGlassTint, uGlassTint.a);
    }

    // 2. Edge Highlight (luminous glass bevel)
    float edgeDist = 1.0 - pow(p4, 0.25);
    float rimWidth = (uIsFocused > 0.5) ? 0.045 : 0.025;
    float isRim = smoothstep(rimWidth, 0.005, edgeDist);

    // 3. Specular Highlight (subtle top glass sheen)
    float topGradient = smoothstep(0.0, 0.45, vTexCoord.y);
    float specular = pow(topGradient, 3.0) * uSpecularIntensity * 0.35;

    // 4. Sample Window 2D Content
    vec4 content = texture(uContentTexture, vTexCoord);

    // Composite: backdrop + content + specular + rim
    vec4 baseGlass = mix(backdrop, content, content.a);
    baseGlass.rgb += vec3(specular);

    vec4 finalColor = mix(baseGlass, uBorderColor, isRim);
    finalColor.a *= uOpacity;

    fragColor = finalColor;
}
"""

        private val VERTICES = floatArrayOf(
            // x, y, z,   u, v
            -0.5f, -0.5f, 0.0f,  0.0f, 1.0f,
             0.5f, -0.5f, 0.0f,  1.0f, 1.0f,
             0.5f,  0.5f, 0.0f,  1.0f, 0.0f,
            -0.5f,  0.5f, 0.0f,  0.0f, 0.0f
        )

        private val INDICES = shortArrayOf(0, 1, 2, 0, 2, 3)
    }

    private var programId = 0
    private var uMVPMatrixLoc = -1
    private var uScaleLoc = -1
    private var uBlurredBackgroundLoc = -1
    private var uContentTextureLoc = -1
    private var uScreenSizeLoc = -1
    private var uBorderColorLoc = -1
    private var uGlassTintLoc = -1
    private var uIsFocusedLoc = -1
    private var uOpacityLoc = -1
    private var uReduceTransparencyLoc = -1
    private var uSpecularIntensityLoc = -1

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer

    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    // Glass aesthetics configuration
    var isDarkMode: Boolean = true
    var isReduceTransparency: Boolean = false
    var specularIntensity: Float = 0.65f

    init {
        vertexBuffer = ByteBuffer.allocateDirect(VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(VERTICES)
                position(0)
            }

        indexBuffer = ByteBuffer.allocateDirect(INDICES.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(INDICES)
                position(0)
            }
    }

    fun onSurfaceCreated() {
        programId = GLHelper.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMVPMatrixLoc = GLES30.glGetUniformLocation(programId, "uMVPMatrix")
        uScaleLoc = GLES30.glGetUniformLocation(programId, "uScale")
        uBlurredBackgroundLoc = GLES30.glGetUniformLocation(programId, "uBlurredBackground")
        uContentTextureLoc = GLES30.glGetUniformLocation(programId, "uContentTexture")
        uScreenSizeLoc = GLES30.glGetUniformLocation(programId, "uScreenSize")
        uBorderColorLoc = GLES30.glGetUniformLocation(programId, "uBorderColor")
        uGlassTintLoc = GLES30.glGetUniformLocation(programId, "uGlassTint")
        uIsFocusedLoc = GLES30.glGetUniformLocation(programId, "uIsFocused")
        uOpacityLoc = GLES30.glGetUniformLocation(programId, "uOpacity")
        uReduceTransparencyLoc = GLES30.glGetUniformLocation(programId, "uReduceTransparency")
        uSpecularIntensityLoc = GLES30.glGetUniformLocation(programId, "uSpecularIntensity")
    }

    /**
     * Draws all visible spatial windows with Liquid Glass rendering and spring physics.
     */
    fun drawWindows(
        windowManager: WindowManager3D,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        vpMatrix: FloatArray,
        blurredBackdropTexId: Int,
        screenWidth: Int,
        screenHeight: Int,
        dtSec: Float = 0.016f
    ) {
        if (programId == 0) return

        GLES30.glUseProgram(programId)

        // Setup vertex attributes
        val stride = 5 * 4
        vertexBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // Upload shared uniforms
        GLES30.glUniform2f(uScreenSizeLoc, screenWidth.toFloat(), screenHeight.toFloat())
        GLES30.glUniform1f(uReduceTransparencyLoc, if (isReduceTransparency) 1.0f else 0.0f)
        GLES30.glUniform1f(uSpecularIntensityLoc, specularIntensity)

        // Dark or light glass tint
        if (isDarkMode) {
            GLES30.glUniform4f(uGlassTintLoc, 0.11f, 0.11f, 0.13f, 0.65f) // #1C1C1E glass tint
        } else {
            GLES30.glUniform4f(uGlassTintLoc, 0.95f, 0.95f, 0.97f, 0.70f) // #F2F2F7 light glass
        }

        // Texture Unit 0: Blurred Backdrop
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurredBackdropTexId)
        GLES30.glUniform1i(uBlurredBackgroundLoc, 0)

        // Texture Unit 1: Window 2D Content
        GLES30.glUniform1i(uContentTextureLoc, 1)

        val visibleWindows = windowManager.activeWindows.sortedBy { it.zOrder }

        for (window in visibleWindows) {
            // Update spring scale physics
            val currentScale = window.scaleSpring.update(dtSec)

            // Frustum Culling
            if (!windowManager.isWindowInFrustum(window, vpMatrix)) {
                continue
            }

            // Ensure offscreen surface texture is ready
            val surface = window.contentSurface
            if (surface != null) {
                if (surface.textureId == 0) {
                    surface.onSurfaceCreated()
                }
                // Only update texture when window is active and not sleeping
                if (!window.isSleeping) {
                    surface.updateTextureIfNeeded()
                }
                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, surface.textureId)
            }

            // Compute MVP Matrix = Projection * View * WindowModel
            Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, window.getModelMatrix(), 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
            GLES30.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
            GLES30.glUniform1f(uScaleLoc, currentScale)

            // Focus border and opacity styling
            if (window.isFocused) {
                GLES30.glUniform4f(uBorderColorLoc, 0.04f, 0.52f, 1.0f, 0.95f) // Vibrant #0A84FF
                GLES30.glUniform1f(uIsFocusedLoc, 1.0f)
                GLES30.glUniform1f(uOpacityLoc, window.opacity)
            } else {
                GLES30.glUniform4f(uBorderColorLoc, 0.55f, 0.55f, 0.60f, 0.40f) // Soft translucent gray
                GLES30.glUniform1f(uIsFocusedLoc, 0.0f)
                val sleepingOpacity = if (window.isSleeping) 0.45f else window.opacity * 0.85f
                GLES30.glUniform1f(uOpacityLoc, sleepingOpacity)
            }

            indexBuffer.position(0)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, INDICES.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)
        }

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}
