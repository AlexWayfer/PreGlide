package name.alexwayfer.preglide.ime

import android.text.InputType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.emoji.Emoji
import name.alexwayfer.preglide.engine.emoji.EmojiGroup
import name.alexwayfer.preglide.engine.emoji.EmojiName
import name.alexwayfer.preglide.engine.emoji.EmojiNames
import name.alexwayfer.preglide.engine.emoji.EmojiPalette
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.AFTER_NUMBER
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.NextWords
import name.alexwayfer.preglide.engine.suggest.PersonalWords
import name.alexwayfer.preglide.engine.suggest.learn
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class WordSuggestionsTest {
    /** A field that holds its text before the cursor and [after] it, none unless a test puts some there. */
    private class FakeField(var text: String, override var inputType: Int = InputType.TYPE_CLASS_TEXT) :
        SuggestionField {
        var after = ""

        override val imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = after.take(length)

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
            after = after.drop(deleteAfter)
        }
    }

    private val keyboard = KeyboardState()
    private val field = FakeField("Ну прив")
    private val loaded = mutableListOf<Language>()

    // Unconfined runs a load at once, in the call that starts it.
    private val now = testDispatchers.now

    /** Models that load with [readModel] in [loadContext]. */
    private fun models(loadContext: CoroutineContext = now, readModel: (Language) -> LanguageModel) =
        LanguageModels(CoroutineScope(now), loadContext, readModel)

    private val models = models {
        loaded += it
        when (it) {
            Language.Russian -> {
                val dictionary = Dictionary.parse(
                    sequenceOf("привет\t30", "привести\t20", "приводит\t10", "пока\t5", "всем\t4", "как\t3"),
                )
                LanguageModel(dictionary, NextWords.parse(sequenceOf("привет\tвсем\t9", "привет\tкак\t8"), dictionary))
            }
            Language.English -> LanguageModel(Dictionary.parse(sequenceOf("hello\t10")))
        }
    }
    private val suggestions = suggestionsOf(models)

    /** Suggestions in the field from [models], which load once [available]. */
    private fun suggestionsOf(models: LanguageModels, available: () -> Boolean = { true }) =
        WordSuggestions(keyboard, field, KeyboardWork(CoroutineScope(now), now, available), WordSources(models))

    private fun readEmoji() {
        keyboard.emoji.palette = EmojiPalette(mapOf(EmojiGroup.Food to listOf(Emoji("🍕"))))
        keyboard.emoji.names = EmojiNames(listOf(EmojiName("🍕", "пицца")))
    }

    @Test
    fun theStripOffersEmojiForTheWordBeforeTheCursor() {
        readEmoji()
        field.text = "Ну пицца"

        suggestions.update()

        assertEquals(listOf("🍕"), keyboard.stripEmoji)
    }

    @Test
    fun aPasswordGetsNoEmoji() {
        readEmoji()
        field.text = "пицца"
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        suggestions.update()

        assertEquals(emptyList<String>(), keyboard.stripEmoji)
    }

    @Test
    fun anEmailFieldGetsNoEmoji() {
        readEmoji()
        field.text = "пицца"
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        suggestions.update()

        assertEquals(emptyList<String>(), keyboard.stripEmoji)
    }

    @Test
    fun suggestionsFinishTheWordBeforeTheCursor() {
        suggestions.update()

        assertEquals(listOf("привет", "привести", "приводит"), keyboard.suggestions)
    }

    @Test
    fun rightAfterAMarkTheNextWordsComeAndOneChosenGoesASpaceAfterIt() {
        field.text = "привет,"
        suggestions.update()
        assertEquals(listOf("всем", "как"), keyboard.suggestions)

        suggestions.choose("всем")

        assertEquals("привет, всем ", field.text)
    }

    @Test
    fun aNumberAndAHyphenGetItsEndingsAndOneChosenGoesRightOn() {
        field.text = "с 2-"
        suggestions.update()
        assertEquals(listOf("й", "го", "х"), keyboard.suggestions)

        field.text = "с 2-г"
        suggestions.update()
        assertEquals(listOf("го"), keyboard.suggestions)
        assertEquals(null, keyboard.typedWord)

        suggestions.choose("го")
        assertEquals("с 2-го ", field.text)
    }

    @Test
    fun theWordsTheUserTypedAfterANumberComeAmongItsEndings() {
        suggestions.update()
        suggestions.model(Language.Russian)!!.personal.learn("разовое", listOf(AFTER_NUMBER), 1)
        field.text = "с 2-"

        suggestions.update()

        assertEquals(listOf("й", "разовое", "го"), keyboard.suggestions)
    }

    @Test
    fun afterAHyphenTheNextWordsAfterThePartBeforeItGoOnTheWord() {
        field.text = "Ну привет-"
        suggestions.update()
        assertEquals(listOf("привет-всем", "привет-как"), keyboard.suggestions)
        assertEquals(GluedWords("привет-", setOf("привет-всем", "привет-как")), keyboard.gluedWords)

        suggestions.choose("привет-всем")
        assertEquals("Ну привет-всем ", field.text)
        suggestions.update()
        assertEquals(null, keyboard.gluedWords)
    }

    @Test
    fun aWholeWordWithAHyphenTheUserTypedIsNoLongerGlued() {
        suggestions.update()
        suggestions.model(Language.Russian)!!.personal.learn("привет-всем", previous = null, time = 1)
        field.text = "Ну привет-"

        suggestions.update()

        assertEquals(listOf("привет-всем", "привет-как"), keyboard.suggestions)
        assertEquals(GluedWords("привет-", setOf("привет-как")), keyboard.gluedWords)
    }

    @Test
    fun anEmptyFieldOffersTheWordsTheUserOftenBeginsWithInTheCaseShiftWouldType() {
        field.text = ""
        suggestions.update()
        repeat(3) { suggestions.model(Language.Russian)!!.personal.starts.learn("привет", time = 1) }
        keyboard.shift = ShiftState.Once

        suggestions.update()

        assertEquals(listOf("Привет"), keyboard.suggestions)
        assertTrue(keyboard.fieldStart)
    }

    @Test
    fun anEmptyFieldWithNoFrequentFirstWordStaysClear() {
        field.text = ""
        suggestions.update()
        suggestions.model(Language.Russian)!!.personal.starts.learn("привет", time = 1)

        suggestions.update()

        assertEquals(emptyList<String>(), keyboard.suggestions)
        assertEquals(StripContent.Empty, stripContent(keyboard))
    }

    @Test
    fun theFirstLetterTypedTurnsTheWordsToBeginWithIntoCompletions() {
        field.text = ""
        suggestions.update()
        repeat(3) { suggestions.model(Language.Russian)!!.personal.starts.learn("пока", time = 1) }
        suggestions.update()

        field.text = "при"
        suggestions.update()

        assertEquals(listOf("привет", "привести", "приводит"), keyboard.suggestions)
        assertFalse(keyboard.fieldStart)
    }

    @Test
    fun aGlideTypesItsWordAndTheStripOffersTheOthersInItsPlace() {
        field.text = "Ну "
        suggestions.update()
        val glides = suggestions.glides
        val words = glides.reading()!!.words(glideThrough("привет"))
        assertEquals("привет", words.first())

        assertEquals(SuggestionEdit(0, "привет"), glides.type(words, 3))
        suggestions.update()

        assertEquals("Ну привет", field.text)
        assertEquals(words, keyboard.suggestions)
        assertEquals(Glided("привет", "привет", 3, words.drop(1)), glides.last())
    }

    @Test
    fun aWordChosenForTheGlidedOneGoesInWithASpaceAndTheStripSuggestsTheNext() {
        val glides = suggestions.glides
        field.text = "Ну "
        suggestions.update()
        glides.type(listOf("приводит", "привет"), 3)

        glides.swap("привет")
        assertEquals("Ну привет ", field.text)
        suggestions.update()

        assertEquals(listOf("всем", "как"), keyboard.suggestions.take(2))
        assertEquals(null, glides.erase())
    }

    @Test
    fun aWordChosenForTheGlidedOneBeforeASpaceTakesNoSpaceOfItsOwn() {
        val glides = suggestions.glides
        field.text = "Ну "
        field.after = " всем"
        suggestions.update()
        glides.type(listOf("приводит", "привет"), 3)

        glides.swap("привет")

        assertEquals("Ну привет", field.text)
        assertEquals(" всем", field.after)
        // No space of the keyboard's own to drop: a space typed now is the user's.
        assertFalse(suggestions.ownSpace.dropsSpace())
    }

    @Test
    fun aBackspaceErasesTheGlidedWordWithTheSpaceBeforeIt() {
        val glides = suggestions.glides
        field.text = "Ну пр"
        suggestions.update()
        glides.type(listOf("привет", "приводит"), 6)
        assertEquals("Ну пр привет", field.text)

        assertEquals(SuggestionEdit(7, ""), glides.erase())
        assertEquals("Ну пр", field.text)
        assertEquals(null, glides.erase())
    }

    @Test
    fun aGlidedWordErasedWithItsSpaceOwesTheSpaceUntilTheTextChanges() {
        val glides = suggestions.glides
        field.text = "Ну пр"
        suggestions.update()
        glides.type(listOf("привет"), 6)
        assertFalse(glides.owesSpace())

        glides.erase()
        suggestions.update()
        assertTrue(glides.owesSpace())

        field.text = "Ну п"
        assertFalse(glides.owesSpace())

        field.text = "Ну "
        glides.type(listOf("привет"), 3)
        glides.erase()
        assertFalse(glides.owesSpace())

        field.text = "Ну пр"
        glides.type(listOf("привет"), 6)
        glides.erase()
        glides.forget()
        assertFalse(glides.owesSpace())
    }

    @Test
    fun typingOnEndsTheGlide() {
        val glides = suggestions.glides
        field.text = "Ну "
        suggestions.update()
        glides.type(listOf("привет", "приводит"), 3)
        field.text += "к"
        suggestions.update()

        assertEquals(null, glides.last())
        assertEquals(null, glides.swap("приводит"))

        field.text = "Ну "
        glides.type(listOf("привет"), 3)
        suggestions.clear()
        assertEquals(null, glides.last())
    }

    @Test
    fun aGlideFollowsShiftAndReadsNothingWhereNoSuggestionsGo() {
        field.text = "Ну. "
        suggestions.update()
        keyboard.shift = ShiftState.Once
        val glides = suggestions.glides
        assertEquals("Привет", glides.cased(glides.reading()!!.words(glideThrough("привет"))).first())

        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(null, glides.reading())
    }

    @Test
    fun choosingReplacesTheWordAndAddsASpace() {
        suggestions.update()

        suggestions.choose("привет")

        assertEquals("Ну привет ", field.text)
    }

    @Test
    fun afterASpaceTheNextWordsComeAndChoosingOneTypesIt() {
        field.text = "Ну привет "
        suggestions.update()
        assertEquals(listOf("всем", "как"), keyboard.suggestions)

        suggestions.choose("всем")

        assertEquals("Ну привет всем ", field.text)
    }

    @Test
    fun shiftSetsTheCaseOfANextWord() {
        field.text = "Ну привет "
        keyboard.shift = ShiftState.Once
        suggestions.update()
        assertEquals(listOf("Всем", "Как"), keyboard.suggestions)

        keyboard.shift = ShiftState.Locked
        suggestions.update()
        assertEquals(listOf("ВСЕМ", "КАК"), keyboard.suggestions)
    }

    @Test
    fun shiftLeavesTheCaseOfACompletionToTheTypedWord() {
        keyboard.shift = ShiftState.Once
        suggestions.update()

        assertEquals(listOf("привет", "привести", "приводит"), keyboard.suggestions)
    }

    @Test
    fun choosingTellsTheEditItMade() {
        assertEquals(SuggestionEdit(deleteBefore = 4, text = "привет "), suggestions.choose("привет"))
    }

    @Test
    fun aWordChosenInTheMiddleOfTheTextKeepsTheSpaceThatFollowsAndLeavesItTheUsers() {
        field.after = "ет всем"

        assertEquals(SuggestionEdit(4, "привести", deleteAfter = 2), suggestions.choose("привести"))

        assertEquals("Ну привести всем", field.text + field.after)
        assertEquals(false, suggestions.ownSpace.dropsSpace())
    }

    @Test
    fun autocorrectKeepsTheSpaceTypedBeforeText() {
        field.after = " всем"

        suggestions.choose("привет", spaceTyped = true)

        assertEquals("Ну привет  всем", field.text + field.after)
    }

    @Test
    fun marksAfterAChosenWordTakeItsSpaceOneAfterAnother() {
        suggestions.choose("привет")

        assertEquals(SuggestionEdit(1, "? "), suggestions.ownSpace.type('?'))
        assertEquals(SuggestionEdit(1, "! "), suggestions.ownSpace.type('!'))

        assertEquals("Ну привет?! ", field.text)
    }

    @Test
    fun aSpaceRightAfterTheKeyboardsOwnOneIsDroppedOnce() {
        suggestions.choose("привет")

        assertEquals(true, suggestions.ownSpace.dropsSpace())
        assertEquals("Ну привет ", field.text)
        // The next space is the user's own on purpose.
        assertEquals(false, suggestions.ownSpace.dropsSpace())
    }

    @Test
    fun aSpaceRightAfterAMovedMarkIsDropped() {
        suggestions.choose("привет")
        suggestions.ownSpace.type('.')

        assertEquals(true, suggestions.ownSpace.dropsSpace())
        assertEquals("Ну привет. ", field.text)
    }

    @Test
    fun aSpaceAfterAnythingElseTypedGoesIn() {
        suggestions.choose("привет")
        suggestions.ownSpace.type('и')
        assertEquals(false, suggestions.ownSpace.dropsSpace())

        suggestions.choose("пока")
        suggestions.ownSpace.forget()
        assertEquals(false, suggestions.ownSpace.dropsSpace())
    }

    @Test
    fun aMarkTypedOverASelectionDeletesNothingBeforeIt() {
        suggestions.choose("привет")
        suggestions.ownSpace.type('.')
        suggestions.update(cursorIsPoint = false)

        assertEquals(SuggestionEdit(0, ","), suggestions.ownSpace.type(','))
    }

    @Test
    fun anythingElseTypedAfterTheSpaceMakesItTheUsers() {
        suggestions.choose("привет")
        assertEquals(SuggestionEdit(0, "и"), suggestions.ownSpace.type('и'))

        assertEquals(SuggestionEdit(0, "."), suggestions.ownSpace.type('.'))
        assertEquals("Ну привет и.", field.text)
    }

    @Test
    fun aForgottenOrClearedSpaceStays() {
        suggestions.choose("привет")
        suggestions.ownSpace.forget()
        assertEquals(SuggestionEdit(0, "."), suggestions.ownSpace.type('.'))

        suggestions.choose("пока")
        suggestions.clear()
        assertEquals(SuggestionEdit(0, "."), suggestions.ownSpace.type('.'))
    }

    @Test
    fun anEmailFieldSuggestsTheUsersAddressesAndChoosingOneTypesItWhole() {
        val email = FakeField("al", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val addresses = PersonalWords().apply { learn("alex.wayfer@gmail.com", previous = null, time = 1) }
        val emailSuggestions = WordSuggestions(
            keyboard,
            email,
            KeyboardWork(CoroutineScope(now), now),
            WordSources(models { error("An email field needs no language model") }, readEmails = { addresses }),
        )

        emailSuggestions.update()
        assertEquals(listOf("alex.wayfer@gmail.com"), keyboard.suggestions)

        assertEquals(SuggestionEdit(2, "alex.wayfer@gmail.com"), emailSuggestions.choose("alex.wayfer@gmail.com"))
        assertEquals("alex.wayfer@gmail.com", email.text)
        // No space of its own follows an address, so a mark goes right after it as typed.
        assertEquals(SuggestionEdit(0, "."), emailSuggestions.ownSpace.type('.'))
    }

    @Test
    fun theStripKnowsWhileTheDictionaryLoads() {
        // The load waits in the queue until the test runs it.
        val queued = ArrayDeque<Runnable>()
        val later = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                queued += block
            }
        }
        val slowModels = models(later) { LanguageModel(Dictionary.parse(sequenceOf("привет\t1"))) }
        val slow = suggestionsOf(slowModels)

        slow.update()
        assertEquals(true, keyboard.loading)

        while (queued.isNotEmpty()) queued.removeFirst().run()
        assertEquals(false, keyboard.loading)
        assertEquals(listOf("привет"), keyboard.suggestions)
    }

    @Test
    fun nothingIsAwaitedWhileTheDeviceIsLocked() {
        val locked = suggestionsOf(models, available = { false })
        keyboard.loading = true

        locked.update()

        assertEquals(false, keyboard.loading)
    }

    @Test
    fun preloadingLoadsEachModelOnceInOrder() {
        keyboard.language = Language.English

        suggestions.preload(listOf(Language.English, Language.Russian))
        suggestions.preload(listOf(Language.English))
        suggestions.update()

        assertEquals(listOf(Language.English, Language.Russian), loaded)
        assertEquals(true, suggestions.model(Language.Russian) != null)
    }

    @Test
    fun aKeyboardMadeAnewFindsTheModelsLoadedBeforeIt() {
        // As the app loads them when it starts, before the keyboard is there.
        models.load(listOf(Language.Russian))

        suggestionsOf(models).update()

        assertEquals(listOf("привет", "привести", "приводит"), keyboard.suggestions)
        assertEquals(listOf(Language.Russian), loaded)
    }

    @Test
    fun theLanguageInUseLoadsFirstAndTheOtherEnabledOnesAfter() {
        assertEquals(
            listOf(Language.English, Language.Russian),
            preloadOrder(Language.English, listOf(Language.Russian, Language.English)),
        )
        assertEquals(listOf(Language.Russian), preloadOrder(Language.Russian, emptyList()))
    }

    @Test
    fun nothingPreloadsWhileTheDeviceIsLocked() {
        val locked = suggestionsOf(models, available = { false })

        locked.preload(listOf(Language.Russian))

        assertEquals(emptyList<Language>(), loaded)
    }

    @Test
    fun noModelLoadsWhileTheDeviceIsLocked() {
        val locked = suggestionsOf(models, available = { false })

        locked.update()

        assertEquals(emptyList<Language>(), loaded)
        assertEquals(emptyList<String>(), keyboard.suggestions)
        assertEquals(null, locked.model(Language.Russian))
    }

    @Test
    fun aLoadedModelIsThere() {
        suggestions.update()

        assertEquals(true, suggestions.model(Language.Russian) != null)
        assertEquals(null, suggestions.model(Language.English))
    }

    @Test
    fun aSelectionOrAPasswordGetsNoSuggestions() {
        suggestions.update(cursorIsPoint = false)
        assertEquals(emptyList<String>(), keyboard.suggestions)

        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        suggestions.update(cursorIsPoint = true)
        assertEquals(emptyList<String>(), keyboard.suggestions)
    }

    @Test
    fun theKeyboardLanguagePicksTheDictionaryAndEachLoadsOnce() {
        field.text = "hel"
        keyboard.language = Language.English
        suggestions.update()
        suggestions.update()

        assertEquals(listOf("hello"), keyboard.suggestions)
        assertEquals(listOf(Language.English), loaded)
    }

    @Test
    fun clearingEmptiesTheStrip() {
        suggestions.update()

        suggestions.clear()

        assertEquals(emptyList<String>(), keyboard.suggestions)
    }

    @Test
    fun aTypedAbbreviationUsesItsDictionaryCaseWithoutADuplicate() {
        val models = models {
            LanguageModel(Dictionary.parse(sequenceOf("ИИ\t1", "ИИ-вставкам\t30", "ИИ-кавер\t20")))
        }
        val suggestions = suggestionsOf(models)
        field.text = "Ну Ии"

        suggestions.update()

        assertEquals("ИИ", keyboard.typedWord)
        assertEquals(listOf("ИИ", "ИИ-вставкам", "ИИ-кавер"), stripSlots(keyboard.suggestions, keyboard.typedWord))

        suggestions.choose(keyboard.typedWord!!)
        suggestions.update()

        assertEquals("Ну ИИ ", field.text)
        assertEquals(null, keyboard.typedWord)
    }

    @Test
    fun aTypedWordUsesTheLearnedCaseBeforeTheDictionaryCase() {
        suggestions.update()
        models[Language.Russian]!!.personal.learn("ПРИВЕТ", previous = null, time = 0)
        field.text = "Ну Привет"

        suggestions.update()

        assertEquals("ПРИВЕТ", keyboard.typedWord)
        assertFalse(keyboard.suggestions.any { it.equals("привет", ignoreCase = true) })
    }

    @Test
    fun aTypedAbbreviationStaysBesideItsCompletionsAndCanBeChosen() {
        val models = models {
            LanguageModel(Dictionary.parse(sequenceOf("ИИ\t1", "ИИ-вставкам\t30", "ИИ-кавер\t20")))
        }
        val suggestions = suggestionsOf(models)
        field.text = "Ну ИИ"

        suggestions.update()

        assertEquals("ИИ", keyboard.typedWord)
        assertEquals(listOf("ИИ", "ИИ-вставкам", "ИИ-кавер"), stripSlots(keyboard.suggestions, keyboard.typedWord))
        assertFalse(keyboard.suggestions.any { it.equals("ИИ", ignoreCase = true) })

        suggestions.choose("ИИ")
        suggestions.update()

        assertEquals("Ну ИИ ", field.text)
        assertEquals(null, keyboard.typedWord)
    }

    @Test
    fun theWordAsTypedIsShownWhetherKnownOrUnknownAndNotSuggestedTwice() {
        suggestions.update()
        assertEquals("прив", keyboard.typedWord)

        models[Language.Russian]?.personal?.learn("приветик", previous = null, time = 0)
        field.text = "Ну приветик"
        suggestions.update()
        assertEquals("приветик", keyboard.typedWord)
        // The words it may mean by a mistake come, but not the word itself again.
        assertEquals(false, keyboard.suggestions.any { it.equals("приветик", ignoreCase = true) })

        field.text = "Ну привет"
        suggestions.update()
        assertEquals("привет", keyboard.typedWord)
        assertFalse(keyboard.suggestions.any { it.equals("привет", ignoreCase = true) })
    }

    @Test
    fun noWordAsTypedIsShownAfterASpaceOrOnceCleared() {
        field.text = "Ну прив "
        suggestions.update()
        assertEquals(null, keyboard.typedWord)

        field.text = "Ну прив"
        suggestions.update()
        suggestions.clear()
        assertEquals(null, keyboard.typedWord)
    }

    @Test
    fun aKnownTypedWordStaysBesideALikelierCorrection() {
        val models = models {
            LanguageModel(Dictionary.parse(sequenceOf("выключенную\t100000000", "включённую\t100")))
        }
        val suggestions = WordSuggestions(
            keyboard, field, KeyboardWork(CoroutineScope(now), now), WordSources(models), corrects = { true },
        )
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        field.text = "включённую"

        suggestions.update()

        assertEquals("выключенную", keyboard.correction)
        assertEquals("включённую", keyboard.typedWord)
        assertEquals(listOf("включённую", "выключенную", null), stripSlots(keyboard.suggestions, keyboard.typedWord))
        assertTrue(keyboard.keepsTyped("включённую"))
        assertFalse(keyboard.suggestions.any { it == "включённую" })

        field.text = "выключенную"
        suggestions.update()
        assertEquals(null, keyboard.correction)
        assertEquals("выключенную", keyboard.typedWord)
    }
}
