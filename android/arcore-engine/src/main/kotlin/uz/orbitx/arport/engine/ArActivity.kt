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
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import uz.orbitx.arport.engine.camera.PassthroughCameraManager
import uz.orbitx.arport.engine.render.ArEngineRenderer
import uz.orbitx.arport.engine.stereo.LensProfile
import uz.orbitx.arport.engine.tracking.TrackingManager

/**
 * Native Activity hosting the OpenGL ES 3.0 rendering cycle, CameraX passthrough,
 * 3DoF/6DoF spatial motion tracking, and Stereoscopic dual-viewport with Cardboard lens distortion.
 */
class ArActivity : FragmentActivity() {

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
    private var isReduceTransparency: Boolean = false
    private var isLowSpecMode: Boolean = false

    private lateinit var rootLayout: FrameLayout
    private lateinit var glSurfaceView: GLSurfaceView
    private var trackingManager: TrackingManager? = null
    private var handTracker: uz.orbitx.arport.engine.hand.HandTracker? = null
    private var arRenderer: ArEngineRenderer? = null
    private var cameraManager: PassthroughCameraManager? = null
    private var thermalMonitor: uz.orbitx.arport.engine.render.ThermalMonitor? = null

    // Dual HUD (Left and Right eyes for stereoscopic comfort)
    private lateinit var leftHudTextView: TextView
    private lateinit var rightHudTextView: TextView
    private lateinit var modeToggleButton: TextView
    private lateinit var actionRow: LinearLayout
    private lateinit var cornerExitButton: TextView
    private var cameraRotationDegrees: Int = 0
    private val analysisExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isHudActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlobalCrashHandler.install(applicationContext)

        try {
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

            rootLayout = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }

            // 1. Initialize Motion Tracking Manager
            val tm = TrackingManager(this)
            trackingManager = tm

            // 2. OpenGL ES 3.0 Surface View
            glSurfaceView = GLSurfaceView(this).apply {
                setEGLContextClientVersion(3)
                try {
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                } catch (_: Throwable) {
                    setEGLConfigChooser(true)
                }
                preserveEGLContextOnPause = true
                setOnTouchListener { view, event ->
                    handleScreenTouch(event, view.width, view.height)
                    true
                }
            }

            arRenderer = ArEngineRenderer(
                context = this,
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

            arRenderer?.onHomeDockRequested = {
                runOnUiThread {
                    Toast.makeText(this, "🏠 Home Dock ochildi (Kaft ochiq gesti)", Toast.LENGTH_SHORT).show()
                }
            }

            arRenderer?.onExitRequested = {
                runOnUiThread {
                    showExitConfirmDialog()
                }
            }

            arRenderer?.onVolumeToggleRequested = {
                runOnUiThread {
                    Toast.makeText(this, "🔊 Ovoz boshqaruvi", Toast.LENGTH_SHORT).show()
                }
            }

            arRenderer?.onBrightnessToggleRequested = {
                runOnUiThread {
                    Toast.makeText(this, "☀️ Yorug'lik boshqaruvi", Toast.LENGTH_SHORT).show()
                }
            }

            arRenderer?.onPerformanceWarning = { warning ->
                runOnUiThread {
                    Toast.makeText(this, warning, Toast.LENGTH_LONG).show()
                }
            }

            // Initialize Hardware Thermal & Headroom Monitor
            val tmMonitor = uz.orbitx.arport.engine.render.ThermalMonitor(this)
            thermalMonitor = tmMonitor
            tmMonitor.onThermalStatusChanged = { status, _ ->
                arRenderer?.adaptiveQualityController?.onThermalStatusChanged(status)
            }
            tmMonitor.onThermalWarning = { warning ->
                runOnUiThread {
                    Toast.makeText(this, warning, Toast.LENGTH_LONG).show()
                }
            }

            val ht = uz.orbitx.arport.engine.hand.HandTracker(this)
            handTracker = ht
            arRenderer?.handTracker = ht

            glSurfaceView.setRenderer(arRenderer)
            glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

            rootLayout.addView(
                glSurfaceView,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            )

            // 3. Liquid Glass Dual Debug HUD (Left Eye & Right Eye aligned in 50% split columns)
            leftHudTextView = createHudTextView()
            rightHudTextView = createHudTextView()

            val dualHudContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    setMargins(20, 20, 20, 0)
                }
            }

            val leftCell = FrameLayout(this)
            leftCell.addView(leftHudTextView)
            dualHudContainer.addView(leftCell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f))

            val rightCell = FrameLayout(this)
            rightCell.addView(rightHudTextView)
            dualHudContainer.addView(rightCell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f))

            rootLayout.addView(dualHudContainer)

            // 4. Action Controls Bar (Top-Right, shown in Oddiy / Normal mode)
            actionRow = LinearLayout(this).apply {
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

            // Keyboard Button (Fazoviy Klaviatura)
            val keyboardButton = TextView(this).apply {
                text = "⌨️ Klaviatura"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(24, 14, 24, 14)
                background = createGlassBackground()
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    arRenderer?.toggleSpatialKeyboard()
                }
            }
            actionRow.addView(
                keyboardButton,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 16, 0)
                }
            )

            // Home Button (Bosh Ekran)
            val homeButton = TextView(this).apply {
                text = "🏠 Home"
                textSize = 12f
                setTextColor(Color.WHITE)
                setPadding(24, 14, 24, 14)
                background = createGlassBackground()
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    arRenderer?.toggleHomePanel()
                }
            }
            actionRow.addView(
                homeButton,
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
                setMargins(0, 20, 20, 0)
            }
            rootLayout.addView(actionRow, actionParams)

            // Minimalist Corner Exit Button for Stereo Mode
            cornerExitButton = TextView(this).apply {
                text = "✕"
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#991C1C1E"))
                    cornerRadius = 40f
                    setStroke(1, Color.parseColor("#44FFFFFF"))
                }
                setOnClickListener { finish() }
            }
            val cornerExitParams = FrameLayout.LayoutParams(68, 68).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, 20, 20, 0)
            }
            rootLayout.addView(cornerExitButton, cornerExitParams)

            updateHudVisibility()

            setContentView(rootLayout)
            enableImmersiveMode()

            startHudUpdater()
        } catch (e: Throwable) {
            android.util.Log.e("ArActivity", "AR Activity ishga tushirishda kutilmagan xatolik yuz berdi", e)
            Toast.makeText(
                this,
                "AR ishga tushirilmadi: ${e.localizedMessage ?: e.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun createHudTextView(): TextView {
        return TextView(this).apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(16, 8, 16, 8)
            background = createGlassBackground()
            text = "AR Port • $viewMode | FPS: --"
        }
    }

    private fun createGlassBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#991C1C1E"))
            cornerRadius = 20f
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
        val isStereo = (viewMode == "stereo")
        rightHudTextView.visibility = if (isStereo) View.VISIBLE else View.GONE
        actionRow.visibility = if (isStereo) View.GONE else View.VISIBLE
        cornerExitButton.visibility = if (isStereo) View.VISIBLE else View.GONE
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

        // Glass Rendering Controls (TZ 8.2)
        val glassLabel = TextView(this).apply {
            text = "Liquid Glass sozlamalari:"
            textSize = 13f
            setTextColor(Color.parseColor("#DDFFFFFF"))
            setPadding(0, 20, 0, 10)
        }
        dialogLayout.addView(glassLabel)

        val glassRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // Reduce Transparency Toggle Button
        val reduceTransBtn = TextView(this).apply {
            text = if (isReduceTransparency) "✓ Qattiq Fon (Tejamkor)" else "○ Shaffoflikni kamaytirish"
            textSize = 11f
            setPadding(16, 12, 16, 12)
            setTextColor(if (isReduceTransparency) Color.parseColor("#30D158") else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (isReduceTransparency) Color.parseColor("#3330D158") else Color.parseColor("#44333333"))
                cornerRadius = 18f
                setStroke(1, if (isReduceTransparency) Color.parseColor("#30D158") else Color.parseColor("#33FFFFFF"))
            }
            setOnClickListener {
                isReduceTransparency = !isReduceTransparency
                arRenderer?.setGlassParameters(true, isReduceTransparency, isLowSpecMode)
                dialog.dismiss()
                showCalibrationDialog()
            }
        }
        glassRow.addView(reduceTransBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(4, 0, 4, 0)
        })

        // Low Spec Mode (1/4 texture Kawase pass)
        val lowSpecBtn = TextView(this).apply {
            text = if (isLowSpecMode) "✓ Yengil Rejim (1/4)" else "○ Yuqori Blur (1/2)"
            textSize = 11f
            setPadding(16, 12, 16, 12)
            setTextColor(if (isLowSpecMode) Color.parseColor("#FF9F0A") else Color.WHITE)
            background = GradientDrawable().apply {
                setColor(if (isLowSpecMode) Color.parseColor("#33FF9F0A") else Color.parseColor("#44333333"))
                cornerRadius = 18f
                setStroke(1, if (isLowSpecMode) Color.parseColor("#FF9F0A") else Color.parseColor("#33FFFFFF"))
            }
            setOnClickListener {
                isLowSpecMode = !isLowSpecMode
                arRenderer?.setGlassParameters(true, isReduceTransparency, isLowSpecMode)
                dialog.dismiss()
                showCalibrationDialog()
            }
        }
        glassRow.addView(lowSpecBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(4, 0, 4, 0)
        })

        dialogLayout.addView(glassRow)

        // Camera Rotation Adjustment Button
        val camRotBtn = TextView(this).apply {
            text = "📷 Kamera burchagi: $cameraRotationDegrees°"
            textSize = 12f
            setPadding(16, 12, 16, 12)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#44333333"))
                cornerRadius = 18f
                setStroke(1, Color.parseColor("#33FFFFFF"))
            }
            setOnClickListener {
                cameraRotationDegrees = (cameraRotationDegrees + 90) % 360
                arRenderer?.setCameraRotation(cameraRotationDegrees)
                text = "📷 Kamera burchagi: $cameraRotationDegrees°"
            }
        }
        val camRotParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 10, 0, 10)
        }
        dialogLayout.addView(camRotBtn, camRotParams)

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
        try {
            val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_90
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }

            val cm = PassthroughCameraManager(
                context = this,
                lifecycleOwner = this,
                cameraId = cameraId,
                resolution = resolution,
                targetFps = targetFps,
                onPreviewSizeKnown = { width, height ->
                    arRenderer?.setCameraPreviewSize(width, height)
                },
                onRotationDegreesKnown = { rotation ->
                    // User & VR headset requirement: direct 0° unrotated passthrough window
                    cameraRotationDegrees = 0
                    arRenderer?.setCameraRotation(0)
                }
            )

            // Setup ImageAnalysis for 30 FPS hand landmark tracking (TZ 6.1)
            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setTargetRotation(displayRotation)
                .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            handTracker?.let { tracker ->
                imageAnalysis.setAnalyzer(analysisExecutor, tracker)
            }
            cm.imageAnalysis = imageAnalysis

            cameraManager = cm
            cm.startCamera(surfaceTexture)
        } catch (e: Throwable) {
            android.util.Log.e("ArActivity", "Kamera oqimini ishga tushirishda xatolik: ${e.message}", e)
        }
    }

    private fun startHudUpdater() {
        val updateRunnable = object : Runnable {
            override fun run() {
                if (!isHudActive) return
                val fps = arRenderer?.getFps() ?: 0f
                val latency = arRenderer?.getLatencyMs() ?: 0f
                val trackingLabel = trackingManager?.getTrackingModeLabel() ?: "3DoF"
                val modeLabel = if (viewMode == "stereo") "Stereo (IPD: ${ipdMm.toInt()}mm)" else "Oddiy"

                val handFps = handTracker?.fpsTracker?.getFps() ?: 0f
                val visibleHands = handTracker?.getVisibleHands() ?: emptyList()
                val handCount = visibleHands.size
                val handStatus = when (handCount) {
                    0 -> "Qo'l yo'q"
                    1 -> if (visibleHands[0].isRightHand) "1 ta (O'ng)" else "1 ta (Chap)"
                    else -> "2 ta (Chap + O'ng)"
                }
                val gestureStatus = arRenderer?.lastGestureLabel ?: "Kutilmoqda"

                val hudText = String.format(
                    "AR Port • %s | %s\nFPS: %.1f | Kechikish: %.1f ms | Qo'l: %s | Gest: %s",
                    modeLabel, trackingLabel, fps, latency, handStatus, gestureStatus
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
        try {
            val decor = window?.decorView ?: return
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, decor)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Throwable) {
            try {
                @Suppress("DEPRECATION")
                window?.decorView?.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            } catch (_: Throwable) {
                // Ignore any system ui visibility error on custom OEM ROMs
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            enableImmersiveMode()
            trackingManager?.start()
            if (::glSurfaceView.isInitialized) {
                glSurfaceView.onResume()
            }
        } catch (e: Throwable) {
            android.util.Log.e("ArActivity", "onResume xatolik", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            trackingManager?.stop()
            if (::glSurfaceView.isInitialized) {
                glSurfaceView.onPause()
            }
        } catch (e: Throwable) {
            android.util.Log.e("ArActivity", "onPause xatolik", e)
        }
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

    private var touchDraggedWindow: uz.orbitx.arport.engine.window.SpatialWindow? = null
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f

    private fun handleScreenTouch(event: MotionEvent, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val renderer = arRenderer ?: return

        // In normal mode, convert touch to raycast into the 3D scene
        val tanHalfFovY = kotlin.math.tan(Math.toRadians(65.0 * 0.5)).toFloat()
        val aspect = width.toFloat() / height.toFloat()
        val tanHalfFovX = tanHalfFovY * aspect

        val ndcX = (2f * event.x / width) - 1.0f
        val ndcY = 1.0f - (2f * event.y / height)

        val rayDirX = ndcX * tanHalfFovX
        val rayDirY = ndcY * tanHalfFovY
        val rayDirZ = -1.0f

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                touchDraggedWindow = null

                val hitPair = renderer.windowManager.raycast(0f, 0f, 0f, rayDirX, rayDirY, rayDirZ)
                if (hitPair != null) {
                    val (hitWin, hitResult) = hitPair
                    glSurfaceView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    // 1. Keyboard Tap
                    if (hitWin.appId == "spatial_keyboard") {
                        renderer.spatialKeyboard.onKeyHit(hitResult.u, hitResult.v)
                        return
                    }

                    // 2. Dock Tap
                    if (hitWin.appId == "spatial_dock") {
                        renderer.spatialDock.onDockHit(hitResult.u, hitResult.v)
                        return
                    }

                    // 3. Home Panel Tap
                    if (hitWin.appId == "spatial_home") {
                        renderer.spatialHomePanel.onHomeHit(hitResult.u, hitResult.v)
                        return
                    }

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
                        uz.orbitx.arport.engine.window.SpatialWindow.WindowComponent.HEADER_TITLE,
                        uz.orbitx.arport.engine.window.SpatialWindow.WindowComponent.BOTTOM_DRAG_HANDLE -> {
                            renderer.windowManager.setFocus(hitWin.id)
                            touchDraggedWindow = hitWin
                        }
                        else -> {
                            renderer.windowManager.setFocus(hitWin.id)
                            renderer.appRegistry.activeWebSurfaces[hitWin.id]?.dispatchTouchEvent(hitResult.u, hitResult.v, event.action)
                            renderer.appRegistry.activeNativeApps[hitWin.id]?.onPointerEvent(hitResult.u, hitResult.v, event.action)
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val win = touchDraggedWindow
                if (win != null) {
                    val dx = (event.x - lastTouchX) / width * 2.0f
                    val dy = -(event.y - lastTouchY) / height * 2.0f
                    win.setPosition(win.posX + dx, win.posY + dy, win.posZ)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchDraggedWindow = null
            }
        }
    }

    private fun showExitConfirmDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 36, 48, 36)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61C1C1E"))
                cornerRadius = 36f
                setStroke(1, Color.parseColor("#44FFFFFF"))
            }
        }

        val title = TextView(this).apply {
            text = "AR Rejimdan Chiqish"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        val desc = TextView(this).apply {
            text = "Rostdan ham AR rejimini yopmoqchimisiz?"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 24)
        }
        layout.addView(desc)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val cancelBtn = TextView(this).apply {
            text = "Bekor qilish"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(28, 14, 28, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 20f
            }
            setOnClickListener { dialog.dismiss() }
        }
        btnRow.addView(cancelBtn)

        val exitBtn = TextView(this).apply {
            text = "Chiqish"
            textSize = 14f
            setTextColor(Color.parseColor("#FF453A"))
            setPadding(28, 14, 28, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FF453A"))
                cornerRadius = 20f
                setStroke(1, Color.parseColor("#66FF453A"))
            }
            setOnClickListener {
                dialog.dismiss()
                finish()
            }
        }
        val exitParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(16, 0, 0, 0)
        }
        btnRow.addView(exitBtn, exitParams)

        layout.addView(btnRow)
        dialog.setContentView(layout)
        dialog.show()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        arRenderer?.windowManager?.onTrimMemory(level)
    }

    override fun onDestroy() {
        super.onDestroy()
        isHudActive = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            analysisExecutor.shutdown()
            trackingManager?.stop()
            handTracker?.release()
            thermalMonitor?.release()
            cameraManager?.stopCamera()
            arRenderer?.release()
        } catch (e: Throwable) {
            android.util.Log.e("ArActivity", "onDestroy tozalash xatolik", e)
        }
    }
}
