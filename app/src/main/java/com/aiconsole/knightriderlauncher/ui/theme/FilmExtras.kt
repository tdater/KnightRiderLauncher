package com.aiconsole.knightriderlauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiconsole.knightriderlauncher.ui.gauges.SegmentDisplay
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.delay

/** One row of the time circuits: month in letters, then day, year and 24-hour time as LED digits. */
data class TimeCircuitRow(val month: String, val digits: String)

/** Pure time-circuit formatting (unit-tested). */
object TimeCircuits {
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    fun row(epochMillis: Long, zone: TimeZone = TimeZone.getDefault()): TimeCircuitRow {
        val c = Calendar.getInstance(zone, Locale.US).apply { timeInMillis = epochMillis }
        return TimeCircuitRow(
            MONTHS[c.get(Calendar.MONTH)],
            "%02d %04d %02d%02d".format(Locale.US, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.YEAR), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        )
    }
}

/**
 * Back to the Future's time circuits, compact enough for the status strip: destination (the
 * route's arrival time, when navigating), present time and last time departed (when this drive
 * started), in the film's red, green and amber.
 */
@Composable
fun TimeCircuitsStrip(destination: Long?, present: Long, departed: Long?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        destination?.let { CircuitRow("DEST", TimeCircuits.row(it), Color(0xFFFF3A2A), Modifier.fillMaxHeight()) }
        CircuitRow("PRES", TimeCircuits.row(present), Color(0xFF3DFF6E), Modifier.fillMaxHeight())
        departed?.let { CircuitRow("LAST", TimeCircuits.row(it), Color(0xFFFFB627), Modifier.fillMaxHeight()) }
    }
}

@Composable
private fun CircuitRow(label: String, row: TimeCircuitRow, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color.Black).border(1.dp, Color(0xFF3A3F45)).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold), color = Color(0xFFBFC6CC))
        Text(row.month, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold), color = color)
        SegmentDisplay(row.digits, Modifier.width(118.dp).fillMaxHeight(), color, cells = row.digits.length)
    }
}

/**
 * A film theme's boot sequence, shown when the theme comes on: lines appear one by one (typed
 * out character by character in The Matrix), then it fades away by itself. Tap to skip.
 */
@Composable
fun ThemeIntro(style: HudStyle, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val lines = remember(style) { FilmHudText.intro(style) }
    val typed = style == HudStyle.Matrix
    // The first line shows at once, so the screen is never blank while the launcher is still starting.
    var shownChars by remember(style) { mutableIntStateOf(if (typed) 0 else lines.first().length + 1) }
    val total = lines.sumOf { it.length + 1 }
    LaunchedEffect(style) {
        if (typed) {
            while (shownChars < total) {
                delay(30)
                shownChars++
            }
        } else {
            for (i in 1 until lines.size) {
                delay(320)
                shownChars = lines.take(i + 1).sumOf { it.length + 1 }
            }
        }
        delay(1_400)
        onDone()
    }
    val (background, color) = when (style) {
        HudStyle.Terminator -> Color(0xF2140000) to Color(0xFFFF3B2A)
        HudStyle.RoboCop -> Color(0xF2020A14) to Color(0xFF66CCFF)
        HudStyle.Matrix -> Color(0xF2000000) to Color(0xFF00FF41)
        HudStyle.Future -> Color(0xF20A0C0E) to Color(0xFF9FE8FF)
    }
    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone)
            .padding(32.dp),
        contentAlignment = if (typed) Alignment.TopStart else Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var budget = shownChars
            lines.forEach { line ->
                if (budget <= 0) return@forEach
                val visible = line.take(budget)
                budget -= line.length + 1
                Text(
                    visible + if (typed && budget < 0) "█" else "",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = if (typed) 22.sp else 20.sp, letterSpacing = if (typed) 0.sp else 2.sp),
                    color = color
                )
            }
            if (style == HudStyle.Future && shownChars >= total) {
                TimeCircuitsStrip(destination = null, present = System.currentTimeMillis(), departed = null, modifier = Modifier.height(30.dp))
            }
        }
        Text(
            "TAP TO SKIP",
            Modifier.align(Alignment.BottomEnd),
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.6f)
        )
    }
}

/** Film-styled alert header: the line above KITT's message in the banner. */
@Composable
fun FilmAlertHeader(style: HudStyle?, color: Color) {
    val title = FilmHudText.alertTitle(style) ?: return
    Text(title, style = MaterialTheme.typography.labelMedium, color = color)
}

/** The assistant's on-screen name: KITT, or the film theme's character when that's on. */
@Composable
fun assistantName(): String {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.aiconsole.knightriderlauncher.KittApplication
    val on = app?.settings?.filmCharacters?.state?.collectAsState()?.value ?: false
    return (if (on) com.aiconsole.knightriderlauncher.assistant.FilmCharacter.forStyle(ThemeState.theme.hud)?.shortName else null) ?: "KITT"
}

/**
 * Terminator and RoboCop target the vehicle on the map: corner brackets with a readout
 * (Terminator) or a visor reticle (RoboCop). Drawn over the map, never touchable.
 */
@Composable
fun FilmMapReticle(style: HudStyle?, modifier: Modifier = Modifier) {
    if (style != HudStyle.Terminator && style != HudStyle.RoboCop) return
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.MONOSPACE } }
    androidx.compose.foundation.Canvas(modifier) {
        val c = center
        val color = if (style == HudStyle.Terminator) Color(0xFFFF3B2A) else Color(0xFF66CCFF)
        val stroke = 1.5.dp.toPx()
        if (style == HudStyle.Terminator) {
            val half = 46.dp.toPx()
            val len = 14.dp.toPx()
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx * len, y), stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + dy * len), stroke)
            }
            corner(c.x - half, c.y - half, 1f, 1f)
            corner(c.x + half, c.y - half, -1f, 1f)
            corner(c.x - half, c.y + half, 1f, -1f)
            corner(c.x + half, c.y + half, -1f, -1f)
            paint.textSize = 9.dp.toPx()
            paint.color = color.copy(alpha = 0.85f).toArgb()
            drawContext.canvas.nativeCanvas.drawText("SUBJECT: VEHICLE", c.x - half, c.y - half - 6.dp.toPx(), paint)
            drawContext.canvas.nativeCanvas.drawText("MATCH 99.8%", c.x - half, c.y + half + 14.dp.toPx(), paint)
        } else {
            val r = 40.dp.toPx()
            drawCircle(color.copy(alpha = 0.8f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawCircle(color.copy(alpha = 0.35f), r * 1.6f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            val tick = 12.dp.toPx()
            listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f).forEach { (dx, dy) ->
                drawLine(
                    color, androidx.compose.ui.geometry.Offset(c.x + dx * (r * 1.6f + 2), c.y + dy * (r * 1.6f + 2)),
                    androidx.compose.ui.geometry.Offset(c.x + dx * (r * 1.6f + tick), c.y + dy * (r * 1.6f + tick)), stroke
                )
            }
        }
    }
}

