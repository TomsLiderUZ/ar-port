package uz.orbitx.arport.engine.gestures

import uz.orbitx.arport.engine.hand.HandLandmark
import uz.orbitx.arport.engine.hand.TrackedHand
import uz.orbitx.arport.engine.window.SpatialWindow
import uz.orbitx.arport.engine.window.WindowManager3D
import kotlin.math.abs
import kotlin.math.sqrt

enum class GestureType {
    NONE,
    HOVER,
    TAP,
    HOLD,
    DRAG,
    SCROLL,
    TWO_HAND_ZOOM,
    CORNER_RESIZE,
    DEPTH_MOVE,
    PALM_UP,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    FIST
}

data class GestureEvent(
    val type: GestureType,
    val handIndex: Int,
    val isRightHand: Boolean,
    val window: SpatialWindow? = null,
    val component: SpatialWindow.WindowComponent? = null,
    val u: Float = 0.5f,
    val v: Float = 0.5f,
    val deltaX: Float = 0f,
    val deltaY: Float = 0f,
    val deltaZ: Float = 0f,
    val scaleFactor: Float = 1.0f,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val timestampMs: Long = System.currentTimeMillis()
)

interface GestureListener {
    fun onGestureEvent(event: GestureEvent)
}

/**
 * High-performance 3D Spatial Gesture Engine.
 * Detects Pinch, Tap, Hold, Drag, Scroll (with inertia), Two-Hand Zoom,
 * Corner Resize, Depth Move (Z), Palm-Up (Home dock), Fist (Close), and Swipe.
 * Employs hysteresis thresholds to prevent false positives and jitter.
 */
class GestureEngine(
    private var listener: GestureListener? = null
) {
    var pinchEnterThreshold: Float = PINCH_ENTER_THRESHOLD
    var pinchExitThreshold: Float = PINCH_EXIT_THRESHOLD
    companion object {
        const val PINCH_ENTER_THRESHOLD = 0.052f  // ~2.5cm equivalent
        const val PINCH_EXIT_THRESHOLD = 0.078f   // Hysteresis boundary
        const val TAP_MAX_DURATION_MS = 350L
        const val HOLD_MIN_DURATION_MS = 500L
        const val DRAG_MIN_MOVEMENT = 0.035f
        const val PALM_UP_HOLD_DURATION_MS = 400L
        const val FIST_HOLD_DURATION_MS = 500L
        const val SWIPE_MIN_DISTANCE = 0.16f
        const val SWIPE_MAX_DURATION_MS = 280L
    }

    class HandTrackingState(val handIndex: Int) {
        var isPinching: Boolean = false
        var pinchStartTimeMs: Long = 0L
        var pinchStartU: Float = 0.5f
        var pinchStartV: Float = 0.5f
        var lastPinchU: Float = 0.5f
        var lastPinchV: Float = 0.5f

        var pinchStartOriginX: Float = 0f
        var pinchStartOriginY: Float = 0f
        var pinchStartOriginZ: Float = 0f
        var lastOriginX: Float = 0f
        var lastOriginY: Float = 0f
        var lastOriginZ: Float = 0f

        var isDragging: Boolean = false
        var isHolding: Boolean = false

        var targetWindow: SpatialWindow? = null
        var targetComponent: SpatialWindow.WindowComponent? = null

        var palmUpStartTimeMs: Long = 0L
        var palmUpTriggered: Boolean = false

        var fistStartTimeMs: Long = 0L
        var fistTriggered: Boolean = false

        var swipeStartX: Float = 0f
        var swipeStartTimeMs: Long = 0L

        // Inertial scrolling
        var scrollVelocityX: Float = 0f
        var scrollVelocityY: Float = 0f

        fun resetPinch() {
            isPinching = false
            isDragging = false
            isHolding = false
            targetWindow = null
            targetComponent = null
        }
    }

    private val handStates = arrayOf(HandTrackingState(0), HandTrackingState(1))
    private var twoHandInitialDistance: Float = 0f
    private var isTwoHandZooming: Boolean = false
    private var twoHandTargetWindow: SpatialWindow? = null

    fun setListener(listener: GestureListener?) {
        this.listener = listener
    }

    /**
     * Evaluates Euclidean distance between thumb tip (4) and index tip (8).
     */
    fun calculatePinchDistance(hand: TrackedHand): Float {
        val thumb = hand.landmarks[TrackedHand.THUMB_TIP]
        val index = hand.landmarks[TrackedHand.INDEX_FINGER_TIP]
        val dx = thumb.x - index.x
        val dy = thumb.y - index.y
        val dz = thumb.z - index.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Checks if all fingers are extended and palm faces up.
     */
    fun isPalmUp(hand: TrackedHand): Boolean {
        val wrist = hand.landmarks[TrackedHand.WRIST]
        val indexMcp = hand.landmarks[TrackedHand.INDEX_FINGER_MCP]
        val indexTip = hand.landmarks[TrackedHand.INDEX_FINGER_TIP]
        val middleMcp = hand.landmarks[TrackedHand.MIDDLE_FINGER_MCP]
        val middleTip = hand.landmarks[TrackedHand.MIDDLE_FINGER_TIP]
        val ringMcp = hand.landmarks[TrackedHand.RING_FINGER_MCP]
        val ringTip = hand.landmarks[TrackedHand.RING_FINGER_TIP]
        val pinkyMcp = hand.landmarks[TrackedHand.PINKY_MCP]
        val pinkyTip = hand.landmarks[TrackedHand.PINKY_TIP]

        // All 4 fingers extended away from wrist
        val indexExt = distSq(wrist, indexTip) > distSq(wrist, indexMcp) * 1.25f
        val midExt = distSq(wrist, middleTip) > distSq(wrist, middleMcp) * 1.25f
        val ringExt = distSq(wrist, ringTip) > distSq(wrist, ringMcp) * 1.25f
        val pinkyExt = distSq(wrist, pinkyTip) > distSq(wrist, pinkyMcp) * 1.25f

        if (!indexExt || !midExt || !ringExt || !pinkyExt) return false

        // In camera coordinate system (Y downward), wrist is at bottom or palm faces user
        val wristBelowFingers = wrist.y >= middleTip.y - 0.05f
        return wristBelowFingers
    }

    /**
     * Checks if all fingers are curled tightly into a fist.
     */
    fun isFist(hand: TrackedHand): Boolean {
        val wrist = hand.landmarks[TrackedHand.WRIST]
        val indexMcp = hand.landmarks[TrackedHand.INDEX_FINGER_MCP]
        val indexTip = hand.landmarks[TrackedHand.INDEX_FINGER_TIP]
        val middleMcp = hand.landmarks[TrackedHand.MIDDLE_FINGER_MCP]
        val middleTip = hand.landmarks[TrackedHand.MIDDLE_FINGER_TIP]
        val ringMcp = hand.landmarks[TrackedHand.RING_FINGER_MCP]
        val ringTip = hand.landmarks[TrackedHand.RING_FINGER_TIP]
        val pinkyMcp = hand.landmarks[TrackedHand.PINKY_MCP]
        val pinkyTip = hand.landmarks[TrackedHand.PINKY_TIP]

        val indexCurled = distSq(wrist, indexTip) < distSq(wrist, indexMcp) * 1.15f
        val midCurled = distSq(wrist, middleTip) < distSq(wrist, middleMcp) * 1.15f
        val ringCurled = distSq(wrist, ringTip) < distSq(wrist, ringMcp) * 1.15f
        val pinkyCurled = distSq(wrist, pinkyTip) < distSq(wrist, pinkyMcp) * 1.15f

        return indexCurled && midCurled && ringCurled && pinkyCurled
    }

    /**
     * Distance between two hands' index fingertips.
     */
    fun calculateTwoHandDistance(hand1: TrackedHand, hand2: TrackedHand): Float {
        val p1 = hand1.landmarks[TrackedHand.INDEX_FINGER_TIP]
        val p2 = hand2.landmarks[TrackedHand.INDEX_FINGER_TIP]
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        val dz = p1.z - p2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Processes tracked hands and window manager state each frame.
     */
    fun processHands(
        hands: List<TrackedHand>,
        windowManager: WindowManager3D,
        currentTimestampMs: Long = System.currentTimeMillis()
    ) {
        // 1. Check Two-Hand Pinch Zoom/Resize
        if (hands.size >= 2) {
            val h0 = hands[0]
            val h1 = hands[1]
            val s0 = handStates[0]
            val s1 = handStates[1]

            val d0 = calculatePinchDistance(h0)
            val d1 = calculatePinchDistance(h1)

            val p0 = if (s0.isPinching) d0 < pinchExitThreshold else d0 < pinchEnterThreshold
            val p1 = if (s1.isPinching) d1 < pinchExitThreshold else d1 < pinchEnterThreshold

            if (p0 && p1) {
                val currentTwoHandDist = calculateTwoHandDistance(h0, h1)
                if (!isTwoHandZooming) {
                    isTwoHandZooming = true
                    twoHandInitialDistance = currentTwoHandDist.coerceAtLeast(0.05f)
                    twoHandTargetWindow = windowManager.activeWindows.firstOrNull { it.isFocused }
                } else if (twoHandInitialDistance > 0.01f) {
                    val scaleFactor = currentTwoHandDist / twoHandInitialDistance
                    twoHandTargetWindow?.let { win ->
                        listener?.onGestureEvent(
                            GestureEvent(
                                type = GestureType.TWO_HAND_ZOOM,
                                handIndex = 0,
                                isRightHand = true,
                                window = win,
                                scaleFactor = scaleFactor,
                                timestampMs = currentTimestampMs
                            )
                        )
                    }
                }
                return // Two-hand gesture takes precedence
            } else {
                isTwoHandZooming = false
                twoHandTargetWindow = null
            }
        } else {
            isTwoHandZooming = false
            twoHandTargetWindow = null
        }

        // 2. Process Individual Hands
        for (hand in hands) {
            if (hand.handIndex !in handStates.indices) continue
            val state = handStates[hand.handIndex]

            // A. Palm-Up (Home dock) Detection
            val isCurrentlyPalmUp = isPalmUp(hand)
            if (isCurrentlyPalmUp) {
                if (state.palmUpStartTimeMs == 0L) {
                    state.palmUpStartTimeMs = currentTimestampMs
                    state.palmUpTriggered = false
                } else if (!state.palmUpTriggered &&
                    (currentTimestampMs - state.palmUpStartTimeMs) >= PALM_UP_HOLD_DURATION_MS
                ) {
                    state.palmUpTriggered = true
                    listener?.onGestureEvent(
                        GestureEvent(
                            type = GestureType.PALM_UP,
                            handIndex = hand.handIndex,
                            isRightHand = hand.isRightHand,
                            timestampMs = currentTimestampMs
                        )
                    )
                }
            } else {
                state.palmUpStartTimeMs = 0L
                state.palmUpTriggered = false
            }

            // B. Fist (Close window confirmation) Detection
            val isCurrentlyFist = isFist(hand)
            if (isCurrentlyFist) {
                if (state.fistStartTimeMs == 0L) {
                    state.fistStartTimeMs = currentTimestampMs
                    state.fistTriggered = false
                } else if (!state.fistTriggered &&
                    (currentTimestampMs - state.fistStartTimeMs) >= FIST_HOLD_DURATION_MS
                ) {
                    state.fistTriggered = true
                    val focusedWin = windowManager.activeWindows.firstOrNull { it.isFocused }
                    listener?.onGestureEvent(
                        GestureEvent(
                            type = GestureType.FIST,
                            handIndex = hand.handIndex,
                            isRightHand = hand.isRightHand,
                            window = focusedWin,
                            timestampMs = currentTimestampMs
                        )
                    )
                }
            } else {
                state.fistStartTimeMs = 0L
                state.fistTriggered = false
            }

            // C. Swipe Detection (Horizontal fast wrist movement)
            val wrist = hand.landmarks[TrackedHand.WRIST]
            if (state.swipeStartTimeMs == 0L) {
                state.swipeStartX = wrist.x
                state.swipeStartTimeMs = currentTimestampMs
            } else {
                val dt = currentTimestampMs - state.swipeStartTimeMs
                val dx = wrist.x - state.swipeStartX
                if (dt in 50L..SWIPE_MAX_DURATION_MS && abs(dx) > SWIPE_MIN_DISTANCE) {
                    val swipeType = if (dx > 0) GestureType.SWIPE_RIGHT else GestureType.SWIPE_LEFT
                    listener?.onGestureEvent(
                        GestureEvent(
                            type = swipeType,
                            handIndex = hand.handIndex,
                            isRightHand = hand.isRightHand,
                            timestampMs = currentTimestampMs
                        )
                    )
                    state.swipeStartX = wrist.x
                    state.swipeStartTimeMs = currentTimestampMs
                } else if (dt > SWIPE_MAX_DURATION_MS) {
                    state.swipeStartX = wrist.x
                    state.swipeStartTimeMs = currentTimestampMs
                }
            }

            // D. Pinch, Tap, Hold, Drag, Scroll Detection
            val pinchDist = calculatePinchDistance(hand)
            val isPinchingNow = if (state.isPinching) {
                pinchDist < pinchExitThreshold
            } else {
                pinchDist < pinchEnterThreshold
            }

            // Hit test with current hand ray
            val rayHit = windowManager.raycast(
                hand.rayOriginX, hand.rayOriginY, hand.rayOriginZ,
                hand.rayDirX, hand.rayDirY, hand.rayDirZ
            )

            if (isPinchingNow) {
                if (!state.isPinching) {
                    // Pinch Started (ACTION_DOWN candidate)
                    state.isPinching = true
                    state.pinchStartTimeMs = currentTimestampMs
                    state.isDragging = false
                    state.isHolding = false

                    state.targetWindow = rayHit?.first
                    state.targetComponent = rayHit?.second?.component

                    val u = rayHit?.second?.u ?: 0.5f
                    val v = rayHit?.second?.v ?: 0.5f
                    state.pinchStartU = u
                    state.pinchStartV = v
                    state.lastPinchU = u
                    state.lastPinchV = v

                    state.pinchStartOriginX = hand.rayOriginX
                    state.pinchStartOriginY = hand.rayOriginY
                    state.pinchStartOriginZ = hand.rayOriginZ
                    state.lastOriginX = hand.rayOriginX
                    state.lastOriginY = hand.rayOriginY
                    state.lastOriginZ = hand.rayOriginZ
                } else {
                    // Pinch Continuing: check Hold or Drag
                    val duration = currentTimestampMs - state.pinchStartTimeMs
                    val currentU = rayHit?.second?.u ?: state.lastPinchU
                    val currentV = rayHit?.second?.v ?: state.lastPinchV

                    val du = currentU - state.pinchStartU
                    val dv = currentV - state.pinchStartV
                    val distMoved = sqrt(du * du + dv * dv)

                    val dOriginX = hand.rayOriginX - state.lastOriginX
                    val dOriginY = hand.rayOriginY - state.lastOriginY
                    val dOriginZ = hand.rayOriginZ - state.lastOriginZ

                    if (!state.isDragging && distMoved > DRAG_MIN_MOVEMENT) {
                        state.isDragging = true
                    }

                    if (state.isDragging) {
                        val win = state.targetWindow
                        when (state.targetComponent) {
                            SpatialWindow.WindowComponent.HEADER_TITLE,
                            SpatialWindow.WindowComponent.BOTTOM_DRAG_HANDLE -> {
                                // Drag or Depth move window
                                val isZMovement = abs(dOriginZ) > (abs(dOriginX) + abs(dOriginY)) * 1.5f && abs(dOriginZ) > 0.005f
                                val gesture = if (isZMovement) GestureType.DEPTH_MOVE else GestureType.DRAG
                                listener?.onGestureEvent(
                                    GestureEvent(
                                        type = gesture,
                                        handIndex = hand.handIndex,
                                        isRightHand = hand.isRightHand,
                                        window = win,
                                        component = state.targetComponent,
                                        deltaX = dOriginX * 1.5f,
                                        deltaY = dOriginY * 1.5f,
                                        deltaZ = dOriginZ * 2.0f,
                                        timestampMs = currentTimestampMs
                                    )
                                )
                            }
                            SpatialWindow.WindowComponent.RESIZE_HANDLE -> {
                                listener?.onGestureEvent(
                                    GestureEvent(
                                        type = GestureType.CORNER_RESIZE,
                                        handIndex = hand.handIndex,
                                        isRightHand = hand.isRightHand,
                                        window = win,
                                        component = state.targetComponent,
                                        deltaX = (currentU - state.lastPinchU) * (win?.widthMeters ?: 1f),
                                        deltaY = (currentV - state.lastPinchV) * (win?.heightMeters ?: 0.56f),
                                        timestampMs = currentTimestampMs
                                    )
                                )
                            }
                            SpatialWindow.WindowComponent.CONTENT_AREA -> {
                                // Scroll inside content
                                val deltaU = currentU - state.lastPinchU
                                val deltaV = currentV - state.lastPinchV
                                state.scrollVelocityX = deltaU * 60f
                                state.scrollVelocityY = deltaV * 60f

                                listener?.onGestureEvent(
                                    GestureEvent(
                                        type = GestureType.SCROLL,
                                        handIndex = hand.handIndex,
                                        isRightHand = hand.isRightHand,
                                        window = win,
                                        component = state.targetComponent,
                                        u = currentU,
                                        v = currentV,
                                        deltaX = deltaU,
                                        deltaY = deltaV,
                                        velocityX = state.scrollVelocityX,
                                        velocityY = state.scrollVelocityY,
                                        timestampMs = currentTimestampMs
                                    )
                                )
                            }
                            else -> {
                                // Default drag
                                listener?.onGestureEvent(
                                    GestureEvent(
                                        type = GestureType.DRAG,
                                        handIndex = hand.handIndex,
                                        isRightHand = hand.isRightHand,
                                        window = win,
                                        component = state.targetComponent,
                                        deltaX = dOriginX * 1.5f,
                                        deltaY = dOriginY * 1.5f,
                                        deltaZ = dOriginZ * 2.0f,
                                        timestampMs = currentTimestampMs
                                    )
                                )
                            }
                        }
                    } else if (!state.isHolding && duration >= HOLD_MIN_DURATION_MS) {
                        // Hold event (Long press)
                        state.isHolding = true
                        listener?.onGestureEvent(
                            GestureEvent(
                                type = GestureType.HOLD,
                                handIndex = hand.handIndex,
                                isRightHand = hand.isRightHand,
                                window = state.targetWindow,
                                component = state.targetComponent,
                                u = state.pinchStartU,
                                v = state.pinchStartV,
                                timestampMs = currentTimestampMs
                            )
                        )
                    }

                    state.lastPinchU = currentU
                    state.lastPinchV = currentV
                    state.lastOriginX = hand.rayOriginX
                    state.lastOriginY = hand.rayOriginY
                    state.lastOriginZ = hand.rayOriginZ
                }
            } else {
                if (state.isPinching) {
                    // Pinch Released
                    val pinchDuration = currentTimestampMs - state.pinchStartTimeMs
                    if (!state.isDragging && !state.isHolding && pinchDuration <= TAP_MAX_DURATION_MS) {
                        // Confirmed Tap event
                        listener?.onGestureEvent(
                            GestureEvent(
                                type = GestureType.TAP,
                                handIndex = hand.handIndex,
                                isRightHand = hand.isRightHand,
                                window = state.targetWindow,
                                component = state.targetComponent,
                                u = state.pinchStartU,
                                v = state.pinchStartV,
                                timestampMs = currentTimestampMs
                            )
                        )
                    }
                    state.resetPinch()
                } else if (rayHit != null) {
                    // Hovering
                    listener?.onGestureEvent(
                        GestureEvent(
                            type = GestureType.HOVER,
                            handIndex = hand.handIndex,
                            isRightHand = hand.isRightHand,
                            window = rayHit.first,
                            component = rayHit.second.component,
                            u = rayHit.second.u,
                            v = rayHit.second.v,
                            timestampMs = currentTimestampMs
                        )
                    )
                }

                // Process scroll inertia if previously scrolling
                if (abs(state.scrollVelocityX) > 0.001f || abs(state.scrollVelocityY) > 0.001f) {
                    state.scrollVelocityX *= 0.92f
                    state.scrollVelocityY *= 0.92f
                    if (abs(state.scrollVelocityX) < 0.001f) state.scrollVelocityX = 0f
                    if (abs(state.scrollVelocityY) < 0.001f) state.scrollVelocityY = 0f
                }
            }
        }
    }

    private fun distSq(p1: HandLandmark, p2: HandLandmark): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        val dz = p1.z - p2.z
        return dx * dx + dy * dy + dz * dz
    }
}

