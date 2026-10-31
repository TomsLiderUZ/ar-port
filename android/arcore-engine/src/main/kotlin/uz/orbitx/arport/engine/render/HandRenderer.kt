package uz.orbitx.arport.engine.render

import android.opengl.GLES30
import android.opengl.Matrix
import uz.orbitx.arport.engine.hand.TrackedHand
import uz.orbitx.arport.engine.window.WindowManager3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 3.0 renderer for hand tracking visualization.
 * Renders glowing hand auras on landmarks, skeletal bone connectors,
 * 3D laser hand ray, and dynamic hover reticles on spatial windows (TZ 6.2).
 */
class HandRenderer {

    companion object {
        // Point Aura Shader
        private const val POINT_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in float aSize;

uniform mat4 uMVPMatrix;

out float vSize;

void main() {
    vSize = aSize;
    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
    gl_PointSize = aSize;
}
"""

        private const val POINT_FRAGMENT_SHADER = """#version 300 es
precision mediump float;

in float vSize;
uniform vec4 uGlowColor;

out vec4 fragColor;

void main() {
    vec2 p = gl_PointCoord - vec2(0.5);
    float dist = length(p);
    if (dist > 0.5) {
        discard;
    }
    // Radial soft glow falloff
    float glow = smoothstep(0.5, 0.0, dist);
    fragColor = vec4(uGlowColor.rgb, uGlowColor.a * glow);
}
"""

        // Line Shader for Bones and Ray
        private const val LINE_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPosition;

uniform mat4 uMVPMatrix;

void main() {
    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
}
"""

        private const val LINE_FRAGMENT_SHADER = """#version 300 es
precision mediump float;

uniform vec4 uLineColor;

out vec4 fragColor;

void main() {
    fragColor = uLineColor;
}
"""
    }

    private var pointProgram = 0
    private var uPointMVPLoc = -1
    private var uGlowColorLoc = -1

    private var lineProgram = 0
    private var uLineMVPLoc = -1
    private var uLineColorLoc = -1

    // Dynamic vertex buffers
    private val pointsBuffer: FloatBuffer
    private val linesBuffer: FloatBuffer
    private val mvpMatrix = FloatArray(16)
    var isGlowEnabled: Boolean = true

    init {
        // Up to 42 points (2 hands * 21 points), 4 floats per point (x, y, z, size)
        pointsBuffer = ByteBuffer.allocateDirect(42 * 4 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        // Up to 100 lines (2 hands * 21 bones + rays), 3 floats per vertex
        linesBuffer = ByteBuffer.allocateDirect(200 * 3 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
    }

    fun onSurfaceCreated() {
        pointProgram = GLHelper.createProgram(POINT_VERTEX_SHADER, POINT_FRAGMENT_SHADER)
        uPointMVPLoc = GLES30.glGetUniformLocation(pointProgram, "uMVPMatrix")
        uGlowColorLoc = GLES30.glGetUniformLocation(pointProgram, "uGlowColor")

        lineProgram = GLHelper.createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
        uLineMVPLoc = GLES30.glGetUniformLocation(lineProgram, "uMVPMatrix")
        uLineColorLoc = GLES30.glGetUniformLocation(lineProgram, "uLineColor")
    }

    /**
     * Draws hand skeletal glow, pointer rays, and window hover reticles.
     */
    fun drawHands(
        hands: List<TrackedHand>,
        windowManager: WindowManager3D,
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray
    ) {
        if (!isGlowEnabled || hands.isEmpty() || pointProgram == 0 || lineProgram == 0) return

        // Compute Camera VP Matrix
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE) // Additive glow
        GLES30.glDisable(GLES30.GL_DEPTH_TEST) // Render on top of scene (overlay)

        for (hand in hands) {
            // 1. Raycast hand pointer ray against spatial windows
            val hit = windowManager.raycast(
                hand.rayOriginX, hand.rayOriginY, hand.rayOriginZ,
                hand.rayDirX, hand.rayDirY, hand.rayDirZ
            )

            if (hit != null) {
                hand.isHoveringWindow = true
                hand.hoveredWindowId = hit.first.id
                hand.hoverHitU = hit.second.u
                hand.hoverHitV = hit.second.v
                hand.hoverDistance = hit.second.distance
            } else {
                hand.isHoveringWindow = false
                hand.hoveredWindowId = null
                hand.hoverDistance = 2.0f
            }

            // 2. Draw 21 Landmark Glow Points
            drawLandmarkPoints(hand)

            // 3. Draw Skeletal Bones and Laser Ray
            drawSkeletalBonesAndRay(hand)
        }

        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawLandmarkPoints(hand: TrackedHand) {
        pointsBuffer.clear()

        for (i in 0 until 21) {
            val lm = hand.landmarks[i]
            // Project into camera local coordinates (depth ~ 0.45m)
            val z = -0.45f - (lm.z * 0.15f)
            val aspect = 16f / 9f
            val x = ((lm.x * 2f) - 1f) * 0.28f * aspect
            val y = (1f - (lm.y * 2f)) * 0.28f

            val size = when (i) {
                TrackedHand.INDEX_FINGER_TIP, TrackedHand.THUMB_TIP -> 28.0f // Enlarged fingertip aura
                else -> 16.0f
            }

            pointsBuffer.put(x)
            pointsBuffer.put(y)
            pointsBuffer.put(z)
            pointsBuffer.put(size)
        }

        // Add hover spot at ray intersection
        if (hand.isHoveringWindow) {
            val hitX = hand.rayOriginX + hand.rayDirX * hand.hoverDistance
            val hitY = hand.rayOriginY + hand.rayDirY * hand.hoverDistance
            val hitZ = hand.rayOriginZ + hand.rayDirZ * hand.hoverDistance
            pointsBuffer.put(hitX)
            pointsBuffer.put(hitY)
            pointsBuffer.put(hitZ)
            pointsBuffer.put(48.0f) // Bright hover reticle dot
        }

        pointsBuffer.position(0)

        GLES30.glUseProgram(pointProgram)
        GLES30.glUniformMatrix4fv(uPointMVPLoc, 1, false, mvpMatrix, 0)
        // Cyan-blue luminous aura (#64D2FF)
        GLES30.glUniform4f(uGlowColorLoc, 0.39f, 0.82f, 1.0f, 0.85f)

        pointsBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 4 * 4, pointsBuffer)

        pointsBuffer.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 4 * 4, pointsBuffer)

        val pointCount = if (hand.isHoveringWindow) 22 else 21
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, pointCount)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun drawSkeletalBonesAndRay(hand: TrackedHand) {
        linesBuffer.clear()
        val aspect = 16f / 9f

        // Skeletal bones
        for (bone in TrackedHand.BONE_CONNECTIONS) {
            val p1 = hand.landmarks[bone.first]
            val p2 = hand.landmarks[bone.second]

            val x1 = ((p1.x * 2f) - 1f) * 0.28f * aspect
            val y1 = (1f - (p1.y * 2f)) * 0.28f
            val z1 = -0.45f - (p1.z * 0.15f)

            val x2 = ((p2.x * 2f) - 1f) * 0.28f * aspect
            val y2 = (1f - (p2.y * 2f)) * 0.28f
            val z2 = -0.45f - (p2.z * 0.15f)

            linesBuffer.put(x1); linesBuffer.put(y1); linesBuffer.put(z1)
            linesBuffer.put(x2); linesBuffer.put(y2); linesBuffer.put(z2)
        }

        // Pointer Laser Ray from Index Fingertip
        val tip = hand.landmarks[TrackedHand.INDEX_FINGER_TIP]
        val tipX = ((tip.x * 2f) - 1f) * 0.28f * aspect
        val tipY = (1f - (tip.y * 2f)) * 0.28f
        val tipZ = -0.45f - (tip.z * 0.15f)

        val rayLen = hand.hoverDistance.coerceAtMost(3.0f)
        val targetX = hand.rayOriginX + hand.rayDirX * rayLen
        val targetY = hand.rayOriginY + hand.rayDirY * rayLen
        val targetZ = hand.rayOriginZ + hand.rayDirZ * rayLen

        linesBuffer.put(tipX); linesBuffer.put(tipY); linesBuffer.put(tipZ)
        linesBuffer.put(targetX); linesBuffer.put(targetY); linesBuffer.put(targetZ)

        linesBuffer.position(0)

        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(uLineMVPLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform4f(uLineColorLoc, 0.39f, 0.82f, 1.0f, 0.65f)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, linesBuffer)

        val lineCount = TrackedHand.BONE_CONNECTIONS.size * 2 + 2
        GLES30.glLineWidth(3.0f)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, lineCount)

        GLES30.glDisableVertexAttribArray(0)
    }

    fun release() {
        if (pointProgram != 0) {
            GLES30.glDeleteProgram(pointProgram)
            pointProgram = 0
        }
        if (lineProgram != 0) {
            GLES30.glDeleteProgram(lineProgram)
            lineProgram = 0
        }
    }
}

