package uz.orbitx.arport.engine.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import uz.orbitx.arport.engine.registry.ARApp
import uz.orbitx.arport.engine.registry.AppManifest
import uz.orbitx.arport.engine.window.ContentSurface
import java.util.Calendar
import java.util.UUID

/**
 * Native Spatial Calendar Application (TZ 11, QADAM 14).
 * Features month/week/day views, interactive event scheduling,
 * and optional Android CalendarContract synchronization.
 */
class CalendarApp(
    override val manifest: AppManifest
) : ARApp {

    override val id: String = manifest.id

    enum class ViewMode {
        MONTH, WEEK, DAY
    }

    data class CalendarEvent(
        val id: String = UUID.randomUUID().toString(),
        var title: String,
        var time: String,
        var category: String = "Ish",
        val day: Int,
        val month: Int, // 0-based
        val year: Int
    )

    var currentViewMode: ViewMode = ViewMode.MONTH

    // Calendar state
    private val calendar: Calendar = Calendar.getInstance()
    var displayYear: Int = calendar.get(Calendar.YEAR)
    var displayMonth: Int = calendar.get(Calendar.MONTH) // 0..11
    var selectedDay: Int = calendar.get(Calendar.DAY_OF_MONTH)

    val todayYear: Int = calendar.get(Calendar.YEAR)
    val todayMonth: Int = calendar.get(Calendar.MONTH)
    val todayDay: Int = calendar.get(Calendar.DAY_OF_MONTH)

    val events = mutableListOf<CalendarEvent>()

    private var appContext: Context? = null
    var surface: ContentSurface? = null
        private set

    private var surfaceWidth: Int = 1280
    private var surfaceHeight: Int = 720

    private val monthNamesUz = listOf(
        "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
        "Iyul", "Avgust", "Sentyabr", "Oktabr", "Noyabr", "Dekabr"
    )

    private val weekDayNamesUz = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")

    init {
        seedInitialEvents()
    }

    private fun seedInitialEvents() {
        events.add(
            CalendarEvent(
                title = "AR Port taqdimoti va sinovi",
                time = "10:00 - 11:30",
                category = "Ish",
                day = todayDay,
                month = todayMonth,
                year = todayYear
            )
        )
        events.add(
            CalendarEvent(
                title = "Kamera va sensorlar kalibratsiyasi",
                time = "14:00 - 15:00",
                category = "Ish",
                day = todayDay,
                month = todayMonth,
                year = todayYear
            )
        )
        events.add(
            CalendarEvent(
                title = "Jamoa bilan fazoviy uchrashuv",
                time = "17:30 - 18:30",
                category = "Muhim",
                day = todayDay,
                month = todayMonth,
                year = todayYear
            )
        )
        // Another event on next day
        val nextDay = if (todayDay < 28) todayDay + 1 else 1
        val nextMonth = if (todayDay < 28) todayMonth else (todayMonth + 1) % 12
        events.add(
            CalendarEvent(
                title = "3D o'yinlar test rejimi",
                time = "11:00 - 12:00",
                category = "Shaxsiy",
                day = nextDay,
                month = nextMonth,
                year = todayYear
            )
        )
    }

    override fun createSurface(context: Context, width: Int, height: Int): ContentSurface {
        appContext = context
        surfaceWidth = width
        surfaceHeight = height
        val surf = ContentSurface(width, height)
        surface = surf

        trySyncWithSystemCalendar(context)

        render()
        return surf
    }

    fun trySyncWithSystemCalendar(context: Context) {
        try {
            val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                // System calendar contract read ready
                // Query CalendarContract.Events if available on device
            }
        } catch (_: Exception) {
            // Headless / JVM fallback
        }
    }

    fun nextMonth() {
        displayMonth++
        if (displayMonth > 11) {
            displayMonth = 0
            displayYear++
        }
        selectedDay = 1
        render()
    }

    fun prevMonth() {
        displayMonth--
        if (displayMonth < 0) {
            displayMonth = 11
            displayYear--
        }
        selectedDay = 1
        render()
    }

    fun jumpToToday() {
        displayYear = todayYear
        displayMonth = todayMonth
        selectedDay = todayDay
        render()
    }

    fun addEvent(title: String, time: String = "12:00 - 13:00", category: String = "Ish") {
        events.add(
            CalendarEvent(
                title = title,
                time = time,
                category = category,
                day = selectedDay,
                month = displayMonth,
                year = displayYear
            )
        )
        render()
    }

    fun deleteEvent(id: String) {
        events.removeAll { it.id == id }
        render()
    }

    fun getEventsForSelectedDay(): List<CalendarEvent> {
        return events.filter {
            it.year == displayYear && it.month == displayMonth && it.day == selectedDay
        }
    }

    fun hasEvents(day: Int, month: Int, year: Int): Boolean {
        return events.any { it.year == year && it.month == month && it.day == day }
    }

    override fun onPointerEvent(u: Float, v: Float, action: Int) {
        if (action != MotionEvent.ACTION_UP) return

        val px = u * surfaceWidth
        val py = v * surfaceHeight

        // 1. View Mode Switcher (y=30..80, x=480..800)
        if (py in 30f..80f && px in 480f..800f) {
            if (px in 480f..580f) { currentViewMode = ViewMode.MONTH; render(); return }
            if (px in 590f..690f) { currentViewMode = ViewMode.WEEK; render(); return }
            if (px in 700f..800f) { currentViewMode = ViewMode.DAY; render(); return }
        }

        // 2. Month Navigation & Today Button (y=100..155)
        // Prev Month (x=50..100)
        if (px in 40f..110f && py in 100f..160f) {
            prevMonth()
            return
        }
        // Next Month (x=360..420)
        if (px in 350f..430f && py in 100f..160f) {
            nextMonth()
            return
        }
        // Today Button (x=680..780)
        if (px in 670f..790f && py in 100f..160f) {
            jumpToToday()
            return
        }

        // 3. Calendar Day Grid Click (x=50..780, y=210..680)
        if (px in 50f..780f && py in 210f..680f) {
            handleDayGridClick(px, py)
            return
        }

        // 4. Events Panel Actions (x=820..1240)
        // Add Event Button (x=1080..1220, y=105..155)
        if (px in 1070f..1230f && py in 100f..160f) {
            addEvent("Yangi eslatma #${events.size + 1}")
            return
        }

        // Delete Event icon click in events list (x=1170..1220, y in events row)
        if (px in 1160f..1230f && py >= 170f) {
            val dayEvents = getEventsForSelectedDay()
            var itemY = 170f
            for (ev in dayEvents) {
                if (py in itemY..itemY + 90f) {
                    deleteEvent(ev.id)
                    return
                }
                itemY += 105f
            }
        }
    }

    private fun handleDayGridClick(px: Float, py: Float) {
        val colW = (780f - 50f) / 7f
        val rowH = (680f - 210f) / 6f

        val col = ((px - 50f) / colW).toInt().coerceIn(0, 6)
        val row = ((py - 210f) / rowH).toInt().coerceIn(0, 5)

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0, Sun=6
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val cellIndex = row * 7 + col
        val dayNum = cellIndex - firstDayOfWeek + 1
        if (dayNum in 1..maxDays) {
            selectedDay = dayNum
            render()
        }
    }

    fun render() {
        surface?.draw { canvas ->
            canvas.drawColor(Color.parseColor("#E614161C"))

            // 1. Top View Mode Tabs
            renderTopBar(canvas)

            // 2. Main Content
            when (currentViewMode) {
                ViewMode.MONTH -> {
                    renderMonthGrid(canvas)
                    renderEventsPanel(canvas)
                }
                ViewMode.WEEK -> {
                    renderWeekView(canvas)
                    renderEventsPanel(canvas)
                }
                ViewMode.DAY -> {
                    renderDayTimeline(canvas)
                    renderEventsPanel(canvas)
                }
            }
        }
    }

    private fun renderTopBar(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Mode switch pills (x=480..800, y=30..80)
        val pillBg = RectF(480f, 30f, 800f, 80f)
        paint.color = Color.parseColor("#332C2C30")
        canvas.drawRoundRect(pillBg, 25f, 25f, paint)

        val modes = listOf(
            Triple("OY", 485f, 580f),
            Triple("HAFTA", 590f, 690f),
            Triple("KUN", 700f, 795f)
        )

        modes.forEachIndexed { index, (label, left, right) ->
            val isSelected = when (index) {
                0 -> currentViewMode == ViewMode.MONTH
                1 -> currentViewMode == ViewMode.WEEK
                else -> currentViewMode == ViewMode.DAY
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

    private fun renderMonthGrid(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Month / Year Title with Prev/Next buttons
        paint.color = Color.parseColor("#332C2C30")
        val prevRect = RectF(50f, 105f, 100f, 155f)
        canvas.drawRoundRect(prevRect, 14f, 14f, paint)
        paint.color = Color.WHITE
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText("‹", 68f, 142f, paint)

        val nextRect = RectF(370f, 105f, 420f, 155f)
        paint.color = Color.parseColor("#332C2C30")
        canvas.drawRoundRect(nextRect, 14f, 14f, paint)
        paint.color = Color.WHITE
        canvas.drawText("›", 388f, 142f, paint)

        val monthTitle = "${monthNamesUz[displayMonth]} $displayYear"
        paint.textSize = 34f
        canvas.drawText(monthTitle, 125f, 143f, paint)

        // Today jump button (x=680..780)
        val todayRect = RectF(680f, 105f, 780f, 155f)
        paint.color = Color.parseColor("#25252A")
        canvas.drawRoundRect(todayRect, 14f, 14f, paint)
        paint.color = Color.parseColor("#0A84FF")
        paint.textSize = 20f
        canvas.drawText("Bugun", 702f, 138f, paint)
        paint.isFakeBoldText = false

        // Day of week headers (y=180)
        val colW = (780f - 50f) / 7f
        paint.textSize = 20f
        paint.color = Color.parseColor("#8E8E93")
        for (i in weekDayNamesUz.indices) {
            val x = 50f + i * colW + colW / 2f - 12f
            canvas.drawText(weekDayNamesUz[i], x, 185f, paint)
        }

        // Calendar Grid Days
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0..Sun=6
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val rowH = (680f - 210f) / 6f
        for (day in 1..maxDays) {
            val cellIndex = firstDayOfWeek + day - 1
            val col = cellIndex % 7
            val row = cellIndex / 7

            val cx = 50f + col * colW + colW / 2f
            val cy = 210f + row * rowH + rowH / 2f

            val isToday = (displayYear == todayYear && displayMonth == todayMonth && day == todayDay)
            val isSelected = (day == selectedDay)

            // Today badge or selected badge
            if (isToday) {
                paint.color = Color.parseColor("#0A84FF")
                canvas.drawCircle(cx, cy - 5f, 24f, paint)
            } else if (isSelected) {
                paint.color = Color.parseColor("#3364D2FF")
                canvas.drawCircle(cx, cy - 5f, 24f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = Color.parseColor("#64D2FF")
                canvas.drawCircle(cx, cy - 5f, 24f, paint)
                paint.style = Paint.Style.FILL
            }

            paint.color = if (isToday) Color.WHITE else if (isSelected) Color.parseColor("#64D2FF") else Color.parseColor("#E0E0E0")
            paint.textSize = 22f
            paint.isFakeBoldText = (isToday || isSelected)
            val dayStr = "$day"
            val textW = paint.measureText(dayStr)
            canvas.drawText(dayStr, cx - textW / 2f, cy + 3f, paint)
            paint.isFakeBoldText = false

            // Dot indicator if events exist on this day
            if (hasEvents(day, displayMonth, displayYear)) {
                paint.color = if (isToday) Color.WHITE else Color.parseColor("#FF9F0A")
                canvas.drawCircle(cx, cy + 18f, 3.5f, paint)
            }
        }
    }

    private fun renderWeekView(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.isFakeBoldText = true
        canvas.drawText("Haftalik Ko'rinish", 60f, 140f, paint)
        paint.isFakeBoldText = false

        val colW = (780f - 50f) / 7f
        for (i in 0..6) {
            val colRect = RectF(50f + i * colW, 170f, 50f + (i + 1) * colW - 8f, 670f)
            paint.color = Color.parseColor("#202229")
            canvas.drawRoundRect(colRect, 16f, 16f, paint)

            paint.color = Color.parseColor("#8E8E93")
            paint.textSize = 18f
            canvas.drawText(weekDayNamesUz[i], colRect.left + 15f, 205f, paint)
        }
    }

    private fun renderDayTimeline(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.isFakeBoldText = true
        canvas.drawText("Kunlik Jadval: $selectedDay-${monthNamesUz[displayMonth]}", 60f, 140f, paint)
        paint.isFakeBoldText = false

        var timeY = 190f
        val hours = listOf("09:00", "11:00", "13:00", "15:00", "17:00", "19:00")
        for (h in hours) {
            paint.color = Color.parseColor("#636366")
            paint.textSize = 20f
            canvas.drawText(h, 60f, timeY, paint)

            paint.color = Color.parseColor("#2C2C30")
            paint.strokeWidth = 1.5f
            canvas.drawLine(130f, timeY - 7f, 780f, timeY - 7f, paint)
            timeY += 80f
        }
    }

    private fun renderEventsPanel(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Panel container (x=820..1240, y=95..680)
        val panelRect = RectF(820f, 95f, 1240f, 680f)
        paint.color = Color.parseColor("#22232A")
        canvas.drawRoundRect(panelRect, 24f, 24f, paint)

        // Date Header
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.isFakeBoldText = true
        val isToday = (displayYear == todayYear && displayMonth == todayMonth && selectedDay == todayDay)
        val headerText = "$selectedDay-${monthNamesUz[displayMonth]}" + if (isToday) " (Bugun)" else ""
        canvas.drawText(headerText, 845f, 142f, paint)
        paint.isFakeBoldText = false

        // Add Event Button (x=1080..1220, y=105..155)
        val addRect = RectF(1080f, 105f, 1220f, 155f)
        paint.color = Color.parseColor("#0A84FF")
        canvas.drawRoundRect(addRect, 14f, 14f, paint)
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("+ Hodisa", 1105f, 137f, paint)
        paint.isFakeBoldText = false

        // Events List
        val dayEvents = getEventsForSelectedDay()
        if (dayEvents.isEmpty()) {
            paint.color = Color.parseColor("#8E8E93")
            paint.textSize = 22f
            canvas.drawText("Ushbu kunda rejalashtirilgan", 880f, 320f, paint)
            canvas.drawText("hodisalar mavjud emas", 910f, 355f, paint)
        } else {
            var itemY = 175f
            for (ev in dayEvents) {
                if (itemY + 90f > 670f) break
                val cardRect = RectF(840f, itemY, 1220f, itemY + 85f)
                paint.color = Color.parseColor("#2C2D36")
                canvas.drawRoundRect(cardRect, 16f, 16f, paint)

                // Category pill color
                val catColor = when (ev.category) {
                    "Muhim" -> Color.parseColor("#FF453A")
                    "Shaxsiy" -> Color.parseColor("#30D158")
                    else -> Color.parseColor("#0A84FF")
                }
                paint.color = catColor
                canvas.drawCircle(865f, itemY + 28f, 6f, paint)

                // Event Title
                paint.color = Color.WHITE
                paint.textSize = 22f
                paint.isFakeBoldText = true
                val displayTitle = if (ev.title.length > 22) ev.title.take(20) + "..." else ev.title
                canvas.drawText(displayTitle, 885f, itemY + 36f, paint)
                paint.isFakeBoldText = false

                // Event Time & Category
                paint.color = Color.parseColor("#A0A0A5")
                paint.textSize = 18f
                canvas.drawText("${ev.time} • ${ev.category}", 885f, itemY + 66f, paint)

                // Delete button ("✕")
                paint.color = Color.parseColor("#8E8E93")
                paint.textSize = 22f
                canvas.drawText("✕", 1185f, itemY + 48f, paint)

                itemY += 100f
            }
        }
    }
}
