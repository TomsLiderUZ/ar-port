package uz.orbitx.arport.engine.window

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowTest {

    private val epsilon = 0.001f

    @Test
    fun testWindowAspectDimensions() {
        val win = SpatialWindow(
            appId = "test",
            title = "Test Window",
            widthMeters = 1.6f,
            aspect = WindowAspect.LOCKED_16_9
        )
        assertEquals(0.9f, win.heightMeters, epsilon)

        win.updateAspect(WindowAspect.LOCKED_9_16)
        assertEquals(1.6f * (16f / 9f), win.heightMeters, epsilon)
    }

    @Test
    fun testWindowHitTesting() {
        val win = SpatialWindow(
            appId = "test",
            title = "Target",
            posX = 0.0f, posY = 0.0f, posZ = -1.5f,
            widthMeters = 1.0f, heightMeters = 0.5625f
        )

        // 1. Ray aiming directly at the center of the window
        val centerHit = win.hitTest(0f, 0f, 0f, 0f, 0f, -1f)
        assertNotNull(centerHit)
        assertEquals(SpatialWindow.WindowComponent.CONTENT_AREA, centerHit?.component)

        // 2. Ray aiming at top-right (close button)
        // Window width=1.0, height=0.5625 at Z=-1.5
        // Top-right is around x=+0.45, y=+0.25
        val closeHit = win.hitTest(0f, 0f, 0f, 0.45f / 1.5f, 0.25f / 1.5f, -1f)
        assertNotNull(closeHit)
        assertEquals(SpatialWindow.WindowComponent.CLOSE_BUTTON, closeHit?.component)

        // 3. Ray aiming far away (miss)
        val missHit = win.hitTest(0f, 0f, 0f, 2.0f, 2.0f, -1f)
        assertNull(missHit)
    }

    @Test
    fun testWindowManagerDefaultWindows() {
        val wm = WindowManager3D()
        assertEquals(3, wm.activeWindows.size)

        val monitorWin = wm.activeWindows.find { it.appId == "system_monitor" }
        assertNotNull(monitorWin)
        assertTrue(monitorWin?.isFocused == true)

        val notesWin = wm.activeWindows.find { it.appId == "spatial_notes" }
        assertNotNull(notesWin)
        assertEquals(WindowAspect.LOCKED_9_16, notesWin?.aspect)
    }

    @Test
    fun testAntiOverlapPlacement() {
        val wm = WindowManager3D()
        val originalCount = wm.activeWindows.size

        val newWin = wm.createWindow("new_app", "New App", width = 0.8f, height = 0.45f)
        assertEquals(originalCount + 1, wm.activeWindows.size)

        // New window should not be placed at x=0.0 because system_monitor is already at x=0.0
        assertTrue(kotlin.math.abs(newWin.posX) > 0.3f)
    }

    @Test
    fun testFocusAndZOrder() {
        val wm = WindowManager3D()
        val notesWin = wm.activeWindows.find { it.appId == "spatial_notes" }!!

        assertFalse(notesWin.isFocused)
        val oldZ = notesWin.zOrder

        wm.setFocus(notesWin.id)
        assertTrue(notesWin.isFocused)
        assertTrue(notesWin.zOrder > oldZ)
    }

    @Test
    fun testCloseAndMinimize() {
        val wm = WindowManager3D()
        val mediaWin = wm.activeWindows.find { it.appId == "media_player" }!!

        wm.minimizeWindow(mediaWin.id)
        assertFalse(wm.activeWindows.contains(mediaWin))

        wm.restoreWindow(mediaWin.id)
        assertTrue(wm.activeWindows.contains(mediaWin))

        wm.closeWindow(mediaWin.id)
        assertFalse(wm.allWindows.contains(mediaWin))
    }

    @Test
    fun testSleepPolicyOnLimitExceeded() {
        val wm = WindowManager3D(maxWindowsLimit = 3)
        // Default has 3 windows, all active
        assertFalse(wm.activeWindows.any { it.isSleeping })

        // Adding 4th window exceeds limit of 3
        wm.createWindow("overflow_app", "Overflow App")
        wm.enforceSleepPolicy()

        // At least one unfocused window must be put to sleep
        val sleepingWindows = wm.activeWindows.filter { it.isSleeping }
        assertTrue(sleepingWindows.isNotEmpty())
        assertFalse(sleepingWindows.any { it.isFocused })
    }
}

