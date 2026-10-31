package uz.orbitx.arport.engine.games

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uz.orbitx.arport.engine.apps.TableTennisApp
import uz.orbitx.arport.engine.registry.AppManifest

/**
 * Unit tests for 3D Table Tennis physics, AI bot, and match scoring (TZ 9.3, QADAM 17).
 */
class TableTennisTest {

    private lateinit var app: TableTennisApp
    private lateinit var manifest: AppManifest

    @Before
    fun setUp() {
        manifest = AppManifest(
            id = "pingpong3d",
            name = mapOf("uz" to "3D Stol Tennisi", "en" to "3D Table Tennis"),
            icon = "🏓",
            version = "1.0.0",
            type = "game3d",
            category = "games",
            entry = "uz.orbitx.arport.engine.apps.TableTennisApp",
            defaultAspect = "16:9",
            defaultWidth = 1.2f,
            defaultHeight = 0.675f,
            multiWindow = false,
            order = 17
        )
        app = TableTennisApp(manifest)
        app.isAudioEnabled = false // Disable audio playback during headless tests
    }

    @Test
    fun testTableGeometryAndITTFProportions() {
        assertEquals(1.525f, app.TABLE_WIDTH, 0.001f)
        assertEquals(2.74f, app.TABLE_LENGTH, 0.001f)
        assertEquals(1.525f / 2.0f, app.TABLE_HALF_W, 0.001f)
        assertEquals(0.1525f, app.NET_HEIGHT, 0.001f)
        assertEquals(2.50f, app.TABLE_Z_NET, 0.001f)
        assertTrue(app.TABLE_Z_FAR > app.TABLE_Z_NEAR)
    }

    @Test
    fun testPerspective3DProjection() {
        // Center of table at net level (X = 0, Y = 0, Z = 2.5)
        val pCenter = app.project3D(0f, 0f, app.TABLE_Z_NET)
        assertEquals(app.surfaceWidth / 2f, pCenter.x, 1.0f)
        assertTrue(pCenter.y > 0f && pCenter.y < app.surfaceHeight)

        // Near edge vs Far edge: near edge has higher scale (larger Y on screen / foreshortening)
        val pNear = app.project3D(0f, 0f, app.TABLE_Z_NEAR)
        val pFar = app.project3D(0f, 0f, app.TABLE_Z_FAR)
        assertTrue("Near table edge should be rendered lower on screen than far edge", pNear.y > pFar.y)
    }

    @Test
    fun testServeAndBallState() {
        assertEquals(TableTennisApp.GameState.SERVE_WAIT, app.gameState)
        assertEquals(TableTennisApp.PlayerSide.PLAYER, app.server)

        // Trigger serve
        app.serveBall()
        assertEquals(TableTennisApp.GameState.IN_RALLY, app.gameState)
        assertTrue("Ball forward velocity Vz must be positive on player serve", app.ballVz > 3.0f)
        assertTrue("Ball upward velocity Vy must be positive", app.ballVy > 0.5f)
        assertEquals(TableTennisApp.PlayerSide.PLAYER, app.lastHitter)
    }

    @Test
    fun testGravityAndPhysicsStep() {
        app.serveBall()
        val initialVy = app.ballVy

        // Step physics forward 0.02s
        app.updatePhysics(0.02f)
        assertTrue("Gravity must reduce upward vertical velocity Vy", app.ballVy < initialVy)
        assertTrue("Ball Z position must advance forward", app.ballZ > 1.0f)
    }

    @Test
    fun testTableSurfaceBounce() {
        app.gameState = TableTennisApp.GameState.IN_RALLY
        app.ballX = 0.0f
        app.ballZ = 2.0f // Within table bounds
        app.ballY = 0.035f // Hitting table surface
        app.ballVy = -2.5f // Moving downward
        app.ballVz = 3.0f

        app.updatePhysics(0.016f)

        assertTrue("Ball Vy must reverse upward after table bounce", app.ballVy > 0f)
        assertTrue("Ball Y must be restored above table level", app.ballY >= app.TABLE_Y + app.ballRadius)
        assertEquals(1, app.playerTableBounces)
    }

    @Test
    fun testNetCollision() {
        app.gameState = TableTennisApp.GameState.IN_RALLY
        app.ballX = 0.0f
        app.ballZ = app.TABLE_Z_NET // At the net
        app.ballY = 0.08f // Below net height 0.1525m
        app.ballVz = 4.0f // Moving forward

        app.updatePhysics(0.016f)

        assertTrue("Ball Vz must bounce backward when colliding into the net", app.ballVz < 0f)
    }

    @Test
    fun testPlayerPaddleStrike() {
        app.gameState = TableTennisApp.GameState.IN_RALLY
        app.lastHitter = TableTennisApp.PlayerSide.BOT
        app.paddleX = 0.10f
        app.paddleY = 0.30f

        // Ball arriving at player baseline
        app.ballX = 0.12f
        app.ballY = 0.31f
        app.ballZ = app.paddleZ
        app.ballVz = -3.5f // Coming from opponent

        app.updatePhysics(0.016f)

        assertEquals("Player must now be the last hitter", TableTennisApp.PlayerSide.PLAYER, app.lastHitter)
        assertTrue("Ball must fly forward toward opponent", app.ballVz > 3.0f)
    }

    @Test
    fun testBotAITrackingAcrossDifficulties() {
        for (diff in TableTennisApp.Difficulty.values()) {
            app.difficulty = diff
            app.gameState = TableTennisApp.GameState.IN_RALLY
            app.ballX = 0.45f
            app.ballY = 0.35f
            app.ballZ = 3.2f
            app.ballVz = 3.0f // Heading towards bot
            app.botPaddleX = 0.0f

            val initialBotX = app.botPaddleX
            app.updatePhysics(0.05f)

            assertTrue("Bot paddle must move towards ball X for $diff", app.botPaddleX > initialBotX)
        }
    }

    @Test
    fun test11PointScoringRulesAndDeuce() {
        app.playerScore = 10
        app.botScore = 10
        app.gameState = TableTennisApp.GameState.IN_RALLY

        // Ball out behind bot -> point to player
        app.lastHitter = TableTennisApp.PlayerSide.PLAYER
        app.ballZ = 5.2f // Out behind bot baseline
        app.botTableBounces = 1
        app.updatePhysics(0.016f)

        assertEquals(11, app.playerScore)
        assertEquals(10, app.botScore)
        assertEquals(TableTennisApp.GameState.SERVE_WAIT, app.gameState) // Game continues due to 2-point rule!

        // Another point to player -> 12-10 Win!
        app.gameState = TableTennisApp.GameState.IN_RALLY
        app.lastHitter = TableTennisApp.PlayerSide.PLAYER
        app.ballZ = 5.2f
        app.botTableBounces = 1
        app.updatePhysics(0.016f)

        assertEquals(12, app.playerScore)
        assertEquals(TableTennisApp.GameState.GAME_OVER, app.gameState)
        assertEquals(TableTennisApp.PlayerSide.PLAYER, app.winner)
    }

    @Test
    fun testPointerEventControlsAndPaddleMapping() {
        // Center pointer (u = 0.5, v = 0.5)
        app.onPointerEvent(0.5f, 0.5f, MotionEvent.ACTION_MOVE)
        assertEquals(0.0f, app.paddleX, 0.05f)

        // Right pointer (u = 0.85)
        app.onPointerEvent(0.85f, 0.5f, MotionEvent.ACTION_MOVE)
        assertTrue("Paddle must move right when u > 0.5", app.paddleX > 0.5f)

        // Left pointer (u = 0.15)
        app.onPointerEvent(0.15f, 0.5f, MotionEvent.ACTION_MOVE)
        assertTrue("Paddle must move left when u < 0.5", app.paddleX < -0.5f)
    }

    @Test
    fun testDifficultyAndAudioButtons() {
        val osonU = 110f / app.surfaceWidth
        val osonV = 1.0f - (40f / app.surfaceHeight)
        app.onPointerEvent(osonU, osonV, MotionEvent.ACTION_UP)
        assertEquals(TableTennisApp.Difficulty.OSON, app.difficulty)

        val qiyinU = 340f / app.surfaceWidth
        val qiyinV = 1.0f - (40f / app.surfaceHeight)
        app.onPointerEvent(qiyinU, qiyinV, MotionEvent.ACTION_UP)
        assertEquals(TableTennisApp.Difficulty.QIYIN, app.difficulty)

        val audioU = 1035f / app.surfaceWidth
        val audioV = 1.0f - (40f / app.surfaceHeight)
        val initialAudio = app.isAudioEnabled
        app.onPointerEvent(audioU, audioV, MotionEvent.ACTION_UP)
        assertEquals(!initialAudio, app.isAudioEnabled)
    }

    @Test
    fun testResetMatch() {
        app.playerScore = 7
        app.botScore = 5
        app.gameState = TableTennisApp.GameState.IN_RALLY

        app.resetMatch()
        assertEquals(0, app.playerScore)
        assertEquals(0, app.botScore)
        assertEquals(TableTennisApp.GameState.SERVE_WAIT, app.gameState)
        assertEquals(TableTennisApp.PlayerSide.PLAYER, app.server)
    }
}

