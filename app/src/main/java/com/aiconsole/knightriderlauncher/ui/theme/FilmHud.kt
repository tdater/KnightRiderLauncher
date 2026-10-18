package com.aiconsole.knightriderlauncher.ui.theme

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.browser.WebMedia
import kotlin.random.Random

/** Text the film HUD themes show in the status strip, panels, screensaver and intros (see [FilmEdition]). */
object FilmHudText {

    fun lines(edition: FilmEdition): List<String> = edition.lines

    /** The film's original edition's readouts. */
    fun lines(style: HudStyle): List<String> = lines(FilmEdition.default(style))

    /** Brand line for the status strip. */
    fun brand(edition: FilmEdition): String = edition.brand

    /** What the alert banner calls KITT's messages. */
    fun alertTitle(edition: FilmEdition?): String? = edition?.alertTitle

    /** The boot sequence each edition shows when it comes on: original lines, not dialogue. */
    fun intro(edition: FilmEdition): List<String> = edition.intro

    fun intro(style: HudStyle): List<String> = intro(FilmEdition.default(style))

    /** Machine-looking tag for a panel title, e.g. "0x5E2A": stable per title. */
    fun hexTag(title: String): String = "0x%04X".format(title.uppercase().hashCode() and 0xFFFF)

    /** The T-800 vision's scrolling machine code: made-up assembly and telemetry, the same every run. */
    val MACHINE_CODE: List<String> = Random(1984).let { r ->
        val ops = listOf("LDA", "STA", "JSR", "CMP", "BNE", "LDX", "INX", "RTS", "AND", "ORA")
        val words = listOf("SCAN", "VEHICLE.SYS", "TARGET", "ROUTE", "THERMAL", "AUDIO", "MOTOR", "VISION", "PURSUIT", "FUEL")
        List(48) { i ->
            when (i % 4) {
                0 -> "%04X %s #$%02X".format(0x0400 + i * 6, ops[r.nextInt(ops.size)], r.nextInt(256))
                1 -> "%s.%02d %s".format(words[r.nextInt(words.size)], r.nextInt(100), if (r.nextBoolean()) "OK" else "...")
                2 -> "MATCH %d.%d%%".format(80 + r.nextInt(20), r.nextInt(10))
                else -> List(4) { "%02X".format(r.nextInt(256)) }.joinToString(" ")
            }
        }
    }
}

/** Which editions' overlays move, and how often they redraw (nanoseconds per frame). */
private fun overlayStep(edition: FilmEdition): Long? = when (edition) {
    FilmEdition.T800 -> 80_000_000L // ~12 fps for the code column.
    FilmEdition.Visor -> 33_000_000L // ~30 fps for the visor sweep.
    FilmEdition.T1000, FilmEdition.FutureWar -> 50_000_000L // Slow sheen and searchlight: 20 fps reads as smooth.
    else -> null
}

/**
 * Full-screen HUD layer drawn over the launcher (never intercepts touches), per edition:
 * - Terminator: red vision with scrolling machine code (T-800); a liquid-chrome sheen flowing
 *   across a steel vignette (Liquid Metal); a hunter-killer searchlight over night-battle blue
 *   (Future War);
 * - RoboCop: visor glow with a sweeping scan band (Visor); a hazard-stripe band and red sensor
 *   glow (ED-209); executive gold light top and bottom (Delta City);
 * - The Matrix: green CRT bloom (Digital Rain), the hovercraft's cold blue monitors (Real World),
 *   Zion's amber firelight;
 * - Back to the Future: brushed-steel edges (1985), neon magenta and cyan (2015), sepia (1885).
 * Moving parts stand still in lite mode and while a web page is on screen.
 */
@Composable
fun FilmHudOverlay(edition: FilmEdition, modifier: Modifier = Modifier) {
    val step = overlayStep(edition)
    // Still over a web page too: this layer covers the whole screen, so every frame of it made
    // the page underneath composite again.
    val animate = step != null && !LocalLiteMode.current && WebMedia.pagesOnScreen == 0
    var now by remember { mutableLongStateOf(0L) }
    if (animate) {
        LaunchedEffect(edition) {
            var last = 0L
            while (true) withFrameNanos {
                if (it - last >= step!!) {
                    last = it
                    now = it
                }
            }
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    Canvas(modifier) {
        if (edition.film != HudStyle.Future && edition != FilmEdition.Zion) scanlines(if (edition.film == HudStyle.Terminator) 0.22f else 0.16f)
        fun vignette(color: Color, reach: Float = 0.75f) =
            drawRect(Brush.radialGradient(listOf(Color.Transparent, color), center = center, radius = size.maxDimension * reach))
        when (edition) {
            FilmEdition.T800 -> {
                vignette(Color(0x55FF1A0A))
                machineCode(paint, now)
            }
            FilmEdition.T1000 -> {
                vignette(Color(0x40A8C0D8), 0.8f)
                chromeSheen(now)
            }
            FilmEdition.FutureWar -> {
                vignette(Color(0x55101E66))
                searchlight(now)
            }
            FilmEdition.Visor -> {
                edgeGlow(Color(0x3366CCFF))
                visorSweep(now)
            }
            FilmEdition.Ed209 -> {
                vignette(Color(0x33FF2A1A), 0.85f)
                hazardBand()
            }
            FilmEdition.DeltaCity -> edgeGlow(Color(0x30E8C45A))
            // Green phosphor bloom at the edges of an old monochrome CRT.
            FilmEdition.Code -> vignette(Color(0x3300FF41), 0.8f)
            FilmEdition.RealWorld -> vignette(Color(0x2E5FD4FF), 0.8f)
            FilmEdition.Zion -> vignette(Color(0x40FF8A1E), 0.7f)
            FilmEdition.Delorean1985 -> {
                // Brushed-steel sheen along the edges, like the DeLorean's panels.
                sideGlow(Color(0x22C8D0D8), Color(0x22C8D0D8))
            }
            FilmEdition.HillValley2015 -> sideGlow(Color(0x2EFF3DB8), Color(0x2E3DF0FF))
            FilmEdition.Frontier1885 -> vignette(Color(0x66402810), 0.7f)
        }
    }
}

private fun DrawScope.scanlines(alpha: Float) {
    val pitch = 3.dp.toPx()
    var y = 0f
    val line = Color.Black.copy(alpha = alpha)
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
        y += pitch
    }
}

/** Light along the top and bottom edges (a visor's rim, a boardroom's downlights). */
private fun DrawScope.edgeGlow(glow: Color) {
    drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), startY = 0f, endY = size.height * 0.12f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), startY = size.height * 0.88f, endY = size.height))
}

private fun DrawScope.sideGlow(left: Color, right: Color) {
    drawRect(Brush.horizontalGradient(listOf(left, Color.Transparent), startX = 0f, endX = size.width * 0.06f))
    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, right), startX = size.width * 0.94f, endX = size.width))
}

/** Liquid metal: a soft diagonal band of chrome light flows across the screen now and then. */
private fun DrawScope.chromeSheen(now: Long) {
    if (now == 0L) return
    val period = 9_000_000_000L
    val flow = 2_200_000_000L
    val t = now % period
    if (t > flow) return
    val p = t.toFloat() / flow
    val x = -size.width * 0.3f + size.width * 1.6f * p
    val band = size.width * 0.22f
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color(0x22DCE8F2), Color(0x44FFFFFF), Color(0x22DCE8F2), Color.Transparent),
            start = Offset(x - band, 0f),
            end = Offset(x + band, size.height * 0.6f)
        )
    )
}

/** Future war: a hunter-killer's searchlight drifts across the top of the screen. */
private fun DrawScope.searchlight(now: Long) {
    if (now == 0L) return
    val period = 14_000_000_000L
    val phase = (now % period).toDouble() / period
    val x = size.width * (0.5f + 0.42f * kotlin.math.sin(2 * Math.PI * phase).toFloat())
    val y = size.height * (0.22f + 0.08f * kotlin.math.cos(4 * Math.PI * phase).toFloat())
    val r = size.minDimension * 0.42f
    drawCircle(Brush.radialGradient(listOf(Color(0x2AC8D4FF), Color(0x10C8D4FF), Color.Transparent), center = Offset(x, y), radius = r), r, Offset(x, y))
}

/** ED-209: a thin yellow-and-black hazard band along the bottom edge. */
private fun DrawScope.hazardBand() {
    val h = 4.dp.toPx()
    val top = size.height - h
    val stripe = 10.dp.toPx()
    drawRect(Color(0x99000000), Offset(0f, top), Size(size.width, h))
    clipRect(0f, top, size.width, size.height) {
        var x = -h
        while (x < size.width + h) {
            drawLine(Color(0x99FFC400), Offset(x, size.height), Offset(x + h, top), stripe / 2)
            x += stripe
        }
    }
}

/** The T-800's column of scrolling code down the right edge. */
private fun DrawScope.machineCode(paint: Paint, now: Long) {
    val lines = FilmHudText.MACHINE_CODE
    paint.textSize = 9.dp.toPx()
    paint.color = Color(0xFFFF4A3A).copy(alpha = 0.55f).toArgb()
    val lineHeight = paint.textSize * 1.35f
    val shown = (size.height * 0.5f / lineHeight).toInt().coerceAtLeast(1)
    val offset = ((now / 160_000_000L) % lines.size).toInt()
    val x = size.width - paint.textSize * 11
    val top = size.height * 0.25f
    drawIntoCanvas { canvas ->
        for (i in 0 until shown) {
            canvas.nativeCanvas.drawText(lines[(offset + i) % lines.size], x, top + i * lineHeight, paint)
        }
    }
}

/** RoboCop's visor scan: a bright band sweeps down the screen every few seconds. */
private fun DrawScope.visorSweep(now: Long) {
    if (now == 0L) return
    val period = 7_000_000_000L
    val sweep = 1_400_000_000L
    val t = now % period
    if (t > sweep) return
    val y = size.height * (t.toFloat() / sweep)
    val band = 26.dp.toPx()
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x4066CCFF), Color(0x9966CCFF), Color.Transparent), startY = y - band, endY = y + band / 3),
        topLeft = Offset(0f, y - band),
        size = Size(size.width, band * 1.4f)
    )
}

/**
 * Panel frame marks: RoboCop draws targeting brackets on the four corners; Terminator draws
 * short corner ticks like the T-800's framing marks; Back to the Future puts a steel rivet in
 * each corner like the DeLorean's bolted-on hardware.
 */
fun Modifier.hudFrame(style: HudStyle?, accent: Color): Modifier = when (style) {
    null, HudStyle.Matrix -> this
    HudStyle.Future -> drawWithContent {
        drawContent()
        val r = 3.dp.toPx()
        val inset = 7.dp.toPx()
        listOf(
            Offset(inset, inset), Offset(size.width - inset, inset),
            Offset(inset, size.height - inset), Offset(size.width - inset, size.height - inset)
        ).forEach { c ->
            drawCircle(Color(0xFF9AA2AA), r, c)
            drawCircle(Color(0xFF3A3F45), r * 0.45f, c)
        }
    }
    else -> drawWithContent {
        drawContent()
        val len = (if (style == HudStyle.RoboCop) 18.dp else 10.dp).toPx()
        val inset = 3.dp.toPx()
        val w = (if (style == HudStyle.RoboCop) 2.dp else 1.5.dp).toPx()
        val c = accent.copy(alpha = 0.9f)
        val l = inset
        val t = inset
        val r = size.width - inset
        val b = size.height - inset
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, y), Offset(x + dx * len, y), w, StrokeCap.Square)
            drawLine(c, Offset(x, y), Offset(x, y + dy * len), w, StrokeCap.Square)
        }
        corner(l, t, 1f, 1f)
        corner(r, t, -1f, 1f)
        corner(l, b, 1f, -1f)
        corner(r, b, -1f, -1f)
    }
}
