package name.alexwayfer.preglide.ui.theme

import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperSeedsTest {
    private fun color(hue: Double, chroma: Double = 60.0): Int = Hct.from(hue, chroma, 50.0).toInt()

    private val red = color(25.0)
    private val orange = color(60.0)
    private val green = color(140.0)
    private val blue = color(260.0)
    private val gray = color(200.0, chroma = 2.0)

    private fun read(vararg numbers: Int): WallpaperColorData? {
        val next = numbers.iterator()
        return readWallpaperColors(numbers.size) { next.next() }
    }

    @Test
    fun theParcelGivesTheMainColorsThenEachColorWithItsPopulation() {
        val hints = 0

        val colors = read(2, blue, red, 2, blue, 70, red, 30, hints)

        assertEquals(WallpaperColorData(listOf(blue, red), mapOf(blue to 70, red to 30)), colors)
    }

    @Test
    fun aParcelOfAnotherFormatReadsAsNone() {
        assertNull(read(-1))
        assertNull(read(5, blue))
        assertNull(read(1, blue, 3, red, 10))
    }

    @Test
    fun theColorCoveringMostOfTheWallpaperLeads() {
        val colors = WallpaperColorData(listOf(blue), mapOf(blue to 60, red to 30, green to 10))

        assertEquals(listOf(blue, red, green), wallpaperSeeds(colors))
    }

    @Test
    fun aMoreColorfulColorOutranksAGrayerOneOfTheSameShare() {
        val dull = color(25.0, chroma = 20.0)
        val vivid = color(260.0, chroma = 80.0)

        val colors = WallpaperColorData(emptyList(), mapOf(dull to 50, vivid to 50))

        assertEquals(listOf(vivid, dull), wallpaperSeeds(colors))
    }

    @Test
    fun colorsCloseInHueGiveOneSeed() {
        val colors = WallpaperColorData(listOf(red), mapOf(red to 50, orange to 40, blue to 10))

        assertEquals(listOf(red, blue), wallpaperSeeds(colors))
    }

    @Test
    fun aColorBetweenTwoSeedsInHueGivesNone() {
        val thirds = listOf(0.0, 120.0, 240.0).map { color(it) }
        // Grayer, so it ranks last and finds the hues on both sides taken.
        val between = color(60.0, chroma = 10.0)
        val population = (thirds + between).associateWith { 25 }

        assertEquals(thirds.toSet(), wallpaperSeeds(WallpaperColorData(emptyList(), population)).toSet())
    }

    @Test
    fun aColorOnATinyPartOfTheWallpaperIsNoSeed() {
        val colors = WallpaperColorData(listOf(blue), mapOf(blue to 999, red to 1))

        assertEquals(listOf(blue), wallpaperSeeds(colors))
    }

    @Test
    fun aGrayWallpaperTakesGooglesBlue() {
        assertEquals(listOf(GOOGLE_BLUE), wallpaperSeeds(WallpaperColorData(listOf(gray), mapOf(gray to 100))))
    }

    @Test
    fun aWallpaperWithoutPopulationsRanksItsMainColorsInOrder() {
        val live = WallpaperColorData(listOf(red, gray, red, blue), emptyMap())

        assertEquals(listOf(red, blue), wallpaperSeeds(live))
        assertEquals(listOf(GOOGLE_BLUE), wallpaperSeeds(WallpaperColorData(listOf(gray), emptyMap())))
    }
}
