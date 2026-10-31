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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Spatial Clock Application (TZ 11, QADAM 14).
 * Includes World Clock, Alarms with iOS switches, Stopwatch with lap splits,
 * and circular countdown Timer.
 */
class ClockApp(
    override val manifest: AppManifest
) : ARApp {

    override val id: String = manifest.id

    enum class Tab {
        WORLD, ALARM, STOPWATCH, TIMER
    }

    data class WorldCity(
        val name: String,
        val country: String,
        val timeZoneId: String,
        val offsetLabel: String
    )

    data class AlarmItem(
        val id: String,
        var timeStr: String,
        var label: String,
        var days: String,
        var isEnabled: Boolean
    )

    data class LapSplit(
        val lapNumber: Int,
        val lapTimeMs: Long,
        val totalTimeMs: Long
    )

    var currentTab: Tab = Tab.WORLD

    // 1. World Cities
    val worldCities = listOf(
        WorldCity("Toshkent", "O'zbekiston", "Asia/Tashkent", "Mahalliy (UTC+5)"),
        WorldCity("London", "Buyuk Britaniya", "Europe/London", "-5 soat (UTC+0)"),
        WorldCity("Nyu-York", "AQSH", "America/New_York", "-9 soat (UTC-4)"),
        WorldCity("Tokio", "Yaponiya", "Asia/Tokyo", "+4 soat (UTC+9)")
    )

    // 2. Alarms
    val alarms = mutableListOf(
        AlarmItem("1", "06:30", "Ertalabki mashq", "Har kuni", true),
        AlarmItem("2", "07:15", "Nonushta va tayyorgarlik", "Dush - Juma", true),
        AlarmItem("3", "13:00", "Tushlik vaqti", "Har kuni", false),
        AlarmItem("4", "22:30", "Kitob o'qish / Uyqu", "Har kuni", true)
    )

    // 3. Stopwatch State
    var isStopwatchRunning: Boolean = false
    var stopwatchElapsedMs: Long = 0L
    var stopwatchLastStartMs: Long = 0L
    val lapSplits = mutableListOf<LapSplit>()

    // 4. Timer State
    var isTimerRunning: Boolean = false
    var timerDurationMs: Long = 5 * 60 * 1000L // default 5 min
    var timerRemainingMs: Long = 5 * 60 * 1000L
    var timerLastStartMs: Long = 0L
    var timerFinished: Boolean = false

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
        render()
        return surf
    }

    fun startStopwatch() {
        if (!isStopwatchRunning) {
            isStopwatchRunning = true
            stopwatchLastStartMs = System.currentTimeMillis()
        } else {
            isStopwatchRunning = false
            stopwatchElapsedMs += (System.currentTimeMillis() - stopwatchLastStartMs)
        }
        render()
    }

    fun lapStopwatch() {
        if (isStopwatchRunning || stopwatchElapsedMs > 0) {
            val total = getStopwatchCurrentMs()
            val lastTotal = lapSplits.firstOrNull()?.totalTimeMs ?: 0L
            val lapTime = (total - lastTotal).coerceAtLeast(0L)
            lapSplits.add(0, LapSplit(lapSplits.size + 1, lapTime, total))
            render()
        }
    }

    fun resetStopwatch() {
        isStopwatchRunning = false
        stopwatchElapsedMs = 0L
        lapSplits.clear()
        render()
    }

    fun getStopwatchCurrentMs(): Long {
        return if (isStopwatchRunning) {
            stopwatchElapsedMs + (System.currentTimeMillis() - stopwatchLastStartMs)
        } else {
            stopwatchElapsedMs
        }
    }

    fun startTimer() {
        if (timerFinished) {
            timerFinished = false
            timerRemainingMs = timerDurationMs
        }
        if (!isTimerRunning) {
            if (timerRemainingMs <= 0) timerRemainingMs = timerDurationMs
            isTimerRunning = true
            timerLastStartMs = System.currentTimeMillis()
        } else {
            isTimerRunning = false
            val elapsed = System.currentTimeMillis() - timerLastStartMs
            timerRemainingMs = (timerRemainingMs - elapsed).coerceAtLeast(0L)
        }
        render()
    }

    fun resetTimer() {
        isTimerRunning = false
        timerFinished = false
        timerRemainingMs = timerDurationMs
        render()
    }

    fun setTimerPreset(minutes: Int) {
        isTimerRunning = false
        timerFinished = false
        timerDurationMs = minutes * 60 * 1000L
        timerRemainingMs = timerDurationMs
        render()
    }

    fun tick(currentTimeMs: Long = System.currentTimeMillis()) {
        var needsRender = false
        if (isStopwatchRunning) {
            needsRender = true
        }
        if (isTimerRunning) {
            val elapsed = currentTimeMs - timerLastStartMs
            val rem = (timerRemainingMs - elapsed)
            if (rem <= 0) {
                isTimerRunning = false
                timerRemainingMs = 0
                timerFinished = true
            }
            needsRender = true
        }
        if (needsRender || currentTab == Tab.WORLD) {
            render()
        }
    }

    override fun onPointerEvent(u: Float, v: Float, action: Int) {
        if (action != MotionEvent.ACTION_UP) return

        val px = u * surfaceWidth
        val py = v * surfaceHeight

        // 1. Top Tab Bar (y=30..80, x=340..940)
        if (py in 30f..80f && px in 340f..940f) {
            if (px in 340f..480f) { currentTab = Tab.WORLD; render(); return }
            if (px in 490f..630f) { currentTab = Tab.ALARM; render(); return }
            if (px in 640f..790f) { currentTab = Tab.STOPWATCH; render(); return }
            if (px in 800f..940f) { currentTab = Tab.TIMER; render(); return }
        }

        when (currentTab) {
            Tab.WORLD -> {}
            Tab.ALARM -> handleAlarmPointer(px, py)
            Tab.STOPWATCH -> handleStopwatchPointer(px, py)
            Tab.TIMER -> handleTimerPointer(px, py)
        }
    }

    private fun handleAlarmPointer(px: Float, py: Float) {
        // Alarms toggle click detection
        var startY = 120f
        for (alarm in alarms) {
            val cardBottom = startY + 105f
            if (py in startY..cardBottom) {
                // Toggle button area (x=1120..1220)
                if (px in 1100f..1230f) {
                    alarm.isEnabled = !alarm.isEnabled
                    render()
                    return
                }
            }
            startY += 125f
        }
    }

    private fun handleStopwatchPointer(px: Float, py: Float) {
        // Start/Stop button (x=460..580, y=360..480)
        if (px in 450f..590f && py in 350f..490f) {
            startStopwatch()
            return
        }
        // Lap button (x=620..740, y=360..480)
        if (px in 610f..750f && py in 350f..490f) {
            lapStopwatch()
            return
        }
        // Reset button (x=780..900, y=360..480)
        if (px in 770f..910f && py in 350f..490f) {
            resetStopwatch()
            return
        }
    }

    private fun handleTimerPointer(px: Float, py: Float) {
        // Preset Pills (y=110..160, x=340..940)
        if (py in 110f..160f) {
            if (px in 340f..440f) { setTimerPreset(1); return }
            if (px in 450f..550f) { setTimerPreset(5); return }
            if (px in 560f..660f) { setTimerPreset(10); return }
            if (px in 670f..810f) { setTimerPreset(25); return }
            if (px in 820f..940f) { setTimerPreset(60); return }
        }

        // Timer Start/Pause button (x=500..620, y=560..640)
        if (px in 490f..630f && py in 550f..650f) {
            startTimer()
            return
        }
        // Timer Reset button (x=660..780, y=560..640)
        if (px in 650f..790f && py in 550f..650f) {
            resetTimer()
            return
        }
    }

    fun render() {
        surface?.draw { canvas ->
            canvas.drawColor(Color.parseColor("#E6131418"))

            // Render Top Tab Navigation Bar
            renderTabBar(canvas)

            when (currentTab) {
                Tab.WORLD -> renderWorldTab(canvas)
                Tab.ALARM -> renderAlarmTab(canvas)
                Tab.STOPWATCH -> renderStopwatchTab(canvas)
                Tab.TIMER -> renderTimerTab(canvas)
            }
        }
    }

    private fun renderTabBar(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pillBg = RectF(340f, 30f, 940f, 80f)
        paint.color = Color.parseColor("#332C2C30")
        canvas.drawRoundRect(pillBg, 25f, 25f, paint)

        val tabs = listOf(
            Triple("🌐 Dunyo", 345f, 480f),
            Triple("⏰ Budilnik", 490f, 630f),
            Triple("⏱ Sekundomer", 640f, 790f),
            Triple("⏳ Taymer", 800f, 935f)
        )

        tabs.forEachIndexed { index, (label, left, right) ->
            val isSelected = when (index) {
                0 -> currentTab == Tab.WORLD
                1 -> currentTab == Tab.ALARM
                2 -> currentTab == Tab.STOPWATCH
                else -> currentTab == Tab.TIMER
            }
            if (isSelected) {
                paint.color = Color.parseColor("#0A84FF")
                canvas.drawRoundRect(RectF(left, 33f, right, 77f), 22f, 22f, paint)
            }
            paint.color = if (isSelected) Color.WHITE else Color.parseColor("#8E8E93")
            paint.textSize = 21f
            paint.isFakeBoldText = isSelected
            val textW = paint.measureText(label)
            canvas.drawText(label, left + (right - left - textW) / 2f, 63f, paint)
        }
        paint.isFakeBoldText = false
    }

    private fun renderWorldTab(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val now = Date()

        // 4 Cities 2x2 Grid:
        // Top row: y=120..380. Left: x=60..620, Right: x=660..1220
        // Bottom row: y=410..670. Left: x=60..620, Right: x=660..1220
        val positions = listOf(
            RectF(60f, 120f, 620f, 380f),
            RectF(660f, 120f, 1220f, 380f),
            RectF(60f, 410f, 620f, 670f),
            RectF(660f, 410f, 1220f, 670f)
        )

        for (i in worldCities.indices) {
            val city = worldCities[i]
            val rect = positions[i]

            paint.color = Color.parseColor("#24252C")
            canvas.drawRoundRect(rect, 24f, 24f, paint)

            val tz = TimeZone.getTimeZone(city.timeZoneId)
            val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = tz }
            val sdfDate = SimpleDateFormat("EEE, dd MMM", Locale.US).apply { timeZone = tz }

            // City name & country
            paint.color = Color.WHITE
            paint.textSize = 32f
            paint.isFakeBoldText = true
            canvas.drawText(city.name, rect.left + 35f, rect.top + 55f, paint)

            paint.color = Color.parseColor("#8E8E93")
            paint.textSize = 20f
            paint.isFakeBoldText = false
            canvas.drawText("${city.country} • ${city.offsetLabel}", rect.left + 35f, rect.top + 90f, paint)

            // Big Digital Time
            paint.color = Color.parseColor("#64D2FF")
            paint.textSize = 54f
            paint.isFakeBoldText = true
            canvas.drawText(sdfTime.format(now), rect.left + 35f, rect.top + 175f, paint)

            paint.color = Color.parseColor("#A0A0A5")
            paint.textSize = 22f
            paint.isFakeBoldText = false
            canvas.drawText(sdfDate.format(now), rect.left + 35f, rect.top + 220f, paint)

            // Analog Clock Face (Right side of card)
            renderMiniAnalogClock(canvas, rect.right - 100f, rect.centerY(), 70f, tz, now)
        }
    }

    private fun renderMiniAnalogClock(canvas: Canvas, cx: Float, cy: Float, radius: Float, tz: TimeZone, now: Date) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Face Dial
        paint.color = Color.parseColor("#18191E")
        canvas.drawCircle(cx, cy, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#44FFFFFF")
        canvas.drawCircle(cx, cy, radius, paint)

        // Calendar parse hours, mins, secs
        val cal = java.util.Calendar.getInstance(tz).apply { time = now }
        val hours = cal.get(java.util.Calendar.HOUR)
        val mins = cal.get(java.util.Calendar.MINUTE)
        val secs = cal.get(java.util.Calendar.SECOND)

        // Hour Hand
        val hrAngle = Math.toRadians(((hours + mins / 60f) * 30.0) - 90.0)
        paint.color = Color.WHITE
        paint.strokeWidth = 4f
        val hrLen = radius * 0.5f
        canvas.drawLine(cx, cy, (cx + hrLen * cos(hrAngle)).toFloat(), (cy + hrLen * sin(hrAngle)).toFloat(), paint)

        // Minute Hand
        val minAngle = Math.toRadians((mins * 6.0) - 90.0)
        paint.strokeWidth = 3f
        val minLen = radius * 0.75f
        canvas.drawLine(cx, cy, (cx + minLen * cos(minAngle)).toFloat(), (cy + minLen * sin(minAngle)).toFloat(), paint)

        // Second Hand (Orange)
        val secAngle = Math.toRadians((secs * 6.0) - 90.0)
        paint.color = Color.parseColor("#FF9F0A")
        paint.strokeWidth = 2f
        val secLen = radius * 0.85f
        canvas.drawLine(cx, cy, (cx + secLen * cos(secAngle)).toFloat(), (cy + secLen * sin(secAngle)).toFloat(), paint)

        // Center Cap
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 4f, paint)
    }

    private fun renderAlarmTab(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Alarms list
        var startY = 120f
        for (alarm in alarms) {
            val cardRect = RectF(60f, startY, 1220f, startY + 105f)
            paint.color = Color.parseColor("#24252C")
            canvas.drawRoundRect(cardRect, 20f, 20f, paint)

            // Alarm time
            paint.color = if (alarm.isEnabled) Color.WHITE else Color.parseColor("#636366")
            paint.textSize = 52f
            paint.isFakeBoldText = true
            canvas.drawText(alarm.timeStr, 90f, startY + 68f, paint)

            // Label and days
            paint.color = if (alarm.isEnabled) Color.parseColor("#A0A0A5") else Color.parseColor("#505055")
            paint.textSize = 22f
            paint.isFakeBoldText = false
            canvas.drawText("${alarm.label} • ${alarm.days}", 280f, startY + 62f, paint)

            // Switch Toggle (x=1120..1190, y=startY+32..startY+72)
            val switchRect = RectF(1120f, startY + 32f, 1190f, startY + 72f)
            paint.color = if (alarm.isEnabled) Color.parseColor("#30D158") else Color.parseColor("#3A3A3C")
            canvas.drawRoundRect(switchRect, 20f, 20f, paint)

            // Knob
            val knobX = if (alarm.isEnabled) 1170f else 1140f
            paint.color = Color.WHITE
            canvas.drawCircle(knobX, startY + 52f, 16f, paint)

            startY += 125f
        }
    }

    private fun renderStopwatchTab(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val curMs = getStopwatchCurrentMs()

        val mins = (curMs / 60000)
        val secs = (curMs % 60000) / 1000
        val hundredths = (curMs % 1000) / 10

        // Big Stopwatch Display
        val timeStr = String.format(Locale.US, "%02d:%02d.%02d", mins, secs, hundredths)
        paint.color = Color.parseColor("#64D2FF")
        paint.textSize = 100f
        paint.isFakeBoldText = true
        val textW = paint.measureText(timeStr)
        canvas.drawText(timeStr, (surfaceWidth - textW) / 2f, 230f, paint)
        paint.isFakeBoldText = false

        // Buttons row (y=320..420)
        // 1. Start/Stop button (Circle at cx=520, cy=370, r=50)
        val startColor = if (isStopwatchRunning) Color.parseColor("#FF453A") else Color.parseColor("#30D158")
        paint.color = startColor
        canvas.drawCircle(520f, 370f, 50f, paint)
        paint.color = Color.BLACK
        paint.textSize = 22f
        paint.isFakeBoldText = true
        val startLabel = if (isStopwatchRunning) "Stop" else "Start"
        canvas.drawText(startLabel, 495f, 378f, paint)

        // 2. Lap button (Circle at cx=680, cy=370, r=50)
        paint.color = Color.parseColor("#2C2C30")
        canvas.drawCircle(680f, 370f, 50f, paint)
        paint.color = Color.WHITE
        canvas.drawText("Lap", 662f, 378f, paint)

        // 3. Reset button (Circle at cx=840, cy=370, r=50)
        paint.color = Color.parseColor("#2C2C30")
        canvas.drawCircle(840f, 370f, 50f, paint)
        paint.color = Color.parseColor("#A0A0A5")
        canvas.drawText("Reset", 815f, 378f, paint)
        paint.isFakeBoldText = false

        // Lap Splits Table (y=470..680)
        if (lapSplits.isNotEmpty()) {
            var startY = 470f
            for (lap in lapSplits.take(4)) {
                val cardRect = RectF(340f, startY, 940f, startY + 45f)
                paint.color = Color.parseColor("#202127")
                canvas.drawRoundRect(cardRect, 12f, 12f, paint)

                val lMins = (lap.lapTimeMs / 60000)
                val lSecs = (lap.lapTimeMs % 60000) / 1000
                val lHund = (lap.lapTimeMs % 1000) / 10
                val lapStr = String.format(Locale.US, "%02d:%02d.%02d", lMins, lSecs, lHund)

                paint.color = Color.WHITE
                paint.textSize = 22f
                canvas.drawText("Davra ${lap.lapNumber}", 370f, startY + 31f, paint)

                paint.color = Color.parseColor("#30D158")
                canvas.drawText(lapStr, 800f, startY + 31f, paint)

                startY += 52f
            }
        }
    }

    private fun renderTimerTab(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Presets row (y=110..160)
        val presets = listOf(
            Pair("1 daq", 1),
            Pair("5 daq", 5),
            Pair("10 daq", 10),
            Pair("25 daq", 25),
            Pair("60 daq", 60)
        )
        val btnPositions = listOf(
            RectF(340f, 110f, 440f, 160f),
            RectF(450f, 110f, 550f, 160f),
            RectF(560f, 110f, 660f, 160f),
            RectF(670f, 110f, 810f, 160f),
            RectF(820f, 110f, 940f, 160f)
        )
        for (i in presets.indices) {
            val (label, mins) = presets[i]
            val rect = btnPositions[i]
            val isSel = (timerDurationMs == mins * 60 * 1000L)
            paint.color = if (isSel) Color.parseColor("#0A84FF") else Color.parseColor("#25252A")
            canvas.drawRoundRect(rect, 16f, 16f, paint)
            paint.color = if (isSel) Color.WHITE else Color.parseColor("#8E8E93")
            paint.textSize = 20f
            paint.isFakeBoldText = isSel
            val textW = paint.measureText(label)
            canvas.drawText(label, rect.left + (rect.width() - textW) / 2f, 142f, paint)
        }
        paint.isFakeBoldText = false

        // Big Circular Progress Ring (cx=640, cy=350, r=140)
        val cx = 640f
        val cy = 350f
        val r = 140f

        // Background Ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 12f
        paint.color = Color.parseColor("#2C2C32")
        canvas.drawCircle(cx, cy, r, paint)

        // Progress Arc
        val progress = if (timerDurationMs > 0) {
            (timerRemainingMs.toFloat() / timerDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        paint.color = if (timerFinished) Color.parseColor("#FF453A") else Color.parseColor("#FF9F0A")
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(oval, -90f, progress * 360f, false, paint)
        paint.style = Paint.Style.FILL

        // Center Countdown Text
        val tSecsTotal = (timerRemainingMs / 1000).toInt()
        val tMins = tSecsTotal / 60
        val tSecs = tSecsTotal % 60
        val timerStr = if (timerFinished) "00:00" else String.format(Locale.US, "%02d:%02d", tMins, tSecs)

        paint.color = if (timerFinished) Color.parseColor("#FF453A") else Color.WHITE
        paint.textSize = 64f
        paint.isFakeBoldText = true
        val textW = paint.measureText(timerStr)
        canvas.drawText(timerStr, cx - textW / 2f, cy + 22f, paint)
        paint.isFakeBoldText = false

        if (timerFinished) {
            paint.color = Color.parseColor("#FF453A")
            paint.textSize = 22f
            val finStr = "⏰ Taymer yakunlandi!"
            val finW = paint.measureText(finStr)
            canvas.drawText(finStr, cx - finW / 2f, cy + 60f, paint)
        }

        // Action Buttons (y=560..630)
        // Start/Pause Button (x=500..620)
        val startRect = RectF(500f, 560f, 620f, 630f)
        paint.color = if (isTimerRunning) Color.parseColor("#FF9F0A") else Color.parseColor("#30D158")
        canvas.drawRoundRect(startRect, 20f, 20f, paint)
        paint.color = Color.BLACK
        paint.textSize = 22f
        paint.isFakeBoldText = true
        val btnLabel = if (isTimerRunning) "Pauza" else "Boshlash"
        val btnW = paint.measureText(btnLabel)
        canvas.drawText(btnLabel, startRect.left + (startRect.width() - btnW) / 2f, 603f, paint)

        // Reset Button (x=660..780)
        val resetRect = RectF(660f, 560f, 780f, 630f)
        paint.color = Color.parseColor("#2C2C32")
        canvas.drawRoundRect(resetRect, 20f, 20f, paint)
        paint.color = Color.WHITE
        val rLabel = "Bekor"
        val rW = paint.measureText(rLabel)
        canvas.drawText(rLabel, resetRect.left + (resetRect.width() - rW) / 2f, 603f, paint)
        paint.isFakeBoldText = false
    }
}
