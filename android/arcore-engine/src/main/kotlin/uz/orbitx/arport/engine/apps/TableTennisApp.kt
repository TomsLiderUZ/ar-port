package uz.orbitx.arport.engine.apps

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.MotionEvent
import uz.orbitx.arport.engine.registry.ARApp
import uz.orbitx.arport.engine.registry.AppManifest
import uz.orbitx.arport.engine.window.ContentSurface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Native 3D Table Tennis application with physics, 3-level AI bot,
 * and Apple VisionOS Liquid Glass HUD (TZ 9.3, PROMPT QADAM 17).
 */
class TableTennisApp(
    override val manifest: AppManifest
) : ARApp {

    override val id: String = manifest.id

    enum class Difficulty(val title: String, val speed: Float, val reactionSec: Float, val accuracy: Float) {
        OSON("Oson", 2.6f, 0.20f, 0.78f),
        ORTA("O'rta", 4.8f, 0.08f, 0.90f),
        QIYIN("Qiyin", 7.6f, 0.02f, 0.98f)
    }

    enum class GameState {
        SERVE_WAIT,
        IN_RALLY,
        POINT_SCORED,
        GAME_OVER
    }

    enum class PlayerSide {
        PLAYER,
        BOT
    }

    // Match & Score state
    var difficulty: Difficulty = Difficulty.ORTA
    var gameState: GameState = GameState.SERVE_WAIT
    var server: PlayerSide = PlayerSide.PLAYER
    var playerScore: Int = 0
    var botScore: Int = 0
    var playerSets: Int = 0
    var botSets: Int = 0
    var winner: PlayerSide? = null
    var statusMessage: String = "To'pni kiritish uchun bosing"
    var isAudioEnabled: Boolean = true

    // Table Geometry (Standard ITTF proportions in meters)
    val TABLE_WIDTH = 1.525f
    val TABLE_LENGTH = 2.74f
    val TABLE_HALF_W = TABLE_WIDTH / 2.0f // 0.7625m
    val TABLE_Z_NEAR = 1.13f
    val TABLE_Z_FAR = 3.87f
    val TABLE_Z_NET = 2.50f
    val NET_HEIGHT = 0.1525f
    val TABLE_Y = 0.0f
    val GRAVITY = 9.8f

    // 3D Ball Physics State
    var ballX: Float = 0.0f
    var ballY: Float = 0.35f
    var ballZ: Float = 1.0f
    var ballVx: Float = 0.0f
    var ballVy: Float = 0.0f
    var ballVz: Float = 0.0f
    var ballSpinX: Float = 0.0f // Topspin / Backspin
    var ballSpinY: Float = 0.0f // Sidespin
    val ballRadius: Float = 0.038f // 38-40mm Ping Pong Ball

    var lastHitter: PlayerSide? = null
    var playerTableBounces: Int = 0
    var botTableBounces: Int = 0

    // Trajectory history for motion trail
    private val ballTrail = mutableListOf<Point2D>()
    private val maxTrailPoints = 12

    // Player Paddle State (controlled by hand ray / pointer)
    var paddleX: Float = 0.0f
    var paddleY: Float = 0.25f
    val paddleZ: Float = 0.85f
    private var prevPaddleX: Float = 0.0f
    private var prevPaddleY: Float = 0.25f
    var paddleSpeedX: Float = 0.0f
    var paddleSpeedY: Float = 0.0f
    var paddleTilt: Float = 0.0f

    // Bot Paddle State (AI opponent)
    var botPaddleX: Float = 0.0f
    var botPaddleY: Float = 0.25f
    val botPaddleZ: Float = 4.15f
    private var botReactionTimer: Float = 0.0f

    // Surface & Sizing
    var surface: ContentSurface? = null
        private set
    var surfaceWidth: Int = 1280
    var surfaceHeight: Int = 720

    private var appContext: Context? = null

    // UI Clickable button bounds
    private val btnOsonRect = RectF(60f, 15f, 160f, 65f)
    private val btnOrtaRect = RectF(175f, 15f, 275f, 65f)
    private val btnQiyinRect = RectF(290f, 15f, 390f, 65f)
    private val btnServeRect = RectF(490f, 640f, 790f, 705f)
    private val btnRestartRect = RectF(1100f, 15f, 1220f, 65f)
    private val btnAudioRect = RectF(990f, 15f, 1080f, 65f)

    override fun createSurface(context: Context, width: Int, height: Int): ContentSurface {
        appContext = context
        surfaceWidth = width
        surfaceHeight = height
        val surf = ContentSurface(width, height)
        surface = surf

        resetBallForServe(PlayerSide.PLAYER)
        render()
        return surf
    }

    override fun onPointerEvent(u: Float, v: Float, action: Int) {
        val px = u * surfaceWidth
        val py = (1.0f - v) * surfaceHeight

        // 1. Handle UI button taps
        if (action == MotionEvent.ACTION_UP) {
            if (px in 60f..160f && py in 15f..65f) {
                difficulty = Difficulty.OSON
                playSound(SoundType.CLICK)
                render()
                return
            }
            if (px in 175f..275f && py in 15f..65f) {
                difficulty = Difficulty.ORTA
                playSound(SoundType.CLICK)
                render()
                return
            }
            if (px in 290f..390f && py in 15f..65f) {
                difficulty = Difficulty.QIYIN
                playSound(SoundType.CLICK)
                render()
                return
            }
            if (px in 990f..1080f && py in 15f..65f) {
                isAudioEnabled = !isAudioEnabled
                playSound(SoundType.CLICK)
                render()
                return
            }
            if (px in 1100f..1220f && py in 15f..65f) {
                resetMatch()
                playSound(SoundType.CLICK)
                render()
                return
            }
            if (gameState == GameState.SERVE_WAIT && server == PlayerSide.PLAYER) {
                if ((px in 490f..790f && py in 640f..705f) || py > 450f) {
                    serveBall()
                    return
                }
            }
            if (gameState == GameState.GAME_OVER) {
                resetMatch()
                return
            }
        }

        // 2. Map pointer coordinates (u, v) to Player 3D Paddle
        // u [0..1] -> [-1.1m .. +1.1m] horizontal table reach
        // v [0..1] -> [-0.2m .. +0.9m] vertical table reach
        val targetX = (u - 0.5f) * 2.3f
        val targetY = ((1.0f - v) - 0.35f) * 1.5f

        paddleSpeedX = (targetX - prevPaddleX) / 0.016f
        paddleSpeedY = (targetY - prevPaddleY) / 0.016f

        prevPaddleX = paddleX
        prevPaddleY = paddleY
        paddleX = targetX.coerceIn(-1.3f, 1.3f)
        paddleY = targetY.coerceIn(-0.1f, 0.95f)
        paddleTilt = (-paddleSpeedX * 0.08f).coerceIn(-0.6f, 0.6f)

        // If player taps during rally in normal touch mode, also check paddle hit
        if (action == MotionEvent.ACTION_DOWN && gameState == GameState.IN_RALLY) {
            checkPlayerPaddleCollision(isSwipe = true)
        }
    }

    override fun onUpdate(dtSec: Float) {
        val dt = dtSec.coerceIn(0.001f, 0.04f)
        updatePhysics(dt)
        render()
    }

    /**
     * Stepper for 3D physics and game rules simulation.
     */
    fun updatePhysics(dtSec: Float) {
        if (gameState == GameState.SERVE_WAIT) {
            // Keep ball hovering at serving position
            if (server == PlayerSide.PLAYER) {
                ballX = paddleX * 0.85f
                ballY = paddleY + 0.12f
                ballZ = paddleZ + 0.18f
            } else {
                ballX = botPaddleX * 0.85f
                ballY = botPaddleY + 0.12f
                ballZ = botPaddleZ - 0.18f
                botReactionTimer += dtSec
                if (botReactionTimer >= 1.1f) {
                    serveBall()
                }
            }
            return
        }

        if (gameState != GameState.IN_RALLY) {
            return
        }

        // 1. Advance Ball Position with Velocity
        ballVy -= GRAVITY * dtSec
        // Magnus effect: spin curves the trajectory
        ballVy += ballSpinX * 0.45f * dtSec
        ballVx += ballSpinY * 0.45f * dtSec

        // Air drag
        ballVx *= (1.0f - 0.04f * dtSec)
        ballVy *= (1.0f - 0.04f * dtSec)
        ballVz *= (1.0f - 0.03f * dtSec)

        ballX += ballVx * dtSec
        ballY += ballVy * dtSec
        ballZ += ballVz * dtSec

        // Record motion trail
        val pt = project3D(ballX, ballY, ballZ)
        ballTrail.add(pt)
        if (ballTrail.size > maxTrailPoints) {
            ballTrail.removeAt(0)
        }

        // 2. Table Bounce Collision (Y = TABLE_Y)
        if (ballY <= TABLE_Y + ballRadius && ballVy < 0f) {
            // Check if within 3D table rectangular boundary
            val isOverTable = (ballZ in TABLE_Z_NEAR..TABLE_Z_FAR) && (abs(ballX) <= TABLE_HALF_W)
            if (isOverTable) {
                ballY = TABLE_Y + ballRadius
                ballVy = -ballVy * 0.86f // Elastic restitution
                ballVx += ballSpinY * 0.15f
                ballSpinX *= 0.65f
                ballSpinY *= 0.65f

                playSound(SoundType.TABLE_BOUNCE)

                // Track which side of the net the ball bounced on
                if (ballZ < TABLE_Z_NET) {
                    playerTableBounces++
                    if (playerTableBounces > 1) {
                        // Double bounce on player side -> Bot scores!
                        awardPoint(PlayerSide.BOT, "Ikki marta urilish (Player)")
                        return
                    }
                } else {
                    botTableBounces++
                    if (botTableBounces > 1) {
                        // Double bounce on bot side -> Player scores!
                        awardPoint(PlayerSide.PLAYER, "Ikki marta urilish (Bot)")
                        return
                    }
                }
            } else {
                // Ball landed on floor outside the table!
                if (ballZ < TABLE_Z_NET) {
                    if (lastHitter == PlayerSide.BOT && playerTableBounces == 0) {
                        awardPoint(PlayerSide.PLAYER, "Bot to'pni tashqariga urdi (Out)")
                    } else {
                        awardPoint(PlayerSide.BOT, "Tashqariga tushdi (Out)")
                    }
                } else {
                    if (lastHitter == PlayerSide.PLAYER && botTableBounces == 0) {
                        awardPoint(PlayerSide.BOT, "Siz to'pni tashqariga urdingiz (Out)")
                    } else {
                        awardPoint(PlayerSide.PLAYER, "Tashqariga tushdi (Out)")
                    }
                }
                return
            }
        }

        // 3. Net Collision (Z = TABLE_Z_NET)
        if (abs(ballZ - TABLE_Z_NET) < 0.08f) {
            if (ballY < NET_HEIGHT && abs(ballX) <= TABLE_HALF_W + 0.1f) {
                // Ball hit the net!
                ballVz = -ballVz * 0.35f
                ballVy *= 0.5f
                playSound(SoundType.NET_HIT)
            }
        }

        // 4. Player Paddle Hit Check (Near baseline Z = paddleZ)
        if (ballZ <= paddleZ + 0.16f && ballZ >= paddleZ - 0.12f && ballVz < 0f) {
            checkPlayerPaddleCollision(isSwipe = false)
        }

        // 5. Bot AI Movement & Hit Check
        updateBotAI(dtSec)

        // 6. Out of Bounds baseline checks
        if (ballZ < 0.25f) {
            // Ball passed behind player
            if (playerTableBounces == 1 && lastHitter == PlayerSide.BOT) {
                awardPoint(PlayerSide.BOT, "O'tkazib yuborildi")
            } else if (lastHitter == PlayerSide.BOT && playerTableBounces == 0) {
                awardPoint(PlayerSide.PLAYER, "Bot autga urdi")
            } else {
                awardPoint(PlayerSide.BOT, "To'p o'tkazildi")
            }
        } else if (ballZ > 4.75f) {
            // Ball passed behind bot
            if (botTableBounces == 1 && lastHitter == PlayerSide.PLAYER) {
                awardPoint(PlayerSide.PLAYER, "Siz ajoyib zarba berdingiz!")
            } else if (lastHitter == PlayerSide.PLAYER && botTableBounces == 0) {
                awardPoint(PlayerSide.BOT, "Autga ketdi")
            } else {
                awardPoint(PlayerSide.PLAYER, "Bot to'pni qaytara olmadi")
            }
        }
    }

    private fun checkPlayerPaddleCollision(isSwipe: Boolean) {
        val dx = ballX - paddleX
        val dy = ballY - paddleY
        val dist = sqrt(dx * dx + dy * dy)

        // Paddle radius ~ 0.20m
        if (dist <= 0.24f) {
            // Player successfully strikes the ball!
            lastHitter = PlayerSide.PLAYER
            playerTableBounces = 0
            botTableBounces = 0

            val smashBoost = if (isSwipe || paddleSpeedY > 2.0f) 2.2f else 1.0f
            ballVz = (5.2f + abs(paddleSpeedY) * 0.4f) * smashBoost
            ballVx = dx * 8.2f + paddleSpeedX * 0.25f
            ballVy = (1.8f - dy * 3.5f).coerceIn(0.8f, 3.2f)
            ballSpinX = paddleSpeedY * 2.2f
            ballSpinY = -paddleSpeedX * 1.8f

            playSound(SoundType.PADDLE_HIT)
            statusMessage = "Ralli: Qaytardingiz! 🏓"
        }
    }

    private fun updateBotAI(dtSec: Float) {
        // AI bot tracking logic based on difficulty
        val targetX = if (ballVz > 0f) {
            when (difficulty) {
                Difficulty.OSON -> {
                    // Slight delay and inaccuracy
                    ballX * 0.85f
                }
                Difficulty.ORTA -> {
                    // Good accuracy
                    ballX
                }
                Difficulty.QIYIN -> {
                    // Anticipate trajectory
                    val timeToReach = (botPaddleZ - ballZ) / ballVz.coerceAtLeast(0.1f)
                    (ballX + ballVx * timeToReach).coerceIn(-TABLE_HALF_W, TABLE_HALF_W)
                }
            }
        } else {
            0.0f // Return to center when waiting
        }

        val targetY = (ballY + 0.05f).coerceIn(0.1f, 0.7f)

        // Move bot paddle smoothly toward target
        val moveSpeed = difficulty.speed
        val stepX = (targetX - botPaddleX).coerceIn(-moveSpeed * dtSec, moveSpeed * dtSec)
        val stepY = (targetY - botPaddleY).coerceIn(-moveSpeed * dtSec, moveSpeed * dtSec)
        botPaddleX += stepX
        botPaddleY += stepY

        // Bot Paddle Hit Check
        if (ballZ >= botPaddleZ - 0.16f && ballZ <= botPaddleZ + 0.12f && ballVz > 0f) {
            val dx = ballX - botPaddleX
            val dy = ballY - botPaddleY
            val dist = sqrt(dx * dx + dy * dy)

            if (dist <= 0.25f && (Math.random() < difficulty.accuracy)) {
                // Bot strikes ball back to player!
                lastHitter = PlayerSide.BOT
                playerTableBounces = 0
                botTableBounces = 0

                // Bot aims towards player's weak side
                val aimOffset = if (paddleX > 0f) -0.35f else 0.35f
                ballVz = - (4.8f + (if (difficulty == Difficulty.QIYIN) 1.6f else 0.5f))
                ballVx = (aimOffset - dx * 2.5f) * (if (difficulty == Difficulty.QIYIN) 1.5f else 1.0f)
                ballVy = (1.7f + (if (difficulty == Difficulty.QIYIN) 0.5f else 0.2f))
                ballSpinX = if (difficulty == Difficulty.QIYIN) -3.5f else 0.0f

                playSound(SoundType.PADDLE_HIT)
                statusMessage = "Bot qaytardi! Ehtiyot bo'ling!"
            }
        }
    }

    /**
     * Serves the ball forward according to whose turn it is.
     */
    fun serveBall() {
        if (gameState != GameState.SERVE_WAIT) return

        lastHitter = server
        playerTableBounces = 0
        botTableBounces = 0
        gameState = GameState.IN_RALLY

        if (server == PlayerSide.PLAYER) {
            ballX = paddleX * 0.8f
            ballY = paddleY + 0.15f
            ballZ = paddleZ + 0.15f
            ballVx = (Math.random().toFloat() - 0.5f) * 0.8f
            ballVy = 1.6f
            ballVz = 4.6f + (if (difficulty == Difficulty.QIYIN) 0.8f else 0.0f)
            playSound(SoundType.PADDLE_HIT)
            statusMessage = "Kiritildi! O'yin davom etmoqda..."
        } else {
            ballX = botPaddleX * 0.8f
            ballY = botPaddleY + 0.15f
            ballZ = botPaddleZ - 0.15f
            ballVx = (Math.random().toFloat() - 0.5f) * 1.2f
            ballVy = 1.7f
            ballVz = -4.5f
            playSound(SoundType.PADDLE_HIT)
            statusMessage = "Bot kiritdi! To'pni qaytaring!"
        }
    }

    private fun awardPoint(scorer: PlayerSide, reason: String) {
        gameState = GameState.POINT_SCORED
        ballTrail.clear()

        if (scorer == PlayerSide.PLAYER) {
            playerScore++
            playSound(SoundType.CHEER)
            statusMessage = "Ochko Sizga! ($reason) 🎉"
        } else {
            botScore++
            playSound(SoundType.MISS)
            statusMessage = "Ochko Botga! ($reason) ❌"
        }

        // Standard 11 points win condition with 2-point margin
        val totalPoints = playerScore + botScore
        if ((playerScore >= 11 || botScore >= 11) && abs(playerScore - botScore) >= 2) {
            gameState = GameState.GAME_OVER
            if (playerScore > botScore) {
                playerSets++
                winner = PlayerSide.PLAYER
                statusMessage = "G'alaba Qozondingiz! 🏆 $playerScore - $botScore"
            } else {
                botSets++
                winner = PlayerSide.BOT
                statusMessage = "Bot g'alaba qozondi! $botScore - $playerScore"
            }
            return
        }

        // Alternates serves every 2 points (or every 1 point if in deuce >= 10-10)
        val isDeuce = (playerScore >= 10 && botScore >= 10)
        val serveInterval = if (isDeuce) 1 else 2
        server = if ((totalPoints / serveInterval) % 2 == 0) PlayerSide.PLAYER else PlayerSide.BOT

        // Reset for next serve
        resetBallForServe(server)
        gameState = GameState.SERVE_WAIT
        botReactionTimer = 0.0f
    }

    private fun resetBallForServe(serverSide: PlayerSide) {
        ballTrail.clear()
        if (serverSide == PlayerSide.PLAYER) {
            ballX = 0.0f
            ballY = 0.35f
            ballZ = paddleZ + 0.15f
        } else {
            ballX = 0.0f
            ballY = 0.35f
            ballZ = botPaddleZ - 0.15f
        }
        ballVx = 0f
        ballVy = 0f
        ballVz = 0f
        ballSpinX = 0f
        ballSpinY = 0f
        playerTableBounces = 0
        botTableBounces = 0
    }

    fun resetMatch() {
        playerScore = 0
        botScore = 0
        winner = null
        server = PlayerSide.PLAYER
        gameState = GameState.SERVE_WAIT
        statusMessage = "Yangi o'yin! To'pni kiritish uchun bosing"
        resetBallForServe(server)
        render()
    }

    data class Point2D(val x: Float, val y: Float)

    init {
        resetBallForServe(PlayerSide.PLAYER)
    }

    /**
     * Advances simulation in discrete fixed timesteps (useful for tests and fixed-rate physics).
     */
    fun stepPhysics(totalSec: Float, stepDt: Float = 0.016f) {
        var remaining = totalSec
        while (remaining > 0f) {
            val dt = remaining.coerceAtMost(stepDt)
            updatePhysics(dt)
            remaining -= dt
        }
    }

    /**
     * 3D Perspective Projection Mathematics (ITTF 3D spatial coordinate -> 2D screen coordinate).
     */
    fun project3D(x: Float, y: Float, z: Float): Point2D {
        val dCam = 1.35f
        val focalDist = 840f
        val clampedZ = z.coerceAtLeast(0.1f)
        val scale = focalDist / (clampedZ + dCam)

        val screenX = (surfaceWidth / 2f) + (x * scale)
        val yCam = 0.65f
        val screenY = (surfaceHeight / 2f) + 140f - ((y - yCam) * scale)
        return Point2D(screenX, screenY)
    }

    // ==========================================
    // 3D RENDERING & LIQUID GLASS VISUAL ENGINE
    // ==========================================

    fun render() {
        surface?.draw { canvas ->
            // 1. Futuristic Spatial Stadium Horizon Background
            drawBackground(canvas)

            // 2. 3D Ping Pong Table & Net
            draw3DTable(canvas)

            // 3. Motion Trail & 3D Ping Pong Ball with Shadow
            drawBallAndShadow(canvas)

            // 4. Opponent and Player 3D Paddles
            drawPaddles(canvas)

            // 5. VisionOS Liquid Glass HUD (Scoreboard, Controls, Badges)
            drawLiquidGlassHUD(canvas)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        // Deep spatial vignette gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, surfaceHeight.toFloat(),
                Color.parseColor("#090B10"),
                Color.parseColor("#151C28"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), bgPaint)

        // Subtle glowing cyber horizon grid
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A64D2FF")
            strokeWidth = 1.2f
        }

        val horizonY = surfaceHeight * 0.42f
        for (i in -8..8) {
            val startX = (surfaceWidth / 2f) + (i * 120f)
            canvas.drawLine(surfaceWidth / 2f, horizonY, startX, surfaceHeight.toFloat(), gridPaint)
        }
        for (j in 0..5) {
            val y = horizonY + (j * 55f)
            canvas.drawLine(0f, y, surfaceWidth.toFloat(), y, gridPaint)
        }
    }

    private fun draw3DTable(canvas: Canvas) {
        // Table 4 Corners in 3D: Near-Left, Near-Right, Far-Right, Far-Left
        val pNearLeft = project3D(-TABLE_HALF_W, TABLE_Y, TABLE_Z_NEAR)
        val pNearRight = project3D(TABLE_HALF_W, TABLE_Y, TABLE_Z_NEAR)
        val pFarRight = project3D(TABLE_HALF_W, TABLE_Y, TABLE_Z_FAR)
        val pFarLeft = project3D(-TABLE_HALF_W, TABLE_Y, TABLE_Z_FAR)

        // 1. Under-table Soft Shadow
        val shadowPath = Path().apply {
            val sNL = project3D(-TABLE_HALF_W * 1.08f, -0.76f, TABLE_Z_NEAR)
            val sNR = project3D(TABLE_HALF_W * 1.08f, -0.76f, TABLE_Z_NEAR)
            val sFR = project3D(TABLE_HALF_W * 1.08f, -0.76f, TABLE_Z_FAR)
            val sFL = project3D(-TABLE_HALF_W * 1.08f, -0.76f, TABLE_Z_FAR)
            moveTo(sNL.x, sNL.y)
            lineTo(sNR.x, sNR.y)
            lineTo(sFR.x, sFR.y)
            lineTo(sFL.x, sFL.y)
            close()
        }
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4D000000")
        }
        canvas.drawPath(shadowPath, shadowPaint)

        // 2. Table Leg Posts
        val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E2430")
            strokeWidth = 8f
        }
        fun drawLeg(x: Float, z: Float) {
            val top = project3D(x, TABLE_Y, z)
            val btm = project3D(x, -0.74f, z)
            canvas.drawLine(top.x, top.y, btm.x, btm.y, legPaint)
        }
        drawLeg(-TABLE_HALF_W + 0.1f, TABLE_Z_NEAR + 0.1f)
        drawLeg(TABLE_HALF_W - 0.1f, TABLE_Z_NEAR + 0.1f)
        drawLeg(-TABLE_HALF_W + 0.1f, TABLE_Z_FAR - 0.1f)
        drawLeg(TABLE_HALF_W - 0.1f, TABLE_Z_FAR - 0.1f)

        // 3. Table Surface Solid Body (Tournament Deep Royal Blue)
        val tablePath = Path().apply {
            moveTo(pNearLeft.x, pNearLeft.y)
            lineTo(pNearRight.x, pNearRight.y)
            lineTo(pFarRight.x, pFarRight.y)
            lineTo(pFarLeft.x, pFarLeft.y)
            close()
        }
        val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                pFarLeft.x, pFarLeft.y, pNearLeft.x, pNearLeft.y,
                Color.parseColor("#0C3663"),
                Color.parseColor("#1A5B9C"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(tablePath, tablePaint)

        // 4. White Outer Boundary Lines (ITTF standard 2cm borders)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
        }
        canvas.drawPath(tablePath, borderPaint)

        // 5. White Center Dividing Line
        val centerLineNear = project3D(0f, TABLE_Y, TABLE_Z_NEAR)
        val centerLineFar = project3D(0f, TABLE_Y, TABLE_Z_FAR)
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6FFFFFF")
            strokeWidth = 2.5f
        }
        canvas.drawLine(centerLineNear.x, centerLineNear.y, centerLineFar.x, centerLineFar.y, centerPaint)

        // 6. 3D Net Across Center (Z = TABLE_Z_NET)
        val netLeftBtm = project3D(-TABLE_HALF_W - 0.12f, TABLE_Y, TABLE_Z_NET)
        val netRightBtm = project3D(TABLE_HALF_W + 0.12f, TABLE_Y, TABLE_Z_NET)
        val netLeftTop = project3D(-TABLE_HALF_W - 0.12f, NET_HEIGHT, TABLE_Z_NET)
        val netRightTop = project3D(TABLE_HALF_W + 0.12f, NET_HEIGHT, TABLE_Z_NET)

        val netPath = Path().apply {
            moveTo(netLeftBtm.x, netLeftBtm.y)
            lineTo(netRightBtm.x, netRightBtm.y)
            lineTo(netRightTop.x, netRightTop.y)
            lineTo(netLeftTop.x, netLeftTop.y)
            close()
        }
        val netMeshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A61E2838")
        }
        canvas.drawPath(netPath, netMeshPaint)

        // Net Top Tape (White Band)
        val netTapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3.5f
        }
        canvas.drawLine(netLeftTop.x, netLeftTop.y, netRightTop.x, netRightTop.y, netTapePaint)

        // Net Side Metal Posts
        val postPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 6f
        }
        canvas.drawLine(netLeftBtm.x, netLeftBtm.y, netLeftTop.x, netLeftTop.y, postPaint)
        canvas.drawLine(netRightBtm.x, netRightBtm.y, netRightTop.x, netRightTop.y, postPaint)
    }

    private fun drawBallAndShadow(canvas: Canvas) {
        // 1. Ball Shadow on Table Surface
        val shadowPt = project3D(ballX, TABLE_Y, ballZ)
        val shadowScale = (1.0f - (ballY / 1.2f)).coerceIn(0.2f, 1.0f)
        val shadowRadius = (16f * shadowScale)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8005080E")
        }
        canvas.drawOval(
            shadowPt.x - shadowRadius * 1.5f,
            shadowPt.y - shadowRadius * 0.7f,
            shadowPt.x + shadowRadius * 1.5f,
            shadowPt.y + shadowRadius * 0.7f,
            shadowPaint
        )

        // 2. Motion Trail Particles
        val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8064D2FF")
        }
        for (i in ballTrail.indices) {
            val alpha = ((i + 1).toFloat() / ballTrail.size * 180).toInt()
            trailPaint.alpha = alpha
            val r = 2.5f + (i * 0.5f)
            canvas.drawCircle(ballTrail[i].x, ballTrail[i].y, r, trailPaint)
        }

        // 3. 3D Ping Pong Ball (High Visibility Neon Orange / Pure White)
        val ballPt = project3D(ballX, ballY, ballZ)
        val dCam = 1.35f
        val focalDist = 840f
        val ballPixelRadius = (ballRadius * (focalDist / (ballZ + dCam))).coerceIn(7f, 32f)

        // Ball 3D Shading
        val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                ballPt.x - ballPixelRadius * 0.4f,
                ballPt.y - ballPixelRadius * 0.4f,
                ballPt.x + ballPixelRadius,
                ballPt.y + ballPixelRadius,
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#FFA000"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(ballPt.x, ballPt.y, ballPixelRadius, ballPaint)

        // Specular highlight spot
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        canvas.drawCircle(
            ballPt.x - ballPixelRadius * 0.35f,
            ballPt.y - ballPixelRadius * 0.35f,
            ballPixelRadius * 0.28f,
            highlightPaint
        )
    }

    private fun drawPaddles(canvas: Canvas) {
        // 1. Bot Paddle at Z = botPaddleZ
        val botPt = project3D(botPaddleX, botPaddleY, botPaddleZ)
        val botScale = 840f / (botPaddleZ + 1.35f)
        val botW = 0.18f * botScale
        val botH = 0.22f * botScale

        val botPaddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D32F2F") // Red Rubber
        }
        val botEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1C1E")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(
            RectF(botPt.x - botW / 2, botPt.y - botH / 2, botPt.x + botW / 2, botPt.y + botH / 2),
            botW * 0.45f, botW * 0.45f, botPaddlePaint
        )
        canvas.drawRoundRect(
            RectF(botPt.x - botW / 2, botPt.y - botH / 2, botPt.x + botW / 2, botPt.y + botH / 2),
            botW * 0.45f, botW * 0.45f, botEdgePaint
        )

        // 2. Player Paddle at Z = paddleZ
        val playerPt = project3D(paddleX, paddleY, paddleZ)
        val playerScale = 840f / (paddleZ + 1.35f)
        val playerW = 0.24f * playerScale
        val playerH = 0.28f * playerScale

        canvas.save()
        canvas.rotate(paddleTilt * 30f, playerPt.x, playerPt.y)

        // Wooden Handle
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C89D6C")
        }
        canvas.drawRoundRect(
            RectF(playerPt.x - playerW * 0.16f, playerPt.y + playerH * 0.4f, playerPt.x + playerW * 0.16f, playerPt.y + playerH * 0.95f),
            8f, 8f, handlePaint
        )

        // Red Paddle Face with Specular Rim
        val playerPaddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E53935")
        }
        val playerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64D2FF")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(
            RectF(playerPt.x - playerW / 2, playerPt.y - playerH / 2, playerPt.x + playerW / 2, playerPt.y + playerH / 2),
            playerW * 0.48f, playerW * 0.48f, playerPaddlePaint
        )
        canvas.drawRoundRect(
            RectF(playerPt.x - playerW / 2, playerPt.y - playerH / 2, playerPt.x + playerW / 2, playerPt.y + playerH / 2),
            playerW * 0.48f, playerW * 0.48f, playerRimPaint
        )

        canvas.restore()
    }

    private fun drawLiquidGlassHUD(canvas: Canvas) {
        // 1. Top Glass Scoreboard Bar
        val scoreRect = RectF(420f, 12f, 860f, 95f)
        drawGlassCard(canvas, scoreRect)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        // Sets Count
        textPaint.textSize = 20f
        textPaint.color = Color.parseColor("#8E8E93")
        canvas.drawText("Set: $playerSets - $botSets", 640f, 38f, textPaint)

        // Scores
        textPaint.textSize = 44f
        textPaint.color = Color.parseColor("#64D2FF")
        canvas.drawText("$playerScore", 560f, 82f, textPaint)

        textPaint.color = Color.parseColor("#A0A0A0")
        textPaint.textSize = 34f
        canvas.drawText(":", 640f, 78f, textPaint)

        textPaint.color = Color.parseColor("#FF453A")
        textPaint.textSize = 44f
        canvas.drawText("$botScore", 720f, 82f, textPaint)

        // Server Indicator Badge
        val serverText = if (server == PlayerSide.PLAYER) "🏓 Sizda" else "🏓 Botda"
        textPaint.textSize = 18f
        textPaint.color = Color.parseColor("#FFD60A")
        canvas.drawText(serverText, 640f, 125f, textPaint)

        // 2. Status Message Toast
        textPaint.textSize = 22f
        textPaint.color = Color.WHITE
        canvas.drawText(statusMessage, 640f, 155f, textPaint)

        // 3. Difficulty Selection Pills (Top-Left)
        drawDifficultyPill(canvas, btnOsonRect, "Oson", difficulty == Difficulty.OSON)
        drawDifficultyPill(canvas, btnOrtaRect, "O'rta", difficulty == Difficulty.ORTA)
        drawDifficultyPill(canvas, btnQiyinRect, "Qiyin", difficulty == Difficulty.QIYIN)

        // 4. Action Buttons (Top-Right)
        drawGlassButton(canvas, btnAudioRect, if (isAudioEnabled) "🔊" else "🔇")
        drawGlassButton(canvas, btnRestartRect, "🔄 Qayta")

        // 5. Big Serve Button (When waiting for player serve)
        if (gameState == GameState.SERVE_WAIT && server == PlayerSide.PLAYER) {
            val servePulseAlpha = (180 + 75 * sin(System.currentTimeMillis() * 0.006)).toInt()
            val serveBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(servePulseAlpha, 10, 132, 255)
            }
            canvas.drawRoundRect(btnServeRect, 28f, 28f, serveBg)

            val serveBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawRoundRect(btnServeRect, 28f, 28f, serveBorder)

            textPaint.textSize = 28f
            textPaint.color = Color.WHITE
            canvas.drawText("🏓 To'pni Kiritish (Serve)", 640f, 680f, textPaint)
        }

        // 6. Game Over Overlay Modal
        if (gameState == GameState.GAME_OVER) {
            val overlayPaint = Paint().apply {
                color = Color.parseColor("#B3000000")
            }
            canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), overlayPaint)

            val modalRect = RectF(380f, 200f, 900f, 480f)
            drawGlassCard(canvas, modalRect)

            val isPlayerWon = (winner == PlayerSide.PLAYER)
            textPaint.textSize = 42f
            textPaint.color = if (isPlayerWon) Color.parseColor("#30D158") else Color.parseColor("#FF453A")
            canvas.drawText(if (isPlayerWon) "🏆 G'ALABA!" else "MAG'LUBIYAT", 640f, 275f, textPaint)

            textPaint.textSize = 26f
            textPaint.color = Color.WHITE
            canvas.drawText("Yakuniy hisob: $playerScore - $botScore", 640f, 335f, textPaint)

            val modalBtnRect = RectF(480f, 380f, 800f, 445f)
            drawGlassButton(canvas, modalBtnRect, "🔄 Yangi O'yin Boshlash")
        }
    }

    private fun drawGlassCard(canvas: Canvas, rect: RectF) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B31C1D24")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4DFFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(rect, 24f, 24f, bgPaint)
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)
    }

    private fun drawDifficultyPill(canvas: Canvas, rect: RectF, label: String, isSelected: Boolean) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.parseColor("#E60A84FF") else Color.parseColor("#6620242D")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.parseColor("#64D2FF") else Color.parseColor("#33FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
        canvas.drawRoundRect(rect, 18f, 18f, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = isSelected
        }
        canvas.drawText(label, rect.centerX(), rect.centerY() + 7f, textPaint)
    }

    private fun drawGlassButton(canvas: Canvas, rect: RectF, label: String) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#802A3040")
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4DFFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
        canvas.drawRoundRect(rect, 18f, 18f, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(label, rect.centerX(), rect.centerY() + 7f, textPaint)
    }

    // ==========================================
    // SOUND SYNTHESIZER (Pure PCM Math Generation)
    // ==========================================

    enum class SoundType {
        PADDLE_HIT,
        TABLE_BOUNCE,
        NET_HIT,
        CHEER,
        MISS,
        CLICK
    }

    fun playSound(type: SoundType) {
        if (!isAudioEnabled) return
        Thread {
            try {
                val sampleRate = 22050
                val durationMs = when (type) {
                    SoundType.PADDLE_HIT -> 60
                    SoundType.TABLE_BOUNCE -> 45
                    SoundType.NET_HIT -> 80
                    SoundType.CHEER -> 220
                    SoundType.MISS -> 180
                    SoundType.CLICK -> 30
                }
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val env = 1.0 - (i.toDouble() / numSamples)
                    val freq = when (type) {
                        SoundType.PADDLE_HIT -> 680.0 - (i * 3.5)
                        SoundType.TABLE_BOUNCE -> 920.0 - (i * 4.0)
                        SoundType.NET_HIT -> 180.0
                        SoundType.CHEER -> 523.25 + (sin(t * 40.0) * 120.0)
                        SoundType.MISS -> 220.0 - (i * 1.5)
                        SoundType.CLICK -> 1200.0
                    }
                    val sample = sin(2.0 * PI * freq * t) * env
                    buffer[i] = (sample * 24000).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
            } catch (_: Exception) {
                // Graceful fallback for mocked / headless unit test environments
            }
        }.start()
    }
}

