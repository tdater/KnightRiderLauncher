package com.aiconsole.knightriderlauncher.ui.browser

import android.app.Activity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.browser.BrowserDownloads
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.browser.BrowserTab
import com.aiconsole.knightriderlauncher.browser.PageScripts
import com.aiconsole.knightriderlauncher.browser.SiteSettings
import com.aiconsole.knightriderlauncher.browser.WebViewVersion
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.stream.StreamSite
import com.aiconsole.knightriderlauncher.stream.StreamSites
import com.aiconsole.knightriderlauncher.stream.StreamUrls
import com.aiconsole.knightriderlauncher.stream.Userscript
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittTextField
import com.aiconsole.knightriderlauncher.ui.components.SectionHeader
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import java.io.IOException
import kotlinx.coroutines.launch

private const val GREASY_FORK = "https://greasyfork.org/"

/**
 * False while the home page holding this browser isn't the page the pager settled on (it's
 * being pre-composed or swiped past). The WebView is only attached on the settled page; the tab
 * and its WebView stay alive, so nothing reloads when the driver comes back.
 */
val LocalWebViewHostActive = compositionLocalOf { true }

/**
 * The shared browser: tabs, toolbar, the page (or two side by side in split mode), and the
 * script manager / install prompt. [picker] draws the new-tab page for this browser's purpose.
 * [extraTools] adds page-specific buttons to the second toolbar row.
 */
@Composable
fun BrowserView(
    session: BrowserSession,
    bookmarks: KittSettings.Pref<String>,
    picker: @Composable (BrowserSession) -> Unit,
    modifier: Modifier = Modifier,
    extraTools: @Composable () -> Unit = {},
    /** Game mode / theater: no tab strip or toolbar, the page gets the space. */
    compact: Boolean = false,
    /** Drawn over a web page (not the home page), e.g. the Games page's touch pad. */
    pageOverlay: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val scripts by app.userscripts.scripts.collectAsState()
    LaunchedEffect(scripts) { session.applyScripts(scripts) }
    LaunchedEffect(session.tabs.isEmpty()) { if (session.tabs.isEmpty()) session.newTab() }

    // Files a page asked for (e.g. a ROM for an emulator).
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        session.deliverFiles(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data).takeIf { result.resultCode == Activity.RESULT_OK })
    }
    val request = session.fileRequest
    LaunchedEffect(request) {
        if (request != null) runCatching { chooser.launch(request) }.onFailure { session.deliverFiles(null) }
    }

    val webViewVersion = remember { runCatching { WebViewCompat.getCurrentWebViewPackage(context)?.versionName }.getOrNull() }
    val fullscreen = session.fullscreenView
    val current = session.current
    val readyApk by BrowserDownloads.readyApk.collectAsState()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Held pop-ups and downloads, so nothing a site does goes unnoticed.
        session.blockedPopup?.let { url ->
            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HelpText("Pop-up held back: ${StreamUrls.titleOf(url)}", Modifier.weight(1f), color = DashAmber)
                KittButton("Open", { session.openBlockedPopup() }, Modifier.height(32.dp).width(72.dp), DashAmber)
                KittButton("✕", { session.blockedPopup = null }, Modifier.height(32.dp).width(40.dp), DashGrey)
            }
        }
        // Nothing is saved, and no installer opens, until the driver taps.
        session.pendingDownload?.let { d ->
            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HelpText("Save file? ${d.name} (${StreamUrls.titleOf(d.url)})", Modifier.weight(1f), color = DashAmber)
                KittButton("Save", { session.saveDownload(context) }, Modifier.height(32.dp).width(72.dp), DashAmber)
                KittButton("✕", { session.pendingDownload = null }, Modifier.height(32.dp).width(40.dp), DashGrey)
            }
        }
        readyApk?.let { apk ->
            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HelpText("${apk.name} downloaded. Install it? (Not while driving.)", Modifier.weight(1f), color = LedGreen)
                KittButton("Install", { BrowserDownloads.install(context, apk) }, Modifier.height(32.dp).width(88.dp), LedGreen)
                KittButton("✕", { BrowserDownloads.dismissApk() }, Modifier.height(32.dp).width(40.dp), DashGrey)
            }
        }
        session.downloadMessage?.let { m ->
            Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HelpText(m, Modifier.weight(1f), color = LedGreen)
                KittButton("✕", { session.downloadMessage = null }, Modifier.height(28.dp).width(40.dp), DashGrey)
            }
        }
        if (fullscreen == null && !compact) {
            TabStrip(session)
            if (current != null && !current.isPicker && session.pendingInstall == null && !session.showScripts) {
                Toolbar(session, current, bookmarks, extraTools)
            }
            if (WebViewVersion.isOutdated(webViewVersion)) {
                HelpText(
                    "This head unit's Android System WebView is version $webViewVersion. Web apps like YouTube Music run " +
                        "slowly on it. Updating \"Android System WebView\" (Play Store or APK) makes the browser much faster.",
                    color = DashAmber
                )
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                session.pendingInstall != null -> InstallPrompt(app, session, session.pendingInstall!!)
                session.showScripts -> ScriptManager(app, session)
                current == null -> Unit
                current.isPicker -> picker(session)
                session.split && session.tabs.size > 1 -> SplitPages(session)
                else -> Page(session, current, Modifier.fillMaxSize())
            }
            if (current != null && !current.isPicker && !current.gone && session.pendingInstall == null && !session.showScripts) pageOverlay()
            if (fullscreen != null && LocalWebViewHostActive.current) {
                AndroidView(
                    factory = { fullscreen.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier.fillMaxSize().background(KittBlack)
                )
                KittButton("Exit full", { session.exitFullscreen() }, Modifier.align(Alignment.TopEnd).padding(6.dp).height(32.dp), ScannerRed)
            }
        }
    }
}

@Composable
private fun TabStrip(session: BrowserSession) {
    Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(session.tabs, key = { _, t -> t.id }) { index, tab ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    KittButton(
                        label = (if (tab.playing) "♪ " else "") + (tab.title?.take(18) ?: tab.host ?: "New tab"),
                        onClick = { session.select(index) },
                        modifier = Modifier.widthIn(min = 80.dp, max = 180.dp).fillMaxHeight(),
                        color = ScannerRed,
                        active = index == session.currentIndex
                    )
                    KittButton("✕", { session.closeTab(index) }, Modifier.width(32.dp).fillMaxHeight(), DashGrey)
                }
            }
        }
        KittButton("+ Tab", { session.newTab() }, Modifier.width(72.dp).fillMaxHeight(), LedGreen, enabled = session.tabs.size < MAX_TABS)
    }
}

private const val MAX_TABS = 8

@Composable
private fun Toolbar(session: BrowserSession, tab: BrowserTab, bookmarks: KittSettings.Pref<String>, extraTools: @Composable () -> Unit) {
    var more by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KittButton("Back", { session.goBack() }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            KittButton("Reload", { session.reload() }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            KittButton("▲", { session.run(PageScripts.scrollBy(-0.8)) }, Modifier.weight(0.6f).fillMaxHeight(), DashAmber)
            KittButton("▼", { session.run(PageScripts.scrollBy(0.8)) }, Modifier.weight(0.6f).fillMaxHeight(), DashAmber)
            KittButton(
                "✕ Popups",
                { session.run(PageScripts.CLOSE_POPUPS) { n -> message = if (n == "0") "No pop-ups found." else "Closed $n pop-up(s)." } },
                Modifier.weight(1.2f).fillMaxHeight(),
                ScannerRed
            )
            KittButton(
                "★ Save",
                {
                    val url = tab.url ?: return@KittButton
                    val label = tab.title?.take(40)?.takeIf { it.isNotBlank() } ?: StreamUrls.titleOf(url)
                    bookmarks.set(StreamSites.encode(StreamSites.decode(bookmarks.value) + StreamSite(label, url)))
                    message = "Saved \"$label\" to My sites (tap SITES to see it)."
                },
                Modifier.weight(1f).fillMaxHeight(),
                LedGreen
            )
            KittButton("Sites", { session.showPicker() }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            KittButton(if (more) "Less" else "More", { more = !more }, Modifier.weight(1f).fillMaxHeight(), LedGreen, active = more)
        }
        if (more) {
            // Site options live in settings, not Compose state: watch them so the buttons show
            // (and toggle from) the current values.
            session.siteOptionFlows.forEach { it.collectAsState().value }
            val desktop = session.isDesktop(tab)
            val dark = session.isDark(tab)
            val ads = session.blocksAds(tab)
            val zoom = session.textZoom(tab)
            val saver = session.dataSaver(tab)
            LazyRow(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Google sign-in always loads as mobile (see SiteSettings.forcesMobile).
                item { KittButton(if (desktop) "Desktop" else "Mobile", { session.updateSite { it.copy(desktop = !desktop) } }, Modifier.fillMaxHeight(), LedGreen, active = desktop, enabled = !SiteSettings.forcesMobile(tab.host)) }
                item { KittButton("Dark", { session.updateSite { it.copy(dark = !dark) } }, Modifier.fillMaxHeight(), LedGreen, active = dark) }
                item { KittButton("Block ads", { session.updateSite { it.copy(blockAds = !ads) } }, Modifier.fillMaxHeight(), LedGreen, active = ads) }
                item { KittButton("Data saver", { session.updateSite { it.copy(dataSaver = !saver) } }, Modifier.fillMaxHeight(), LedGreen, active = saver) }
                item { KittButton("A−", { session.updateSite { it.copy(textZoom = (zoom - 10).coerceAtLeast(60)) } }, Modifier.fillMaxHeight(), DashAmber) }
                item { KittButton("A+ ($zoom%)", { session.updateSite { it.copy(textZoom = (zoom + 10).coerceAtMost(200)) } }, Modifier.fillMaxHeight(), DashAmber) }
                item { KittButton("Scripts", { session.showScripts = true }, Modifier.fillMaxHeight(), DashAmber) }
                item {
                    KittButton("Install script", {
                        session.run(PageScripts.FIND_USERSCRIPT) { url ->
                            if (url.isNotBlank()) session.pendingInstall = url else message = "No userscript install link on this page."
                        }
                    }, Modifier.fillMaxHeight(), DashAmber)
                }
                item { KittButton(if (session.split) "Single" else "Split", { session.split = !session.split }, Modifier.fillMaxHeight(), DashAmber, active = session.split, enabled = session.tabs.size > 1) }
                item { extraTools() }
            }
        }
        message?.let { HelpText(it) }
    }
}

/** Current tab and the next one side by side (or stacked on a tall screen). */
@Composable
private fun SplitPages(session: BrowserSession) {
    val first = session.current ?: return
    val second = session.tabs.getOrNull((session.currentIndex + 1) % session.tabs.size) ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Page(session, first, Modifier.weight(1f).fillMaxHeight())
                Page(session, second, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Page(session, first, Modifier.weight(1f).fillMaxWidth())
                Page(session, second, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Page(session: BrowserSession, tab: BrowserTab, modifier: Modifier) {
    if (tab.isPicker) return
    val hostActive = LocalWebViewHostActive.current
    Box(modifier.clip(MaterialTheme.shapes.small).border(1.dp, KittBezel, MaterialTheme.shapes.small).background(KittBlack)) {
        if (tab.gone) {
            PageStopped(session, tab)
            return@Box
        }
        if (hostActive) key(tab.id) {
            AndroidView(
                factory = { context ->
                    session.webViewFor(context, tab).also {
                        // It may still sit in the background-audio parking spot (see WebMedia).
                        (it.parent as? ViewGroup)?.removeView(it)
                        it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        session.onShown(tab, it)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                // Leaving the screen (page swiped away, other tab shown): a playing page keeps
                // playing; a silent one pauses until it's shown again.
                onRelease = { web -> session.onHidden(tab, web) }
            )
        }
        if (tab.progress < 100) {
            // Thin LED loading bar so a slow page never looks frozen.
            Canvas(Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopStart)) {
                drawRect(ScannerRed, size = Size(size.width * tab.progress / 100f, size.height))
            }
        }
    }
}

/** The page's renderer died (crash, out of memory, or shut down because it hung the launcher). */
@Composable
private fun PageStopped(session: BrowserSession, tab: BrowserTab) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PAGE STOPPED", style = MaterialTheme.typography.titleMedium, color = ScannerRed)
        Text(
            "${tab.title ?: tab.host ?: "This page"} stopped responding or ran out of memory, so it was shut down " +
                "to keep the launcher running. Long AI chats are the usual cause: start a new chat if it happens again.",
            style = MaterialTheme.typography.bodyMedium,
            color = DashWhite
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Reload", { session.reloadGone(tab) }, Modifier.height(48.dp).width(120.dp), LedGreen, active = true)
            KittButton("Close tab", { session.closeTab(session.tabs.indexOf(tab)) }, Modifier.height(48.dp).width(120.dp), DashGrey)
        }
    }
}

@Composable
private fun ScriptManager(app: KittApplication, session: BrowserSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scripts by app.userscripts.scripts.collectAsState()
    var address by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("Userscripts", Modifier.weight(1f), color = DashAmber)
            KittButton("Done", { session.showScripts = false }, Modifier.height(36.dp).width(80.dp), LedGreen, active = true)
        }
        HelpText(
            "Tampermonkey-style scripts run on matching pages (Chrome extensions can't run in an embedded browser). " +
                "On a script's page tap More → Install script, paste its code from the clipboard, or enter a .user.js address. " +
                "Only install scripts you trust: they can read pages you're signed in to."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KittTextField(address, { address = it }, "https://…/script.user.js", Modifier.weight(1f))
            KittButton("Install", { StreamUrls.normalize(address)?.let { session.pendingInstall = it } }, Modifier.height(48.dp).width(88.dp), LedGreen, enabled = StreamUrls.normalize(address) != null)
        }
        Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Paste code", {
                val clip = (context.getSystemService(android.content.ClipboardManager::class.java))?.primaryClip
                val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                val parsed = com.aiconsole.knightriderlauncher.stream.Userscripts.parse(text)
                if (parsed == null) {
                    message = "The clipboard doesn't hold a userscript (needs a ==UserScript== header with @name)."
                } else {
                    scope.launch {
                        app.userscripts.install(parsed)
                        message = "Installed ${parsed.name}. Reload the page to run it."
                    }
                }
            }, Modifier.weight(1f).fillMaxHeight(), LedGreen)
            KittButton("Greasy Fork", {
                session.showScripts = false
                session.open(GREASY_FORK, "Greasy Fork")
            }, Modifier.weight(1f).fillMaxHeight(), DashAmber)
        }
        message?.let { HelpText(it, color = DashAmber) }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (scripts.isEmpty()) item { HelpText("No scripts installed.") }
            items(scripts, key = { it.script.id }) { installed ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(KittBlack)
                        .border(1.dp, if (installed.enabled) LedGreen.copy(alpha = 0.6f) else KittBezel, MaterialTheme.shapes.small).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${installed.script.name} ${installed.script.version}".trim(), style = MaterialTheme.typography.labelLarge, color = DashWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(scopeText(installed.script), style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    KittButton(if (installed.enabled) "On" else "Off", { scope.launch { app.userscripts.setEnabled(installed.script.id, !installed.enabled) } }, Modifier.height(40.dp).width(64.dp), LedGreen, active = installed.enabled)
                    KittButton("Delete", { scope.launch { app.userscripts.remove(installed.script.id) } }, Modifier.height(40.dp).width(80.dp), AlertRed)
                }
            }
        }
    }
}

/** Download, show what the script is and where it runs, then install on confirmation. */
@Composable
private fun InstallPrompt(app: KittApplication, session: BrowserSession, url: String) {
    val scope = rememberCoroutineScope()
    var script by remember(url) { mutableStateOf<Userscript?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        try {
            script = app.userscripts.fetch(url)
        } catch (e: IOException) {
            error = e.message ?: "Download failed"
        } catch (e: IllegalArgumentException) {
            error = "Invalid address"
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Install userscript?", color = DashAmber)
        val loaded = script
        when {
            error != null -> Text("Couldn't load the script: $error", style = MaterialTheme.typography.bodyMedium, color = AlertRed)
            loaded == null -> HelpText("Downloading…")
            else -> {
                Text("${loaded.name} ${loaded.version}".trim(), style = MaterialTheme.typography.titleMedium, color = DashWhite)
                if (loaded.description.isNotEmpty()) HelpText(loaded.description)
                Text("Runs on: ${scopeText(loaded)}", style = MaterialTheme.typography.bodyMedium, color = DashAmber)
                HelpText("It can read and change those pages, including while you're signed in. Install only if you trust it.")
            }
        }
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Cancel", { session.pendingInstall = null }, Modifier.weight(1f).fillMaxHeight(), DashGrey)
            KittButton("Install", {
                loaded?.let {
                    scope.launch {
                        app.userscripts.install(it)
                        session.pendingInstall = null
                        session.reload()
                    }
                }
            }, Modifier.weight(1f).fillMaxHeight(), LedGreen, active = true, enabled = loaded != null)
        }
    }
}

private fun scopeText(script: Userscript): String =
    (script.matches + script.includes).ifEmpty { listOf("all sites") }.joinToString(", ")
