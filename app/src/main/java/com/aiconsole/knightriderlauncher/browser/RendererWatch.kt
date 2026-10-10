package com.aiconsole.knightriderlauncher.browser

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Stops a hung web page from taking the whole launcher down.
 *
 * Android's WebView draws in lock-step with the page's renderer, so a page stuck in heavy
 * script (a long Claude conversation, a runaway web app) can freeze the launcher's UI thread
 * until Android declares it not responding and falls back to the stock launcher. WebView
 * reports an unresponsive renderer on a background thread; this kills that renderer. Every page
 * it hosted then gets onRenderProcessGone, shows a "page stopped" card, and reloads on request.
 */
object RendererWatch {

    /** Callbacks run here, never on the (possibly blocked) main thread. */
    val executor: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "kitt-renderer-watch").apply { isDaemon = true } }

    private const val TAG = "RendererWatch"

    val client = object : WebViewRenderProcessClient() {
        override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
            // Don't touch the view here: we're off the main thread.
            if (renderer != null && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) {
                Log.w(TAG, "Web page renderer unresponsive: terminating it so the launcher stays responsive")
                runCatching { renderer.terminate() }.onFailure { Log.w(TAG, "Couldn't terminate the renderer", it) }
            } else {
                Log.w(TAG, "Web page renderer unresponsive (this WebView can't terminate it)")
            }
        }

        override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) {
            Log.i(TAG, "Web page renderer responsive again")
        }
    }
}
