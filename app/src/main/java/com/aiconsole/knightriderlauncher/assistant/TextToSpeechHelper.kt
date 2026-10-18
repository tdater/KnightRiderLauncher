package com.aiconsole.knightriderlauncher.assistant

import com.aiconsole.knightriderlauncher.ui.theme.ThemeState

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import com.aiconsole.knightriderlauncher.data.settings.VoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * KITT's voice: one app-wide speaker shared by the assistant and proactive alerts.
 *
 * With [VoiceEngine.Gemini] each line is synthesised by Gemini's neural speech (see
 * [GeminiSpeechClient]) in sentence chunks, so playback starts while the rest is generated.
 * [VoiceEngine.OnDevice] uses a free neural voice running on the head unit ([OnDeviceVoice]),
 * which also stands in for Gemini without a key or network once a voice is downloaded.
 * The device TTS engine takes over whenever those fail.
 * Everything is queued and played on the main thread.
 */
class TextToSpeechHelper(context: Context, private val settings: KittSettings, scope: CoroutineScope) {

    private val neural = GeminiSpeechClient(context.applicationContext, settings)

    /** The free on-device neural voice and its downloadable voices (see [LocalVoices]). */
    val onDevice = OnDeviceVoice(context.applicationContext)
    private val ducker = AudioDucker(context)
    private val main = CoroutineScope(scope.coroutineContext + Dispatchers.Main)

    @Volatile
    private var ready = false
    private var preferredVoice = settings.kittVoiceName.value
    private val localReady = CompletableDeferred<Boolean>()
    private val localDone = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private lateinit var tts: TextToSpeech

    // Main thread only.
    private val queue = ArrayDeque<Utterance>()
    private var worker: Job? = null

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private data class Utterance(val text: String, val urgent: Boolean, val deviceOnly: Boolean)

    init {
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            // Slightly lowered pitch and a measured rate: calmer than the engine's default.
            tts.setAudioAttributes(ducker.attributes)
            tts.setPitch(0.92f)
            tts.setSpeechRate(0.96f)
            applyVoice(preferredVoice)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finished(utteranceId)

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
            })
        }
        localReady.complete(ready)
    }

    private fun finished(utteranceId: String?) {
        utteranceId?.let { localDone.remove(it)?.complete(Unit) }
    }

    /** Offline-capable English device voices, for the fallback picker. Empty until the engine is ready. */
    fun englishVoices(): List<String> = if (!ready) emptyList() else runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired }
            .filterNot { it.features.orEmpty().contains("notInstalled") }
            .map(Voice::getName)
            .sorted()
    }.getOrDefault(emptyList())

    /** Selects the device TTS voice (the fallback when Gemini speech is on). */
    fun setVoice(name: String) {
        preferredVoice = name
        if (ready) applyVoice(name)
    }

    private fun applyVoice(name: String) {
        val voice = runCatching { tts.voices.orEmpty().firstOrNull { it.name == name } }.getOrNull()
        if (voice != null) tts.voice = voice else tts.language = Locale.getDefault()
    }

    /**
     * Queues [text]; [interrupt] drops whatever is playing or waiting first. [urgent] lines
     * (safety alerts) get a firmer delivery and fall back to the device voice much sooner.
     * [deviceOnly] skips Gemini, e.g. to preview the fallback voice.
     */
    fun speak(text: String, interrupt: Boolean = true, urgent: Boolean = false, deviceOnly: Boolean = false) {
        if (text.isBlank()) return
        main.launch {
            if (interrupt) haltPlayback()
            queue.addLast(Utterance(text, urgent, deviceOnly))
            if (worker?.isActive != true) worker = main.launch { drain() }
        }
    }

    fun stop() {
        main.launch { haltPlayback() }
    }

    fun shutdown() {
        main.launch {
            haltPlayback()
            tts.shutdown()
        }
    }

    private fun haltPlayback() {
        queue.clear()
        worker?.cancel()
        worker = null
        if (ready) tts.stop()
        _speaking.value = false
    }

    private suspend fun drain() {
        // Duck music/video for the whole queue so KITT is always heard.
        ducker.duck()
        try {
            while (true) {
                val next = queue.removeFirstOrNull() ?: break
                say(next)
            }
        } finally {
            _speaking.value = false
            ducker.release()
        }
    }

    private suspend fun say(utterance: Utterance) {
        val chunks = GeminiSpeechProtocol.chunks(GeminiSpeechProtocol.speakable(utterance.text))
        if (chunks.isEmpty()) return
        // Gemini when chosen and keyed, then the free on-device voice, then the device TTS.
        var spoken = if (!utterance.deviceOnly && useNeural()) sayNeural(chunks, utterance.urgent) else 0
        if (spoken < chunks.size && !utterance.deviceOnly && useOnDevice()) spoken += sayOnDevice(chunks.drop(spoken), utterance.urgent)
        if (spoken < chunks.size) sayLocal(chunks.drop(spoken))
    }

    private fun useNeural(): Boolean =
        settings.voiceEngine.value == VoiceEngine.Gemini && settings.geminiKey.isNotEmpty()

    /** The free offline voice: chosen, or standing in for Gemini; only once a voice is downloaded. */
    private fun useOnDevice(): Boolean =
        settings.voiceEngine.value != VoiceEngine.Device && onDevice.unavailable == null &&
            onDevice.isInstalled(LocalVoices.byId(settings.localVoice.value))

    /** Plays chunks through the on-device voice, synthesizing the next while one plays. Returns how many were spoken. */
    private suspend fun sayOnDevice(chunks: List<String>, urgent: Boolean): Int = supervisorScope {
        val voice = LocalVoices.byId(settings.localVoice.value)
        val speaker = settings.localVoiceSpeaker.value
        val film = filmCharacter()
        val speed = (film?.rate ?: 1f) * (if (urgent) 1.06f else 1f)
        // The characters' pitches are tuned for the device voice; a neural voice needs less.
        val pitch = film?.let { 1f + (it.pitch - 1f) * 0.7f } ?: 1f
        fun fetch(index: Int) = async(Dispatchers.IO) { onDevice.synthesize(voice, chunks[index], speaker, speed) }

        var next = fetch(0)
        for (index in chunks.indices) {
            val clip = try {
                next.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "On-device voice failed; using device voice: ${e.message}")
                null
            } ?: return@supervisorScope index
            if (index + 1 < chunks.size) next = fetch(index + 1)
            _speaking.value = true
            onDevice.play(clip, ducker.attributes, pitch)
        }
        chunks.size
    }

    /** Plays chunks through Gemini speech, prefetching the next while one plays. Returns how many were spoken. */
    private suspend fun sayNeural(chunks: List<String>, urgent: Boolean): Int = supervisorScope {
        val film = filmCharacter()
        val voice = film?.geminiVoice ?: settings.geminiVoice.value
        val baseStyle = film?.style ?: GeminiSpeechProtocol.STYLE
        val style = if (urgent) "$baseStyle For this line: firm, focused and urgent, still in character." else baseStyle
        val timeout = if (urgent) URGENT_TIMEOUT_MS else CHUNK_TIMEOUT_MS
        fun fetch(index: Int) = async(Dispatchers.IO) { neural.synthesize(chunks[index], voice, style) }

        var next = fetch(0)
        for (index in chunks.indices) {
            val file = try {
                withTimeout(timeout) { next.await() }
            } catch (e: TimeoutCancellationException) {
                next.cancel()
                Log.w(TAG, "Gemini speech timed out; using device voice")
                return@supervisorScope index
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini speech failed; using device voice: ${e.message}")
                return@supervisorScope index
            }
            if (index + 1 < chunks.size) next = fetch(index + 1)
            _speaking.value = true
            try {
                play(file)
            } catch (e: IOException) {
                Log.w(TAG, "Playback failed; using device voice", e)
                next.cancel()
                return@supervisorScope index
            }
        }
        chunks.size
    }

    private suspend fun sayLocal(chunks: List<String>) {
        val ok = withTimeoutOrNull(LOCAL_INIT_TIMEOUT_MS) { localReady.await() } ?: false
        if (!ok) return // No TTS engine installed; the answer is still shown on screen.
        // Film characters get their own pitch and pace on the device voice too.
        val film = filmCharacter()
        tts.setPitch(film?.pitch?.times(0.92f) ?: 0.92f)
        tts.setSpeechRate(film?.rate?.times(0.96f) ?: 0.96f)
        for (chunk in chunks) {
            val id = "kitt-${System.nanoTime()}"
            val done = CompletableDeferred<Unit>()
            localDone[id] = done
            try {
                _speaking.value = true
                if (tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) return
                done.await()
            } finally {
                localDone.remove(id)
            }
        }
    }

    private suspend fun play(file: File) = suspendCancellableCoroutine { continuation ->
        val player = MediaPlayer()
        var released = false
        fun release() {
            if (!released) {
                released = true
                player.release()
            }
        }
        player.setAudioAttributes(ducker.attributes)
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener {
            release()
            if (continuation.isActive) continuation.resume(Unit)
        }
        player.setOnErrorListener { _, what, extra ->
            release()
            if (continuation.isActive) continuation.resumeWithException(IOException("MediaPlayer error $what/$extra"))
            true
        }
        // Cancellation comes from haltPlayback on the main thread, same as the player callbacks.
        continuation.invokeOnCancellation { release() }
        try {
            player.setDataSource(file.path)
            player.prepareAsync()
        } catch (e: Exception) {
            release()
            continuation.resumeWithException(IOException("Couldn't play ${file.name}", e))
        }
    }

    private fun filmCharacter(): FilmCharacter? =
        if (settings.filmCharacters.value) ThemeState.filmCharacter else null

    private companion object {
        const val TAG = "KittVoice"
        const val CHUNK_TIMEOUT_MS = 10_000L
        const val URGENT_TIMEOUT_MS = 3_000L
        const val LOCAL_INIT_TIMEOUT_MS = 5_000L
    }
}
