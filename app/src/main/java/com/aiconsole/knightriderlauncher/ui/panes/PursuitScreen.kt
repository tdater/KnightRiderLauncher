package com.aiconsole.knightriderlauncher.ui.panes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.obd.ObdManager
import com.aiconsole.knightriderlauncher.obd.PidCatalog
import com.aiconsole.knightriderlauncher.obd.PidDefinition
import com.aiconsole.knightriderlauncher.obd.PidReading
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.components.LedBarGauge
import com.aiconsole.knightriderlauncher.ui.components.VoiceModulator
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed

/**
 * Pursuit mode: a full-screen dash in the style of KITT's 1982 console. A bar-graph
 * speedometer across the top, the big digital speed, the tach bar, the voice modulator in the
 * middle, and coolant, battery and throttle readouts. Glanceable from a distance.
 */
@Composable
fun PursuitScreen(
    hud: () -> HudState,
    reading: (String) -> PidReading?,
    definition: (String) -> PidDefinition?,
    metric: Boolean,
    kittSpeaking: Boolean,
    onKitt: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    val state = hud()
    val speedValue = state.speed?.toDoubleOrNull() ?: 0.0
    val topSpeed = if (metric) 200.0 else 120.0
    val speedColor = if (state.overLimit) ScannerRed else DashAmber

    fun readout(id: String): String {
        val def = definition(id) ?: return "--"
        val value = reading(id)?.takeIf { System.currentTimeMillis() - it.timestamp < 10_000 }?.value ?: return "--"
        return ObdManager.formatValue(def.displayValue(value, metric), def)
    }
    fun unit(id: String) = definition(id)?.unit(metric).orEmpty()

    BoxWithConstraints(
        modifier
            .background(KittBlack)
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(12.dp)
    ) {
        val wide = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PURSUIT MODE", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ScannerRed)
                Text(state.linkLabel, style = MaterialTheme.typography.labelMedium, color = if (state.linked) LedGreen else DashGrey)
                KittButton("Exit", onDismiss, Modifier.height(40.dp).width(96.dp), DashGrey)
            }
            // Bar-graph speedometer, the 1982 way: a long row of lamps.
            LedBarGauge((speedValue / topSpeed).toFloat(), Modifier.fillMaxWidth().height(if (wide) 34.dp else 44.dp), segments = 40, color = speedColor)
            if (wide) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Readouts(::readout, ::unit, Modifier.weight(1f).fillMaxHeight())
                    BigSpeed(state, speedColor, Modifier.weight(1.6f).fillMaxHeight())
                    KittBox(kittSpeaking, onKitt, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                BigSpeed(state, speedColor, Modifier.fillMaxWidth().weight(1.3f))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Readouts(::readout, ::unit, Modifier.weight(1f).fillMaxHeight())
                    KittBox(kittSpeaking, onKitt, Modifier.weight(1f).fillMaxHeight())
                }
            }
            LedBarGauge(state.rpmFraction, Modifier.fillMaxWidth().height(24.dp), segments = 32)
            Text(state.rpmText?.let { "$it RPM" } ?: "RPM", style = MaterialTheme.typography.labelMedium, color = DashGrey)
        }
    }
}

@Composable
private fun BigSpeed(state: HudState, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        SegmentDisplay(state.speed ?: "--", Modifier.fillMaxWidth().weight(1f), color, cells = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.speedUnit, style = MaterialTheme.typography.titleMedium, color = color)
            state.speedLimit?.let { Text("LIMIT $it", style = MaterialTheme.typography.titleMedium, color = if (state.overLimit) ScannerRed else DashGrey) }
        }
    }
}

@Composable
private fun Readouts(value: (String) -> String, unit: (String) -> String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PidCatalog.COOLANT to "COOLANT", PidCatalog.MODULE_VOLTAGE to "BATTERY", PidCatalog.THROTTLE to "THROTTLE").forEach { (id, label) ->
            val shape = MaterialTheme.shapes.small
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(shape)
                    .border(1.dp, KittBezel, shape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("$label ${unit(id)}".trim(), style = MaterialTheme.typography.labelSmall, color = DashGrey)
                SegmentDisplay(value(id), Modifier.fillMaxWidth().weight(1f), DashAmber, cells = 4)
            }
        }
    }
}

@Composable
private fun KittBox(speaking: Boolean, onKitt: () -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.medium
    Box(modifier.clip(shape).border(1.dp, ScannerRed.copy(alpha = 0.6f), shape)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VoiceModulator(active = speaking, modifier = Modifier.fillMaxWidth().weight(1f))
            KittButton("KITT", onKitt, Modifier.fillMaxWidth().height(48.dp), ScannerRed, active = true)
            Text("TAP TO TALK", Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = DashGrey, textAlign = TextAlign.Center)
        }
    }
}
