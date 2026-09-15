package com.robinying.paddlevision.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the vision screens. Screens read them through [VisionTheme.colors]
 * instead of hard-coding literals, so a palette change stays in one file and a dark
 * variant only has to supply a second token set.
 */
@Immutable
data class VisionColors(
    val ink: Color,
    val mineral: Color,
    val paper: Color,
    val signalTeal: Color,
    val tealMist: Color,
    val amber: Color,
    val slate: Color,
    val line: Color,
    val faceAccent: Color,
)

private val LightVisionColors = VisionColors(
    ink = Color(0xFF101B2D),
    mineral = Color(0xFFF2F5F7),
    paper = Color(0xFFFFFFFF),
    signalTeal = Color(0xFF087E8B),
    tealMist = Color(0xFFDDF4F5),
    amber = Color(0xFFE59F23),
    slate = Color(0xFF64748B),
    line = Color(0xFFD7E0E6),
    faceAccent = Color(0xFF6950A1),
)

private val DarkVisionColors = VisionColors(
    ink = Color(0xFFE6EDF5),
    mineral = Color(0xFF0D141F),
    paper = Color(0xFF18222F),
    signalTeal = Color(0xFF4FC7D4),
    tealMist = Color(0xFF11333A),
    amber = Color(0xFFF0B24A),
    slate = Color(0xFF9AA9BA),
    line = Color(0xFF2C3846),
    faceAccent = Color(0xFF9B85D6),
)

private val LocalVisionColors = staticCompositionLocalOf { LightVisionColors }

/** Entry point for the design tokens, mirroring the `MaterialTheme` object shape. */
object VisionTheme {
    val colors: VisionColors
        @Composable
        @ReadOnlyComposable
        get() = LocalVisionColors.current
}

/**
 * Applies the vision palette and mirrors it into `MaterialTheme.colorScheme`, so Material 3
 * components that fall back to theme defaults stay consistent with the screen tokens.
 */
@Composable
fun VisionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkVisionColors else LightVisionColors
    CompositionLocalProvider(LocalVisionColors provides colors) {
        MaterialTheme(colorScheme = colors.toColorScheme(darkTheme), content = content)
    }
}

private fun VisionColors.toColorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = signalTeal,
        onPrimary = mineral,
        secondary = amber,
        background = mineral,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = tealMist,
        onSurfaceVariant = slate,
        outline = line,
    )
} else {
    lightColorScheme(
        primary = signalTeal,
        onPrimary = paper,
        secondary = amber,
        background = mineral,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = tealMist,
        onSurfaceVariant = slate,
        outline = line,
    )
}
