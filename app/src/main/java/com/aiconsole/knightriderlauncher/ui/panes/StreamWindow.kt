package com.aiconsole.knightriderlauncher.ui.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.stream.StreamSites
import com.aiconsole.knightriderlauncher.ui.browser.BrowserView
import com.aiconsole.knightriderlauncher.ui.browser.HomePrefs
import com.aiconsole.knightriderlauncher.ui.browser.SiteHome
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

/**
 * The streaming window drawn over the map pane: the shared browser with a tile home page of
 * free movie, TV, music and radio services (filters, favourites, "continue watching", my sites).
 * Theater gives it the whole screen; Focus video blows the video up inside the page.
 */
@Composable
fun StreamWindow(session: BrowserSession, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val prefs = remember { HomePrefs(app.settings.streamBookmarks, app.settings.streamFavorites, app.settings.streamVisits) }
    val onPage = session.current?.isPicker == false
    KittPanel(
        title = session.current?.title?.take(40) ?: "Stream",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (session.blockedCount > 0) Text("🛡 ${session.blockedCount}", style = MaterialTheme.typography.labelMedium, color = DashGrey)
                if (onPage) KittButton("Focus", { session.focusMedia() }, Modifier.height(32.dp).width(72.dp), DashAmber)
                KittButton(
                    if (session.immersive) "Exit theater" else "Theater",
                    { session.immersive = !session.immersive },
                    Modifier.height(32.dp),
                    ScannerRed,
                    active = session.immersive
                )
                if (!session.immersive) KittButton("Map", { session.visible = false }, Modifier.height(32.dp).width(64.dp), ScannerRed)
                KittButton("Close", {
                    session.immersive = false
                    session.close()
                }, Modifier.height(32.dp).width(72.dp), DashGrey)
            }
        }
    ) {
        BrowserView(
            session = session,
            bookmarks = app.settings.streamBookmarks,
            picker = {
                SiteHome(
                    session = it,
                    sites = StreamSites.HOME,
                    prefs = prefs,
                    continueTitle = "Continue watching",
                    searchHint = "Search services, or type any address",
                    help = "Free, legal services: ad-supported, free tiers, or free with a library card. Save any other site with " +
                        "★ SAVE (My sites), or import bookmarks from another browser. Sign-ins stay in this window's own sandbox. " +
                        "THEATER gives the window the whole screen; FOCUS blows the video up inside the page. Ads and pop-ups " +
                        "are blocked (a held pop-up can be opened from the bar above the page). Page won't scroll? Use ▲ ▼ or " +
                        "switch Mobile/Desktop under MORE."
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
