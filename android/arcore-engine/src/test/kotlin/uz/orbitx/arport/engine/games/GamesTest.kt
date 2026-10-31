package uz.orbitx.arport.engine.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uz.orbitx.arport.engine.registry.AppRegistry
import uz.orbitx.arport.engine.sandbox.NavigationDecision
import uz.orbitx.arport.engine.sandbox.SandboxPolicy
import uz.orbitx.arport.engine.web.WebAppSurface
import uz.orbitx.arport.engine.window.WindowAspect
import uz.orbitx.arport.engine.window.WindowManager3D
import java.io.File

/**
 * Unit tests for 2D Games and AR Chess (QADAM 16).
 */
class GamesTest {

    private lateinit var appRegistry: AppRegistry
    private lateinit var windowManager: WindowManager3D

    @Before
    fun setUp() {
        appRegistry = AppRegistry(null)
        windowManager = WindowManager3D(maxWindowsLimit = 8)
    }

    @Test
    fun testGameManifests() {
        val games = listOf("tetris", "dino", "flappy", "snake", "g2048", "chess")
        for (gameId in games) {
            val manifest = appRegistry.getApp(gameId)
            assertNotNull("Manifest for $gameId must exist", manifest)
            assertEquals("games", manifest!!.category)
            assertTrue("Type must be game2d or game", manifest.type == "game2d" || manifest.type == "game")
            assertTrue("Entry must not be empty", manifest.entry.isNotBlank())
            assertTrue("Default aspect must be valid", manifest.defaultAspect in listOf("16:9", "9:16", "free"))
        }
    }

    @Test
    fun testResolveEntryUrl() {
        assertEquals(
            "file:///android_asset/games/tetris/index.html",
            WebAppSurface.resolveEntryUrl("assets/games/tetris/index.html")
        )
        assertEquals(
            "file:///android_asset/games/chess/index.html",
            WebAppSurface.resolveEntryUrl("games/chess/index.html")
        )
        assertEquals(
            "https://www.chess.com/play",
            WebAppSurface.resolveEntryUrl("https://www.chess.com/play")
        )
        assertEquals(
            "file:///android_asset/custom/index.html",
            WebAppSurface.resolveEntryUrl("file:///android_asset/custom/index.html")
        )
    }

    @Test
    fun testSandboxPolicyForGames() {
        // 1. Local Android asset files are allowed
        val assetDecision = SandboxPolicy.evaluateNavigation(
            "file:///android_asset/games/tetris/index.html",
            emptyList()
        )
        assertEquals(NavigationDecision.ALLOW_IN_CURRENT_WINDOW, assetDecision)

        // 2. Prohibited file schemes (e.g. system files) are blocked
        val rootFileDecision = SandboxPolicy.evaluateNavigation(
            "file:///etc/hosts",
            emptyList()
        )
        assertEquals(NavigationDecision.BLOCK, rootFileDecision)

        // 3. Chess.com domain allowed when in allowedHosts
        val chessAllowed = SandboxPolicy.evaluateNavigation(
            "https://www.chess.com/play",
            listOf("chess.com", "*.chess.com")
        )
        assertEquals(NavigationDecision.ALLOW_IN_CURRENT_WINDOW, chessAllowed)

        // 4. External domain routed to browser
        val externalDecision = SandboxPolicy.evaluateNavigation(
            "https://untrusted-site.com/game",
            listOf("chess.com", "*.chess.com")
        )
        assertEquals(NavigationDecision.OPEN_IN_BROWSER_TAB, externalDecision)
    }

    @Test
    fun testGameHtmlFilesExistAndContainGestureHooks() {
        val gameFolders = listOf("tetris", "dino", "flappy", "snake", "g2048", "chess")
        for (folder in gameFolders) {
            val file = listOf(
                File("src/main/assets/games/$folder/index.html"),
                File("android/arcore-engine/src/main/assets/games/$folder/index.html"),
                File("apps/games/$folder/index.html"),
                File("../../apps/games/$folder/index.html")
            ).firstOrNull { it.exists() }
            assertNotNull("File for $folder/index.html must exist in project or assets", file)
            val content = file!!.readText()
            assertTrue("File $folder/index.html must have <!DOCTYPE html>", content.contains("<!DOCTYPE html>"))
            assertTrue("File $folder/index.html must declare onArGesture hook", content.contains("window.onArGesture"))
            assertTrue("File $folder/index.html must have AudioContext synth", content.contains("AudioContext"))
            assertTrue("File $folder/index.html must have local storage", content.contains("localStorage"))
        }
    }

    @Test
    fun testAppRegistryLaunchGame2D() {
        val initialCount = windowManager.activeWindows.size

        // Tetris: 9:16 aspect
        val tetrisWindow = appRegistry.launchApp("tetris", windowManager)
        assertNotNull(tetrisWindow)
        assertEquals("tetris", tetrisWindow!!.appId)
        assertEquals(WindowAspect.LOCKED_9_16, tetrisWindow.aspect)
        assertNotNull(tetrisWindow.contentSurface)

        // Chess: 16:9 aspect
        val chessWindow = appRegistry.launchApp("chess", windowManager)
        assertNotNull(chessWindow)
        assertEquals("chess", chessWindow!!.appId)
        assertEquals(WindowAspect.LOCKED_16_9, chessWindow.aspect)
        assertNotNull(chessWindow.contentSurface)

        // Dino: 16:9 aspect
        val dinoWindow = appRegistry.launchApp("dino", windowManager)
        assertNotNull(dinoWindow)
        assertEquals("dino", dinoWindow!!.appId)
        assertEquals(WindowAspect.LOCKED_16_9, dinoWindow.aspect)

        assertEquals(initialCount + 3, windowManager.activeWindows.size)
    }
}
