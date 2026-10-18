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
import com.aiconsole.knightriderlauncher.browser.WebMedia
import com.aiconsole.knightriderlauncher.data.DeviceProfile
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.FilmEdition
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
    val edition = ThemeState.edition
    // Terminator: a fine 20-segment targeting bar instead of KITT's eight lenses. Zion's dock
    // beacons are a longer row of round lamps.
    val lamps = when {
        hud == HudStyle.Terminator -> TERMINATOR_SEGMENTS
        edition == FilmEdition.Zion -> ZION_BEACONS
        hud == HudStyle.Matrix -> MATRIX_GLYPHS
        else -> lampCount
    }
    val code = remember { CodeBar() }
    val engine = remember(lamps) { ScannerEngine(lamps) }
    var frameNanos by remember { mutableLongStateOf(0L) }
    // Lite mode: ~30 fps is plenty for the sweep and halves the redraws. Slower still beside a web page.
    val minFrame = DeviceProfile.scannerFrameNanos(LocalLiteMode.current, WebMedia.pagesOnScreen > 0)
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
        // Each film edition has its own light; the shift light always uses the lamps below.
        if (shiftLevel == null) {
            val fast = alert || rpmFraction() > 0.6f
            when (edition) {
                FilmEdition.T1000 -> drawMercury(levels, color, pulse, frameNanos)
                FilmEdition.FutureWar -> drawPlasma(levels, color, DashAmber, pulse)
                FilmEdition.Visor, FilmEdition.DeltaCity -> drawVisor(levels, color, pulse)
                FilmEdition.Ed209 -> drawSensorEye(levels, color, AlertRed, pulse)
                FilmEdition.Code -> code.draw(this, levels, color, pulse, frameNanos)
                FilmEdition.RealWorld -> drawEmp(frameNanos, color, pulse, fast)
                FilmEdition.Delorean1985 -> drawFlux(frameNanos, color, pulse, fast)
                FilmEdition.HillValley2015 -> drawFlux(frameNanos, color, pulse, fast, alt = DashAmber)
                FilmEdition.Frontier1885 -> drawFlux(frameNanos, color, pulse, fast, steam = true)
                FilmEdition.T800, FilmEdition.Zion, null -> null
            }?.let { return@Canvas }
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
    val head = headOf(levels)
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

/** Where the sweep is, 0 (left) to 1 (right): the lamps' brightness-weighted centre. */
private fun headOf(levels: FloatArray): Float {
    var sum = 0f
    var weighted = 0f
    levels.forEachIndexed { i, v ->
        sum += v
        weighted += v * i
    }
    return if (sum > 0f) weighted / sum / (levels.size - 1).coerceAtLeast(1) else 0.5f
}

/**
 * Terminator 2's liquid metal: a molten chrome bead slides along a dark slot, stretching as it
 * moves and wobbling like mercury, with a faint reflective trail.
 */
private fun DrawScope.drawMercury(levels: FloatArray, color: Color, pulse: Float, now: Long) {
    val head = headOf(levels)
    val slotTop = size.height * 0.24f
    val slotHeight = size.height * 0.52f
    drawRect(color.copy(alpha = 0.08f), Offset(0f, slotTop), Size(size.width, slotHeight))
    val wobble = kotlin.math.sin(now / 140_000_000.0).toFloat()
    val beadWidth = size.width * (0.075f + 0.02f * wobble)
    val beadHeight = slotHeight * (0.92f - 0.1f * wobble)
    val x = size.width * head - beadWidth / 2
    val y = size.height / 2 - beadHeight / 2
    // Reflective trail either side.
    drawRect(
        Brush.horizontalGradient(
            listOf(Color.Transparent, color.copy(alpha = 0.22f * pulse), Color.Transparent),
            startX = x - beadWidth * 2.5f,
            endX = x + beadWidth * 3.5f
        ),
        Offset(0f, slotTop),
        Size(size.width, slotHeight)
    )
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = pulse), color, Color(0xFF3A4450), color, Color.White.copy(alpha = 0.8f * pulse)),
            startY = y,
            endY = y + beadHeight
        ),
        Offset(x, y),
        Size(beadWidth, beadHeight),
        CornerRadius(beadHeight / 2)
    )
    // Specular highlight.
    drawRoundRect(Color.White.copy(alpha = 0.7f * pulse), Offset(x + beadWidth * 0.2f, y + beadHeight * 0.14f), Size(beadWidth * 0.35f, beadHeight * 0.16f), CornerRadius(beadHeight))
}

/**
 * The future war: a plasma bolt streaks along the slot (blue core, violet glow) with two
 * fading after-images behind it.
 */
private fun DrawScope.drawPlasma(levels: FloatArray, color: Color, glow: Color, pulse: Float) {
    val head = headOf(levels)
    val mid = size.height / 2
    drawLine(color.copy(alpha = 0.18f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
    val bolt = size.width * 0.09f
    for (k in 2 downTo 0) {
        val x = size.width * head
        val trail = bolt * (1 + k * 0.9f)
        val alpha = pulse * (1f - k * 0.35f)
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, glow.copy(alpha = 0.5f * alpha), color.copy(alpha = alpha), Color.White.copy(alpha = alpha), color.copy(alpha = alpha), glow.copy(alpha = 0.5f * alpha), Color.Transparent),
                startX = x - trail,
                endX = x + trail
            ),
            Offset(x - trail, mid - size.height * (0.18f - k * 0.04f)),
            Size(trail * 2, size.height * (0.36f - k * 0.08f))
        )
    }
}

/**
 * ED-209: yellow-and-black hazard stripes at both ends and a red sensor eye scanning the slit
 * between them.
 */
private fun DrawScope.drawSensorEye(levels: FloatArray, hazard: Color, eye: Color, pulse: Float) {
    val end = size.width * 0.1f
    val stripe = size.height * 0.7f
    listOf(0f, size.width - end).forEach { left ->
        clipRect(left, 0f, left + end, size.height) {
            drawRect(Color(0xFF111111), Offset(left, 0f), Size(end, size.height))
            var x = left - size.height
            while (x < left + end + size.height) {
                drawLine(hazard.copy(alpha = 0.85f), Offset(x, size.height), Offset(x + size.height, 0f), stripe / 2)
                x += stripe
            }
        }
    }
    val slitLeft = end + size.height * 0.3f
    val slitWidth = size.width - 2 * slitLeft
    val slitTop = size.height * 0.36f
    val slitHeight = size.height * 0.28f
    drawRect(eye.copy(alpha = 0.12f), Offset(slitLeft, slitTop), Size(slitWidth, slitHeight))
    val x = slitLeft + slitWidth * headOf(levels)
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = pulse), eye.copy(alpha = pulse), Color.Transparent), center = Offset(x, size.height / 2), radius = size.height * 0.55f),
        size.height * 0.55f,
        Offset(x, size.height / 2)
    )
}

private const val EMP_CELLS = 24

/**
 * The hovercraft's EMP: cells charge up from the left, then the whole bar discharges in a
 * white-blue flash and starts again. Charges faster when the engine works hard.
 */
private fun DrawScope.drawEmp(now: Long, color: Color, pulse: Float, fast: Boolean) {
    val period = if (fast) 1_600_000_000L else 3_200_000_000L
    val t = (now % period).toFloat() / period
    val charging = 0.85f
    val gap = size.height * 0.16f
    val cell = (size.width - gap * (EMP_CELLS + 1)) / EMP_CELLS
    val h = size.height * 0.5f
    val top = (size.height - h) / 2
    val lit = if (t < charging) (t / charging * EMP_CELLS).toInt() else EMP_CELLS
    val flash = if (t >= charging) 1f - (t - charging) / (1f - charging) else 0f
    for (i in 0 until EMP_CELLS) {
        val x = gap + i * (cell + gap)
        drawRect(color.copy(alpha = 0.12f), Offset(x, top), Size(cell, h))
        val level = when {
            flash > 0f -> flash
            i < lit -> 0.35f + 0.65f * (i + 1f) / EMP_CELLS
            else -> 0f
        } * pulse
        if (level > 0.02f) drawRect(lerp(color, Color.White, flash * 0.8f).copy(alpha = level), Offset(x, top), Size(cell, h))
    }
    drawLine(KittBezel, Offset(0f, 0f), Offset(size.width, 0f), 1f)
    drawLine(KittBezel, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
}

private const val ZION_BEACONS = 12

private const val FLUX_CELLS = 28

/**
 * Back to the Future: the flux capacitor laid out flat. Pulses of blue-white light run in from
 * both ends to a bright core in the middle, faster when the engine works hard. 2015 alternates
 * two neon colours ([alt]); 1885 puffs slow, soft steam pulses through brass ([steam]).
 */
private fun DrawScope.drawFlux(now: Long, color: Color, pulse: Float, fast: Boolean, alt: Color? = null, steam: Boolean = false) {
    val period = (if (fast) 420_000_000L else 900_000_000L) * (if (steam) 2 else 1)
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
            level = maxOf(level, kotlin.math.exp(-(dist * dist) / if (steam) 0.02f else 0.006f))
        }
        level = maxOf(level, (1f - d * 4f).coerceAtLeast(0f)) * pulse
        val x = gap + i * (cell + gap)
        val tint = if (alt != null && (i / 2) % 2 == 1) alt else color
        drawRect(tint.copy(alpha = 0.12f), Offset(x, top), Size(cell, h))
        if (level > 0.02f) {
            drawRect(lerp(tint, Color.White, level * (if (steam) 0.35f else 0.7f)).copy(alpha = level), Offset(x, top), Size(cell, h))
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
