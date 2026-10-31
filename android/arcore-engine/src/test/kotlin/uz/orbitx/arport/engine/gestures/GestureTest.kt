package uz.orbitx.arport.engine.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.orbitx.arport.engine.hand.TrackedHand
import uz.orbitx.arport.engine.input.InputInjector
import uz.orbitx.arport.engine.input.KeyAction
import uz.orbitx.arport.engine.input.KeyboardLanguage
import uz.orbitx.arport.engine.input.SpatialKeyboard
import uz.orbitx.arport.engine.window.SpatialWindow
import uz.orbitx.arport.engine.window.WindowManager3D
import kotlin.math.abs

class GestureTest {

    private fun createMockHand(handIndex: Int = 0): TrackedHand {
        val hand = TrackedHand(handIndex)
        hand.isDetected = true
        // Set default neutral wrist
        hand.landmarks[TrackedHand.WRIST].apply { x = 0.5f; y = 0.8f; z = -0.5f }
        return hand
    }

    @Test
    fun testPinchDistanceCalculation() {
        val engine = GestureEngine()
        val hand = createMockHand()

        // Thumb at (0.5, 0.5, 0.0), Index at (0.53, 0.54, 0.0) -> distance = 0.05
        hand.landmarks[TrackedHand.THUMB_TIP].apply { x = 0.5f; y = 0.5f; z = 0.0f }
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.53f; y = 0.54f; z = 0.0f }

        val dist = engine.calculatePinchDistance(hand)
        assertEquals(0.05f, dist, 0.001f)
    }

    @Test
    fun testPinchHysteresisThresholds() {
        val hand = createMockHand()
        val wm = WindowManager3D()
        val events = mutableListOf<GestureEvent>()
        val engine = GestureEngine()
        engine.setListener(object : GestureListener {
            override fun onGestureEvent(event: GestureEvent) {
                events.add(event)
            }
        })

        // 1. Initial wide open hand (dist = 0.20)
        hand.landmarks[TrackedHand.THUMB_TIP].apply { x = 0.4f; y = 0.5f; z = 0.0f }
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.6f; y = 0.5f; z = 0.0f }
        engine.processHands(listOf(hand), wm, currentTimestampMs = 1000L)

        // 2. Pinch closes below threshold (< 0.052)
        hand.landmarks[TrackedHand.THUMB_TIP].apply { x = 0.5f; y = 0.5f; z = 0.0f }
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.54f; y = 0.5f; z = 0.0f } // dist = 0.04
        engine.processHands(listOf(hand), wm, currentTimestampMs = 1050L)

        // 3. Pinch separates slightly to 0.070 (between ENTER 0.052 and EXIT 0.078)
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.57f; y = 0.5f; z = 0.0f } // dist = 0.07
        engine.processHands(listOf(hand), wm, currentTimestampMs = 1100L)

        // 4. Pinch opens wide to 0.090 (> EXIT 0.078) -> should release and emit TAP
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.59f; y = 0.5f; z = 0.0f } // dist = 0.09
        engine.processHands(listOf(hand), wm, currentTimestampMs = 1200L) // duration = 150ms (< 350ms)

        val tapEvent = events.firstOrNull { it.type == GestureType.TAP }
        assertNotNull("Should have emitted a TAP gesture on release within 350ms", tapEvent)
    }

    @Test
    fun testHoldGestureDetection() {
        val hand = createMockHand()
        val wm = WindowManager3D()
        val events = mutableListOf<GestureEvent>()
        val engine = GestureEngine()
        engine.setListener(object : GestureListener {
            override fun onGestureEvent(event: GestureEvent) {
                events.add(event)
            }
        })

        // Pinch closes
        hand.landmarks[TrackedHand.THUMB_TIP].apply { x = 0.5f; y = 0.5f; z = 0.0f }
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.54f; y = 0.5f; z = 0.0f } // dist = 0.04
        engine.processHands(listOf(hand), wm, currentTimestampMs = 1000L)

        // Kept held for 550ms (> HOLD 500ms) without moving
        engine.processHands(listOf(hand), wm, currentTimestampMs = 1550L)

        val holdEvent = events.firstOrNull { it.type == GestureType.HOLD }
        assertNotNull("Should emit HOLD gesture after 500ms continuous pinch", holdEvent)
    }

    @Test
    fun testPalmUpDetection() {
        val engine = GestureEngine()
        val hand = createMockHand()

        // Wrist at bottom (y = 0.8)
        hand.landmarks[TrackedHand.WRIST].apply { x = 0.5f; y = 0.8f; z = 0.0f }
        // MCP knuckles at middle (y = 0.6)
        hand.landmarks[TrackedHand.INDEX_FINGER_MCP].apply { x = 0.45f; y = 0.6f; z = 0.0f }
        hand.landmarks[TrackedHand.MIDDLE_FINGER_MCP].apply { x = 0.50f; y = 0.58f; z = 0.0f }
        hand.landmarks[TrackedHand.RING_FINGER_MCP].apply { x = 0.55f; y = 0.6f; z = 0.0f }
        hand.landmarks[TrackedHand.PINKY_MCP].apply { x = 0.60f; y = 0.62f; z = 0.0f }

        // Extended fingertips pointing forward/up (y = 0.3, further from wrist than MCP)
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.45f; y = 0.3f; z = 0.0f }
        hand.landmarks[TrackedHand.MIDDLE_FINGER_TIP].apply { x = 0.50f; y = 0.28f; z = 0.0f }
        hand.landmarks[TrackedHand.RING_FINGER_TIP].apply { x = 0.55f; y = 0.3f; z = 0.0f }
        hand.landmarks[TrackedHand.PINKY_TIP].apply { x = 0.60f; y = 0.35f; z = 0.0f }

        assertTrue("Extended fingers with wrist at bottom should register as palm up", engine.isPalmUp(hand))

        // Curl fingers down to test negative
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.45f; y = 0.7f; z = 0.0f }
        assertFalse("Curled fingers should not register as palm up", engine.isPalmUp(hand))
    }

    @Test
    fun testFistDetection() {
        val engine = GestureEngine()
        val hand = createMockHand()

        // Wrist at bottom (y = 0.8)
        hand.landmarks[TrackedHand.WRIST].apply { x = 0.5f; y = 0.8f; z = 0.0f }

        // MCPs at middle (y = 0.55)
        hand.landmarks[TrackedHand.INDEX_FINGER_MCP].apply { x = 0.45f; y = 0.55f; z = 0.0f }
        hand.landmarks[TrackedHand.MIDDLE_FINGER_MCP].apply { x = 0.50f; y = 0.53f; z = 0.0f }
        hand.landmarks[TrackedHand.RING_FINGER_MCP].apply { x = 0.55f; y = 0.55f; z = 0.0f }
        hand.landmarks[TrackedHand.PINKY_MCP].apply { x = 0.60f; y = 0.57f; z = 0.0f }

        // Tips curled tightly back towards wrist (y = 0.65, very close to MCP/wrist)
        hand.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.45f; y = 0.62f; z = 0.0f }
        hand.landmarks[TrackedHand.MIDDLE_FINGER_TIP].apply { x = 0.50f; y = 0.60f; z = 0.0f }
        hand.landmarks[TrackedHand.RING_FINGER_TIP].apply { x = 0.55f; y = 0.62f; z = 0.0f }
        hand.landmarks[TrackedHand.PINKY_TIP].apply { x = 0.60f; y = 0.64f; z = 0.0f }

        assertTrue("Curled fingers should be detected as fist", engine.isFist(hand))
    }

    @Test
    fun testTwoHandDistanceCalculation() {
        val engine = GestureEngine()
        val h0 = createMockHand(0)
        val h1 = createMockHand(1)

        h0.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.3f; y = 0.5f; z = 0.0f }
        h1.landmarks[TrackedHand.INDEX_FINGER_TIP].apply { x = 0.7f; y = 0.5f; z = 0.0f }

        val dist = engine.calculateTwoHandDistance(h0, h1)
        assertEquals(0.4f, dist, 0.001f)
    }

    @Test
    fun testInputInjectorUvToPixelMapping() {
        val (px1, py1) = InputInjector.uvToPixelCoordinates(0.0f, 1.0f, 1280, 720)
        // Top-left in UV (u=0, v=1) corresponds to (0, 0) in Android pixel coordinates
        assertEquals(0.0f, px1, 0.01f)
        assertEquals(0.0f, py1, 0.01f)

        val (px2, py2) = InputInjector.uvToPixelCoordinates(1.0f, 0.0f, 1280, 720)
        // Bottom-right in UV (u=1, v=0) corresponds to (1280, 720)
        assertEquals(1280.0f, px2, 0.01f)
        assertEquals(720.0f, py2, 0.01f)

        val (pxCenter, pyCenter) = InputInjector.uvToPixelCoordinates(0.5f, 0.5f, 1280, 720)
        assertEquals(640.0f, pxCenter, 0.01f)
        assertEquals(360.0f, pyCenter, 0.01f)
    }

    @Test
    fun testInputInjectorWindowTransformations() {
        val injector = InputInjector()
        val wm = WindowManager3D()
        val win = wm.activeWindows.first()

        val startX = win.posX
        val startY = win.posY
        val startZ = win.posZ

        // 1. Drag
        val dragEvent = GestureEvent(
            type = GestureType.DRAG,
            handIndex = 0,
            isRightHand = true,
            window = win,
            deltaX = 0.15f,
            deltaY = -0.10f
        )
        injector.handleWindowGesture(dragEvent, wm)
        assertEquals(startX + 0.15f, win.posX, 0.001f)
        assertEquals(startY - 0.10f, win.posY, 0.001f)

        // 2. Depth Move
        val depthEvent = GestureEvent(
            type = GestureType.DEPTH_MOVE,
            handIndex = 0,
            isRightHand = true,
            window = win,
            deltaZ = -0.30f
        )
        injector.handleWindowGesture(depthEvent, wm)
        assertEquals(startZ - 0.30f, win.posZ, 0.001f)

        // 3. Two-Hand Zoom
        val startW = win.widthMeters
        val zoomEvent = GestureEvent(
            type = GestureType.TWO_HAND_ZOOM,
            handIndex = 0,
            isRightHand = true,
            window = win,
            scaleFactor = 1.25f
        )
        injector.handleWindowGesture(zoomEvent, wm)
        assertEquals(startW * 1.25f, win.widthMeters, 0.01f)
    }

    @Test
    fun testSpatialKeyboardLayoutAndTyping() {
        val keyboard = SpatialKeyboard(language = KeyboardLanguage.UZ)

        // 1. Check layout keys
        val layout = keyboard.getCurrentLayout()
        assertTrue("Keyboard must have at least 4 rows", layout.size >= 4)

        // First row should contain Uzbek characters
        val row0Labels = layout[0].map { it.label.uppercase() }
        assertTrue("Uzbek keyboard must contain G'", row0Labels.contains("G'"))
        assertTrue("Uzbek keyboard must contain O'", row0Labels.contains("O'"))

        // 2. Hit test key
        // Top-left corner (u=0.04, v=0.88 -> row 0 first key 'q')
        val key = keyboard.hitKey(0.04f, 0.88f)
        assertNotNull("Should hit key at top-left", key)

        // 3. Text input callback
        var typedText = ""
        keyboard.onTextInput = { str -> typedText += str }

        keyboard.onKeyHit(0.04f, 0.88f)
        assertTrue("Key hit should produce text output", typedText.isNotEmpty())

        // 4. Shift toggle
        assertFalse(keyboard.isShiftActive)
        // Hit Shift key (row 2, first key)
        val shiftAction = keyboard.onKeyHit(0.05f, 0.35f)
        assertEquals(KeyAction.ShiftToggle, shiftAction)
        assertTrue(keyboard.isShiftActive)

        // 5. Language cycle
        assertEquals(KeyboardLanguage.UZ, keyboard.language)
        // Bottom row, second key (lang switch)
        keyboard.onKeyHit(0.18f, 0.12f)
        assertEquals(KeyboardLanguage.EN, keyboard.language)
    }
}

