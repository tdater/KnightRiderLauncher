package com.aiconsole.knightriderlauncher.ui.panes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.obd.PidReading
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.KittPanel
import com.aiconsole.knightriderlauncher.ui.gauges.GaugeTile
import com.aiconsole.knightriderlauncher.ui.gauges.GaugeUi
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

/**
 * Gauges page: the dashboard gauges at a size readable at a glance. Like the home grid, each
 * tile pulls its value through [reading] so OBD updates redraw only the tiles.
 */
@Composable
fun GaugeWallPane(
    gauges: List<GaugeUi>,
    metric: Boolean,
    reading: (String) -> PidReading?,
    onGaugeClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPursuit: () -> Unit = {}
) {
    KittPanel(
        title = "Gauges",
        modifier = modifier,
        accent = LedGreen,
        trailing = { KittButton("Pursuit", onPursuit, Modifier.height(32.dp).width(104.dp), ScannerRed, active = true) }
    ) {
        if (gauges.isEmpty()) {
            Text(
                "No gauges yet. Tap here to add some in Diagnostics.",
                modifier = Modifier.clickable(onClick = onGaugeClick).padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = DashGrey
            )
            return@KittPanel
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(gauges, key = { it.config.id }) { gauge ->
                GaugeTile(
                    gauge = gauge.copy(reading = reading(gauge.config.pidId)),
                    metric = metric,
                    modifier = Modifier.height(180.dp),
                    onClick = onGaugeClick
                )
            }
        }
    }
}
