package name.alexwayfer.preglide.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppColorsTest {
    private val red = 0xFFDB4437.toInt()
    private val green = 0xFF0F9D58.toInt()

    @Test
    fun anAppsOwnColorIsTaken() {
        assertEquals(red, appSeed(red, platformDefaults = listOf(green)))
    }

    @Test
    fun anAppWithoutAPrimaryColorGivesNone() {
        assertNull(appSeed(null, platformDefaults = emptyList()))
    }

    @Test
    fun aGrayWhiteOrBlackBarGivesNone() {
        listOf(0xFFF5F5F5, 0xFFFFFFFF, 0xFF212121, 0xFF000000, 0xFFECEFF1).forEach {
            assertNull(appSeed(it.toInt(), platformDefaults = emptyList()))
        }
    }

    @Test
    fun androidsOwnThemesColorGivesNone() {
        assertNull(appSeed(green, platformDefaults = listOf(green)))
    }

    @Test
    fun aColorTheLibrariesAndTemplatesGiveUntilAnAppSetsItsOwnGivesNone() {
        listOf(0xFF3F51B5, 0xFF008577, 0xFF6200EE, 0xFFBB86FC, 0xFF6750A4, 0xFFD0BCFF).forEach {
            assertNull(appSeed(it.toInt(), platformDefaults = emptyList()))
        }
    }

    @Test
    fun anAppsColorBuildsAPaletteKeptNearIt() {
        assertEquals(Palette(red, PaletteStyle.Content, PaletteSource.App), appPalette(red))
    }

    @Test
    fun theAppsColorGoesOverTheChosenOnes() {
        val chosen = presetPalettes.last()

        assertEquals(appPalette(red), shownPalette(red, chosen))
        assertEquals(appPalette(red), shownPalette(red, null))
    }

    @Test
    fun withoutTheAppsColorTheChosenOnesShow() {
        val chosen = presetPalettes.last()

        assertEquals(chosen, shownPalette(null, chosen))
        assertNull(shownPalette(null, null))
    }
}
