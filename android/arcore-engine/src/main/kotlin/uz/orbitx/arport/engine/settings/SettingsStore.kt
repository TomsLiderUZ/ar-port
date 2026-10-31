package uz.orbitx.arport.engine.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File

/**
 * Data model for all configurable AR Port OS settings (TZ 10, QADAM 15).
 */
data class ArSettings(
    val version: Int = 1,

    // 1. Display
    var backgroundMode: String = "passthrough", // "passthrough", "solid", "gradient"
    var backgroundColorHex: String = "#000000",
    var backgroundOpacity: Float = 0.0f,        // 0.0..1.0
    var backgroundBlur: Float = 0.0f,           // 0.0..1.0
    var reduceTransparency: Boolean = false,
    var glassBlurRadius: Float = 16.0f,
    var displayBrightness: Float = 1.0f,
    var defaultWindowDistanceZ: Float = -1.8f,
    var defaultWindowScale: Float = 1.0f,

    // 2. Hands & Gestures
    var handTrackingEnabled: Boolean = true,
    var handGlowEnabled: Boolean = true,
    var handGlowColorHex: String = "#64D2FF",
    var handGlowIntensity: Float = 1.0f,
    var pinchThresholdEnter: Float = 0.052f,
    var pointerRayLength: Float = 2.5f,
    var gestureShortcuts: MutableMap<String, String> = mutableMapOf(
        "PALM_UP" to "HOME",
        "FIST" to "CLOSE_WINDOW",
        "SWIPE_LEFT" to "CYCLE_WINDOWS_NEXT",
        "SWIPE_RIGHT" to "CYCLE_WINDOWS_PREV"
    ),

    // 3. Audio
    var masterVolume: Float = 1.0f,
    var spatialAudioEnabled: Boolean = true,
    var audioOutputMode: String = "speaker",    // "speaker", "headphones"

    // 4. Camera
    var lowLatencyCameraMode: Boolean = true,
    var cameraFpsTarget: Int = 60,

    // 5. Windows
    var maxOpenWindows: Int = 6,
    var backgroundWindowFps: Int = 30,
    var sleepInactiveWindows: Boolean = true,

    // 6. Performance & Thermal
    var performanceProfile: String = "auto",    // "low", "medium", "high", "auto"
    var thermalProtectionEnabled: Boolean = true,
    var batterySaverEnabled: Boolean = false,

    // 7. Sandbox & Security
    var blockExternalIntents: Boolean = true,
    var blockAdsAndTrackers: Boolean = true,

    // 8. Accessibility
    var uiTextScale: Float = 1.0f,
    var highContrast: Boolean = false,
    var reduceMotion: Boolean = false
) {
    fun toJsonString(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": $version,\n")
        sb.append("  \"backgroundMode\": \"$backgroundMode\",\n")
        sb.append("  \"backgroundColorHex\": \"$backgroundColorHex\",\n")
        sb.append("  \"backgroundOpacity\": $backgroundOpacity,\n")
        sb.append("  \"backgroundBlur\": $backgroundBlur,\n")
        sb.append("  \"reduceTransparency\": $reduceTransparency,\n")
        sb.append("  \"glassBlurRadius\": $glassBlurRadius,\n")
        sb.append("  \"displayBrightness\": $displayBrightness,\n")
        sb.append("  \"defaultWindowDistanceZ\": $defaultWindowDistanceZ,\n")
        sb.append("  \"defaultWindowScale\": $defaultWindowScale,\n")
        sb.append("  \"handTrackingEnabled\": $handTrackingEnabled,\n")
        sb.append("  \"handGlowEnabled\": $handGlowEnabled,\n")
        sb.append("  \"handGlowColorHex\": \"$handGlowColorHex\",\n")
        sb.append("  \"handGlowIntensity\": $handGlowIntensity,\n")
        sb.append("  \"pinchThresholdEnter\": $pinchThresholdEnter,\n")
        sb.append("  \"pointerRayLength\": $pointerRayLength,\n")
        sb.append("  \"masterVolume\": $masterVolume,\n")
        sb.append("  \"spatialAudioEnabled\": $spatialAudioEnabled,\n")
        sb.append("  \"audioOutputMode\": \"$audioOutputMode\",\n")
        sb.append("  \"lowLatencyCameraMode\": $lowLatencyCameraMode,\n")
        sb.append("  \"cameraFpsTarget\": $cameraFpsTarget,\n")
        sb.append("  \"maxOpenWindows\": $maxOpenWindows,\n")
        sb.append("  \"backgroundWindowFps\": $backgroundWindowFps,\n")
        sb.append("  \"sleepInactiveWindows\": $sleepInactiveWindows,\n")
        sb.append("  \"performanceProfile\": \"$performanceProfile\",\n")
        sb.append("  \"thermalProtectionEnabled\": $thermalProtectionEnabled,\n")
        sb.append("  \"batterySaverEnabled\": $batterySaverEnabled,\n")
        sb.append("  \"blockExternalIntents\": $blockExternalIntents,\n")
        sb.append("  \"blockAdsAndTrackers\": $blockAdsAndTrackers,\n")
        sb.append("  \"uiTextScale\": $uiTextScale,\n")
        sb.append("  \"highContrast\": $highContrast,\n")
        sb.append("  \"reduceMotion\": $reduceMotion,\n")
        val shortcutsPairs = gestureShortcuts.entries.joinToString(",") { "\"${it.key}\": \"${it.value}\"" }
        sb.append("  \"gestureShortcuts\": { $shortcutsPairs }\n")
        sb.append("}")
        return sb.toString()
    }

    fun toJson(): JSONObject {
        return try {
            JSONObject(toJsonString())
        } catch (_: Exception) {
            JSONObject()
        }
    }

    companion object {
        fun fromJsonString(str: String): ArSettings {
            val settings = ArSettings()
            fun getStr(key: String): String? {
                val match = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(str)
                return match?.groupValues?.get(1)
            }
            fun getFloat(key: String): Float? {
                val match = Regex("\"$key\"\\s*:\\s*([0-9.]+)").find(str)
                return match?.groupValues?.get(1)?.toFloatOrNull()
            }
            fun getInt(key: String): Int? {
                val match = Regex("\"$key\"\\s*:\\s*([0-9]+)").find(str)
                return match?.groupValues?.get(1)?.toIntOrNull()
            }
            fun getBool(key: String): Boolean? {
                val match = Regex("\"$key\"\\s*:\\s*(true|false)").find(str)
                return match?.groupValues?.get(1)?.toBooleanStrictOrNull()
            }

            getStr("backgroundMode")?.let { settings.backgroundMode = it }
            getStr("backgroundColorHex")?.let { settings.backgroundColorHex = it }
            getFloat("backgroundOpacity")?.let { settings.backgroundOpacity = it }
            getFloat("backgroundBlur")?.let { settings.backgroundBlur = it }
            getBool("reduceTransparency")?.let { settings.reduceTransparency = it }
            getFloat("glassBlurRadius")?.let { settings.glassBlurRadius = it }
            getFloat("displayBrightness")?.let { settings.displayBrightness = it }
            getFloat("defaultWindowDistanceZ")?.let { settings.defaultWindowDistanceZ = it }
            getFloat("defaultWindowScale")?.let { settings.defaultWindowScale = it }

            getBool("handTrackingEnabled")?.let { settings.handTrackingEnabled = it }
            getBool("handGlowEnabled")?.let { settings.handGlowEnabled = it }
            getStr("handGlowColorHex")?.let { settings.handGlowColorHex = it }
            getFloat("handGlowIntensity")?.let { settings.handGlowIntensity = it }
            getFloat("pinchThresholdEnter")?.let { settings.pinchThresholdEnter = it }
            getFloat("pointerRayLength")?.let { settings.pointerRayLength = it }

            val shortcutsBlock = Regex("\"gestureShortcuts\"\\s*:\\s*\\{([^}]+)\\}").find(str)?.groupValues?.get(1)
            if (shortcutsBlock != null) {
                val matches = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"").findAll(shortcutsBlock)
                settings.gestureShortcuts.clear()
                for (m in matches) {
                    settings.gestureShortcuts[m.groupValues[1]] = m.groupValues[2]
                }
            }

            getFloat("masterVolume")?.let { settings.masterVolume = it }
            getBool("spatialAudioEnabled")?.let { settings.spatialAudioEnabled = it }
            getStr("audioOutputMode")?.let { settings.audioOutputMode = it }

            getBool("lowLatencyCameraMode")?.let { settings.lowLatencyCameraMode = it }
            getInt("cameraFpsTarget")?.let { settings.cameraFpsTarget = it }

            getInt("maxOpenWindows")?.let { settings.maxOpenWindows = it }
            getInt("backgroundWindowFps")?.let { settings.backgroundWindowFps = it }
            getBool("sleepInactiveWindows")?.let { settings.sleepInactiveWindows = it }

            getStr("performanceProfile")?.let { settings.performanceProfile = it }
            getBool("thermalProtectionEnabled")?.let { settings.thermalProtectionEnabled = it }
            getBool("batterySaverEnabled")?.let { settings.batterySaverEnabled = it }

            getBool("blockExternalIntents")?.let { settings.blockExternalIntents = it }
            getBool("blockAdsAndTrackers")?.let { settings.blockAdsAndTrackers = it }

            getFloat("uiTextScale")?.let { settings.uiTextScale = it }
            getBool("highContrast")?.let { settings.highContrast = it }
            getBool("reduceMotion")?.let { settings.reduceMotion = it }

            return settings
        }

        fun fromJson(json: JSONObject): ArSettings {
            val jsonStr = try { json.toString() } catch (_: Exception) { "" }
            return if (jsonStr.isNotBlank() && jsonStr != "{}") {
                fromJsonString(jsonStr)
            } else {
                ArSettings()
            }
        }
    }
}

/**
 * Manages persistence, export/import, and live notification for ArSettings (TZ 10, QADAM 15).
 */
class SettingsStore private constructor(
    private val context: Context?
) {
    companion object {
        private const val PREFS_NAME = "ar_port_os_settings"
        private const val KEY_SETTINGS_JSON = "key_settings_json"

        @Volatile
        private var instance: SettingsStore? = null

        fun getInstance(context: Context? = null): SettingsStore {
            return instance ?: synchronized(this) {
                instance ?: SettingsStore(context?.applicationContext ?: context).also { instance = it }
            }
        }
    }

    private val listeners = mutableListOf<(ArSettings) -> Unit>()
    var currentSettings: ArSettings = ArSettings()
        private set

    init {
        loadSettings()
    }

    fun addListener(listener: (ArSettings) -> Unit) {
        listeners.add(listener)
        listener(currentSettings)
    }

    fun removeListener(listener: (ArSettings) -> Unit) {
        listeners.remove(listener)
    }

    fun loadSettings(): ArSettings {
        val prefs = getPrefs()
        if (prefs != null) {
            val jsonStr = prefs.getString(KEY_SETTINGS_JSON, null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    currentSettings = ArSettings.fromJsonString(jsonStr)
                    return currentSettings
                } catch (_: Exception) {}
            }
        }
        return currentSettings
    }

    fun saveSettings(settings: ArSettings) {
        currentSettings = settings
        val prefs = getPrefs()
        if (prefs != null) {
            try {
                val jsonStr = settings.toJsonString()
                prefs.edit().putString(KEY_SETTINGS_JSON, jsonStr).apply()
            } catch (_: Exception) {}
        }
        notifyListeners()
    }

    fun resetToDefaults(): ArSettings {
        val defaults = ArSettings()
        saveSettings(defaults)
        return defaults
    }

    fun exportSettingsToSandbox(): File? {
        val baseDir = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".", "ar_port_sandbox")
        val exportDir = File(baseDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val exportFile = File(exportDir, "ar_port_settings_backup.json")
        try {
            exportFile.writeText(currentSettings.toJsonString())
            return exportFile
        } catch (_: Exception) {
            return null
        }
    }

    fun importSettingsFromJson(jsonStr: String): Boolean {
        return try {
            val imported = ArSettings.fromJsonString(jsonStr)
            saveSettings(imported)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyListeners() {
        for (listener in listeners) {
            try {
                listener(currentSettings)
            } catch (_: Exception) {}
        }
    }

    private fun getPrefs(): SharedPreferences? {
        return try {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }
}
