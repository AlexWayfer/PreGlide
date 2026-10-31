package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BracketsTest {
    @Test
    fun aBracketBeforeAWordOpens() {
        assertTrue(bracketIsOpen(')', "Привет (кстати"))
        assertTrue(bracketIsOpen(')', "(Кстати, давно не виделись. Как ты"))
        assertTrue(bracketIsOpen(']', "Ссылка [тут"))
    }

    @Test
    fun aBracketAfterAWordCloses() {
        assertFalse(bracketIsOpen(')', "Привет (кстати) как"))
        assertFalse(bracketIsOpen(']', "Ссылка [тут] и"))
    }

    @Test
    fun withNoBracketOpenNothingCloses() {
        assertFalse(bracketIsOpen(')', "Привет"))
        assertFalse(bracketIsOpen(')', "Привет))"))
    }

    @Test
    fun nestedBracketsCloseOneByOne() {
        assertTrue(bracketIsOpen(')', "((Привет"))
        assertTrue(bracketIsOpen(')', "((Привет)"))
        assertFalse(bracketIsOpen(')', "((Привет))"))
        assertTrue(bracketIsOpen(')', "(раз (два) три"))
    }

    @Test
    fun emotesAndSmileysDoNothing() {
        assertFalse(bracketIsOpen(')', "Ну (( ладно"))
        assertTrue(bracketIsOpen(')', "Ну (кстати )) ладно"))
        assertTrue(bracketIsOpen(')', "Ну (кстати :) ладно"))
        assertTrue(bracketIsOpen(')', "Ну (кстати ;) ладно"))
        assertFalse(bracketIsOpen(')', "Пустые () скобки"))
    }

    @Test
    fun aBracketEndsWithItsLine() {
        assertFalse(bracketIsOpen(')', "Привет (кстати\nКак ты"))
    }

    @Test
    fun eachKindOfBracketCountsApart() {
        assertFalse(bracketIsOpen(']', "Привет (кстати"))
        assertTrue(bracketIsOpen(')', "Привет (см. [1] тут"))
    }
}
