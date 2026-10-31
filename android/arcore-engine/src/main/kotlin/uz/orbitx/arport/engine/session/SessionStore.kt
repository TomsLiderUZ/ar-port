package uz.orbitx.arport.engine.session

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * Central session and cookie store ensuring a unified login state across all web apps (TZ 12.2).
 * Enables Single Sign-On behavior where logging into Google in one window automatically
 * shares credentials with YouTube, Spotify, and other services in the same group.
 */
class SessionStore private constructor() {

    private val loggedInGroups = mutableSetOf<String>()

    companion object {
        @Volatile
        private var instance: SessionStore? = null

        fun getInstance(): SessionStore {
            return instance ?: synchronized(this) {
                instance ?: SessionStore().also { instance = it }
            }
        }
    }

    /**
     * Initializes global cookie policies.
     */
    fun init(context: Context) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
        } catch (_: Exception) {
            // Safe fallback for host JVM tests
        }
    }

    /**
     * Enables 3rd party cookies on a specific WebView instance.
     */
    fun enableThirdPartyCookies(webView: android.webkit.WebView) {
        try {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    /**
     * Persists in-memory cookies to storage asynchronously.
     */
    fun flush() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    /**
     * Retrieves stored cookies for a given URL.
     */
    fun getCookies(url: String): String? {
        return try {
            CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Sets a cookie for a target URL.
     */
    fun setCookie(url: String, cookieValue: String) {
        try {
            CookieManager.getInstance().setCookie(url, cookieValue)
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    /**
     * Tracks an active login session for a session group (e.g. "google", "telegram", "meta").
     */
    fun markSessionActive(sessionGroup: String) {
        synchronized(loggedInGroups) {
            loggedInGroups.add(sessionGroup.lowercase())
        }
    }

    /**
     * Checks if a session group is currently authenticated.
     */
    fun hasActiveSession(sessionGroup: String): Boolean {
        synchronized(loggedInGroups) {
            return loggedInGroups.contains(sessionGroup.lowercase())
        }
    }

    /**
     * Clears all cookies and web storage (e.g. on user logout / cache reset).
     */
    fun clearAll(onComplete: (() -> Unit)? = null) {
        try {
            CookieManager.getInstance().removeAllCookies {
                WebStorage.getInstance().deleteAllData()
                synchronized(loggedInGroups) {
                    loggedInGroups.clear()
                }
                flush()
                onComplete?.invoke()
            }
        } catch (_: Exception) {
            synchronized(loggedInGroups) {
                loggedInGroups.clear()
            }
            onComplete?.invoke()
        }
    }
}

