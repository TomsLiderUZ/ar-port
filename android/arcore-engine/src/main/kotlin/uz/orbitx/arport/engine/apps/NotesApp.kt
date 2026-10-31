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

/**
 * Native Spatial Notes Application with SQL database persistence,
 * search, categorisation, export, and interactive keyboard input (TZ 11, QADAM 14).
 */
class NotesApp(
    override val manifest: AppManifest
) : ARApp {

    override val id: String = manifest.id

    enum class ViewState {
        LIST, EDIT
    }

    enum class FocusTarget {
        NONE, SEARCH, TITLE, BODY
    }

    var currentView: ViewState = ViewState.LIST
    var selectedCategory: String = "Barchasi"
    var searchQuery: String = ""
    var editingNote: NoteItem? = null
    var focusTarget: FocusTarget = FocusTarget.NONE
    var toastMessage: String? = null
    var toastExpireMs: Long = 0L

    var repository: NotesRepository = InMemoryNotesRepository()
    private var appContext: Context? = null

    var surface: ContentSurface? = null
        private set

    private var surfaceWidth: Int = 720
    private var surfaceHeight: Int = 1280

    override fun createSurface(context: Context, width: Int, height: Int): ContentSurface {
        appContext = context
        surfaceWidth = width
        surfaceHeight = height
        val surf = ContentSurface(width, height)
        surface = surf

        try {
            repository = SqliteNotesRepository(context)
        } catch (_: Exception) {
            repository = InMemoryNotesRepository()
        }

        render()
        return surf
    }

    fun openNewNote() {
        editingNote = NoteItem(
            title = "",
            content = "",
            category = if (selectedCategory != "Barchasi") selectedCategory else "G'oyalar"
        )
        currentView = ViewState.EDIT
        focusTarget = FocusTarget.TITLE
        render()
    }

    fun openEditNote(note: NoteItem) {
        editingNote = note.copy()
        currentView = ViewState.EDIT
        focusTarget = FocusTarget.BODY
        render()
    }

    fun saveCurrentNote() {
        val note = editingNote ?: return
        if (note.title.isBlank() && note.content.isBlank()) {
            currentView = ViewState.LIST
            editingNote = null
            render()
            return
        }
        if (note.title.isBlank()) {
            note.title = if (note.content.length > 20) note.content.take(20) + "..." else note.content
        }
        repository.saveNote(note)
        showToast("Qayd saqlandi")
        currentView = ViewState.LIST
        editingNote = null
        focusTarget = FocusTarget.NONE
        render()
    }

    fun deleteCurrentNote() {
        val note = editingNote ?: return
        repository.deleteNote(note.id)
        showToast("Qayd o'chirildi")
        currentView = ViewState.LIST
        editingNote = null
        focusTarget = FocusTarget.NONE
        render()
    }

    fun exportNotes() {
        val file = repository.exportNotesToSandbox()
        if (file != null) {
            showToast("Sandboxga eksport qilindi: ${file.name}")
        } else {
            showToast("Eksportda xatolik yuz berdi")
        }
        render()
    }

    fun showToast(msg: String) {
        toastMessage = msg
        toastExpireMs = System.currentTimeMillis() + 3000L
    }

    fun appendInputText(text: String) {
        when (currentView) {
            ViewState.LIST -> {
                if (focusTarget == FocusTarget.SEARCH) {
                    searchQuery += text
                    render()
                }
            }
            ViewState.EDIT -> {
                val note = editingNote ?: return
                if (focusTarget == FocusTarget.TITLE) {
                    note.title += text
                    render()
                } else if (focusTarget == FocusTarget.BODY) {
                    note.content += text
                    render()
                }
            }
        }
    }

    fun backspaceInputText() {
        when (currentView) {
            ViewState.LIST -> {
                if (focusTarget == FocusTarget.SEARCH && searchQuery.isNotEmpty()) {
                    searchQuery = searchQuery.dropLast(1)
                    render()
                }
            }
            ViewState.EDIT -> {
                val note = editingNote ?: return
                if (focusTarget == FocusTarget.TITLE && note.title.isNotEmpty()) {
                    note.title = note.title.dropLast(1)
                    render()
                } else if (focusTarget == FocusTarget.BODY && note.content.isNotEmpty()) {
                    note.content = note.content.dropLast(1)
                    render()
                }
            }
        }
    }

    override fun onPointerEvent(u: Float, v: Float, action: Int) {
        if (action != MotionEvent.ACTION_UP) return

        val px = u * surfaceWidth
        val py = v * surfaceHeight

        when (currentView) {
            ViewState.LIST -> handleListPointerEvent(px, py)
            ViewState.EDIT -> handleEditPointerEvent(px, py)
        }
    }

    private fun handleListPointerEvent(px: Float, py: Float) {
        // 1. Top Buttons (y=40..100)
        // Export button (x=380..520)
        if (px in 380f..520f && py in 40f..100f) {
            exportNotes()
            return
        }
        // New Note button (x=540..680)
        if (px in 540f..680f && py in 40f..100f) {
            openNewNote()
            return
        }

        // 2. Search Bar (x=40..680, y=120..180)
        if (px in 40f..680f && py in 120f..180f) {
            // Check clear button tap if query not blank (x=620..680)
            if (searchQuery.isNotEmpty() && px >= 620f) {
                searchQuery = ""
            } else {
                focusTarget = FocusTarget.SEARCH
            }
            render()
            return
        }

        // 3. Category Filter Tabs (y=200..250)
        if (py in 200f..250f) {
            if (px in 40f..180f) { selectedCategory = "Barchasi"; render(); return }
            if (px in 190f..330f) { selectedCategory = "Shaxsiy"; render(); return }
            if (px in 340f..460f) { selectedCategory = "Ish"; render(); return }
            if (px in 470f..620f) { selectedCategory = "G'oyalar"; render(); return }
        }

        // 4. Notes List Cards (y=270..1200)
        if (py >= 270f) {
            val notes = getFilteredNotes()
            var startY = 270f
            for (note in notes) {
                val cardBottom = startY + 120f
                if (py in startY..cardBottom && px in 40f..680f) {
                    openEditNote(note)
                    return
                }
                startY += 135f
                if (startY > 1200f) break
            }
        }
    }

    private fun handleEditPointerEvent(px: Float, py: Float) {
        // 1. Top Bar Buttons (y=40..100)
        // Back button (x=40..160)
        if (px in 40f..160f && py in 40f..100f) {
            saveCurrentNote()
            return
        }
        // Save button (x=470..570)
        if (px in 470f..570f && py in 40f..100f) {
            saveCurrentNote()
            return
        }
        // Delete button (x=585..685)
        if (px in 585f..685f && py in 40f..100f) {
            deleteCurrentNote()
            return
        }

        // 2. Category Selector (y=120..170)
        if (py in 120f..170f) {
            val note = editingNote ?: return
            if (px in 40f..180f) { note.category = "Shaxsiy"; render(); return }
            if (px in 195f..315f) { note.category = "Ish"; render(); return }
            if (px in 330f..480f) { note.category = "G'oyalar"; render(); return }
        }

        // 3. Title Box (y=190..270)
        if (px in 40f..680f && py in 190f..270f) {
            focusTarget = FocusTarget.TITLE
            render()
            return
        }

        // 4. Body Box (y=290..1150)
        if (px in 40f..680f && py in 290f..1150f) {
            focusTarget = FocusTarget.BODY
            render()
            return
        }
    }

    fun getFilteredNotes(): List<NoteItem> {
        val base = if (searchQuery.isNotBlank()) {
            repository.searchNotes(searchQuery)
        } else {
            repository.getNotesByCategory(selectedCategory)
        }
        return if (searchQuery.isNotBlank() && selectedCategory != "Barchasi") {
            base.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        } else {
            base
        }
    }

    fun render() {
        surface?.draw { canvas ->
            canvas.drawColor(Color.parseColor("#E616171B"))

            when (currentView) {
                ViewState.LIST -> renderListView(canvas)
                ViewState.EDIT -> renderEditView(canvas)
            }

            renderToastIfNeeded(canvas)
        }
    }

    private fun renderListView(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Header
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.isFakeBoldText = true
        canvas.drawText("📝 Qaydlar", 45f, 80f, paint)
        paint.isFakeBoldText = false

        // Export Button (x=380..520, y=40..95)
        val exportRect = RectF(380f, 45f, 520f, 95f)
        paint.color = Color.parseColor("#332C2C30")
        canvas.drawRoundRect(exportRect, 16f, 16f, paint)
        paint.color = Color.parseColor("#64D2FF")
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("📤 Eksport", 400f, 78f, paint)

        // New Note Button (x=540..680, y=40..95)
        val newRect = RectF(540f, 45f, 680f, 95f)
        paint.color = Color.parseColor("#0A84FF")
        canvas.drawRoundRect(newRect, 16f, 16f, paint)
        paint.color = Color.WHITE
        paint.textSize = 22f
        canvas.drawText("+ Yangi", 575f, 78f, paint)
        paint.isFakeBoldText = false

        // Search Bar (x=40..680, y=120..175)
        val searchRect = RectF(40f, 120f, 680f, 175f)
        paint.color = Color.parseColor("#25252A")
        canvas.drawRoundRect(searchRect, 18f, 18f, paint)

        paint.color = if (focusTarget == FocusTarget.SEARCH) Color.parseColor("#0A84FF") else Color.parseColor("#3A3A40")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(searchRect, 18f, 18f, paint)
        paint.style = Paint.Style.FILL

        paint.color = if (searchQuery.isEmpty()) Color.parseColor("#8E8E93") else Color.WHITE
        paint.textSize = 24f
        val searchDisplay = if (searchQuery.isEmpty()) {
            "🔍 Qidirish..."
        } else {
            "🔍 $searchQuery" + if (focusTarget == FocusTarget.SEARCH) "|" else ""
        }
        canvas.drawText(searchDisplay, 60f, 157f, paint)

        if (searchQuery.isNotEmpty()) {
            paint.color = Color.parseColor("#8E8E93")
            paint.textSize = 24f
            canvas.drawText("✕", 645f, 157f, paint)
        }

        // Category Pills (y=200..245)
        val categories = listOf("Barchasi", "Shaxsiy", "Ish", "G'oyalar")
        var tabX = 40f
        for (cat in categories) {
            val isSel = (cat == selectedCategory)
            val pillW = paint.measureText(cat) + 36f
            val pillRect = RectF(tabX, 200f, tabX + pillW, 245f)
            paint.color = if (isSel) Color.parseColor("#0A84FF") else Color.parseColor("#25252A")
            canvas.drawRoundRect(pillRect, 16f, 16f, paint)
            paint.color = if (isSel) Color.WHITE else Color.parseColor("#8E8E93")
            paint.textSize = 20f
            paint.isFakeBoldText = isSel
            canvas.drawText(cat, tabX + 18f, 230f, paint)
            paint.isFakeBoldText = false
            tabX += pillW + 12f
        }

        // Notes List Cards (y=270..1200)
        val notes = getFilteredNotes()
        if (notes.isEmpty()) {
            paint.color = Color.parseColor("#8E8E93")
            paint.textSize = 26f
            canvas.drawText("Hech qanday qayd topilmadi", 200f, 400f, paint)
        } else {
            var startY = 270f
            for (note in notes) {
                if (startY + 120f > 1240f) break
                val cardRect = RectF(40f, startY, 680f, startY + 120f)
                paint.color = Color.parseColor("#24252B")
                canvas.drawRoundRect(cardRect, 20f, 20f, paint)

                // Category indicator tag pill
                val tagColor = when (note.category) {
                    "Ish" -> Color.parseColor("#0A84FF")
                    "Shaxsiy" -> Color.parseColor("#30D158")
                    else -> Color.parseColor("#FF9F0A")
                }
                paint.color = tagColor
                canvas.drawRoundRect(RectF(60f, startY + 18f, 140f, startY + 44f), 8f, 8f, paint)
                paint.color = Color.BLACK
                paint.textSize = 16f
                paint.isFakeBoldText = true
                canvas.drawText(note.category.take(6), 70f, startY + 36f, paint)

                // Date
                paint.color = Color.parseColor("#8E8E93")
                paint.textSize = 18f
                paint.isFakeBoldText = false
                val dateStr = SimpleDateFormat("dd.MM.yy HH:mm", Locale.US).format(Date(note.timestamp))
                canvas.drawText(dateStr, 530f, startY + 36f, paint)

                // Title
                paint.color = Color.WHITE
                paint.textSize = 26f
                paint.isFakeBoldText = true
                val displayTitle = if (note.title.length > 28) note.title.take(26) + "..." else note.title
                canvas.drawText(displayTitle, 60f, startY + 76f, paint)

                // Snippet
                paint.color = Color.parseColor("#A0A0A5")
                paint.textSize = 20f
                paint.isFakeBoldText = false
                val snippet = note.content.replace("\n", " ")
                val displaySnippet = if (snippet.length > 40) snippet.take(38) + "..." else snippet
                canvas.drawText(displaySnippet, 60f, startY + 104f, paint)

                startY += 135f
            }
        }
    }

    private fun renderEditView(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val note = editingNote ?: return

        // Top Navigation
        // Back Button
        val backRect = RectF(40f, 45f, 160f, 95f)
        paint.color = Color.parseColor("#332C2C30")
        canvas.drawRoundRect(backRect, 16f, 16f, paint)
        paint.color = Color.parseColor("#0A84FF")
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("‹ Qaydlar", 55f, 78f, paint)

        // Save Button
        val saveRect = RectF(470f, 45f, 570f, 95f)
        paint.color = Color.parseColor("#30D158")
        canvas.drawRoundRect(saveRect, 16f, 16f, paint)
        paint.color = Color.BLACK
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("💾 Saqlash", 480f, 78f, paint)

        // Delete Button
        val delRect = RectF(585f, 45f, 685f, 95f)
        paint.color = Color.parseColor("#44FF3B30")
        canvas.drawRoundRect(delRect, 16f, 16f, paint)
        paint.color = Color.parseColor("#FF453A")
        paint.textSize = 20f
        canvas.drawText("🗑️ O'chirish", 595f, 78f, paint)
        paint.isFakeBoldText = false

        // Category Pills (y=120..165)
        val categories = listOf("Shaxsiy", "Ish", "G'oyalar")
        var catX = 40f
        for (c in categories) {
            val isSel = (c == note.category)
            val pillW = 120f
            val pillRect = RectF(catX, 120f, catX + pillW, 165f)
            paint.color = if (isSel) Color.parseColor("#0A84FF") else Color.parseColor("#25252A")
            canvas.drawRoundRect(pillRect, 14f, 14f, paint)
            paint.color = if (isSel) Color.WHITE else Color.parseColor("#8E8E93")
            paint.textSize = 20f
            paint.isFakeBoldText = isSel
            canvas.drawText(c, catX + 22f, 150f, paint)
            paint.isFakeBoldText = false
            catX += pillW + 15f
        }

        // Title Input Area (y=190..260)
        val titleRect = RectF(40f, 190f, 680f, 260f)
        paint.color = Color.parseColor("#202127")
        canvas.drawRoundRect(titleRect, 18f, 18f, paint)
        if (focusTarget == FocusTarget.TITLE) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#0A84FF")
            paint.strokeWidth = 2f
            canvas.drawRoundRect(titleRect, 18f, 18f, paint)
            paint.style = Paint.Style.FILL
        }

        paint.color = if (note.title.isEmpty()) Color.parseColor("#636366") else Color.WHITE
        paint.textSize = 26f
        paint.isFakeBoldText = true
        val titleText = if (note.title.isEmpty()) "Sarlavha..." else note.title + if (focusTarget == FocusTarget.TITLE) "|" else ""
        canvas.drawText(titleText, 60f, 235f, paint)
        paint.isFakeBoldText = false

        // Body Content Input Area (y=280..1180)
        val bodyRect = RectF(40f, 280f, 680f, 1180f)
        paint.color = Color.parseColor("#202127")
        canvas.drawRoundRect(bodyRect, 20f, 20f, paint)
        if (focusTarget == FocusTarget.BODY) {
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#0A84FF")
            paint.strokeWidth = 2f
            canvas.drawRoundRect(bodyRect, 20f, 20f, paint)
            paint.style = Paint.Style.FILL
        }

        paint.color = if (note.content.isEmpty()) Color.parseColor("#636366") else Color.parseColor("#EDEDED")
        paint.textSize = 24f

        if (note.content.isEmpty()) {
            val emptyHint = "Bu yerga matn yozing...\nFazoviy klaviatura bilan to'liq ishlaydi."
            var lineY = 325f
            for (line in emptyHint.split("\n")) {
                canvas.drawText(line, 60f, lineY, paint)
                lineY += 36f
            }
        } else {
            val lines = note.content.split("\n")
            var lineY = 325f
            for ((idx, line) in lines.withIndex()) {
                if (lineY > 1150f) break
                val lineDisplay = if (idx == lines.size - 1 && focusTarget == FocusTarget.BODY) line + "|" else line
                canvas.drawText(lineDisplay, 60f, lineY, paint)
                lineY += 36f
            }
        }

        // Character count (y=1210)
        paint.color = Color.parseColor("#8E8E93")
        paint.textSize = 18f
        canvas.drawText("Belgilar: ${note.content.length} • Sarlavha: ${note.title.length}", 60f, 1215f, paint)
    }

    private fun renderToastIfNeeded(canvas: Canvas) {
        val msg = toastMessage ?: return
        if (System.currentTimeMillis() > toastExpireMs) {
            toastMessage = null
            return
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val toastRect = RectF(80f, 1190f, 640f, 1250f)
        paint.color = Color.parseColor("#E62C2C2E")
        canvas.drawRoundRect(toastRect, 20f, 20f, paint)

        paint.color = Color.parseColor("#64D2FF")
        paint.textSize = 22f
        paint.isFakeBoldText = true
        val textW = paint.measureText(msg)
        canvas.drawText(msg, toastRect.left + (toastRect.width() - textW) / 2f, 1230f, paint)
    }
}
