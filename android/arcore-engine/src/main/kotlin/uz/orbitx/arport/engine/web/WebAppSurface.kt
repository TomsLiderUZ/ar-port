package uz.orbitx.arport.engine.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import uz.orbitx.arport.engine.registry.AppManifest
import uz.orbitx.arport.engine.sandbox.NavigationDecision
import uz.orbitx.arport.engine.sandbox.SandboxPolicy
import uz.orbitx.arport.engine.session.SessionStore
import uz.orbitx.arport.engine.window.ContentSurface

/**
 * Offscreen Web Application Surface (TZ 9.1 & 12).
 * Renders an Android WebView directly into an OpenGL 2D texture, enforces sandbox isolation,
 * integrates with unified SessionStore, and routes pointer gestures to web content.
 */
class WebAppSurface(
    private val context: Context,
    val manifest: AppManifest,
    val surfaceWidth: Int = 1280,
    val surfaceHeight: Int = 720,
    val isDesktopMode: Boolean = false,
    val showNavigationBar: Boolean = false
) {
    val contentSurface = ContentSurface(surfaceWidth, surfaceHeight)
    private val mainHandler = Handler(Looper.getMainLooper())

    var webView: WebView? = null
        private set

    var currentUrl: String = manifest.entry
    var pageTitle: String = manifest.getLocalizedName("uz")
    var isLoading: Boolean = false
    var loadProgress: Int = 0

    var onOpenExternalUrl: ((String) -> Unit)? = null
    var onTitleChanged: ((String) -> Unit)? = null
    var onPopupRequested: ((String) -> Unit)? = null

    companion object {
        const val NAV_BAR_HEIGHT = 68f

        fun resolveEntryUrl(entry: String): String {
            if (entry.startsWith("http://") || entry.startsWith("https://") || entry.startsWith("file://")) {
                return entry
            }
            val cleanPath = entry.removePrefix("assets/").removePrefix("/")
            return "file:///android_asset/$cleanPath"
        }
    }

    init {
        // Initialize WebView on Main UI Thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            setupWebView()
        } else {
            mainHandler.post { setupWebView() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        try {
            val wv = WebView(context)
            wv.layout(0, 0, surfaceWidth, (surfaceHeight - (if (showNavigationBar) NAV_BAR_HEIGHT.toInt() else 0)))

            val settings = wv.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportMultipleWindows(true)
            settings.userAgentString = SandboxPolicy.getCleanUserAgent(isDesktopMode)

            SessionStore.getInstance().enableThirdPartyCookies(wv)

            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return handleNavigation(url)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    return handleNavigation(url)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    isLoading = true
                    currentUrl = url ?: currentUrl
                    captureFrame()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                    currentUrl = url ?: currentUrl
                    SessionStore.getInstance().flush()
                    captureFrame()
                }
            }

            wv.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadProgress = newProgress
                    captureFrame()
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    title?.let {
                        pageTitle = it
                        onTitleChanged?.invoke(it)
                    }
                    captureFrame()
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    if (transport != null) {
                        val popupWv = WebView(context)
                        popupWv.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                val popupUrl = req?.url?.toString() ?: return false
                                onPopupRequested?.invoke(popupUrl)
                                return true
                            }
                        }
                        transport.webView = popupWv
                        resultMsg.sendToTarget()
                        return true
                    }
                    return false
                }
            }

            webView = wv
            val initialUrl = resolveEntryUrl(manifest.entry)
            currentUrl = initialUrl
            wv.loadUrl(initialUrl)
        } catch (_: Exception) {
            // Safe fallback for mocked/headless environments
            renderPlaceholderFrame()
        }
    }

    /**
     * Sends an AR spatial gesture directly to the web app / game via JavaScript hook.
     */
    fun sendGameGesture(gestureType: String) {
        mainHandler.post {
            try {
                webView?.evaluateJavascript(
                    "if (typeof window.onArGesture === 'function') { window.onArGesture('$gestureType'); }",
                    null
                )
            } catch (_: Exception) {}
        }
    }

    private fun handleNavigation(url: String): Boolean {
        val decision = SandboxPolicy.evaluateNavigation(url, manifest.allowedHosts)
        when (decision) {
            NavigationDecision.ALLOW_IN_CURRENT_WINDOW -> {
                return false // Let WebView load it
            }
            NavigationDecision.OPEN_IN_BROWSER_TAB -> {
                mainHandler.post { onOpenExternalUrl?.invoke(url) }
                return true // Intercepted, do not escape sandbox
            }
            NavigationDecision.BLOCK -> {
                return true // Prohibited intent/scheme blocked
            }
        }
    }

    /**
     * Dispatches spatial UV raycast hit into WebView MotionEvents.
     */
    fun dispatchTouchEvent(u: Float, v: Float, action: Int) {
        val wv = webView ?: return
        val clampedU = u.coerceIn(0f, 1f)
        val clampedV = v.coerceIn(0f, 1f)

        val px = clampedU * surfaceWidth
        val py = (1.0f - clampedV) * surfaceHeight

        // Check if hitting navigation bar in browser mode
        if (showNavigationBar && py < NAV_BAR_HEIGHT) {
            if (action == MotionEvent.ACTION_UP) {
                handleNavBarClick(px)
            }
            return
        }

        val adjustedPy = if (showNavigationBar) py - NAV_BAR_HEIGHT else py
        mainHandler.post {
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, action, px, adjustedPy, 0)
            wv.dispatchTouchEvent(event)
            event.recycle()
            captureFrame()
        }
    }

    private fun handleNavBarClick(px: Float) {
        // Back: 10..60, Forward: 70..120, Reload: 130..180
        when {
            px in 10f..65f -> goBack()
            px in 75f..130f -> goForward()
            px in 140f..195f -> reload()
        }
    }

    fun goBack() {
        mainHandler.post {
            if (webView?.canGoBack() == true) {
                webView?.goBack()
            }
        }
    }

    fun goForward() {
        mainHandler.post {
            if (webView?.canGoForward() == true) {
                webView?.goForward()
            }
        }
    }

    fun reload() {
        mainHandler.post {
            webView?.reload()
        }
    }

    fun loadUrl(url: String) {
        mainHandler.post {
            currentUrl = url
            webView?.loadUrl(url)
        }
    }

    /**
     * Captures current WebView frame into offscreen ContentSurface bitmap.
     */
    fun captureFrame() {
        val wv = webView ?: return
        contentSurface.draw { canvas ->
            if (showNavigationBar) {
                drawBrowserNavBar(canvas)
                canvas.save()
                canvas.translate(0f, NAV_BAR_HEIGHT)
                wv.draw(canvas)
                canvas.restore()
            } else {
                wv.draw(canvas)
            }
        }
    }

    private fun drawBrowserNavBar(canvas: Canvas) {
        val navBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6202128")
        }
        canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), NAV_BAR_HEIGHT, navBgPaint)

        val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33FFFFFF")
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }

        // Back Button
        canvas.drawRoundRect(RectF(12f, 10f, 62f, 58f), 12f, 12f, btnBgPaint)
        canvas.drawText("◀", 37f, 44f, iconPaint)

        // Forward Button
        canvas.drawRoundRect(RectF(74f, 10f, 124f, 58f), 12f, 12f, btnBgPaint)
        canvas.drawText("▶", 99f, 44f, iconPaint)

        // Reload Button
        canvas.drawRoundRect(RectF(136f, 10f, 186f, 58f), 12f, 12f, btnBgPaint)
        canvas.drawText("🔄", 161f, 44f, iconPaint)

        // URL Address Bar
        val urlBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26FFFFFF")
        }
        val urlBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val urlRect = RectF(200f, 10f, surfaceWidth - 20f, 58f)
        canvas.drawRoundRect(urlRect, 14f, 14f, urlBarPaint)
        canvas.drawRoundRect(urlRect, 14f, 14f, urlBorderPaint)

        val urlTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64D2FF")
            textSize = 22f
        }
        val displayUrl = if (currentUrl.length > 70) currentUrl.take(68) + "..." else currentUrl
        canvas.drawText("🔒 $displayUrl", 220f, 42f, urlTextPaint)

        // Progress bar if loading
        if (isLoading && loadProgress in 1..99) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0A84FF")
                strokeWidth = 3f
            }
            val progressW = (surfaceWidth.toFloat() * (loadProgress / 100f))
            canvas.drawLine(0f, NAV_BAR_HEIGHT, progressW, NAV_BAR_HEIGHT, progressPaint)
        }
    }

    private fun renderPlaceholderFrame() {
        contentSurface.draw { canvas ->
            canvas.drawColor(Color.parseColor("#E6181920"))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                isFakeBoldText = true
            }
            canvas.drawText("${manifest.icon} ${manifest.getLocalizedName("uz")}", 60f, 110f, paint)

            paint.textSize = 26f
            paint.color = Color.parseColor("#8E8E93")
            paint.isFakeBoldText = false
            canvas.drawText("Xavfsiz Web Sandbox • ${manifest.entry}", 60f, 160f, paint)
        }
    }

    fun release() {
        mainHandler.post {
            webView?.stopLoading()
            webView?.clearHistory()
            webView?.destroy()
            webView = null
            contentSurface.release()
        }
    }
}

