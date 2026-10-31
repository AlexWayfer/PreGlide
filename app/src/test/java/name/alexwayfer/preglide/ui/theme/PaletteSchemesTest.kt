package name.alexwayfer.preglide.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PaletteSchemesTest {
    private val blue = Palette(0xFF1E88E5.toInt(), PaletteStyle.TonalSpot, PaletteSource.Wallpaper)

    private fun scheme(palette: Palette = blue, dark: Boolean = false, contrast: Double = 0.0): ColorScheme =
        paletteColorScheme(palette, dark, contrast)

    @Test
    fun aDarkSchemeHasADarkSurfaceAndALightOneALightSurface() {
        assertTrue(scheme(dark = true).surface.luminance() < 0.1f)
        assertTrue(scheme(dark = false).surface.luminance() > 0.8f)
    }

    @Test
    fun eachStyleGivesItsOwnColors() {
        val primaries = PaletteStyle.entries.map { scheme(blue.copy(style = it)).primary }

        assertEquals(primaries.size, primaries.toSet().size)
    }

    @Test
    fun aGraySeedBuildsGooglesBlueAsAndroidDoes() {
        assertEquals(
            scheme(blue.copy(seed = GOOGLE_BLUE)).roles(),
            scheme(blue.copy(seed = 0xFF808080.toInt())).roles(),
        )
    }

    @Test
    fun theSystemsContrastSettingChangesTheColors() {
        assertNotEquals(scheme(contrast = 0.0).onSurfaceVariant, scheme(contrast = 1.0).onSurfaceVariant)
    }

    @Test
    fun aWallpaperSwatchShowsThePrimaryColorOverTheSecondaryAndTertiaryOnes() {
        val swatch = swatch(blue, dark = true)

        assertEquals(scheme(dark = true).primary, swatch.top)
        assertNotEquals(swatch.bottomLeft, swatch.bottomRight)
        assertNotEquals(swatch.top, swatch.bottomLeft)
    }

    @Test
    fun aPresetOfAHueIsOneColor() {
        val swatch = swatch(presetPalettes.last(), dark = false)

        assertEquals(swatch.top, swatch.bottomLeft)
        assertEquals(swatch.top, swatch.bottomRight)
    }

    @Test
    fun thePresetsShowAsOnAPixelWithADarkScreen() {
        // Their swatches on a Pixel, read off its screen, are their seeds; a hue rounds to a step either way.
        presetPalettes.drop(1).forEach { preset ->
            val shown = swatch(preset, dark = true).top.toArgb()
            listOf(16, 8, 0).forEach { shift ->
                val channel = { argb: Int -> argb shr shift and 0xFF }
                val seed = "%08X".format(Locale.ROOT, preset.seed)
                assertEquals(seed, channel(preset.seed).toFloat(), channel(shown).toFloat(), 1f)
            }
        }
    }

    @Test
    fun aSchemesSwatchShowsItsPrimaryColorOverItsSecondaryAndTertiaryOnes() {
        val colors = scheme(dark = true)
        val swatch = schemeSwatch(colors)

        assertEquals(Swatch(colors.primary, colors.secondaryContainer, colors.tertiary), swatch)
        assertNotEquals(swatch.top, swatch.bottomLeft)
    }

    @Test
    fun aMonochromeSwatchIsBlackOnTopOnALightScreenAndWhiteOnADarkOne() {
        val monochrome = presetPalettes.first()

        assertEquals(Color.Black, swatch(monochrome, dark = false).top)
        assertTrue(swatch(monochrome, dark = true).top.luminance() > 0.95f)
    }
}
