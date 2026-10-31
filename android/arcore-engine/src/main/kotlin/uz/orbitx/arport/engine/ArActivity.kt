package uz.orbitx.arport.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Native Activity hosting the AR Port engine.
 * Receives runtime configuration from Flutter launcher and operates in immersive landscape mode.
 */
class ArActivity : Activity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read launch configuration
        intent?.let {
            viewMode = it.getStringExtra(EXTRA_VIEW_MODE) ?: "stereo"
            cameraId = it.getStringExtra(EXTRA_CAMERA_ID) ?: "0"
            resolution = it.getStringExtra(EXTRA_RESOLUTION) ?: "1080p"
            targetFps = it.getIntExtra(EXTRA_TARGET_FPS, 60)
            ipdMm = it.getDoubleExtra(EXTRA_IPD_MM, 63.0)
            lensProfile = it.getStringExtra(EXTRA_LENS_PROFILE) ?: "cardboard_v2"
        }

        // Configure full screen and keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()

        // Root container for QADAM 4 (black screen with configuration confirmation)
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // Informative configuration badge
        val infoView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            text = buildString {
                append("AR Port Dvigateli Faol\n")
                append("Rejim: $viewMode | Kamera: #$cameraId ($resolution, ${targetFps}fps)\n")
                if (viewMode == "stereo") {
                    append("IPD: ${ipdMm}mm | Linza: $lensProfile\n")
                }
                append("(Chiqish uchun ekranga bosing)")
            }
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        rootLayout.addView(infoView, params)
        setContentView(rootLayout)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }
}

