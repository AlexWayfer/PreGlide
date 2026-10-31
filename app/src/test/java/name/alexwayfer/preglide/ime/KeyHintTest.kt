package name.alexwayfer.preglide.ime

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyHintTest {
    @Test
    fun aKeyOffersItsHintWhileHintsAreShown() {
        val key = KeyAction.Letter('е', listOf('ё'), hint = '5')

        assertEquals(listOf('е', '5', 'ё'), offeredOptions(key, hints = true, symbols = false))
        assertEquals(listOf('е', 'ё'), offeredOptions(key, hints = false, symbols = true))
    }

    @Test
    fun aKeyWithOnlyAHintOffersNoPopupWhileHintsAreHidden() {
        val key = KeyAction.Letter('q', hint = '1')

        assertEquals(KeyAction.Letter('q'), offeredAction(key, hints = false, symbols = true))
        assertEquals(KeyAction.Space, offeredAction(KeyAction.Space, hints = false, symbols = false))
    }

    @Test
    fun aKeyOffersItsSymbolsOnlyWhileTheLettersSymbolsAreShown() {
        val key = KeyAction.Letter('ь', listOf('ъ'), symbols = listOf('?'))
        val withAlternates = KeyAction.Letter('ж', symbols = "/\\|".toList())

        assertEquals(listOf('ь', '?', 'ъ'), offeredOptions(key, hints = false, symbols = true))
        assertEquals(listOf('ь', 'ъ'), offeredOptions(key, hints = true, symbols = false))
        assertEquals("ж/\\|", offeredOptions(withAlternates, hints = false, symbols = true).joinToString(""))
        assertEquals(KeyAction.Letter('ж'), offeredAction(withAlternates, true, symbols = false))
    }

    @Test
    fun theCornerShowsADigitOrASymbolWhileShownAndTheLongPressesOptionOtherwise() {
        val soft = KeyAction.Letter('ь', listOf('ъ'), symbols = listOf('?'), optionsHint = "ъ")
        val digit = KeyAction.Letter('й', hint = '1')

        assertEquals(Corner.Symbol, shownCorner(soft, hints = true, symbols = true))
        assertEquals(Corner.Options, shownCorner(soft, hints = true, symbols = false))
        assertEquals(Corner.Digit, shownCorner(digit, hints = true, symbols = true))
        assertNull(shownCorner(digit, hints = false, symbols = true))
        assertNull(shownCorner(KeyAction.Letter('ф', symbols = listOf('@')), hints = true, symbols = false))
        assertNull(shownCorner(KeyAction.Space, hints = true, symbols = true))
    }

    @Test
    fun theLettersSymbolsShowByTheirOwnSettingWhateverTheDigitRow() {
        val symbols = { hints: Boolean, digitRow: Boolean, symbols: Boolean ->
            keyLook(defaultLookSettings.copy(hints = hints, digitRow = digitRow, symbols = symbols)).symbols
        }

        assertTrue(symbols(false, false, true))
        assertTrue(symbols(true, true, true))
        assertFalse(symbols(true, true, false))
    }

    private fun offeredOptions(key: KeyAction, hints: Boolean, symbols: Boolean): List<Char> =
        (offeredAction(key, hints, symbols) as KeyAction.Letter).options

    @Test
    fun thePeriodShowsTheMarksItsLongPressOffersInTheMiddleOfItsTop() {
        val period = KeyAction.Letter('.', "…,;?!".toList())

        assertEquals("?!", marksHint(period))
        assertEquals(Alignment.TopCenter, hintAlignment(period, Page.Letters))
        assertEquals("!", marksHint(KeyAction.Letter('.', listOf('…', '!'))))
    }

    @Test
    fun aKeyThatOffersNoSentenceMarksShowsNoMarks() {
        val digit = KeyAction.Letter('й', hint = '1')

        assertNull(marksHint(KeyAction.Letter('.', listOf('…', ','))))
        assertNull(marksHint(digit))
        assertNull(marksHint(KeyAction.Space))
        assertEquals(Alignment.TopEnd, hintAlignment(digit, Page.Letters))
    }

    @Test
    fun aHintStaysInTheCornerWhileTheRoundingLeavesItRoom() {
        val square = hintInset(0.dp)

        assertEquals(HintInset(top = 0.dp, end = 1.dp), square)
        assertEquals(square, hintInset(1.dp))
    }

    @Test
    fun aHintMovesInAlongTheDiagonalJustPastARoundCornersArc() {
        val square = hintInset(0.dp)
        val round = hintInset(16.dp)

        // The arc of 16 dp is 4.69 dp in at the diagonal; the hint's corner has 0.5 dp of its own room.
        assertEquals(4.19f, (round.top - square.top).value, TOLERANCE)
        // Both edges by the same amount, along the corner's diagonal.
        assertEquals((round.top - square.top).value, (round.end - square.end).value, TOLERANCE)
        assertTrue(hintInset(8.dp).top < round.top)
    }

    @Test
    fun aHintIsGrayAndMutedOnAPressedKey() {
        val colors = lightColorScheme()
        val pressed = hintColor(pressed = true, colors)

        assertEquals(colors.onSurfaceVariant, hintColor(pressed = false, colors))
        assertEquals(pressedColors(colors).content.copy(alpha = 1f), pressed.copy(alpha = 1f))
        assertTrue(pressed.alpha < 1f)
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
