package uz.orbitx.arport.engine

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import uz.orbitx.arport.engine.camera.PassthroughCameraManager
import uz.orbitx.arport.engine.render.ArEngineRenderer
import uz.orbitx.arport.engine.tracking.TrackingManager

/**
 * Native Activity hosting the OpenGL ES 3.0 rendering cycle, CameraX passthrough,
 * and 3DoF/6DoF spatial motion tracking.
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

    private lateinit var glSurfaceView: GLSurfaceView
    private var trackingManager: TrackingManager? = null
    private var arRenderer: ArEngineRenderer? = null
    private var cameraManager: PassthroughCameraManager? = null

    private lateinit var hudTextView: TextView
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

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 1. Initialize Motion Tracking Manager
        val tm = TrackingManager(this)
        trackingManager = tm

        // 2. OpenGL ES 3.0 Surface View
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
        }

        arRenderer = ArEngineRenderer(
            viewMode = viewMode,
            trackingManager = tm,
            ipdMm = ipdMm,
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

        // 3. Liquid Glass Debug HUD (Top-Left)
        hudTextView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(24, 14, 24, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#991C1C1E")) // Frosted glass dark
                cornerRadius = 24f
                setStroke(1, Color.parseColor("#44FFFFFF"))
            }
            text = "AR Port • $viewMode | FPS: -- | Kechikish: --"
        }

        val hudParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setMargins(32, 32, 0, 0)
        }
        rootLayout.addView(hudTextView, hudParams)

        // 4. Top-Right Action Controls (Recenter + Close Buttons)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Recenter (Markazlashtirish) Button
        val recenterButton = TextView(this).apply {
            text = "🎯 Markazlashtirish"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(24, 14, 24, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#991C1C1E"))
                cornerRadius = 24f
                setStroke(1, Color.parseColor("#44FFFFFF"))
            }
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
                val modeLabel = if (viewMode == "stereo") "Stereo (IPD: ${ipdMm}mm)" else "Oddiy"

                hudTextView.text = String.format(
                    "AR Port • %s | %s\nFPS: %.1f | Kechikish: %.1f ms | %s",
                    modeLabel, trackingLabel, fps, latency, resolution
                )
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

    override fun onDestroy() {
        super.onDestroy()
        isHudActive = false
        mainHandler.removeCallbacksAndMessages(null)
        trackingManager?.stop()
        cameraManager?.stopCamera()
        arRenderer?.release()
    }
}
