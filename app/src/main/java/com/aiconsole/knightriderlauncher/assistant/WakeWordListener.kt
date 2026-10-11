package com.aiconsole.knightriderlauncher.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.aiconsole.knightriderlauncher.data.settings.KittSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Pure rules for [WakeWordListener] (unit-tested). */
object WakeWordRules {
    /**
     * Listen only when everything allows it. Never while music or any other media is playing:
     * the recognizer can't hear "Hey KITT" over it anyway, and restarting a session every few
     * seconds made head units chime, duck or interrupt the music over and over.
     */
    fun shouldListen(
        enabled: Boolean,
        visible: Boolean,
        suspended: Boolean,
        kittSpeaking: Boolean,
        micBlocked: Boolean,
        mediaPlaying: Boolean,
        micPermission: Boolean
    ): Boolean = enabled && visible && !suspended && !kittSpeaking && !micBlocked && !mediaPlaying && micPermission

    /** Pause before the next session: a short gap after a quiet one, growing back-off after errors. */
    fun restartDelayMs(error: Boolean, failures: Int): Long =
        if (error) (500L * failures).coerceIn(500L, 15_000L) else QUIET_GAP_MS

    /** How often to look again while media plays (no callback says when it stops). */
    const val MEDIA_RECHECK_MS = 5_000L
    private const val QUIET_GAP_MS = 1_000L
}

/**
 * "Hey KITT": keeps a speech recognizer listening in short sessions while the launcher is in
 * front and idle, and fires [wakes] when it hears the phrase. Android has no public hotword API
 * for third-party apps, so this uses the regular recognizer: an on-device one (Android 12+) keeps
 * it offline; some recognizers chime each time a session starts. It stands down while any app is
 * playing media (see [WakeWordRules.shouldListen]). Main thread only.
 */
class WakeWordListener(
    context: Context,
    private val settings: KittSettings,
    private val voice: TextToSpeechHelper,
    scope: CoroutineScope
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private var recognizer: SpeechRecognizer? = null
    private var visible = false
    private var suspended = false
    private var speaking = false
    private var enabled = settings.wakeWord.value
    private var listening = false
    private var failures = 0

    private val _wakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val wakes: SharedFlow<Unit> = _wakes.asSharedFlow()

    init {
        // A takeover while listening for "Hey KITT" would repeat forever: turn the wake word off.
        scope.launch(Dispatchers.Main) {
            MicGuard.stops.collect {
                stopAll()
                settings.wakeWord.set(false)
            }
        }
        scope.launch(Dispatchers.Main) {
            combine(settings.wakeWord.state, voice.speaking) { on, talking -> on to talking }.collect { (on, talking) ->
                enabled = on
                speaking = talking
                update()
            }
        }
    }

    /** The launcher is (not) in front. */
    fun setVisible(value: Boolean) {
        visible = value
        update()
    }

    /** Pause while the assistant or another overlay is using the microphone or the screen. */
    fun setSuspended(value: Boolean) {
        suspended = value
        update()
    }

    private fun mediaPlaying(): Boolean = audio?.isMusicActive == true

    private fun shouldListen(): Boolean = WakeWordRules.shouldListen(
        enabled = enabled,
        visible = visible,
        suspended = suspended,
        kittSpeaking = speaking,
        micBlocked = MicGuard.autoListenBlocked(),
        mediaPlaying = mediaPlaying(),
        micPermission = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )

    private fun update() {
        main.removeCallbacksAndMessages(null)
        if (shouldListen()) {
            if (!listening) startSession()
        } else {
            stopAll()
            waitForMediaToStop()
        }
    }

    /** Media is the only blocker nothing tells us about: look again every few seconds until it stops. */
    private fun waitForMediaToStop() {
        if (enabled && visible && !suspended && mediaPlaying()) {
            main.postDelayed({ update() }, WakeWordRules.MEDIA_RECHECK_MS)
        }
    }

    private fun startSession() {
        if (!shouldListen()) return
        val target = SpeechRecognizers.resolve(appContext, settings.speechRecognizer.value)
        if (target == RecognizerTarget.Unavailable) {
            Log.w(TAG, "No safe speech recognizer; \"Hey KITT\" stays off")
            return
        }
        val active = recognizer ?: SpeechRecognizers.create(appContext, target).also {
            it.setRecognitionListener(this)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        listening = true
        MicGuard.listeningStarted()
        try {
            active.startListening(intent)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Wake word listener couldn't start", e)
            listening = false
            scheduleRestart(error = true)
        }
    }

    private fun stopAll() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun scheduleRestart(error: Boolean) {
        listening = false
        failures = if (error) failures + 1 else 0
        // Back off on repeated errors (e.g. no network recognizer) so the mic isn't hammered.
        main.postDelayed({ update() }, WakeWordRules.restartDelayMs(error, failures))
    }

    private fun check(bundle: Bundle?): Boolean {
        val heard = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        if (heard.none(WakeWord::matches)) return false
        stopAll() // Free the microphone for the assistant.
        _wakes.tryEmit(Unit)
        return true
    }

    override fun onPartialResults(partialResults: Bundle?) {
        check(partialResults)
    }

    override fun onResults(results: Bundle?) {
        if (!check(results)) scheduleRestart(error = false)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(error = false)
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stopAll()
            else -> {
                stopAll()
                scheduleRestart(error = true)
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        failures = 0
    }

    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private companion object {
        const val TAG = "WakeWord"
    }
}
