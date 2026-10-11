package com.aiconsole.knightriderlauncher.ui.panes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.KittCharcoal
import com.aiconsole.knightriderlauncher.ui.theme.KittGrid
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.assistantName
import com.aiconsole.knightriderlauncher.ui.trip.rememberRecentTrips
import java.util.Locale

/** Bottom pane: stationary dock with climate, media and vehicle shortcuts. */
@Composable
fun ControlDock(
    onAssistant: () -> Unit,
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier,
    kittSpeaking: Boolean = false,
    onTrip: () -> Unit = {},
    metric: Boolean = false
) {
    val context = LocalContext.current
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .clip(shape)
            .background(KittCharcoal)
            .border(1.dp, KittBezel, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TripSection(onTrip, metric, Modifier.weight(1.3f).fillMaxHeight())
            MediaSection(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onMediaKey = { sendMediaKey(context, it) }
            )
            VehicleSection(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onSystemSettings = { openSettings(context, Settings.ACTION_SETTINGS) },
                onTrips = onTrip,
                onDiagnostics = onDiagnostics,
                onConfig = onConfig
            )
            AssistantSection(onAssistant, kittSpeaking, Modifier.weight(0.5f).fillMaxHeight())
        }
    }
}

/**
 * Slim one-row dock for short landscape screens (7"–10" head units), where the stacked dock
 * would eat a quarter of the height: last trip, now playing with media keys, and the vehicle
 * buttons. KITT itself sits at the top of the side rail.
 */
@Composable
fun DockBar(
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier,
    onTrip: () -> Unit = {},
    metric: Boolean = false
) {
    val context = LocalContext.current
    val app = context.applicationContext as KittApplication
    val shape = MaterialTheme.shapes.medium
    val latest = rememberRecentTrips(app.tripDatabase.tripMetricDao(), days = 1)?.firstOrNull()
    val now by app.media.nowPlaying.collectAsState()
    val current = now
    Row(
        modifier
            .clip(shape)
            .background(KittCharcoal)
            .border(1.dp, KittBezel, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Last trip: tap for the trip computer.
        Row(
            Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(KittBlack)
                .border(1.dp, KittGrid, MaterialTheme.shapes.small)
                .clickable(onClick = onTrip)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentDisplay(
                latest?.let { "%.1f".format(Locale.US, if (metric) it.distanceKm else it.distanceKm * 0.621371) } ?: "--",
                Modifier.weight(1f).fillMaxHeight(0.8f),
                DashAmber,
                cells = 4
            )
            Text(if (metric) "KM" else "MI", style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
            SegmentDisplay(
                latest?.let { t -> (if (metric) t.litersPer100Km else t.mpg)?.let { "%.1f".format(Locale.US, it) } } ?: "--",
                Modifier.weight(1f).fillMaxHeight(0.8f),
                LedGreen,
                cells = 4
            )
            Text(if (metric) "L/100" else "MPG", style = MaterialTheme.typography.labelSmall, color = DashGrey, maxLines = 1)
        }
        // Now playing with media keys.
        Row(
            Modifier
                .weight(2f)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(KittBlack)
                .border(1.dp, KittGrid, MaterialTheme.shapes.small)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    current?.let { it.title ?: it.appLabel } ?: "NO MEDIA",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (current != null) DashWhite else DashGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                current?.let {
                    Text(
                        it.artist ?: it.appLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = DashGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            KittButton(
                "◀◀",
                { if (current != null) app.media.previous() else sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                Modifier.width(52.dp).fillMaxHeight(),
                ScannerRed
            )
            KittButton(
                if (current?.playing == true) "❚❚" else "▶",
                { if (current != null) app.media.playPause() else sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                Modifier.width(56.dp).fillMaxHeight(),
                ScannerRed,
                active = current?.playing == true
            )
            KittButton(
                "▶▶",
                { if (current != null) app.media.next() else sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                Modifier.width(52.dp).fillMaxHeight(),
                ScannerRed
            )
        }
        KittButton("Diag", onDiagnostics, Modifier.weight(0.6f).fillMaxHeight(), LedGreen, active = true)
        KittButton("Trips", onTrip, Modifier.weight(0.6f).fillMaxHeight(), ScannerRed, active = true)
        KittButton("Config", onConfig, Modifier.weight(0.6f).fillMaxHeight(), DashAmber)
        KittButton("System", { openSettings(context, Settings.ACTION_SETTINGS) }, Modifier.weight(0.6f).fillMaxHeight(), DashAmber)
    }
}

/** Whole section is one large touch target that opens the KITT assistant. */
@Composable
private fun AssistantSection(onClick: () -> Unit, speaking: Boolean, modifier: Modifier = Modifier) {
    DockSection("Assistant", ScannerRed, modifier.clickable(onClick = onClick)) {
        VoiceModulator(
            active = speaking,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Text(
            text = assistantName(),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            color = ScannerRed,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DockSection(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .clip(shape)
            .background(KittBlack)
            .border(1.dp, KittGrid, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
        content()
    }
}

/**
 * Last drive at a glance; tap for the trip computer. (This slot used to hold climate sliders,
 * but third-party apps can't reach the HVAC bus on aftermarket head units.)
 */
@Composable
private fun TripSection(onClick: () -> Unit, metric: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val latest = rememberRecentTrips(app.tripDatabase.tripMetricDao(), days = 1)?.firstOrNull()
    DockSection("Trip", DashAmber, modifier.clickable(onClick = onClick)) {
        TripLine(
            "DIST",
            latest?.let { "%.1f".format(Locale.US, if (metric) it.distanceKm else it.distanceKm * 0.621371) } ?: "--",
            if (metric) "KM" else "MI",
            DashAmber,
            Modifier.weight(1f)
        )
        TripLine(
            "ECON",
            latest?.let { t -> (if (metric) t.litersPer100Km else t.mpg)?.let { "%.1f".format(Locale.US, it) } } ?: "--",
            if (metric) "L/100" else "MPG",
            LedGreen,
            Modifier.weight(1f)
        )
        TripLine(
            "TIME",
            latest?.let { (it.durationMs / 60_000).toString() } ?: "--",
            "MIN",
            DashAmber,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun TripLine(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = DashGrey)
        SegmentDisplay(value, Modifier.weight(1f).fillMaxHeight(0.8f), color, cells = 5)
        Text(
            text = unit,
            modifier = Modifier.width(44.dp),
            style = MaterialTheme.typography.labelSmall,
            color = DashGrey,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

/** Compact now-playing: track and progress when a media session is visible, else plain media keys. */
@Composable
private fun MediaSection(onMediaKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as KittApplication
    val now by app.media.nowPlaying.collectAsState()
    val current = now

    DockSection("Media", ScannerRed, modifier) {
        if (current != null) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    current.title ?: current.appLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = DashWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    current.artist ?: current.appLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = DashGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ProgressBar(current)
            }
        } else {
            VoiceModulator(
                active = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KittButton(
                label = "Prev",
                onClick = { if (current != null) app.media.previous() else onMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = ScannerRed
            )
            KittButton(
                label = if (current?.playing == true) "Pause" else "Play",
                onClick = { if (current != null) app.media.playPause() else onMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = ScannerRed,
                active = current?.playing == true
            )
            KittButton(
                label = "Next",
                onClick = { if (current != null) app.media.next() else onMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                color = ScannerRed
            )
        }
    }
}

@Composable
private fun VehicleSection(
    onSystemSettings: () -> Unit,
    onTrips: () -> Unit,
    onDiagnostics: () -> Unit,
    onConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    DockSection("Vehicle", LedGreen, modifier) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KittButton("Diagnostics", onDiagnostics, Modifier.weight(1f).fillMaxHeight(), LedGreen, active = true)
            KittButton("Trips", onTrips, Modifier.weight(1f).fillMaxHeight(), ScannerRed, active = true)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KittButton("KITT Config", onConfig, Modifier.weight(1f).fillMaxHeight(), DashAmber)
            KittButton("System", onSystemSettings, Modifier.weight(1f).fillMaxHeight(), DashAmber)
        }
    }
}

/** Sends a media key to whichever app currently holds the active media session. */
private fun sendMediaKey(context: Context, keyCode: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Some head-unit ROMs strip individual settings screens.
    }
}
