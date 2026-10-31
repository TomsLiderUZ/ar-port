package uz.orbitx.arport.engine.render

import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import uz.orbitx.arport.engine.apps.HomeCategory
import uz.orbitx.arport.engine.apps.SpatialDock
import uz.orbitx.arport.engine.apps.SpatialDockAction
import uz.orbitx.arport.engine.apps.SpatialHomePanel
import uz.orbitx.arport.engine.gestures.GestureEngine
import uz.orbitx.arport.engine.gestures.GestureEvent
import uz.orbitx.arport.engine.gestures.GestureListener
import uz.orbitx.arport.engine.gestures.GestureType
import uz.orbitx.arport.engine.input.InputInjector
import uz.orbitx.arport.engine.input.KeyAction
import uz.orbitx.arport.engine.input.SpatialKeyboard
import uz.orbitx.arport.engine.registry.AppRegistry
import uz.orbitx.arport.engine.stereo.LensProfile
import uz.orbitx.arport.engine.stereo.StereoRenderer
import uz.orbitx.arport.engine.tracking.TrackingManager
import uz.orbitx.arport.engine.window.WindowManager3D
import uz.orbitx.arport.engine.window.WindowState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Main OpenGL ES 3.0 renderer orchestrating CameraX Passthrough,
 * Liquid Glass 3D spatial windows, Kawase background blur passes,
 * and Stereoscopic barrel distortion post-processing.
 */
class ArEngineRenderer(
    var viewMode: String, // "stereo" or "normal"
    val trackingManager: TrackingManager,
    var ipdMm: Double = 63.0,
    var lensProfileId: String = "cardboard_v2",
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    private val requestRender: () -> Unit
) : GLSurfaceView.Renderer {

    val passthroughRenderer = PassthroughRenderer {
        requestRender()
    }

    val windowManager = WindowManager3D()
    val windowRenderer = WindowRenderer3D()
    val glassRenderer = GlassRenderer()
    val handRenderer = HandRenderer()
    var handTracker: uz.orbitx.arport.engine.hand.HandTracker? = null

    val gestureEngine = GestureEngine()
    val inputInjector = InputInjector()
    val spatialKeyboard = SpatialKeyboard()
    val appRegistry = AppRegistry()
    val spatialDock = SpatialDock()
    val spatialHomePanel = SpatialHomePanel(appRegistry)

    var lastGestureLabel: String = "Kutilmoqda"
    var onHomeDockRequested: (() -> Unit)? = null
    var onVolumeToggleRequested: (() -> Unit)? = null
    var onBrightnessToggleRequested: (() -> Unit)? = null
    var onExitRequested: (() -> Unit)? = null

    init {
        gestureEngine.setListener(object : GestureListener {
            override fun onGestureEvent(event: GestureEvent) {
                handleGestureEvent(event)
            }
        })

        spatialKeyboard.onTextInput = { text ->
            windowManager.allWindows.find { it.appId == "spatial_notes" }?.let { notesWin ->
                windowManager.appendNotesText(notesWin, text)
            }
        }

        spatialKeyboard.onKeyAction = { action ->
            if (action is KeyAction.Backspace) {
                windowManager.allWindows.find { it.appId == "spatial_notes" }?.let { notesWin ->
                    windowManager.backspaceNotesText(notesWin)
                }
            }
        }

        spatialDock.onDockAction = { action ->
            when (action) {
                SpatialDockAction.HOME -> toggleHomePanel()
                SpatialDockAction.RECENTER -> recenter()
                SpatialDockAction.KEYBOARD -> toggleSpatialKeyboard()
                SpatialDockAction.VOLUME -> onVolumeToggleRequested?.invoke()
                SpatialDockAction.BRIGHTNESS -> onBrightnessToggleRequested?.invoke()
                SpatialDockAction.EXIT -> onExitRequested?.invoke()
            }
        }

        spatialHomePanel.onAppSelected = { app ->
            appRegistry.launchApp(app.id, windowManager)
            spatialHomePanel.homeWindow?.let { win ->
                windowManager.minimizeWindow(win.id)
            }
        }
    }

    val stereoRenderer = StereoRenderer(
        ipdMm = ipdMm,
        lensProfile = LensProfile.fromId(lensProfileId)
    )

    private var screenWidth: Int = 1920
    private var screenHeight: Int = 1080

    // Normal mode FBO handles
    private val normalFboIds = IntArray(1)
    private val normalColorTexIds = IntArray(1)
    private val normalDepthRbIds = IntArray(1)

    // Cached matrix buffers for GL thread
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    private var frameCounter = 0
    private var lastFrameTimeNs: Long = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glClearDepthf(1.0f)

        passthroughRenderer.onSurfaceCreated()
        stereoRenderer.onSurfaceCreated()
        glassRenderer.onSurfaceCreated()
        windowRenderer.onSurfaceCreated()
        handRenderer.onSurfaceCreated()

        // Initialize Spatial Dock and Home Panel
        if (spatialDock.dockWindow == null) {
            val dockWin = spatialDock.createDockWindow()
            windowManager.addWindow(dockWin)
        }
        if (spatialHomePanel.homeWindow == null) {
            val homeWin = spatialHomePanel.createHomeWindow()
            windowManager.addWindow(homeWin)
        }

        passthroughRenderer.surfaceTexture?.let { st ->
            onSurfaceTextureReady(st)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenWidth = width
        screenHeight = height

        passthroughRenderer.onSurfaceChanged(width, height)
        stereoRenderer.onSurfaceChanged(width, height)
        glassRenderer.onSurfaceChanged(width, height)

        setupNormalFramebuffers()
    }

    private fun setupNormalFramebuffers() {
        releaseNormalFramebuffers()

        GLES30.glGenFramebuffers(1, normalFboIds, 0)
        GLES30.glGenTextures(1, normalColorTexIds, 0)
        GLES30.glGenRenderbuffers(1, normalDepthRbIds, 0)

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, normalColorTexIds[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
            screenWidth, screenHeight, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, normalDepthRbIds[0])
        GLES30.glRenderbufferStorage(
            GLES30.GL_RENDERBUFFER,
            GLES30.GL_DEPTH_COMPONENT24,
            screenWidth,
            screenHeight
        )

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, normalFboIds[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            normalColorTexIds[0],
            0
        )
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_RENDERBUFFER,
            normalDepthRbIds[0]
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dtSec = if (lastFrameTimeNs > 0) {
            ((nowNs - lastFrameTimeNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
        } else {
            0.016f
        }
        lastFrameTimeNs = nowNs

        frameCounter++
        if (frameCounter % 30 == 0) {
            // Periodically refresh system monitor window with live metrics
            windowManager.allWindows.find { it.appId == "system_monitor" }?.let { win ->
                windowManager.renderSystemMonitorContent(win, getFps(), getLatencyMs())
            }
        }

        val visibleHands = handTracker?.getVisibleHands() ?: emptyList()
        gestureEngine.processHands(visibleHands, windowManager)

        if (viewMode == "stereo") {
            val eyeW = stereoRenderer.eyeWidth
            val eyeH = stereoRenderer.eyeHeight
            val eyeOffsetM = stereoRenderer.getEyeOffsetMeters()

            trackingManager.getProjectionMatrix(projectionMatrix, eyeW, eyeH, fovYDeg = 65f)

            // 1. Left Eye Pass
            stereoRenderer.beginLeftEye()
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            passthroughRenderer.draw(0, 0, eyeW, eyeH)

            // Kawase blur pass on background for Left Eye
            val blurredLeftTex = glassRenderer.performBlur(stereoRenderer.getLeftColorTexId())

            stereoRenderer.beginLeftEye()
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            trackingManager.getViewMatrix(viewMatrix, -eyeOffsetM)
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            windowRenderer.drawWindows(windowManager, viewMatrix, projectionMatrix, vpMatrix, blurredLeftTex, eyeW, eyeH, dtSec)
            handRenderer.drawHands(visibleHands, windowManager, viewMatrix, projectionMatrix)

            // 2. Right Eye Pass
            stereoRenderer.beginRightEye()
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            passthroughRenderer.draw(0, 0, eyeW, eyeH)

            // Kawase blur pass on background for Right Eye
            val blurredRightTex = glassRenderer.performBlur(stereoRenderer.getRightColorTexId())

            stereoRenderer.beginRightEye()
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            trackingManager.getViewMatrix(viewMatrix, eyeOffsetM)
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            windowRenderer.drawWindows(windowManager, viewMatrix, projectionMatrix, vpMatrix, blurredRightTex, eyeW, eyeH, dtSec)
            handRenderer.drawHands(visibleHands, windowManager, viewMatrix, projectionMatrix)

            // 3. Post-processing Barrel Distortion Pass
            stereoRenderer.finishAndDistort()

        } else {
            // Normal (Fullscreen) Mode
            // Render background to normal FBO
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, normalFboIds[0])
            GLES30.glViewport(0, 0, screenWidth, screenHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(false)
            passthroughRenderer.draw(0, 0, screenWidth, screenHeight)

            // Kawase blur pass on background
            val blurredTex = glassRenderer.performBlur(normalColorTexIds[0])

            // Render to Main Screen
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, screenWidth, screenHeight)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            passthroughRenderer.draw(0, 0, screenWidth, screenHeight)

            // 3D Liquid Glass Windows
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthMask(true)
            trackingManager.getProjectionMatrix(projectionMatrix, screenWidth, screenHeight, fovYDeg = 65f)
            trackingManager.getViewMatrix(viewMatrix, 0f)
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            windowRenderer.drawWindows(windowManager, viewMatrix, projectionMatrix, vpMatrix, blurredTex, screenWidth, screenHeight, dtSec)
            handRenderer.drawHands(visibleHands, windowManager, viewMatrix, projectionMatrix)
        }
    }

    private fun handleGestureEvent(event: GestureEvent) {
        lastGestureLabel = when (event.type) {
            GestureType.TAP -> "Bosish (Tap)"
            GestureType.HOLD -> "Ushlab turish (Hold)"
            GestureType.DRAG -> "Sudrash (Drag)"
            GestureType.SCROLL -> "Aylantirish (Scroll)"
            GestureType.TWO_HAND_ZOOM -> "Kattalashtirish (Zoom)"
            GestureType.CORNER_RESIZE -> "O'lcham (Resize)"
            GestureType.DEPTH_MOVE -> "Z-masofa (Depth)"
            GestureType.PALM_UP -> "Kaft ochiq (Home)"
            GestureType.SWIPE_LEFT -> "Surish chapga (Swipe L)"
            GestureType.SWIPE_RIGHT -> "Surish o'ngga (Swipe R)"
            GestureType.FIST -> "Musht (Fist)"
            GestureType.HOVER -> "Nishon (Hover)"
            else -> "Oddiy"
        }

        // 1. Keyboard Tap interaction
        if (event.window?.appId == "spatial_keyboard" && event.type == GestureType.TAP) {
            spatialKeyboard.onKeyHit(event.u, event.v)
            return
        }

        // 2. Dock Tap interaction
        if (event.window?.appId == "spatial_dock" && event.type == GestureType.TAP) {
            spatialDock.onDockHit(event.u, event.v)
            return
        }

        // 3. Home Panel Tap interaction
        if (event.window?.appId == "spatial_home" && event.type == GestureType.TAP) {
            spatialHomePanel.onHomeHit(event.u, event.v)
            return
        }

        // 4. Direct Window Transformations (Drag, Depth, Resize, Controls)
        val handled = inputInjector.handleWindowGesture(event, windowManager)
        if (handled) return

        // 5. System-level Gestures
        when (event.type) {
            GestureType.PALM_UP -> {
                toggleHomePanel()
                onHomeDockRequested?.invoke()
            }
            GestureType.SWIPE_LEFT -> {
                cycleWindowFocus(forward = true)
            }
            GestureType.SWIPE_RIGHT -> {
                cycleWindowFocus(forward = false)
            }
            GestureType.FIST -> {
                event.window?.let { windowManager.closeWindow(it.id) }
            }
            else -> {}
        }
    }

    fun toggleHomePanel() {
        val win = spatialHomePanel.homeWindow ?: return
        if (win.state == WindowState.MINIMIZED) {
            windowManager.restoreWindow(win.id)
            spatialHomePanel.renderHomeContent()
        } else {
            windowManager.minimizeWindow(win.id)
        }
    }

    fun cycleWindowFocus(forward: Boolean) {
        val active = windowManager.activeWindows
        if (active.size <= 1) return
        val currentIndex = active.indexOfFirst { it.isFocused }
        val nextIndex = if (forward) {
            (currentIndex + 1) % active.size
        } else {
            (currentIndex - 1 + active.size) % active.size
        }
        windowManager.setFocus(active[nextIndex].id)
    }

    fun toggleSpatialKeyboard() {
        val existingKb = windowManager.allWindows.find { it.appId == "spatial_keyboard" }
        if (existingKb == null) {
            val kbWin = spatialKeyboard.createKeyboardWindow()
            windowManager.addWindow(kbWin)
        } else {
            if (existingKb.state == WindowState.MINIMIZED) {
                windowManager.restoreWindow(existingKb.id)
            } else {
                windowManager.minimizeWindow(existingKb.id)
            }
        }
    }

    fun updateStereoSettings(newIpdMm: Double, newLensProfileId: String) {
        this.ipdMm = newIpdMm
        this.lensProfileId = newLensProfileId
        stereoRenderer.ipdMm = newIpdMm
        stereoRenderer.lensProfile = LensProfile.fromId(newLensProfileId)
    }

    fun setGlassParameters(isDark: Boolean, reduceTransparency: Boolean, lowSpec: Boolean) {
        windowRenderer.isDarkMode = isDark
        windowRenderer.isReduceTransparency = reduceTransparency
        glassRenderer.isReduceTransparency = reduceTransparency
        glassRenderer.isLowSpecMode = lowSpec
    }

    fun recenter() {
        trackingManager.recenter()
    }

    fun setCameraPreviewSize(width: Int, height: Int) {
        passthroughRenderer.setCameraPreviewSize(width, height)
    }

    fun getFps(): Float = passthroughRenderer.fpsTracker.getFps()

    fun getLatencyMs(): Float = passthroughRenderer.fpsTracker.getLatencyMs()

    private fun releaseNormalFramebuffers() {
        if (normalFboIds[0] != 0) {
            GLES30.glDeleteFramebuffers(1, normalFboIds, 0)
            normalFboIds[0] = 0
        }
        if (normalColorTexIds[0] != 0) {
            GLES30.glDeleteTextures(1, normalColorTexIds, 0)
            normalColorTexIds[0] = 0
        }
        if (normalDepthRbIds[0] != 0) {
            GLES30.glDeleteRenderbuffers(1, normalDepthRbIds, 0)
            normalDepthRbIds[0] = 0
        }
    }

    fun release() {
        releaseNormalFramebuffers()
        passthroughRenderer.release()
        windowRenderer.release()
        handRenderer.release()
        glassRenderer.release()
        stereoRenderer.release()
        windowManager.release()
    }
}
