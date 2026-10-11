package com.aiconsole.knightriderlauncher.data

/** Drawer groups, Smart Launcher-style, tuned for what's used in a car. */
enum class AppGroup(val label: String) {
    Navigation("Navigation"),
    Music("Music & audio"),
    Video("Video"),
    Car("Car"),
    Social("Social & news"),
    Tools("Tools"),
    Other("Other");

    companion object {
        // android.content.pm.ApplicationInfo.CATEGORY_* values (API 26+), inlined for JVM tests.
        private const val GAME = 0
        private const val AUDIO = 1
        private const val VIDEO = 2
        private const val IMAGE = 3
        private const val SOCIAL = 4
        private const val NEWS = 5
        private const val MAPS = 6
        private const val PRODUCTIVITY = 7

        private val CAR_WORDS = listOf("obd", "torque", "car scanner", "carscanner", "dash", "tpms", "dvr", "canbus", "vehicle")
        private val NAV_WORDS = listOf("maps", "waze", "navi", "gps", "sygic", "here we go")
        private val MUSIC_WORDS = listOf("music", "spotify", "pandora", "podcast", "audible", "radio", "sirius", "tunein")
        private val VIDEO_WORDS = listOf("youtube", "netflix", "video", "plex", "tv", "twitch", "vlc", "kodi")

        /** App store category first (when declared), then the label and package name. */
        fun of(category: Int?, label: String, packageName: String): AppGroup {
            val text = "$label $packageName".lowercase()
            if (CAR_WORDS.any { it in text } && !text.contains("music")) return Car
            when (category) {
                MAPS -> return Navigation
                AUDIO -> return Music
                VIDEO -> return Video
                SOCIAL, NEWS -> return Social
                PRODUCTIVITY, IMAGE -> return Tools
                GAME -> return Other
            }
            return when {
                NAV_WORDS.any { it in text } -> Navigation
                MUSIC_WORDS.any { it in text } -> Music // Before video: "YouTube Music" is music.
                VIDEO_WORDS.any { it in text } -> Video
                text.contains("settings") || text.contains("file") || text.contains("calculator") || text.contains("browser") -> Tools
                else -> Other
            }
        }
    }
}

/** Launch counts for "most used" ordering. Stored as key=count lines. */
object AppUsage {
    fun decode(text: String): Map<String, Int> = text.lines().mapNotNull { line ->
        val i = line.lastIndexOf('=')
        if (i <= 0) null else line.substring(0, i) to (line.substring(i + 1).toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

    fun encode(counts: Map<String, Int>): String =
        counts.entries.sortedByDescending { it.value }.take(200).joinToString("\n") { "${it.key}=${it.value}" }

    fun record(counts: Map<String, Int>, key: String): Map<String, Int> = counts + (key to (counts[key] ?: 0) + 1)

    fun mostUsed(counts: Map<String, Int>, limit: Int): List<String> =
        counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(limit).map { it.key }
}

object Compass {
    private val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun cardinal(bearing: Float): String = POINTS[((((bearing % 360) + 360) % 360 + 22.5f) / 45f).toInt() % 8]
}

/** What a mapped steering-wheel (or other hardware) button does while the launcher is in front. */
enum class KeyAction(val label: String) {
    OpenKitt("Talk to KITT"),
    NextPage("Next page"),
    PreviousPage("Previous page"),
    DrivePage("Drive page"),
    ToggleStream("Stream window"),
    PlayPause("Play / pause"),
    Diagnostics("Diagnostics"),
    Pursuit("Pursuit mode")
}

object KeyBindings {
    fun decode(text: String): Map<Int, KeyAction> = text.lines().mapNotNull { line ->
        val (code, action) = line.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        (code.toIntOrNull() ?: return@mapNotNull null) to (runCatching { KeyAction.valueOf(action) }.getOrNull() ?: return@mapNotNull null)
    }.toMap()

    fun encode(bindings: Map<Int, KeyAction>): String = bindings.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value.name}" }
}

/** When the start-up routine runs: at launcher start, or when the screen comes back after a long sleep. */
object StartupRule {
    const val MIN_OFF_MS = 5 * 60_000L

    fun shouldRun(firstStartThisProcess: Boolean, screenOffForMs: Long?): Boolean =
        firstStartThisProcess || (screenOffForMs != null && screenOffForMs >= MIN_OFF_MS)
}

/**
 * Home-page swipes that start in the strips along the screen edges. Web pages and the map keep
 * drags that start inside them, so the edges always change page.
 */
object EdgeSwipe {
    /** Past this share of the page width, or this fast (px/s), the swipe changes page. */
    const val DISTANCE_SHARE = 0.2f
    const val FLING_PX_PER_S = 900f

    /** The page to settle on after a drag of [dragPx] (negative = leftward, towards the next page). */
    fun target(startPage: Int, pageCount: Int, dragPx: Float, velocityPx: Float, widthPx: Float): Int {
        val next = dragPx < -widthPx * DISTANCE_SHARE || velocityPx < -FLING_PX_PER_S
        val previous = dragPx > widthPx * DISTANCE_SHARE || velocityPx > FLING_PX_PER_S
        val target = when {
            next && !previous -> startPage + 1
            previous && !next -> startPage - 1
            else -> startPage
        }
        return target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }
}
