package uz.orbitx.arport.engine.window

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import uz.orbitx.arport.engine.math.Matrix4x4

/**
 * 3D Spatial Window Manager coordinating window layout, z-ordering,
 * anti-overlap positioning, focus management, frustum culling, and sleep policies.
 */
class WindowManager3D(
    var maxWindowsLimit: Int = 6
) {
    private val windows = mutableListOf<SpatialWindow>()
    private val lock = Any()
    private var nextZOrder = 1

    val activeWindows: List<SpatialWindow>
        get() = synchronized(lock) { windows.filter { it.state != WindowState.MINIMIZED } }

    val allWindows: List<SpatialWindow>
        get() = synchronized(lock) { windows.toList() }

    init {
        createDefaultTestWindows()
    }

    /**
     * Creates and positions a new spatial window with anti-overlap positioning.
     */
    fun createWindow(
        appId: String,
        title: String,
        width: Float = 1.0f,
        height: Float = 0.5625f,
        aspect: WindowAspect = WindowAspect.LOCKED_16_9
    ): SpatialWindow {
        synchronized(lock) {
            val window = SpatialWindow(
                appId = appId,
                title = title,
                widthMeters = width,
                heightMeters = height,
                aspect = aspect
            )

            // Anti-overlap positioning algorithm
            val targetX = calculateNonOverlappingX(width)
            window.setPosition(targetX, 0.0f, -1.6f)
            window.zOrder = nextZOrder++

            windows.add(window)
            enforceSleepPolicy()
            setFocus(window.id)
            return window
        }
    }

    /**
     * Finds a free horizontal position so windows do not overlap directly.
     */
    private fun calculateNonOverlappingX(newWidth: Float): Float {
        if (windows.isEmpty()) return 0.0f

        val occupiedPositions = windows.filter { it.state != WindowState.MINIMIZED }.map { it.posX }
        var candidateX = 0.0f
        var step = 0.75f
        var direction = 1

        while (occupiedPositions.any { kotlin.math.abs(it - candidateX) < (newWidth * 0.7f) }) {
            candidateX = direction * step
            if (direction > 0) {
                direction = -1
            } else {
                direction = 1
                step += 0.65f
            }
            if (step > 4.0f) break // Safe boundary
        }
        return candidateX
    }

    fun setFocus(windowId: String) {
        synchronized(lock) {
            for (w in windows) {
                val shouldFocus = (w.id == windowId)
                w.isFocused = shouldFocus
                if (shouldFocus) {
                    w.zOrder = nextZOrder++
                    w.isSleeping = false
                    // Tactile Apple-style spring bounce on focus
                    w.scaleSpring.snapTo(0.96f)
                }
            }
        }
    }

    fun closeWindow(windowId: String) {
        synchronized(lock) {
            val win = windows.find { it.id == windowId }
            win?.contentSurface?.release()
            windows.removeAll { it.id == windowId }

            // Shift focus to highest z-order remaining window
            windows.filter { it.state != WindowState.MINIMIZED }
                .maxByOrNull { it.zOrder }
                ?.let { setFocus(it.id) }
        }
    }

    fun minimizeWindow(windowId: String) {
        synchronized(lock) {
            val win = windows.find { it.id == windowId }
            win?.state = WindowState.MINIMIZED
            win?.isFocused = false
            win?.scaleSpring?.targetValue = 0.0f
        }
    }

    fun restoreWindow(windowId: String) {
        synchronized(lock) {
            val win = windows.find { it.id == windowId }
            win?.let {
                it.state = WindowState.NORMAL
                it.scaleSpring.targetValue = 1.0f
                setFocus(it.id)
            }
        }
    }

    /**
     * Enforces the maximum concurrent active window policy (TZ 7.2).
     * If active windows exceed limit, the oldest unfocused window is put to sleep.
     */
    fun enforceSleepPolicy() {
        synchronized(lock) {
            val active = windows.filter { it.state != WindowState.MINIMIZED }
            if (active.size > maxWindowsLimit) {
                val excessCount = active.size - maxWindowsLimit
                val candidates = active
                    .filter { !it.isFocused && !it.isSleeping }
                    .sortedBy { it.zOrder }

                for (i in 0 until excessCount.coerceAtMost(candidates.size)) {
                    candidates[i].isSleeping = true
                }
            }
        }
    }

    /**
     * Checks if a 3D window is visible inside the camera view frustum (Frustum Culling).
     */
    fun isWindowInFrustum(window: SpatialWindow, vpMatrix: FloatArray): Boolean {
        val clip = FloatArray(4)
        val pos = floatArrayOf(window.posX, window.posY, window.posZ, 1.0f)
        Matrix4x4.multiplyMV(clip, 0, vpMatrix, 0, pos, 0)

        val w = clip[3]
        if (w <= 0.01f) return false // Behind camera

        // Bounding margin in clip coordinates
        val margin = 1.35f * w
        return clip[0] >= -margin && clip[0] <= margin &&
               clip[1] >= -margin && clip[1] <= margin
    }

    /**
     * Performs a 3D raycast hit test across all windows (top z-order first).
     */
    fun raycast(rayOriginX: Float, rayOriginY: Float, rayOriginZ: Float,
                rayDirX: Float, rayDirY: Float, rayDirZ: Float): Pair<SpatialWindow, SpatialWindow.HitResult>? {
        synchronized(lock) {
            val sortedWindows = windows
                .filter { it.state != WindowState.MINIMIZED }
                .sortedByDescending { it.zOrder }

            for (w in sortedWindows) {
                val hit = w.hitTest(rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ)
                if (hit != null) {
                    return Pair(w, hit)
                }
            }
            return null
        }
    }

    /**
     * Pre-populates the 3 test windows specified in QADAM 8.
     */
    private fun createDefaultTestWindows() {
        // 1. Center Window: System Monitor (16:9)
        val win1 = SpatialWindow(
            appId = "system_monitor",
            title = "Tizim Monitori",
            posX = 0.0f, posY = 0.05f, posZ = -1.6f,
            widthMeters = 0.95f, heightMeters = 0.534f,
            aspect = WindowAspect.LOCKED_16_9
        ).apply {
            zOrder = 3
            isFocused = true
            contentSurface = ContentSurface(1280, 720)
        }
        // 2. Left Window: Spatial Notes (9:16 Portrait)
        val win2 = SpatialWindow(
            appId = "spatial_notes",
            title = "Fazoviy Eslatmalar",
            posX = -0.95f, posY = 0.0f, posZ = -1.7f,
            widthMeters = 0.45f, heightMeters = 0.80f,
            aspect = WindowAspect.LOCKED_9_16
        ).apply {
            zOrder = 2
            isFocused = false
            contentSurface = ContentSurface(720, 1280)
        }

        // 3. Right Window: Media Player (16:9)
        val win3 = SpatialWindow(
            appId = "media_player",
            title = "Media Pleer",
            posX = 0.95f, posY = 0.0f, posZ = -1.7f,
            widthMeters = 0.85f, heightMeters = 0.478f,
            aspect = WindowAspect.LOCKED_16_9
        ).apply {
            zOrder = 1
            isFocused = false
            contentSurface = ContentSurface(1280, 720)
        }

        try {
            renderSystemMonitorContent(win1)
            renderNotesContent(win2)
            renderMediaContent(win3)
        } catch (_: Exception) {
            // Safe fallback for host JVM tests
        }

        windows.addAll(listOf(win1, win2, win3))
        nextZOrder = 4
    }

    fun renderSystemMonitorContent(window: SpatialWindow, fps: Float = 60f, latency: Float = 14f) {
        window.contentSurface?.draw { canvas ->
            canvas.drawColor(Color.parseColor("#E6121418"))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                isFakeBoldText = true
            }
            canvas.drawText("AR Port • Tizim Monitori", 60f, 100f, paint)

            paint.textSize = 30f
            paint.isFakeBoldText = false
            paint.color = Color.parseColor("#8E8E93")
            canvas.drawText("OpenGL ES 3.0 • Passthrough Zero-Copy Pipeline", 60f, 150f, paint)

            // Status cards
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2C2C2E")
            }
            canvas.drawRoundRect(RectF(60f, 190f, 600f, 380f), 24f, 24f, cardPaint)
            paint.color = Color.parseColor("#30D158") // iOS Green
            paint.textSize = 52f
            paint.isFakeBoldText = true
            canvas.drawText("${fps.toInt()} FPS", 90f, 275f, paint)
            paint.textSize = 24f
            paint.color = Color.parseColor("#8E8E93")
            canvas.drawText("Kadrlar chastotasi", 90f, 330f, paint)

            canvas.drawRoundRect(RectF(640f, 190f, 1180f, 380f), 24f, 24f, cardPaint)
            paint.color = Color.parseColor("#0A84FF") // iOS Blue
            paint.textSize = 52f
            paint.isFakeBoldText = true
            canvas.drawText("${latency.toInt()} ms", 670f, 275f, paint)
            paint.textSize = 24f
            paint.color = Color.parseColor("#8E8E93")
            canvas.drawText("Kamera kechikishi (motion-to-frame)", 670f, 330f, paint)

            // Info bar
            canvas.drawRoundRect(RectF(60f, 420f, 1180f, 640f), 24f, 24f, cardPaint)
            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.isFakeBoldText = false
            canvas.drawText("• Datchik: 3DoF/6DoF kvaternion filtri (16ms bashorat)", 90f, 480f, paint)
            canvas.drawText("• Rejim: Fazoviy oyna menejeri (WindowManager3D)", 90f, 535f, paint)
            canvas.drawText("• Aspekt: 16:9 sukut bo'yicha qulflangan", 90f, 590f, paint)
        }
    }

    fun renderNotesContent(window: SpatialWindow) {
        window.contentSurface?.draw { canvas ->
            canvas.drawColor(Color.parseColor("#E618181A"))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 44f
                isFakeBoldText = true
            }
            canvas.drawText("Qaydlar", 50f, 100f, paint)

            paint.color = Color.parseColor("#FFD60A") // iOS Yellow note tint
            paint.textSize = 24f
            canvas.drawText("Bugungi vazifalar", 50f, 150f, paint)

            val notes = listOf(
                "1. AR Port stereo va linza tekshiruvi",
                "2. Fazoviy oynalarni joylashtirish",
                "3. MediaPipe qo'l kuzatuvini sinash",
                "4. Liquid glass animatsiyalari",
                "5. Web sandbox xavfsizligi"
            )

            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#252528")
            }

            var startY = 200f
            for (note in notes) {
                canvas.drawRoundRect(RectF(40f, startY, 680f, startY + 140f), 20f, 20f, cardPaint)
                paint.color = Color.WHITE
                paint.textSize = 26f
                paint.isFakeBoldText = false
                canvas.drawText(note, 65f, startY + 80f, paint)
                startY += 170f
            }
        }
    }

    fun renderMediaContent(window: SpatialWindow) {
        window.contentSurface?.draw { canvas ->
            canvas.drawColor(Color.parseColor("#E614161C"))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                isFakeBoldText = true
            }
            canvas.drawText("Media Pleer", 60f, 100f, paint)

            // Album Art placeholder
            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF2D55") // Apple Music Pink
            }
            canvas.drawRoundRect(RectF(60f, 150f, 400f, 490f), 24f, 24f, artPaint)
            paint.color = Color.WHITE
            paint.textSize = 60f
            canvas.drawText("🎵", 200f, 340f, paint)

            // Track details
            paint.textSize = 38f
            paint.isFakeBoldText = true
            canvas.drawText("Spatial Ambient Drift", 440f, 220f, paint)

            paint.textSize = 28f
            paint.isFakeBoldText = false
            paint.color = Color.parseColor("#8E8E93")
            canvas.drawText("OrbitX Spatial Sound Lab", 440f, 275f, paint)

            // Progress bar
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#3A3A3C")
            }
            canvas.drawRoundRect(RectF(440f, 340f, 1180f, 355f), 8f, 8f, barPaint)

            barPaint.color = Color.parseColor("#FF2D55")
            canvas.drawRoundRect(RectF(440f, 340f, 800f, 355f), 8f, 8f, barPaint)

            paint.textSize = 22f
            canvas.drawText("02:14 / 04:32", 440f, 395f, paint)

            // Control buttons
            paint.textSize = 44f
            paint.color = Color.WHITE
            canvas.drawText("⏮   ▶   ⏭", 640f, 480f, paint)
        }
    }

    fun release() {
        synchronized(lock) {
            for (w in windows) {
                w.contentSurface?.release()
            }
            windows.clear()
        }
    }
}

