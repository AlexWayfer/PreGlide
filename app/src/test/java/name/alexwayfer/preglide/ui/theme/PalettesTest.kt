package name.alexwayfer.preglide.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {
    private val blue = 0xFF1E88E5.toInt()
    private val red = 0xFFE53935.toInt()

    @Test
    fun aPaletteReadsBackAsItWasKept() {
        val palettes = wallpaperPalettes(listOf(blue, red)) + presetPalettes

        palettes.forEach { assertEquals(it, Palette.parse(it.encode())) }
    }

    @Test
    fun aPaletteKeptByAnotherVersionReadsAsNone() {
        listOf(
            "",
            "Wallpaper:TonalSpot",
            "Wallpaper:TonalSpot:ff1e88e5:more",
            "Elsewhere:TonalSpot:ff1e88e5",
            "Wallpaper:Pastel:ff1e88e5",
            "Preset:Rainbow:zz",
        ).forEach { assertNull(it, Palette.parse(it)) }
    }

    @Test
    fun eachWallpaperColorComesInFourStylesAsAndroidOffersThem() {
        val palettes = wallpaperPalettes(listOf(blue, red))

        assertEquals(listOf(blue, blue, blue, blue, red, red, red, red), palettes.map { it.seed })
        assertEquals(
            listOf(PaletteStyle.TonalSpot, PaletteStyle.Neutral, PaletteStyle.Vibrant, PaletteStyle.Expressive),
            palettes.take(4).map { it.style },
        )
        assertTrue(palettes.all { it.source == PaletteSource.Wallpaper })
    }

    @Test
    fun atMostFourWallpaperColorsAreOffered() {
        assertEquals(16, wallpaperPalettes(List(6) { it }).size)
    }

    @Test
    fun thePresetsAreMonochromeThenCalmOnesAsOnAPixel() {
        assertEquals(PaletteStyle.Monochrome, presetPalettes.first().style)
        assertEquals(12, presetPalettes.count { it.style == PaletteStyle.TonalSpot })
        assertTrue(presetPalettes.all { it.source == PaletteSource.Preset })
    }

    @Test
    fun aNewWallpaperWithoutTheChosenColorBringsTheSystemsBack() {
        val chosen = wallpaperPalettes(listOf(blue)).first()

        assertTrue(outlivedWallpaper(chosen, listOf(red)))
        assertFalse(outlivedWallpaper(chosen, listOf(red, blue)))
    }

    @Test
    fun aPresetAndTheSystemsColorsOutliveAnyWallpaper() {
        assertFalse(outlivedWallpaper(presetPalettes.last(), listOf(red)))
        assertFalse(outlivedWallpaper(null, listOf(red)))
    }

    @Test
    fun theChoiceStaysWhileTheWallpapersColorsAreUnknown() {
        assertFalse(outlivedWallpaper(wallpaperPalettes(listOf(blue)).first(), null))
    }

    @Test
    fun turningTheSystemsColorsOffKeepsThePaletteTheSystemHas() {
        val systems = wallpaperPalettes(listOf(red, blue))[2]

        assertEquals(systems, paletteAfterSystem(listOf(red, blue), systems))
    }

    @Test
    fun turningTheSystemsColorsOffTakesTheWallpapersFirstPaletteWhenTheSystemHasNoneOfThem() {
        assertEquals(wallpaperPalettes(listOf(red, blue)).first(), paletteAfterSystem(listOf(red, blue), null))
        assertEquals(presetPalettes.first(), paletteAfterSystem(null, null))
    }
}
