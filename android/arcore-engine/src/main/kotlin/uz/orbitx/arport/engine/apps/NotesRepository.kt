package uz.orbitx.arport.engine.apps

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class NoteItem(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var content: String,
    var category: String = "G'oyalar",
    var timestamp: Long = System.currentTimeMillis()
)

interface NotesRepository {
    fun getAllNotes(): List<NoteItem>
    fun searchNotes(query: String): List<NoteItem>
    fun getNotesByCategory(category: String): List<NoteItem>
    fun saveNote(note: NoteItem)
    fun deleteNote(id: String)
    fun exportNotesToSandbox(): File?
}

/**
 * In-memory fallback notes repository (also used in host JVM unit tests).
 */
class InMemoryNotesRepository : NotesRepository {
    private val notes = mutableListOf<NoteItem>()

    init {
        seedInitialNotes()
    }

    private fun seedInitialNotes() {
        notes.add(
            NoteItem(
                id = "note_1",
                title = "AR Port-ga xush kelibsiz",
                content = "Apple Vision Pro uslubidagi fazoviy oynalar, Liquid Glass shaffofligi va qo'l nuri bilan to'liq boshqaruv.",
                category = "G'oyalar",
                timestamp = System.currentTimeMillis() - 86400000L
            )
        )
        notes.add(
            NoteItem(
                id = "note_2",
                title = "Fazoviy boshqaruv eslatmalari",
                content = "Qo'lni ko'tarib nishonga oling. Bosh va ko'rsatkich barmog'ingizni tekkizib chimchilang (Tap). Surish uchun ushlab torting.",
                category = "Ish",
                timestamp = System.currentTimeMillis() - 3600000L
            )
        )
        notes.add(
            NoteItem(
                id = "note_3",
                title = "Loyiha rejasi",
                content = "1. Stereo linza kalibratsiyasi\n2. Qaydlar va soat ilovalari\n3. 3D stol tennisi o'yini",
                category = "Shaxsiy",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override fun getAllNotes(): List<NoteItem> {
        return notes.sortedByDescending { it.timestamp }
    }

    override fun searchNotes(query: String): List<NoteItem> {
        if (query.isBlank()) return getAllNotes()
        val q = query.lowercase(Locale.ROOT)
        return notes.filter {
            it.title.lowercase(Locale.ROOT).contains(q) ||
            it.content.lowercase(Locale.ROOT).contains(q)
        }.sortedByDescending { it.timestamp }
    }

    override fun getNotesByCategory(category: String): List<NoteItem> {
        if (category == "Barchasi") return getAllNotes()
        return notes.filter { it.category.equals(category, ignoreCase = true) }
            .sortedByDescending { it.timestamp }
    }

    override fun saveNote(note: NoteItem) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index >= 0) {
            notes[index] = note.copy(timestamp = System.currentTimeMillis())
        } else {
            notes.add(0, note.copy(timestamp = System.currentTimeMillis()))
        }
    }

    override fun deleteNote(id: String) {
        notes.removeAll { it.id == id }
    }

    override fun exportNotesToSandbox(): File? {
        val tmpDir = File(System.getProperty("java.io.tmpdir") ?: ".", "ar_port_sandbox")
        val exportDir = File(tmpDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportFile = File(exportDir, "notes_export_${timeStamp}.txt")

        val sb = StringBuilder()
        sb.append("=== AR PORT FAZOVIY QAYDLAR EKSPORTI ===\n")
        sb.append("Eksport vaqti: $timeStamp\n\n")
        for (n in getAllNotes()) {
            sb.append("----------------------------------------\n")
            sb.append("Sarlavha: ${n.title}\n")
            sb.append("Kategoriya: ${n.category}\n")
            sb.append("Vaqt: ${Date(n.timestamp)}\n")
            sb.append("Matn:\n${n.content}\n\n")
        }

        try {
            exportFile.writeText(sb.toString())
            return exportFile
        } catch (_: Exception) {
            return null
        }
    }
}

/**
 * SQLite-backed persistent repository for production Android runtime (TZ 11, QADAM 14).
 */
class SqliteNotesRepository(
    private val context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION), NotesRepository {

    companion object {
        const val DATABASE_NAME = "ar_port_notes.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NOTES = "notes"

        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_CONTENT = "content"
        const val COL_CATEGORY = "category"
        const val COL_TIMESTAMP = "timestamp"
    }

    private val fallback = InMemoryNotesRepository()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_NOTES (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_CONTENT TEXT NOT NULL,
                $COL_CATEGORY TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // Seed initial notes
        for (n in fallback.getAllNotes()) {
            val cv = ContentValues().apply {
                put(COL_ID, n.id)
                put(COL_TITLE, n.title)
                put(COL_CONTENT, n.content)
                put(COL_CATEGORY, n.category)
                put(COL_TIMESTAMP, n.timestamp)
            }
            db.insert(TABLE_NOTES, null, cv)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTES")
        onCreate(db)
    }

    override fun getAllNotes(): List<NoteItem> {
        return try {
            val db = readableDatabase
            val cursor = db.query(TABLE_NOTES, null, null, null, null, null, "$COL_TIMESTAMP DESC")
            val list = mutableListOf<NoteItem>()
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        NoteItem(
                            id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                            title = it.getString(it.getColumnIndexOrThrow(COL_TITLE)),
                            content = it.getString(it.getColumnIndexOrThrow(COL_CONTENT)),
                            category = it.getString(it.getColumnIndexOrThrow(COL_CATEGORY)),
                            timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            fallback.getAllNotes()
        }
    }

    override fun searchNotes(query: String): List<NoteItem> {
        if (query.isBlank()) return getAllNotes()
        return try {
            val db = readableDatabase
            val q = "%$query%"
            val cursor = db.query(
                TABLE_NOTES,
                null,
                "$COL_TITLE LIKE ? OR $COL_CONTENT LIKE ?",
                arrayOf(q, q),
                null,
                null,
                "$COL_TIMESTAMP DESC"
            )
            val list = mutableListOf<NoteItem>()
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        NoteItem(
                            id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                            title = it.getString(it.getColumnIndexOrThrow(COL_TITLE)),
                            content = it.getString(it.getColumnIndexOrThrow(COL_CONTENT)),
                            category = it.getString(it.getColumnIndexOrThrow(COL_CATEGORY)),
                            timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            fallback.searchNotes(query)
        }
    }

    override fun getNotesByCategory(category: String): List<NoteItem> {
        if (category == "Barchasi") return getAllNotes()
        return try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_NOTES,
                null,
                "$COL_CATEGORY = ?",
                arrayOf(category),
                null,
                null,
                "$COL_TIMESTAMP DESC"
            )
            val list = mutableListOf<NoteItem>()
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        NoteItem(
                            id = it.getString(it.getColumnIndexOrThrow(COL_ID)),
                            title = it.getString(it.getColumnIndexOrThrow(COL_TITLE)),
                            content = it.getString(it.getColumnIndexOrThrow(COL_CONTENT)),
                            category = it.getString(it.getColumnIndexOrThrow(COL_CATEGORY)),
                            timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            fallback.getNotesByCategory(category)
        }
    }

    override fun saveNote(note: NoteItem) {
        try {
            val db = writableDatabase
            val cv = ContentValues().apply {
                put(COL_ID, note.id)
                put(COL_TITLE, note.title)
                put(COL_CONTENT, note.content)
                put(COL_CATEGORY, note.category)
                put(COL_TIMESTAMP, System.currentTimeMillis())
            }
            db.insertWithOnConflict(TABLE_NOTES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: Exception) {
            fallback.saveNote(note)
        }
    }

    override fun deleteNote(id: String) {
        try {
            val db = writableDatabase
            db.delete(TABLE_NOTES, "$COL_ID = ?", arrayOf(id))
        } catch (_: Exception) {
            fallback.deleteNote(id)
        }
    }

    override fun exportNotesToSandbox(): File? {
        val exportDir = File(context.filesDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportFile = File(exportDir, "notes_export_${timeStamp}.txt")

        val sb = StringBuilder()
        sb.append("=== AR PORT FAZOVIY QAYDLAR EKSPORTI ===\n")
        sb.append("Eksport vaqti: $timeStamp\n\n")
        for (n in getAllNotes()) {
            sb.append("----------------------------------------\n")
            sb.append("Sarlavha: ${n.title}\n")
            sb.append("Kategoriya: ${n.category}\n")
            sb.append("Vaqt: ${Date(n.timestamp)}\n")
            sb.append("Matn:\n${n.content}\n\n")
        }

        return try {
            exportFile.writeText(sb.toString())
            exportFile
        } catch (_: Exception) {
            fallback.exportNotesToSandbox()
        }
    }
}

