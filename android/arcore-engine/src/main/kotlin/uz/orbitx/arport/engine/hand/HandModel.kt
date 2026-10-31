package uz.orbitx.arport.engine.hand

/**
 * 3D point landmark for hand tracking.
 */
data class HandLandmark(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
)

/**
 * Represents a single tracked hand with 21 skeletal landmarks,
 * 3D pointer ray, and 200ms persistence timeout on tracking loss.
 */
class TrackedHand(
    val handIndex: Int,
    var isRightHand: Boolean = true
) {
    // 21 skeletal landmarks in normalized camera space [0..1]
    val landmarks = Array(21) { HandLandmark() }

    // World AR coordinates (meters)
    val worldLandmarks = Array(21) { HandLandmark() }

    var confidence: Float = 0.0f
    var lastSeenTimestampMs: Long = 0
    var isDetected: Boolean = false

    // 3D Pointer Ray originating from index fingertip (landmark 8)
    var rayOriginX: Float = 0f
    var rayOriginY: Float = 0f
    var rayOriginZ: Float = 0f

    var rayDirX: Float = 0f
    var rayDirY: Float = 0f
    var rayDirZ: Float = -1f

    // Active hover status on spatial window
    var isHoveringWindow: Boolean = false
    var hoveredWindowId: String? = null
    var hoverHitU: Float = 0.5f
    var hoverHitV: Float = 0.5f
    var hoverDistance: Float = 1.5f

    /**
     * Checks if hand should remain visible (TZ 6.1: 200 ms persistence on loss).
     */
    fun isVisible(currentTimestampMs: Long): Boolean {
        if (isDetected) return true
        return (currentTimestampMs - lastSeenTimestampMs) < 200L
    }

    companion object {
        // Skeletal bone connections for MediaPipe 21 landmarks
        val BONE_CONNECTIONS = arrayOf(
            // Palm
            Pair(0, 1), Pair(0, 5), Pair(5, 9), Pair(9, 13), Pair(13, 17), Pair(0, 17),
            // Thumb
            Pair(1, 2), Pair(2, 3), Pair(3, 4),
            // Index finger
            Pair(5, 6), Pair(6, 7), Pair(7, 8),
            // Middle finger
            Pair(9, 10), Pair(10, 11), Pair(11, 12),
            // Ring finger
            Pair(13, 14), Pair(14, 15), Pair(15, 16),
            // Pinky
            Pair(17, 18), Pair(18, 19), Pair(19, 20)
        )

        // Landmark indices
        const val WRIST = 0
        const val THUMB_CMC = 1
        const val THUMB_MCP = 2
        const val THUMB_IP = 3
        const val THUMB_TIP = 4
        const val INDEX_FINGER_MCP = 5
        const val INDEX_FINGER_PIP = 6
        const val INDEX_FINGER_DIP = 7
        const val INDEX_FINGER_TIP = 8
        const val MIDDLE_FINGER_MCP = 9
        const val MIDDLE_FINGER_PIP = 10
        const val MIDDLE_FINGER_DIP = 11
        const val MIDDLE_FINGER_TIP = 12
        const val RING_FINGER_MCP = 13
        const val RING_FINGER_PIP = 14
        const val RING_FINGER_DIP = 15
        const val RING_FINGER_TIP = 16
        const val PINKY_MCP = 17
        const val PINKY_PIP = 18
        const val PINKY_DIP = 19
        const val PINKY_TIP = 20
    }
}

