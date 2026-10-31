package name.alexwayfer.preglide.ime

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionsTest {
    /** A field that holds its text, with the cursor at its end. */
    private class FakeField(var text: String) : SuggestionField {
        override val inputType = InputType.TYPE_CLASS_TEXT
        override val imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = ""

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
        }
    }

    private val field = FakeField("Ну пртвет")
    private val ownSpace = OwnSpace(field)
    private val corrections = Corrections(field, ownSpace) { word ->
        pick("пртвет", word).also { field.replace(it.deleteBefore, it.text) }
    }

    @Test
    fun aSpaceBringsTheWordWithASpaceAfterIt() {
        assertEquals(SuggestionEdit(6, "привет "), corrections.correct("привет", ' '))
        assertEquals("Ну привет ", field.text)
    }

    @Test
    fun aMarkBringsTheWordAloneForTheMarkToFollow() {
        assertEquals(SuggestionEdit(6, "привет"), corrections.correct("привет", ','))
        assertEquals("Ну привет", field.text)
    }

    @Test
    fun aBackspaceRightAfterPutsTheWordBackAsTypedAndKeepsIt() {
        corrections.correct("привет", ' ')

        assertEquals(SuggestionEdit(7, "пртвет"), corrections.revert())
        assertEquals("Ну пртвет", field.text)
        assertTrue(corrections.keeps("Пртвет"))
        assertFalse(corrections.keeps("привет"))
        // Once undone, a backspace deletes as usual.
        assertNull(corrections.revert())
    }

    @Test
    fun aBackspaceRightAfterTheMarkPutsTheWordBackWithoutIt() {
        corrections.correct("привет", ',')
        field.replace(0, ",")

        assertEquals(SuggestionEdit(7, "пртвет"), corrections.revert())
        assertEquals("Ну пртвет", field.text)
        assertTrue(corrections.keeps("пртвет"))
    }

    @Test
    fun aForgottenCorrectionIsPastUndoing() {
        corrections.correct("привет", ' ')
        corrections.forget()
        assertNull(corrections.revert())
        assertEquals("Ну привет ", field.text)
    }

    @Test
    fun aChangedTextIsNotUndone() {
        corrections.correct("привет", ' ')
        field.text = "Ну привет как"

        assertNull(corrections.revert())
        assertEquals("Ну привет как", field.text)
    }

    @Test
    fun aClearedFieldKeepsNoWord() {
        corrections.correct("привет", ' ')
        corrections.revert()
        corrections.clear()

        assertFalse(corrections.keeps("пртвет"))
    }

    @Test
    fun undoingPutsTheTypedWordInPlaceOfWhatAutocorrectTyped() {
        assertEquals(SuggestionEdit(7, "пртвет"), revertEdit("Ну привет ", "пртвет", "привет "))
        assertNull(revertEdit("Ну привет как", "пртвет", "привет "))
    }
}
