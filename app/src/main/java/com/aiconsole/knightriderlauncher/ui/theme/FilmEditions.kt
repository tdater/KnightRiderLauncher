package com.aiconsole.knightriderlauncher.ui.theme

import androidx.compose.ui.graphics.Color
import com.aiconsole.knightriderlauncher.assistant.FilmCharacter

/**
 * Each film theme comes in editions: a different film, era or machine from the same series,
 * with its own colours, scanner, screen overlay, HUD text, boot sequence and default character.
 * All text is original, in the style of the films' machines and screens; none of it is film
 * dialogue. The first edition of each film is the theme as it was before editions existed.
 */
enum class FilmEdition(
    val film: HudStyle,
    val label: String,
    val year: String,
    /** One line for the settings screen. */
    val blurb: String,
    val palette: KittPalette,
    /** Brand line for the status strip and screensaver. */
    val brand: String,
    /** Readouts that rotate in the status strip and fill the screensaver. */
    val lines: List<String>,
    /** What the alert banner calls KITT's messages. */
    val alertTitle: String,
    /** Boot sequence when the theme or edition comes on. */
    val intro: List<String>,
    val character: FilmCharacter
) {
    // ---- The Terminator ----------------------------------------------------------------
    T800(
        HudStyle.Terminator, "T-800 Vision", "1984",
        "The cyborg's red vision: targeting readouts, scrolling machine code, 20-segment scanner.",
        TerminatorPalette,
        "CYBERDYNE 101",
        listOf("SCAN MODE 43984", "ANALYSIS: COMPLETE", "THREAT ASSESSMENT: NONE", "SYSTEMS: NOMINAL", "MATCH: VEHICLE PROFILE", "PRIORITY: MISSION"),
        "▌PRIORITY TRANSMISSION",
        listOf("CYBERDYNE SYSTEMS MODEL 101", "NEURAL NET PROCESSOR: ONLINE", "VISION OVERLAY: ACTIVE", "SCANNING VEHICLE ............ OK", "PRIMARY OBJECTIVE: DRIVE"),
        FilmCharacter.Cyborg
    ),
    T1000(
        HudStyle.Terminator, "Liquid Metal", "1991",
        "The liquid-metal prototype: mercury chrome, a molten bead for a scanner, a sheen that flows across the screen.",
        LiquidMetalPalette,
        "MIMETIC POLYALLOY · T-1000",
        listOf("POLYALLOY: STABLE", "FORM: VEHICLE INTERIOR", "SURFACE TENSION: 100%", "PURSUIT PROFILE: PATIENT", "DAMAGE: SELF-REPAIRED", "MIMIC LIBRARY: 1 VEHICLE"),
        "◆ LIQUID METAL",
        listOf("CYBERDYNE ADVANCED PROTOTYPE", "MIMETIC POLYALLOY ........... FLUID", "FORM ACQUIRED: VEHICLE", "REFORMING .................. OK", "OBJECTIVE: FOLLOW THE ROAD"),
        FilmCharacter.Liquid
    ),
    FutureWar(
        HudStyle.Terminator, "Future War", "2029",
        "The machines' war in the ruined future: night-battle blue, plasma bolts, a hunter-killer searchlight overhead.",
        FutureWarPalette,
        "SKYNET · TACTICAL NET 2029",
        listOf("HK PATROL: SECTOR CLEAR", "PLASMA CELLS: CHARGED", "INFRARED: TRACKING", "RESISTANCE ACTIVITY: NONE", "SEARCHLIGHT: SWEEPING"),
        "⚠ SKYNET UPLINK",
        listOf("SKYNET GLOBAL DEFENSE NETWORK", "HUNTER-KILLER LINK .......... UP", "INFRARED SEARCHLIGHTS ....... ON", "PLASMA CELLS ................ CHARGED", "UNIT ASSIGNED: GROUND VEHICLE"),
        FilmCharacter.Resistance
    ),

    // ---- RoboCop -----------------------------------------------------------------------
    Visor(
        HudStyle.RoboCop, "Visor", "1987",
        "The cyborg officer's visor: cold blue, a sweeping scan band, targeting brackets.",
        RoboCopPalette,
        "OCP · METRO WEST",
        listOf("DIRECTIVE 1: SERVE THE PUBLIC TRUST", "DIRECTIVE 2: PROTECT THE INNOCENT", "DIRECTIVE 3: UPHOLD THE LAW", "DIRECTIVE 4: [CLASSIFIED]"),
        "[ OCP ADVISORY ]",
        listOf("OCP CRIME PREVENTION SYSTEM", "VISOR CALIBRATION ........... OK", "TARGETING ................... OK", "DIRECTIVES LOADED: 4", "STATUS: ON PATROL"),
        FilmCharacter.Officer
    ),
    Ed209(
        HudStyle.RoboCop, "ED-209", "1987",
        "OCP's hulking enforcement droid: gunmetal and hazard yellow, a red sensor eye between warning stripes.",
        Ed209Palette,
        "ED-209 · ENFORCEMENT DROID",
        listOf("ENFORCEMENT MODE: STANDBY", "HYDRAULICS: PRESSURISED", "COMPLIANCE: CONFIRMED", "STAIRS: AVOID", "TARGET ACQUISITION: SAFE"),
        "[ ED-209 · COMPLY ]",
        listOf("ENFORCEMENT DROID SERIES 209", "HYDRAULIC LEGS .............. OK", "WEAPON SYSTEMS .............. SAFE", "VOICE PROCESSOR ............. LOUD", "STAIRS DETECTED: NONE"),
        FilmCharacter.Enforcer
    ),
    DeltaCity(
        HudStyle.RoboCop, "OCP Delta City", "1987",
        "The corporation's boardroom: navy and executive gold, a gold visor, news-flash alerts.",
        DeltaCityPalette,
        "OCP · DELTA CITY",
        listOf("DELTA CITY: UNDER CONSTRUCTION", "SECURITY CONCEPTS DIVISION", "SHARE PRICE: ▲ 2.1", "BOARD MEETING: 10:00", "PRODUCT RECALLS: NONE"),
        "▶ OCP NEWS FLASH",
        listOf("OMNI CONSUMER PRODUCTS", "DELTA CITY PLANNING ......... APPROVED", "SECURITY CONCEPTS ........... ONLINE", "STOCKHOLDERS ................ PLEASED", "STATUS: OPEN FOR BUSINESS"),
        FilmCharacter.Executive
    ),

    // ---- The Matrix --------------------------------------------------------------------
    Code(
        HudStyle.Matrix, "Digital Rain", "1999",
        "Inside the simulation: green phosphor code, mirrored glyphs in the scanner, the digital rain.",
        ColoradoPalette,
        "NEBUCHADNEZZAR · OPERATOR",
        listOf("WAKE UP, NEO...", "THE MATRIX HAS YOU...", "FOLLOW THE WHITE RABBIT.", "KNOCK, KNOCK."),
        "> INCOMING_",
        listOf("Wake up...", "The vehicle has you...", "Follow the white rabbit.", "Knock, knock."),
        FilmCharacter.Agent
    ),
    RealWorld(
        HudStyle.Matrix, "Real World", "1999",
        "Aboard the hovercraft: cold steel blue, amber consoles, an EMP that charges and fires in the scanner.",
        RealWorldPalette,
        "HOVERCRAFT · BROADCAST DEPTH",
        listOf("SENTINEL SWEEP: CLEAR", "EMP: ARMED", "BROADCAST DEPTH: HOLDING", "HARDLINE: LOCATED", "OPERATOR: ON STATION"),
        "▶ SHIP COMMS",
        listOf("HOVERCRAFT SYSTEMS ONLINE", "Magnetic pads: level.", "EMP: armed.", "Sentinels: none in range.", "Operator ready."),
        FilmCharacter.Operator
    ),
    Zion(
        HudStyle.Matrix, "Zion", "2003",
        "The last human city: warm amber firelight, dock beacons sweeping the scanner, gate-control alerts.",
        ZionPalette,
        "ZION · DOCK CONTROL",
        listOf("DOCK 3: CLEARED", "GATE: OPEN", "MAINLINE: ONLINE", "COUNCIL: IN SESSION", "TEMPLE: LOUD"),
        "◇ ZION CONTROL",
        listOf("Zion dock control.", "Gate clearance: granted.", "Loader bays: ready.", "Mainline: online.", "Welcome home."),
        FilmCharacter.Captain
    ),

    // ---- Back to the Future ------------------------------------------------------------
    Delorean1985(
        HudStyle.Future, "DeLorean 1985", "1985",
        "Brushed stainless and the time circuits: the flux capacitor in the scanner, label-maker panel titles.",
        FuturePalette,
        "DMC-12 · TIME CIRCUITS",
        listOf("TIME CIRCUITS: ON", "FLUX CAPACITOR: FLUXING", "1.21 GIGAWATTS: STANDBY", "TEMPORAL DISPLACEMENT: READY"),
        "⚡ TIME CIRCUITS",
        listOf("TIME CIRCUITS ON", "FLUX CAPACITOR ........ FLUXING", "DESTINATION TIME SET", "1.21 GIGAWATTS ......... READY", "WHERE WE'RE DRIVING, WE STILL NEED ROADS"),
        FilmCharacter.Inventor
    ),
    HillValley2015(
        HudStyle.Future, "Hill Valley 2015", "1989",
        "The future of Part II: hover conversion, neon magenta and cyan, a two-tone flux capacitor.",
        HillValley2015Palette,
        "HILL VALLEY 2015 · HOVER CONVERSION",
        listOf("HOVER CONVERSION: ENGAGED", "MR. FUSION: LOADED", "WEATHER: EXACTLY AS FORECAST", "SKYWAY: CLEAR", "SELF-LACING: ON"),
        "✦ HILL VALLEY 2015",
        listOf("HOVER CONVERSION ...... ON", "MR. FUSION ............ LOADED", "SKYWAY LANE ........... ASSIGNED", "DESTINATION: OCT 21 2015", "PRESENT TIME: NOW"),
        FilmCharacter.Host
    ),
    Frontier1885(
        HudStyle.Future, "Hill Valley 1885", "1990",
        "The Old West of Part III: brass and sepia, patched-up time circuits, steam pulses in the scanner.",
        Frontier1885Palette,
        "HILL VALLEY 1885 · TELEGRAPH",
        listOf("BLACKSMITH & LIVERY: OPEN", "STEAM PRESSURE: HOLDING", "TELEGRAPH: CONNECTED", "RAILROAD SPUR: CLEAR", "TIME CIRCUITS: PATCHED"),
        "✶ HILL VALLEY TELEGRAPH",
        listOf("TIME CIRCUITS ......... PATCHED", "DESTINATION: SEP 2 1885", "STEAM PRESSURE ........ RISING", "RAILROAD SPUR ......... CLEAR", "FRONTIER TOWN AHEAD"),
        FilmCharacter.Kid
    );

    companion object {
        fun forFilm(film: HudStyle): List<FilmEdition> = entries.filter { it.film == film }

        /** The film's original look. */
        fun default(film: HudStyle): FilmEdition = forFilm(film).first()
    }
}

/** The driver's edition and character for one film; a null character means the edition's own. */
data class FilmChoice(val edition: FilmEdition, val character: FilmCharacter? = null)

/** Plain-Kotlin storage for [FilmChoice]s, one film per line: "Terminator=T1000:Liquid" (unit-tested). */
object FilmChoices {

    fun decode(text: String): Map<HudStyle, FilmChoice> = text.lines().mapNotNull { line ->
        val film = runCatching { HudStyle.valueOf(line.substringBefore('=').trim()) }.getOrNull() ?: return@mapNotNull null
        val value = line.substringAfter('=', "")
        val edition = FilmEdition.entries.firstOrNull { it.name == value.substringBefore(':') && it.film == film }
            ?: FilmEdition.default(film)
        val character = FilmCharacter.entries.firstOrNull { it.name == value.substringAfter(':', "") && it.film == film }
        film to FilmChoice(edition, character)
    }.toMap()

    fun encode(choices: Map<HudStyle, FilmChoice>): String = choices.entries.sortedBy { it.key.ordinal }
        .joinToString("\n") { (film, c) -> "${film.name}=${c.edition.name}:${c.character?.name.orEmpty()}" }

    fun edition(choices: Map<HudStyle, FilmChoice>, film: HudStyle): FilmEdition =
        choices[film]?.edition ?: FilmEdition.default(film)

    /** The character that speaks for [film]: the driver's pick, else the chosen edition's own. */
    fun character(choices: Map<HudStyle, FilmChoice>, film: HudStyle): FilmCharacter =
        choices[film]?.character ?: edition(choices, film).character

    fun withEdition(choices: Map<HudStyle, FilmChoice>, edition: FilmEdition): Map<HudStyle, FilmChoice> =
        choices + (edition.film to FilmChoice(edition, choices[edition.film]?.character))

    fun withCharacter(choices: Map<HudStyle, FilmChoice>, film: HudStyle, character: FilmCharacter?): Map<HudStyle, FilmChoice> =
        choices + (film to FilmChoice(edition(choices, film), character?.takeIf { it.film == film }))
}

/** Small film moments triggered by driving (pure, unit-tested). */
object FilmMoments {
    /** The DeLorean's famous speed, as km/h so it's a nod at legal speeds, never a target to chase. */
    const val DISPLACEMENT_KMH = 88.0

    /** Speed just climbed through 88 km/h. */
    fun reached88(previousKmh: Double?, kmh: Double?): Boolean =
        previousKmh != null && kmh != null && previousKmh < DISPLACEMENT_KMH && kmh >= DISPLACEMENT_KMH
}

// ---- Edition palettes ----------------------------------------------------------------------

// Terminator 2 (1991): the liquid-metal prototype. Mercury silver on blue-black steel.
val LiquidMetalPalette = KittPalette(
    black = Color(0xFF05070A),
    charcoal = Color(0xFF0D1116),
    graphite = Color(0xFF161C23),
    bezel = Color(0xFF5A6673),
    grid = Color(0xFF222A33),
    accent = Color(0xFFC9D6E3),
    accentDim = Color(0xFF2A3440),
    danger = Color(0xFFFF3B2A),
    amber = Color(0xFF9FB8D0),
    amberDim = Color(0xFF1E2B38),
    green = Color(0xFFDCE8F2),
    greenDim = Color(0xFF26323E),
    yellow = Color(0xFFE6EEF5),
    info = Color(0xFF7FA8D0),
    white = Color(0xFFF0F5FA),
    grey = Color(0xFF8A97A5)
)

// The future war (2029): night battlefield blue, plasma blue-violet, fire for highlights.
val FutureWarPalette = KittPalette(
    black = Color(0xFF020309),
    charcoal = Color(0xFF070B18),
    graphite = Color(0xFF0D1326),
    bezel = Color(0xFF2C3A6B),
    grid = Color(0xFF141D3A),
    accent = Color(0xFF6E8BFF),
    accentDim = Color(0xFF141E4D),
    danger = Color(0xFFFF3030),
    amber = Color(0xFFB08CFF),
    amberDim = Color(0xFF2A1D4D),
    green = Color(0xFF9FD2FF),
    greenDim = Color(0xFF13304A),
    yellow = Color(0xFFFFD27A),
    info = Color(0xFF6E8BFF),
    white = Color(0xFFE2E8FF),
    grey = Color(0xFF7C86A8)
)

// ED-209: gunmetal, hazard yellow, the red sensor eye for warnings.
val Ed209Palette = KittPalette(
    black = Color(0xFF070707),
    charcoal = Color(0xFF121212),
    graphite = Color(0xFF1C1C1B),
    bezel = Color(0xFF4A4A44),
    grid = Color(0xFF262624),
    accent = Color(0xFFFFC400),
    accentDim = Color(0xFF453500),
    danger = Color(0xFFFF2A1A),
    amber = Color(0xFFFF7A1A),
    amberDim = Color(0xFF40200A),
    green = Color(0xFFE0E0D0),
    greenDim = Color(0xFF33332C),
    yellow = Color(0xFFFFE14D),
    info = Color(0xFFB8C0C8),
    white = Color(0xFFF2F2EA),
    grey = Color(0xFF8E8E84)
)

// OCP's Delta City boardroom: navy and executive gold.
val DeltaCityPalette = KittPalette(
    black = Color(0xFF03050C),
    charcoal = Color(0xFF0A0F1F),
    graphite = Color(0xFF111830),
    bezel = Color(0xFF3B4466),
    grid = Color(0xFF182040),
    accent = Color(0xFFE8C45A),
    accentDim = Color(0xFF3D3212),
    danger = Color(0xFFFF3D3D),
    amber = Color(0xFFF2DCA0),
    amberDim = Color(0xFF3A3220),
    green = Color(0xFF9FC4FF),
    greenDim = Color(0xFF16284A),
    yellow = Color(0xFFFFE07A),
    info = Color(0xFF7FA8FF),
    white = Color(0xFFF1EEE4),
    grey = Color(0xFF8E93A8)
)

// The Matrix's real world: the hovercraft's cold steel blue, amber consoles, sentinel red.
val RealWorldPalette = KittPalette(
    black = Color(0xFF020507),
    charcoal = Color(0xFF071015),
    graphite = Color(0xFF0C1820),
    bezel = Color(0xFF2B4A58),
    grid = Color(0xFF13262F),
    accent = Color(0xFF5FD4FF),
    accentDim = Color(0xFF0E3444),
    danger = Color(0xFFFF3A2E),
    amber = Color(0xFFFFB347),
    amberDim = Color(0xFF3D2A0E),
    green = Color(0xFF8FE3D2),
    greenDim = Color(0xFF123A33),
    yellow = Color(0xFFFFD27A),
    info = Color(0xFF5FD4FF),
    white = Color(0xFFD8EEF5),
    grey = Color(0xFF6F8E99)
)

// Zion: warm amber firelight deep underground.
val ZionPalette = KittPalette(
    black = Color(0xFF070402),
    charcoal = Color(0xFF140B05),
    graphite = Color(0xFF201209),
    bezel = Color(0xFF5A3A1E),
    grid = Color(0xFF2C1A0C),
    accent = Color(0xFFFF9A2E),
    accentDim = Color(0xFF4A2A08),
    danger = Color(0xFFFF3A2A),
    amber = Color(0xFFFFC670),
    amberDim = Color(0xFF40290E),
    green = Color(0xFFE8D2A0),
    greenDim = Color(0xFF3A2E18),
    yellow = Color(0xFFFFE08A),
    info = Color(0xFF8FC8FF),
    white = Color(0xFFF5E8D8),
    grey = Color(0xFF9E8A74)
)

// Hill Valley 2015 (Part II): neon magenta and cyan, fusion-reactor lime.
val HillValley2015Palette = KittPalette(
    black = Color(0xFF06030C),
    charcoal = Color(0xFF100820),
    graphite = Color(0xFF180D30),
    bezel = Color(0xFF4A2A78),
    grid = Color(0xFF221340),
    accent = Color(0xFFFF3DB8),
    accentDim = Color(0xFF4A0F38),
    danger = Color(0xFFFF3A2A),
    amber = Color(0xFF3DF0FF),
    amberDim = Color(0xFF0B3A44),
    green = Color(0xFFB8FF3D),
    greenDim = Color(0xFF2E400F),
    yellow = Color(0xFFFFE03D),
    info = Color(0xFF3DF0FF),
    white = Color(0xFFF5ECFF),
    grey = Color(0xFF9C8CB8)
)

// Hill Valley 1885 (Part III): brass, sepia and weathered wood.
val Frontier1885Palette = KittPalette(
    black = Color(0xFF080604),
    charcoal = Color(0xFF15100A),
    graphite = Color(0xFF211910),
    bezel = Color(0xFF6E5432),
    grid = Color(0xFF2E2216),
    accent = Color(0xFFE0A94F),
    accentDim = Color(0xFF3F2C10),
    danger = Color(0xFFFF4A2A),
    amber = Color(0xFFF2C27A),
    amberDim = Color(0xFF3D2C14),
    green = Color(0xFFC8D49A),
    greenDim = Color(0xFF2F3418),
    yellow = Color(0xFFFFD98A),
    info = Color(0xFFA8C4D8),
    white = Color(0xFFF2E6D2),
    grey = Color(0xFFA6927A)
)
