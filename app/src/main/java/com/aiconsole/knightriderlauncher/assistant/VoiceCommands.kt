package com.aiconsole.knightriderlauncher.assistant

/** Something KITT can do on the launcher directly, without asking the AI. */
sealed interface VoiceCommand {
    data class OpenApp(val query: String) : VoiceCommand
    data class ShowPage(val page: String) : VoiceCommand
    data class Stream(val site: String) : VoiceCommand
    data object CloseStream : VoiceCommand
    data object Play : VoiceCommand
    data object Pause : VoiceCommand
    data object NextTrack : VoiceCommand
    data object PreviousTrack : VoiceCommand
    data object ReadCodes : VoiceCommand
    data object StartLogging : VoiceCommand
    data object StopLogging : VoiceCommand
    data object OpenDiagnostics : VoiceCommand
    data class Navigate(val destination: String) : VoiceCommand
    data object StopNavigation : VoiceCommand
    data object NearestFuel : VoiceCommand
    data object WhereParked : VoiceCommand
    data object Pursuit : VoiceCommand
}

/**
 * Plain-Kotlin parser for launcher voice commands. Only whole, command-shaped utterances match;
 * anything else (questions, chat) returns null and goes to the AI as before.
 */
object VoiceCommands {

    val PAGES = mapOf(
        "drive" to "drive", "map" to "drive", "home" to "drive", "navigation" to "drive",
        "gauges" to "gauges", "gauge" to "gauges", "dashboard" to "gauges",
        "media" to "media", "music player" to "media",
        "widgets" to "widgets", "widget" to "widgets",
        "apps" to "apps", "app drawer" to "apps", "applications" to "apps",
        "ai" to "ai", "a i" to "ai", "chat" to "ai", "games" to "games", "game" to "games", "arcade" to "games"
    )

    private val LEAD = Regex("""^(hey |ok |okay )?(kitt|kit)[, ]+|^(please |can you |could you |would you )+""")
    private val TAIL = Regex("""( please| for me| now)+$""")

    fun normalize(text: String): String {
        var t = text.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
        repeat(2) { t = t.replace(LEAD, "").trim() }
        return t.replace(TAIL, "").trim()
    }

    fun parse(text: String): VoiceCommand? {
        val t = normalize(text)
        if (t.isEmpty()) return null
        Regex("""^(start|begin) (logging|recording|a log|data logging)$""").find(t)?.let { return VoiceCommand.StartLogging }
        Regex("""^(stop|end) (logging|recording|the log|data logging)$""").find(t)?.let { return VoiceCommand.StopLogging }
        Regex("""^(read|scan|check)( my| the| for)?( engine| trouble| fault)? codes$|^(scan|check) (the )?(engine|check engine light)$""")
            .find(t)?.let { return VoiceCommand.ReadCodes }
        Regex("""^(play|resume)( the| my)?( music| song| audio)?$""").find(t)?.let { return VoiceCommand.Play }
        Regex("""^(pause|stop)( the| my)?( music| song| audio)?$""").find(t)?.let { return VoiceCommand.Pause }
        Regex("""^(next|skip)( the)?( song| track)?$""").find(t)?.let { return VoiceCommand.NextTrack }
        Regex("""^(previous|last|go back)( song| track)$|^previous$""").find(t)?.let { return VoiceCommand.PreviousTrack }
        Regex("""^(close|hide|exit)( the)? (stream|video|window|browser)$""").find(t)?.let { return VoiceCommand.CloseStream }
        Regex("""^(stop|cancel|end|exit)( the)? (navigation|navigating|route|directions)$""").find(t)?.let { return VoiceCommand.StopNavigation }
        Regex("""^(find|show|navigate to|take me to|get me to|where's|where is)( me)?( a| the)?( nearest| closest)? (gas station|gas|fuel|petrol station|petrol)( nearby)?$|^i need (gas|fuel|petrol)$""")
            .find(t)?.let { return VoiceCommand.NearestFuel }
        Regex("""^(where did i park|where('s| is) my (car|truck|vehicle|suv)|where did i leave (the|my) (car|truck|vehicle|suv))$""")
            .find(t)?.let { return VoiceCommand.WhereParked }
        Regex("""^((enter|start|engage|go to|open|switch to|activate) )?pursuit( mode)?$""").find(t)?.let { return VoiceCommand.Pursuit }
        Regex("""^(navigate|take me|drive|get me|give me directions|get directions|directions) to (.+)$""").find(t)?.let { m ->
            return VoiceCommand.Navigate(m.groupValues[2])
        }
        Regex("""^(open|show|go to)( the)? diagnostics$""").find(t)?.let { return VoiceCommand.OpenDiagnostics }
        Regex("""^(show|open|go to|switch to)( the| my)? (.+?)( page| screen)?$""").find(t)?.let { m ->
            PAGES[m.groupValues[3]]?.let { return VoiceCommand.ShowPage(it) }
        }
        Regex("""^(stream|watch)( some)? (.+?)( in the window| on the screen| on screen)?$""").find(t)?.let { m ->
            return VoiceCommand.Stream(m.groupValues[3])
        }
        Regex("""^(open|play|put on) (.+?) (in|on) the (window|screen)$""").find(t)?.let { m ->
            return VoiceCommand.Stream(m.groupValues[2])
        }
        Regex("""^(open|launch|start|run) (the )?(.+?)( app)?$""").find(t)?.let { m ->
            return VoiceCommand.OpenApp(m.groupValues[3])
        }
        return null
    }

    /**
     * Index of the label that best matches [query]: exact, then prefix, then contains, then all
     * words present. Null when nothing is a reasonable match.
     */
    fun bestMatch(query: String, labels: List<String>): Int? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val norm = labels.map { normalize(it) }
        norm.indexOfFirst { it == q }.takeIf { it >= 0 }?.let { return it }
        norm.indexOfFirst { it.startsWith(q) }.takeIf { it >= 0 }?.let { return it }
        norm.indexOfFirst { q.length >= 3 && q in it }.takeIf { it >= 0 }?.let { return it }
        val words = q.split(' ').filter { it.length >= 2 }
        return norm.indexOfFirst { label -> words.isNotEmpty() && words.all { it in label } }.takeIf { it >= 0 }
    }
}

/** "Hey KITT" detection on recognizer text, tolerant of common mis-hearings. */
object WakeWord {
    private val PATTERN = Regex("""\b(hey|hi|ok|okay|yo)\s+(kitt|kit|kid|kate|keith)\b""")

    fun matches(text: String): Boolean = PATTERN.containsMatchIn(text.lowercase())
}
