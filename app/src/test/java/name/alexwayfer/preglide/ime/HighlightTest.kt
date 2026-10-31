package name.alexwayfer.preglide.ime

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import name.alexwayfer.preglide.ui.theme.paletteColorScheme
import name.alexwayfer.preglide.ui.theme.presetPalettes
import name.alexwayfer.preglide.ui.theme.wallpaperPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {
    /** Every palette offered for a few wallpapers, among them a gray one, and the presets, light and dark. */
    private val schemes: List<ColorScheme> = run {
        val seeds = listOf(0xFF1E88E5, 0xFFE53935, 0xFF43A047, 0xFFFDD835, 0xFF808080).map { it.toInt() }
        val palettes = wallpaperPalettes(seeds) + presetPalettes
        listOf(false, true).flatMap { dark -> palettes.map { paletteColorScheme(it, dark, contrast = 0.0) } } +
            listOf(lightColorScheme(), darkColorScheme())
    }

    @Test
    fun theTextOnAHighlightReads() {
        schemes.forEach { assertTrue(contrast(highlightColor(it), it.onSurface) >= TEXT_CONTRAST) }
    }

    @Test
    fun aHighlightStandsApartFromTheKeysInEveryPalette() {
        schemes.forEach { colors ->
            val highlight = highlightColor(colors)
            assertTrue(contrast(highlight, colors.surfaceBright) >= LEAST_APART)
            assertTrue(contrast(highlight, colors.surfaceContainerHighest) >= LEAST_APART)
        }
    }

    @Test
    fun aPressedKeyStandsApartAndItsLabelReadsInEveryPalette() {
        schemes.forEach { colors ->
            val pressed = pressedColors(colors)
            assertTrue(contrast(pressed.background, colors.surfaceBright) >= LEAST_APART)
            assertTrue(contrast(pressed.background, colors.surfaceContainerHighest) >= LEAST_APART)
            assertTrue(contrast(pressed.background, pressed.content) >= TEXT_CONTRAST)
        }
    }

    @Test
    fun aPressedKeyTakesThePrimaryContainerWhereItStandsApart() {
        val vibrant = paletteColorScheme(wallpaperPalettes(listOf(0xFF1E88E5.toInt()))[2], dark = true, contrast = 0.0)

        assertEquals(PressedColors(vibrant.primaryContainer, vibrant.onPrimaryContainer), pressedColors(vibrant))
    }

    @Test
    fun aPressedKeyTakesTheHighlightWhereTheContainerMatchesTheKeys() {
        val neutral = paletteColorScheme(wallpaperPalettes(listOf(0xFF1E88E5.toInt()))[1], dark = false, contrast = 0.0)

        assertEquals(PressedColors(highlightColor(neutral), neutral.onSurface), pressedColors(neutral))
    }

    @Test
    fun aHighlightKeepsTheAccentsHue() {
        val colors = paletteColorScheme(wallpaperPalettes(listOf(0xFF1E88E5.toInt()))[2], dark = true, contrast = 0.0)
        val accent = Hct.fromInt(colors.primary.toArgb())
        val highlight = Hct.fromInt(highlightColor(colors).toArgb())

        assertEquals(accent.hue, highlight.hue, HUE_ROUNDING)
        assertTrue(highlight.chroma > accent.chroma * 0.9)
    }

    @Test
    fun contrastGoesFromOneForTheSameColorToTwentyOneForBlackAndWhite() {
        assertEquals(1f, contrast(Color.Gray, Color.Gray), 0.001f)
        assertEquals(21f, contrast(Color.Black, Color.White), 0.001f)
    }

    private companion object {
        /** A color rounded to whole channels may shift its hue by a little. */
        const val HUE_ROUNDING = 2.0
    }
}
