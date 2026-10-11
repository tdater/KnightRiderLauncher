package com.aiconsole.knightriderlauncher.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The launcher's selectable looks. */
enum class UiTheme(val label: String) {
    Kitt("KITT"),
    Karr("KARR"),
    NightVision("Night Vision"),
    SuperPursuit("Super Pursuit"),
    RetroVfd("Retro VFD"),
    Outrun("Outrun"),
    Stealth("Stealth"),
    Terminator("Terminator"),
    RoboCop("RoboCop"),
    // Stored as "Colorado" (its first name) so earlier selections carry over.
    Colorado("The Matrix"),
    BackToTheFuture("Back to the Future")
}

/** Film HUD themes change more than colours: scanner, panels, overlay, HUD text, intro and voice. */
enum class HudStyle { Terminator, RoboCop, Matrix, Future }

val UiTheme.hud: HudStyle?
    get() = when (this) {
        UiTheme.Terminator -> HudStyle.Terminator
        UiTheme.RoboCop -> HudStyle.RoboCop
        UiTheme.Colorado -> HudStyle.Matrix
        UiTheme.BackToTheFuture -> HudStyle.Future
        else -> null
    }

/**
 * Every color the UI draws with, by role. [accent] is the brand color (KITT's scanner red),
 * [danger] is reserved for warnings and destructive actions so it stays red in every theme.
 */
data class KittPalette(
    val black: Color,
    val charcoal: Color,
    val graphite: Color,
    val bezel: Color,
    val grid: Color,
    val accent: Color,
    val accentDim: Color,
    val danger: Color,
    val amber: Color,
    val amberDim: Color,
    val green: Color,
    val greenDim: Color,
    val yellow: Color,
    val info: Color,
    val white: Color,
    val grey: Color
)

// The black-glass KITT dashboard: scanner red, dash amber, LED green.
val KittClassicPalette = KittPalette(
    black = Color(0xFF050505),
    charcoal = Color(0xFF111213),
    graphite = Color(0xFF1A1B1D),
    bezel = Color(0xFF2B2C2F),
    grid = Color(0xFF222326),
    accent = Color(0xFFFF1E1E),
    accentDim = Color(0xFF4A0808),
    danger = Color(0xFFFF1E1E),
    amber = Color(0xFFFFB000),
    amberDim = Color(0xFF3D2A00),
    green = Color(0xFF3DFF3D),
    greenDim = Color(0xFF0C330C),
    yellow = Color(0xFFFFE600),
    info = Color(0xFF00BFFF),
    white = Color(0xFFE8E8E6),
    grey = Color(0xFF85878B)
)

// The Matrix (1999): green phosphor terminal on true black (#00FF41 code green), the multicolour
// digital rain behind translucent panels; red pill for warnings, blue pill for info.
val ColoradoPalette = KittPalette(
    black = Color(0xFF000000),
    charcoal = Color(0xFF020803),
    graphite = Color(0xFF051208),
    bezel = Color(0xFF1C5A2C),
    grid = Color(0xFF0E2A16),
    accent = Color(0xFF00FF41),
    accentDim = Color(0xFF003B12),
    danger = Color(0xFFFF2D2D),
    amber = Color(0xFF8CFFA8),
    amberDim = Color(0xFF12361E),
    green = Color(0xFF00FF41),
    greenDim = Color(0xFF003B12),
    yellow = Color(0xFFD8FF7A),
    info = Color(0xFF3D7BFF),
    white = Color(0xFFD4FFDC),
    grey = Color(0xFF5E9E6E)
)

// KARR, KITT's prototype twin: the same black glass with a gold scanner and amber-orange dash.
val KarrPalette = KittPalette(
    black = Color(0xFF060504),
    charcoal = Color(0xFF12100C),
    graphite = Color(0xFF1C1912),
    bezel = Color(0xFF3A3221),
    grid = Color(0xFF28231A),
    accent = Color(0xFFFFC21A),
    accentDim = Color(0xFF453300),
    danger = Color(0xFFFF2A1A),
    amber = Color(0xFFFF8A1A),
    amberDim = Color(0xFF402104),
    green = Color(0xFFE8D27A),
    greenDim = Color(0xFF3A3318),
    yellow = Color(0xFFFFE066),
    info = Color(0xFF9FC4E8),
    white = Color(0xFFEDE6D6),
    grey = Color(0xFF8E8676)
)

// Night Vision: only dim reds and red-oranges on true black, so the screen doesn't spoil the
// driver's dark adaptation. Warnings stay a brighter red to stand out.
val NightVisionPalette = KittPalette(
    black = Color(0xFF000000),
    charcoal = Color(0xFF070202),
    graphite = Color(0xFF0D0303),
    bezel = Color(0xFF2E0B0B),
    grid = Color(0xFF1C0606),
    accent = Color(0xFFB81414),
    accentDim = Color(0xFF330505),
    danger = Color(0xFFFF2E2E),
    amber = Color(0xFFB03A1C),
    amberDim = Color(0xFF2C0D06),
    green = Color(0xFFC0521E),
    greenDim = Color(0xFF301306),
    yellow = Color(0xFFC46A22),
    info = Color(0xFF9E3030),
    white = Color(0xFFC24848),
    grey = Color(0xFF7A2E2E)
)

// Super Pursuit Mode (season 4): blue-black panels, pursuit red with police blue secondary lights.
val SuperPursuitPalette = KittPalette(
    black = Color(0xFF03040A),
    charcoal = Color(0xFF0A0D18),
    graphite = Color(0xFF111626),
    bezel = Color(0xFF26304D),
    grid = Color(0xFF1A2138),
    accent = Color(0xFFFF2233),
    accentDim = Color(0xFF4A0A10),
    danger = Color(0xFFFF2233),
    amber = Color(0xFF3DA5FF),
    amberDim = Color(0xFF0B2A4A),
    green = Color(0xFF63F2FF),
    greenDim = Color(0xFF0E3A42),
    yellow = Color(0xFFFFE14D),
    info = Color(0xFF3DA5FF),
    white = Color(0xFFE6ECFA),
    grey = Color(0xFF7F8AA6)
)

// Retro VFD: the teal-cyan glow of an 80s vacuum-fluorescent dashboard.
val RetroVfdPalette = KittPalette(
    black = Color(0xFF020605),
    charcoal = Color(0xFF07100E),
    graphite = Color(0xFF0C1816),
    bezel = Color(0xFF1E3C38),
    grid = Color(0xFF142A27),
    accent = Color(0xFF3DF5E0),
    accentDim = Color(0xFF0B3A35),
    danger = Color(0xFFFF4040),
    amber = Color(0xFF9CFFE8),
    amberDim = Color(0xFF16403A),
    green = Color(0xFF6BFFB8),
    greenDim = Color(0xFF12402C),
    yellow = Color(0xFFE6FF7A),
    info = Color(0xFF3DC8F5),
    white = Color(0xFFD9FFF8),
    grey = Color(0xFF6E9C95)
)

// Outrun: synthwave neon, magenta and cyan on deep purple with a sunset yellow.
val OutrunPalette = KittPalette(
    black = Color(0xFF0B0014),
    charcoal = Color(0xFF14041F),
    graphite = Color(0xFF1D0A2E),
    bezel = Color(0xFF3A1760),
    grid = Color(0xFF2A0F46),
    accent = Color(0xFFFF2BD6),
    accentDim = Color(0xFF4A0A40),
    danger = Color(0xFFFF3355),
    amber = Color(0xFF2BE8FF),
    amberDim = Color(0xFF0A3A45),
    green = Color(0xFFFFD02B),
    greenDim = Color(0xFF45380A),
    yellow = Color(0xFFFFD02B),
    info = Color(0xFF2BE8FF),
    white = Color(0xFFF5E6FF),
    grey = Color(0xFF9B83B5)
)

// Stealth: true black and greys, minimal glow; only warnings keep colour.
val StealthPalette = KittPalette(
    black = Color(0xFF000000),
    charcoal = Color(0xFF050505),
    graphite = Color(0xFF0B0B0B),
    bezel = Color(0xFF222222),
    grid = Color(0xFF161616),
    accent = Color(0xFFD8D8D8),
    accentDim = Color(0xFF2E2E2E),
    danger = Color(0xFFFF3030),
    amber = Color(0xFFB0B0B0),
    amberDim = Color(0xFF262626),
    green = Color(0xFFE6E6E6),
    greenDim = Color(0xFF2A2A2A),
    yellow = Color(0xFFCCCCCC),
    info = Color(0xFF9E9E9E),
    white = Color(0xFFEDEDED),
    grey = Color(0xFF7A7A7A)
)

// The Terminator (1984): T-800 vision. Everything in shades of red on near-black, readouts in
// pale red-white, warnings white-hot so they still stand out in an all-red view.
val TerminatorPalette = KittPalette(
    black = Color(0xFF070000),
    charcoal = Color(0xFF120202),
    graphite = Color(0xFF1C0404),
    bezel = Color(0xFF5C1210),
    grid = Color(0xFF2E0807),
    accent = Color(0xFFFF2B1A),
    accentDim = Color(0xFF4A0905),
    danger = Color(0xFFFFFFFF),
    amber = Color(0xFFFF6B45),
    amberDim = Color(0xFF3D1208),
    green = Color(0xFFFFC2B8),
    greenDim = Color(0xFF3C1714),
    yellow = Color(0xFFFFD2A6),
    info = Color(0xFFFF8F80),
    white = Color(0xFFFFE4DE),
    grey = Color(0xFFB0645C)
)

// RoboCop (1987): visor POV and OCP chrome. Cold visor blue on steel-grey, OCP gold highlights,
// red reserved for threats.
val RoboCopPalette = KittPalette(
    black = Color(0xFF02060B),
    charcoal = Color(0xFF08111C),
    graphite = Color(0xFF0E1A29),
    bezel = Color(0xFF3A5674),
    grid = Color(0xFF16283C),
    accent = Color(0xFF66CCFF),
    accentDim = Color(0xFF0F2F4A),
    danger = Color(0xFFFF3D3D),
    amber = Color(0xFFE8C45A),
    amberDim = Color(0xFF3A2F10),
    green = Color(0xFFA9E8FF),
    greenDim = Color(0xFF123444),
    yellow = Color(0xFFFFE07A),
    info = Color(0xFF66CCFF),
    white = Color(0xFFE6F3FF),
    grey = Color(0xFF8399B3)
)

// Back to the Future (1985): brushed DeLorean stainless and the time circuits' three rows of
// LED readouts (red destination, green present, amber last departed), flux-capacitor blue-white.
val FuturePalette = KittPalette(
    black = Color(0xFF07080A),
    charcoal = Color(0xFF15181C),
    graphite = Color(0xFF22262B),
    bezel = Color(0xFF6E757D),
    grid = Color(0xFF30353B),
    accent = Color(0xFF9FE8FF),
    accentDim = Color(0xFF173A48),
    danger = Color(0xFFFF3A2A),
    amber = Color(0xFFFFB627),
    amberDim = Color(0xFF3F2C06),
    green = Color(0xFF3DFF6E),
    greenDim = Color(0xFF0E3A1A),
    yellow = Color(0xFFFF7A1A),
    info = Color(0xFF9FE8FF),
    white = Color(0xFFE9ECEF),
    grey = Color(0xFF9AA2AA)
)

fun UiTheme.palette(): KittPalette = when (this) {
    UiTheme.Kitt -> KittClassicPalette
    UiTheme.Karr -> KarrPalette
    UiTheme.NightVision -> NightVisionPalette
    UiTheme.SuperPursuit -> SuperPursuitPalette
    UiTheme.RetroVfd -> RetroVfdPalette
    UiTheme.Outrun -> OutrunPalette
    UiTheme.Stealth -> StealthPalette
    UiTheme.Terminator -> TerminatorPalette
    UiTheme.RoboCop -> RoboCopPalette
    UiTheme.Colorado -> ColoradoPalette
    UiTheme.BackToTheFuture -> FuturePalette
}

/** Swatches for the custom accent; 0 means "use the theme's own". */
val AccentSwatches: List<Long> = listOf(
    0xFFFF1E1E, 0xFFFF8A1A, 0xFFFFC21A, 0xFF3DFF3D, 0xFF3DF5E0, 0xFF3DA5FF, 0xFF9B5CFF, 0xFFFF2BD6, 0xFFE8E8E6
)

/** Replaces the brand accent (scanner, headers, active buttons) with [accent]. */
fun KittPalette.withAccent(accent: Color?): KittPalette =
    if (accent == null) this else copy(accent = accent, accentDim = lerp(black, accent, 0.25f))

/**
 * The active theme. Snapshot state, so every composable and draw lambda that reads one of
 * the colors below redraws by itself when the driver switches themes.
 */
object ThemeState {
    var theme by mutableStateOf(UiTheme.Kitt)

    /** Driver-picked accent overriding the theme's, or null. */
    var accentOverride by mutableStateOf<Color?>(null)

    val palette: KittPalette get() = theme.palette().withAccent(accentOverride)
}

// Surfaces
val KittBlack: Color get() = ThemeState.palette.black
val KittCharcoal: Color get() = ThemeState.palette.charcoal
val KittGraphite: Color get() = ThemeState.palette.graphite
val KittBezel: Color get() = ThemeState.palette.bezel
val KittGrid: Color get() = ThemeState.palette.grid

// Accents. ScannerRed is the brand accent (code green in The Matrix); AlertRed always means danger.
val ScannerRed: Color get() = ThemeState.palette.accent
val ScannerRedDim: Color get() = ThemeState.palette.accentDim
val AlertRed: Color get() = ThemeState.palette.danger
val DashAmber: Color get() = ThemeState.palette.amber
val DashAmberDim: Color get() = ThemeState.palette.amberDim
val LedGreen: Color get() = ThemeState.palette.green
val LedGreenDim: Color get() = ThemeState.palette.greenDim
val DashYellow: Color get() = ThemeState.palette.yellow
val DashBlue: Color get() = ThemeState.palette.info

// Text
val DashWhite: Color get() = ThemeState.palette.white
val DashGrey: Color get() = ThemeState.palette.grey
