package uz.orbitx.arport.engine.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.orbitx.arport.engine.registry.AppManifest
import java.io.File

class NativeAppsTest {

    private fun createDummyManifest(id: String, type: String = "native"): AppManifest {
        val cap = id.replaceFirstChar { it.uppercase() }
        return AppManifest(
            id = id,
            name = mapOf("uz" to cap, "en" to cap, "ru" to cap),
            icon = "📱",
            version = "1.0.0",
            type = type,
            category = "tools",
            entry = "uz.orbitx.arport.engine.apps.${cap}App",
            defaultAspect = "16:9",
            defaultWidth = 1.0f,
            defaultHeight = 0.5625f,
            multiWindow = false,
            permissions = emptyList(),
            allowedHosts = emptyList(),
            sessionGroup = "system",
            minEngineVersion = "1.0",
            order = 1
        )
    }

    // --- 1. CameraApp Tests ---

    @Test
    fun testCameraAppControlsAndModes() {
        val app = CameraApp(createDummyManifest("camera"))

        assertEquals(CameraApp.Mode.PHOTO, app.currentMode)
        app.currentMode = CameraApp.Mode.VIDEO
        assertEquals(CameraApp.Mode.VIDEO, app.currentMode)

        // Timer cycling
        assertEquals(CameraApp.TimerSetting.OFF, app.timerSetting)
        app.cycleTimer()
        assertEquals(CameraApp.TimerSetting.SEC_3, app.timerSetting)
        app.cycleTimer()
        assertEquals(CameraApp.TimerSetting.SEC_5, app.timerSetting)
        app.cycleTimer()
        assertEquals(CameraApp.TimerSetting.SEC_10, app.timerSetting)
        app.cycleTimer()
        assertEquals(CameraApp.TimerSetting.OFF, app.timerSetting)

        // Flash cycling
        assertEquals(CameraApp.FlashMode.AUTO, app.flashMode)
        app.cycleFlash()
        assertEquals(CameraApp.FlashMode.ON, app.flashMode)
        app.cycleFlash()
        assertEquals(CameraApp.FlashMode.OFF, app.flashMode)
        app.cycleFlash()
        assertEquals(CameraApp.FlashMode.AUTO, app.flashMode)

        // Camera flip
        assertFalse(app.isFrontCamera)
        app.flipCamera()
        assertTrue(app.isFrontCamera)
    }

    @Test
    fun testCameraPhotoCaptureAndGallery() {
        val app = CameraApp(createDummyManifest("camera"))
        val initialCount = app.capturedPhotos.size

        val photo = app.snapPhoto()
        assertNotNull(photo)
        assertEquals(initialCount + 1, app.capturedPhotos.size)
        assertTrue(photo!!.file.name.startsWith("IMG_"))

        // Video recording toggle
        assertFalse(app.isRecordingVideo)
        assertTrue(app.toggleVideoRecording())
        assertTrue(app.isRecordingVideo)
        assertFalse(app.toggleVideoRecording())
        assertFalse(app.isRecordingVideo)

        // Delete photo
        app.deleteCurrentPhoto()
        assertEquals(initialCount, app.capturedPhotos.size)
    }

    // --- 2. NotesApp Tests ---

    @Test
    fun testNotesRepositoryAndSearch() {
        val repo = InMemoryNotesRepository()
        val all = repo.getAllNotes()
        assertTrue("Should have seeded initial notes", all.size >= 3)

        val searchResult = repo.searchNotes("Vision Pro")
        assertTrue(searchResult.isNotEmpty())
        assertEquals("AR Port-ga xush kelibsiz", searchResult[0].title)

        val workNotes = repo.getNotesByCategory("Ish")
        assertTrue(workNotes.any { it.category == "Ish" })

        // Save new note
        val newNote = NoteItem(title = "Yangi test", content = "Test content", category = "Shaxsiy")
        repo.saveNote(newNote)
        assertEquals(all.size + 1, repo.getAllNotes().size)

        // Delete note
        repo.deleteNote(newNote.id)
        assertEquals(all.size, repo.getAllNotes().size)

        // Export
        val exportFile = repo.exportNotesToSandbox()
        assertNotNull(exportFile)
        assertTrue(exportFile!!.exists())
        assertTrue(exportFile.length() > 0)
    }

    @Test
    fun testNotesAppTypingAndEditing() {
        val app = NotesApp(createDummyManifest("notes"))
        app.repository = InMemoryNotesRepository()

        app.openNewNote()
        assertEquals(NotesApp.ViewState.EDIT, app.currentView)
        assertEquals(NotesApp.FocusTarget.TITLE, app.focusTarget)

        app.appendInputText("Salom")
        assertEquals("Salom", app.editingNote?.title)

        app.backspaceInputText()
        assertEquals("Salo", app.editingNote?.title)

        app.focusTarget = NotesApp.FocusTarget.BODY
        app.appendInputText("Fazoviy matn")
        assertEquals("Fazoviy matn", app.editingNote?.content)

        app.saveCurrentNote()
        assertEquals(NotesApp.ViewState.LIST, app.currentView)
        assertTrue(app.repository.searchNotes("Salo").isNotEmpty())
    }

    // --- 3. ClockApp Tests ---

    @Test
    fun testClockWorldCities() {
        val app = ClockApp(createDummyManifest("clock"))
        assertEquals(ClockApp.Tab.WORLD, app.currentTab)
        assertEquals(4, app.worldCities.size)
        assertEquals("Toshkent", app.worldCities[0].name)
        assertEquals("London", app.worldCities[1].name)
        assertEquals("Nyu-York", app.worldCities[2].name)
        assertEquals("Tokio", app.worldCities[3].name)
    }

    @Test
    fun testClockStopwatchAndTimer() {
        val app = ClockApp(createDummyManifest("clock"))

        // Stopwatch
        assertEquals(0L, app.getStopwatchCurrentMs())
        app.startStopwatch()
        assertTrue(app.isStopwatchRunning)
        app.lapStopwatch()
        assertEquals(1, app.lapSplits.size)
        app.startStopwatch() // pauses
        assertFalse(app.isStopwatchRunning)
        app.resetStopwatch()
        assertEquals(0L, app.getStopwatchCurrentMs())
        assertEquals(0, app.lapSplits.size)

        // Timer
        app.setTimerPreset(25)
        assertEquals(25 * 60 * 1000L, app.timerDurationMs)
        assertEquals(25 * 60 * 1000L, app.timerRemainingMs)
        app.startTimer()
        assertTrue(app.isTimerRunning)

        // Simulate tick past duration
        app.tick(currentTimeMs = app.timerLastStartMs + 26 * 60 * 1000L)
        assertFalse(app.isTimerRunning)
        assertTrue(app.timerFinished)
        assertEquals(0L, app.timerRemainingMs)

        app.resetTimer()
        assertFalse(app.timerFinished)
        assertEquals(25 * 60 * 1000L, app.timerRemainingMs)
    }

    // --- 4. CalendarApp Tests ---

    @Test
    fun testCalendarMonthNavigationAndEvents() {
        val app = CalendarApp(createDummyManifest("calendar"))
        val startMonth = app.displayMonth
        val startYear = app.displayYear

        app.nextMonth()
        val expectedMonth = (startMonth + 1) % 12
        assertEquals(expectedMonth, app.displayMonth)

        app.prevMonth()
        assertEquals(startMonth, app.displayMonth)
        assertEquals(startYear, app.displayYear)

        // Event management
        val initialEvents = app.events.size
        assertTrue(initialEvents > 0)

        app.addEvent("Yangi majlis", "15:00 - 16:00", "Ish")
        assertEquals(initialEvents + 1, app.events.size)

        val dayEvents = app.getEventsForSelectedDay()
        assertTrue(dayEvents.any { it.title == "Yangi majlis" })

        val added = dayEvents.first { it.title == "Yangi majlis" }
        app.deleteEvent(added.id)
        assertEquals(initialEvents, app.events.size)
    }
}
