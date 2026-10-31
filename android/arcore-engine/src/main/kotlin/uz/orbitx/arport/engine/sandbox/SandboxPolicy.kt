package uz.orbitx.arport.engine.sandbox

import android.net.Uri

enum class NavigationDecision {
    ALLOW_IN_CURRENT_WINDOW,
    OPEN_IN_BROWSER_TAB,
    BLOCK
}

/**
 * Strict Security Sandbox Policy (TZ 12.1 & 12.3).
 * Guarantees that no external intents, system browsers, or unauthorized links escape
 * the AR Port environment. Intercepts external web URLs and routes them to the internal browser.
 */
object SandboxPolicy {

    // Default clean Chrome User-Agents (avoids Google 403 disallowed_useragent)
    const val CLEAN_MOBILE_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    const val CLEAN_DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    // Schemes strictly forbidden from escaping into system intents
    private val BLOCKED_SCHEMES = setOf(
        "intent",
        "market",
        "tg",
        "instagram",
        "vnd.youtube",
        "whatsapp",
        "fb",
        "twitter",
        "spotify",
        "tel",
        "sms",
        "mailto"
    )

    /**
     * Sanitizes a User-Agent string by stripping WebView markers (`; wv` and `Version/4.0`).
     */
    fun sanitizeUserAgent(rawUserAgent: String): String {
        return rawUserAgent
            .replace("; wv", "")
            .replace("; wv;", ";")
            .replace(Regex("Version/\\d+\\.\\d+ "), "")
            .trim()
    }

    /**
     * Returns appropriate clean User-Agent.
     */
    fun getCleanUserAgent(isDesktop: Boolean = false): String {
        return if (isDesktop) CLEAN_DESKTOP_USER_AGENT else CLEAN_MOBILE_USER_AGENT
    }

    /**
     * Evaluates whether a target host matches the allowed host patterns.
     * Supports exact matching, wildcard subdomain matching (*.domain.com), and global wildcard (*).
     */
    fun isHostAllowed(urlString: String, allowedHosts: List<String>): Boolean {
        if (allowedHosts.isEmpty()) return false
        if (allowedHosts.contains("*")) return true

        val host = extractHost(urlString)?.lowercase() ?: return false

        for (pattern in allowedHosts) {
            val p = pattern.trim().lowercase()
            if (p == "*") return true

            if (p.startsWith("*.")) {
                val rootDomain = p.substring(2)
                if (host == rootDomain || host.endsWith(".$rootDomain")) {
                    return true
                }
            } else if (p == host) {
                return true
            }
        }
        return false
    }

    /**
     * Determines how a navigation request should be handled according to the sandbox policy.
     */
    fun evaluateNavigation(
        urlString: String,
        allowedHosts: List<String>
    ): NavigationDecision {
        val trimmed = urlString.trim()
        val scheme = extractScheme(trimmed)?.lowercase() ?: return NavigationDecision.BLOCK

        // 1. Block prohibited custom intent schemes immediately
        if (BLOCKED_SCHEMES.contains(scheme)) {
            return NavigationDecision.BLOCK
        }

        // 2. Data and about:blank are allowed internally
        if (scheme == "data" || scheme == "about" || scheme == "javascript") {
            return NavigationDecision.ALLOW_IN_CURRENT_WINDOW
        }

        // 3. For http / https requests:
        if (scheme == "http" || scheme == "https") {
            // Check if URL belongs to current app's domain
            if (isHostAllowed(trimmed, allowedHosts)) {
                return NavigationDecision.ALLOW_IN_CURRENT_WINDOW
            }
            // External domain -> Route to internal Google Browser window
            return NavigationDecision.OPEN_IN_BROWSER_TAB
        }

        // 4. Any other unrecognized scheme is blocked
        return NavigationDecision.BLOCK
    }

    private fun extractHost(urlString: String): String? {
        return try {
            val uri = java.net.URI(urlString)
            val h = uri.host
            if (h != null) h else extractHostFallback(urlString)
        } catch (_: Exception) {
            extractHostFallback(urlString)
        }
    }

    private fun extractHostFallback(urlString: String): String? {
        var s = urlString
        val protoIdx = s.indexOf("://")
        if (protoIdx >= 0) {
            s = s.substring(protoIdx + 3)
        }
        val slashIdx = s.indexOf('/')
        if (slashIdx >= 0) {
            s = s.substring(0, slashIdx)
        }
        val colonIdx = s.indexOf(':')
        if (colonIdx >= 0) {
            s = s.substring(0, colonIdx)
        }
        return s.trim().ifEmpty { null }
    }

    private fun extractScheme(urlString: String): String? {
        val protoIdx = urlString.indexOf("://")
        if (protoIdx > 0) {
            return urlString.substring(0, protoIdx)
        }
        val colonIdx = urlString.indexOf(':')
        if (colonIdx > 0) {
            return urlString.substring(0, colonIdx)
        }
        return null
    }
}

