package com.aiconsole.knightriderlauncher.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.aiconsole.knightriderlauncher.ui.theme.HudStyle
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Optional KITT-style sound effects, synthesized on the fly (original sounds, no recordings
 * from the series): the scanner "whoosh" when the launcher starts and a rising "turbo boost"
 * at full throttle. Off unless the driver turns them on in KITT Config.
 */
object SoundFx {
    @Volatile
    var enabled = false

    private const val RATE = 22_050
    private val whooshPcm by lazy { whoosh() }
    private val turboPcm by lazy { turbo() }

    fun scanner() = play { whooshPcm }

    fun turboBoost() = play { turboPcm }

    private val filmPcm = HashMap<HudStyle, ShortArray>()

    /** A film theme's start-up sound (see [FilmSounds]); played at launch and when switching. */
    fun film(style: HudStyle) = play { synchronized(filmPcm) { filmPcm.getOrPut(style) { FilmSounds.forFilm(style, RATE) } } }

    private fun play(pcm: () -> ShortArray) {
        if (!enabled) return
        Thread({
            try {
                val data = pcm()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(data.size * 2)
                    .build()
                track.write(data, 0, data.size)
                track.play()
                Thread.sleep(data.size * 1000L / RATE + 100)
                track.release()
            } catch (e: Exception) {
                Log.w("SoundFx", "Couldn't play a sound effect", e)
            }
        }, "kitt-sfx").apply { isDaemon = true }.start()
    }

    /** Filtered noise that sweeps up and back down, like the scanner passing by. */
    private fun whoosh(): ShortArray {
        val n = (RATE * 1.1).toInt()
        val out = ShortArray(n)
        val random = Random(7)
        var low = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val cutoff = 0.02 + 0.25 * sin(PI * t) // Filter opens and closes.
            low += cutoff * (random.nextDouble(-1.0, 1.0) - low)
            val hum = 0.25 * sin(2 * PI * (180 + 260 * sin(PI * t)) * i / RATE)
            val env = sin(PI * t).let { it * it }
            out[i] = ((low * 2.2 + hum) * env * 9_000).toInt().coerceIn(-32_000, 32_000).toShort()
        }
        return out
    }

    /** A rising whine with a little grit: KITT's turbo boost. */
    private fun turbo(): ShortArray {
        val n = (RATE * 1.4).toInt()
        val out = ShortArray(n)
        val random = Random(11)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val freq = 220 + 1_400 * t * t
            phase += 2 * PI * freq / RATE
            val tone = sin(phase) + 0.35 * sin(2 * phase) + 0.15 * random.nextDouble(-1.0, 1.0)
            val env = (t * 6).coerceAtMost(1.0) * (1 - t).coerceAtLeast(0.0).let { kotlin.math.sqrt(it) }
            out[i] = (tone * env * 8_000).toInt().coerceIn(-32_000, 32_000).toShort()
        }
        return out
    }
}

/**
 * The film themes' start-up sounds, synthesized (original sounds, nothing recorded or
 * transcribed from the films). Plain Kotlin, unit-tested:
 * - Terminator: two heavy metallic hits, like a machine's footsteps;
 * - RoboCop: a servo whirring up and settling, with gear ticks;
 * - The Matrix: a falling cascade of digital blips;
 * - Back to the Future: an electrical crackle and a low boom.
 */
object FilmSounds {

    fun forFilm(style: HudStyle, rate: Int): ShortArray = when (style) {
        HudStyle.Terminator -> metalHits(rate)
        HudStyle.RoboCop -> servo(rate)
        HudStyle.Matrix -> blips(rate)
        HudStyle.Future -> crackleBoom(rate)
    }

    private fun pcm(seconds: Double, rate: Int, sample: (i: Int, t: Double) -> Double): ShortArray {
        val n = (rate * seconds).toInt()
        return ShortArray(n) { i -> (sample(i, i.toDouble() / rate) * 9_000).toInt().coerceIn(-32_000, 32_000).toShort() }
    }

    private fun metalHits(rate: Int): ShortArray {
        val random = Random(1984)
        val partials = doubleArrayOf(55.0, 82.0, 147.0, 233.0, 311.0)
        return pcm(1.1, rate) { _, t ->
            listOf(0.0, 0.42).sumOf { start ->
                val d = t - start
                if (d < 0) 0.0 else {
                    val ring = partials.withIndex().sumOf { (k, f) -> sin(2 * PI * f * d) * exp(-d * (4.0 + k * 3)) / (k + 1) }
                    val click = if (d < 0.012) random.nextDouble(-1.0, 1.0) * (1 - d / 0.012) else 0.0
                    ring * 1.6 + click * 0.8
                }
            }
        }
    }

    private fun servo(rate: Int): ShortArray {
        var phase = 0.0
        val length = 0.95
        return pcm(length, rate) { _, t ->
            val p = t / length
            val freq = if (p < 0.55) 300 + 340 * (p / 0.55) else 640 - 220 * ((p - 0.55) / 0.45)
            phase += 2 * PI * freq / rate
            val tone = sin(phase) + 0.3 * sin(2 * phase) + 0.15 * sin(3 * phase)
            val tick = if ((t % 0.11) < 0.004) 0.9 else 0.0
            val env = (p * 10).coerceAtMost(1.0) * (1 - p).coerceAtLeast(0.0)
            (tone * 0.7 + tick) * env
        }
    }

    private fun blips(rate: Int): ShortArray {
        val random = Random(1999)
        val count = 12
        val freqs = DoubleArray(count) { k -> 2_600 - k * 140 + random.nextDouble(-120.0, 120.0) }
        val slot = 0.07
        return pcm(count * slot + 0.1, rate) { _, t ->
            val k = (t / slot).toInt()
            if (k >= count) 0.0 else {
                val d = t - k * slot
                if (d > 0.045) 0.0 else sin(2 * PI * freqs[k] * d) * (d / 0.004).coerceAtMost(1.0) * exp(-d * 60) * 1.4
            }
        }
    }

    private fun crackleBoom(rate: Int): ShortArray {
        val random = Random(1985)
        var boomPhase = 0.0
        var low = 0.0
        return pcm(1.3, rate) { _, t ->
            val crackle = if (t < 0.55 && random.nextDouble() < 0.06) random.nextDouble(-1.0, 1.0) * (1 - t / 0.55) * 1.6 else 0.0
            val d = t - 0.5
            val boom = if (d < 0) 0.0 else {
                boomPhase += 2 * PI * (110 * exp(-d * 2.2) + 35) / rate
                low += 0.05 * (random.nextDouble(-1.0, 1.0) - low)
                (sin(boomPhase) * 1.4 + low * 3) * exp(-d * 3.2) * (d / 0.01).coerceAtMost(1.0)
            }
            crackle + boom
        }
    }
}
