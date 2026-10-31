package name.alexwayfer.preglide.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionRulesTest {
    @Test
    fun textFieldsGetSuggestions() {
        assertTrue(offersSuggestions(InputType.TYPE_CLASS_TEXT))
        assertTrue(offersSuggestions(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertTrue(offersSuggestions(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
    }

    @Test
    fun aTextFieldThatAsksForNoSuggestionsGetsThemAndTeachesTheKeyboard() {
        // YouTube's comment field.
        val comment = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        assertTrue(offersSuggestions(comment))
        assertTrue(learnsFrom(comment, 0))
        assertTrue(correctsIn(comment))
    }

    @Test
    fun passwordsAndNumbersGetNone() {
        assertFalse(offersSuggestions(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(offersSuggestions(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertFalse(offersSuggestions(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertFalse(offersSuggestions(InputType.TYPE_CLASS_NUMBER))
        assertFalse(offersSuggestions(InputType.TYPE_CLASS_PHONE))
        assertFalse(offersSuggestions(InputType.TYPE_NULL))
    }

    @Test
    fun theKeyboardLearnsFromTextFields() {
        assertTrue(learnsFrom(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 0))
    }

    @Test
    fun theKeyboardDoesNotLearnAddressesPasswordsOrInIncognito() {
        val text = InputType.TYPE_CLASS_TEXT
        assertFalse(learnsFrom(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0))
        assertFalse(learnsFrom(text or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, 0))
        assertFalse(learnsFrom(text or InputType.TYPE_TEXT_VARIATION_URI, 0))
        assertFalse(learnsFrom(text or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0))
        assertFalse(learnsFrom(text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
    }

    @Test
    fun pickingReplacesTheTypedWordAndAddsASpace() {
        assertEquals(SuggestionEdit(deleteBefore = 4, text = "Привет "), pick("Прив", "Привет"))
        assertEquals(SuggestionEdit(deleteBefore = 4, text = "Привет "), pick("Ну. Прив", "Привет"))
    }

    @Test
    fun aWordPickedBeforeTextReplacesTheWholeWordAndAddsNoSpace() {
        assertEquals(SuggestionEdit(deleteBefore = 4, text = "Привет"), pick("Ну прив", "Привет", " всем"))
        assertEquals(SuggestionEdit(deleteBefore = 4, text = "Привет"), pick("Ну прив", "Привет", ", всем"))
        assertEquals(SuggestionEdit(4, "Привет", deleteAfter = 2), pick("Ну прив", "Привет", "ет всем"))
        assertEquals(SuggestionEdit(4, "Привет ", deleteAfter = 2), pick("Ну прив", "Привет", "ет"))
    }

    @Test
    fun aWordPickedForOneTypedRightBeforeAnotherLeavesThatOneAfterIt() {
        assertEquals(SuggestionEdit(3, "https"), pick("htt", "https", "alexwayfer.name", wordAhead = "alexwayfer"))
        assertEquals(SuggestionEdit(3, "https"), pick("htt", "https", "alexwayfer", wordAhead = "alexwayfer"))
    }

    @Test
    fun aWordPickedThatEndsWithTheWordAheadTakesItIn() {
        val edit = pick("п", "привет", "ривет всем", wordAhead = "ривет")

        assertEquals(SuggestionEdit(1, "привет", deleteAfter = 5), edit)
    }

    @Test
    fun aWordPickedRightAfterAMarkGoesASpaceAfterIt() {
        assertEquals(SuggestionEdit(deleteBefore = 0, text = " Как "), pick("Привет.", "Как"))
        assertEquals(SuggestionEdit(deleteBefore = 0, text = "как "), pick("Привет, ", "как"))
        assertEquals(SuggestionEdit(deleteBefore = 0, text = "Привет "), pick("", "Привет"))
    }

    @Test
    fun eachLanguageHasItsDictionaryAssets() {
        assertEquals("dictionaries/ru.tsv", dictionaryAsset(Language.Russian))
        assertEquals("dictionaries/en.tsv", dictionaryAsset(Language.English))
        assertEquals("dictionaries/ru.next.tsv", nextWordsAsset(Language.Russian))
    }

    @Test
    fun eachLanguageHasItsPredictionAssetsInOneFolder() {
        assertEquals("prediction/ru.tflite", predictionAsset(Language.Russian))
        assertEquals("prediction/en.vocabulary.txt", vocabularyAsset(Language.English))
        assertEquals(PREDICTION_FOLDER, predictionAsset(Language.English).substringBefore('/'))
    }

    @Test
    fun autocorrectWorksInTextFieldsThatAskForItOrTakeManyLines() {
        val text = InputType.TYPE_CLASS_TEXT
        val corrected = text or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT

        assertTrue(correctsIn(corrected))
        assertTrue(correctsIn(corrected or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertTrue(correctsIn(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(correctsIn(text))
        assertFalse(correctsIn(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(correctsIn(corrected or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(correctsIn(corrected or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(correctsIn(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
    }

    @Test
    fun aSpaceOrAMarkRightAfterAWordEndsItForAutocorrect() {
        for (char in " .,!?;:…»") assertTrue("$char", endsWordForCorrection(char, "слово"))
        for (char in "-'\"(@а1\n") assertFalse("$char", endsWordForCorrection(char, "слово"))
    }

    @Test
    fun aClosingQuoteOrBracketEndsAWordOnlyWhenOneIsOpen() {
        assertTrue(endsWordForCorrection('"', "Он сказал \"слово"))
        assertTrue(endsWordForCorrection(')', "Так (слово"))
        // A bracket with none open is an emote, and a quote with none open opens one.
        assertFalse(endsWordForCorrection(')', "Так слово"))
        assertFalse(endsWordForCorrection('"', "Так слово"))
    }

    @Test
    fun aNextWordTakesTheCaseShiftWouldType() {
        assertEquals("всем", casedForShift("всем", ShiftState.Off))
        assertEquals("Всем", casedForShift("всем", ShiftState.Once))
        assertEquals("ВСЕМ", casedForShift("всем", ShiftState.Locked))
        assertEquals("Москва", casedForShift("Москва", ShiftState.Off))
    }

    @Test
    fun aWordBeingTypedIsSuggestedInCapsOnlyWithCapsLock() {
        assertEquals("TDLIB", casedForCapsLock("TDLib", ShiftState.Locked))
        assertEquals("TDLib", casedForCapsLock("TDLib", ShiftState.Off))
        // Shift pressed in the middle of a word capitalizes the next letter, not the word's first.
        assertEquals("привет", casedForCapsLock("привет", ShiftState.Once))
    }
}
