package uz.orbitx.arport.engine.registry

import org.json.JSONObject

/**
 * Metadata definition for an AR Port app or plugin parsed from manifest.json.
 */
data class AppManifest(
    val id: String,
    val name: Map<String, String>,
    val icon: String,
    val version: String,
    val type: String,               // "web" | "native" | "game2d" | "game3d"
    val category: String,           // "media" | "social" | "tools" | "games" | "system"
    val entry: String,
    val defaultAspect: String = "16:9",
    val defaultWidth: Float = 1.0f,
    val defaultHeight: Float = 0.5625f,
    val multiWindow: Boolean = false,
    val permissions: List<String> = emptyList(),
    val allowedHosts: List<String> = emptyList(),
    val sessionGroup: String = "default",
    val minEngineVersion: String = "1.0",
    val order: Int = 100
) {
    fun getLocalizedName(lang: String = "uz"): String {
        return name[lang] ?: name["uz"] ?: name["en"] ?: id
    }

    companion object {
        fun fromJson(json: JSONObject): AppManifest {
            val nameMap = mutableMapOf<String, String>()
            val nameObj = json.optJSONObject("name")
            if (nameObj != null) {
                val keys = nameObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    nameMap[k] = nameObj.optString(k, "")
                }
            } else {
                val singleName = json.optString("name", "")
                nameMap["uz"] = singleName
                nameMap["en"] = singleName
            }

            val sizeObj = json.optJSONObject("defaultSize")
            val w = sizeObj?.optDouble("w", 1.0)?.toFloat() ?: 1.0f
            val h = sizeObj?.optDouble("h", 0.5625)?.toFloat() ?: (w * (9.0f / 16.0f))

            val perms = mutableListOf<String>()
            val permsArr = json.optJSONArray("permissions")
            if (permsArr != null) {
                for (i in 0 until permsArr.length()) {
                    perms.add(permsArr.getString(i))
                }
            }

            val hosts = mutableListOf<String>()
            val hostsArr = json.optJSONArray("allowedHosts")
            if (hostsArr != null) {
                for (i in 0 until hostsArr.length()) {
                    hosts.add(hostsArr.getString(i))
                }
            }

            return AppManifest(
                id = json.getString("id"),
                name = nameMap,
                icon = json.optString("icon", "📱"),
                version = json.optString("version", "1.0.0"),
                type = json.getString("type"),
                category = json.getString("category"),
                entry = json.getString("entry"),
                defaultAspect = json.optString("defaultAspect", "16:9"),
                defaultWidth = w,
                defaultHeight = h,
                multiWindow = json.optBoolean("multiWindow", false),
                permissions = perms,
                allowedHosts = hosts,
                sessionGroup = json.optString("sessionGroup", "default"),
                minEngineVersion = json.optString("minEngineVersion", "1.0"),
                order = json.optInt("order", 100)
            )
        }
    }
}

