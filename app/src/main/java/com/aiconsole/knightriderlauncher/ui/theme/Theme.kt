package com.aiconsole.knightriderlauncher.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

// Always dark, never dynamic: the dash look must not change with the wallpaper.
private fun colorScheme(p: KittPalette): ColorScheme = darkColorScheme(
    primary = p.accent,
    onPrimary = p.black,
    primaryContainer = p.accentDim,
    onPrimaryContainer = p.white,
    secondary = p.amber,
    onSecondary = p.black,
    secondaryContainer = p.amberDim,
    onSecondaryContainer = p.amber,
    tertiary = p.green,
    onTertiary = p.black,
    tertiaryContainer = p.greenDim,
    onTertiaryContainer = p.green,
    background = p.black,
    onBackground = p.white,
    surface = p.charcoal,
    onSurface = p.white,
    surfaceVariant = p.graphite,
    onSurfaceVariant = p.grey,
    surfaceDim = p.black,
    surfaceBright = p.bezel,
    surfaceContainerLowest = p.black,
    surfaceContainerLow = p.charcoal,
    surfaceContainer = p.charcoal,
    surfaceContainerHigh = p.graphite,
    surfaceContainerHighest = p.bezel,
    outline = p.bezel,
    outlineVariant = p.grid,
    error = p.danger,
    onError = p.black
)

// Tight, near-square corners, like 80s instrument bezels
private val KittShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

// The Matrix: tight terminal-window corners.
private val ColoradoShapes = Shapes(
    extraSmall = RoundedCornerShape(1.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(6.dp)
)

// Terminator: hard square edges, like a machine's display grid.
private val TerminatorShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

// RoboCop: chamfered corners, like OCP armour plating and the visor's angled frame.
private val RoboCopShapes = Shapes(
    extraSmall = CutCornerShape(2.dp),
    small = CutCornerShape(5.dp),
    medium = CutCornerShape(8.dp),
    large = CutCornerShape(10.dp),
    extraLarge = CutCornerShape(14.dp)
)

@Composable
fun KnightRiderLauncherTheme(content: @Composable () -> Unit) {
    val theme = ThemeState.theme
    val accent = ThemeState.accentOverride
    val scheme = remember(theme, accent) { colorScheme(ThemeState.palette) }
    MaterialTheme(
        colorScheme = scheme,
        typography = when {
            theme == UiTheme.Colorado -> ColoradoTypography
            theme == UiTheme.BackToTheFuture -> Typography
            theme.hud != null -> HudTypography
            else -> Typography
        },
        shapes = when (theme) {
            UiTheme.Colorado -> ColoradoShapes
            UiTheme.Terminator -> TerminatorShapes
            UiTheme.RoboCop -> RoboCopShapes
            else -> KittShapes
        },
        content = content
    )
}
