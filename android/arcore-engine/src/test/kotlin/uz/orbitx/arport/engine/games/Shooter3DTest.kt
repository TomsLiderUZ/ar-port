package uz.orbitx.arport.engine.games

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uz.orbitx.arport.engine.apps.Shooter3DApp
import uz.orbitx.arport.engine.registry.AppManifest

/**
 * Unit tests for 3D Target Shooter weapons, waves, raycasting, and mechanics (TZ 9.3, QADAM 17).
 */
class Shooter3DTest {

    private lateinit var app: Shooter3DApp
    private lateinit var manifest: AppManifest

    @Before
    fun setUp() {
        manifest = AppManifest(
            id = "shooter3d",
            name = mapOf("uz" to "3D Mergan", "en" to "3D Target Shooter"),
            icon = "🎯",
            version = "1.0.0",
            type = "game3d",
            category = "games",
            entry = "uz.orbitx.arport.engine.apps.Shooter3DApp",
            defaultAspect = "16:9",
            defaultWidth = 1.2f,
            defaultHeight = 0.675f,
            multiWindow = false,
            order = 18
        )
        app = Shooter3DApp(manifest)
        app.isAudioEnabled = false // Disable audio playback during headless tests
    }

    @Test
    fun testWeaponProperties() {
        // 1. Laser Pistol
        val laser = Shooter3DApp.WeaponType.LASER_PISTOL
        assertEquals(35, laser.damage)
        assertEquals(-1, laser.maxAmmo) // Unlimited
        assertFalse(laser.isAoE)

        // 2. Plasma Rifle
        val plasma = Shooter3DApp.WeaponType.PLASMA_RIFLE
        assertEquals(25, plasma.damage)
        assertEquals(30, plasma.maxAmmo)
        assertEquals(1.4f, plasma.reloadSec, 0.01f)

        // 3. Rocket Launcher
        val rocket = Shooter3DApp.WeaponType.ROCKET_LAUNCHER
        assertEquals(120, rocket.damage)
        assertEquals(4, rocket.maxAmmo)
        assertEquals(2.2f, rocket.reloadSec, 0.01f)
        assertTrue(rocket.isAoE)
    }

    @Test
    fun testEnemyTypesAndWaveSpawning() {
        assertEquals(1, app.currentWave)
        assertTrue("Wave 1 must contain enemies", app.enemies.isNotEmpty())

        // Verify Boss spawns on wave 5
        app.spawnWave(5)
        assertEquals(5, app.currentWave)
        assertTrue("Wave 5 must spawn Boss Mothership", app.enemies.any { it.type == Shooter3DApp.EnemyType.BOSS })

        // Check Boss HP
        val boss = app.enemies.first { it.type == Shooter3DApp.EnemyType.BOSS }
        assertEquals(350, boss.maxHp)
    }

    @Test
    fun testCrosshairPointerMapping() {
        // Center pointer (u = 0.5, v = 0.5) -> crosshair (0, 0)
        app.onPointerEvent(0.5f, 0.5f, MotionEvent.ACTION_MOVE)
        assertEquals(0.0f, app.crosshairX, 0.05f)
        assertEquals(0.0f, app.crosshairY, 0.05f)

        // Move to upper right
        app.onPointerEvent(0.85f, 0.85f, MotionEvent.ACTION_MOVE)
        assertTrue("Crosshair X must be positive", app.crosshairX > 0.5f)
        assertTrue("Crosshair Y must be positive", app.crosshairY > 0.5f)
    }

    @Test
    fun testLaserPistolFiringAndTargetDamage() {
        app.switchWeapon(Shooter3DApp.WeaponType.LASER_PISTOL)
        app.enemies.clear()

        // Place a Drone right in front of the crosshair
        val testDrone = Shooter3DApp.Enemy(
            id = 999L,
            type = Shooter3DApp.EnemyType.DRONE,
            x = 0.0f,
            y = 0.0f,
            z = 6.0f,
            hp = 50,
            maxHp = 50,
            speedZ = 1.0f
        )
        app.enemies.add(testDrone)

        // Aim at center (0, 0)
        app.crosshairX = 0f
        app.crosshairY = 0f

        // Fire Laser!
        app.fireWeapon()

        // Verify damage applied
        assertEquals("Drone should take 35 damage (50 - 35 = 15)", 15, testDrone.hp)
        assertFalse(testDrone.isDead)

        // Fire second shot after cooldown
        app.weaponCooldownTimer = 0f
        app.fireWeapon()

        assertTrue("Drone should be destroyed on second shot", testDrone.isDead)
        assertTrue("Particles should be spawned on destruction", app.particles.isNotEmpty())
        assertTrue("Score should increase", app.score > 0)
    }

    @Test
    fun testPlasmaRifleAmmoAndReloadMechanics() {
        app.switchWeapon(Shooter3DApp.WeaponType.PLASMA_RIFLE)
        assertEquals(30, app.ammoCount)

        // Fire 5 rounds
        for (i in 0 until 5) {
            app.weaponCooldownTimer = 0f
            app.fireWeapon()
        }
        assertEquals(25, app.ammoCount)

        // Trigger Reload
        app.startReload()
        assertTrue("Reloading state must be active", app.isReloading)
        assertEquals(Shooter3DApp.WeaponType.PLASMA_RIFLE.reloadSec, app.reloadTimer, 0.01f)

        // Step time forward past reload time
        app.updateGame(1.5f)
        assertFalse("Reloading must be finished", app.isReloading)
        assertEquals(30, app.ammoCount)
    }

    @Test
    fun testRocketLauncherFiringAndAoEExplosion() {
        app.switchWeapon(Shooter3DApp.WeaponType.ROCKET_LAUNCHER)
        assertEquals(4, app.rocketAmmo)
        app.enemies.clear()

        // Spawn 2 clustered enemies
        val e1 = Shooter3DApp.Enemy(1L, Shooter3DApp.EnemyType.DRONE, 0.0f, 0.0f, 10.0f, 50, 50, 1.0f)
        val e2 = Shooter3DApp.Enemy(2L, Shooter3DApp.EnemyType.DRONE, 0.8f, 0.2f, 10.5f, 50, 50, 1.0f)
        app.enemies.add(e1)
        app.enemies.add(e2)

        app.crosshairX = 0f
        app.crosshairY = 0f

        // Fire Rocket
        app.fireWeapon()
        assertEquals(3, app.rocketAmmo)
        assertEquals(1, app.rockets.size)

        // Step physics until rocket hits
        app.stepGame(0.8f)
        assertEquals(0, app.rockets.size) // Rocket exploded
        assertTrue("Both clustered enemies should take AoE damage", e1.hp < 50 && e2.hp < 50)
    }

    @Test
    fun testPlayerDamageAndShieldDepletion() {
        val initialShield = app.playerShield
        val initialHp = app.playerHp

        // Enemy breaches defenses (Z <= 0.65m)
        val enemy = Shooter3DApp.Enemy(1L, Shooter3DApp.EnemyType.DRONE, 0f, 0f, 0.60f, 50, 50, 1.0f)
        app.enemies.clear()
        app.enemies.add(enemy)

        app.updateGame(0.016f)

        assertTrue("Shield must absorb damage first", app.playerShield < initialShield)
        assertEquals(initialHp, app.playerHp)
    }

    @Test
    fun testComboMultiplierProgression() {
        app.enemies.clear()
        val e1 = Shooter3DApp.Enemy(1L, Shooter3DApp.EnemyType.SPEED_SCOUT, 0f, 0f, 5f, 20, 35, 1f)
        val e2 = Shooter3DApp.Enemy(2L, Shooter3DApp.EnemyType.SPEED_SCOUT, 0f, 0f, 5f, 20, 35, 1f)
        app.enemies.add(e1)
        app.enemies.add(e2)

        app.crosshairX = 0f
        app.crosshairY = 0f

        // Kill first enemy
        app.fireWeapon()
        assertEquals(2, app.comboMultiplier)

        // Kill second enemy
        app.weaponCooldownTimer = 0f
        app.fireWeapon()
        assertEquals(3, app.comboMultiplier)

        // Fast forward 4.0s (past combo expiry 3.5s)
        app.updateGame(4.0f)
        assertEquals("Combo multiplier must reset to 1 after expiry", 1, app.comboMultiplier)
    }

    @Test
    fun testDifficultySettings() {
        app.difficulty = Shooter3DApp.Difficulty.OSON
        assertEquals(1.2f, app.difficulty.enemySpeed, 0.01f)
        assertEquals(10, app.difficulty.enemyDamage)

        app.difficulty = Shooter3DApp.Difficulty.QIYIN
        assertEquals(3.6f, app.difficulty.enemySpeed, 0.01f)
        assertEquals(28, app.difficulty.enemyDamage)
        assertEquals(2.5f, app.difficulty.scoreMultiplier, 0.01f)
    }

    @Test
    fun testResetGame() {
        app.score = 1500
        app.currentWave = 4
        app.playerHp = 40
        app.playerShield = 10f

        app.resetGame()

        assertEquals(0, app.score)
        assertEquals(1, app.currentWave)
        assertEquals(app.maxPlayerHp, app.playerHp)
        assertEquals(app.maxPlayerShield, app.playerShield, 0.01f)
        assertFalse(app.isGameOver)
    }
}

