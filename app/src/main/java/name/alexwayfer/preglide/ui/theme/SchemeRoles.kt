package name.alexwayfer.preglide.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Every color of the scheme, in a fixed order, read back by [colorSchemeOf]. A list compares by its colors, which a
 * [ColorScheme] does not, and blends color by color.
 */
fun ColorScheme.roles(): List<Color> = listOf(
    primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
    secondary, onSecondary, secondaryContainer, onSecondaryContainer,
    tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
    background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, surfaceTint,
    inverseSurface, inverseOnSurface, error, onError, errorContainer, onErrorContainer,
    outline, outlineVariant, scrim, surfaceBright, surfaceDim,
    surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest,
    primaryFixed, primaryFixedDim, onPrimaryFixed, onPrimaryFixedVariant,
    secondaryFixed, secondaryFixedDim, onSecondaryFixed, onSecondaryFixedVariant,
    tertiaryFixed, tertiaryFixedDim, onTertiaryFixed, onTertiaryFixedVariant,
)

/** The scheme of [roles], in the order [ColorScheme.roles] lists them. */
fun colorSchemeOf(roles: List<Color>): ColorScheme {
    // Arguments are read in the order they are written.
    val next = roles.iterator()
    return ColorScheme(
        primary = next.next(), onPrimary = next.next(), primaryContainer = next.next(),
        onPrimaryContainer = next.next(), inversePrimary = next.next(),
        secondary = next.next(), onSecondary = next.next(), secondaryContainer = next.next(),
        onSecondaryContainer = next.next(),
        tertiary = next.next(), onTertiary = next.next(), tertiaryContainer = next.next(),
        onTertiaryContainer = next.next(),
        background = next.next(), onBackground = next.next(), surface = next.next(), onSurface = next.next(),
        surfaceVariant = next.next(), onSurfaceVariant = next.next(), surfaceTint = next.next(),
        inverseSurface = next.next(), inverseOnSurface = next.next(), error = next.next(), onError = next.next(),
        errorContainer = next.next(), onErrorContainer = next.next(),
        outline = next.next(), outlineVariant = next.next(), scrim = next.next(), surfaceBright = next.next(),
        surfaceDim = next.next(),
        surfaceContainer = next.next(), surfaceContainerHigh = next.next(), surfaceContainerHighest = next.next(),
        surfaceContainerLow = next.next(), surfaceContainerLowest = next.next(),
        primaryFixed = next.next(), primaryFixedDim = next.next(), onPrimaryFixed = next.next(),
        onPrimaryFixedVariant = next.next(),
        secondaryFixed = next.next(), secondaryFixedDim = next.next(), onSecondaryFixed = next.next(),
        onSecondaryFixedVariant = next.next(),
        tertiaryFixed = next.next(), tertiaryFixedDim = next.next(), onTertiaryFixed = next.next(),
        onTertiaryFixedVariant = next.next(),
    )
}

/** The colors [fraction] of the way from [start] to [stop], role by role. */
fun blend(start: List<Color>, stop: List<Color>, fraction: Float): List<Color> =
    start.zip(stop) { from, to -> lerp(from, to, fraction) }
