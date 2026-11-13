package name.alexwayfer.preglide.ime

import android.text.InputType
import kotlinx.coroutines.CoroutineScope
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.LanguageModel
import name.alexwayfer.preglide.engine.suggest.LearningPolicy
import name.alexwayfer.preglide.testDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher

class GlideTypingTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** A field that holds its text, with the cursor at its end. */
    private class Field : SuggestionField {
        var text = ""
        override var inputType = InputType.TYPE_CLASS_TEXT
        override val imeOptions = 0

        override fun textBeforeCursor(length: Int): CharSequence = text.takeLast(length)

        override fun textAfterCursor(length: Int): CharSequence = ""

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) {
            this.text = this.text.dropLast(deleteBefore) + text
        }
    }

    private val field = Field()
    private val now = testDispatchers.now
    private val personal = PersonalData(PersonalStore { File(folder.root, "personal") }, CoroutineScope(now), now)

    // Lazy: the words load from the folder, which the rule makes only once the test starts.
    private val model by lazy {
        val dictionary = Dictionary.parse(sequenceOf("привет\t100000", "приват\t5000", "как\t50000"))
        LanguageModel(dictionary, personal = personal.load(personalName(Language.Russian)))
    }
    private val models = LanguageModels(CoroutineScope(now), now) { model }
    private val keyboard = KeyboardState()
    private val suggestions =
        WordSuggestions(keyboard, field, KeyboardWork(CoroutineScope(now), now), WordSources(models))
    private val learning = Learning(
        field,
        language = { Language.Russian },
        models = { suggestions.model(it) },
        emails = { null },
        personal = personal,
        clock = { 42 },
    ).apply { policy = { LearningPolicy(uses = 1) } }
    private var erasesWord = true
    /** How many glides ended, a word typed or not. */
    private var glided = 0
    /** The lines logged of glides read. */
    private val logged = mutableListOf<String>()
    /** The glided words told of, as typed, swapped or erased. */
    private val slips = mutableListOf<GlideSlip>()
    private val glideTyping = typing(now)

    /** Glide typing that reads in [readContext]. */
    private fun typing(readContext: CoroutineContext) = GlideTyping(
        keyboard,
        suggestions,
        learning,
        GlideContexts(CoroutineScope(now), readContext),
        GlideOptions(erasesWord = { erasesWord }, bringsBackWords = { true }),
        GlideHooks(
            log = { logged += it },
            recorded = { slips += it },
            afterGlide = { glided++ },
            clock = { 1_000L * slips.size },
        ),
    )

    /** Reads run when [runReads] runs them, as a background thread does later. */
    private val reads = ArrayDeque<Runnable>()
    private val later = Executor { reads.addLast(it) }.asCoroutineDispatcher()

    private fun runReads() {
        while (reads.isNotEmpty()) reads.removeFirst().run()
    }

    private fun reported() {
        learning.selectionChanged(field.text.length, field.text.length)
        suggestions.update()
    }

    private fun glide(word: String, typing: GlideTyping = glideTyping) {
        typing.glided(glideThrough(word))
        reported()
    }

    @Test
    fun aGlideTypesItsWordLearnedAsChosenAndOffersTheOthers() {
        field.text = "Ну "
        reported()
        keyboard.shift = ShiftState.Once

        glide("привет")

        assertEquals("Ну Привет", field.text)
        assertEquals(1, model.personal.learned("привет")?.count)
        assertEquals(listOf("Привет", "Приват"), keyboard.suggestions)
        assertEquals(ShiftState.Off, keyboard.shift)
    }

    @Test
    fun theStripShowsTheWordsAsTheFingerGoesAndDropsAReadTheLiftOvertook() {
        val typing = typing(later)
        reported()

        typing.preview(glideThrough("привет"))
        // One read at a time: a second way waits for the first to be read.
        typing.preview(glideThrough("привет"))
        runReads()
        assertEquals(listOf("привет", "приват"), keyboard.suggestions)

        typing.preview(glideThrough("как"))
        typing.glided(glideThrough("привет"))
        runReads()
        reported()
        assertEquals("привет", field.text)
        assertEquals(listOf("привет", "приват"), keyboard.suggestions)
    }

    @Test
    fun aWordFromTheStripSwapsInForTheGlidedOneWithASpaceAndTheStripMovesOn() {
        reported()
        glide("привет")

        assertFalse(glideTyping.swap("как"))
        assertTrue(glideTyping.swap("приват"))
        reported()

        assertEquals("приват ", field.text)
        assertNull(model.personal.learned("привет"))
        assertEquals(1, model.personal.learned("приват")?.count)
        // The strip suggests the next word now, not the glide's.
        assertNull(suggestions.glides.last())
        assertFalse(glideTyping.swap("привет"))
    }

    @Test
    fun theGlidedWordFromTheStripGetsItsSpaceAndStaysLearned() {
        reported()
        glide("привет")

        assertTrue(glideTyping.swap("привет"))
        reported()

        assertEquals("привет ", field.text)
        assertEquals(1, model.personal.learned("привет")?.count)
        assertNull(suggestions.glides.last())
    }

    @Test
    fun eachGlidedWordIsToldAsItGoesInAndAgainWhenSwapped() {
        reported()
        glide("как")
        glide("привет")
        val glided = suggestions.glides.last()

        glideTyping.swap("приват")

        assertEquals(listOf("как", "привет", "приват"), slips.map { it.chosen })
        val slip = slips.last()
        assertEquals(2, slip.number)
        // When the glide typed its word, not when it was swapped.
        assertEquals(1_000L, slip.at)
        assertEquals(Language.Russian, slip.language)
        assertEquals("привет", slip.typed)
        assertEquals(listOf(glided?.word) + glided?.alternatives.orEmpty(), slip.offered)
    }

    @Test
    fun theGlidedWordItselfIsNoSwap() {
        reported()
        glide("привет")

        glideTyping.swap("привет")

        assertEquals(listOf("привет"), slips.map { it.chosen })
    }

    @Test
    fun aGlidedWordErasedWholeIsToldWithNothingChosen() {
        reported()
        glide("привет")

        glideTyping.beforeKey(KeyAction.Backspace)
        glideTyping.erase()

        assertEquals(listOf("привет", ""), slips.map { it.chosen })
        assertEquals(slips.first().number, slips.last().number)
    }

    @Test
    fun aBackspaceRightAfterErasesTheGlidedWordWholeWhileTheSettingIsOn() {
        field.text = "Ну "
        reported()
        glide("привет")

        glideTyping.beforeKey(KeyAction.Backspace)
        assertEquals(ShiftState.Off, glideTyping.erase())
        reported()

        assertEquals("Ну ", field.text)
        assertNull(model.personal.learned("привет"))

        glide("привет")
        erasesWord = false
        assertNull(glideTyping.erase())
    }

    @Test
    fun anotherKeyPutsTheGlidedWordPastSwappingButShiftDoesNot() {
        reported()
        glide("привет")

        assertTrue(glideTyping.beforeKey(KeyAction.Shift))
        assertTrue(suggestions.glides.last() != null)
        // Right after the glided word, for autocorrect to leave it be.
        assertTrue(glideTyping.beforeKey(KeyAction.Letter('к')))
        assertNull(suggestions.glides.last())
        assertFalse(glideTyping.swap("приват"))
        assertFalse(glideTyping.beforeKey(KeyAction.Letter('к')))
    }

    @Test
    fun aLetterRightAfterAGlidedWordGoesOnWithItAndOnlyTheWordItEndsUpIsLearned() {
        reported()
        glide("привет")

        for (char in "ы ") {
            glideTyping.beforeKey(KeyAction.Letter(char))
            learning.beforeTyping(char)
            learning.expectEdit(suggestions.ownSpace.type(char))
            reported()
        }

        assertEquals("приветы ", field.text)
        assertNull(model.personal.learned("привет"))
        assertEquals(1, model.personal.learned("приветы")?.count)
    }

    @Test
    fun aGlideInAFieldThatAsksForNoSuggestionsTypesItsWordAndOffersTheOthers() {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        reported()

        glide("привет")

        assertEquals("привет", field.text)
        assertEquals(listOf("привет", "приват"), keyboard.suggestions)
    }

    @Test
    fun aGlideInAPasswordFieldTypesNothing() {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        glide("привет")

        assertEquals("", field.text)
        // Still told of, so the shift follows the text either way.
        assertEquals(1, glided)
    }

    @Test
    fun whatTheUserDoesWhileTheGlideIsReadWaitsForItsWordAndReadsItOnce() {
        val typing = typing(later)
        reported()
        val done = mutableListOf<String>()

        glide("привет", typing)
        typing.afterGlides.whenRead { done += field.text }
        typing.afterGlides.whenRead { done += "after" }
        assertEquals(emptyList<String>(), done)

        runReads()
        assertEquals(listOf("привет", "after"), done)
        assertEquals(1, logged.size)
        // With no glide being read, at once.
        typing.afterGlides.whenRead { done += "now" }
        assertEquals("now", done.last())
    }

    @Test
    fun aGlideLiftedWhileTheOneBeforeIsReadGoesInAfterIt() {
        val typing = typing(later)
        reported()

        glide("привет", typing)
        glide("как", typing)
        runReads()

        assertEquals("привет как", field.text)
        assertEquals(2, glided)
    }

    @Test
    fun leavingTheFieldDropsWhatWaitedForTheGlide() {
        val typing = typing(later)
        reported()
        var done = false

        glide("привет", typing)
        typing.afterGlides.whenRead { done = true }
        typing.drop()
        runReads()

        assertFalse(done)
        assertEquals("", field.text)
    }

    @Test
    fun eachReadOfALiftedGlideIsLoggedWithoutItsWordsButNoPreviewIs() {
        val typing = typing(later)
        reported()

        typing.preview(glideThrough("привет"))
        runReads()
        assertEquals(emptyList<String>(), logged)

        glide("привет", typing)
        runReads()
        assertEquals(1, logged.size)
        assertTrue(logged.single().startsWith("Glide of "))
        assertFalse(logged.single().contains("привет"))
    }

    @Test
    fun aGlideReadLineTellsTheLengthAndTime() {
        assertEquals("Glide of 9 key widths read in 12 ms", glideReadLine(12_400_000, 8.6f))
        assertEquals("Glide of 3 key widths read in 0 ms", glideReadLine(300_000, 3.2f))
    }

    @Test
    fun aGlideReadInTheBackgroundGoesInWhenDoneUnlessTheFieldWasLeft() {
        val typing = typing(later)
        reported()

        glide("привет", typing)
        runReads()
        assertEquals("привет", field.text)

        glide("привет", typing)
        typing.drop()
        runReads()
        assertEquals("привет", field.text)
    }
}
