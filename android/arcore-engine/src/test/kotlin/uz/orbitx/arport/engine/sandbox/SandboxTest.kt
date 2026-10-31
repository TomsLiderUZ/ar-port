package uz.orbitx.arport.engine.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.orbitx.arport.engine.session.SessionStore

class SandboxTest {

    @Test
    fun testExactHostAllowed() {
        val allowedHosts = listOf("youtube.com", "instagram.com")

        assertTrue(SandboxPolicy.isHostAllowed("https://youtube.com/watch?v=123", allowedHosts))
        assertTrue(SandboxPolicy.isHostAllowed("https://instagram.com/explore", allowedHosts))
        assertFalse(SandboxPolicy.isHostAllowed("https://malicious.com", allowedHosts))
    }

    @Test
    fun testWildcardSubdomainAllowed() {
        val allowedHosts = listOf("*.youtube.com", "*.telegram.org")

        assertTrue(SandboxPolicy.isHostAllowed("https://m.youtube.com", allowedHosts))
        assertTrue(SandboxPolicy.isHostAllowed("https://web.telegram.org/a/", allowedHosts))
        assertTrue(SandboxPolicy.isHostAllowed("https://api.v2.telegram.org", allowedHosts))
        assertTrue(SandboxPolicy.isHostAllowed("https://youtube.com", allowedHosts))

        // Host ending with root domain without dot must not match
        assertFalse(SandboxPolicy.isHostAllowed("https://notyoutube.com", allowedHosts))
    }

    @Test
    fun testGlobalWildcardAllowed() {
        val browserHosts = listOf("*")
        assertTrue(SandboxPolicy.isHostAllowed("https://en.wikipedia.org", browserHosts))
        assertTrue(SandboxPolicy.isHostAllowed("https://github.com", browserHosts))
    }

    @Test
    fun testBlockedIntentSchemes() {
        val allowedHosts = listOf("*.youtube.com", "youtube.com")

        assertEquals(
            NavigationDecision.BLOCK,
            SandboxPolicy.evaluateNavigation("intent://video#Intent;package=com.google.android.youtube;end", allowedHosts)
        )
        assertEquals(
            NavigationDecision.BLOCK,
            SandboxPolicy.evaluateNavigation("market://details?id=com.instagram.android", allowedHosts)
        )
        assertEquals(
            NavigationDecision.BLOCK,
            SandboxPolicy.evaluateNavigation("tg://resolve?domain=orbitx", allowedHosts)
        )
        assertEquals(
            NavigationDecision.BLOCK,
            SandboxPolicy.evaluateNavigation("instagram://user?username=orbitx", allowedHosts)
        )
        assertEquals(
            NavigationDecision.BLOCK,
            SandboxPolicy.evaluateNavigation("vnd.youtube://12345", allowedHosts)
        )
    }

    @Test
    fun testNavigationDecisionAllowedInCurrentWindow() {
        val allowedHosts = listOf("youtube.com", "*.youtube.com")
        val decision = SandboxPolicy.evaluateNavigation("https://m.youtube.com/trending", allowedHosts)
        assertEquals(NavigationDecision.ALLOW_IN_CURRENT_WINDOW, decision)
    }

    @Test
    fun testNavigationDecisionRedirectsExternalToBrowserTab() {
        val allowedHosts = listOf("youtube.com", "*.youtube.com")
        // External link clicked inside YouTube (e.g. sponsor link)
        val decision = SandboxPolicy.evaluateNavigation("https://patreon.com/creator", allowedHosts)
        assertEquals(NavigationDecision.OPEN_IN_BROWSER_TAB, decision)
    }

    @Test
    fun testSanitizeUserAgentStripsWebViewMarkers() {
        val rawUa = "Mozilla/5.0 (Linux; U; Android 14; Pixel 8; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.0.0 Mobile Safari/537.36"
        val cleanUa = SandboxPolicy.sanitizeUserAgent(rawUa)

        assertFalse("Clean UA must not contain '; wv'", cleanUa.contains("; wv"))
        assertFalse("Clean UA must not contain 'Version/4.0'", cleanUa.contains("Version/4.0"))
        assertTrue("Clean UA must retain Chrome version", cleanUa.contains("Chrome/128.0.0.0"))
    }

    @Test
    fun testCleanChromeUserAgents() {
        val mobileUa = SandboxPolicy.getCleanUserAgent(isDesktop = false)
        val desktopUa = SandboxPolicy.getCleanUserAgent(isDesktop = true)

        assertFalse(mobileUa.contains("; wv"))
        assertFalse(desktopUa.contains("; wv"))
        assertTrue(mobileUa.contains("Mobile Safari"))
        assertTrue(desktopUa.contains("Windows NT"))
    }

    @Test
    fun testSessionStoreActiveSessionTracking() {
        val sessionStore = SessionStore.getInstance()

        sessionStore.markSessionActive("google")
        assertTrue(sessionStore.hasActiveSession("google"))
        assertFalse(sessionStore.hasActiveSession("telegram"))

        sessionStore.markSessionActive("telegram")
        assertTrue(sessionStore.hasActiveSession("telegram"))

        sessionStore.clearAll()
        assertFalse(sessionStore.hasActiveSession("google"))
        assertFalse(sessionStore.hasActiveSession("telegram"))
    }
}

