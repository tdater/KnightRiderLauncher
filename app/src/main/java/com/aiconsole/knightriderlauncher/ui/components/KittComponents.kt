package com.aiconsole.knightriderlauncher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashAmberDim
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashYellow
import com.aiconsole.knightriderlauncher.ui.theme.FilmHudText
import com.aiconsole.knightriderlauncher.ui.theme.HudStyle
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.KittBlack
import com.aiconsole.knightriderlauncher.ui.theme.KittCharcoal
import com.aiconsole.knightriderlauncher.ui.theme.KittGraphite
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRedDim
import com.aiconsole.knightriderlauncher.ui.theme.ThemeState
import com.aiconsole.knightriderlauncher.ui.theme.UiTheme
import com.aiconsole.knightriderlauncher.ui.theme.hud
import com.aiconsole.knightriderlauncher.ui.theme.hudFrame
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * KITT voice modulator: three columns of bars, the middle one reaching furthest.
 * Pass [level] (0..1, e.g. mic loudness) to drive it from real audio instead of the idle pattern.
 */
@Composable
fun VoiceModulator(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = ScannerRed,
    level: Float? = null
) {
    // Idle bars stand still: no frame clock runs (and nothing redraws) until KITT is active.
    val phase = if (active) voicePhase() else 0f
    VoiceBars(active, phase, level, modifier, color)
}

@Composable
private fun voicePhase(): Float {
    val transition = rememberInfiniteTransition(label = "voice")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "voicePhase"
    )
    return phase
}

@Composable
private fun VoiceBars(active: Boolean, phase: Float, level: Float?, modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val bars = 9
        val half = bars / 2
        val barGap = size.height * 0.05f
        val barHeight = (size.height - barGap * (bars - 1)) / bars
        val columnWidth = (size.width / 7f).coerceAtMost(size.height * 1.2f)
        val columnGap = columnWidth * 0.35f
        val startX = (size.width - (columnWidth * 3 + columnGap * 2)) / 2
        for (column in 0 until 3) {
            val amplitude = when {
                !active -> 0.1f
                level != null -> level * (if (column == 1) 1f else 0.6f + 0.15f * abs(sin(phase + column)))
                column == 1 -> 0.35f + 0.65f * abs(sin(phase * 2f))
                else -> 0.2f + 0.5f * abs(sin(phase + column))
            }
            val litHalf = (amplitude * half).roundToInt()
            for (bar in 0 until bars) {
                val lit = abs(bar - half) <= litHalf
                drawRect(
                    color = if (lit) color else color.copy(alpha = 0.12f),
                    topLeft = Offset(startX + column * (columnWidth + columnGap), bar * (barHeight + barGap)),
                    size = Size(columnWidth, barHeight)
                )
            }
        }
    }
}

/** Framed dash panel with an accent-tagged header strip. */
@Composable
fun KittPanel(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = ScannerRed,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    val console = ThemeState.theme == UiTheme.Colorado
    val hud = ThemeState.theme.hud
    Column(
        modifier
            .hudFrame(hud, accent)
            // The Matrix: translucent terminal glass with a soft phosphor glow over the rain.
            .then(if (console) Modifier.shadow(10.dp, shape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(shape)
            .background(
                if (console) Brush.verticalGradient(listOf(KittGraphite.copy(alpha = 0.9f), KittCharcoal.copy(alpha = 0.86f)))
                else Brush.verticalGradient(listOf(KittGraphite, KittCharcoal))
            )
            .border(1.dp, if (hud != null) accent.copy(alpha = 0.55f) else KittBezel, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!console && hud == null) {
                Box(
                    Modifier
                        .size(width = 4.dp, height = 14.dp)
                        .background(accent)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                // The Matrix reads like a terminal ("> NAVIGATION_"); the other film HUDs like targeting
                // readouts: "▌NAVIGATION" with a machine tag, or "[ NAVIGATION ]" in the visor style.
                text = when {
                    console -> "> ${title.uppercase()}_"
                    hud == HudStyle.Terminator -> "▌${title.uppercase()}"
                    hud == HudStyle.RoboCop -> "[ ${title.uppercase()} ]"
                    else -> title.uppercase()
                },
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1
            )
            if (hud == HudStyle.Terminator) {
                Spacer(Modifier.width(8.dp))
                Text(FilmHudText.hexTag(title), style = MaterialTheme.typography.labelSmall, color = accent.copy(alpha = 0.55f), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        HorizontalDivider(thickness = 1.dp, color = KittBezel)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            content = content
        )
    }
}

/** Rectangular back-lit dash button in the style of KITT's console switches. */
@Composable
fun KittButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = DashAmber,
    active: Boolean = false,
    enabled: Boolean = true
) {
    val shape = MaterialTheme.shapes.small
    val tint = if (enabled) color else DashGrey.copy(alpha = 0.5f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (active && enabled) tint.copy(alpha = 0.22f) else KittBlack)
            .border(1.dp, if (active && enabled) tint else tint.copy(alpha = 0.45f), shape)
            .clickable(enabled = enabled) {
                UiSounds.click()
                onClick()
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) tint else tint.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Tap/drag slider rendered as a row of LED segments. [value] is 0..1. */
@Composable
fun SegmentedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    segments: Int = 16,
    activeColor: Color = DashAmber,
    inactiveColor: Color = DashAmberDim
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    Canvas(
        modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress { target ->
                    currentOnValueChange(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnValueChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    currentOnValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val gap = 2.dp.toPx()
        val segmentWidth = (size.width - gap * (segments - 1)) / segments
        val lit = (value * segments).roundToInt()
        for (i in 0 until segments) {
            drawRoundRect(
                color = if (i < lit) activeColor else inactiveColor,
                topLeft = Offset(i * (segmentWidth + gap), 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

/**
 * Read-only LED bar graph. With [color] null the segments grade
 * green → yellow → red like KITT's dash meters; otherwise a single color.
 */
@Composable
fun LedBarGauge(
    level: Float,
    modifier: Modifier = Modifier,
    segments: Int = 12,
    color: Color? = null
) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val segmentWidth = (size.width - gap * (segments - 1)) / segments
        val lit = (level.coerceIn(0f, 1f) * segments).roundToInt()
        for (i in 0 until segments) {
            val fraction = (i + 1f) / segments
            val onColor = color ?: when {
                fraction <= 0.6f -> LedGreen
                fraction <= 0.85f -> DashYellow
                else -> AlertRed
            }
            drawRect(
                color = if (i < lit) onColor else onColor.copy(alpha = 0.12f),
                topLeft = Offset(i * (segmentWidth + gap), 0f),
                size = Size(segmentWidth, size.height)
            )
        }
    }
}
