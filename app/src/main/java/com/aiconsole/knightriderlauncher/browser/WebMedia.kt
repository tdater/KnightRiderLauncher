package com.aiconsole.knightriderlauncher.browser

import android.content.ComponentCallbacks2
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import com.aiconsole.knightriderlauncher.data.DeviceProfile
import java.lang.ref.WeakReference

/**
 * Background sound for the browser pages (stream window, AI, Games):
 * - a page that's playing keeps playing when its home page is swiped away: its WebView is parked
 *   in a tiny always-attached container, so the site still sees a visible page and doesn't pause;
 * - only one source plays: a page that starts playing pauses any other page, and takes Android
 *   audio focus so music apps pause; when another app takes focus (play pressed in YouTube
 *   Music), the page pauses. KITT's voice only ducks it.
 * Main thread only.
 */
object WebMedia {

    private val sessions = mutableListOf<WeakReference<BrowserSession>>()
    private var audio: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private val pausedUntilGain = mutableSetOf<BrowserTab>()

    /** Set by the activity: an attached 1×1 container behind the launcher UI. Weak, so the activity isn't leaked. */
    private var parkingRef: WeakReference<ViewGroup>? = null
    var parking: ViewGroup?
        get() = parkingRef?.get()
        set(value) {
            parkingRef = value?.let(::WeakReference)
        }

    /**
     * A page is playing, or is paused only until audio focus comes back. "Hey KITT" must stay
     * off then: each recognizer session takes focus briefly, which paused the page, and the
     * page sounding silent let the next session start, so music stopped and started in a loop.
     */
    val busy: Boolean get() = pausedUntilGain.isNotEmpty() || playingTabs().isNotEmpty()

    /**
     * Web pages on screen right now (kept by [BrowserTab.attached]). Every launcher frame makes
     * an on-screen WebView composite again, so the launcher's own animations slow down meanwhile.
     */
    var pagesOnScreen by mutableIntStateOf(0)
        private set

    internal fun pageShown(shown: Boolean) {
        pagesOnScreen = (pagesOnScreen + if (shown) 1 else -1).coerceAtLeast(0)
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        // In problem reports: shows which app interrupts a page's playback, and how.
        Log.i("WebMedia", "Audio focus change $change while ${playingTabs().size} page(s) play")
        when (MediaFocus.onChange(change)) {
            MediaFocus.Action.Pause -> {
                hasFocus = false
                pausedUntilGain.clear()
                playingTabs().forEach(::pause)
            }
            MediaFocus.Action.PauseUntilGain -> playingTabs().forEach { tab ->
                pausedUntilGain += tab
                pause(tab)
            }
            MediaFocus.Action.Resume -> {
                pausedUntilGain.forEach { tab -> tab.web?.evaluateJavascript(PageScripts.RESUME_MEDIA, null) }
                pausedUntilGain.clear()
            }
            MediaFocus.Action.Nothing -> Unit
        }
    }

    fun init(context: Context) {
        audio = context.applicationContext.getSystemService(AudioManager::class.java)
    }

    fun register(session: BrowserSession) {
        sessions.removeAll { it.get() == null }
        sessions += WeakReference(session)
    }

    /**
     * Keeps at most [keep] WebViews alive across every browser: hidden, silent tabs are
     * discarded oldest first (they reload when shown). Visible and playing tabs always stay.
     */
    fun enforceBudget(keep: Int) {
        sessions.removeAll { it.get() == null }
        val owners = sessions.mapNotNull { it.get() }.flatMap { session -> session.tabs.filter { it.web != null }.map { it to session } }
        DeviceProfile.discardable(owners, { it.first.lastUsed }, { it.first.attached }, { it.first.playing }, keep)
            .forEach { (tab, session) -> session.discard(tab) }
    }

    /**
     * The launcher went behind another app (or came back). Silent pages on screen pause while
     * it's hidden, so a web app left open doesn't keep the CPU busy under navigation or music.
     */
    fun setLauncherVisible(visible: Boolean) {
        sessions.mapNotNull { it.get() }.flatMap { it.tabs }.filter { it.attached && !it.playing }.forEach { tab ->
            tab.web?.let { if (visible) it.onResume() else it.onPause() }
        }
    }

    /** Android is short of memory: free every hidden, silent page; at worst they reload when shown. */
    fun trimMemory(level: Int) {
        @Suppress("DEPRECATION")
        val pressure = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (pressure) enforceBudget(0)
    }

    /** The JavaScript bridge a tab's pages report playback through. */
    fun bridgeFor(web: WebView, tab: BrowserTab): Bridge = Bridge(web, tab)

    /** A tab's frame started or stopped playing. */
    fun onFrameState(tab: BrowserTab, frame: String, playing: Boolean) {
        val was = tab.playing
        if (playing) tab.playingFrames += frame else tab.playingFrames -= frame
        tab.playing = tab.playingFrames.isNotEmpty()
        if (tab.playing == was) return
        if (tab.playing) {
            pausedUntilGain.clear()
            playingTabs().filter { it !== tab }.forEach(::pause)
            requestFocus()
        } else {
            if (tab !in pausedUntilGain) {
                unpark(tab)
                // Off screen and silent now: stop rendering it until it's shown again.
                if (!tab.attached) tab.web?.onPause()
            }
            if (playingTabs().isEmpty() && pausedUntilGain.isEmpty()) abandonFocus()
        }
    }

    /** Set by the activity: the corner mini player (see [parkIfPlaying]). Weak, like [parking]. */
    private var miniRef: WeakReference<ViewGroup>? = null
    var mini: ViewGroup?
        get() = miniRef?.get()
        set(value) {
            miniRef = value?.let(::WeakReference)
        }
    private var miniTabRef: WeakReference<BrowserTab>? = null
    private var miniTab: BrowserTab?
        get() = miniTabRef?.get()
        set(value) {
            miniTabRef = value?.let(::WeakReference)
        }

    /**
     * The tab's page is leaving the screen; keep it attached (and audible) if it's playing. With
     * [toMini] (the stream window, mini player on) it moves to the visible corner player instead
     * of the hidden 1×1 spot, so a video keeps showing.
     */
    fun parkIfPlaying(tab: BrowserTab, web: WebView, toMini: Boolean = false) {
        if (!tab.playing || tab.web !== web) return
        val miniPlayer = mini
        if (toMini && miniPlayer != null) {
            (web.parent as? ViewGroup)?.removeView(web)
            miniPlayer.addView(web, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            miniPlayer.visibility = android.view.View.VISIBLE
            miniTab = tab
            return
        }
        val container = parking ?: return
        val width = web.width.coerceAtLeast(1)
        val height = web.height.coerceAtLeast(1)
        (web.parent as? ViewGroup)?.removeView(web)
        container.addView(web, FrameLayout.LayoutParams(width, height))
    }

    /** A page went back on screen: if it was in the mini player, the corner empties. */
    fun onShown(web: WebView) {
        val miniPlayer = mini ?: return
        if (miniTab?.web === web || web.parent === miniPlayer) closeMini(pause = false)
    }

    /** The mini player's close button: pause the video and hide the corner. */
    fun closeMini(pause: Boolean = true) {
        val miniPlayer = mini ?: return
        val tab = miniTab
        miniTab = null
        tab?.web?.let { web ->
            if (pause) web.evaluateJavascript(PageScripts.PAUSE_MEDIA, null)
            if (web.parent === miniPlayer) miniPlayer.removeView(web)
        }
        miniPlayer.visibility = android.view.View.GONE
    }

    /** The tab is closing or its WebView is going away. */
    fun forget(tab: BrowserTab) {
        tab.playingFrames.clear()
        tab.playing = false
        pausedUntilGain -= tab
        if (playingTabs().isEmpty() && pausedUntilGain.isEmpty()) abandonFocus()
    }

    private fun unpark(tab: BrowserTab) {
        if (tab === miniTab) {
            closeMini(pause = false)
            return
        }
        val web = tab.web ?: return
        val container = parking ?: return
        if (web.parent === container) container.removeView(web)
    }

    private fun pause(tab: BrowserTab) {
        tab.web?.evaluateJavascript(PageScripts.PAUSE_MEDIA, null)
    }

    private fun playingTabs(): List<BrowserTab> =
        sessions.mapNotNull { it.get() }.flatMap { it.tabs }.filter { it.playing }

    private fun requestFocus() {
        val manager = audio ?: return
        if (hasFocus) return
        hasFocus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        val manager = audio ?: return
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(manager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusListener)
        }
        hasFocus = false
    }

    /** Called by page scripts on a background thread; hops to the main thread. */
    class Bridge internal constructor(private val web: WebView, private val tab: BrowserTab) {
        @JavascriptInterface
        fun state(frame: String, playing: Boolean) {
            web.post { if (tab.web === web) onFrameState(tab, frame, playing) }
        }
    }
}
