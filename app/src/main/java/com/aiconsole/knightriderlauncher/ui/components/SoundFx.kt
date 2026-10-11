package com.aiconsole.knightriderlauncher.ui.components

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
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
