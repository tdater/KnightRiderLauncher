package com.aiconsole.knightriderlauncher.browser

/**
 * How controller (and on-screen touch pad) input reaches a game page (from RetroTV):
 * - [Auto]: the standard gamepad while the page reads gamepads, the mapped keyboard keys
 *   otherwise, decided per button press, so both kinds of emulator just work;
 * - [Gamepad]: only the Gamepad API (`navigator.getGamepads()`, see [VirtualPads]);
 * - [Keyboard]: only keyboard keys ([Gamepad.DEFAULT]);
 * - [Both]: both at once.
 */
enum class ControllerMode(val label: String) {
    Auto("Auto"),
    Gamepad("Gamepad API"),
    Keyboard("Keyboard keys"),
    Both("Gamepad + keys");

    fun next(): ControllerMode = entries[(ordinal + 1) % entries.size]

    /** Whether a button sends keyboard keys, given whether the page is reading gamepads right now. */
    fun sendsKeys(pagePolls: Boolean): Boolean = when (this) {
        Auto -> !pagePolls
        Gamepad -> false
        Keyboard, Both -> true
    }

    fun feedsGamepad(): Boolean = this != Keyboard

    companion object {
        fun parse(name: String?): ControllerMode = entries.firstOrNull { it.name == name } ?: Auto
    }
}

/**
 * Turns game-controller buttons into the keyboard keys browser emulators listen for
 * (EmulatorJS and most web emulators default to arrows, Z/X/A/S, Enter and Shift).
 * Android KeyEvent codes are inlined so this stays plain Kotlin.
 */
object Gamepad {

    // android.view.KeyEvent codes.
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_THUMBL = 106
    const val BUTTON_THUMBR = 107
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
    const val BUTTON_MODE = 110

    private const val KEY_ENTER = 66
    private const val KEY_SHIFT_RIGHT = 60
    private const val KEY_Z = 54
    private const val KEY_X = 52
    private const val KEY_A = 29
    private const val KEY_S = 47
    private const val KEY_Q = 45
    private const val KEY_W = 51
    private const val KEY_E = 33
    private const val KEY_R = 46

    /** Controller key -> keyboard key. D-pad keys pass through as arrows. */
    val DEFAULT: Map<Int, Int> = mapOf(
        DPAD_UP to DPAD_UP,
        DPAD_DOWN to DPAD_DOWN,
        DPAD_LEFT to DPAD_LEFT,
        DPAD_RIGHT to DPAD_RIGHT,
        BUTTON_A to KEY_X,
        BUTTON_B to KEY_Z,
        BUTTON_X to KEY_S,
        BUTTON_Y to KEY_A,
        BUTTON_L1 to KEY_Q,
        BUTTON_R1 to KEY_W,
        BUTTON_L2 to KEY_E,
        BUTTON_R2 to KEY_R,
        BUTTON_START to KEY_ENTER,
        BUTTON_SELECT to KEY_SHIFT_RIGHT
    )

    fun map(keyCode: Int): Int? = DEFAULT[keyCode]

    fun isGamepadButton(keyCode: Int): Boolean = keyCode in BUTTON_A..BUTTON_MODE

    /** Standard-mapping gamepad button index (W3C) for an Android key code, or -1. */
    fun standardIndex(keyCode: Int): Int = when (keyCode) {
        BUTTON_A -> 0
        BUTTON_B -> 1
        BUTTON_X -> 2
        BUTTON_Y -> 3
        BUTTON_L1 -> 4
        BUTTON_R1 -> 5
        BUTTON_L2 -> 6
        BUTTON_R2 -> 7
        BUTTON_SELECT -> 8
        BUTTON_START -> 9
        BUTTON_THUMBL -> 10
        BUTTON_THUMBR -> 11
        DPAD_UP -> 12
        DPAD_DOWN -> 13
        DPAD_LEFT -> 14
        DPAD_RIGHT -> 15
        BUTTON_MODE -> 16
        else -> -1
    }

    /** Analog stick to D-pad: which directions are held for stick position (x, y in -1..1). */
    fun stickDirections(x: Float, y: Float, deadZone: Float = 0.45f): Set<Int> = buildSet {
        if (x <= -deadZone) add(DPAD_LEFT)
        if (x >= deadZone) add(DPAD_RIGHT)
        if (y <= -deadZone) add(DPAD_UP)
        if (y >= deadZone) add(DPAD_DOWN)
    }
}

/** A built-in site on a browser home page (games or streaming). */
data class HomeSite(
    val name: String,
    val url: String,
    val description: String,
    val color: Long,
    val tags: List<String> = emptyList()
)

/**
 * The Games page's sites: RetroTV's in-browser retro game libraries (the owner's list), plus
 * preservation archives and free browser-game portals. Nothing is hosted by the launcher; these
 * are links, and the driver can add any other site to My sites.
 */
object GameSites {
    val ALL: List<HomeSite> = listOf(
        HomeSite("RetroGames.cc", "https://www.retrogames.cc/", "Huge NES, SNES, Genesis, GBA, N64, PS1 and arcade library", 0xFFD84315, listOf("Arcade", "Consoles")),
        HomeSite("PlayClassic.games", "https://playclassic.games/", "Classic console and DOS games in the browser", 0xFF6A1B9A, listOf("Consoles", "DOS")),
        HomeSite("ClassicReload", "https://classicreload.com/", "Thousands of DOS, Windows 3.1 and console classics", 0xFF2E7D32, listOf("DOS", "PC")),
        HomeSite("Emulator Gamer", "https://emulatorgamer.com/", "Play emulator games online for free", 0xFF1565C0, listOf("Consoles")),
        HomeSite("Retro Games Nexus", "https://retrogamesnexus.com/", "Classic retro and emulator games online", 0xFF00838F, listOf("Consoles")),
        HomeSite("Internet Arcade", "https://archive.org/details/internetarcade", "Internet Archive's playable arcade machines", 0xFF37474F, listOf("Arcade", "Archive")),
        HomeSite("OldGames.app", "https://oldgames.app/", "Classic retro games online", 0xFF8D6E63, listOf("Consoles")),
        HomeSite("Afterplay.io", "https://afterplay.io/", "Retro games on any device", 0xFFC2185B, listOf("Consoles")),
        HomeSite("Rec0deD:88", "https://rec0ded88.com/", "Retro games online, many systems", 0xFF283593, listOf("Consoles", "Arcade")),
        HomeSite("RetroGames.onl", "https://www.retrogames.onl/", "Online retro console games", 0xFFEF6C00, listOf("Consoles")),
        HomeSite("PlayRetroGames", "https://www.playretrogames.com/", "Sega, Nintendo and Game Boy classics online", 0xFF558B2F, listOf("Consoles")),
        HomeSite("webЯcade", "https://play.webrcade.com/", "Gamepad-first emulation front end", 0xFF4527A0, listOf("Consoles", "Controller")),
        HomeSite("Free 80s Arcade", "https://www.free80sarcade.com/", "NES, Atari, Sega and ColecoVision games", 0xFFAD1457, listOf("Arcade", "Consoles")),
        HomeSite("GAM.ONL", "https://gam.onl/", "Play retro games online", 0xFF00695C, listOf("Consoles")),
        HomeSite("MS-DOS Library", "https://archive.org/details/softwarelibrary_msdos_games", "Internet Archive: thousands of DOS games", 0xFF424242, listOf("DOS", "Archive")),
        HomeSite("Console Living Room", "https://archive.org/details/consolelivingroom", "Internet Archive: Atari, ColecoVision, Odyssey and more", 0xFF5D4037, listOf("Consoles", "Archive")),
        HomeSite("Poki", "https://poki.com/", "Free browser games, touch friendly", 0xFF1E88E5, listOf("Browser games")),
        HomeSite("CrazyGames", "https://www.crazygames.com/", "Free browser games", 0xFF7B1FA2, listOf("Browser games")),
        HomeSite("Addicting Games", "https://www.addictinggames.com/", "Over 5,000 free browser games", 0xFFF57C00, listOf("Browser games"))
    )

    /** Emulator apps for the head unit itself, by their official download pages. */
    val EMULATORS: List<HomeSite> = listOf(
        HomeSite("RetroArch", "https://www.retroarch.com/?page=platforms", "Almost everything: NES to PS1, arcade, DOS", 0xFF424242, listOf("Apps")),
        HomeSite("Lemuroid", "https://github.com/Swordfish90/Lemuroid/releases", "Simple multi-system emulator, controller friendly", 0xFF2E7D32, listOf("Apps")),
        HomeSite("PPSSPP", "https://www.ppsspp.org/download/", "PlayStation Portable", 0xFF1565C0, listOf("Apps")),
        HomeSite("Flycast", "https://github.com/flyinghead/flycast/releases", "Dreamcast, Naomi, Atomiswave", 0xFFEF6C00, listOf("Apps")),
        HomeSite("melonDS", "https://github.com/rafaelvcaetano/melonDS-android/releases", "Nintendo DS", 0xFF6A1B9A, listOf("Apps")),
        HomeSite("ScummVM", "https://www.scummvm.org/downloads/", "Classic point-and-click adventures", 0xFF00838F, listOf("Apps"))
    )

    /** Free and homebrew games to load into those apps. */
    val GAME_SOURCES: List<HomeSite> = listOf(
        HomeSite("PDRoms", "https://pdroms.de/", "Homebrew and public-domain ROMs", 0xFF5D4037, listOf("Homebrew")),
        HomeSite("Homebrew Hub", "https://hh.gbdev.io/", "Hundreds of free Game Boy homebrew games", 0xFF558B2F, listOf("Homebrew")),
        HomeSite("itch.io NES", "https://itch.io/games/tag-nes", "New games for the NES", 0xFFD84315, listOf("Homebrew"))
    )
}
