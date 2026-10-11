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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Text the film HUD themes show in the status strip, panels, screensaver and intros. */
object FilmHudText {

    /** Readouts in the style of the T-800's vision overlays. */
    val TERMINATOR = listOf(
        "SCAN MODE 43984",
        "ANALYSIS: COMPLETE",
        "THREAT ASSESSMENT: NONE",
        "SYSTEMS: NOMINAL",
        "MATCH: VEHICLE PROFILE",
        "PRIORITY: MISSION"
    )

    /** RoboCop's published prime directives; the fourth stays classified, as in the film. */
    val ROBOCOP = listOf(
        "DIRECTIVE 1: SERVE THE PUBLIC TRUST",
        "DIRECTIVE 2: PROTECT THE INNOCENT",
        "DIRECTIVE 3: UPHOLD THE LAW",
        "DIRECTIVE 4: [CLASSIFIED]"
    )

    /** Short terminal lines in the style of the film's opening messages. */
    val MATRIX = listOf(
        "WAKE UP, NEO...",
        "THE MATRIX HAS YOU...",
        "FOLLOW THE WHITE RABBIT.",
        "KNOCK, KNOCK."
    )

    /** The DeLorean's dash, in the film's own terms. */
    val FUTURE = listOf(
        "TIME CIRCUITS: ON",
        "FLUX CAPACITOR: FLUXING",
        "1.21 GIGAWATTS: STANDBY",
        "TEMPORAL DISPLACEMENT: READY"
    )

    fun lines(style: HudStyle): List<String> = when (style) {
        HudStyle.Terminator -> TERMINATOR
        HudStyle.RoboCop -> ROBOCOP
        HudStyle.Matrix -> MATRIX
        HudStyle.Future -> FUTURE
    }

    /** Brand line for the status strip. */
    fun brand(style: HudStyle): String = when (style) {
        HudStyle.Terminator -> "CYBERDYNE 101"
        HudStyle.RoboCop -> "OCP · METRO WEST"
        HudStyle.Matrix -> "NEBUCHADNEZZAR · OPERATOR"
        HudStyle.Future -> "DMC-12 · TIME CIRCUITS"
    }

    /** What the alert banner calls KITT's messages in each film. */
    fun alertTitle(style: HudStyle?): String? = when (style) {
        HudStyle.Terminator -> "▌PRIORITY TRANSMISSION"
        HudStyle.RoboCop -> "[ OCP ADVISORY ]"
        HudStyle.Matrix -> "> INCOMING_"
        HudStyle.Future -> "⚡ TIME CIRCUITS"
        null -> null
    }

    /**
     * The boot sequence each film theme shows when it comes on: original lines in the style of
     * each film's machines, not dialogue from them.
     */
    fun intro(style: HudStyle): List<String> = when (style) {
        HudStyle.Terminator -> listOf(
            "CYBERDYNE SYSTEMS MODEL 101",
            "NEURAL NET PROCESSOR: ONLINE",
            "VISION OVERLAY: ACTIVE",
            "SCANNING VEHICLE ............ OK",
            "PRIMARY OBJECTIVE: DRIVE"
        )
        HudStyle.RoboCop -> listOf(
            "OCP CRIME PREVENTION SYSTEM",
            "VISOR CALIBRATION ........... OK",
            "TARGETING ................... OK",
            "DIRECTIVES LOADED: 4",
            "STATUS: ON PATROL"
        )
        HudStyle.Matrix -> listOf(
            "Wake up...",
            "The vehicle has you...",
            "Follow the white rabbit.",
            "Knock, knock."
        )
        HudStyle.Future -> listOf(
            "TIME CIRCUITS ON",
            "FLUX CAPACITOR ........ FLUXING",
            "DESTINATION TIME SET",
            "1.21 GIGAWATTS ......... READY",
            "WHERE WE'RE DRIVING, WE STILL NEED ROADS"
        )
    }

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

/**
 * Full-screen HUD layer drawn over the launcher (never intercepts touches): CRT scanlines, plus a
 * red vision vignette with scrolling machine code (Terminator), a cold visor glow with a sweeping
 * scan bar (RoboCop), green CRT bloom (The Matrix), or a stainless-steel edge sheen (Back to
 * the Future). The moving parts stand still in lite mode.
 */
@Composable
fun FilmHudOverlay(style: HudStyle, modifier: Modifier = Modifier) {
    val animate = !LocalLiteMode.current && (style == HudStyle.Terminator || style == HudStyle.RoboCop)
    var now by remember { mutableLongStateOf(0L) }
    if (animate) {
        LaunchedEffect(style) {
            var last = 0L
            // ~12 fps for the code column, ~30 fps for the visor sweep: enough to read as motion.
            val step = if (style == HudStyle.Terminator) 80_000_000L else 33_000_000L
            while (true) withFrameNanos {
                if (it - last >= step) {
                    last = it
                    now = it
                }
            }
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    Canvas(modifier) {
        if (style != HudStyle.Future) {
            val pitch = 3.dp.toPx()
            var y = 0f
            val line = Color.Black.copy(alpha = if (style == HudStyle.Terminator) 0.22f else 0.16f)
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
                y += pitch
            }
        }
        when (style) {
            HudStyle.Matrix -> drawRect(
                // Green phosphor bloom at the edges of an old monochrome CRT.
                Brush.radialGradient(listOf(Color.Transparent, Color(0x3300FF41)), center = center, radius = size.maxDimension * 0.8f)
            )
            HudStyle.Terminator -> {
                drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0x55FF1A0A)), center = center, radius = size.maxDimension * 0.75f))
                machineCode(paint, now)
            }
            HudStyle.RoboCop -> {
                val glow = Color(0x3366CCFF)
                drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), startY = 0f, endY = size.height * 0.12f))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), startY = size.height * 0.88f, endY = size.height))
                visorSweep(now)
            }
            HudStyle.Future -> {
                // Brushed-steel sheen along the edges, like the DeLorean's panels.
                val steel = Color(0x22C8D0D8)
                drawRect(Brush.horizontalGradient(listOf(steel, Color.Transparent), startX = 0f, endX = size.width * 0.06f))
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, steel), startX = size.width * 0.94f, endX = size.width))
            }
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
