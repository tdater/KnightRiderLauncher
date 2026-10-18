package com.aiconsole.knightriderlauncher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.assistant.LocalVoices
import com.aiconsole.knightriderlauncher.ui.components.ChoiceRow
import com.aiconsole.knightriderlauncher.ui.components.HelpText
import com.aiconsole.knightriderlauncher.ui.components.KittButton
import com.aiconsole.knightriderlauncher.ui.theme.AlertRed
import com.aiconsole.knightriderlauncher.ui.theme.DashAmber
import com.aiconsole.knightriderlauncher.ui.theme.DashGrey
import com.aiconsole.knightriderlauncher.ui.theme.DashWhite
import com.aiconsole.knightriderlauncher.ui.theme.LedGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

private const val VOICE_TEST = "KITT online. All systems are functioning within normal parameters."

/**
 * The free on-device voice: download a voice (once, about 20 MB), pick it, choose a speaker for
 * multi-voice packs, test it, or delete it to free space.
 */
@Composable
fun OnDeviceVoiceSettings(app: KittApplication) {
    val scope = rememberCoroutineScope()
    val engine = app.kittVoice.onDevice
    val installed by engine.installed.collectAsState()
    val download by engine.download.collectAsState()
    val chosenId by app.settings.localVoice.state.collectAsState()
    val speaker by app.settings.localVoiceSpeaker.state.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val chosen = LocalVoices.byId(chosenId)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HelpText(
            "Natural neural voices that run on the head unit itself: free, no API key, and no internet once a voice is " +
                "downloaded. Download one (about 20 MB), tap USE, then TEST. Film characters keep their own pitch and pace."
        )
        engine.unavailable?.let { HelpText("This head unit can't run the on-device voice ($it). KITT uses the device voice.", color = AlertRed) }
        LocalVoices.ALL.forEach { voice ->
            val have = voice.id in installed
            val busy = download?.first == voice.id
            val inUse = have && voice.id == chosen.id
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    voice.label + "  · ${voice.sizeMb} MB" + if (inUse) "  · IN USE" else "",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (inUse) LedGreen else DashWhite,
                    maxLines = 2
                )
                when {
                    busy -> Text("${download?.second ?: 0}%", style = MaterialTheme.typography.labelLarge, color = DashAmber)
                    !have -> KittButton("Download", {
                        message = null
                        scope.launch {
                            message = try {
                                engine.install(voice)
                                app.settings.localVoice.set(voice.id)
                                app.settings.localVoiceSpeaker.set(voice.defaultSpeaker)
                                app.kittVoice.speak(VOICE_TEST)
                                "${voice.label.substringBefore(" ·")} is ready."
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: IOException) {
                                "Download failed: ${e.message}. Check the internet connection and try again."
                            }
                        }
                    }, Modifier.height(40.dp).width(112.dp), DashAmber, enabled = download == null)
                    else -> {
                        KittButton("Use", {
                            app.settings.localVoice.set(voice.id)
                            app.settings.localVoiceSpeaker.set(voice.defaultSpeaker)
                            app.kittVoice.speak(VOICE_TEST)
                        }, Modifier.height(40.dp).width(64.dp), LedGreen, active = inUse)
                        KittButton("Delete", { scope.launch { engine.remove(voice) } }, Modifier.height(40.dp).width(80.dp), DashGrey)
                    }
                }
            }
        }
        if (chosen.speakers.isNotEmpty() && chosen.id in installed) {
            Text("SPEAKER", style = MaterialTheme.typography.labelMedium, color = DashGrey)
            ChoiceRow(chosen.speakers.indices.toList(), LocalVoices.speaker(chosen, speaker), { chosen.speakers[it] }, {
                app.settings.localVoiceSpeaker.set(it)
                app.kittVoice.speak(VOICE_TEST)
            })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KittButton("Test voice", { app.kittVoice.speak(VOICE_TEST) }, Modifier.height(40.dp).width(120.dp), LedGreen, enabled = chosen.id in installed)
        }
        if (chosen.heavy) HelpText("Kokoro sounds the most natural but takes longer to start each sentence on slower head units.", color = DashAmber)
        message?.let { HelpText(it, color = DashAmber) }
    }
}
