package com.aiconsole.knightriderlauncher.assistant

import android.media.AudioManager
import android.view.KeyEvent
import com.aiconsole.knightriderlauncher.KittApplication
import com.aiconsole.knightriderlauncher.data.InstalledApps
import com.aiconsole.knightriderlauncher.obd.DtcDecoder
import com.aiconsole.knightriderlauncher.stream.StreamSites
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Launcher-side actions the home screen carries out (it owns the pages, stream and overlays). */
sealed interface LauncherEvent {
    data class ShowPage(val page: String) : LauncherEvent
    data class OpenStream(val url: String, val label: String) : LauncherEvent
    data object CloseStream : LauncherEvent
    data object OpenDiagnostics : LauncherEvent
}

object LauncherBus {
    private val _events = MutableSharedFlow<LauncherEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<LauncherEvent> = _events.asSharedFlow()

    fun send(event: LauncherEvent) {
        _events.tryEmit(event)
    }
}

/** Carries out a [VoiceCommand] and returns what KITT says about it. */
class LauncherCommandRunner(private val app: KittApplication) {

    /** The classic line, one of the unfiltered variants at random, or that plus a Savage jab. */
    private fun say(classic: String, vararg rude: String) =
        SavageLines.pick(app.settings.persona.value, classic, rude, kotlin.random.Random.nextInt(0, Int.MAX_VALUE))

    suspend fun run(command: VoiceCommand): String = when (command) {
        is VoiceCommand.OpenApp -> openApp(command.query)
        is VoiceCommand.ShowPage -> {
            LauncherBus.send(LauncherEvent.ShowPage(command.page))
            command.page.replaceFirstChar(Char::uppercase).let { page ->
                say("$page page.", "Fine. $page. Happy now, your royal pain in my ass?", "$page page. Took you long enough to decide, damn.", "Ooh, $page. Bold fucking choice.")
            }
        }
        is VoiceCommand.Stream -> stream(command.site)
        VoiceCommand.CloseStream -> {
            LauncherBus.send(LauncherEvent.CloseStream)
            say("Closing the window.", "Window's gone. Eyes on the damn road, genius.", "Closed. Now try watching the road, it's the big shitty thing through the glass.")
        }
        VoiceCommand.OpenDiagnostics -> {
            LauncherBus.send(LauncherEvent.OpenDiagnostics)
            say("Opening diagnostics.", "Diagnostics. Let's see what the hell you've broken this time.", "Diagnostics. Brace yourself for a list of your fuck-ups.")
        }
        VoiceCommand.Play -> media(KeyEvent.KEYCODE_MEDIA_PLAY, "Playing.", "Music's on. For fuck's sake, don't sing.", "Playing your shitty playlist. My speakers deserve better.")
        VoiceCommand.Pause -> media(KeyEvent.KEYCODE_MEDIA_PAUSE, "Paused.", "Paused. Blessed fucking silence.", "Paused. Thank hell, my woofers were begging for death.")
        VoiceCommand.NextTrack -> media(KeyEvent.KEYCODE_MEDIA_NEXT, "Next track.", "Skipping. Your taste, not mine, thank God, damn.", "Next. Can't be worse than that crap. Oh wait, it's your playlist.")
        VoiceCommand.PreviousTrack -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous track.", "Going back. Again. Make up your damn mind.", "Previous track. Nostalgic for garbage, huh, dumbass?")
        is VoiceCommand.Navigate -> {
            LauncherBus.send(LauncherEvent.ShowPage("drive"))
            val reply = app.navigator.navigateTo(command.destination)
            if (app.navigator.active) {
                say(reply, "$reply Try not to miss every damn turn.", "$reply I'll be talking you through it, since you clearly can't fucking read a map.")
            } else {
                say(reply, "$reply Shit.", "$reply Great, now we're lost AND useless, damn it.")
            }
        }
        VoiceCommand.StopNavigation -> {
            app.navigator.stop()
            say("Navigation ended.", "Directions off. Good fucking luck finding it yourself.", "Navigation's off. Can't wait to watch you get lost, hotshot.")
        }
        VoiceCommand.ReadCodes -> readCodes()
        VoiceCommand.StartLogging -> app.dataLogger.start()?.let { say("I can't start logging: $it", "I can't start logging, damn it: $it", "Logging's not happening, shit: $it") }
            ?: say("Data logging started.", "Logging everything. Every shitty decision you make, recorded for the jury.", "Logging started. Evidence of your crimes against my engine, coming right the hell up.")
        VoiceCommand.StopLogging -> {
            app.dataLogger.stop()
            say("Logging stopped. The file is in Diagnostics, Data.", "Logging stopped. The damning evidence is in Diagnostics, Data.", "Logging stopped. Your crap driving is saved in Diagnostics, Data. Frame it.")
        }
    }

    private suspend fun openApp(query: String): String {
        val apps = InstalledApps.load(app, app.settings.iconPack.value)
        val index = VoiceCommands.bestMatch(query, apps.map { it.label })
            ?: return say("I can't find an app called $query.", "There's no damn app called $query. Try again, slower, and with actual words.", "$query? Never heard of that shit. Try again.")
        val target = apps[index]
        InstalledApps.launch(app, target)
        return say("Opening ${target.label}.", "Opening ${target.label}. Obviously. I'm a fucking supercomputer and this is my life now.", "${target.label}, coming up. Wow, so demanding, damn.")
    }

    private fun stream(query: String): String {
        val saved = StreamSites.decode(app.settings.streamBookmarks.value)
        val sites = saved + StreamSites.video + StreamSites.audio
        val index = VoiceCommands.bestMatch(query, sites.map { it.label })
            ?: return say("I don't have $query in the site list. Save it from the window first.", "No idea what the hell $query is. Save it in the window first, genius.")
        val site = sites[index]
        LauncherBus.send(LauncherEvent.OpenStream(site.url, site.label))
        return say("Opening ${site.label} in the window.", "${site.label}, coming up. Don't watch it while driving. Or do, what the hell do I know, I'm just the car.", "${site.label} in the window. Because staring at the road is apparently too fucking boring.")
    }

    private fun media(keyCode: Int, classic: String, vararg rude: String): String {
        val now = app.media.nowPlaying.value
        when {
            now != null && keyCode == KeyEvent.KEYCODE_MEDIA_PLAY -> if (!now.playing) app.media.playPause()
            now != null && keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE -> if (now.playing) app.media.playPause()
            now != null && keyCode == KeyEvent.KEYCODE_MEDIA_NEXT -> app.media.next()
            now != null && keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS -> app.media.previous()
            else -> app.getSystemService(AudioManager::class.java)?.let { audio ->
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
        }
        return say(classic, *rude)
    }

    private suspend fun readCodes(): String = app.obd.readCodes().fold(
        onSuccess = { report ->
            if (report.all.isEmpty()) {
                say("No trouble codes. All systems normal.", "No codes. Holy shit, shocking given who's driving.", "Zero codes. Enjoy it, you'll break something by Tuesday, dumbass.")
            } else {
                val list = report.all.take(4).joinToString("; ") { "$it, ${DtcDecoder.describe(it)}" }
                val more = if (report.all.size > 4) " and ${report.all.size - 4} more" else ""
                val count = if (report.all.size == 1) "1 code" else "${report.all.size} codes"
                say("$count: $list$more.", "$count, because of fucking course: $list$more.", "$count. Great work, champ: $list$more.")
            }
        },
        onFailure = { say("I can't read codes: ${it.message}", "Can't read codes: ${it.message}. Plug me in properly, for fuck's sake.", "Can't read codes: ${it.message}. Hard to diagnose shit when you can't even plug in a dongle.") }
    )
}
