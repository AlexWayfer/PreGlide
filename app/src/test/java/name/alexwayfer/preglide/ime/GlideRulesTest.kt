package name.alexwayfer.preglide.ime

import android.text.InputType
import androidx.compose.ui.geometry.Offset
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideRulesTest {
    @Test
    fun textFieldsGetGlidesEvenOnesThatRefuseSuggestions() {
        assertTrue(glidesIn(InputType.TYPE_CLASS_TEXT))
        assertTrue(glidesIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        // YouTube's search, as it told the keyboard: multi-line, capitalized, without suggestions.
        assertTrue(glidesIn(0x2a4001))
    }

    @Test
    fun aLetterAfterAGlideStartsAWordAndAnythingElseGoesOnTheGlidedOne() {
        assertTrue(startsWordAfterGlide('и'))
        assertTrue(startsWordAfterGlide('Ё'))
        assertTrue(startsWordAfterGlide('a'))
        for (char in ",.!?-'2") assertFalse("$char", startsWordAfterGlide(char))
    }

    @Test
    fun aSpaceOrAMarkAfterAGlideLetsABackspaceBringItBackButALetterOrEnterDoesNot() {
        assertTrue(glideComesBackAfter(KeyAction.Space))
        for (char in ",.!?-'2") assertTrue("$char", glideComesBackAfter(KeyAction.Letter(char)))
        assertFalse(glideComesBackAfter(KeyAction.Letter('к')))
        assertFalse(glideComesBackAfter(KeyAction.Enter))
        assertFalse(glideComesBackAfter(KeyAction.ShowPage(Page.Symbols)))
    }

    @Test
    fun aWordStillPreviewedKeepsItsPlaceAndNewOnesTakeThePlacesLeft() {
        assertEquals(listOf("как", "так", "там"), steadyPreview(emptyList(), listOf("как", "так", "там")))
        // так goes first now, but stays where it was; там is gone, and что takes its place.
        assertEquals(
            listOf("как", "так", "что"),
            steadyPreview(listOf("как", "так", "там"), listOf("так", "что", "как")),
        )
        // With fewer words, a word whose place is gone takes one left.
        assertEquals(listOf("как", "там"), steadyPreview(listOf("как", "так", "там"), listOf("там", "как")))
    }

    @Test
    fun passwordsEmailsAndNumbersGetNoGlides() {
        assertFalse(glidesIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(glidesIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertFalse(glidesIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertFalse(glidesIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(glidesIn(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertFalse(glidesIn(InputType.TYPE_CLASS_NUMBER))
        assertFalse(glidesIn(InputType.TYPE_NULL))
    }

    @Test
    fun aGlidedWordLeavesTheCursorRightAfterIt() {
        assertEquals(SuggestionEdit(0, "привет"), glideEdit("", "привет"))
        assertEquals(SuggestionEdit(0, "как"), glideEdit("Ну привет ", "как"))
    }

    @Test
    fun aGlidedWordGetsASpaceBeforeItAfterAWordOrAMarkThatEndsAPhrase() {
        assertEquals(SuggestionEdit(0, " привет"), glideEdit("Ну пр", "привет"))
        assertEquals(SuggestionEdit(0, " как"), glideEdit("Привет,", "как"))
        assertEquals(SuggestionEdit(0, " раз"), glideEdit("в 2", "раз"))
        assertEquals(SuggestionEdit(0, " и"), glideEdit("(да)", "и"))
    }

    @Test
    fun aGlidedWordGoesRightOnAfterAnOpeningBracketOrQuoteOrADash() {
        assertFalse(glideNeedsSpace("("))
        assertFalse(glideNeedsSpace("«"))
        assertFalse(glideNeedsSpace("что-"))
        assertFalse(glideNeedsSpace("строка\n"))
    }

    @Test
    fun aGlideGoesOnAWordThatAHyphenOrAnApostropheEnds() {
        assertEquals("ИИ-", gluedPrefix("к ИИ-"))
        assertEquals("don'", gluedPrefix("I don'"))
        assertEquals("", gluedPrefix("к ИИ"))
        assertEquals("", gluedPrefix("к ИИ "))
        // A dash after a space ends no word.
        assertEquals("", gluedPrefix("к -"))
        assertEquals("", gluedPrefix(""))
    }

    @Test
    fun aChosenWordGoesASpaceAfterAMarkButReplacesAWordTyped() {
        assertTrue(choiceNeedsSpace("Привет."))
        assertTrue(choiceNeedsSpace("(да)"))
        assertFalse(choiceNeedsSpace("Привет. Ка"))
        assertFalse(choiceNeedsSpace("Привет "))
        assertFalse(choiceNeedsSpace(""))
    }

    @Test
    fun aWordChosenForTheGlidedOneGoesInWithASpace() {
        val glided = Glided(" привет", "привет", 6, listOf("приват", "пример"))

        assertEquals(SuggestionEdit(7, " пример "), glided.swappedFor("пример", textAfter = ""))
        assertEquals(SuggestionEdit(7, " привет "), glided.swappedFor("привет", textAfter = ""))
    }

    @Test
    fun aWordChosenForTheGlidedOneTakesNoSpaceWhereASpaceOrAMarkFollows() {
        val glided = Glided(" привет", "привет", 6, listOf("пример"))

        assertEquals(SuggestionEdit(7, " пример"), glided.swappedFor("пример", textAfter = " на"))
        assertEquals(SuggestionEdit(7, " пример"), glided.swappedFor("пример", textAfter = "\nна"))
        assertEquals(SuggestionEdit(7, " пример"), glided.swappedFor("пример", textAfter = ", на"))
        assertEquals(SuggestionEdit(7, " пример"), glided.swappedFor("пример", textAfter = ")"))
        // A word right after the cursor would run on into the chosen one.
        assertEquals(SuggestionEdit(7, " пример "), glided.swappedFor("пример", textAfter = "на"))
    }

    @Test
    fun anEraseTakesTheGlidedWordWithTheSpaceBeforeIt() {
        assertEquals(SuggestionEdit(7, ""), Glided(" привет", "привет", 6, emptyList()).erased())
    }

    @Test
    fun aGlideIsLastWhileTheTextEndsWithIt() {
        val glided = Glided(" привет", "привет", 3, emptyList())

        assertTrue(glided.isLast("Ну привет"))
        assertFalse(glided.isLast("Ну привет,"))
    }

    @Test
    fun aGlideStartsOnALetterOrTheSpaceBarOfTheLetters() {
        assertTrue(glidesFrom(KeyAction.Letter('п'), Page.Letters))
        assertFalse(glidesFrom(KeyAction.Letter(','), Page.Letters))
        assertFalse(glidesFrom(KeyAction.Letter('п'), Page.Symbols))
        // A finger aimed at a letter of the bottom row may land on the space bar.
        assertTrue(glidesFrom(KeyAction.Space, Page.Letters))
        assertFalse(glidesFrom(KeyAction.Space, Page.Symbols))
        assertFalse(glidesFrom(KeyAction.Backspace, Page.Letters))
    }

    @Test
    fun aFingerGlidesOnceItGoesHalfAKeyAway() {
        assertEquals(20f, glideDistance(keyWidth = 40))
        assertFalse(movedPast(Offset(10f, 10f), Offset(30f, 10f), glideDistance(keyWidth = 40)))
        assertTrue(movedPast(Offset(10f, 10f), Offset(10f, 31f), glideDistance(keyWidth = 40)))
    }
}
