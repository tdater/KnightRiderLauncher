package com.aiconsole.knightriderlauncher.ui.panes

import android.view.InputDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.browser.BrowserSession
import com.aiconsole.knightriderlauncher.browser.GameSites
import com.aiconsole.knightriderlauncher.ui.browser.BrowserView
import com.aiconsole.knightriderlauncher.ui.browser.HomePrefs
import com.aiconsole.knightriderlauncher.ui.browser.SiteHome
import com.aiconsole.knightriderlauncher.ui.browser.TouchPad
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import kotlinx.coroutines.delay

/**
 * Games page: RetroTV's retro-game browser, made for a head unit. The home page is a tile grid
 * of in-browser game sites (filters, favourites, "continue playing", my sites, emulator apps);
 * games play with a Bluetooth/USB controller or the on-screen touch pad, which reach the page as
 * a standard gamepad and/or keyboard keys (Auto picks per page). Game mode hides the launcher's
 * rail, dock and status strip; Focus game blows the game up to fill the page.
 */
@Composable
fun GamesPane(session: BrowserSession, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val touchPad by app.settings.touchPad.state.collectAsState()
    var focusNote by remember { mutableStateOf<String?>(null) }
    val controllers by produceState(emptyList<String>()) {
        while (true) {
            value = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
                .filter { d -> d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }
                .map { it.name }
                .distinct()
            delay(3_000)
        }
    }
    val onGame = session.current?.isPicker == false
    val prefs = remember { HomePrefs(app.settings.gameBookmarks, app.settings.gameFavorites, app.settings.gameVisits) }

    KittPanel(
        title = if (session.immersive) (session.current?.title?.take(30) ?: "Game") else "Games",
        modifier = modifier,
        accent = ScannerRed,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (session.blockedCount > 0) Text("🛡 ${session.blockedCount}", style = MaterialTheme.typography.labelMedium, color = DashGrey)
                Text(
                    if (controllers.isEmpty()) "NO CONTROLLER" else "🎮 ${controllers.first().uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (controllers.isEmpty()) DashGrey else LedGreen,
                    maxLines = 1
                )
            }
        }
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onGame) {
                LazyRow(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item {
                        KittButton(
                            if (session.immersive) "Exit game mode" else "Game mode",
                            { session.immersive = !session.immersive },
                            Modifier.height(40.dp),
                            ScannerRed,
                            active = session.immersive
                        )
                    }
                    item {
                        KittButton("Focus game", {
                            session.focusMedia { r -> focusNote = if (r == "none") "No game found on this page yet: start one first." else null }
                        }, Modifier.height(40.dp), DashAmber)
                    }
                    item { KittButton("Touch pad", { app.settings.touchPad.set(!touchPad) }, Modifier.height(40.dp), LedGreen, active = touchPad) }
                    item {
                        KittButton(
                            "Input: ${session.controllerMode.label}",
                            { session.changeControllerMode(session.controllerMode.next()) },
                            Modifier.height(40.dp),
                            LedGreen
                        )
                    }
                    if (session.immersive) {
                        item { KittButton("Back", { session.goBack() }, Modifier.height(40.dp).width(72.dp), DashAmber) }
                        item { KittButton("Games", { session.immersive = false; session.showPicker() }, Modifier.height(40.dp).width(88.dp), DashAmber) }
                    }
                }
                focusNote?.let { HelpText(it, color = DashAmber) }
            }
            BrowserView(
                session = session,
                bookmarks = app.settings.gameBookmarks,
                picker = {
                    SiteHome(
                        session = it,
                        sites = GameSites.ALL,
                        prefs = prefs,
                        continueTitle = "Continue playing",
                        searchHint = "Search games sites, or type any address",
                        help = "Pair a Bluetooth controller in Android settings or plug in a USB one, or use the on-screen TOUCH PAD. " +
                            "INPUT: AUTO sends a game controller to pages that read one and keyboard keys to the rest " +
                            "(A→X  B→Z  X→S  Y→A  L/R→Q/W  Start→Enter  Select→Shift). GAME MODE gives the game the whole screen; " +
                            "FOCUS GAME blows the game up inside the page. Ads and pop-ups are blocked; a held pop-up can be opened " +
                            "from the bar above the page. Downloads (emulator apps, homebrew) go to Downloads/KITT. " +
                            "These sites aren't run by KITT: what you play is up to you.",
                        sections = listOf("Emulator apps for the head unit" to GameSites.EMULATORS, "Free homebrew games" to GameSites.GAME_SOURCES)
                    )
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                compact = session.immersive,
                pageOverlay = { if (touchPad) TouchPad(session, Modifier.fillMaxSize()) }
            )
        }
    }
}
