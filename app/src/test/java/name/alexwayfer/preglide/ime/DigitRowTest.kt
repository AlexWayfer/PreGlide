package name.alexwayfer.preglide.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitRowTest {
    private fun look(digitRow: Float) = KeyLook(
        hints = false,
        backgrounds = 1f,
        dividers = 0f,
        cornerRadius = 8.dp,
        height = 50.dp,
        stripHeight = 40.dp,
        digitRow = digitRow,
    )

    @Test
    fun theDigitRowHidesTheTopRowsHintsWhileItShows() {
        val look = { digitRow: Boolean -> keyLook(defaultLookSettings.copy(digitRow = digitRow)) }

        assertTrue(look(false).hints)
        assertFalse(look(true).hints)
        assertEquals(1f, look(true).digitRow)
        assertEquals(0f, look(false).digitRow)
    }

    @Test
    fun theDigitRowShowsAboveTheLettersOnly() {
        val letters = shownRows(KeyboardLayout.of(Language.Russian, Page.Letters), look(1f))
        val symbols = shownRows(KeyboardLayout.of(Language.Russian, Page.Symbols), look(1f))

        assertEquals(5, letters.size)
        assertTrue(letters.first().digits)
        assertEquals(
            "1234567890",
            letters.first().keys.joinToString("") { (it.action as KeyAction.Letter).char.toString() },
        )
        assertEquals(4, symbols.size)
        assertTrue(symbols.none { it.digits })
    }

    @Test
    fun withoutTheDigitRowEveryPageKeepsItsRows() {
        for (page in Page.entries) {
            val layout = KeyboardLayout.of(Language.English, page)

            assertEquals(layout.rows.map { ShownRow(it, 50.dp) }, shownRows(layout, look(0f)))
        }
    }

    @Test
    fun theOtherPagesStretchToTheLettersHeightWithTheDigitRow() {
        val look = look(1f)

        assertEquals(250.dp, keysHeight(look, 4))
        assertEquals(50.dp, pageRowHeight(look, Page.Letters, 4))
        assertEquals(62.5.dp, pageRowHeight(look, Page.Symbols, 4))
        assertEquals(62.5.dp, pageRowHeight(look, Page.Phone, 4))
    }

    @Test
    fun aGrowingDigitRowTakesItsShareOfTheHeight() {
        val look = look(0.5f)

        assertEquals(25.dp, digitRowHeight(look, Page.Letters))
        assertEquals(0.dp, digitRowHeight(look, Page.Symbols))
        assertEquals(225.dp, keysHeight(look, 4))
        assertEquals(56.25.dp, pageRowHeight(look, Page.Symbols, 4))
    }

    @Test
    fun aGlideIsReadOnTheLettersUnderTheDigitRow() {
        val keys = Rect(10f, 100f, 1010f, 600f)

        assertEquals(Rect(10f, 200f, 1010f, 600f), lettersBounds(keys, look(1f), 4))
        assertEquals(keys, lettersBounds(keys, look(0f), 4))
    }

    @Test
    fun aGlideOnTheTopLetterRowReadsTheSameWithTheDigitRowAbove() {
        val letters = Rect(0f, 100f, 1100f, 500f)
        val withDigits = Rect(0f, 0f, 1100f, 500f)
        val path = listOf(Offset(50f, 150f), Offset(550f, 150f))

        val plain = lettersGlide(path, letters, look(0f), Language.Russian)
        val underDigits = lettersGlide(path, withDigits, look(1f), Language.Russian)

        assertEquals(plain.rowHeight, underDigits.rowHeight, 1e-4f)
        assertEquals(plain.length, underDigits.length, 1e-4f)
    }
}
