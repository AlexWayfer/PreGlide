package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.ui.theme.DarkMode
import name.alexwayfer.preglide.ui.theme.Palette
import name.alexwayfer.preglide.ui.theme.PaletteSource
import name.alexwayfer.preglide.ui.theme.PaletteStyle
import name.alexwayfer.preglide.ui.theme.presetPalettes
import name.alexwayfer.preglide.ui.theme.wallpaperPalettes
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorLabelsTest {
    /** A string resource as its id with its arguments, so a test sees which one was read. */
    private fun text(id: Int, args: List<Any>): String = if (args.isEmpty()) "$id" else "$id$args"

    private val blue = 0xFF1E88E5.toInt()
    private val red = 0xFFE53935.toInt()

    @Test
    fun aWallpaperPaletteGoesByItsColorsNumberAndItsStyle() {
        val palettes = wallpaperPalettes(listOf(blue, red))
        val vibrantRed = palettes.single { it.seed == red && it.style == PaletteStyle.Vibrant }

        assertEquals(
            text(R.string.colors_wallpaper_option, listOf(2, text(R.string.colors_style_vibrant, emptyList()))),
            paletteLabel(palettes, vibrantRed, ::text),
        )
    }

    @Test
    fun eachPresetGoesByItsOwnName() {
        val labels = presetPalettes.map { paletteLabel(presetPalettes, it, ::text) }

        assertEquals(text(R.string.colors_monochrome, emptyList()), labels.first())
        assertEquals(text(R.string.colors_lilac, emptyList()), labels.last())
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun eachModeHasItsOwnName() {
        assertEquals(DarkMode.entries.size, DarkMode.entries.map(::darkModeName).toSet().size)
        assertEquals(R.string.dark_mode_system, darkModeName(DarkMode.System))
    }

    @Test
    fun theHintNamesTheSystemsColorsAloneWhileTheModeFollowsTheSystem() {
        assertEquals(
            text(R.string.colors_system, emptyList()),
            colorsHint(appColors = false, null, emptyList(), DarkMode.System, ::text),
        )
    }

    @Test
    fun theHintNamesTheAppsColorsOverTheChosenOnes() {
        val pink = presetPalettes[2]

        assertEquals(
            text(R.string.colors_app, emptyList()),
            colorsHint(appColors = true, pink, emptyList(), DarkMode.System, ::text),
        )
        assertEquals(
            text(
                R.string.colors_hint_with_mode,
                listOf(text(R.string.colors_app, emptyList()), text(R.string.dark_mode_hint_dark, emptyList())),
            ),
            colorsHint(appColors = true, null, emptyList(), DarkMode.Dark, ::text),
        )
    }

    @Test
    fun theHintAddsAModeThatDoesNotFollowTheSystem() {
        val pink = presetPalettes[2]

        assertEquals(
            text(
                R.string.colors_hint_with_mode,
                listOf(text(R.string.colors_pink, emptyList()), text(R.string.dark_mode_hint_dark, emptyList())),
            ),
            colorsHint(appColors = false, pink, emptyList(), DarkMode.Dark, ::text),
        )
        assertEquals(
            text(
                R.string.colors_hint_with_mode,
                listOf(text(R.string.colors_system, emptyList()), text(R.string.dark_mode_hint_light, emptyList())),
            ),
            colorsHint(appColors = false, null, emptyList(), DarkMode.Light, ::text),
        )
    }

    @Test
    fun theHintNamesAWallpaperPaletteAsThePickerDoes() {
        val palettes = wallpaperPalettes(listOf(blue, red))
        val vibrantRed = palettes.single { it.seed == red && it.style == PaletteStyle.Vibrant }

        assertEquals(
            paletteLabel(palettes, vibrantRed, ::text),
            colorsHint(appColors = false, vibrantRed, palettes, DarkMode.System, ::text),
        )
    }

    @Test
    fun theHintNamesAWallpaperPaletteByTheWallpaperAloneBeforeItsColorsAreKnown() {
        val vibrantRed = wallpaperPalettes(listOf(red)).single { it.style == PaletteStyle.Vibrant }

        assertEquals(
            text(R.string.colors_wallpaper, emptyList()),
            colorsHint(appColors = false, vibrantRed, emptyList(), DarkMode.System, ::text),
        )
    }

    @Test
    fun aPaletteOutsideThePresetsGoesUnnamed() {
        val other = Palette(blue, PaletteStyle.TonalSpot, PaletteSource.Preset)

        assertEquals("", paletteLabel(presetPalettes, other, ::text))
    }
}
