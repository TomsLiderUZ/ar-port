package uz.orbitx.arport.engine.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.orbitx.arport.engine.apps.HomeCategory
import uz.orbitx.arport.engine.apps.SpatialDock
import uz.orbitx.arport.engine.apps.SpatialDockAction
import uz.orbitx.arport.engine.apps.SpatialHomePanel
import uz.orbitx.arport.engine.window.WindowAspect
import uz.orbitx.arport.engine.window.WindowManager3D

class RegistryTest {

    @Test
    fun testAppRegistryDefaultAppsCount() {
        val registry = AppRegistry()
        assertEquals(18, registry.allApps.size)
    }

    @Test
    fun testAppManifestDetails() {
        val registry = AppRegistry()

        val yt = registry.getApp("youtube")
        assertNotNull(yt)
        assertEquals("YouTube", yt!!.getLocalizedName("uz"))
        assertEquals("media", yt.category)
        assertEquals("web", yt.type)
        assertEquals("16:9", yt.defaultAspect)
        assertTrue(yt.multiWindow)

        val chess = registry.getApp("chess")
        assertNotNull(chess)
        assertEquals("AR Shaxmat", chess!!.getLocalizedName("uz"))
        assertEquals("games", chess.category)
        assertEquals("game2d", chess.type)
        assertFalse(chess.multiWindow)
    }

    @Test
    fun testCategoryFiltering() {
        val registry = AppRegistry()

        val games = registry.getAppsByCategory("games")
        assertEquals(8, games.size)
        assertTrue(games.any { it.id == "tetris" })
        assertTrue(games.any { it.id == "chess" })
        assertTrue(games.any { it.id == "pingpong3d" })

        val social = registry.getAppsByCategory("social")
        assertEquals(2, social.size)
        assertTrue(social.any { it.id == "telegram" })
        assertTrue(social.any { it.id == "instagram" })

        val media = registry.getAppsByCategory("media")
        assertEquals(2, media.size)
        assertTrue(media.any { it.id == "youtube" })
        assertTrue(media.any { it.id == "spotify" })
    }

    @Test
    fun testSearchApps() {
        val registry = AppRegistry()

        val searchResult1 = registry.searchApps("shaxmat")
        assertEquals(1, searchResult1.size)
        assertEquals("chess", searchResult1[0].id)

        val searchResult2 = registry.searchApps("dino")
        assertEquals(1, searchResult2.size)
        assertEquals("dino", searchResult2[0].id)
    }

    @Test
    fun testLaunchAppCreatesSpatialWindow() {
        val registry = AppRegistry()
        val wm = WindowManager3D()

        val win = registry.launchApp("youtube", wm)
        assertNotNull(win)
        assertEquals("youtube", win!!.appId)
        assertEquals("YouTube", win.title)
        assertEquals(WindowAspect.LOCKED_16_9, win.aspect)
        assertTrue(wm.activeWindows.any { it.id == win.id })
    }

    @Test
    fun testMultiWindowPolicy() {
        val registry = AppRegistry()
        val wm = WindowManager3D()

        // Telegram has multiWindow = false
        val win1 = registry.launchApp("telegram", wm)
        val win2 = registry.launchApp("telegram", wm)

        assertNotNull(win1)
        assertNotNull(win2)
        assertEquals("Launching single-window app twice should return existing window", win1!!.id, win2!!.id)
    }

    @Test
    fun testSpatialDockHitTesting() {
        val dock = SpatialDock()

        // 6 buttons: HOME, RECENTER, VOLUME, BRIGHTNESS, KEYBOARD, EXIT
        assertEquals(SpatialDockAction.HOME, dock.hitTest(0.05f, 0.5f))
        assertEquals(SpatialDockAction.RECENTER, dock.hitTest(0.20f, 0.5f))
        assertEquals(SpatialDockAction.VOLUME, dock.hitTest(0.38f, 0.5f))
        assertEquals(SpatialDockAction.BRIGHTNESS, dock.hitTest(0.55f, 0.5f))
        assertEquals(SpatialDockAction.KEYBOARD, dock.hitTest(0.72f, 0.5f))
        assertEquals(SpatialDockAction.EXIT, dock.hitTest(0.95f, 0.5f))

        var receivedAction: SpatialDockAction? = null
        dock.onDockAction = { receivedAction = it }
        dock.onDockHit(0.05f, 0.5f)
        assertEquals(SpatialDockAction.HOME, receivedAction)
    }

    @Test
    fun testSpatialHomePanelCategoryAndGridHit() {
        val registry = AppRegistry()
        val home = SpatialHomePanel(registry)

        assertEquals(HomeCategory.ALL, home.activeCategory)
        assertEquals(18, home.getFilteredApps().size)

        // Hit category tab (v in 0.82 .. 0.94)
        // Tab 2: GAMES (around u = 0.55)
        val tabHit = home.onHomeHit(0.55f, 0.88f)
        assertEquals(HomeCategory.GAMES, tabHit)
        assertEquals(HomeCategory.GAMES, home.activeCategory)
        assertEquals(8, home.getFilteredApps().size)

        // Hit first app in grid (row 0, col 0)
        val app = home.hitAppGrid(0.08f, 0.72f)
        assertNotNull("First app in games grid should be hit", app)
        assertEquals("tetris", app!!.id)
    }
}

