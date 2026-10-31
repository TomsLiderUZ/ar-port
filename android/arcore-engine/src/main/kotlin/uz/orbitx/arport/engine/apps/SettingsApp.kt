package uz.orbitx.arport.engine.apps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import uz.orbitx.arport.engine.registry.ARApp
import uz.orbitx.arport.engine.registry.AppManifest
import uz.orbitx.arport.engine.session.SessionStore
import uz.orbitx.arport.engine.settings.ArSettings
import uz.orbitx.arport.engine.settings.SettingsStore
import uz.orbitx.arport.engine.window.ContentSurface

/**
 * Native Spatial Settings Application (TZ 10, QADAM 15).
 * Full control over display opacity/blur/passthrough, liquid glass parameters,
 * hand gestures & shortcuts ("o'rgatish" learning mode), audio, camera,
 * window limits, sandbox cookies, and performance profiles.
 */
class SettingsApp(
    override val manifest: AppManifest
) : ARApp {

    override val id: String = manifest.id

    enum class Section(val label: String, val icon: String) {
        DISPLAY("Ko'rinish", "📱"),
        GESTURES("Qo'l va Gestlar", "🖐"),
        WINDOWS("Oynalar", "🪟"),
        PERFORMANCE("Samaradorlik", "⚡"),
        AUDIO("Ovoz", "🔊"),
        CAMERA("Kamera", "📷"),
        SANDBOX("Xavfsizlik", "🛡"),
        ABOUT("Haqida", "ℹ️")
    }

    var currentSection: Section = Section.DISPLAY
    var settings: ArSettings = ArSettings()
    var isLearningGesture: Boolean = false
    var toastMessage: String? = null
    var toastExpireMs: Long = 0L

    private var appContext: Context? = null
    var surface: ContentSurface? = null
        private set

    private var surfaceWidth: Int = 1280
    private var surfaceHeight: Int = 720

    override fun createSurface(context: Context, width: Int, height: Int): ContentSurface {
        appContext = context
        surfaceWidth = width
        surfaceHeight = height
        val surf = ContentSurface(width, height)
        surface = surf

        val store = SettingsStore.getInstance(context)
        settings = store.currentSettings
        store.addListener { updated ->
            settings = updated
            render()
        }

        render()
        return surf
    }

    fun showToast(msg: String) {
        toastMessage = msg
        toastExpireMs = System.currentTimeMillis() + 3000L
        render()
    }

    fun persistSettings() {
        val store = SettingsStore.getInstance(appContext)
        store.saveSettings(settings)
    }

    override fun onPointerEvent(u: Float, v: Float, action: Int) {
        if (action != MotionEvent.ACTION_UP) return

        val px = u * surfaceWidth
        val py = v * surfaceHeight

        // If in Gesture Learning mode dialog: tap dismisses it
        if (isLearningGesture) {
            if (px in 450f..830f && py in 450f..510f) {
                isLearningGesture = false
                showToast("Gest o'rgatildi: Kaft yuqoriga ➔ Home")
                render()
            }
            return
        }

        // 1. Left Sidebar Selection (x=40..340, y=30..690)
        if (px in 40f..340f) {
            val sections = Section.values()
            var startY = 80f
            for (sec in sections) {
                if (py in startY..startY + 65f) {
                    currentSection = sec
                    render()
                    return
                }
                startY += 72f
            }
            return
        }

        // 2. Right Details Panel Interactions (x=360..1240)
        when (currentSection) {
            Section.DISPLAY -> handleDisplayPointer(px, py)
            Section.GESTURES -> handleGesturesPointer(px, py)
            Section.WINDOWS -> handleWindowsPointer(px, py)
            Section.PERFORMANCE -> handlePerformancePointer(px, py)
            Section.AUDIO -> handleAudioPointer(px, py)
            Section.CAMERA -> handleCameraPointer(px, py)
            Section.SANDBOX -> handleSandboxPointer(px, py)
            Section.ABOUT -> handleAboutPointer(px, py)
        }
    }

    private fun handleDisplayPointer(px: Float, py: Float) {
        // Fon Rejimi Pills (y=110..160, x=390..930)
        if (py in 110f..160f) {
            if (px in 390f..560f) { settings.backgroundMode = "passthrough"; persistSettings(); return }
            if (px in 570f..740f) { settings.backgroundMode = "solid"; persistSettings(); return }
            if (px in 750f..930f) { settings.backgroundMode = "gradient"; persistSettings(); return }
        }

        // Orqa fon shaffofligi / tint opacity (y=210..260, x=390..870)
        if (py in 210f..260f) {
            if (px in 390f..470f) { settings.backgroundOpacity = 0.0f; persistSettings(); return }
            if (px in 480f..560f) { settings.backgroundOpacity = 0.25f; persistSettings(); return }
            if (px in 570f..650f) { settings.backgroundOpacity = 0.50f; persistSettings(); return }
            if (px in 660f..740f) { settings.backgroundOpacity = 0.75f; persistSettings(); return }
            if (px in 750f..870f) { settings.backgroundOpacity = 1.0f; persistSettings(); return }
        }

        // Shisha xiraligi / Reduce Transparency Toggle (y=300..350, x=1090..1170)
        if (py in 300f..350f && px in 1080f..1190f) {
            settings.reduceTransparency = !settings.reduceTransparency
            persistSettings()
            return
        }

        // Shisha blur radiusi (y=390..440, x=390..790)
        if (py in 390f..440f) {
            if (px in 390f..510f) { settings.glassBlurRadius = 0.0f; persistSettings(); return }
            if (px in 520f..640f) { settings.glassBlurRadius = 16.0f; persistSettings(); return }
            if (px in 650f..790f) { settings.glassBlurRadius = 32.0f; persistSettings(); return }
        }

        // Ekran yorqinligi (y=480..530, x=390..790)
        if (py in 480f..530f) {
            if (px in 390f..510f) { settings.displayBrightness = 0.5f; persistSettings(); return }
            if (px in 520f..640f) { settings.displayBrightness = 0.75f; persistSettings(); return }
            if (px in 650f..790f) { settings.displayBrightness = 1.0f; persistSettings(); return }
        }
    }

    private fun handleGesturesPointer(px: Float, py: Float) {
        // Qo'l yoritilishi (Hand glow) toggle (y=110..160, x=1090..1170)
        if (py in 110f..160f && px in 1080f..1190f) {
            settings.handGlowEnabled = !settings.handGlowEnabled
            persistSettings()
            return
        }

        // Sezgirlik / Pinch threshold (y=200..250, x=390..790)
        if (py in 200f..250f) {
            if (px in 390f..510f) { settings.pinchThresholdEnter = 0.065f; persistSettings(); return } // Yuqori
            if (px in 520f..640f) { settings.pinchThresholdEnter = 0.052f; persistSettings(); return } // Normal
            if (px in 650f..790f) { settings.pinchThresholdEnter = 0.040f; persistSettings(); return } // Past
        }

        // Gest O'rgatish Rejimini boshlash tugmasi (y=490..550, x=390..690)
        if (py in 490f..550f && px in 390f..690f) {
            isLearningGesture = true
            render()
            return
        }
    }

    private fun handleWindowsPointer(px: Float, py: Float) {
        // Maksimal oynalar soni (y=110..160, x=390..750)
        if (py in 110f..160f) {
            if (px in 390f..490f) { settings.maxOpenWindows = 3; persistSettings(); return }
            if (px in 500f..610f) { settings.maxOpenWindows = 6; persistSettings(); return }
            if (px in 620f..750f) { settings.maxOpenWindows = 8; persistSettings(); return }
        }

        // Uxlatish siyosati / Sleep inactive windows toggle (y=210..260, x=1090..1170)
        if (py in 210f..260f && px in 1080f..1190f) {
            settings.sleepInactiveWindows = !settings.sleepInactiveWindows
            persistSettings()
            return
        }
    }

    private fun handlePerformancePointer(px: Float, py: Float) {
        // Profil (y=110..160, x=390..790)
        if (py in 110f..160f) {
            if (px in 390f..510f) { settings.performanceProfile = "auto"; persistSettings(); return }
            if (px in 520f..640f) { settings.performanceProfile = "high"; persistSettings(); return }
            if (px in 650f..790f) { settings.performanceProfile = "low"; persistSettings(); return }
        }

        // Termal himoya toggle (y=210..260, x=1090..1170)
        if (py in 210f..260f && px in 1080f..1190f) {
            settings.thermalProtectionEnabled = !settings.thermalProtectionEnabled
            persistSettings()
            return
        }
    }

    private fun handleAudioPointer(px: Float, py: Float) {
        // Ovoz balandligi (y=110..160, x=390..790)
        if (py in 110f..160f) {
            if (px in 390f..470f) { settings.masterVolume = 0.0f; persistSettings(); return }
            if (px in 480f..560f) { settings.masterVolume = 0.5f; persistSettings(); return }
            if (px in 570f..660f) { settings.masterVolume = 0.75f; persistSettings(); return }
            if (px in 670f..790f) { settings.masterVolume = 1.0f; persistSettings(); return }
        }

        // Fazoviy ovoz toggle (y=210..260, x=1090..1170)
        if (py in 210f..260f && px in 1080f..1190f) {
            settings.spatialAudioEnabled = !settings.spatialAudioEnabled
            persistSettings()
            return
        }
    }

    private fun handleCameraPointer(px: Float, py: Float) {
        // Low latency toggle (y=110..160, x=1090..1170)
        if (py in 110f..160f && px in 1080f..1190f) {
            settings.lowLatencyCameraMode = !settings.lowLatencyCameraMode
            persistSettings()
            return
        }

        // Camera target FPS (y=210..260, x=390..650)
        if (py in 210f..260f) {
            if (px in 390f..500f) { settings.cameraFpsTarget = 60; persistSettings(); return }
            if (px in 510f..650f) { settings.cameraFpsTarget = 30; persistSettings(); return }
        }
    }

    private fun handleSandboxPointer(px: Float, py: Float) {
        // Cookie & Sessions tozalash tugmasi (y=120..180, x=390..740)
        if (py in 120f..180f && px in 390f..740f) {
            SessionStore.getInstance().clearAll()
            showToast("Barcha sessiyalar va cookie-fayllar tozalandi")
            return
        }

        // Sozlamalarni eksport qilish tugmasi (y=210..270, x=390..740)
        if (py in 210f..270f && px in 390f..740f) {
            val file = SettingsStore.getInstance(appContext).exportSettingsToSandbox()
            if (file != null) {
                showToast("Sozlamalar eksport qilindi: ${file.name}")
            } else {
                showToast("Eksport xatosi")
            }
            return
        }
    }

    private fun handleAboutPointer(px: Float, py: Float) {
        // Zavod holatiga qaytarish (Reset to Defaults, y=490..550, x=390..690)
        if (py in 490f..550f && px in 390f..690f) {
            SettingsStore.getInstance(appContext).resetToDefaults()
            showToast("Barcha sozlamalar zavod holatiga qaytarildi")
            return
        }
    }

    fun render() {
        surface?.draw { canvas ->
            canvas.drawColor(Color.parseColor("#E614161C"))

            // Left Navigation Sidebar
            renderSidebar(canvas)

            // Right Details Panel
            renderDetailsPanel(canvas)

            // Learning Mode Dialog if active
            if (isLearningGesture) {
                renderLearningDialog(canvas)
            }

            // Toast if active
            renderToastIfNeeded(canvas)
        }
    }

    private fun renderSidebar(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Sidebar title
        paint.color = Color.WHITE
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText("⚙️ Sozlamalar", 50f, 65f, paint)
        paint.isFakeBoldText = false

        val sections = Section.values()
        var startY = 80f
        for (sec in sections) {
            val isSel = (sec == currentSection)
            val rect = RectF(40f, startY, 340f, startY + 62f)

            paint.color = if (isSel) Color.parseColor("#0A84FF") else Color.parseColor("#22232A")
            canvas.drawRoundRect(rect, 16f, 16f, paint)

            paint.color = if (isSel) Color.WHITE else Color.parseColor("#E0E0E0")
            paint.textSize = 21f
            paint.isFakeBoldText = isSel
            canvas.drawText("${sec.icon} ${sec.label}", 60f, startY + 39f, paint)
            paint.isFakeBoldText = false

            startY += 72f
        }
    }

    private fun renderDetailsPanel(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Panel Container (x=360..1240, y=30..690)
        val panelRect = RectF(360f, 30f, 1240f, 690f)
        paint.color = Color.parseColor("#202128")
        canvas.drawRoundRect(panelRect, 24f, 24f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText("${currentSection.icon} ${currentSection.label}", 390f, 75f, paint)
        paint.isFakeBoldText = false

        when (currentSection) {
            Section.DISPLAY -> renderDisplayDetails(canvas)
            Section.GESTURES -> renderGesturesDetails(canvas)
            Section.WINDOWS -> renderWindowsDetails(canvas)
            Section.PERFORMANCE -> renderPerformanceDetails(canvas)
            Section.AUDIO -> renderAudioDetails(canvas)
            Section.CAMERA -> renderCameraDetails(canvas)
            Section.SANDBOX -> renderSandboxDetails(canvas)
            Section.ABOUT -> renderAboutDetails(canvas)
        }
    }

    private fun renderDisplayDetails(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Orqa fon rejimi (Background Mode)
        paint.color = Color.parseColor("#A0A0A5")
        paint.textSize = 20f
        canvas.drawText("Orqa fon rejimi", 390f, 100f, paint)

        val modes = listOf(
            Triple("Passthrough (Kamera)", 390f, 560f),
            Triple("Qora (Solid)", 570f, 740f),
            Triple("Gradient", 750f, 930f)
        )
        modes.forEach { (label, l, r) ->
            val isSel = when (label) {
                "Passthrough (Kamera)" -> settings.backgroundMode == "passthrough"
                "Qora (Solid)" -> settings.backgroundMode == "solid"
                else -> settings.backgroundMode == "gradient"
            }
            renderPill(canvas, l, 110f, r, 160f, label, isSel)
        }

        // 2. Orqa fon shaffofligi (Opacity)
        paint.color = Color.parseColor("#A0A0A5")
        canvas.drawText("Orqa fon shaffofligi (Tint opacity)", 390f, 200f, paint)
        val opacities = listOf(
            Pair("0%", 0.0f),
            Pair("25%", 0.25f),
            Pair("50%", 0.50f),
            Pair("75%", 0.75f),
            Pair("100%", 1.0f)
        )
        val opX = listOf(390f, 480f, 570f, 660f, 750f)
        for (i in opacities.indices) {
            val (label, v) = opacities[i]
            val l = opX[i]
            val r = l + 80f
            val isSel = Math.abs(settings.backgroundOpacity - v) < 0.05f
            renderPill(canvas, l, 210f, r, 260f, label, isSel)
        }

        // 3. Shisha xiraligi / Reduce Transparency Toggle
        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("Shaffoflikni kamaytirish (Reduce Transparency)", 390f, 330f, paint)
        renderSwitch(canvas, 1090f, 310f, settings.reduceTransparency)

        // 4. Shisha Blur Radiusi
        paint.color = Color.parseColor("#A0A0A5")
        paint.textSize = 20f
        canvas.drawText("Shisha blur kuchi", 390f, 380f, paint)
        renderPill(canvas, 390f, 390f, 510f, 440f, "Off (0px)", settings.glassBlurRadius == 0.0f)
        renderPill(canvas, 520f, 390f, 640f, 440f, "O'rta (16px)", settings.glassBlurRadius == 16.0f)
        renderPill(canvas, 650f, 390f, 790f, 440f, "Yuqori (32px)", settings.glassBlurRadius == 32.0f)

        // 5. Ekran Yorug'ligi
        canvas.drawText("Ekran yorqinligi", 390f, 470f, paint)
        renderPill(canvas, 390f, 480f, 510f, 530f, "50%", settings.displayBrightness == 0.5f)
        renderPill(canvas, 520f, 390f + 90f, 640f, 530f, "75%", settings.displayBrightness == 0.75f)
        renderPill(canvas, 650f, 390f + 90f, 790f, 530f, "100%", settings.displayBrightness == 1.0f)
    }

    private fun renderGesturesDetails(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Hand glow toggle
        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("Qo'l neon yoritilishi (Hand Glow)", 390f, 135f, paint)
        renderSwitch(canvas, 1090f, 115f, settings.handGlowEnabled)

        // 2. Pinch Sezgirligi
        paint.color = Color.parseColor("#A0A0A5")
        paint.textSize = 20f
        canvas.drawText("Barmoq chimchilash (Pinch) sezgirligi", 390f, 190f, paint)
        renderPill(canvas, 390f, 200f, 510f, 250f, "Yuqori", settings.pinchThresholdEnter > 0.06f)
        renderPill(canvas, 520f, 200f, 640f, 250f, "Normal", settings.pinchThresholdEnter in 0.045f..0.06f)
        renderPill(canvas, 650f, 200f, 790f, 250f, "Past", settings.pinchThresholdEnter < 0.045f)

        // 3. Gest shortkutlari jadvali
        paint.color = Color.parseColor("#A0A0A5")
        canvas.drawText("Faol fazoviy gestlar birikmasi", 390f, 290f, paint)

        val shortcuts = listOf(
            Pair("🖐 Kaft yuqoriga (Palm-Up)", "🏠 Home panelni ochish"),
            Pair("✊ Musht (Fist)", "✕ Oynani yopish"),
            Pair("👈 Swipe Chapga", "⏩ Keyingi oyna"),
            Pair("👉 Swipe O'ngga", "⏪ Oldingi oyna")
        )
        var rowY = 310f
        for ((gest, act) in shortcuts) {
            val card = RectF(390f, rowY, 1180f, rowY + 38f)
            paint.color = Color.parseColor("#282932")
            canvas.drawRoundRect(card, 10f, 10f, paint)

            paint.color = Color.WHITE
            paint.textSize = 19f
            canvas.drawText(gest, 410f, rowY + 26f, paint)

            paint.color = Color.parseColor("#64D2FF")
            canvas.drawText(act, 880f, rowY + 26f, paint)
            rowY += 44f
        }

        // 4. O'rgatish rejimi tugmasi
        val learnRect = RectF(390f, 490f, 690f, 550f)
        paint.color = Color.parseColor("#0A84FF")
        canvas.drawRoundRect(learnRect, 18f, 18f, paint)
        paint.color = Color.WHITE
        paint.textSize = 21f
        paint.isFakeBoldText = true
        canvas.drawText("🎯 Yangi gest o'rgatish", 420f, 527f, paint)
        paint.isFakeBoldText = false
    }

    private fun renderWindowsDetails(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.parseColor("#A0A0A5")
        paint.textSize = 20f
        canvas.drawText("Maksimal bir vaqtda ochiq oynalar soni", 390f, 100f, paint)
        renderPill(canvas, 390f, 110f, 490f, 160f, "3 ta", settings.maxOpenWindows == 3)
        renderPill(canvas, 500f, 110f, 610f, 160f, "6 ta", settings.maxOpenWindows == 6)
        renderPill(canvas, 620f, 110f, 750f, 160f, "8 ta", settings.maxOpenWindows == 8)

        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("Nofaol oynalarni avtomatik uxlatish (Sleep Policy)", 390f, 235f, paint)
        renderSwitch(canvas, 1090f, 215f, settings.sleepInactiveWindows)
    }

    private fun renderPerformanceDetails(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.parseColor("#A0A0A5")
        paint.textSize = 20f
        canvas.drawText("Samaradorlik profili", 390f, 100f, paint)
        renderPill(canvas, 390f, 110f, 510f, 160f, "Avto (Smart)", settings.performanceProfile == "auto")
        renderPill(canvas, 520f, 110f, 640f, 160f, "Yuqori 60 FPS", settings.performanceProfile == "high")
        renderPill(canvas, 650f, 110f, 790f, 160f, "Batareya tejash", settings.performanceProfile == "low")

        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("Qurilma qizishidan himoya qilish (Thermal Throttling)", 390f, 235f, paint)
        renderSwitch(canvas, 1090f, 215f, settings.thermalProtectionEnabled)
    }

    private fun renderAudioDetails(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.parseColor("#A0A0A5")
        paint.textSize = 20f
        canvas.drawText("Asosiy ovoz balandligi", 390f, 100f, paint)
        renderPill(canvas, 390f, 110f, 470f, 160f, "Mute", settings.masterVolume == 0.0f)
        renderPill(canvas, 480f, 110f, 560f, 160f, "50%", settings.masterVolume == 0.5f)
        renderPill(canvas, 570f, 110f, 660f, 160f, "75%", settings.masterVolume == 0.75f)
        renderPill(canvas, 670f, 110f, 790f, 160f, "100%", settings.masterVolume == 1.0f)

        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("Fazoviy audio (Spatial Audio)", 390f, 235f, paint)
        renderSwitch(canvas, 1090f, 215f, settings.spatialAudioEnabled)
    }

    private fun renderCameraDetails(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("Past kechikish rejimi (EIS Off, Zero-Copy OES)", 390f, 135f, paint)
        renderSwitch(canvas, 1090f, 115f, settings.lowLatencyCameraMode)

        paint.color = Color.parseColor("#A0A0A5")
        paint.textSize = 20f
        canvas.drawText("Maqsadli kadr tezligi (Target FPS)", 390f, 200f, paint)
        renderPill(canvas, 390f, 210f, 500f, 260f, "60 FPS", settings.cameraFpsTarget == 60)
        renderPill(canvas, 510f, 210f, 650f, 260f, "30 FPS", settings.cameraFpsTarget == 30)
    }

    private fun renderSandboxDetails(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("Tashqi havolalar faqat ichki brauzerda ochiladi", 390f, 90f, paint)

        // Clear Cookies Button
        val clearBtn = RectF(390f, 120f, 740f, 180f)
        paint.color = Color.parseColor("#44FF3B30")
        canvas.drawRoundRect(clearBtn, 16f, 16f, paint)
        paint.color = Color.parseColor("#FF453A")
        paint.textSize = 21f
        paint.isFakeBoldText = true
        canvas.drawText("🧹 Barcha sessiyalarni tozalash", 415f, 157f, paint)

        // Export Settings Button
        val exportBtn = RectF(390f, 210f, 740f, 270f)
        paint.color = Color.parseColor("#332C2C30")
        canvas.drawRoundRect(exportBtn, 16f, 16f, paint)
        paint.color = Color.parseColor("#64D2FF")
        canvas.drawText("📤 Sozlamalarni eksport qilish", 415f, 247f, paint)
        paint.isFakeBoldText = false
    }

    private fun renderAboutDetails(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.WHITE
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText("AR Port — Spatial Headset OS", 390f, 115f, paint)
        paint.isFakeBoldText = false

        paint.color = Color.parseColor("#8E8E93")
        paint.textSize = 20f
        canvas.drawText("Versiya: 1.0.0 (Release Build 1) • minSdk 29 • targetSdk 35", 390f, 155f, paint)
        canvas.drawText("Dvigatel: OpenGL ES 3.0, CameraX OES Zero-Copy, MediaPipe Vision", 390f, 195f, paint)
        canvas.drawText("Paket: uz.orbitx.arport • Litsenziya: MIT / Google DeepMind Pair", 390f, 235f, paint)

        // Reset to Defaults Button
        val resetBtn = RectF(390f, 490f, 690f, 550f)
        paint.color = Color.parseColor("#33FF3B30")
        canvas.drawRoundRect(resetBtn, 18f, 18f, paint)
        paint.color = Color.parseColor("#FF453A")
        paint.textSize = 21f
        paint.isFakeBoldText = true
        canvas.drawText("🔄 Zavod holatiga qaytarish", 415f, 527f, paint)
        paint.isFakeBoldText = false
    }

    private fun renderPill(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, text: String, isSelected: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(l, t, r, b)
        paint.color = if (isSelected) Color.parseColor("#0A84FF") else Color.parseColor("#282932")
        canvas.drawRoundRect(rect, 14f, 14f, paint)

        paint.color = if (isSelected) Color.WHITE else Color.parseColor("#8E8E93")
        paint.textSize = 19f
        paint.isFakeBoldText = isSelected
        val textW = paint.measureText(text)
        canvas.drawText(text, l + (r - l - textW) / 2f, t + (b - t + 14f) / 2f, paint)
    }

    private fun renderSwitch(canvas: Canvas, cx: Float, cy: Float, isChecked: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(cx, cy, cx + 70f, cy + 40f)
        paint.color = if (isChecked) Color.parseColor("#30D158") else Color.parseColor("#3A3A3C")
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        val knobX = if (isChecked) cx + 50f else cx + 20f
        paint.color = Color.WHITE
        canvas.drawCircle(knobX, cy + 20f, 16f, paint)
    }

    private fun renderLearningDialog(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Scrim
        paint.color = Color.parseColor("#99000000")
        canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), paint)

        // Modal Box
        val modalRect = RectF(380f, 180f, 900f, 540f)
        paint.color = Color.parseColor("#EE1C1C1E")
        canvas.drawRoundRect(modalRect, 28f, 28f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#64D2FF")
        canvas.drawRoundRect(modalRect, 28f, 28f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.isFakeBoldText = true
        canvas.drawText("🎯 Yangi Gest O'rgatish Rejimi", 450f, 240f, paint)
        paint.isFakeBoldText = false

        paint.color = Color.parseColor("#E0E0E0")
        paint.textSize = 20f
        canvas.drawText("Kameraga qarab qo'lingizni ko'taring.", 480f, 300f, paint)
        canvas.drawText("Kaftingizni yuqoriga qaratib 1 soniya ushlab turing.", 430f, 340f, paint)
        canvas.drawText("Ushbu gest avtomatik 'Home Dock' ga biriktiriladi.", 440f, 380f, paint)

        // OK Button
        val okRect = RectF(520f, 440f, 760f, 500f)
        paint.color = Color.parseColor("#0A84FF")
        canvas.drawRoundRect(okRect, 18f, 18f, paint)
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("Saqlash va Yopish", 555f, 477f, paint)
    }

    private fun renderToastIfNeeded(canvas: Canvas) {
        val msg = toastMessage ?: return
        if (System.currentTimeMillis() > toastExpireMs) {
            toastMessage = null
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val toastRect = RectF(400f, 630f, 880f, 680f)
        paint.color = Color.parseColor("#E62C2C2E")
        canvas.drawRoundRect(toastRect, 18f, 18f, paint)

        paint.color = Color.parseColor("#64D2FF")
        paint.textSize = 20f
        paint.isFakeBoldText = true
        val textW = paint.measureText(msg)
        canvas.drawText(msg, toastRect.left + (toastRect.width() - textW) / 2f, 663f, paint)
    }
}
