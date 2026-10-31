package name.alexwayfer.preglide.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/** The colors that tell one palette's scheme from another's: its accents and its surfaces. */
fun ColorScheme.keyColors(): List<Color> =
    listOf(primary, secondary, tertiary, primaryContainer, surface, surfaceContainer)

/** The [keyColors] of [palette]'s scheme, built without the rest of its colors. */
fun paletteKeyColors(palette: Palette, dark: Boolean, contrast: Double): List<Color> =
    with(dynamicScheme(palette, dark, contrast)) {
        listOf(primary, secondary, tertiary, primaryContainer, surface, surfaceContainer).map(::Color)
    }

/** A channel may differ by this many steps of 255 and still count as the same: Android may round its own way. */
private const val SAME_COLOR_STEPS = 2
private const val CHANNEL_STEPS = 255f

/**
 * The palette of [options] that has the system's [keyColors], as the one the user chose in the system's picker, with
 * [keyColorsOf] giving each one's; null when none has, as when the phone's maker builds its palettes its own way.
 */
fun systemPalette(keyColors: List<Color>, options: List<Palette>, keyColorsOf: (Palette) -> List<Color>): Palette? =
    options.firstOrNull { sameColors(keyColors, keyColorsOf(it)) }

private fun sameColors(a: List<Color>, b: List<Color>): Boolean = a.size == b.size && a.zip(b).all { (x, y) ->
    listOf(x.red - y.red, x.green - y.green, x.blue - y.blue).all { abs(it) * CHANNEL_STEPS <= SAME_COLOR_STEPS }
}
