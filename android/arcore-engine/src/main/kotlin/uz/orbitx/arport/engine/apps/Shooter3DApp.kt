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
 * Native 3D Spatial Target Shooter with wave progression, 3 weapon classes,
 * crosshair raycasting, particle explosions, and Liquid Glass HUD (TZ 9.3, PROMPT QADAM 17).
 */
class Shooter3DApp(
    override val manifest: AppManifest
) : ARApp {

    override val id: String = manifest.id

    enum class Difficulty(val title: String, val enemySpeed: Float, val enemyDamage: Int, val scoreMultiplier: Float) {
        OSON("Oson", 1.2f, 10, 1.0f),
        ORTA("O'rta", 2.2f, 18, 1.5f),
        QIYIN("Qiyin", 3.6f, 28, 2.5f)
    }

    enum class WeaponType(
        val title: String,
        val icon: String,
        val damage: Int,
        val maxAmmo: Int,
        val cooldownSec: Float,
        val reloadSec: Float,
        val isAoE: Boolean,
        val beamColorHex: String
    ) {
        LASER_PISTOL("Lazer To'pponcha", "🔫", 35, -1, 0.22f, 0.0f, false, "#00E5FF"),
        PLASMA_RIFLE("Plazma Avtomat", "⚡", 25, 30, 0.10f, 1.4f, false, "#FF007F"),
        ROCKET_LAUNCHER("Raketa Otgich", "🚀", 120, 4, 0.85f, 2.2f, true, "#FF9500")
    }

    enum class EnemyType(val title: String, val maxHp: Int, val radius: Float, val scoreReward: Int, val colorHex: String) {
        DRONE("Dron", 50, 0.45f, 100, "#00F0FF"),
        ARMORED_SPHERE("Zirhli Sfera", 130, 0.65f, 250, "#FFB300"),
        SPEED_SCOUT("Tezkor Razvedkachi", 35, 0.32f, 180, "#FF2D55"),
        BOSS("Ona Dron (Boss)", 350, 1.20f, 1000, "#AF52DE")
    }

    data class Enemy(
        val id: Long,
        val type: EnemyType,
        var x: Float,
        var y: Float,
        var z: Float,
        var hp: Int,
        val maxHp: Int,
        val speedZ: Float,
        var wobbleTimer: Float = 0f,
        var isDead: Boolean = false
    )

    data class Particle(
        var x: Float,
        var y: Float,
        var z: Float,
        var vx: Float,
        var vy: Float,
        var vz: Float,
        var life: Float,
        val maxLife: Float,
        val colorHex: String,
        val size: Float
    )

    data class RocketProjectile(
        var x: Float,
        var y: Float,
        var z: Float,
        val targetX: Float,
        val targetY: Float,
        val targetZ: Float,
        var life: Float = 1.5f
    )

    data class FloatingText(
        var screenX: Float,
        var screenY: Float,
        val text: String,
        val colorHex: String,
        var alpha: Float = 1.0f
    )

    // Player State
    var difficulty: Difficulty = Difficulty.ORTA
    var playerHp: Int = 100
    val maxPlayerHp: Int = 100
    var playerShield: Float = 50f
    val maxPlayerShield: Float = 50f
    var score: Int = 0
    var highScore: Int = 0
    var currentWave: Int = 1
    val maxWaves: Int = 10
    var comboMultiplier: Int = 1
    var comboExpiryTimer: Float = 0f
    var isGameOver: Boolean = false
    var isVictory: Boolean = false
    var isAudioEnabled: Boolean = true

    // Weapons State
    var currentWeapon: WeaponType = WeaponType.LASER_PISTOL
    var ammoCount: Int = WeaponType.PLASMA_RIFLE.maxAmmo
    var rocketAmmo: Int = WeaponType.ROCKET_LAUNCHER.maxAmmo
    var weaponCooldownTimer: Float = 0f
    var isReloading: Boolean = false
    var reloadTimer: Float = 0f

    // Targeting Crosshair (mapped from hand ray or pointer)
    var crosshairX: Float = 0f // Normalized [-1.0 .. +1.0]
    var crosshairY: Float = 0f // Normalized [-1.0 .. +1.0]
    var isTargetLocked: Boolean = false
    private var muzzleFlashAlpha: Float = 0f
    private var damageFlashAlpha: Float = 0f

    // Entities in 3D Space
    val enemies = mutableListOf<Enemy>()
    val particles = mutableListOf<Particle>()
    val rockets = mutableListOf<RocketProjectile>()
    val floatingTexts = mutableListOf<FloatingText>()
    private var nextEnemyId: Long = 1L

    // Surface & Dimensions
    var surface: ContentSurface? = null
        private set
    var surfaceWidth: Int = 1280
    var surfaceHeight: Int = 720

    data class Point2D(val x: Float, val y: Float)

    init {
        resetGame()
    }

    /**
     * Advances simulation in discrete fixed timesteps (useful for tests and fixed-rate physics).
     */
    fun stepGame(totalSec: Float, stepDt: Float = 0.016f) {
        var remaining = totalSec
        while (remaining > 0f) {
            val dt = remaining.coerceAtMost(stepDt)
            updateGame(dt)
            remaining -= dt
        }
    }

    private var appContext: Context? = null

    // UI Clickable button bounds
    private val btnOsonRect = RectF(60f, 15f, 160f, 65f)
    private val btnOrtaRect = RectF(175f, 15f, 275f, 65f)
    private val btnQiyinRect = RectF(290f, 15f, 390f, 65f)
    private val btnAudioRect = RectF(990f, 15f, 1080f, 65f)
    private val btnRestartRect = RectF(1100f, 15f, 1220f, 65f)

    // Weapon Switcher pills (bottom left)
    private val btnLaserRect = RectF(60f, 640f, 220f, 705f)
    private val btnPlasmaRect = RectF(235f, 640f, 405f, 705f)
    private val btnRocketRect = RectF(420f, 640f, 595f, 705f)
    private val btnReloadRect = RectF(620f, 640f, 770f, 705f)

    override fun createSurface(context: Context, width: Int, height: Int): ContentSurface {
        appContext = context
        surfaceWidth = width
        surfaceHeight = height
        val surf = ContentSurface(width, height)
        surface = surf

        resetGame()
        render()
        return surf
    }

    override fun onPointerEvent(u: Float, v: Float, action: Int) {
        val px = u * surfaceWidth
        val py = (1.0f - v) * surfaceHeight

        // 1. UI Button taps
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
                resetGame()
                playSound(SoundType.CLICK)
                render()
                return
            }
            if (px in 60f..220f && py in 640f..705f) {
                switchWeapon(WeaponType.LASER_PISTOL)
                return
            }
            if (px in 235f..405f && py in 640f..705f) {
                switchWeapon(WeaponType.PLASMA_RIFLE)
                return
            }
            if (px in 420f..595f && py in 640f..705f) {
                switchWeapon(WeaponType.ROCKET_LAUNCHER)
                return
            }
            if (px in 620f..770f && py in 640f..705f) {
                startReload()
                return
            }
            if (isGameOver || isVictory) {
                resetGame()
                return
            }
        }

        // 2. Map pointer coordinates (u, v) to 3D Crosshair in perspective
        crosshairX = ((u - 0.5f) * 2.2f).coerceIn(-1.0f, 1.0f)
        crosshairY = ((v - 0.5f) * 2.0f).coerceIn(-1.0f, 1.0f)

        // 3. Pinch or Tap = Fire Current Weapon!
        if (action == MotionEvent.ACTION_DOWN) {
            if (py < 620f) { // If not touching bottom UI bar
                fireWeapon()
            }
        }
    }

    override fun onUpdate(dtSec: Float) {
        val dt = dtSec.coerceIn(0.001f, 0.04f)
        updateGame(dt)
        render()
    }

    fun updateGame(dtSec: Float) {
        if (isGameOver || isVictory) return

        // 1. Cooldown and Reload Timers
        if (weaponCooldownTimer > 0f) {
            weaponCooldownTimer -= dtSec
        }
        if (isReloading) {
            reloadTimer -= dtSec
            if (reloadTimer <= 0f) {
                finishReload()
            }
        }

        // Shield gradual regeneration
        if (playerShield < maxPlayerShield) {
            playerShield = (playerShield + 3.5f * dtSec).coerceAtMost(maxPlayerShield)
        }

        // Combo expiration timer
        if (comboExpiryTimer > 0f) {
            comboExpiryTimer -= dtSec
            if (comboExpiryTimer <= 0f) {
                comboMultiplier = 1
            }
        }

        // Visual flash fade
        if (muzzleFlashAlpha > 0f) muzzleFlashAlpha = (muzzleFlashAlpha - 5f * dtSec).coerceAtLeast(0f)
        if (damageFlashAlpha > 0f) damageFlashAlpha = (damageFlashAlpha - 3f * dtSec).coerceAtLeast(0f)

        // 2. Update Rockets
        val rocketIt = rockets.iterator()
        while (rocketIt.hasNext()) {
            val r = rocketIt.next()
            r.z += 18f * dtSec
            r.life -= dtSec
            val hitEnemy = enemies.find { e ->
                abs(e.z - r.z) <= 1.5f && sqrt((e.x - r.x) * (e.x - r.x) + (e.y - r.y) * (e.y - r.y)) <= e.type.radius + 1.2f
            }
            if (hitEnemy != null || r.life <= 0f || r.z >= r.targetZ) {
                explodeRocket(r)
                rocketIt.remove()
            }
        }

        // 3. Advance Enemy Positions along Z towards player
        var lockFound = false
        val enemyIt = enemies.iterator()
        while (enemyIt.hasNext()) {
            val enemy = enemyIt.next()
            if (enemy.isDead) {
                enemyIt.remove()
                continue
            }

            enemy.wobbleTimer += dtSec * 3f
            val wobbleX = sin(enemy.wobbleTimer) * (if (enemy.type == EnemyType.SPEED_SCOUT) 1.2f else 0.4f)
            val wobbleY = cos(enemy.wobbleTimer * 0.7f) * 0.3f
            enemy.x += wobbleX * dtSec
            enemy.y += wobbleY * dtSec

            // Advance towards camera
            enemy.z -= enemy.speedZ * difficulty.enemySpeed * dtSec

            // Check if player crosshair locks onto this enemy
            val ep = project3D(enemy.x, enemy.y, enemy.z)
            val chPt = getCrosshairScreenPos()
            val dist = sqrt((ep.x - chPt.x) * (ep.x - chPt.x) + (ep.y - chPt.y) * (ep.y - chPt.y))
            val hitRadiusPx = getEnemyScreenRadius(enemy)
            if (dist <= hitRadiusPx) {
                lockFound = true
            }

            // Enemy reaches player's position (Z <= 0.6m) -> Damages player!
            if (enemy.z <= 0.65f) {
                damagePlayer(difficulty.enemyDamage)
                createExplosion(enemy.x, enemy.y, enemy.z, enemy.type.colorHex, count = 12)
                enemyIt.remove()
            }
        }
        isTargetLocked = lockFound

        // 4. Update Particle Explosions
        val pIt = particles.iterator()
        while (pIt.hasNext()) {
            val p = pIt.next()
            p.x += p.vx * dtSec
            p.y += p.vy * dtSec
            p.z += p.vz * dtSec
            p.life -= dtSec
            if (p.life <= 0f) {
                pIt.remove()
            }
        }

        // 5. Update Floating Score Popups
        val textIt = floatingTexts.iterator()
        while (textIt.hasNext()) {
            val ft = textIt.next()
            ft.screenY -= 35f * dtSec
            ft.alpha -= 0.8f * dtSec
            if (ft.alpha <= 0f) {
                textIt.remove()
            }
        }

        // 6. Check Wave Completion
        if (enemies.isEmpty()) {
            if (currentWave < maxWaves) {
                currentWave++
                spawnWave(currentWave)
                addFloatingText(surfaceWidth / 2f, surfaceHeight * 0.35f, "TO'LQIN $currentWave BOSHLANDI!", "#64D2FF")
                playSound(SoundType.WAVE_START)
            } else {
                isVictory = true
                playSound(SoundType.VICTORY)
            }
        }
    }

    /**
     * Fires the current active weapon towards the 3D crosshair ray.
     */
    fun fireWeapon() {
        if (isGameOver || isVictory || isReloading) return
        if (weaponCooldownTimer > 0f) return

        when (currentWeapon) {
            WeaponType.LASER_PISTOL -> {
                // Unlimited battery, instant raycast
                weaponCooldownTimer = currentWeapon.cooldownSec
                muzzleFlashAlpha = 1.0f
                playSound(SoundType.LASER)
                performRaycastAttack(currentWeapon.damage, isAoE = false)
            }
            WeaponType.PLASMA_RIFLE -> {
                if (ammoCount <= 0) {
                    startReload()
                    return
                }
                ammoCount--
                weaponCooldownTimer = currentWeapon.cooldownSec
                muzzleFlashAlpha = 0.85f
                playSound(SoundType.PLASMA)
                performRaycastAttack(currentWeapon.damage, isAoE = false)
            }
            WeaponType.ROCKET_LAUNCHER -> {
                if (rocketAmmo <= 0) {
                    startReload()
                    return
                }
                rocketAmmo--
                weaponCooldownTimer = currentWeapon.cooldownSec
                muzzleFlashAlpha = 1.0f
                playSound(SoundType.ROCKET_FIRE)

                // Spawn 3D Rocket Projectile
                val aim = getCrosshair3DTarget()
                rockets.add(RocketProjectile(0f, -0.4f, 0.8f, aim.x, aim.y, aim.z))
            }
        }
    }

    private fun performRaycastAttack(damage: Int, isAoE: Boolean) {
        val chPt = getCrosshairScreenPos()
        var hitTarget: Enemy? = null
        var closestDist = Float.MAX_VALUE

        for (enemy in enemies) {
            val ep = project3D(enemy.x, enemy.y, enemy.z)
            val d = sqrt((ep.x - chPt.x) * (ep.x - chPt.x) + (ep.y - chPt.y) * (ep.y - chPt.y))
            val rad = getEnemyScreenRadius(enemy)
            if (d <= rad && enemy.z < closestDist) {
                hitTarget = enemy
                closestDist = enemy.z
            }
        }

        if (hitTarget != null) {
            applyDamageToEnemy(hitTarget, damage)
            createExplosion(hitTarget.x, hitTarget.y, hitTarget.z, currentWeapon.beamColorHex, count = 8)
            playSound(SoundType.HIT_CONFIRM)
        }
    }

    private fun explodeRocket(rocket: RocketProjectile) {
        createExplosion(rocket.x, rocket.y, rocket.z, "#FF9500", count = 35)
        playSound(SoundType.EXPLOSION)

        // AoE Splash Damage to all enemies within 3.5m radius
        val splashRadius = 3.5f
        for (enemy in enemies) {
            val dx = enemy.x - rocket.x
            val dy = enemy.y - rocket.y
            val dz = enemy.z - rocket.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist <= splashRadius) {
                val falloff = (1.0f - (dist / splashRadius)).coerceIn(0.2f, 1.0f)
                val dmg = (WeaponType.ROCKET_LAUNCHER.damage * falloff).toInt()
                applyDamageToEnemy(enemy, dmg)
            }
        }
    }

    private fun applyDamageToEnemy(enemy: Enemy, damage: Int) {
        enemy.hp -= damage
        val ep = project3D(enemy.x, enemy.y, enemy.z)
        addFloatingText(ep.x, ep.y - 20f, "-$damage", "#FF453A")

        if (enemy.hp <= 0) {
            enemy.isDead = true
            createExplosion(enemy.x, enemy.y, enemy.z, enemy.type.colorHex, count = 28)
            playSound(SoundType.ENEMY_DESTROY)

            // Combo & Score calculations
            comboMultiplier = (comboMultiplier + 1).coerceAtMost(5)
            comboExpiryTimer = 3.5f

            val pointsEarned = (enemy.type.scoreReward * comboMultiplier * difficulty.scoreMultiplier).toInt()
            score += pointsEarned
            if (score > highScore) highScore = score

            val comboText = if (comboMultiplier > 1) " (x$comboMultiplier COMBO!)" else ""
            addFloatingText(ep.x, ep.y - 45f, "+$pointsEarned$comboText", "#30D158")
        }
    }

    private fun damagePlayer(amount: Int) {
        damageFlashAlpha = 0.85f
        playSound(SoundType.PLAYER_HURT)

        if (playerShield > 0f) {
            playerShield -= amount
            if (playerShield < 0f) {
                playerHp += playerShield.toInt()
                playerShield = 0f
            }
        } else {
            playerHp -= amount
        }

        if (playerHp <= 0) {
            playerHp = 0
            isGameOver = true
            playSound(SoundType.GAME_OVER)
        }
    }

    fun startReload() {
        if (isReloading) return
        if (currentWeapon == WeaponType.LASER_PISTOL) return

        val needsReload = when (currentWeapon) {
            WeaponType.PLASMA_RIFLE -> ammoCount < WeaponType.PLASMA_RIFLE.maxAmmo
            WeaponType.ROCKET_LAUNCHER -> rocketAmmo < WeaponType.ROCKET_LAUNCHER.maxAmmo
            else -> false
        }
        if (!needsReload) return

        isReloading = true
        reloadTimer = currentWeapon.reloadSec
        playSound(SoundType.RELOAD)
    }

    private fun finishReload() {
        isReloading = false
        when (currentWeapon) {
            WeaponType.PLASMA_RIFLE -> ammoCount = WeaponType.PLASMA_RIFLE.maxAmmo
            WeaponType.ROCKET_LAUNCHER -> rocketAmmo = WeaponType.ROCKET_LAUNCHER.maxAmmo
            else -> {}
        }
    }

    fun switchWeapon(type: WeaponType) {
        currentWeapon = type
        isReloading = false
        playSound(SoundType.CLICK)
        render()
    }

    fun spawnWave(wave: Int) {
        currentWave = wave
        enemies.clear()
        val baseEnemies = 4 + (wave * 2)

        for (i in 0 until baseEnemies) {
            val type = when {
                wave % 5 == 0 && i == 0 -> EnemyType.BOSS
                wave >= 4 && i % 3 == 0 -> EnemyType.ARMORED_SPHERE
                wave >= 2 && i % 2 == 1 -> EnemyType.SPEED_SCOUT
                else -> EnemyType.DRONE
            }

            val spawnX = (Math.random().toFloat() - 0.5f) * 7.5f
            val spawnY = (Math.random().toFloat() - 0.5f) * 4.5f
            val spawnZ = 12f + (i * 2.8f) + (Math.random().toFloat() * 3.5f)

            enemies.add(
                Enemy(
                    id = nextEnemyId++,
                    type = type,
                    x = spawnX,
                    y = spawnY,
                    z = spawnZ,
                    hp = type.maxHp,
                    maxHp = type.maxHp,
                    speedZ = (if (type == EnemyType.SPEED_SCOUT) 2.2f else 1.1f)
                )
            )
        }
    }

    fun resetGame() {
        playerHp = maxPlayerHp
        playerShield = maxPlayerShield
        score = 0
        currentWave = 1
        comboMultiplier = 1
        isGameOver = false
        isVictory = false
        isReloading = false
        ammoCount = WeaponType.PLASMA_RIFLE.maxAmmo
        rocketAmmo = WeaponType.ROCKET_LAUNCHER.maxAmmo
        currentWeapon = WeaponType.LASER_PISTOL
        particles.clear()
        rockets.clear()
        floatingTexts.clear()
        spawnWave(currentWave)
    }

    private fun createExplosion(x: Float, y: Float, z: Float, colorHex: String, count: Int) {
        for (i in 0 until count) {
            val angle = Math.random() * 2.0 * PI
            val speed = 2.0f + (Math.random().toFloat() * 6.5f)
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    z = z,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat() + 1.2f,
                    vz = ((Math.random() - 0.5) * 4.5).toFloat(),
                    life = 0.5f + (Math.random().toFloat() * 0.5f),
                    maxLife = 1.0f,
                    colorHex = colorHex,
                    size = 4f + (Math.random().toFloat() * 8f)
                )
            )
        }
    }

    private fun addFloatingText(x: Float, y: Float, text: String, colorHex: String) {
        floatingTexts.add(FloatingText(x, y, text, colorHex))
    }

    private fun getCrosshairScreenPos(): Point2D {
        val cx = (surfaceWidth / 2f) + (crosshairX * (surfaceWidth * 0.45f))
        val cy = (surfaceHeight / 2f) - (crosshairY * (surfaceHeight * 0.40f))
        return Point2D(cx, cy)
    }

    private fun getCrosshair3DTarget(): PointF3D {
        val targetZ = 16f
        val tx = crosshairX * 6.5f
        val ty = crosshairY * 4.2f
        return PointF3D(tx, ty, targetZ)
    }

    data class PointF3D(val x: Float, val y: Float, val z: Float)

    private fun getEnemyScreenRadius(enemy: Enemy): Float {
        val focalDist = 880f
        val dCam = 1.4f
        val scale = focalDist / (enemy.z + dCam)
        return (enemy.type.radius * scale).coerceIn(18f, 160f)
    }

    fun project3D(x: Float, y: Float, z: Float): Point2D {
        val dCam = 1.4f
        val focalDist = 880f
        val clampedZ = z.coerceAtLeast(0.1f)
        val scale = focalDist / (clampedZ + dCam)

        val screenX = (surfaceWidth / 2f) + (x * scale)
        val screenY = (surfaceHeight / 2f) - (y * scale)
        return Point2D(screenX, screenY)
    }

    // ==========================================
    // 3D RENDERING & LIQUID GLASS HUD
    // ==========================================

    fun render() {
        surface?.draw { canvas ->
            // 1. Sci-Fi Holographic Tunnel Background & Starfield
            drawTunnelBackground(canvas)

            // 2. 3D Flying Targets in Perspective
            draw3DEnemies(canvas)

            // 3. 3D Flying Rockets & Particles
            drawProjectilesAndParticles(canvas)

            // 4. Spatial Laser Crosshair with Dynamic Lock-on Ring
            drawCrosshair(canvas)

            // 5. Screen Flashes (Muzzle fire / Damage hurt)
            drawScreenFlashes(canvas)

            // 6. Floating Score Popups
            drawFloatingTexts(canvas)

            // 7. Apple VisionOS Liquid Glass HUD (Health, Shields, Ammo, Waves)
            drawLiquidGlassHUD(canvas)
        }
    }

    private fun drawTunnelBackground(canvas: Canvas) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, surfaceHeight.toFloat(),
                Color.parseColor("#06080E"),
                Color.parseColor("#101524"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), bgPaint)

        // Holographic neon tunnel corridor grid
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A00F0FF")
            strokeWidth = 1.5f
        }

        val centerX = surfaceWidth / 2f
        val centerY = surfaceHeight / 2f

        // Expanding concentric depth rings
        for (i in 1..7) {
            val depthZ = i * 3.5f
            val scale = 880f / (depthZ + 1.4f)
            val ringW = 9.0f * scale
            val ringH = 5.5f * scale
            val ringRect = RectF(centerX - ringW / 2, centerY - ringH / 2, centerX + ringW / 2, centerY + ringH / 2)
            canvas.drawRoundRect(ringRect, 18f, 18f, gridPaint)
        }

        // Perspective diagonal guide rails
        val cornerTL = project3D(-4.5f, 2.75f, 25f)
        val cornerTR = project3D(4.5f, 2.75f, 25f)
        val cornerBL = project3D(-4.5f, -2.75f, 25f)
        val cornerBR = project3D(4.5f, -2.75f, 25f)

        canvas.drawLine(centerX, centerY, cornerTL.x, cornerTL.y, gridPaint)
        canvas.drawLine(centerX, centerY, cornerTR.x, cornerTR.y, gridPaint)
        canvas.drawLine(centerX, centerY, cornerBL.x, cornerBL.y, gridPaint)
        canvas.drawLine(centerX, centerY, cornerBR.x, cornerBR.y, gridPaint)
    }

    private fun draw3DEnemies(canvas: Canvas) {
        // Sort back-to-front by depth Z for correct painter's layering
        val sortedEnemies = enemies.sortedByDescending { it.z }

        for (enemy in sortedEnemies) {
            val pt = project3D(enemy.x, enemy.y, enemy.z)
            val rad = getEnemyScreenRadius(enemy)

            // Glowing Outer Energy Aura
            val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(enemy.type.colorHex)
                alpha = 80
            }
            canvas.drawCircle(pt.x, pt.y, rad * 1.35f, auraPaint)

            // Solid Enemy Core Body
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(enemy.type.colorHex)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }

            if (enemy.type == EnemyType.BOSS) {
                // Hexagonal Boss Core
                canvas.drawCircle(pt.x, pt.y, rad, corePaint)
                canvas.drawCircle(pt.x, pt.y, rad, borderPaint)
                // Boss rotating shield ring
                val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#AF52DE")
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawCircle(pt.x, pt.y, rad * 1.25f, shieldPaint)
            } else {
                canvas.drawCircle(pt.x, pt.y, rad, corePaint)
                canvas.drawCircle(pt.x, pt.y, rad, borderPaint)
            }

            // Health Bar above enemy
            val hpPct = (enemy.hp.toFloat() / enemy.maxHp).coerceIn(0f, 1f)
            val barW = rad * 1.8f
            val barH = 7f
            val barRect = RectF(pt.x - barW / 2, pt.y - rad - 18f, pt.x + barW / 2, pt.y - rad - 18f + barH)

            val hpBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80000000") }
            val hpFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (hpPct > 0.5f) Color.parseColor("#30D158") else Color.parseColor("#FF453A")
            }
            canvas.drawRoundRect(barRect, 3f, 3f, hpBgPaint)
            val fillRect = RectF(barRect.left, barRect.top, barRect.left + (barW * hpPct), barRect.bottom)
            canvas.drawRoundRect(fillRect, 3f, 3f, hpFillPaint)
        }
    }

    private fun drawProjectilesAndParticles(canvas: Canvas) {
        // 1. Rocket Projectiles
        for (r in rockets) {
            val pt = project3D(r.x, r.y, r.z)
            val pPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF9500")
            }
            canvas.drawCircle(pt.x, pt.y, 9f, pPaint)
            // Rocket smoke trail
            val smokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#80FFFFFF")
            }
            canvas.drawCircle(pt.x, pt.y + 12f, 5f, smokePaint)
        }

        // 2. 3D Particle Shards
        for (p in particles) {
            val pt = project3D(p.x, p.y, p.z)
            val alpha = ((p.life / p.maxLife) * 255).toInt().coerceIn(0, 255)
            val pPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(p.colorHex)
                this.alpha = alpha
            }
            canvas.drawCircle(pt.x, pt.y, p.size, pPaint)
        }
    }

    private fun drawCrosshair(canvas: Canvas) {
        val pt = getCrosshairScreenPos()

        val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isTargetLocked) Color.parseColor("#30D158") else Color.parseColor("#64D2FF")
            style = Paint.Style.STROKE
            strokeWidth = if (isTargetLocked) 3.5f else 2.0f
        }

        // Center Reticle Circle
        val ringRad = if (isTargetLocked) 24f else 32f
        canvas.drawCircle(pt.x, pt.y, ringRad, reticlePaint)

        // Center Point
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isTargetLocked) Color.parseColor("#30D158") else Color.WHITE
        }
        canvas.drawCircle(pt.x, pt.y, 4f, dotPaint)

        // Four Crosshair Ticks
        canvas.drawLine(pt.x - ringRad - 14f, pt.y, pt.x - ringRad + 4f, pt.y, reticlePaint)
        canvas.drawLine(pt.x + ringRad - 4f, pt.y, pt.x + ringRad + 14f, pt.y, reticlePaint)
        canvas.drawLine(pt.x, pt.y - ringRad - 14f, pt.x, pt.y - ringRad + 4f, reticlePaint)
        canvas.drawLine(pt.x, pt.y + ringRad - 4f, pt.x, pt.y + ringRad + 14f, reticlePaint)

        // Lock-on text
        if (isTargetLocked) {
            val lockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#30D158")
                textSize = 18f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText("NISHONGA OLINDI", pt.x, pt.y + ringRad + 24f, lockTextPaint)
        }
    }

    private fun drawScreenFlashes(canvas: Canvas) {
        if (muzzleFlashAlpha > 0f) {
            val mPaint = Paint().apply {
                color = Color.parseColor(currentWeapon.beamColorHex)
                alpha = (muzzleFlashAlpha * 110).toInt()
            }
            canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), mPaint)
        }
        if (damageFlashAlpha > 0f) {
            val dPaint = Paint().apply {
                color = Color.parseColor("#FF3B30")
                alpha = (damageFlashAlpha * 140).toInt()
            }
            canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), dPaint)
        }
    }

    private fun drawFloatingTexts(canvas: Canvas) {
        for (ft in floatingTexts) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(ft.colorHex)
                alpha = (ft.alpha * 255).toInt().coerceIn(0, 255)
                textSize = 24f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText(ft.text, ft.screenX, ft.screenY, textPaint)
        }
    }

    private fun drawLiquidGlassHUD(canvas: Canvas) {
        // 1. Top Glass Status Bar
        val topBarRect = RectF(400f, 12f, 880f, 95f)
        drawGlassCard(canvas, topBarRect)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        // Wave & Score Header
        textPaint.textSize = 20f
        textPaint.color = Color.parseColor("#8E8E93")
        canvas.drawText("To'lqin: $currentWave / $maxWaves  •  Rekord: $highScore", 640f, 38f, textPaint)

        // Current Score
        textPaint.textSize = 42f
        textPaint.color = Color.parseColor("#64D2FF")
        canvas.drawText("$score", 640f, 80f, textPaint)

        // Combo Multiplier Badge
        if (comboMultiplier > 1) {
            val comboPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFD60A")
                textSize = 22f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
            canvas.drawText("⚡ x$comboMultiplier COMBO!", 640f, 125f, comboPaint)
        }

        // 2. Health & Shield Bars (Top Left)
        val statsRect = RectF(60f, 80f, 360f, 150f)
        drawGlassCard(canvas, statsRect)

        // Health Bar
        val hpPct = (playerHp.toFloat() / maxPlayerHp).coerceIn(0f, 1f)
        val hpBg = RectF(75f, 95f, 345f, 108f)
        canvas.drawRoundRect(hpBg, 5f, 5f, Paint().apply { color = Color.parseColor("#33FFFFFF") })
        val hpFill = RectF(hpBg.left, hpBg.top, hpBg.left + (hpBg.width() * hpPct), hpBg.bottom)
        canvas.drawRoundRect(hpFill, 5f, 5f, Paint().apply { color = Color.parseColor("#30D158") })

        val hpText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f
            isFakeBoldText = true
        }
        canvas.drawText("❤️ Sog'liq: $playerHp / $maxPlayerHp", 75f, 90f, hpText)

        // Shield Bar
        val shieldPct = (playerShield / maxPlayerShield).coerceIn(0f, 1f)
        val shBg = RectF(75f, 128f, 345f, 140f)
        canvas.drawRoundRect(shBg, 5f, 5f, Paint().apply { color = Color.parseColor("#33FFFFFF") })
        val shFill = RectF(shBg.left, shBg.top, shBg.left + (shBg.width() * shieldPct), shBg.bottom)
        canvas.drawRoundRect(shFill, 5f, 5f, Paint().apply { color = Color.parseColor("#0A84FF") })
        canvas.drawText("🛡️ Qalqon: ${playerShield.toInt()} / ${maxPlayerShield.toInt()}", 75f, 124f, hpText)

        // 3. Difficulty Pills & Top-Right Controls
        drawDifficultyPill(canvas, btnOsonRect, "Oson", difficulty == Difficulty.OSON)
        drawDifficultyPill(canvas, btnOrtaRect, "O'rta", difficulty == Difficulty.ORTA)
        drawDifficultyPill(canvas, btnQiyinRect, "Qiyin", difficulty == Difficulty.QIYIN)
        drawGlassButton(canvas, btnAudioRect, if (isAudioEnabled) "🔊" else "🔇")
        drawGlassButton(canvas, btnRestartRect, "🔄 Qayta")

        // 4. Bottom Weapons Bar
        drawWeaponPill(canvas, btnLaserRect, WeaponType.LASER_PISTOL, currentWeapon == WeaponType.LASER_PISTOL, "Cheksiz")
        drawWeaponPill(canvas, btnPlasmaRect, WeaponType.PLASMA_RIFLE, currentWeapon == WeaponType.PLASMA_RIFLE, "$ammoCount / ${WeaponType.PLASMA_RIFLE.maxAmmo}")
        drawWeaponPill(canvas, btnRocketRect, WeaponType.ROCKET_LAUNCHER, currentWeapon == WeaponType.ROCKET_LAUNCHER, "$rocketAmmo / ${WeaponType.ROCKET_LAUNCHER.maxAmmo}")

        // Reload Button
        val reloadLabel = if (isReloading) "O'qlanmoqda..." else "🔄 O'qlash"
        drawGlassButton(canvas, btnReloadRect, reloadLabel)

        // 5. Game Over / Victory Screen
        if (isGameOver || isVictory) {
            val ovPaint = Paint().apply { color = Color.parseColor("#CC000000") }
            canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), ovPaint)

            val modalRect = RectF(380f, 190f, 900f, 490f)
            drawGlassCard(canvas, modalRect)

            textPaint.textSize = 42f
            textPaint.color = if (isVictory) Color.parseColor("#30D158") else Color.parseColor("#FF453A")
            canvas.drawText(if (isVictory) "🏆 G'ALABA! TO'LQINLAR TUGADI" else "MAG'LUBIYAT", 640f, 270f, textPaint)

            textPaint.textSize = 26f
            textPaint.color = Color.WHITE
            canvas.drawText("To'plangan Ball: $score", 640f, 335f, textPaint)
            textPaint.color = Color.parseColor("#8E8E93")
            canvas.drawText("To'lqin: $currentWave / $maxWaves", 640f, 380f, textPaint)

            val restartModalBtn = RectF(480f, 410f, 800f, 470f)
            drawGlassButton(canvas, restartModalBtn, "🔄 Qayta O'ynash")
        }
    }

    private fun drawGlassCard(canvas: Canvas, rect: RectF) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B3181C26") }
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

    private fun drawWeaponPill(canvas: Canvas, rect: RectF, weapon: WeaponType, isSelected: Boolean, ammoText: String) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.parseColor("#E60A84FF") else Color.parseColor("#661D222E")
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
            textSize = 17f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = isSelected
        }
        canvas.drawText("${weapon.icon} ${weapon.title}", rect.centerX(), rect.centerY() - 6f, textPaint)

        textPaint.textSize = 14f
        textPaint.color = Color.parseColor("#64D2FF")
        textPaint.isFakeBoldText = false
        canvas.drawText(ammoText, rect.centerX(), rect.centerY() + 16f, textPaint)
    }

    private fun drawGlassButton(canvas: Canvas, rect: RectF, label: String) {
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80252D3D") }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4DFFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
        canvas.drawRoundRect(rect, 18f, 18f, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(label, rect.centerX(), rect.centerY() + 7f, textPaint)
    }

    // ==========================================
    // SOUND SYNTHESIZER (Pure PCM Math Generation)
    // ==========================================

    enum class SoundType {
        LASER,
        PLASMA,
        ROCKET_FIRE,
        EXPLOSION,
        HIT_CONFIRM,
        ENEMY_DESTROY,
        PLAYER_HURT,
        RELOAD,
        WAVE_START,
        VICTORY,
        GAME_OVER,
        CLICK
    }

    fun playSound(type: SoundType) {
        if (!isAudioEnabled) return
        Thread {
            try {
                val sampleRate = 22050
                val durationMs = when (type) {
                    SoundType.LASER -> 85
                    SoundType.PLASMA -> 70
                    SoundType.ROCKET_FIRE -> 160
                    SoundType.EXPLOSION -> 280
                    SoundType.HIT_CONFIRM -> 45
                    SoundType.ENEMY_DESTROY -> 190
                    SoundType.PLAYER_HURT -> 140
                    SoundType.RELOAD -> 110
                    SoundType.WAVE_START -> 260
                    SoundType.VICTORY -> 450
                    SoundType.GAME_OVER -> 320
                    SoundType.CLICK -> 30
                }
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val env = 1.0 - (i.toDouble() / numSamples)
                    val freq = when (type) {
                        SoundType.LASER -> 1200.0 - (i * 9.0)
                        SoundType.PLASMA -> 850.0 - (i * 7.5)
                        SoundType.ROCKET_FIRE -> 280.0 + (i * 4.0)
                        SoundType.EXPLOSION -> 140.0 - (i * 0.4)
                        SoundType.HIT_CONFIRM -> 1400.0
                        SoundType.ENEMY_DESTROY -> 440.0 + (sin(t * 30.0) * 80.0)
                        SoundType.PLAYER_HURT -> 120.0 - (i * 0.5)
                        SoundType.RELOAD -> 950.0 - (i * 4.0)
                        SoundType.WAVE_START -> 587.33 + (t * 220.0)
                        SoundType.VICTORY -> 659.25 + (sin(t * 20.0) * 150.0)
                        SoundType.GAME_OVER -> 160.0 - (t * 60.0)
                        SoundType.CLICK -> 1100.0
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

