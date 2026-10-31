package name.alexwayfer.preglide.ime

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSpaceTest {
    @Test
    fun aMarkAfterAWordTakesTheKeyboardsOwnSpace() {
        for (mark in ".,!?;:…»") {
            val edit = markAfterOwnSpace(mark, "Ну привет ", "привет ")

            assertEquals(SuggestionEdit(deleteBefore = 1, text = "$mark "), edit)
        }
    }

    @Test
    fun lettersDashesAndQuotesKeepTheSpace() {
        for (char in "а-—\"«(") {
            assertNull(char.toString(), markAfterOwnSpace(char, "Ну привет ", "привет "))
        }
    }

    @Test
    fun aQuoteThatClosesAQuotationTakesTheKeyboardsOwnSpace() {
        assertEquals(
            SuggestionEdit(deleteBefore = 1, text = "\" "),
            markAfterOwnSpace('"', "Он сказал \"ну привет ", "привет "),
        )
        assertEquals(
            SuggestionEdit(deleteBefore = 1, text = "` "),
            markAfterOwnSpace('`', "Запусти `verify ", "verify "),
        )
        // The space after a mark that ends the quotation is the keyboard's own too.
        assertEquals(
            SuggestionEdit(deleteBefore = 1, text = "\" "),
            markAfterOwnSpace('"', "Он сказал \"привет. ", ". "),
        )
    }

    @Test
    fun aQuoteAfterAClosedQuotationKeepsTheSpace() {
        assertNull(markAfterOwnSpace('"', "Он сказал \"привет\" ", "\" "))
    }

    @Test
    fun aBracketThatClosesOneOpenTakesTheKeyboardsOwnSpace() {
        assertEquals(
            SuggestionEdit(deleteBefore = 1, text = ") "),
            markAfterOwnSpace(')', "Привет (кстати ", "кстати "),
        )
        assertEquals(
            SuggestionEdit(deleteBefore = 1, text = "] "),
            markAfterOwnSpace(']', "Ссылка [тут ", "тут "),
        )
        // Nested brackets close one after another.
        assertEquals(
            SuggestionEdit(deleteBefore = 1, text = ") "),
            markAfterOwnSpace(')', "((Привет) ", ") "),
        )
    }

    @Test
    fun aBracketWithNoneOpenKeepsTheSpaceForAnEmote() {
        assertNull(markAfterOwnSpace(')', "Привет ", "Привет "))
        assertNull(markAfterOwnSpace(']', "Привет ", "Привет "))
        assertNull(markAfterOwnSpace(')', "(Привет) ", ") "))
    }

    @Test
    fun aBackspaceBringsTheSpaceBackBeforeTheMarksThatTookIt() {
        assertEquals(
            SuggestionEdit(deleteBefore = 2, text = " :"),
            spaceBackBeforeMarks("Ну привет: ", MarkRun("привет ", ":")),
        )
        assertEquals(
            SuggestionEdit(deleteBefore = 4, text = " ..."),
            spaceBackBeforeMarks("Привет... ", MarkRun("Привет ", "...")),
        )
        assertEquals(
            SuggestionEdit(deleteBefore = 3, text = " ?!"),
            spaceBackBeforeMarks("Чё?! ", MarkRun("Чё ", "?!")),
        )
    }

    @Test
    fun theSpaceStaysWhereTheTextChangedSinceTheMarks() {
        assertNull(spaceBackBeforeMarks("Ну привет:)", MarkRun("привет ", ":")))
        assertNull(spaceBackBeforeMarks("Ну привет :", MarkRun("привет ", ":")))
    }

    @Test
    fun aBackspaceAfterTheSpaceCameBackErasesTheLastMark() {
        val erased = SuggestionEdit(deleteBefore = 1, text = "")

        assertEquals(erased, markErasedAfterSpace("Ну привет :", MarkRun("привет ", ":")))
        assertEquals(erased, markErasedAfterSpace("Чё ?!", MarkRun("Чё ", "?!")))
        assertNull(markErasedAfterSpace("Ну привет :P", MarkRun("привет ", ":")))
    }

    @Test
    fun aSpaceRightAfterTheKeyboardsOwnOneDoublesIt() {
        assertTrue(spaceDoublesOwnSpace("Ну привет ", "привет "))
        assertTrue(spaceDoublesOwnSpace("Ну привет. ", ". "))
    }

    @Test
    fun aSpaceAfterAnythingElseDoesNotDoubleTheKeyboardsOwnOne() {
        assertFalse(spaceDoublesOwnSpace("Ну привет ", null))
        // The cursor moved, and the text before it ends otherwise.
        assertFalse(spaceDoublesOwnSpace("Ну пока ", "привет "))
        // What the keyboard typed ends with no space of its own.
        assertFalse(spaceDoublesOwnSpace("alex@gmail.com", "alex@gmail.com"))
    }

    @Test
    fun aHyphenRightAfterAWordAndTheKeyboardsOwnSpaceMayBeGlued() {
        assertTrue(hyphenAfterOwnSpace('-', "Ну слово ", "слово "))
        assertFalse(hyphenAfterOwnSpace('—', "Ну слово ", "слово "))
        assertFalse(hyphenAfterOwnSpace('-', "Ну слово ", null))
        // A hyphen after a mark opens a line of dialog.
        assertFalse(hyphenAfterOwnSpace('-', "Ну привет. ", ". "))
        // The cursor moved, and the text before it ends otherwise.
        assertFalse(hyphenAfterOwnSpace('-', "Ну пока ", "слово "))
    }

    @Test
    fun aLetterGluesTheHyphenToTheWordBeforeTheSpace() {
        assertEquals(SuggestionEdit(deleteBefore = 2, text = "-"), hyphenGlue('т', "Ну слово -", "слово -"))
    }

    @Test
    fun aSpaceADigitOrAMovedCursorLeaveTheHyphenADash() {
        assertNull(hyphenGlue(' ', "Ну слово -", "слово -"))
        assertNull(hyphenGlue('5', "Ну слово -", "слово -"))
        assertNull(hyphenGlue('т', "Ну слово -", null))
        assertNull(hyphenGlue('т', "Ну пока -", "слово -"))
    }

    @Test
    fun aHyphenAfterTheKeyboardsOwnSpaceGoesOnTheWordOnceALetterFollows() {
        val field = TextField("Ну слово ")
        val events = mutableListOf<OwnSpaceEvent>()
        val ownSpace = OwnSpace(field, events::add)
        ownSpace.placed("слово ")
        ownSpace.type('-')
        assertEquals("Ну слово -", field.text)

        assertEquals(SuggestionEdit(deleteBefore = 2, text = "-"), ownSpace.glueHyphen('т'))
        ownSpace.type('т')

        assertEquals("Ну слово-т", field.text)
        assertEquals(listOf(OwnSpaceEvent.Placed, OwnSpaceEvent.HyphenGlued), events)
        // Glued once: the next letter goes on as typed.
        assertNull(ownSpace.glueHyphen('о'))
    }

    @Test
    fun aDashAfterTheKeyboardsOwnSpaceKeepsItsSpaces() {
        val field = TextField("Ну слово ")
        val ownSpace = OwnSpace(field)
        ownSpace.placed("слово ")
        ownSpace.type('-')

        assertNull(ownSpace.glueHyphen(' '))
        ownSpace.type(' ')
        assertNull(ownSpace.glueHyphen('т'))

        assertEquals("Ну слово - ", field.text)
    }

    @Test
    fun aHyphenForgottenWithTheSpaceIsNotGlued() {
        val field = TextField("Ну слово ")
        val ownSpace = OwnSpace(field)
        ownSpace.placed("слово ")
        ownSpace.type('-')
        ownSpace.forget()

        assertNull(ownSpace.glueHyphen('т'))
        assertEquals("Ну слово -", field.text)
    }

    @Test
    fun aSpaceTheKeyboardDidNotPutThereStays() {
        assertNull(markAfterOwnSpace('.', "Ну привет ", null))
        // The cursor moved, and the text before it ends otherwise.
        assertNull(markAfterOwnSpace('.', "Ну пока ", "привет "))
    }

    /** A field with the cursor at the end of its [text]. */
    private class TextField(var text: String) : SuggestionField {
        override val inputType = InputType.TYPE_CLASS_TEXT
        override val imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = ""

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
        }
    }
}
