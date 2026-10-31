package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceTest {
    @Test
    fun `the word typed into an empty field begins it, blanks before it or not`() {
        assertTrue(beginsField("При", wordStart = 0))
        assertTrue(beginsField("", wordStart = 0))
        assertTrue(beginsField("  \nПри", wordStart = 3))
    }

    @Test
    fun `a word after another, or after a mark, does not begin the field`() {
        assertFalse(beginsField("Ну при", wordStart = 3))
        assertFalse(beginsField("Да. При", wordStart = 4))
        assertFalse(beginsField(":) ", wordStart = 3))
    }

    @Test
    fun `the next word goes a space after the end of a sentence or a phrase, and closing marks`() {
        assertTrue(endsWithMarkBeforeSpace("Привет."))
        assertTrue(endsWithMarkBeforeSpace("Да,"))
        assertTrue(endsWithMarkBeforeSpace("(да)"))
        assertTrue(endsWithMarkBeforeSpace("«да»"))
        assertFalse(endsWithMarkBeforeSpace("что-"))
        assertFalse(endsWithMarkBeforeSpace("Привет. "))
        assertFalse(endsWithMarkBeforeSpace("("))
        assertFalse(endsWithMarkBeforeSpace(""))
    }

    @Test
    fun `a sentence ends with a full stop, a question or an exclamation, closing brackets after them or not`() {
        assertTrue(endsSentenceBeforeSpace("Привет."))
        assertTrue(endsSentenceBeforeSpace("Как ты?"))
        assertTrue(endsSentenceBeforeSpace("(Да!)"))
        assertTrue(endsSentenceBeforeSpace("Он сказал: \"Да.\""))
        assertFalse(endsSentenceBeforeSpace("Привет,"))
        assertFalse(endsSentenceBeforeSpace("Ну…"))
    }

    @Test
    fun `a period of an abbreviation ends no sentence`() {
        assertFalse(endsSentenceBeforeSpace("т.е."))
        assertFalse(endsSentenceBeforeSpace("Это, т.е."))
        assertTrue(endsSentenceBeforeSpace("Вот т."))
    }

    @Test
    fun `a field is empty with nothing after the cursor and only blanks before it`() {
        assertTrue(isEmptyField("", ""))
        assertTrue(isEmptyField(" \n", ""))
        assertFalse(isEmptyField("Привет", ""))
        assertFalse(isEmptyField("", "Привет"))
    }

    @Test
    fun `text that does not reach back to the field's start tells nothing of it`() {
        // The text before the cursor was cut off: blanks are read, but the field holds more before them.
        assertFalse(beginsField("  При", wordStart = 40))
    }
}
