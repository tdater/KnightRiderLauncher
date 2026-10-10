package com.aiconsole.knightriderlauncher.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.aiconsole.knightriderlauncher.data.DeviceProfile
import com.aiconsole.knightriderlauncher.data.FreezeWatchdog
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.stream.InstalledScript
import com.aiconsole.knightriderlauncher.stream.StreamUrls
import com.aiconsole.knightriderlauncher.stream.UserscriptStore
import com.aiconsole.knightriderlauncher.stream.Userscripts
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream

/** One browser tab: its page and, while it exists, its WebView. */
class BrowserTab(val id: Long, initialUrl: String?, initialTitle: String?) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)

    /** 0..100 while loading, 100 when done. */
    var progress by mutableIntStateOf(100)

    /** The page is making sound (see [WebMedia]). */
    var playing by mutableStateOf(false)
        internal set
    internal val playingFrames = mutableSetOf<String>()

    /** The new-tab page (site picker) instead of a website. */
    val isPicker: Boolean get() = url == null
    var web: WebView? = null
    var desktop: Boolean? = null

    /** On screen right now (its WebView is attached in a page, not parked or hidden). */
    var attached = false
        internal set

    /** When the tab was last shown, for discarding the least recently used hidden tabs. */
    var lastUsed = 0L
        internal set

    /**
     * The page's renderer crashed, ran out of memory or hung and was shut down. The tab shows a
     * "page stopped" card instead of reloading by itself, so a page that keeps crashing can't
     * loop; Reload clears it.
     */
    var gone by mutableStateOf(false)
        internal set

    /** Last reload for a desktop/mobile switch, so a redirect chain can't ping-pong reloads. */
    internal var lastAgentReload = 0L
    internal val handlers = mutableListOf<ScriptHandler>()

    val host: String? get() = url?.let { runCatching { it.toUri().host }.getOrNull() }
}

/**
 * A tabbed, sandboxed web browser used by the stream window, the AI page and the Games page.
 * Each [space] gets its own WebView profile (cookies, storage, sign-ins) where the WebView
 * supports it, like a Hermit / Native Alpha web app. Sites get per-host options ([SiteConfig]):
 * desktop or mobile layout, dark pages, ad/tracker blocking and text size.
 */
class BrowserSession(
    private val space: String,
    private val settings: KittSettings,
    private val userscripts: UserscriptStore,
    /** Games: turn controller buttons and sticks into the keyboard keys web emulators use. */
    private val gamepad: Boolean = false,
    /** Lite mode (see DeviceProfile): lighter WebViews and fewer kept alive. */
    private val lite: () -> Boolean = { false }
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var currentIndex by mutableIntStateOf(0)

    /** For the stream window: whether it's covering the map. */
    var visible by mutableStateOf(false)

    /** Show two tabs side by side (the AI page's tandem mode). */
    var split by mutableStateOf(false)

    var pendingInstall by mutableStateOf<String?>(null)
    var showScripts by mutableStateOf(false)
    var fullscreenView by mutableStateOf<View?>(null)
        private set
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    /** A site asked for a file (e.g. a ROM); the UI shows the system picker and calls [deliverFiles]. */
    var fileRequest by mutableStateOf<Intent?>(null)
        private set
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private var nextId = 1L
    private val profile = "kitt-$space"

    val current: BrowserTab? get() = tabs.getOrNull(currentIndex)

    init {
        WebMedia.register(this)
    }

    fun newTab(url: String? = null, title: String? = null): BrowserTab {
        val tab = BrowserTab(nextId++, url, title)
        tabs += tab
        currentIndex = tabs.lastIndex
        return tab
    }

    /** Opens [url] in the current tab (a new one if there's none). */
    fun open(url: String, title: String = StreamUrls.titleOf(url)) {
        val tab = current ?: newTab()
        tab.url = url
        tab.title = title
        tab.web?.let { web ->
            applySiteOptions(tab, web)
            web.loadUrl(url)
        }
        visible = true
        showScripts = false
    }

    fun select(index: Int) {
        if (index in tabs.indices) currentIndex = index
    }

    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        destroy(tab)
        tabs.removeAt(index)
        currentIndex = currentIndex.coerceAtMost(tabs.lastIndex).coerceAtLeast(0)
    }

    /** Back to the site picker in the current tab. */
    fun showPicker() {
        current?.let { tab ->
            destroy(tab)
            tab.url = null
            tab.title = null
        }
    }

    fun canGoBack(): Boolean = current?.web?.canGoBack() == true
    fun goBack() = current?.web?.goBack()
    fun reload() = current?.web?.reload()

    fun run(script: String, onResult: (String) -> Unit = {}) {
        current?.web?.evaluateJavascript(script) { onResult(it.orEmpty().trim('"').replace("\\n", "\n")) }
    }

    fun runIn(tab: BrowserTab, script: String, onResult: (String) -> Unit = {}) {
        tab.web?.evaluateJavascript(script) { onResult(it.orEmpty().trim('"').replace("\\n", "\n")) }
    }

    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        fullscreenView = null
    }

    fun deliverFiles(uris: Array<Uri>?) {
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
        fileRequest = null
    }

    /** Closes every tab and frees the WebViews. */
    fun close() {
        exitFullscreen()
        deliverFiles(null)
        tabs.forEach(::destroy)
        tabs.clear()
        currentIndex = 0
        visible = false
        showScripts = false
        pendingInstall = null
    }

    // ---- Site options ------------------------------------------------------------------

    /** Every setting the site options read, for UI that must redraw when one changes. */
    val siteOptionFlows: List<StateFlow<*>>
        get() = listOf(settings.siteSettings.state, settings.streamDesktop.state, settings.darkPages.state, settings.blockAds.state)

    fun siteConfig(tab: BrowserTab?): SiteConfig? = tab?.host?.let { sites()[SiteSettings.keyFor(it)] }

    // Ad blocking asks for the site's options on every request the page makes (dozens a second
    // on a busy web app): decode the stored text once per change, not per request.
    @Volatile private var sitesCache: Pair<String, Map<String, SiteConfig>> = "" to emptyMap()

    private fun sites(): Map<String, SiteConfig> {
        val raw = settings.siteSettings.value
        val cached = sitesCache
        if (cached.first == raw) return cached.second
        return SiteSettings.decode(raw).also { sitesCache = raw to it }
    }

    /** Clears a crashed tab's "page stopped" card; showing it again creates a fresh page. */
    fun reloadGone(tab: BrowserTab) {
        destroy(tab)
        tab.gone = false
    }

    fun isDesktop(tab: BrowserTab): Boolean =
        !SiteSettings.forcesMobile(tab.host) && (siteConfig(tab)?.desktop ?: settings.streamDesktop.value)
    fun isDark(tab: BrowserTab): Boolean = siteConfig(tab)?.dark ?: settings.darkPages.value
    fun blocksAds(tab: BrowserTab): Boolean =
        SiteSettings.blockAllowed(tab.host) && (siteConfig(tab)?.blockAds ?: settings.blockAds.value)
    fun textZoom(tab: BrowserTab): Int = siteConfig(tab)?.textZoom ?: 100

    /** Changes this site's options, remembers them, and re-applies (reloading if the layout changed). */
    fun updateSite(change: (SiteConfig) -> SiteConfig) {
        val tab = current ?: return
        val host = tab.host ?: return
        val adsBefore = blocksAds(tab)
        settings.siteSettings.set(SiteSettings.encode(SiteSettings.update(SiteSettings.decode(settings.siteSettings.value), host, change)))
        // A new layout needs a reload; so do ads, which are already on the page.
        tab.web?.let { if (applySiteOptions(tab, it) || blocksAds(tab) != adsBefore) it.reload() }
    }

    /** Returns true when the user agent changed (the page must reload to use it). */
    private fun applySiteOptions(tab: BrowserTab, web: WebView): Boolean {
        val desktop = isDesktop(tab)
        val changed = tab.desktop != null && tab.desktop != desktop
        tab.desktop = desktop
        val s = web.settings
        s.userAgentString = if (desktop) StreamUrls.desktopUserAgent(WebSettings.getDefaultUserAgent(web.context)) else null
        s.useWideViewPort = desktop
        s.loadWithOverviewMode = desktop
        s.textZoom = textZoom(tab)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, isDark(tab))
        }
        return changed
    }

    // ---- WebViews ----------------------------------------------------------------------

    /** The tab's WebView, created on first use. */
    fun webViewFor(context: Context, tab: BrowserTab): WebView = tab.web ?: createWebView(context, tab).also { web ->
        tab.web = web
        applyScripts(tab, userscripts.scripts.value)
        tab.url?.let(web::loadUrl)
    }

    fun applyScripts(scripts: List<InstalledScript>) = tabs.forEach { applyScripts(it, scripts) }

    private fun applyScripts(tab: BrowserTab, scripts: List<InstalledScript>) {
        val web = tab.web ?: return
        tab.handlers.forEach { it.remove() }
        tab.handlers.clear()
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        tab.handlers += WebViewCompat.addDocumentStartJavaScript(web, PageScripts.MEDIA_WATCH, setOf("*"))
        scripts.filter { it.enabled }.forEach { installed ->
            try {
                tab.handlers += WebViewCompat.addDocumentStartJavaScript(web, Userscripts.wrap(installed.script), setOf("*"))
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Couldn't register ${installed.script.name}", e)
            }
        }
    }

    /**
     * The tab's page is on screen: resume it, then free the least recently used hidden pages
     * beyond the live-WebView budget (they reload when shown again).
     */
    fun onShown(tab: BrowserTab, web: WebView) {
        tab.attached = true
        tab.lastUsed = SystemClock.elapsedRealtime()
        web.onResume()
        WebMedia.enforceBudget(DeviceProfile.liveWebViews(lite()))
        FreezeWatchdog.context = "Last page shown: $space ${tab.host ?: "new tab"}."
    }

    /** The tab's page left the screen. A silent page is paused (animations, timers for rendering); a playing one keeps going. */
    fun onHidden(tab: BrowserTab, web: WebView) {
        tab.attached = false
        if (tab.web !== web) return
        WebMedia.parkIfPlaying(tab, web)
        if (!tab.playing) web.onPause()
    }

    /** Frees a hidden tab's WebView but keeps its address, so showing it again reloads the page. */
    internal fun discard(tab: BrowserTab) {
        if (tab.attached || tab.playing || tab.web == null) return
        Log.i(TAG, "Discarding hidden $space tab ${tab.id} to save memory")
        destroy(tab)
    }

    private fun destroy(tab: BrowserTab) {
        WebMedia.forget(tab)
        tab.handlers.clear()
        tab.web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        tab.web = null
        tab.desktop = null
        tab.attached = false
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createWebView(context: Context, tab: BrowserTab): WebView = WebView(context).apply {
        // Sandbox: each space keeps its own cookies and sign-ins when the WebView supports profiles.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.setProfile(this, profile) }.onFailure { Log.w(TAG, "No separate profile for $space", it) }
        }
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // Pre-rastering the page off screen scrolls smoother but costs a lot of graphics memory:
        // only on capable units. The renderer is a priority only while the page is visible, so
        // Android can reclaim hidden pages first.
        // Never for the AI and Games pages: a long chat or game page is huge to raster.
        settings.offscreenPreRaster = !lite() && space == "stream"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        setBackgroundColor(android.graphics.Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        // Keep gestures in the page: parents (the home pager, scrolling panels) mustn't take them.
        setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                if (!v.hasFocus()) v.requestFocus()
            }
            false
        }
        // A hung page must not freeze the launcher: its renderer is terminated (see RendererWatch).
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) {
            runCatching { WebViewCompat.setWebViewRenderProcessClient(this, RendererWatch.executor, RendererWatch.client) }
        }
        applySiteOptions(tab, this)
        webViewClient = TabClient(tab)
        webChromeClient = TabChrome(tab)
        if (gamepad) installGamepad(this)
        addJavascriptInterface(WebMedia.bridgeFor(this, tab), "KittMedia")
        setDownloadListener { url, _, _, mimetype, _ ->
            if (StreamUrls.isUserscript(url) || mimetype == "text/x-userscript") pendingInstall = url
        }
    }

    /**
     * Controller input arrives at the focused page as key events the page doesn't understand;
     * remap them to the keyboard keys emulators listen for (unless the driver chose native
     * gamepad mode for sites that use the Gamepad API).
     */
    private fun installGamepad(web: WebView) {
        val held = mutableSetOf<Int>()
        fun fromController(source: Int) =
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
                source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        fun send(action: Int, keyCode: Int) {
            val now = SystemClock.uptimeMillis()
            web.dispatchKeyEvent(KeyEvent(now, now, action, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }
        web.setOnKeyListener { _, keyCode, event ->
            if (!settings.gamepadKeyboard.value || !fromController(event.source)) return@setOnKeyListener false
            val mapped = Gamepad.map(keyCode) ?: return@setOnKeyListener false
            if (mapped == keyCode) return@setOnKeyListener false // D-pad already arrives as arrows.
            send(event.action, mapped)
            true
        }
        web.setOnGenericMotionListener { _, event ->
            if (!settings.gamepadKeyboard.value || event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) {
                return@setOnGenericMotionListener false
            }
            val x = event.getAxisValue(MotionEvent.AXIS_X).takeIf { kotlin.math.abs(it) > 0.1f } ?: event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y).takeIf { kotlin.math.abs(it) > 0.1f } ?: event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val now = Gamepad.stickDirections(x, y)
            (held - now).forEach { send(KeyEvent.ACTION_UP, it) }
            (now - held).forEach { send(KeyEvent.ACTION_DOWN, it) }
            held.clear()
            held.addAll(now)
            true
        }
    }

    // onRenderProcessGone IS implemented below; androidx.webkit's check misfires on Kotlin subclasses.
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class TabClient(private val tab: BrowserTab) : WebViewClient() {
        // Web links stay in the tab. Other schemes (intent://, vnd.youtube:, market://) are how a
        // site hands off to its full-screen app, so they're dropped. Userscripts get a prompt.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            if (StreamUrls.isUserscript(target)) {
                pendingInstall = target
                return true
            }
            val scheme = request.url.scheme
            return scheme != "http" && scheme != "https"
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (url != null && url.startsWith("http")) {
                tab.url = url
                // A sign-in redirect between a desktop site and a mobile-only one would otherwise
                // reload back and forth forever; at most one layout reload every few seconds.
                val now = SystemClock.elapsedRealtime()
                if (applySiteOptions(tab, view) && now - tab.lastAgentReload > AGENT_RELOAD_GAP_MS) {
                    tab.lastAgentReload = now
                    view.reload()
                }
            }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            tab.title = view.title?.takeIf { it.isNotBlank() } ?: tab.title
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
            view.evaluateJavascript(PageScripts.MEDIA_WATCH, null)
            userscripts.scripts.value.filter { it.enabled }.forEach { view.evaluateJavascript(Userscripts.wrap(it.script), null) }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!blocksAds(tab) || !AdBlock.isBlocked(request.url.host)) return null
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        // A crashed or killed renderer must not take the launcher down: drop this tab's WebView;
        // it's recreated (and the page reloaded) the next time the tab is shown.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Log.w(TAG, "Renderer gone in $space tab ${tab.id} (${tab.host}), crashed=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail.didCrash() else "?"}")
            if (tab.web === view) {
                WebMedia.forget(tab)
                tab.web = null
                tab.handlers.clear()
                tab.attached = false
                tab.gone = true
            }
            // Every WebView on a dead renderer must go, or the app is killed with it.
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            return true
        }
    }

    private inner class TabChrome(private val tab: BrowserTab) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) tab.title = title
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullscreenCallback?.onCustomViewHidden()
            fullscreenView = view
            fullscreenCallback = callback
        }

        override fun onHideCustomView() {
            fullscreenView = null
            fullscreenCallback = null
        }

        /** Pop-ups the driver triggered open as a new tab; unrequested ones are blocked. */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) return false
            val newTab = newTab(null, "New tab")
            newTab.url = "about:blank"
            val web = createWebView(view.context, newTab)
            newTab.web = web
            applyScripts(newTab, userscripts.scripts.value)
            (resultMsg.obj as? WebView.WebViewTransport)?.webView = web
            resultMsg.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView) {
            tabs.indexOfFirst { it.web === window }.takeIf { it >= 0 }?.let(::closeTab)
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            fileRequest = runCatching { params.createIntent() }.getOrNull() ?: Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
            return true
        }

        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            // Page errors go to the app log so "Report a problem" captures them, but rate-limited:
            // some web apps spew hundreds a second, and each one lands on the main thread.
            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                val now = SystemClock.elapsedRealtime()
                if (now - consoleWindowStart > CONSOLE_WINDOW_MS) {
                    consoleWindowStart = now
                    consoleLogged = 0
                }
                if (consoleLogged++ < CONSOLE_MAX_PER_WINDOW) {
                    Log.w(TAG, "[$space] ${message.message().take(300)} (${message.sourceId()}:${message.lineNumber()})")
                }
            }
            return true
        }
    }

    private var consoleWindowStart = 0L
    private var consoleLogged = 0

    private companion object {
        const val TAG = "Browser"
        const val AGENT_RELOAD_GAP_MS = 5_000L
        const val CONSOLE_WINDOW_MS = 10_000L
        const val CONSOLE_MAX_PER_WINDOW = 20
    }
}
