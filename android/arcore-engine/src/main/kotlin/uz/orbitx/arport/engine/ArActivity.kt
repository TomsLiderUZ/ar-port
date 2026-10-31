package uz.orbitx.arport.engine

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import uz.orbitx.arport.engine.camera.PassthroughCameraManager
import uz.orbitx.arport.engine.render.ArEngineRenderer
import uz.orbitx.arport.engine.stereo.LensProfile
import uz.orbitx.arport.engine.tracking.TrackingManager

/**
 * Native Activity hosting the OpenGL ES 3.0 rendering cycle, CameraX passthrough,
 * 3DoF/6DoF spatial motion tracking, and Stereoscopic dual-viewport with Cardboard lens distortion.
 */
class ArActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIEW_MODE = "EXTRA_VIEW_MODE"
        const val EXTRA_CAMERA_ID = "EXTRA_CAMERA_ID"
        const val EXTRA_RESOLUTION = "EXTRA_RESOLUTION"
        const val EXTRA_TARGET_FPS = "EXTRA_TARGET_FPS"
        const val EXTRA_IPD_MM = "EXTRA_IPD_MM"
        const val EXTRA_LENS_PROFILE = "EXTRA_LENS_PROFILE"
        const val EXTRA_AUDIO_INPUT = "EXTRA_AUDIO_INPUT"
        const val EXTRA_AUDIO_OUTPUT = "EXTRA_AUDIO_OUTPUT"

        fun createIntent(
            context: Context,
            viewMode: String,
            cameraId: String,
            resolution: String,
            targetFps: Int,
            ipdMm: Double,
            lensProfile: String,
            audioInput: String?,
            audioOutput: String?
        ): Intent {
            return Intent(context, ArActivity::class.java).apply {
                putExtra(EXTRA_VIEW_MODE, viewMode)
                putExtra(EXTRA_CAMERA_ID, cameraId)
                putExtra(EXTRA_RESOLUTION, resolution)
                putExtra(EXTRA_TARGET_FPS, targetFps)
                putExtra(EXTRA_IPD_MM, ipdMm)
                putExtra(EXTRA_LENS_PROFILE, lensProfile)
                putExtra(EXTRA_AUDIO_INPUT, audioInput ?: "")
                putExtra(EXTRA_AUDIO_OUTPUT, audioOutput ?: "")
            }
        }
    }

    private var viewMode: String = "stereo"
    private var cameraId: String = "0"
    private var resolution: String = "1080p"
    private var targetFps: Int = 60
    private var ipdMm: Double = 63.0
    private var lensProfile: String = "cardboard_v2"

    private lateinit var rootLayout: FrameLayout
    private lateinit var glSurfaceView: GLSurfaceView
    private var trackingManager: TrackingManager? = null
    private var arRenderer: ArEngineRenderer? = null
    private var cameraManager: PassthroughCameraManager? = null

    // Dual HUD (Left and Right eyes for stereoscopic comfort)
    private lateinit var leftHudTextView: TextView
    private lateinit var rightHudTextView: TextView
    private lateinit var modeToggleButton: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isHudActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read launch arguments
        intent?.let {
            viewMode = it.getStringExtra(EXTRA_VIEW_MODE) ?: "stereo"
            cameraId = it.getStringExtra(EXTRA_CAMERA_ID) ?: "0"
            resolution = it.getStringExtra(EXTRA_RESOLUTION) ?: "1080p"
            targetFps = it.getIntExtra(EXTRA_TARGET_FPS, 60)
            ipdMm = it.getDoubleExtra(EXTRA_IPD_MM, 63.0)
            lensProfile = it.getStringExtra(EXTRA_LENS_PROFILE) ?: "cardboard_v2"
        }

        // Fullscreen and Keep Screen On
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 1. Initialize Motion Tracking Manager
        val tm = TrackingManager(this)
        trackingManager = tm

        // 2. OpenGL ES 3.0 Surface View
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
            setOnTouchListener { view, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    handleScreenTouch(event.x, event.y, view.width, view.height)
                }
                false
            }
        }

        arRenderer = ArEngineRenderer(
            viewMode = viewMode,
            trackingManager = tm,
            ipdMm = ipdMm,
            lensProfileId = lensProfile,
            onSurfaceTextureReady = { surfaceTexture ->
                runOnUiThread {
                    startCameraStream(surfaceTexture)
                }
            },
            requestRender = {
                glSurfaceView.requestRender()
            }
        )

        glSurfaceView.setRenderer(arRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        rootLayout.addView(
            glSurfaceView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        // 3. Liquid Glass Dual Debug HUD (Left Eye & Right Eye)
        leftHudTextView = createHudTextView()
        val leftHudParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(32, 32, 0, 0)
        }
        rootLayout.addView(leftHudTextView, leftHudParams)

        rightHudTextView = createHudTextView()
        val rightHudParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setMargins(32, 32, 0, 0)
        }
        rootLayout.addView(rightHudTextView, rightHudParams)
        updateHudVisibility()

        // 4. Action Controls Bar (Top-Right)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Mode Toggle Button (Stereo <-> Oddiy)
        modeToggleButton = TextView(this).apply {
            text = if (viewMode == "stereo") "👓 Stereo" else "📱 Oddiy"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(24, 14, 24, 14)
            background = createGlassBackground()
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toggleViewMode()
            }
        }
        actionRow.addView(
            modeToggleButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 16, 0)
            }
        )

        // Add Window Button (+ Oyna)
        val addWindowButton = TextView(this).apply {
            text = "+ Oyna"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(24, 14, 24, 14)
            background = createGlassBackground()
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                addNewDemoWindow()
            }
        }
        actionRow.addView(
            addWindowButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 16, 0)
            }
        )

        // Calibration Button (Linza va IPD)
        val calibrationButton = TextView(this).apply {
            text = "⚙️ Kalibratsiya"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(24, 14, 24, 14)
            background = createGlassBackground()
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                showCalibrationDialog()
            }
        }
        actionRow.addView(
            calibrationButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 16, 0)
            }
        )

        // Recenter (Markazlashtirish) Button
        val recenterButton = TextView(this).apply {
            text = "🎯 Markazlashtirish"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(24, 14, 24, 14)
            background = createGlassBackground()
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                arRenderer?.recenter()
            }
        }
        actionRow.addView(
            recenterButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 16, 0)
            }
        )

        // Fallback Close Button
        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#991C1C1E"))
                cornerRadius = 40f
                setStroke(1, Color.parseColor("#44FFFFFF"))
            }
            setOnClickListener { finish() }
        }
        actionRow.addView(closeButton, LinearLayout.LayoutParams(72, 72))

        val actionParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            setMargins(0, 32, 32, 0)
        }
        rootLayout.addView(actionRow, actionParams)

        setContentView(rootLayout)

        startHudUpdater()
    }

    private fun createHudTextView(): TextView {
        return TextView(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(20, 12, 20, 12)
            background = createGlassBackground()
            text = "AR Port • $viewMode | FPS: --"
        }
    }

    private fun createGlassBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#991C1C1E"))
            cornerRadius = 24f
            setStroke(1, Color.parseColor("#44FFFFFF"))
        }
    }

    private fun toggleViewMode() {
        viewMode = if (viewMode == "stereo") "normal" else "stereo"
        arRenderer?.viewMode = viewMode
        modeToggleButton.text = if (viewMode == "stereo") "👓 Stereo" else "📱 Oddiy"
        updateHudVisibility()
    }

    private fun updateHudVisibility() {
        rightHudTextView.visibility = if (viewMode == "stereo") View.VISIBLE else View.GONE
    }

    private fun showCalibrationDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1C1C1E"))
                cornerRadius = 32f
                setStroke(1, Color.parseColor("#55FFFFFF"))
            }
        }

        val title = TextView(this).apply {
            text = "Stereo va Linza Kalibratsiyasi"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(title)

        // IPD Slider Row
        val ipdLabel = TextView(this).apply {
            text = "Ko'zlararo masofa (IPD): ${ipdMm.toInt()} mm"
            textSize = 13f
            setTextColor(Color.parseColor("#DDFFFFFF"))
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(ipdLabel)

        val ipdSeekBar = SeekBar(this).apply {
            max = 20 // 55 to 75
            progress = (ipdMm - 55.0).toInt().coerceIn(0, 20)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val newIpd = 55.0 + progress
                    ipdMm = newIpd
                    ipdLabel.text = "Ko'zlararo masofa (IPD): ${newIpd.toInt()} mm"
                    arRenderer?.updateStereoSettings(ipdMm, lensProfile)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        dialogLayout.addView(ipdSeekBar)

        // Lens Profile Choices
        val lensLabel = TextView(this).apply {
            text = "Linza profili:"
            textSize = 13f
            setTextColor(Color.parseColor("#DDFFFFFF"))
            setPadding(0, 24, 0, 12)
        }
        dialogLayout.addView(lensLabel)

        val lensRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val profiles = listOf(
            LensProfile.CARDBOARD_V2,
            LensProfile.CARDBOARD_V1,
            LensProfile.NONE
        )

        for (p in profiles) {
            val btn = TextView(this).apply {
                text = p.name.replace("Google ", "")
                textSize = 11f
                setPadding(20, 14, 20, 14)
                setTextColor(if (lensProfile == p.id) Color.parseColor("#0A84FF") else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (lensProfile == p.id) Color.parseColor("#330A84FF") else Color.parseColor("#44333333"))
                    cornerRadius = 20f
                    setStroke(1, if (lensProfile == p.id) Color.parseColor("#0A84FF") else Color.parseColor("#33FFFFFF"))
                }
                setOnClickListener {
                    lensProfile = p.id
                    arRenderer?.updateStereoSettings(ipdMm, lensProfile)
                    dialog.dismiss()
                    showCalibrationDialog() // refresh styles
                }
            }
            lensRow.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 8, 0)
            })
        }
        dialogLayout.addView(lensRow)

        // Done Button
        val doneBtn = TextView(this).apply {
            text = "Tayyor"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 8)
            setOnClickListener {
                dialog.dismiss()
            }
        }
        dialogLayout.addView(doneBtn)

        dialog.setContentView(dialogLayout)
        dialog.show()
    }

    private fun startCameraStream(surfaceTexture: android.graphics.SurfaceTexture) {
        cameraManager = PassthroughCameraManager(
            context = this,
            lifecycleOwner = this,
            cameraId = cameraId,
            resolution = resolution,
            targetFps = targetFps,
            onPreviewSizeKnown = { width, height ->
                arRenderer?.setCameraPreviewSize(width, height)
            }
        )
        cameraManager?.startCamera(surfaceTexture)
    }

    private fun startHudUpdater() {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (!isHudActive) return
                val fps = arRenderer?.getFps() ?: 0f
                val latency = arRenderer?.getLatencyMs() ?: 0f
                val trackingLabel = trackingManager?.getTrackingModeLabel() ?: "3DoF"
                val modeLabel = if (viewMode == "stereo") "Stereo (IPD: ${ipdMm.toInt()}mm)" else "Oddiy"

                val hudText = String.format(
                    "AR Port • %s | %s\nFPS: %.1f | Kechikish: %.1f ms | %s",
                    modeLabel, trackingLabel, fps, latency, resolution
                )
                leftHudTextView.text = hudText
                if (viewMode == "stereo") {
                    rightHudTextView.text = hudText
                }
                mainHandler.postDelayed(this, 500)
            }
        }
        mainHandler.post(updateRunnable)
    }

    private fun enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        trackingManager?.start()
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        trackingManager?.stop()
        glSurfaceView.onPause()
    }

    private var demoWindowCounter = 4

    private fun addNewDemoWindow() {
        val renderer = arRenderer ?: return
        val count = demoWindowCounter++
        val isPortrait = (count % 2 == 1)
        val aspect = if (isPortrait) uz.orbitx.arport.engine.window.WindowAspect.LOCKED_9_16 else uz.orbitx.arport.engine.window.WindowAspect.LOCKED_16_9
        val width = if (isPortrait) 0.45f else 0.85f
        val height = if (isPortrait) 0.80f else 0.478f

        val win = renderer.windowManager.createWindow(
            appId = "demo_app_$count",
            title = "Ilova #$count",
            width = width,
            height = height,
            aspect = aspect
        ).apply {
            contentSurface = uz.orbitx.arport.engine.window.ContentSurface(if (isPortrait) 720 else 1280, if (isPortrait) 1280 else 720)
        }

        win.contentSurface?.draw { canvas ->
            canvas.drawColor(android.graphics.Color.parseColor("#E6202228"))
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 44f
                isFakeBoldText = true
            }
            canvas.drawText("Ilova #$count", 60f, 100f, paint)
            paint.textSize = 28f
            paint.color = android.graphics.Color.parseColor("#8E8E93")
            paint.isFakeBoldText = false
            canvas.drawText("Yangi fazoviy oyna (WindowManager3D)", 60f, 160f, paint)

            val boxPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#330A84FF")
            }
            canvas.drawRoundRect(android.graphics.RectF(60f, 220f, 600f, 400f), 24f, 24f, boxPaint)
            paint.color = android.graphics.Color.parseColor("#0A84FF")
            paint.textSize = 32f
            canvas.drawText("Holat: Faol • Z-tartib: ${win.zOrder}", 90f, 310f, paint)
        }
    }

    private fun handleScreenTouch(touchX: Float, touchY: Float, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val renderer = arRenderer ?: return

        // In normal mode, convert touch to raycast into the 3D scene
        val tanHalfFovY = kotlin.math.tan(Math.toRadians(65.0 * 0.5)).toFloat()
        val aspect = width.toFloat() / height.toFloat()
        val tanHalfFovX = tanHalfFovY * aspect

        val ndcX = (2f * touchX / width) - 1.0f
        val ndcY = 1.0f - (2f * touchY / height)

        val rayDirX = ndcX * tanHalfFovX
        val rayDirY = ndcY * tanHalfFovY
        val rayDirZ = -1.0f

        val hitPair = renderer.windowManager.raycast(0f, 0f, 0f, rayDirX, rayDirY, rayDirZ)
        if (hitPair != null) {
            val (hitWin, hitResult) = hitPair
            glSurfaceView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

            when (hitResult.component) {
                uz.orbitx.arport.engine.window.SpatialWindow.WindowComponent.CLOSE_BUTTON -> {
                    renderer.windowManager.closeWindow(hitWin.id)
                }
                uz.orbitx.arport.engine.window.SpatialWindow.WindowComponent.MINIMIZE_BUTTON -> {
                    renderer.windowManager.minimizeWindow(hitWin.id)
                }
                uz.orbitx.arport.engine.window.SpatialWindow.WindowComponent.RESIZE_HANDLE -> {
                    val nextAspect = if (hitWin.aspect == uz.orbitx.arport.engine.window.WindowAspect.LOCKED_16_9) {
                        uz.orbitx.arport.engine.window.WindowAspect.LOCKED_9_16
                    } else {
                        uz.orbitx.arport.engine.window.WindowAspect.LOCKED_16_9
                    }
                    hitWin.updateAspect(nextAspect)
                }
                else -> {
                    renderer.windowManager.setFocus(hitWin.id)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isHudActive = false
        mainHandler.removeCallbacksAndMessages(null)
        trackingManager?.stop()
        cameraManager?.stopCamera()
        arRenderer?.release()
    }
}
