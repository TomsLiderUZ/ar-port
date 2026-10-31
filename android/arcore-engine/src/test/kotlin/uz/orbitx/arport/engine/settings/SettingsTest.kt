package uz.orbitx.arport.engine.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.orbitx.arport.engine.apps.SettingsApp
import uz.orbitx.arport.engine.registry.AppManifest

class SettingsTest {

    private fun createDummyManifest(id: String = "settings"): AppManifest {
        return AppManifest(
            id = id,
            name = mapOf("uz" to "AR Sozlamalar", "en" to "AR Settings", "ru" to "AR Настройки"),
            icon = "⚙️",
            version = "1.0.0",
            type = "native",
            category = "system",
            entry = "uz.orbitx.arport.engine.apps.SettingsApp",
            defaultAspect = "16:9",
            defaultWidth = 1.05f,
            defaultHeight = 0.59f,
            multiWindow = false,
            permissions = emptyList(),
            allowedHosts = emptyList(),
            sessionGroup = "system",
            minEngineVersion = "1.0",
            order = 10
        )
    }

    @Test
    fun testArSettingsDefaults() {
        val s = ArSettings()
        assertEquals(1, s.version)
        assertEquals("passthrough", s.backgroundMode)
        assertEquals(0.0f, s.backgroundOpacity, 0.001f)
        assertFalse(s.reduceTransparency)
        assertEquals(16.0f, s.glassBlurRadius, 0.001f)
        assertTrue(s.handTrackingEnabled)
        assertTrue(s.handGlowEnabled)
        assertEquals(0.052f, s.pinchThresholdEnter, 0.001f)
        assertEquals(6, s.maxOpenWindows)
        assertEquals(60, s.cameraFpsTarget)
        assertTrue(s.lowLatencyCameraMode)
        assertEquals("HOME", s.gestureShortcuts["PALM_UP"])
        assertEquals("CLOSE_WINDOW", s.gestureShortcuts["FIST"])
    }

    @Test
    fun testArSettingsJsonRoundtrip() {
        val original = ArSettings(
            version = 1,
            backgroundMode = "solid",
            backgroundColorHex = "#1C1C1E",
            backgroundOpacity = 0.5f,
            reduceTransparency = true,
            glassBlurRadius = 32.0f,
            handGlowEnabled = false,
            pinchThresholdEnter = 0.065f,
            maxOpenWindows = 8,
            performanceProfile = "high"
        )
        original.gestureShortcuts["PALM_UP"] = "RECENTER"

        val jsonStr = original.toJsonString()
        val restored = ArSettings.fromJsonString(jsonStr)

        assertEquals("solid", restored.backgroundMode)
        assertEquals("#1C1C1E", restored.backgroundColorHex)
        assertEquals(0.5f, restored.backgroundOpacity, 0.001f)
        assertTrue(restored.reduceTransparency)
        assertEquals(32.0f, restored.glassBlurRadius, 0.001f)
        assertFalse(restored.handGlowEnabled)
        assertEquals(0.065f, restored.pinchThresholdEnter, 0.001f)
        assertEquals(8, restored.maxOpenWindows)
        assertEquals("high", restored.performanceProfile)
        assertEquals("RECENTER", restored.gestureShortcuts["PALM_UP"])
    }

    @Test
    fun testSettingsStoreOperationsAndExport() {
        val store = SettingsStore.getInstance(null)
        val initial = store.currentSettings
        assertNotNull(initial)

        var notified = false
        store.addListener {
            notified = true
        }
        assertTrue(notified)

        val updated = ArSettings(backgroundMode = "gradient", maxOpenWindows = 3)
        store.saveSettings(updated)
        assertEquals("gradient", store.currentSettings.backgroundMode)
        assertEquals(3, store.currentSettings.maxOpenWindows)

        val exportFile = store.exportSettingsToSandbox()
        assertNotNull(exportFile)
        assertTrue(exportFile!!.exists())
        assertTrue(exportFile.length() > 0)

        // Reset to defaults
        val def = store.resetToDefaults()
        assertEquals("passthrough", def.backgroundMode)
        assertEquals(6, def.maxOpenWindows)
    }

    @Test
    fun testSettingsAppNavigationAndSections() {
        val app = SettingsApp(createDummyManifest())
        assertEquals(SettingsApp.Section.DISPLAY, app.currentSection)

        app.currentSection = SettingsApp.Section.GESTURES
        assertEquals(SettingsApp.Section.GESTURES, app.currentSection)

        app.currentSection = SettingsApp.Section.WINDOWS
        assertEquals(SettingsApp.Section.WINDOWS, app.currentSection)

        app.currentSection = SettingsApp.Section.PERFORMANCE
        assertEquals(SettingsApp.Section.PERFORMANCE, app.currentSection)

        app.currentSection = SettingsApp.Section.SANDBOX
        assertEquals(SettingsApp.Section.SANDBOX, app.currentSection)

        app.currentSection = SettingsApp.Section.ABOUT
        assertEquals(SettingsApp.Section.ABOUT, app.currentSection)
    }

    @Test
    fun testSettingsAppModifyValues() {
        val app = SettingsApp(createDummyManifest())

        // Modify display setting
        app.settings.backgroundMode = "solid"
        app.settings.reduceTransparency = true
        app.persistSettings()

        val store = SettingsStore.getInstance(null)
        assertEquals("solid", store.currentSettings.backgroundMode)
        assertTrue(store.currentSettings.reduceTransparency)

        // Modify gesture sensitivity
        app.settings.pinchThresholdEnter = 0.040f
        app.settings.handGlowEnabled = false
        app.persistSettings()
        assertEquals(0.040f, store.currentSettings.pinchThresholdEnter, 0.001f)
        assertFalse(store.currentSettings.handGlowEnabled)

        // Reset
        store.resetToDefaults()
    }
}
