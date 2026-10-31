package name.alexwayfer.preglide.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkModeTest {
    @Test
    fun theSystemsModeFollowsTheSystem() {
        assertTrue(DarkMode.System.isDark(systemDark = true))
        assertFalse(DarkMode.System.isDark(systemDark = false))
    }

    @Test
    fun aPickedModeHoldsWhateverTheSystemIs() {
        listOf(true, false).forEach { systemDark ->
            assertFalse(DarkMode.Light.isDark(systemDark))
            assertTrue(DarkMode.Dark.isDark(systemDark))
        }
    }

    @Test
    fun aModeReadsBackByItsName() {
        DarkMode.entries.forEach { assertEquals(it, DarkMode.parse(it.name)) }
    }

    @Test
    fun noModeOrAnUnknownOneIsTheSystems() {
        assertEquals(DarkMode.System, DarkMode.parse(null))
        assertEquals(DarkMode.System, DarkMode.parse("Dim"))
    }
}
