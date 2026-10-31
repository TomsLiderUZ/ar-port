package uz.orbitx.arport.engine

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Dedicated Crash Report Activity that runs in its own process (:crash)
 * to guarantee that any fatal native/JVM crash is gracefully captured
 * and visible to the user instead of a generic system crash popup.
 */
class CrashReportActivity : Activity() {

    companion object {
        const val EXTRA_THREAD = "EXTRA_THREAD"
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        const val EXTRA_STACK = "EXTRA_STACK"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val threadName = intent.getStringExtra(EXTRA_THREAD) ?: "Noma'lum oqim"
        val errorMsg = intent.getStringExtra(EXTRA_MESSAGE) ?: "Xatolik tafsiloti mavjud emas"
        val stackTrace = intent.getStringExtra(EXTRA_STACK) ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121214"))
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Header
        val header = TextView(this).apply {
            text = "⚠️ AR Port Xatolik Hisoboti"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        root.addView(header)

        val subHeader = TextView(this).apply {
            text = "Qurilma: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\nOqim: $threadName"
            textSize = 12f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(0, 0, 0, 20)
        }
        root.addView(subHeader)

        // Error title
        val errorCard = TextView(this).apply {
            text = errorMsg
            textSize = 14f
            setTextColor(Color.parseColor("#FF453A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(24, 20, 24, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C1517"))
                cornerRadius = 16f
                setStroke(2, Color.parseColor("#FF453A"))
            }
        }
        root.addView(errorCard)

        val stackLabel = TextView(this).apply {
            text = "To'liq Stack Trace:"
            textSize = 13f
            setTextColor(Color.parseColor("#AEAEB2"))
            setPadding(0, 20, 0, 8)
        }
        root.addView(stackLabel)

        // Scrollable Stacktrace Box
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply {
                setMargins(0, 0, 0, 24)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C1C1E"))
                cornerRadius = 16f
                setStroke(1, Color.parseColor("#3A3A3C"))
            }
            setPadding(20, 20, 20, 20)
        }

        val stackText = TextView(this).apply {
            text = stackTrace
            textSize = 11f
            setTextColor(Color.parseColor("#E5E5EA"))
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(stackText)
        root.addView(scrollView)

        // Action Buttons Row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val copyBtn = TextView(this).apply {
            text = "📋 Nusxalash"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(32, 20, 32, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A84FF"))
                cornerRadius = 24f
            }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("AR Port Crash", "Xatolik: $errorMsg\n\nStack:\n$stackTrace")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashReportActivity, "Xatolik nusxalandi!", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(copyBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            setMargins(0, 0, 12, 0)
        })

        val closeBtn = TextView(this).apply {
            text = "✕ Yopish"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(32, 20, 32, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C2E"))
                cornerRadius = 24f
            }
            setOnClickListener {
                finishAffinity()
            }
        }
        btnRow.addView(closeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
            setMargins(12, 0, 0, 0)
        })

        root.addView(btnRow)
        setContentView(root)
    }
}

