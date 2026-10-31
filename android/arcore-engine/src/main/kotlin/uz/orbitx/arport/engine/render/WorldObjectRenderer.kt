package uz.orbitx.arport.engine.render

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Renders a 3D world-locked floating spatial card / window proxy in OpenGL ES 3.0.
 * Anchored at world coordinates (e.g. 1.5m in front of the observer) and remains
 * perfectly motionless in the physical room when the user moves or turns their device.
 */
class WorldObjectRenderer {

    companion object {
        private const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uMVPMatrix;

out vec2 vTexCoord;
out vec3 vLocalPos;

void main() {
    vTexCoord = aTexCoord;
    vLocalPos = aPosition;
    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
}
"""

        private const val FRAGMENT_SHADER = """#version 300 es
precision mediump float;

in vec2 vTexCoord;
in vec3 vLocalPos;

uniform vec4 uBorderColor;
uniform vec4 uSurfaceColor;

out vec4 fragColor;

void main() {
    vec2 p = (vTexCoord - 0.5) * 2.0;
    vec2 d = abs(p);

    // Border detection (iOS rounded glass aesthetic)
    float edgeDistX = 1.0 - d.x;
    float edgeDistY = 1.0 - d.y;
    float minEdge = min(edgeDistX, edgeDistY);
    float isBorder = smoothstep(0.04, 0.01, minEdge);

    // Subtle inner spatial grid
    vec2 gridCoord = vTexCoord * 8.0;
    vec2 grid = abs(fract(gridCoord - 0.5) - 0.5) / fwidth(gridCoord);
    float line = min(grid.x, grid.y);
    float gridAlpha = 1.0 - clamp(line, 0.0, 1.0);

    // Center targeting reticle
    float centerDist = length(p * vec2(1.777, 1.0));
    float reticle = smoothstep(0.03, 0.0, abs(centerDist - 0.20));

    // Combine glass card colors
    vec4 baseColor = uSurfaceColor;
    baseColor.rgb += vec3(0.04, 0.52, 1.0) * (gridAlpha * 0.15 + reticle * 0.5);

    vec4 finalColor = mix(baseColor, uBorderColor, isBorder);
    fragColor = finalColor;
}
"""

        // Quad vertices (centered at origin, width=1.0, height=1.0)
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
    private var uBorderColorLoc = -1
    private var uSurfaceColorLoc = -1

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer

    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    // World placement (1.5 meters directly in front of origin, 16:9 aspect ratio)
    var worldPosX: Float = 0.0f
    var worldPosY: Float = 0.0f
    var worldPosZ: Float = -1.5f

    var widthMeters: Float = 0.85f
    var heightMeters: Float = 0.48f // ~16:9 ratio

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

        updateModelMatrix()
    }

    fun onSurfaceCreated() {
        programId = GLHelper.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        uMVPMatrixLoc = GLES30.glGetUniformLocation(programId, "uMVPMatrix")
        uBorderColorLoc = GLES30.glGetUniformLocation(programId, "uBorderColor")
        uSurfaceColorLoc = GLES30.glGetUniformLocation(programId, "uSurfaceColor")
    }

    fun updateModelMatrix() {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, worldPosX, worldPosY, worldPosZ)
        Matrix.scaleM(modelMatrix, 0, widthMeters, heightMeters, 1.0f)
    }

    /**
     * Draws the world-locked 3D object for the given eye/viewport.
     */
    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (programId == 0) return

        // Compute MVP = Projection * View * Model
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        GLES30.glUseProgram(programId)

        // Upload MVP Matrix
        GLES30.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)

        // Upload Colors: iOS vibrant blue border and frosted dark glass surface
        GLES30.glUniform4f(uBorderColorLoc, 0.04f, 0.52f, 1.0f, 0.90f) // #0A84FF
        GLES30.glUniform4f(uSurfaceColorLoc, 0.08f, 0.10f, 0.14f, 0.65f) // Frosted glass

        // Bind attributes
        val stride = 5 * 4 // 5 floats per vertex (3 pos + 2 uv)
        vertexBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, vertexBuffer)

        // Enable alpha blending
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // Draw indices
        indexBuffer.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, INDICES.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)

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

