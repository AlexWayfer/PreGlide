package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.learn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StripRulesTest {
    @Test
    fun theBestSuggestionTakesTheMiddleSlot() {
        assertEquals(listOf("привести", "привет", "приводит"), stripSlots(listOf("привет", "привести", "приводит")))
    }

    @Test
    fun fewSuggestionsLeaveSlotsEmpty() {
        assertEquals(listOf(null, "щас", null), stripSlots(listOf("щас")))
        assertEquals(listOf("ага", "агат", null), stripSlots(listOf("агат", "ага")))
        assertEquals(listOf(null, null, null), stripSlots(emptyList()))
    }

    @Test
    fun theTypedWordTakesTheLeftSlotBesideSuggestions() {
        val suggestions = listOf("привет", "привести", "приводит")
        assertEquals(listOf("прив", "привет", "привести"), stripSlots(suggestions, "прив"))
        assertEquals(listOf("кринж", "кринжовый", null), stripSlots(listOf("кринжовый"), "кринж"))
    }

    @Test
    fun onlyTheWordsPutOnTheStartBeforeAHyphenThatAreKnownNoWholeAreGlued() {
        val model = LanguageModel(Dictionary.parse(sequenceOf("кто-то\t30", "кто\t40")))
        model.personal.learn("кто-нибудь", previous = null, time = 0)
        val suggestions = listOf("Кто-то", "Кто-нибудь", "Кто-тот")

        assertEquals(GluedWords("Кто-", setOf("Кто-тот")), gluedWords("Ну Кто-", suggestions, model))
        assertNull(gluedWords("Ну кто", listOf("кто"), model))
    }

    @Test
    fun theTypedWordAloneTakesTheMiddleSlot() {
        assertEquals(listOf(null, "кринжово", null), stripSlots(emptyList(), "кринжово"))
    }

    @Test
    fun theTypedWordIsShownAsItIs() {
        assertEquals("кринж", typedToKeep("Ну кринж"))
        assertEquals("Привет", typedToKeep("Ну Привет"))
        assertEquals("ИИ", typedToKeep("Ну ИИ"))
        assertNull(typedToKeep("Ну "))
        assertNull(typedToKeep(""))
        // A number's ending is no word to keep.
        assertNull(typedToKeep("с 2-гн"))
    }

    @Test
    fun theTypedWordIsNotSuggestedTwice() {
        assertEquals(listOf("кринжово"), withoutTyped(listOf("Кринж", "кринжово"), "кринж"))
        assertEquals(listOf("кринж"), withoutTyped(listOf("кринж"), null))
    }

    @Test
    fun theTypedWordIsNoSuggestionToRemove() {
        assertFalse(isRemovable("кринж", "кринж"))
        assertTrue(isRemovable("кринжовый", "кринж"))
        assertTrue(isRemovable("кринж", null))
        assertTrue(isRemovable("кринж", "кринж", typedWordRemovable = true))
        assertFalse(isRemovable(null, "кринж"))
        assertFalse(isRemovable(null, null))
    }

    @Test
    fun onlyKnownWordsNotAlreadyRemovedCanBeRemovedWhenTyped() {
        val model = LanguageModel(Dictionary.parse(sequenceOf("привет\t30")))
        model.personal.learn("бер", previous = null, time = 0)

        assertTrue(typedWordRemovable("Бер", model))
        assertTrue(typedWordRemovable("Привет", model))
        assertFalse(typedWordRemovable("бе", model))
        assertFalse(typedWordRemovable(null, model))
        assertFalse(typedWordRemovable("бер", null))
        model.personal.remove("привет", time = 1)
        assertFalse(typedWordRemovable("Привет", model))
    }

    @Test
    fun autocorrectsWordComesFirst() {
        val words = listOf("привести", "привет", "приводит")

        assertEquals(listOf("привет", "привести", "приводит"), firstOf(words, "привет"))
        assertEquals(listOf("привести", "привет"), firstOf(listOf("привести", "привет"), null))
    }

    @Test
    fun theMiddleSlotIsTheBestAndAutocorrectsWordIsMarked() {
        assertEquals(SlotLook(best = true, correcting = true), slotLook(BEST_SLOT, "привет", "привет"))
        assertEquals(SlotLook(best = false, correcting = false), slotLook(0, "пртвет", "привет"))
        assertEquals(SlotLook(best = true, correcting = false), slotLook(BEST_SLOT, "привет", null))
        assertEquals(SlotLook(best = false, correcting = false), slotLook(2, null, null))
    }

    @Test
    fun theWordsOfTheStripAreTakenWithTheTurnTheyCameIn() {
        val state = KeyboardState().apply {
            suggestions = listOf("привет", "приват")
            glidedWord = "привет"
            nextWordsTurns = 3
        }

        val words = stripWords(state)

        assertEquals(listOf("приват", "привет", null), words.slots.map { (it as? SlotContent.Word)?.word })
        assertEquals("привет", words.glidedWord)
        assertEquals(null, words.typedWord)
        assertEquals(null, words.correction)
        assertEquals(3, words.turn)
    }

    @Test
    fun theGlidedWordAmongItsOthersIsMarked() {
        assertEquals(
            SlotLook(best = true, correcting = false, glided = true),
            slotLook(BEST_SLOT, "привет", null, glided = "привет"),
        )
        assertEquals(SlotLook(best = false, correcting = false), slotLook(0, "приват", null, glided = "привет"))
        assertEquals(SlotLook(best = false, correcting = false), slotLook(2, null, null, glided = "привет"))
    }

    @Test
    fun aNextWordRightAfterAMarkTakesTheCapitalTheFieldWantsAfterTheSpace() {
        val sentences = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        assertEquals(ShiftState.Once, shiftForNextWord(ShiftState.Off, "Привет.", sentences))
        assertEquals(ShiftState.Off, shiftForNextWord(ShiftState.Off, "Привет,", sentences))
        assertEquals(ShiftState.Off, shiftForNextWord(ShiftState.Off, "Привет.", InputType.TYPE_CLASS_TEXT))
        // With a space or a word typed, the field tells the keys itself.
        assertEquals(ShiftState.Off, shiftForNextWord(ShiftState.Off, "Привет. ", sentences))
        assertEquals(ShiftState.Locked, shiftForNextWord(ShiftState.Locked, "Привет,", sentences))
    }

    @Test
    fun aFieldWantsACapitalAfterTheSpaceAsAndroidTellsIt() {
        val text = InputType.TYPE_CLASS_TEXT
        assertTrue(wantsCapitalAfterSpace("да,", text or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS))
        assertTrue(wantsCapitalAfterSpace("да,", text or InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        // Sentences' capitals leave words' capitals unasked, as Android does.
        val both = text or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        assertFalse(wantsCapitalAfterSpace("да,", both))
        assertTrue(wantsCapitalAfterSpace("да!", both))
        assertFalse(wantsCapitalAfterSpace("да.", text))
    }
}
