package name.alexwayfer.preglide.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.AFTER_NUMBER
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.Learned
import name.alexwayfer.preglide.engine.suggest.LearningPolicy
import name.alexwayfer.preglide.engine.suggest.after
import name.alexwayfer.preglide.engine.suggest.isRemoved
import name.alexwayfer.preglide.engine.suggest.learn
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LearningTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** A field the keyboard types into at its end, telling [learning] as the keyboard does. */
    private inner class Field : SuggestionField {
        var text = ""
        override var inputType = InputType.TYPE_CLASS_TEXT
        override var imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = ""

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
        }

        fun type(typed: String) {
            for (char in typed) {
                learning.beforeTyping(char)
                val edit = SuggestionEdit(0, char.toString())
                learning.expectEdit(edit)
                replace(edit.deleteBefore, edit.text)
                learning.selectionChanged(text.length, text.length)
            }
        }

        /**
         * Types [typed] before the field reports any of it, as fast typing does; then the field reports each edit
         * in turn, or, [atOnce], only the last.
         */
        fun typeAhead(typed: String, atOnce: Boolean) {
            val places = mutableListOf<Int>()
            for (char in typed) {
                learning.beforeTyping(char)
                val edit = SuggestionEdit(0, char.toString())
                learning.expectEdit(edit)
                replace(edit.deleteBefore, edit.text)
                places += text.length
            }
            for (place in if (atOnce) places.takeLast(1) else places) learning.selectionChanged(place, place)
        }
    }

    private val field = Field()
    private val store = PersonalStore { File(folder.root, "personal") }
    private val now = testDispatchers.now
    private val personal = PersonalData(store, CoroutineScope(now), now)
    // Lazy: the words load from the folder, which the rule makes only once the test starts.
    private val model by lazy {
        val dictionary = Dictionary.parse(sequenceOf("и\t20", "привет\t10"))
        LanguageModel(dictionary, personal = personal.load(personalName(Language.Russian)))
    }
    private val addresses by lazy { personal.load(EMAILS) }
    private var language = Language.Russian
    private val learning = Learning(
        field,
        language = { language },
        models = { if (it == Language.Russian) model else null },
        emails = { addresses },
        personal = personal,
        clock = { 42 },
    ).apply { policy = { LearningPolicy(uses = 1) } }
    private val removal = WordRemoval(
        field,
        language = { language },
        models = { if (it == Language.Russian) model else null },
        emails = { addresses },
        personal = personal,
        clock = { 42 },
    )

    @Test
    fun productiveParticlesNeverBecomeCandidatesOrContexts() {
        learning.policy = { LearningPolicy() }
        field.type("релиз-то релиз-то извольте-с извольте-с привет привет ")
        assertEquals(listOf("привет"), model.personal.all().map { it.spelling })
        assertEquals(emptyList<String>(), learnableContext(listOf("релиз-то"), model))
        learning.finishField()
        assertEquals(listOf("привет"), store.read(personalName(Language.Russian)).all().map { it.spelling })
    }

    @Test
    fun twoFieldStartsTeachTheEmotesSpellingAndBothUses() {
        learning.policy = { LearningPolicy() }
        field.type("Maaaan ")
        assertNull(model.personal.learned("Maaaan"))
        assertTrue(model.personal.starts.all().isEmpty())
        learning.finishField()
        field.text = ""
        learning.startField(0, 0)
        field.type("Maaaan ")
        assertEquals(Learned("Maaaan", 2, 42), model.personal.learned("Maaaan"))
        assertEquals(2, model.personal.starts.all().single().count)
    }

    @Test
    fun anExplicitLowercaseInitialWinsUntilAnUppercaseMidSentenceUse() {
        learning.policy = { LearningPolicy() }
        for (word in listOf("Васян", "васян", "Васян")) {
            field.text = ""
            learning.startField(0, 0)
            field.type("$word ")
            learning.finishField()
        }
        assertEquals(Learned("васян", 3, 42), model.personal.learned("Васян"))
        field.text = ""
        learning.startField(0, 0)
        field.type("тот самый Васян ")
        assertEquals(Learned("Васян", 4, 42), model.personal.learned("васян"))
        assertTrue(model.personal.after("самый").isEmpty())
    }

    @Test
    fun anExplicitlyKeptDictionaryWordTeachesItsCase() {
        field.type("Ну ПРИВЕТ")

        learning.chose("ПРИВЕТ", kept = true)

        assertEquals("ПРИВЕТ", model.personal.learned("привет")?.spelling)
    }

    @Test
    fun typedWordsAreLearnedInTheirUsualCase() {
        field.type("Привет Вася ")

        assertEquals(Learned("привет", 1, 42), model.personal.learned("привет"))
        assertEquals(listOf(Learned("Вася", 1, 42)), model.personal.after("привет"))
    }

    @Test
    fun wordsTypedAheadOfTheFieldsReportsAreLearnedAsWhenTypedSlowly() {
        field.type("Ну ")
        field.typeAhead("и привет", atOnce = false)
        field.typeAhead(" и ", atOnce = true)
        field.type("привет ")

        assertEquals(Learned("и", 2, 42), model.personal.learned("и"))
        assertEquals(Learned("привет", 2, 42), model.personal.learned("привет"))
        assertEquals(listOf(Learned("и", 1, 42)), model.personal.after("ну"))
        assertEquals(listOf(Learned("привет", 2, 42)), model.personal.after("и"))
        assertEquals(listOf(Learned("и", 1, 42)), model.personal.after("привет"))
    }

    @Test
    fun aSingleLetterIsLearnedOnlyAsADictionaryWord() {
        field.type("и т.е. привет ")

        assertEquals(Learned("и", 1, 42), model.personal.learned("и"))
        assertNull(model.personal.learned("т"))
        assertNull(model.personal.learned("е"))
        assertEquals(Learned("привет", 1, 42), model.personal.learned("привет"))
    }

    @Test
    fun aWordWithDigitsIsLearnedWholeButANumberIsNot() {
        field.type("в топ-3 и mp3 с 2024 ")

        assertEquals(Learned("топ-3", 1, 42), model.personal.learned("топ-3"))
        assertEquals(Learned("mp3", 1, 42), model.personal.learned("mp3"))
        assertNull(model.personal.learned("топ"))
        assertNull(model.personal.learned("2024"))
    }

    @Test
    fun aWordAfterANumberAndAHyphenIsLearnedAfterANumber() {
        field.type("на 2-разовое ")

        assertEquals(listOf(Learned("разовое", 1, 42)), model.personal.after(AFTER_NUMBER))
    }

    @Test
    fun aDigitGoesInAWordWithLettersBeforeIt() {
        assertTrue(goesInWord('3') { "в топ-" })
        assertTrue(goesInWord('3') { "mp" })
        assertTrue(goesInWord('ы') { "" })
        assertTrue(goesInWord('-') { "" })
        assertFalse(goesInWord('3') { "в " })
        assertFalse(goesInWord('0') { "в 2" })
        assertFalse(goesInWord(',') { "привет" })
    }

    @Test
    fun aWordAfterASmileyIsLearnedWithoutAPair() {
        field.type("Ну :D привет ")

        assertNull(model.personal.learned("d"))
        assertEquals(emptyList<Learned>(), model.personal.after("d"))
        assertEquals(Learned("привет", 1, 42), model.personal.learned("привет"))
    }

    @Test
    fun aWordIsLearnedAfterTheWordsBeforeItBackToOneThatIsNoWord() {
        field.type("Ну и привет т и привет ")

        assertEquals(listOf(Learned("привет", 2, 42)), model.personal.after(listOf("и")))
        assertEquals(listOf(Learned("привет", 1, 42)), model.personal.after(listOf("ну", "и")))
        assertEquals(emptyList<Learned>(), model.personal.after(listOf("т", "и")))
        assertEquals(emptyList<Learned>(), model.personal.after(listOf("привет", "т", "и")))
    }

    @Test
    fun leavingTheFieldSavesWhatWasLearned() {
        field.type("пиздец ")

        learning.finishField()

        assertEquals(Learned("пиздец", 1, 42), store.read(personalName(Language.Russian)).learned("пиздец"))
    }

    @Test
    fun aRemovedWordIsSavedAsRemoved() {
        removal.remove("привет")
        learning.finishField()

        assertTrue(store.read(personalName(Language.Russian)).isRemoved("привет"))
    }

    @Test
    fun aFieldThatAsksForNoLearningTeachesNothing() {
        field.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        field.type("секрет ")

        assertNull(model.personal.learned("секрет"))
    }

    @Test
    fun nothingIsLearnedWithoutALoadedModel() {
        language = Language.English
        field.type("hello ")
        removal.remove("hello")
        learning.finishField()

        assertNull(model.personal.learned("hello"))
        assertFalse(File(folder.root, "personal").exists())
    }

    @Test
    fun aCursorMoveTheKeyboardDidNotMakeFinishesTheWord() {
        learning.startField(0, 0)
        field.type("прив")

        learning.selectionChanged(0, 0)

        assertEquals(Learned("прив", 1, 42), model.personal.learned("прив"))
    }

    @Test
    fun aWordTypedBeforeTheLanguageChangesIsLearnedInItsLanguage() {
        field.type("привет")

        language = Language.English
        field.type(" ")

        assertEquals(Learned("привет", 1, 42), model.personal.learned("привет"))
    }

    @Test
    fun aWordSelectedToEraseIsNotLearned() {
        learning.startField(0, 0)
        field.type("прив")

        learning.beforeSelectingWords()
        learning.selectionChanged(0, 4)

        assertNull(model.personal.learned("прив"))
    }

    private fun backspace(times: Int) = repeat(times) {
        learning.beforeBackspace()
        field.replace(1, "")
        learning.selectionChanged(field.text.length, field.text.length)
    }

    @Test
    fun aTypoErasedRightAwayAndTypedAgainIsCorrected() {
        field.type("пртвет ")
        backspace(times = 7)
        field.type("привет ")

        assertNull(model.personal.learned("пртвет"))
        assertEquals(Learned("привет", 1, 42), model.personal.learned("привет"))
    }

    @Test
    fun theFirstWordOfAFieldIsLearnedAsBeginningItAndSaved() {
        field.type("Привет. И ")
        learning.finishField()

        assertEquals(listOf(Learned("привет", 1, 42)), model.personal.starts.all())
        assertEquals(listOf(Learned("привет", 1, 42)), store.read(personalName(Language.Russian)).starts.all())
    }

    @Test
    fun aFirstWordFixedRightAwayBeginsTheFieldOnlyAsFixed() {
        field.type("пртвет ")
        backspace(times = 7)
        field.type("привет ")

        assertEquals(listOf(Learned("привет", 1, 42)), model.personal.starts.all())
    }

    @Test
    fun anErasedMessageStaysLearned() {
        field.type("пртвет мир ")
        backspace(times = 11)

        assertEquals(Learned("пртвет", 1, 42), model.personal.learned("пртвет"))
        assertEquals(listOf(Learned("мир", 1, 42)), model.personal.after("пртвет"))
    }

    @Test
    fun anEmailFieldTeachesItsAddressWholeWhenLeft() {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        field.type("alex.wayfer@gmail.com")
        // The app clears the field as the user leaves it: the address was kept as typed.
        field.text = ""

        learning.finishField()

        assertEquals("alex.wayfer@gmail.com", addresses.learned("alex.wayfer@gmail.com")?.spelling)
        assertNull(model.personal.learned("alex"))
        assertEquals(1, store.read(EMAILS).all().size)
    }

    @Test
    fun anEmailFieldWithNoAddressTeachesNothing() {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        field.type("alex")

        learning.finishField()

        assertEquals(emptyList<Learned>(), addresses.all())
    }

    @Test
    fun anAddressIsRemovedInAnEmailField() {
        addresses.learn("old@example.com", previous = null, time = 1)
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        removal.remove("old@example.com")
        learning.finishField()

        assertTrue(store.read(EMAILS).isRemoved("old@example.com"))
    }

    @Test
    fun aChosenWordIsLearned() {
        field.type("Ну ")

        learning.chose("привет")

        assertEquals(listOf(Learned("привет", 1, 42)), model.personal.after("ну"))
    }

    @Test
    fun aWordKeptAsTypedIsLearnedAsKept() {
        field.type("Ну ")

        learning.chose("кринж", kept = true)

        assertEquals(1, model.personal.learned("кринж")?.kept)
    }

    @Test
    fun theCursorAfterAnEditFollowsWhatWasDeletedAndTyped() {
        assertEquals(6, cursorAfterEdit(5, 5, deleteBefore = 0, inserted = 1))
        assertEquals(4, cursorAfterEdit(5, 5, deleteBefore = 1, inserted = 0))
        assertEquals(0, cursorAfterEdit(0, 0, deleteBefore = 1, inserted = 0))
        // A selection is deleted whole, whatever the count.
        assertEquals(2, cursorAfterEdit(2, 9, deleteBefore = 1, inserted = 0))
    }
}
