package name.alexwayfer.preglide.engine.edit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleSpacePeriodTest {
    @Test
    fun twoConsecutiveSpacesAfterAWordPutAPeriodInItsPlace() {
        for (text in listOf("Привет ", "hello ", "42 ", "𐐀 ", "(hello) ", "«слово» ")) {
            val period = DoubleSpacePeriod()
            period.spaced(text)
            assertTrue(text, period.space(text))
            assertTrue(period.undo(text.dropLast(1) + ". "))
            assertFalse(period.undo(text.dropLast(1) + ". "))
        }
    }

    @Test
    fun whitespaceAndPunctuationDoNotEndAnotherSentence() {
        for (text in listOf("", " ", "  ", "\n ", "hello. ", "hello! ", "hello, ", "hello  ", "() ", "hello")) {
            val period = DoubleSpacePeriod()
            period.spaced(text)
            assertFalse(text, period.space(text))
        }
    }

    @Test
    fun onlyAnExplicitSpaceAtTheUnchangedTextCanCompleteThePair() {
        val period = DoubleSpacePeriod()
        assertFalse(period.space("hello "))
        period.spaced("hello ")
        assertTrue(period.space("hello "))
        period.spaced("hello ")
        assertFalse(period.space("other "))
    }

    @Test
    fun anotherKeyCancelsThePairAndOnlyBackspaceKeepsUndo() {
        val period = DoubleSpacePeriod()
        period.spaced("hello ")
        period.otherKey(backspace = false)
        assertFalse(period.space("hello "))
        period.spaced("hello ")
        assertTrue(period.space("hello "))
        period.otherKey(backspace = true)
        assertTrue(period.undo("hello. "))
        period.spaced("hello ")
        assertTrue(period.space("hello "))
        period.otherKey(backspace = false)
        assertFalse(period.undo("hello. "))
    }

    @Test
    fun undoChecksTheTextAndForgetClearsEverything() {
        val period = DoubleSpacePeriod()
        period.spaced("hello ")
        assertTrue(period.space("hello "))
        assertFalse(period.undo("other. "))
        assertFalse(period.undo("hello. "))
        period.spaced("hello ")
        period.forget()
        assertFalse(period.space("hello "))
        period.spaced("hello ")
        assertTrue(period.space("hello "))
        period.forget()
        assertFalse(period.undo("hello. "))
    }
}
