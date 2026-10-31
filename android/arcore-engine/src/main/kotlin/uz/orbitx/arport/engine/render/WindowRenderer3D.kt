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
 * OpenGL ES 3.0 renderer for 3D spatial windows.
 * Renders window content textures, header bars, focus glow borders, and rounded corners.
 */
class WindowRenderer3D {

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uMVPMatrix;

out vec2 vTexCoord;

void main() {
    vTexCoord = aTexCoord;
    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
}
"""

        private const val FRAGMENT_SHADER = """#version 300 es
precision mediump float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uContentTexture;
uniform vec4 uBorderColor;
uniform float uIsFocused;
uniform float uOpacity;

void main() {
    vec2 p = abs((vTexCoord - 0.5) * 2.0);
    float edgeDistX = 1.0 - p.x;
    float edgeDistY = 1.0 - p.y;
    float minEdge = min(edgeDistX, edgeDistY);

    // Rounded corner clip (squircle radius)
    vec2 cornerDelta = max(p - 0.88, 0.0);
    if (length(cornerDelta) > 0.12) {
        discard;
    }

    // Border highlight
    float borderThickness = (uIsFocused > 0.5) ? 0.035 : 0.018;
    float isBorder = smoothstep(borderThickness, 0.005, minEdge);

    // Sample offscreen 2D content
    vec4 content = texture(uContentTexture, vTexCoord);

    vec4 finalColor = mix(content, uBorderColor, isBorder);
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
    private var uContentTextureLoc = -1
    private var uBorderColorLoc = -1
    private var uIsFocusedLoc = -1
    private var uOpacityLoc = -1

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer

    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

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
        uContentTextureLoc = GLES30.glGetUniformLocation(programId, "uContentTexture")
        uBorderColorLoc = GLES30.glGetUniformLocation(programId, "uBorderColor")
        uIsFocusedLoc = GLES30.glGetUniformLocation(programId, "uIsFocused")
        uOpacityLoc = GLES30.glGetUniformLocation(programId, "uOpacity")
    }

    /**
     * Draws all visible spatial windows in back-to-front z-order.
     */
    fun drawWindows(
        windowManager: WindowManager3D,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        vpMatrix: FloatArray
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
        GLES30.glUniform1i(uContentTextureLoc, 0)

        val visibleWindows = windowManager.activeWindows.sortedBy { it.zOrder }

        for (window in visibleWindows) {
            // Frustum Culling: skip rendering if window is outside camera FOV
            if (!windowManager.isWindowInFrustum(window, vpMatrix)) {
                continue
            }

            // Ensure offscreen surface texture is initialized and updated
            val surface = window.contentSurface
            if (surface != null) {
                if (surface.textureId == 0) {
                    surface.onSurfaceCreated()
                }
                surface.updateTextureIfNeeded()
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, surface.textureId)
            }

            // Compute MVP Matrix = Projection * View * WindowModel
            Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, window.getModelMatrix(), 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
            GLES30.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)

            // Focus border and opacity styling
            if (window.isFocused) {
                GLES30.glUniform4f(uBorderColorLoc, 0.04f, 0.52f, 1.0f, 0.95f) // Vibrant iOS Blue
                GLES30.glUniform1f(uIsFocusedLoc, 1.0f)
                GLES30.glUniform1f(uOpacityLoc, window.opacity)
            } else {
                GLES30.glUniform4f(uBorderColorLoc, 0.5f, 0.5f, 0.55f, 0.50f) // Soft translucent gray
                GLES30.glUniform1f(uIsFocusedLoc, 0.0f)
                val sleepingOpacity = if (window.isSleeping) 0.5f else window.opacity * 0.85f
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

