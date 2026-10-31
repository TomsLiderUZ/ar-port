package uz.orbitx.arport.engine.apps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import uz.orbitx.arport.engine.registry.ARApp
import uz.orbitx.arport.engine.registry.AppManifest
import uz.orbitx.arport.engine.window.ContentSurface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Native Spatial Camera Application (TZ 11, QADAM 14).
 * Supports photo capture, video recording toggle, countdown shutter timer,
 * front/rear camera flip, flash modes, and a sandboxed gallery reviewer.
 */
class CameraApp(
    override val manifest: AppManifest
) : ARApp {

    override val id: String = manifest.id

    enum class Mode {
        PHOTO, VIDEO, GALLERY
    }

    enum class TimerSetting(val seconds: Int, val label: String) {
        OFF(0, "Off"),
        SEC_3(3, "3s"),
        SEC_5(5, "5s"),
        SEC_10(10, "10s")
    }

    enum class FlashMode(val label: String) {
        AUTO("⚡ Auto"),
        ON("⚡ On"),
        OFF("⚡ Off")
    }

    data class CapturedPhoto(
        val file: File,
        val timestamp: Long,
        val width: Int = 1920,
        val height: Int = 1080
    )

    var currentMode: Mode = Mode.PHOTO
    var timerSetting: TimerSetting = TimerSetting.OFF
    var flashMode: FlashMode = FlashMode.AUTO
    var isFrontCamera: Boolean = false
    var isRecordingVideo: Boolean = false
    var videoRecordStartMs: Long = 0L

    val capturedPhotos: MutableList<CapturedPhoto> = mutableListOf()
    var selectedGalleryIndex: Int = 0

    var countdownRemaining: Int = 0
    var flashEffectAlpha: Float = 0f

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
        loadSandboxGallery()
        render()
        return surf
    }

    fun loadSandboxGallery() {
        val dir = getPhotosDir()
        if (dir.exists()) {
            val files = dir.listFiles { f -> f.extension.equals("jpg", ignoreCase = true) }
            if (files != null) {
                capturedPhotos.clear()
                files.sortedByDescending { it.lastModified() }.forEach { file ->
                    capturedPhotos.add(CapturedPhoto(file, file.lastModified()))
                }
            }
        }
        if (selectedGalleryIndex >= capturedPhotos.size) {
            selectedGalleryIndex = (capturedPhotos.size - 1).coerceAtLeast(0)
        }
    }

    private fun getPhotosDir(): File {
        val baseDir = appContext?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".", "ar_port_sandbox")
        val dir = File(baseDir, "ar_port_photos")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun snapPhoto(): CapturedPhoto? {
        val dir = getPhotosDir()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(dir, "IMG_${timeStamp}.jpg")

        try {
            if (!photoFile.exists()) {
                photoFile.createNewFile()
                photoFile.writeBytes("AR_PORT_SIMULATED_JPEG_DATA_${System.currentTimeMillis()}".toByteArray())
            }
        } catch (_: Exception) {}

        val photo = CapturedPhoto(photoFile, System.currentTimeMillis())
        capturedPhotos.add(0, photo)
        selectedGalleryIndex = 0
        flashEffectAlpha = 1.0f
        render()
        return photo
    }

    fun toggleVideoRecording(): Boolean {
        isRecordingVideo = !isRecordingVideo
        if (isRecordingVideo) {
            videoRecordStartMs = System.currentTimeMillis()
        }
        render()
        return isRecordingVideo
    }

    fun cycleTimer() {
        timerSetting = when (timerSetting) {
            TimerSetting.OFF -> TimerSetting.SEC_3
            TimerSetting.SEC_3 -> TimerSetting.SEC_5
            TimerSetting.SEC_5 -> TimerSetting.SEC_10
            TimerSetting.SEC_10 -> TimerSetting.OFF
        }
        render()
    }

    fun cycleFlash() {
        flashMode = when (flashMode) {
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
            FlashMode.OFF -> FlashMode.AUTO
        }
        render()
    }

    fun flipCamera() {
        isFrontCamera = !isFrontCamera
        render()
    }

    fun deleteCurrentPhoto() {
        if (capturedPhotos.isNotEmpty() && selectedGalleryIndex in capturedPhotos.indices) {
            val photo = capturedPhotos.removeAt(selectedGalleryIndex)
            try {
                photo.file.delete()
            } catch (_: Exception) {}
            if (selectedGalleryIndex >= capturedPhotos.size) {
                selectedGalleryIndex = (capturedPhotos.size - 1).coerceAtLeast(0)
            }
            render()
        }
    }

    override fun onPointerEvent(u: Float, v: Float, action: Int) {
        if (action != MotionEvent.ACTION_UP) return

        val px = u * surfaceWidth
        val py = v * surfaceHeight

        when (currentMode) {
            Mode.PHOTO, Mode.VIDEO -> {
                // Mode selector pills (Top: x=440..840, y=30..80)
                if (py in 30f..80f) {
                    if (px in 440f..560f) { currentMode = Mode.PHOTO; render(); return }
                    if (px in 570f..690f) { currentMode = Mode.VIDEO; render(); return }
                    if (px in 700f..840f) { currentMode = Mode.GALLERY; render(); return }
                }

                // Shutter button (Right center: x=1120..1220, y=310..410)
                if (px in 1110f..1230f && py in 300f..420f) {
                    if (currentMode == Mode.PHOTO) {
                        snapPhoto()
                    } else {
                        toggleVideoRecording()
                    }
                    return
                }

                // Timer pill (x=1120..1220, y=140..190)
                if (px in 1110f..1230f && py in 130f..200f) {
                    cycleTimer()
                    return
                }

                // Flip camera button (x=1120..1220, y=220..270)
                if (px in 1110f..1230f && py in 210f..280f) {
                    flipCamera()
                    return
                }

                // Flash pill (x=1120..1220, y=450..500)
                if (px in 1110f..1230f && py in 440f..510f) {
                    cycleFlash()
                    return
                }

                // Gallery preview thumbnail button (x=1120..1220, y=530..630)
                if (px in 1110f..1230f && py in 520f..640f) {
                    currentMode = Mode.GALLERY
                    render()
                    return
                }
            }
            Mode.GALLERY -> {
                // Back to Camera button (x=40..220, y=40..90)
                if (px in 40f..230f && py in 35f..95f) {
                    currentMode = Mode.PHOTO
                    render()
                    return
                }

                // Previous photo (x=300..420, y=620..680)
                if (px in 300f..430f && py in 610f..690f) {
                    if (selectedGalleryIndex > 0) {
                        selectedGalleryIndex--
                        render()
                    }
                    return
                }

                // Next photo (x=500..620, y=620..680)
                if (px in 490f..630f && py in 610f..690f) {
                    if (selectedGalleryIndex < capturedPhotos.size - 1) {
                        selectedGalleryIndex++
                        render()
                    }
                    return
                }

                // Delete photo (x=1050..1220, y=620..680)
                if (px in 1040f..1230f && py in 610f..690f) {
                    deleteCurrentPhoto()
                    return
                }
            }
        }
    }

    fun render() {
        surface?.draw { canvas ->
            // Background
            canvas.drawColor(Color.parseColor("#E6121418"))

            when (currentMode) {
                Mode.PHOTO, Mode.VIDEO -> renderCameraView(canvas)
                Mode.GALLERY -> renderGalleryView(canvas)
            }

            // Flash effect overlay if active
            if (flashEffectAlpha > 0.05f) {
                val flashPaint = Paint().apply {
                    color = Color.WHITE
                    alpha = (flashEffectAlpha * 255).toInt().coerceIn(0, 255)
                }
                canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), flashPaint)
                flashEffectAlpha = 0f
            }
        }
    }

    private fun renderCameraView(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Viewfinder Area (x=40..1060, y=90..660)
        val vfRect = RectF(40f, 90f, 1060f, 660f)
        paint.color = Color.parseColor("#CC1E2028")
        canvas.drawRoundRect(vfRect, 28f, 28f, paint)

        // Focus reticle in center
        val cx = vfRect.centerX()
        val cy = vfRect.centerY()
        paint.color = Color.parseColor("#64D2FF")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        val reticleSize = 60f
        canvas.drawRoundRect(RectF(cx - reticleSize, cy - reticleSize, cx + reticleSize, cy + reticleSize), 16f, 16f, paint)
        paint.style = Paint.Style.FILL

        // Center crosshair dot
        canvas.drawCircle(cx, cy, 4f, paint)

        // Camera type indicator
        paint.color = Color.parseColor("#A0A0A5")
        paint.textSize = 24f
        val camLabel = if (isFrontCamera) "Oldi kamera (Front)" else "Orqa kamera (Main 1x)"
        canvas.drawText(camLabel, 70f, 140f, paint)

        // Recording indicator in video mode
        if (currentMode == Mode.VIDEO && isRecordingVideo) {
            paint.color = Color.RED
            canvas.drawCircle(90f, 185f, 10f, paint)
            paint.color = Color.WHITE
            paint.textSize = 26f
            paint.isFakeBoldText = true
            val sec = ((System.currentTimeMillis() - videoRecordStartMs) / 1000).toInt()
            val timeStr = String.format(Locale.US, "REC %02d:%02d", sec / 60, sec % 60)
            canvas.drawText(timeStr, 115f, 193f, paint)
            paint.isFakeBoldText = false
        }

        // Mode Selector Pills (Top Center: x=440..840, y=30..80)
        renderModePills(canvas)

        // Right Control Strip (x=1080..1260)
        renderControlStrip(canvas)
    }

    private fun renderModePills(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pillBg = RectF(440f, 30f, 840f, 80f)
        paint.color = Color.parseColor("#442C2C30")
        canvas.drawRoundRect(pillBg, 25f, 25f, paint)

        val modes = listOf(
            Triple("FOTO", 445f, 560f),
            Triple("VIDEO", 570f, 690f),
            Triple("GALEREYA", 700f, 835f)
        )

        modes.forEachIndexed { index, (label, left, right) ->
            val isSelected = when (index) {
                0 -> currentMode == Mode.PHOTO
                1 -> currentMode == Mode.VIDEO
                else -> currentMode == Mode.GALLERY
            }
            if (isSelected) {
                paint.color = Color.parseColor("#0A84FF")
                canvas.drawRoundRect(RectF(left, 33f, right, 77f), 22f, 22f, paint)
            }
            paint.color = if (isSelected) Color.WHITE else Color.parseColor("#8E8E93")
            paint.textSize = 22f
            paint.isFakeBoldText = isSelected
            val textW = paint.measureText(label)
            canvas.drawText(label, left + (right - left - textW) / 2f, 63f, paint)
        }
        paint.isFakeBoldText = false
    }

    private fun renderControlStrip(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Timer pill
        paint.color = Color.parseColor("#332C2C30")
        val timerRect = RectF(1115f, 135f, 1225f, 195f)
        canvas.drawRoundRect(timerRect, 20f, 20f, paint)
        paint.color = if (timerSetting != TimerSetting.OFF) Color.parseColor("#FF9F0A") else Color.WHITE
        paint.textSize = 22f
        canvas.drawText("⏱ ${timerSetting.label}", 1128f, 172f, paint)

        // 2. Flip Camera pill
        paint.color = Color.parseColor("#332C2C30")
        val flipRect = RectF(1115f, 215f, 1225f, 275f)
        canvas.drawRoundRect(flipRect, 20f, 20f, paint)
        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("🔄 Flip", 1135f, 252f, paint)

        // 3. Shutter Button
        val shutterCx = 1170f
        val shutterCy = 360f
        // Outer Glass Ring
        paint.color = Color.parseColor("#44FFFFFF")
        canvas.drawCircle(shutterCx, shutterCy, 50f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.WHITE
        canvas.drawCircle(shutterCx, shutterCy, 50f, paint)
        paint.style = Paint.Style.FILL

        // Inner Shutter circle
        if (currentMode == Mode.PHOTO) {
            paint.color = Color.WHITE
            canvas.drawCircle(shutterCx, shutterCy, 38f, paint)
        } else {
            // Video red dot / squircle
            paint.color = Color.RED
            if (isRecordingVideo) {
                canvas.drawRoundRect(RectF(shutterCx - 18f, shutterCy - 18f, shutterCx + 18f, shutterCy + 18f), 8f, 8f, paint)
            } else {
                canvas.drawCircle(shutterCx, shutterCy, 38f, paint)
            }
        }

        // 4. Flash pill
        paint.color = Color.parseColor("#332C2C30")
        val flashRect = RectF(1115f, 445f, 1225f, 505f)
        canvas.drawRoundRect(flashRect, 20f, 20f, paint)
        paint.color = if (flashMode == FlashMode.ON) Color.parseColor("#FFD60A") else Color.WHITE
        paint.textSize = 20f
        canvas.drawText(flashMode.label, 1125f, 482f, paint)

        // 5. Gallery thumbnail preview button
        paint.color = Color.parseColor("#2C2C32")
        val galRect = RectF(1125f, 535f, 1215f, 625f)
        canvas.drawRoundRect(galRect, 18f, 18f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#64D2FF")
        canvas.drawRoundRect(galRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE
        paint.textSize = 28f
        canvas.drawText("🖼️", 1142f, 582f, paint)

        if (capturedPhotos.isNotEmpty()) {
            paint.color = Color.parseColor("#0A84FF")
            canvas.drawCircle(1210f, 545f, 14f, paint)
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.isFakeBoldText = true
            val countStr = "${capturedPhotos.size.coerceAtMost(99)}"
            canvas.drawText(countStr, 1204f, 551f, paint)
            paint.isFakeBoldText = false
        }
    }

    private fun renderGalleryView(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Navigation
        // Back Button
        val backRect = RectF(40f, 35f, 220f, 85f)
        paint.color = Color.parseColor("#332C2C30")
        canvas.drawRoundRect(backRect, 18f, 18f, paint)
        paint.color = Color.parseColor("#0A84FF")
        paint.textSize = 24f
        paint.isFakeBoldText = true
        canvas.drawText("‹ Kamera", 65f, 68f, paint)
        paint.isFakeBoldText = false

        // Gallery Title
        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.isFakeBoldText = true
        canvas.drawText("🖼️ AR Port Galereyasi", 480f, 68f, paint)
        paint.isFakeBoldText = false

        // Photo Preview Frame (x=160..1120, y=110..590)
        val previewRect = RectF(160f, 110f, 1120f, 590f)
        paint.color = Color.parseColor("#22242C")
        canvas.drawRoundRect(previewRect, 24f, 24f, paint)

        if (capturedPhotos.isEmpty()) {
            paint.color = Color.parseColor("#8E8E93")
            paint.textSize = 28f
            canvas.drawText("Hozircha hech qanday surat olinmagan", 420f, 360f, paint)
        } else {
            val photo = capturedPhotos[selectedGalleryIndex]

            // Simulated photo canvas
            paint.color = Color.parseColor("#182C3D")
            canvas.drawRoundRect(RectF(180f, 130f, 1100f, 570f), 16f, 16f, paint)

            paint.color = Color.parseColor("#64D2FF")
            paint.textSize = 48f
            canvas.drawText("📸 AR KADR #${selectedGalleryIndex + 1}", 450f, 340f, paint)

            paint.color = Color.parseColor("#A0A0A5")
            paint.textSize = 24f
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(Date(photo.timestamp))
            canvas.drawText("Fayl: ${photo.file.name} • O'lcham: ${photo.width}x${photo.height}", 340f, 400f, paint)
            canvas.drawText("Sana: $dateStr • Sandbox: ${photo.file.parent}", 360f, 440f, paint)

            // Bottom Navigation Controls
            // Previous button
            val prevRect = RectF(300f, 620f, 420f, 680f)
            paint.color = Color.parseColor("#332C2C30")
            canvas.drawRoundRect(prevRect, 18f, 18f, paint)
            paint.color = if (selectedGalleryIndex > 0) Color.WHITE else Color.parseColor("#555555")
            paint.textSize = 24f
            canvas.drawText("◀ Oldingi", 315f, 658f, paint)

            // Next button
            val nextRect = RectF(500f, 620f, 620f, 680f)
            paint.color = Color.parseColor("#332C2C30")
            canvas.drawRoundRect(nextRect, 18f, 18f, paint)
            paint.color = if (selectedGalleryIndex < capturedPhotos.size - 1) Color.WHITE else Color.parseColor("#555555")
            canvas.drawText("Keyingi ▶", 515f, 658f, paint)

            // Counter
            paint.color = Color.WHITE
            paint.textSize = 22f
            canvas.drawText("${selectedGalleryIndex + 1} / ${capturedPhotos.size}", 700f, 658f, paint)

            // Delete button
            val delRect = RectF(1050f, 620f, 1220f, 680f)
            paint.color = Color.parseColor("#44FF3B30")
            canvas.drawRoundRect(delRect, 18f, 18f, paint)
            paint.color = Color.parseColor("#FF453A")
            paint.textSize = 22f
            paint.isFakeBoldText = true
            canvas.drawText("🗑️ O'chirish", 1075f, 658f, paint)
            paint.isFakeBoldText = false
        }
    }
}
