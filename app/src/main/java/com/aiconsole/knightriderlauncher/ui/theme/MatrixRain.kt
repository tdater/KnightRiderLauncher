package com.aiconsole.knightriderlauncher.ui.theme

import android.graphics.Bitmap
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
import com.aiconsole.knightriderlauncher.browser.WebMedia
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

/**
 * The Matrix theme background: multicolour digital rain. Like the TruckAPK console it paints a
 * translucent black wash over the previous frame so each column leaves a fading trail. It
 * renders at half resolution into one reused bitmap at ~28 fps, and stops with the frame
 * clock whenever the launcher isn't drawing.
 */
@Composable
fun MatrixRain(modifier: Modifier = Modifier) {
    val rain = remember { RainField() }
    var tick by remember { mutableLongStateOf(0L) }
    // Frozen while a web page is on screen: a full-screen layer redrawing under it made the page
    // composite every frame too.
    val paused = WebMedia.pagesOnScreen > 0
    LaunchedEffect(rain, paused) {
        if (paused) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - last >= FRAME_NANOS) {
                    last = now
                    tick = now
                }
            }
        }
    }
    Canvas(modifier) {
        val w = (size.width / SCALE).toInt()
        val h = (size.height / SCALE).toInt()
        if (w <= 0 || h <= 0) return@Canvas
        if (tick != 0L) rain.step(w, h)
        rain.bitmap?.let {
            drawImage(
                image = it.asImageBitmap(),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                dstOffset = IntOffset.Zero,
                filterQuality = FilterQuality.Low
            )
        }
    }
}

private const val SCALE = 2f
private const val FRAME_NANOS = 35_000_000L
private const val FONT_PX = 8f
private const val GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%&?"
private val HUES = floatArrayOf(122f, 190f, 280f, 330f, 45f)

private class RainField {
    var bitmap: Bitmap? = null
        private set
    private var canvas: android.graphics.Canvas? = null
    private var dropY = FloatArray(0)
    private var speed = FloatArray(0)
    private var color = IntArray(0)
    private val fade = Paint().apply { color = android.graphics.Color.argb(23, 0, 0, 0) }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = FONT_PX
    }

    fun step(width: Int, height: Int) {
        if (bitmap?.width != width || bitmap?.height != height) reset(width, height)
        val c = canvas ?: return
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fade)
        for (i in dropY.indices) {
            glyph.color = color[i]
            c.drawText(GLYPHS[Random.nextInt(GLYPHS.length)].toString(), i * FONT_PX, dropY[i], glyph)
            dropY[i] += speed[i]
            if (dropY[i] > height) {
                dropY[i] = -Random.nextFloat() * 60f
                color[i] = randomColor()
            }
        }
    }

    private fun reset(width: Int, height: Int) {
        bitmap?.recycle()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap = bmp
        canvas = android.graphics.Canvas(bmp).apply { drawColor(android.graphics.Color.BLACK) }
        val columns = (width / FONT_PX).toInt() + 1
        dropY = FloatArray(columns) { -Random.nextFloat() * height }
        speed = FloatArray(columns) { (1.3f + Random.nextFloat() * 3.5f) / SCALE }
        color = IntArray(columns) { randomColor() }
    }

    private fun randomColor(): Int {
        // hsl(h, 100%, 58%) at 78% alpha, as in the original console.
        val hsv = floatArrayOf(HUES[Random.nextInt(HUES.size)], 0.84f, 1f)
        return android.graphics.Color.HSVToColor((0.78f * 255).toInt(), hsv)
    }
}
