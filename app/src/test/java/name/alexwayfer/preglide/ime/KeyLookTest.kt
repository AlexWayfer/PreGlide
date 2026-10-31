package name.alexwayfer.preglide.ime

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.Key
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.settings.DEFAULT_KEY_CORNER_RADIUS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keys' settings out of the box, in portrait: the tests' start, changed by copy for what one looks at. */
internal val defaultLookSettings = LookSettings(
    hints = true,
    backgrounds = true,
    dividers = false,
    cornerRadius = 8,
    height = 52,
    landscape = false,
)

class KeyLookTest {
    private val settings = defaultLookSettings

    @Test
    fun aKeysSurfaceFollowsTheCornerRadius() {
        val colors = lightColorScheme()
        val key = Key(KeyAction.Letter('а'))
        val square = keyLook(settings.copy(cornerRadius = 0))
        val round = keyLook(settings)

        assertEquals(Modifier.keySurface(key, false, round, colors), Modifier.keySurface(key, false, round, colors))
        assertNotEquals(Modifier.keySurface(key, false, square, colors), Modifier.keySurface(key, false, round, colors))
    }

    @Test
    fun withoutBackgroundsOnlyAPressedKeyHasOne() {
        val colors = lightColorScheme()
        val letter = Key(KeyAction.Letter('а'))

        val pressed = pressedColors(colors).background

        assertEquals(0f, keySurfaceColor(letter, pressed = false, backgrounds = 0f, colors).alpha)
        assertEquals(0f, keySurfaceColor(Key(KeyAction.Shift), pressed = false, backgrounds = 0f, colors).alpha)
        assertEquals(pressed, keySurfaceColor(letter, pressed = true, backgrounds = 0f, colors))
        assertEquals(colors.surfaceBright, keySurfaceColor(letter, pressed = false, backgrounds = 1f, colors))
    }

    @Test
    fun aFadingBackgroundShowsInPartButAPressedKeyLightsUpWhole() {
        val colors = lightColorScheme()
        val letter = Key(KeyAction.Letter('а'))

        assertEquals(
            colors.surfaceBright.copy(alpha = 0.5f),
            keySurfaceColor(letter, pressed = false, backgrounds = 0.5f, colors),
        )
        assertEquals(
            pressedColors(colors).background,
            keySurfaceColor(letter, pressed = true, backgrounds = 0.5f, colors),
        )
    }

    @Test
    fun withoutBackgroundsTheCornersTakeTheDefaultRadius() {
        assertEquals(
            KeyLook(
                hints = true,
                backgrounds = 1f,
                dividers = 0f,
                cornerRadius = 12.dp,
                height = 52.dp,
                stripHeight = 40.dp,
            ),
            keyLook(settings.copy(cornerRadius = 12)),
        )
        assertEquals(
            KeyLook(
                hints = false,
                backgrounds = 0f,
                dividers = 0f,
                cornerRadius = DEFAULT_KEY_CORNER_RADIUS.dp,
                height = 52.dp,
                stripHeight = 40.dp,
            ),
            keyLook(settings.copy(hints = false, backgrounds = false, cornerRadius = 12)),
        )
    }

    @Test
    fun theStripShowsAsTheSettingSays() {
        assertTrue(keyLook(settings).strip)
        assertFalse(keyLook(settings.copy(strip = false)).strip)
    }

    @Test
    fun dividersShowOnlyBetweenKeysWithoutBackgrounds() {
        assertEquals(1f, keyLook(settings.copy(backgrounds = false, dividers = true)).dividers)
        assertEquals(0f, keyLook(settings.copy(dividers = true)).dividers)
    }

    @Test
    fun aRowOfKeysIsAsTallAsTheUserPicked() {
        assertEquals(44.dp, keyLook(settings.copy(height = 44)).height)
        // The height holds without the backgrounds too, unlike the corners' radius.
        assertEquals(60.dp, keyLook(settings.copy(backgrounds = false, dividers = true, height = 60)).height)
    }

    @Test
    fun inLandscapeTheRowsAndTheStripAreLowerAsInAosp() {
        val portrait = keyLook(settings)
        val landscape = keyLook(settings.copy(landscape = true))

        assertEquals(52.dp, portrait.height)
        assertEquals(40.dp, portrait.stripHeight)
        assertEquals(44.51, landscape.height.value.toDouble(), 0.01)
        assertEquals(36.dp, landscape.stripHeight)
    }

    @Test
    fun theLandscapeRowKeepsTheShareOfAnyHeightPicked() {
        assertEquals(40.dp, rowHeight(40, landscape = false))
        assertEquals(34.24, rowHeight(40, landscape = true).value.toDouble(), 0.01)
        assertEquals(54.79, rowHeight(64, landscape = true).value.toDouble(), 0.01)
    }

    @Test
    fun aDividerStandsBetweenTwoKeysButNotBesideAGap() {
        val a = Key(KeyAction.Letter('а'))
        val gap = Key(KeyAction.Gap, width = 0.5f)
        val row = listOf(gap, a, a, Key(KeyAction.Space), gap)

        assertEquals(listOf(false, true, true, false, false), row.indices.map { dividedFromNext(row, it) })
    }

    @Test
    fun aKeyWithoutADividerDrawsNothingMore() {
        val color = lightColorScheme().outlineVariant

        assertEquals(Modifier, Modifier.keyDivider(divided = false, dividers = 1f, color))
        assertEquals(Modifier, Modifier.keyDivider(divided = true, dividers = 0f, color))
        assertNotEquals(Modifier, Modifier.keyDivider(divided = true, dividers = 1f, color))
    }
}
