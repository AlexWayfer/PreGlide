package name.alexwayfer.preglide.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemPaletteTest {
    private val options = wallpaperPalettes(listOf(0xFF1E88E5.toInt(), 0xFFE53935.toInt())) + presetPalettes

    private fun keyColorsOf(palette: Palette): List<Color> = paletteKeyColors(palette, dark = true, contrast = 0.0)

    @Test
    fun theKeyColorsAreTheSchemesOwn() {
        options.forEach { assertEquals(paletteColorScheme(it, true, 0.0).keyColors(), keyColorsOf(it)) }
    }

    @Test
    fun thePaletteTheSystemsColorsAreBuiltFromIsFound() {
        listOf(options[2], options[5], options.last()).forEach { systems ->
            assertEquals(systems, systemPalette(keyColorsOf(systems), options, ::keyColorsOf))
        }
    }

    @Test
    fun aRoundingOfTheSystemsColorsStillMatches() {
        val systems = options[6]
        val rounded = keyColorsOf(systems).map { it.copy(red = (it.red + 1f / 255).coerceAtMost(1f)) }

        assertEquals(systems, systemPalette(rounded, options, ::keyColorsOf))
    }

    @Test
    fun aMakersOwnPaletteMatchesNone() {
        assertNull(systemPalette(darkColorScheme().keyColors(), options, ::keyColorsOf))
    }
}
