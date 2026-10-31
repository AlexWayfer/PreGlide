package name.alexwayfer.preglide.ime

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct

/** The least contrast text needs to read, by WCAG 2's 1.4.3: 4.5 to 1 for text below 18 points, 14 bold. */
internal const val TEXT_CONTRAST = 4.5f

/** The least contrast a pressed key needs to stand apart from the keys around it. */
internal const val LEAST_APART = 1.3f

/** WCAG's flare added to both luminances of a contrast ratio. */
private const val FLARE = 0.05f

/** Halvings of the tones between the key's and the text's: the last one narrows them to a fiftieth of a tone. */
private const val TONE_SEARCH_STEPS = 12

/** A pressed key's [background] and the color of its label on it, its [content]. */
internal data class PressedColors(val background: Color, val content: Color)

/**
 * A pressed key's colors: the primary container with its own text color, as Material pairs them; where the
 * container matches the keys, as in a light neutral palette, the [highlightColor] under the keys' text color.
 */
internal fun pressedColors(colors: ColorScheme): PressedColors =
    if (standsApart(colors.primaryContainer, colors)) {
        PressedColors(colors.primaryContainer, colors.onPrimaryContainer)
    } else {
        PressedColors(highlightColor(colors), colors.onSurface)
    }

/** Whether [color] stands apart from both kinds of keys. */
private fun standsApart(color: Color, colors: ColorScheme): Boolean =
    listOf(colors.surfaceBright, colors.surfaceContainerHighest).all { contrast(color, it) >= LEAST_APART }

/**
 * The accent, its hue and chroma, at the tone farthest from the keys' that keeps the keys' text on it readable. The
 * accent stands apart from the surfaces in every palette, where a container color may match them; it keeps its
 * chroma, which mixing it into the gray of a key would dull; and the text on it keeps its color.
 */
internal fun highlightColor(colors: ColorScheme): Color {
    val accent = Hct.fromInt(colors.primary.toArgb())
    val accentAt = { tone: Double -> Color(Hct.from(accent.hue, accent.chroma, tone).toInt()) }
    val readable = { tone: Double -> contrast(accentAt(tone), colors.onSurface) >= TEXT_CONTRAST }
    // The key's tone reads, the text's own does not: the farthest readable tone is between them.
    var near = Hct.fromInt(colors.surfaceBright.toArgb()).tone
    var far = Hct.fromInt(colors.onSurface.toArgb()).tone
    repeat(TONE_SEARCH_STEPS) {
        val middle = (near + far) / 2
        if (readable(middle)) near = middle else far = middle
    }
    return accentAt(near)
}

/** WCAG's contrast ratio of two colors, from 1 for the same luminance to 21 for black and white. */
internal fun contrast(a: Color, b: Color): Float {
    val (lighter, darker) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (lighter + FLARE) / (darker + FLARE)
}
