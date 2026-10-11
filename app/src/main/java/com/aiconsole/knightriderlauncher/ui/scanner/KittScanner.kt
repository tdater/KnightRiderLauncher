package com.aiconsole.knightriderlauncher.ui.scanner

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withScale
import com.aiconsole.knightriderlauncher.data.DeviceProfile
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.HudStyle
import com.aiconsole.knightriderlauncher.ui.theme.KittBezel
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import com.aiconsole.knightriderlauncher.ui.theme.LocalLiteMode
import com.aiconsole.knightriderlauncher.ui.theme.ScannerRed
import com.aiconsole.knightriderlauncher.ui.theme.ThemeState
import com.aiconsole.knightriderlauncher.ui.theme.hud

/**
 * The always-on KITT front scanner: a black slot with [lampCount] lenses (20 segments in the Terminator theme, one visor in RoboCop) in the theme's accent
 * and a persistence trail. The sweep speeds up with engine revs ([rpmFraction], 0..1) and
 * the lamps pulse while KITT is [speaking]. With [shift] (0..1, null when off or below the
 * band) it becomes a shift light. All three are read in the draw pass, so frame and OBD
 * updates only trigger a redraw, never recomposition.
 */
@Composable
fun KittScanner(
    modifier: Modifier = Modifier,
    lampCount: Int = 8,
    color: Color = ScannerRed,
    alert: Boolean = false,
    rpmFraction: () -> Float = { 0f },
    speaking: () -> Boolean = { false },
    shift: () -> Float? = { null }
) {
    val hud = ThemeState.theme.hud
    // Terminator: a fine 20-segment targeting bar instead of KITT's eight lenses.
    val lamps = when (hud) {
        HudStyle.Terminator -> TERMINATOR_SEGMENTS
        HudStyle.Matrix -> MATRIX_GLYPHS
        else -> lampCount
    }
    val code = remember { CodeBar() }
    val engine = remember(lamps) { ScannerEngine(lamps) }
    var frameNanos by remember { mutableLongStateOf(0L) }
    // Lite mode: ~30 fps is plenty for the sweep and halves the redraws.
    val minFrame = DeviceProfile.scannerFrameNanos(LocalLiteMode.current)
    LaunchedEffect(engine, minFrame) {
        while (true) withFrameNanos { if (it - frameNanos >= minFrame) frameNanos = it }
    }

    Canvas(modifier) {
        engine.speed = ScannerEngine.speedFor(alert, rpmFraction())
        val pulse = if (speaking()) ScannerEngine.voicePulse(frameNanos) else 1f
        val levels = engine.step(frameNanos)
        // Shift light: lamps fill from both ends toward the middle (green, amber, then the
        // accent) and all flash at the shift point. Otherwise the normal sweep.
        val shiftLevel = shift()
        val lampColor = { i: Int ->
            if (shiftLevel == null) color
            else when (ScannerEngine.shiftRank(i, lamps) * 3 / ((lamps + 1) / 2).coerceAtLeast(1)) {
                0 -> LedGreen
                1 -> DashAmber
                else -> color
            }
        }
        val lampLevel = { i: Int ->
            when {
                shiftLevel == null -> levels[i] * pulse
                shiftLevel >= 1f -> if (ScannerEngine.shiftFlashOn(frameNanos)) 1f else 0f
                ScannerEngine.shiftLampLit(i, lamps, shiftLevel) -> 1f
                else -> 0f
            }
        }
        drawRect(Color.Black)
        if (hud == HudStyle.RoboCop && shiftLevel == null) {
            drawVisor(levels, color, pulse)
            return@Canvas
        }
        if (hud == HudStyle.Matrix && shiftLevel == null) {
            code.draw(this, levels, color, pulse, frameNanos)
            return@Canvas
        }
        if (hud == HudStyle.Future && shiftLevel == null) {
            drawFlux(frameNanos, color, pulse, fast = alert || rpmFraction() > 0.6f)
            return@Canvas
        }
        clipRect {
            val gap = size.height * if (hud == HudStyle.Terminator) 0.12f else 0.22f
            val lampWidth = (size.width - gap * (lamps + 1)) / lamps
            val lampHeight = size.height * if (hud == HudStyle.Terminator) 0.4f else 0.56f
            val top = (size.height - lampHeight) / 2
            val corner = if (hud == HudStyle.Terminator) CornerRadius.Zero else CornerRadius(lampHeight * 0.18f)
            for (i in 0 until lamps) {
                val tint = lampColor(i)
                val hot = lerp(tint, Color.White, 0.45f)
                val x = gap + i * (lampWidth + gap)
                val level = lampLevel(i)
                // Unlit lens: dark ruby glass.
                drawRoundRect(tint.copy(alpha = 0.16f), Offset(x, top), Size(lampWidth, lampHeight), corner)
                if (level < 0.02f) continue
                // Bloom spilling around the lens.
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.55f * level), Color.Transparent),
                        center = Offset(x + lampWidth / 2, size.height / 2),
                        radius = lampWidth * 0.85f
                    ),
                    topLeft = Offset(x - lampWidth * 0.35f, 0f),
                    size = Size(lampWidth * 1.7f, size.height)
                )
                // Lit lens with a hot filament line through the middle.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.75f * level),
                            hot.copy(alpha = level),
                            tint.copy(alpha = 0.75f * level)
                        ),
                        startY = top,
                        endY = top + lampHeight
                    ),
                    topLeft = Offset(x, top),
                    size = Size(lampWidth, lampHeight),
                    cornerRadius = corner
                )
            }
        }
        // Chrome bezel lines top and bottom of the slot.
        drawLine(KittBezel, Offset(0f, 0f), Offset(size.width, 0f), 1f)
        drawLine(KittBezel, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
    }
}

private const val TERMINATOR_SEGMENTS = 20

/**
 * RoboCop's visor: one continuous slot with a soft glow sliding along it (following the same
 * eased sweep as KITT's lamps) and a bright scan line through the middle.
 */
private fun DrawScope.drawVisor(levels: FloatArray, color: Color, pulse: Float) {
    var sum = 0f
    var weighted = 0f
    levels.forEachIndexed { i, v ->
        sum += v
        weighted += v * i
    }
    val head = if (sum > 0f) weighted / sum / (levels.size - 1).coerceAtLeast(1) else 0.5f
    val slotTop = size.height * 0.22f
    val slotHeight = size.height * 0.56f
    drawRect(color.copy(alpha = 0.10f), Offset(0f, slotTop), Size(size.width, slotHeight))
    val x = size.width * head
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = 0.85f * pulse), Color.White.copy(alpha = 0.9f * pulse), color.copy(alpha = 0.85f * pulse), Color.Transparent),
            startX = x - size.width * 0.14f,
            endX = x + size.width * 0.14f
        ),
        topLeft = Offset(0f, slotTop),
        size = Size(size.width, slotHeight)
    )
    drawLine(color.copy(alpha = 0.55f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
}

private const val FLUX_CELLS = 28

/**
 * Back to the Future: the flux capacitor laid out flat. Pulses of blue-white light run in from
 * both ends to a bright core in the middle, faster when the engine works hard.
 */
private fun DrawScope.drawFlux(now: Long, color: Color, pulse: Float, fast: Boolean) {
    val period = if (fast) 420_000_000L else 900_000_000L
    val phase = (now % period).toFloat() / period
    val gap = size.height * 0.16f
    val cell = (size.width - gap * (FLUX_CELLS + 1)) / FLUX_CELLS
    val h = size.height * 0.5f
    val top = (size.height - h) / 2
    for (i in 0 until FLUX_CELLS) {
        val d = kotlin.math.abs(i + 0.5f - FLUX_CELLS / 2f) / (FLUX_CELLS / 2f) // 0 centre, 1 edge
        var level = 0f
        for (k in 0 until 3) {
            val p = (phase + k / 3f) % 1f
            val head = 1f - p // pulses travel from the edge (1) to the centre (0)
            val dist = d - head
            level = maxOf(level, kotlin.math.exp(-(dist * dist) / 0.006f))
        }
        level = maxOf(level, (1f - d * 4f).coerceAtLeast(0f)) * pulse
        val x = gap + i * (cell + gap)
        drawRect(color.copy(alpha = 0.12f), Offset(x, top), Size(cell, h))
        if (level > 0.02f) {
            drawRect(lerp(color, Color.White, level * 0.7f).copy(alpha = level), Offset(x, top), Size(cell, h))
        }
    }
    drawLine(KittBezel, Offset(0f, 0f), Offset(size.width, 0f), 1f)
    drawLine(KittBezel, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
}

private const val MATRIX_GLYPHS = 16

/**
 * The Matrix: a row of code glyphs (mirrored half-width katakana and digits). The sweep's lead
 * glyph burns white, the trail fades through code green, and glyphs keep changing like the rain.
 */
private class CodeBar {
    private val glyphs = CharArray(MATRIX_GLYPHS) { GLYPHS.random() }
    private var lastSwap = 0L
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    fun draw(scope: DrawScope, levels: FloatArray, color: Color, pulse: Float, now: Long) = with(scope) {
        if (now - lastSwap > SWAP_NANOS) {
            lastSwap = now
            repeat(3) { glyphs[(glyphs.indices).random()] = GLYPHS.random() }
        }
        val cell = size.width / glyphs.size
        paint.textSize = size.height * 0.78f
        val baseline = size.height / 2 - (paint.ascent() + paint.descent()) / 2
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            glyphs.forEachIndexed { i, glyph ->
                val level = (levels.getOrElse(i) { 0f } * pulse).coerceIn(0f, 1f)
                val tint = if (level > 0.85f) lerp(color, Color.White, (level - 0.85f) / 0.15f) else color
                paint.color = tint.copy(alpha = 0.18f + 0.82f * level).toArgb()
                val x = cell * (i + 0.5f)
                // Mirrored, like the film's code.
                native.withScale(-1f, 1f, x, baseline) { drawText(glyph.toString(), x, baseline, paint) }
            }
        }
    }

    private companion object {
        const val GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789Z:."
        const val SWAP_NANOS = 120_000_000L
    }
}
