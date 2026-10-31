package uz.orbitx.arport.engine.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.orbitx.arport.engine.render.AdaptiveQualityController
import uz.orbitx.arport.engine.render.QualityTier
import uz.orbitx.arport.engine.render.ThermalMonitor
import uz.orbitx.arport.engine.sandbox.NavigationDecision
import uz.orbitx.arport.engine.sandbox.SandboxPolicy
import uz.orbitx.arport.engine.window.SpatialWindow
import uz.orbitx.arport.engine.window.WindowManager3D
import uz.orbitx.arport.engine.window.WindowState
import java.io.File

/**
 * QADAM 18: Performance, Stability, Privacy and Sandbox Security Acceptance Tests.
 * Verifies adaptive quality downgrade/recovery loops, thermal monitoring, multi-window sleep policies,
 * WebView crash recovery, long-running stability loops, and zero external intent escaping.
 */
class PerformanceAndStabilityTest {

    @Test
    fun testAdaptiveQualityInitialTier() {
        val controller = AdaptiveQualityController()
        assertEquals(QualityTier.ULTRA, controller.currentTier)
        assertEquals(1.0f, controller.particleMultiplier, 0.001f)
        assertEquals(60, controller.backgroundFpsThrottle)
        assertFalse(controller.isThermalThrottled)
    }

    @Test
    fun testAdaptiveQualityLowFpsDowngrade() {
        val controller = AdaptiveQualityController()
        var changedTier: QualityTier? = null
        var changeReason: String? = null

        controller.onQualityTierChanged = { tier, reason ->
            changedTier = tier
            changeReason = reason
        }

        // Simulate low FPS (<45) for 45 frames
        for (i in 1..45) {
            controller.onFrame(38.0f)
        }

        assertEquals(QualityTier.BALANCED, controller.currentTier)
        assertEquals(QualityTier.BALANCED, changedTier)
        assertNotNull(changeReason)
        assertEquals(0.6f, controller.particleMultiplier, 0.001f)
        assertEquals(30, controller.backgroundFpsThrottle)

        // Simulate continued low FPS (<45) for another 45 frames -> ECONOMY
        for (i in 1..45) {
            controller.onFrame(32.0f)
        }
        assertEquals(QualityTier.ECONOMY, controller.currentTier)
        assertEquals(0.3f, controller.particleMultiplier, 0.001f)
        assertEquals(15, controller.backgroundFpsThrottle)

        // Severe FPS drop (<25) -> Accelerated step-down to CRITICAL
        for (i in 1..20) {
            controller.onFrame(18.0f)
        }
        assertEquals(QualityTier.CRITICAL, controller.currentTier)
        assertEquals(0.1f, controller.particleMultiplier, 0.001f)
        assertEquals(10, controller.backgroundFpsThrottle)
    }

    @Test
    fun testAdaptiveQualityRecoveryOnHighFps() {
        val controller = AdaptiveQualityController()

        // Force to ECONOMY
        controller.applyTier(QualityTier.ECONOMY, "Test setup")
        assertEquals(QualityTier.ECONOMY, controller.currentTier)

        // Provide 90 consecutive frames of >= 55 FPS
        for (i in 1..90) {
            controller.onFrame(59.0f)
        }
        assertEquals(QualityTier.BALANCED, controller.currentTier)

        // Another 90 frames of 60 FPS -> Restores ULTRA
        for (i in 1..90) {
            controller.onFrame(60.0f)
        }
        assertEquals(QualityTier.ULTRA, controller.currentTier)
        assertEquals(1.0f, controller.particleMultiplier, 0.001f)
    }

    @Test
    fun testThermalStatusClampingAndWarning() {
        val controller = AdaptiveQualityController()
        var warningReceived: String? = null

        controller.onPerformanceWarning = { warning ->
            warningReceived = warning
        }

        // 1. Moderate Thermal (status = 2) -> clamp to ECONOMY
        controller.onThermalStatusChanged(2)
        assertTrue(controller.isThermalThrottled)
        assertEquals(QualityTier.ECONOMY, controller.currentTier)
        assertNotNull(warningReceived)
        assertTrue(warningReceived!!.contains("Qurilma qizimoqda"))

        // High FPS cannot upgrade tier while thermally throttled
        for (i in 1..100) {
            controller.onFrame(60.0f)
        }
        assertEquals(QualityTier.ECONOMY, controller.currentTier)

        // 2. Severe Thermal (status = 3) -> clamp to CRITICAL
        controller.onThermalStatusChanged(3)
        assertEquals(QualityTier.CRITICAL, controller.currentTier)

        // 3. Thermal status clears back to Normal (status = 0)
        controller.onThermalStatusChanged(0)
        assertFalse(controller.isThermalThrottled)
        assertEquals(QualityTier.BALANCED, controller.currentTier)
    }

    @Test
    fun testThermalMonitorStatusLabelsAndDispatch() {
        val monitor = ThermalMonitor(null)
        var dispatchedStatus = -1
        var dispatchedLabel = ""
        var warningReceived = ""

        monitor.onThermalStatusChanged = { status, label ->
            dispatchedStatus = status
            dispatchedLabel = label
        }
        monitor.onThermalWarning = { msg ->
            warningReceived = msg
        }

        monitor.simulateThermalStatus(0)
        assertEquals(0, dispatchedStatus)
        assertEquals("Normal", dispatchedLabel)
        assertEquals("", warningReceived)

        monitor.simulateThermalStatus(2) // Moderate
        assertEquals(2, dispatchedStatus)
        assertTrue(dispatchedLabel.contains("Moderate"))
        assertTrue(warningReceived.contains("Qurilma qizimoqda"))

        monitor.simulateThermalStatus(3) // Severe
        assertEquals(3, dispatchedStatus)
        assertTrue(dispatchedLabel.contains("Severe"))
    }

    @Test
    fun testMultiWindowStressAndSleepPolicy() {
        // Test 6+ concurrent active windows constraint (TZ 7.2 & 14)
        val wm = WindowManager3D(maxWindowsLimit = 6)
        // Default has 3 windows (system_monitor, spatial_notes, media_player)
        assertEquals(3, wm.activeWindows.size)

        // Add 5 more windows (total 8)
        for (i in 1..5) {
            wm.createWindow(
                appId = "app_$i",
                title = "App Window $i",
                width = 0.8f,
                height = 0.45f
            )
        }

        assertEquals(8, wm.allWindows.size)
        // Active windows limit is 6, so at least 2 lowest z-order windows must be sleeping
        val sleepingCount = wm.getSleepingWindowsCount()
        assertTrue("Expected at least 2 sleeping windows, got $sleepingCount", sleepingCount >= 2)
        assertEquals(6, wm.getActiveWindowsCount())

        // Focus a sleeping window -> wakes up
        val sleepingWindow = wm.allWindows.first { it.isSleeping }
        wm.setFocus(sleepingWindow.id)
        assertFalse(sleepingWindow.isSleeping)
        assertTrue(sleepingWindow.isFocused)
    }

    @Test
    fun testOnTrimMemorySleepsBackgroundWindows() {
        val wm = WindowManager3D(maxWindowsLimit = 6)
        assertEquals(3, wm.allWindows.size)

        // Currently, only 1 window is focused
        wm.onTrimMemory(80) // ComponentCallbacks2.TRIM_MEMORY_COMPLETE

        val sleepingCount = wm.getSleepingWindowsCount()
        // Exactly 2 unfocused windows must now be sleeping
        assertEquals(2, sleepingCount)
        assertEquals(1, wm.getActiveWindowsCount())

        val memEstimate = wm.getMemoryUsageEstimateMb()
        assertTrue(memEstimate > 0f)
    }

    @Test
    fun testWebViewCrashRecoverySimulation() {
        // Verify onRenderProcessGone recovery handling (TZ 14)
        val manifest = uz.orbitx.arport.engine.registry.AppManifest(
            id = "test_web",
            name = mapOf("uz" to "Test Web"),
            icon = "globe",
            version = "1.0.0",
            type = "web",
            category = "tools",
            entry = "https://test.example.com",
            allowedHosts = listOf("test.example.com")
        )

        val webSurface = uz.orbitx.arport.engine.web.WebAppSurface(
            context = null,
            manifest = manifest
        )

        var crashMessageReceived: String? = null
        webSurface.onCrashRecovered = { msg ->
            crashMessageReceived = msg
        }

        // 1. Simulate renderer crash
        val recoveredCrash = webSurface.handleRenderProcessGone(didCrash = true)
        assertTrue("handleRenderProcessGone must return true to prevent host termination", recoveredCrash)
        assertNotNull(crashMessageReceived)
        assertTrue(crashMessageReceived!!.contains("crash"))

        // 2. Simulate OOM system termination
        val recoveredOom = webSurface.handleRenderProcessGone(didCrash = false)
        assertTrue("handleRenderProcessGone must return true for OOM eviction", recoveredOom)
        assertTrue(crashMessageReceived.contains("OOM"))
    }

    @Test
    fun testSecuritySandboxBlocksAllForbiddenSchemes() {
        val allowedHosts = listOf("youtube.com", "google.com")

        // 1. Forbidden intent/external schemes must be strictly BLOCKED
        val blockedSchemes = listOf(
            "intent://something",
            "market://details?id=test",
            "tg://resolve?domain=orbitx",
            "instagram://user?username=orbitx",
            "vnd.youtube://video123",
            "whatsapp://send?text=hello",
            "fb://page/123",
            "twitter://user?screen_name=orbitx",
            "spotify://track/abc",
            "tel:+998901234567",
            "sms:+998901234567",
            "mailto:info@orbitx.uz"
        )

        for (url in blockedSchemes) {
            val decision = SandboxPolicy.evaluateNavigation(url, allowedHosts)
            assertEquals("Scheme $url must be BLOCKED", NavigationDecision.BLOCK, decision)
        }

        // 2. Disallowed local file schemes must be BLOCKED
        assertEquals(NavigationDecision.BLOCK, SandboxPolicy.evaluateNavigation("file:///sdcard/Download/secret.txt", allowedHosts))
        assertEquals(NavigationDecision.BLOCK, SandboxPolicy.evaluateNavigation("file:///data/data/uz.orbitx.arport/databases", allowedHosts))
        assertEquals(NavigationDecision.BLOCK, SandboxPolicy.evaluateNavigation("content://media/external/images/media", allowedHosts))

        // 3. Android asset URLs allowed internally
        assertEquals(NavigationDecision.ALLOW_IN_CURRENT_WINDOW, SandboxPolicy.evaluateNavigation("file:///android_asset/games/tetris/index.html", allowedHosts))

        // 4. External web navigation routed to internal browser tab
        assertEquals(NavigationDecision.OPEN_IN_BROWSER_TAB, SandboxPolicy.evaluateNavigation("https://external-news.com/article", allowedHosts))
    }

    @Test
    fun testCodebaseHasZeroExternalIntentEscapes() {
        // Lint Audit: ensure engine code does NOT contain un-sandboxed intent launches
        var engineSrcDir = File("src/main/kotlin/uz/orbitx/arport/engine")
        if (!engineSrcDir.exists()) {
            engineSrcDir = File("android/arcore-engine/src/main/kotlin/uz/orbitx/arport/engine")
        }
        if (!engineSrcDir.exists()) {
            engineSrcDir = File("../android/arcore-engine/src/main/kotlin/uz/orbitx/arport/engine")
        }
        assertTrue("Engine source directory should exist: ${engineSrcDir.absolutePath}", engineSrcDir.exists())

        val kotlinFiles = engineSrcDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Found ${kotlinFiles.size} Kotlin source files", kotlinFiles.isNotEmpty())

        val forbiddenPatterns = listOf(
            "Intent.ACTION_VIEW",
            "android.intent.action.VIEW",
            "Intent(Intent.ACTION_VIEW",
            "startActivity(Intent("
        )

        val violations = mutableListOf<String>()

        for (file in kotlinFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) continue // Skip comments

                for (pattern in forbiddenPatterns) {
                    if (trimmed.contains(pattern)) {
                        violations.add("${file.name}:${index + 1} contains '$pattern'")
                    }
                }
            }
        }

        assertTrue("Found intent escape violations: $violations", violations.isEmpty())
    }

    @Test
    fun testLongRunningStabilitySimulation() {
        // Simulates 10,000 frames (approx 3 continuous hours at 60 FPS in compressed time)
        val wm = WindowManager3D(maxWindowsLimit = 6)
        val qualityController = AdaptiveQualityController(windowManager = wm)

        var frameTick = 0L
        for (i in 1..10_000) {
            frameTick++
            val simulatedFps = 58.0f + ((i % 5) - 2.5f) // Jitter between 55.5 and 60.5 FPS
            qualityController.onFrame(simulatedFps)

            // Simulate occasional touch/focus
            if (i % 500 == 0) {
                val win = wm.allWindows[i % wm.allWindows.size]
                wm.setFocus(win.id)
            }
        }

        assertEquals(10_000L, frameTick)
        assertEquals(3, wm.allWindows.size) // No unbounded list accumulation
        assertEquals(QualityTier.ULTRA, qualityController.currentTier)
    }
}
