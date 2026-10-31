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
    val context: android.content.Context? = null,
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
    val appRegistry = AppRegistry(context)
    val spatialDock = SpatialDock()
    val spatialHomePanel = SpatialHomePanel(appRegistry)
    val adaptiveQualityController = AdaptiveQualityController(glassRenderer, windowManager, passthroughRenderer.fpsTracker)

    var lastGestureLabel: String = "Kutilmoqda"
    var onHomeDockRequested: (() -> Unit)? = null
    var onVolumeToggleRequested: (() -> Unit)? = null
    var onBrightnessToggleRequested: (() -> Unit)? = null
    var onExitRequested: (() -> Unit)? = null
    var onPerformanceWarning: ((String) -> Unit)? = null

    var currentSettings = uz.orbitx.arport.engine.settings.ArSettings()

    init {
        context?.let {
            uz.orbitx.arport.engine.session.SessionStore.getInstance().init(it)
        }
        val settingsStore = uz.orbitx.arport.engine.settings.SettingsStore.getInstance(context)
        settingsStore.addListener { updated ->
            applySettings(updated)
        }
        adaptiveQualityController.onPerformanceWarning = { warning ->
            onPerformanceWarning?.invoke(warning)
        }
        gestureEngine.setListener(object : GestureListener {
            override fun onGestureEvent(event: GestureEvent) {
                handleGestureEvent(event)
            }
        })

        spatialKeyboard.onTextInput = { text ->
            windowManager.allWindows.find { it.appId == "spatial_notes" }?.let { notesWin ->
                windowManager.appendNotesText(notesWin, text)
            }
            for ((_, app) in appRegistry.activeNativeApps) {
                if (app is uz.orbitx.arport.engine.apps.NotesApp) {
                    app.appendInputText(text)
                }
            }
        }

        spatialKeyboard.onKeyAction = { action ->
            if (action is KeyAction.Backspace) {
                windowManager.allWindows.find { it.appId == "spatial_notes" }?.let { notesWin ->
                    windowManager.backspaceNotesText(notesWin)
                }
                for ((_, app) in appRegistry.activeNativeApps) {
                    if (app is uz.orbitx.arport.engine.apps.NotesApp) {
                        app.backspaceInputText()
                    }
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
        try {
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
        } catch (e: Throwable) {
            android.util.Log.e("ArEngineRenderer", "onSurfaceCreated GL xatoligi: ${e.message}", e)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        try {
            screenWidth = width
            screenHeight = height

            passthroughRenderer.onSurfaceChanged(width, height)
            stereoRenderer.onSurfaceChanged(width, height)
            glassRenderer.onSurfaceChanged(width, height)

            setupNormalFramebuffers()
        } catch (e: Throwable) {
            android.util.Log.e("ArEngineRenderer", "onSurfaceChanged GL xatoligi: ${e.message}", e)
        }
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
        try {
            val nowNs = System.nanoTime()
            val dtSec = if (lastFrameTimeNs > 0) {
                ((nowNs - lastFrameTimeNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
            } else {
                0.016f
            }
            lastFrameTimeNs = nowNs

            frameCounter++
            // Live Adaptive Quality Optimization Loop
            adaptiveQualityController.onFrame(getFps())

            if (frameCounter % 30 == 0) {
                // Periodically refresh system monitor window with live metrics
                windowManager.allWindows.find { it.appId == "system_monitor" }?.let { win ->
                    windowManager.renderSystemMonitorContent(win, getFps(), getLatencyMs())
                }
            }
            if (frameCounter % 6 == 0) {
                for ((_, app) in appRegistry.activeNativeApps) {
                    if (app is uz.orbitx.arport.engine.apps.ClockApp) {
                        app.tick()
                    }
                }
            }
            for ((_, app) in appRegistry.activeNativeApps) {
                app.onUpdate(dtSec)
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

                // High-performance blur: compute once from camera feed and share between both eyes for smooth 60 FPS
                val sharedBlurredBackdropTex = glassRenderer.performBlur(stereoRenderer.getLeftColorTexId())

                // Resume Left Eye FBO without wiping camera feed, resetting depth for 3D windows
                stereoRenderer.resumeLeftEye()
                GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                trackingManager.getViewMatrix(viewMatrix, -eyeOffsetM)
                Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
                windowRenderer.drawWindows(windowManager, viewMatrix, projectionMatrix, vpMatrix, sharedBlurredBackdropTex, eyeW, eyeH, dtSec)
                handRenderer.drawHands(visibleHands, windowManager, viewMatrix, projectionMatrix)

                // 2. Right Eye Pass
                stereoRenderer.beginRightEye()
                GLES30.glDisable(GLES30.GL_DEPTH_TEST)
                GLES30.glDepthMask(false)
                passthroughRenderer.draw(0, 0, eyeW, eyeH)

                // Resume Right Eye FBO without wiping camera feed, reusing shared blurred backdrop
                stereoRenderer.resumeRightEye()
                GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                trackingManager.getViewMatrix(viewMatrix, eyeOffsetM)
                Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
                windowRenderer.drawWindows(windowManager, viewMatrix, projectionMatrix, vpMatrix, sharedBlurredBackdropTex, eyeW, eyeH, dtSec)
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
        } catch (e: Throwable) {
            android.util.Log.e("ArEngineRenderer", "onDrawFrame render xatoligi: ${e.message}", e)
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

        // 4. WebAppSurface interaction (Content area click/scroll/gestures)
        val webSurface = appRegistry.activeWebSurfaces[event.window?.id]
        if (webSurface != null && event.component == uz.orbitx.arport.engine.window.SpatialWindow.WindowComponent.CONTENT_AREA) {
            when (event.type) {
                GestureType.TAP -> {
                    webSurface.dispatchTouchEvent(event.u, event.v, android.view.MotionEvent.ACTION_DOWN)
                    webSurface.dispatchTouchEvent(event.u, event.v, android.view.MotionEvent.ACTION_UP)
                    webSurface.sendGameGesture("TAP")
                }
                GestureType.SCROLL, GestureType.DRAG -> {
                    webSurface.dispatchTouchEvent(event.u, event.v, android.view.MotionEvent.ACTION_MOVE)
                    webSurface.sendGameGesture("DRAG")
                }
                GestureType.SWIPE_LEFT -> {
                    webSurface.sendGameGesture("SWIPE_LEFT")
                    if (event.window?.appId in setOf("tetris", "dino", "flappy", "snake", "g2048", "chess", "pingpong3d", "shooter3d")) {
                        return
                    }
                }
                GestureType.SWIPE_RIGHT -> {
                    webSurface.sendGameGesture("SWIPE_RIGHT")
                    if (event.window?.appId in setOf("tetris", "dino", "flappy", "snake", "g2048", "chess", "pingpong3d", "shooter3d")) {
                        return
                    }
                }
                GestureType.SWIPE_UP -> {
                    webSurface.sendGameGesture("SWIPE_UP")
                    if (event.window?.appId in setOf("tetris", "dino", "flappy", "snake", "g2048", "chess", "pingpong3d", "shooter3d")) {
                        return
                    }
                }
                GestureType.SWIPE_DOWN -> {
                    webSurface.sendGameGesture("SWIPE_DOWN")
                    if (event.window?.appId in setOf("tetris", "dino", "flappy", "snake", "g2048", "chess", "pingpong3d", "shooter3d")) {
                        return
                    }
                }
                GestureType.PINCH -> {
                    webSurface.sendGameGesture("PINCH")
                }
                else -> {}
            }
        }

        // 5. Native ARApp interaction (Content area click/scroll)
        val nativeApp = appRegistry.activeNativeApps[event.window?.id]
        if (nativeApp != null && event.component == uz.orbitx.arport.engine.window.SpatialWindow.WindowComponent.CONTENT_AREA) {
            when (event.type) {
                GestureType.TAP -> {
                    nativeApp.onPointerEvent(event.u, event.v, android.view.MotionEvent.ACTION_DOWN)
                    nativeApp.onPointerEvent(event.u, event.v, android.view.MotionEvent.ACTION_UP)
                }
                GestureType.PINCH -> {
                    nativeApp.onPointerEvent(event.u, event.v, android.view.MotionEvent.ACTION_DOWN)
                }
                GestureType.HOVER -> {
                    nativeApp.onPointerEvent(event.u, event.v, android.view.MotionEvent.ACTION_HOVER_MOVE)
                }
                GestureType.SCROLL, GestureType.DRAG -> {
                    nativeApp.onPointerEvent(event.u, event.v, android.view.MotionEvent.ACTION_MOVE)
                }
                else -> {}
            }
        }

        // 5. Direct Window Transformations (Drag, Depth, Resize, Controls)
        val handled = inputInjector.handleWindowGesture(event, windowManager)
        if (handled) return

        // 6. System-level Gestures with dynamic shortcuts
        when (event.type) {
            GestureType.PALM_UP -> {
                val action = currentSettings.gestureShortcuts["PALM_UP"] ?: "HOME"
                executeShortcutAction(action, event)
            }
            GestureType.FIST -> {
                val action = currentSettings.gestureShortcuts["FIST"] ?: "CLOSE_WINDOW"
                executeShortcutAction(action, event)
            }
            GestureType.SWIPE_LEFT -> {
                val action = currentSettings.gestureShortcuts["SWIPE_LEFT"] ?: "CYCLE_WINDOWS_NEXT"
                executeShortcutAction(action, event)
            }
            GestureType.SWIPE_RIGHT -> {
                val action = currentSettings.gestureShortcuts["SWIPE_RIGHT"] ?: "CYCLE_WINDOWS_PREV"
                executeShortcutAction(action, event)
            }
            else -> {}
        }
    }

    private fun executeShortcutAction(action: String, event: GestureEvent) {
        when (action) {
            "HOME" -> {
                toggleHomePanel()
                onHomeDockRequested?.invoke()
            }
            "CLOSE_WINDOW" -> {
                event.window?.let { windowManager.closeWindow(it.id) }
            }
            "CYCLE_WINDOWS_NEXT" -> cycleWindowFocus(forward = true)
            "CYCLE_WINDOWS_PREV" -> cycleWindowFocus(forward = false)
            "RECENTER" -> recenter()
            "CAMERA" -> appRegistry.launchApp("camera", windowManager)
            "SETTINGS" -> appRegistry.launchApp("settings", windowManager)
            else -> {}
        }
    }

    fun applySettings(settings: uz.orbitx.arport.engine.settings.ArSettings) {
        currentSettings = settings
        glassRenderer.isReduceTransparency = settings.reduceTransparency
        glassRenderer.blurRadius = if (settings.glassBlurRadius <= 0f) 0.1f else (settings.glassBlurRadius / 6.0f)
        gestureEngine.pinchEnterThreshold = settings.pinchThresholdEnter
        gestureEngine.pinchExitThreshold = settings.pinchThresholdEnter * 1.5f
        handRenderer.isGlowEnabled = settings.handGlowEnabled
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

    fun setCameraRotation(rotation: Int) {
        passthroughRenderer.setCameraRotation(rotation)
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
        appRegistry.release()

        releaseNormalFramebuffers()
        passthroughRenderer.release()
        windowRenderer.release()
        handRenderer.release()
        glassRenderer.release()
        stereoRenderer.release()
        windowManager.release()
    }
}
