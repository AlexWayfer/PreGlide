package name.alexwayfer.preglide.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/**
 * Material You's scheme of [palette], built as Android builds its own: a gray seed takes Google's blue instead, and
 * [contrast] is the system's contrast setting, from 0 for the standard one. It follows Material 3 Expressive's color
 * spec of 2025, as a Pixel does; AOSP keeps to the one of 2021, which differs in a few of the wallpaper's palettes.
 */
fun dynamicScheme(palette: Palette, dark: Boolean, contrast: Double): DynamicScheme {
    val proposed = Hct.fromInt(palette.seed)
    val seed = if (proposed.chroma < MIN_CHROMA) Hct.fromInt(GOOGLE_BLUE) else proposed
    val spec = ColorSpec.SpecVersion.SPEC_2025
    return when (palette.style) {
        PaletteStyle.TonalSpot -> SchemeTonalSpot(seed, dark, contrast, spec)
        PaletteStyle.Neutral -> SchemeNeutral(seed, dark, contrast, spec)
        PaletteStyle.Vibrant -> SchemeVibrant(seed, dark, contrast, spec)
        PaletteStyle.Expressive -> SchemeExpressive(seed, dark, contrast, spec)
        PaletteStyle.Monochrome -> SchemeMonochrome(seed, dark, contrast, spec)
        PaletteStyle.Content -> SchemeContent(seed, dark, contrast, spec)
    }
}

/** The app's colors from [palette]: see [dynamicScheme]. */
fun paletteColorScheme(palette: Palette, dark: Boolean, contrast: Double): ColorScheme =
    dynamicScheme(palette, dark, contrast).toColorScheme()

/** The three parts of a palette's swatch in the picker: a top half, and two quarters under it. */
data class Swatch(val top: Color, val bottomLeft: Color, val bottomRight: Color)

// The tones Android's picker shows a palette in: its primary color over its secondary and tertiary ones.
private const val PRIMARY_LIGHT = 40
private const val PRIMARY_DARK = 80
private const val SECONDARY_LIGHT = 85
private const val SECONDARY_DARK = 35
private const val TERTIARY = 70

/** Monochrome's primary color in the swatch: black on a light screen, white on a dark one. */
private const val MONOCHROME_LIGHT = 0
private const val MONOCHROME_DARK = 99

/**
 * How Android's picker shows [palette]: the primary color over the secondary and tertiary ones; a preset of a hue
 * by its primary color whole, as a Pixel shows them.
 */
fun swatch(palette: Palette, dark: Boolean): Swatch {
    val scheme = dynamicScheme(palette, dark, contrast = 0.0)
    if (palette.source == PaletteSource.Preset && palette.style != PaletteStyle.Monochrome) {
        val color = Color(scheme.primary)
        return Swatch(color, color, color)
    }
    val primary = when {
        palette.style != PaletteStyle.Monochrome -> if (dark) PRIMARY_DARK else PRIMARY_LIGHT
        dark -> MONOCHROME_DARK
        else -> MONOCHROME_LIGHT
    }
    return Swatch(
        Color(scheme.primaryPalette.tone(primary)),
        Color(scheme.secondaryPalette.tone(if (dark) SECONDARY_DARK else SECONDARY_LIGHT)),
        Color(scheme.tertiaryPalette.tone(TERTIARY)),
    )
}

/**
 * A swatch of colors in use, [scheme], for system colors that are none of the palettes offered: its primary color
 * over the roles nearest to the swatch's other tones, its secondary container and its tertiary color.
 */
fun schemeSwatch(scheme: ColorScheme): Swatch = Swatch(scheme.primary, scheme.secondaryContainer, scheme.tertiary)

/** Each color of [ColorScheme], in the order of [ColorScheme.roles]. */
private fun DynamicScheme.roles(): List<Color> = listOf(
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
).map(::Color)

private fun DynamicScheme.toColorScheme(): ColorScheme = colorSchemeOf(roles())
