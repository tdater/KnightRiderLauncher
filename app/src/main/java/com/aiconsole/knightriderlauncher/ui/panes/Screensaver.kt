package com.aiconsole.knightriderlauncher.ui.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import com.aiconsole.knightriderlauncher.ui.scanner.KittScanner
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.FilmHudText
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.ThemeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Parked idle screen: KITT's voice box, a big segment clock and the scanner. Any tap wakes it. */
@Composable
fun Screensaver(onWake: () -> Unit, modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(10_000)
        }
    }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onWake),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth(0.8f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SegmentDisplay(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), Modifier.fillMaxWidth(0.7f).height(120.dp), ScannerRed, cells = 5)
            Text(
                SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = DashGrey
            )
            VoiceModulator(active = true, modifier = Modifier.fillMaxWidth(0.5f).height(90.dp))
            ThemeState.edition?.let { style ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(FilmHudText.brand(style), style = MaterialTheme.typography.labelLarge, color = ScannerRed)
                    FilmHudText.lines(style).forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = DashGrey) }
                }
            }
        }
        KittScanner(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp))
    }
}
