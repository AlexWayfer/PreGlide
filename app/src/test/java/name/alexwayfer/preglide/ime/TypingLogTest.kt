package name.alexwayfer.preglide.ime

import android.text.InputType
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.suggest.ContextSeen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TypingLogTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bench = KeyboardBench { folder.root }
    private val field = bench.field

    @Before
    fun startTheField() {
        bench.session.start(field.inputType, 0, 0)
    }

    /** The lines the keyboard logged as [action] ran. */
    private fun loggedBy(action: () -> Unit): List<String> {
        bench.logged.clear()
        action()
        return bench.logged
    }

    private fun strip(trigger: StripTrigger, source: StripSource) =
        stripLine(trigger, source, bench.keyboard.suggestions.size)

    @Test
    fun aFieldStartTellsItsFlagsAndWhatTheKeyboardDoesThere() {
        // YouTube's comment field, which asks for no suggestions and gets them all the same.
        assertEquals(
            "Field started: inputType=0x2a4001 imeOptions=0x50000006, " +
                "suggestions on, glides on, learning on, autocorrect on",
            fieldStartLine(0x2a4001, 0x50000006),
        )
        val plain = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        assertEquals(
            "Field started: inputType=0x8001 imeOptions=0x0, suggestions on, glides on, learning on, autocorrect on",
            fieldStartLine(plain, 0),
        )
    }

    @Test
    fun theStripSourceSaysWhereItsWordsCameFrom() {
        assertEquals(StripSource.Selection, stripSource(isPoint = false, suggests = true, loaded = true, "пр"))
        assertEquals(
            StripSource.FieldWithoutSuggestions,
            stripSource(isPoint = true, suggests = false, loaded = true, ""),
        )
        assertEquals(StripSource.NoModel, stripSource(isPoint = true, suggests = true, loaded = false, "пр"))
        assertEquals(StripSource.NextWords, stripSource(isPoint = true, suggests = true, loaded = true, ""))
        assertEquals(
            StripSource.FieldStart,
            stripSource(isPoint = true, suggests = true, loaded = true, "", fieldStart = true),
        )
        assertEquals(StripSource.Completions, stripSource(isPoint = true, suggests = true, loaded = true, "пр"))
    }

    @Test
    fun theOfferedWordsAreAnEmailFieldsAddressesOrAGlidesWords() {
        val email = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertEquals(StripSource.Emails, offeredSource(email))
        assertEquals(StripSource.GlideAlternatives, offeredSource(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun theStripIsLoggedOnceAfterWhatItWasAwaitedFor() {
        val lines = mutableListOf<String>()
        val log = TypingLog(KeyboardBench.Field(), write = { lines += it })

        log.stripShown(StripSource.Completions, listOf("а", "б", "в"))
        log.awaitStrip(StripTrigger.Space)
        log.stripShown(StripSource.NextWords, listOf("а", "б", "в"))
        log.stripShown(StripSource.Completions, listOf("а", "б"))
        log.awaitStrip(StripTrigger.Backspace)
        log.awaitStrip(null)
        log.stripShown(StripSource.Completions, listOf("а"))

        assertEquals(listOf("Strip after a space: 3, next words"), lines)
    }

    @Test
    fun aFieldStartForgetsTheStripAwaited() {
        val lines = mutableListOf<String>()
        val log = TypingLog(KeyboardBench.Field(), write = { lines += it })
        log.awaitStrip(StripTrigger.Glide)

        log.fieldStarted()
        log.stripShown(StripSource.NextWords, listOf("а", "б", "в"))

        assertEquals(1, lines.size)
    }

    @Test
    fun aWordEndedTellsWhatTheStripHadForItWithoutTheWord() {
        val lines = mutableListOf<String>()
        val log = TypingLog(KeyboardBench.Field(), write = { lines += it })

        log.stripShown(StripSource.NextWords, listOf("Дела", "как"), byModel = true)
        log.wordEnded("дела")
        log.stripShown(StripSource.Selection, emptyList())
        log.wordEnded("дела")

        assertEquals(
            listOf(
                "Word ended, model: next word at rank 1; so far next words by rank 1/0/0, completed 0, " +
                    "shown only whole 0, glided 0, missed 0, of 1",
            ),
            lines,
        )
        assertFalse(lines.single().contains("дела", ignoreCase = true))
    }

    @Test
    fun theTalliesAreSummarizedEveryFewWordsEndedWithoutTheWords() {
        val summaries = mutableListOf<String>()
        val log = TypingLog(KeyboardBench.Field(), write = {}, summarize = { summaries += it })

        repeat(TYPING_SUMMARY_EVERY - 1) {
            log.stripShown(StripSource.NextWords, listOf("дела"), byModel = true)
            log.wordEnded("дела")
        }
        // A word the strip did not follow counts for nothing.
        log.wordEnded("дела")
        assertEquals(emptyList<String>(), summaries)

        log.learnedAfter(listOf(ContextSeen(1, seen = true, withWord = true)))
        log.stripShown(StripSource.NextWords, listOf("дела"), byModel = true)
        log.wordEnded("кот")

        assertEquals(
            listOf(
                "Words so far, mid-text: next words by rank 24/0/0, completed 0, shown only whole 0, glided 0, " +
                    "missed 1, of 25",
                "Contexts so far, seen/with word/of 1: 1/1/1, 2: 0/0/0, 3: 0/0/0",
            ),
            summaries,
        )
        assertFalse(summaries.any { it.contains("дела") || it.contains("кот") })
    }

    @Test
    fun theFirstWordOfAnEmptyFieldIsCountedApartFromTheNextWords() {
        val lines = mutableListOf<String>()
        val log = TypingLog(KeyboardBench.Field(), write = { lines += it })

        log.stripShown(StripSource.FieldStart, listOf("Привет"))
        log.stripShown(StripSource.Completions, listOf("слушай"), typed = "с")
        log.wordEnded("слушай")
        log.stripShown(StripSource.NextWords, listOf("как"), byModel = true)
        log.wordEnded("как")

        assertEquals(
            listOf(
                "Word ended, field start: completed after 1 of 6 letters; so far next words by rank 0/0/0, " +
                    "completed 1 after 1.0 of 6.0 letters, shown only whole 0, glided 0, missed 0, of 1",
                "Word ended, model: next word at rank 1; so far next words by rank 1/0/0, completed 0, " +
                    "shown only whole 0, glided 0, missed 0, of 1",
            ),
            lines,
        )
    }

    @Test
    fun theWordAfterAGlideIsCountedApartButTheGlidedWordItselfIsNot() {
        val lines = mutableListOf<String>()
        val log = TypingLog(KeyboardBench.Field(), write = { lines += it })

        log.stripShown(StripSource.GlideNextWords, listOf("как", "дела"), byModel = true)
        // The glided word ends as a space or the next word goes after it: the strip showed its next words.
        log.wordEnded("привет")
        log.wordEnded("дела")
        log.stripShown(StripSource.GlideNextWords, listOf("ты"))
        log.wordEnded("ты", glided = true)

        assertEquals(
            listOf(
                "Word ended, after a glide, model: next word at rank 2; so far next words by rank 0/1/0, " +
                    "completed 0, shown only whole 0, glided 0, missed 0, of 1",
                "Word ended, after a glide, pairs: next word at rank 1; so far next words by rank 1/1/0, " +
                    "completed 0, shown only whole 0, glided 0, missed 0, of 2",
            ),
            lines,
        )
    }

    @Test
    fun aNextWordChosenRightAfterAGlideIsLoggedAsOne() {
        bench.learning.log = bench.suggestions.log
        bench.glideStrip = GlideStrip.NextWords
        bench.type("ну как ")

        val lines = loggedBy {
            bench.glide("ну")
            bench.typing.chose("как")
        }

        assertTrue(lines.any { it.startsWith("Strip after a glide: ") && it.endsWith(", next words after a glide") })
        assertEquals(
            listOf("Word ended, after a glide, pairs: next word at rank 1"),
            lines.filter { it.startsWith("Word ended, after a glide") }.map { it.substringBefore(';') },
        )
    }

    @Test
    fun theKeyboardLogsTheWordsEndedAndTheirContextsOnceLearningTellsIt() {
        bench.learning.log = bench.suggestions.log

        val lines = loggedBy { bench.type("ну как ") }

        // The first word of the empty field counts apart.
        assertEquals(1, lines.count { it.startsWith("Word ended, field start: ") })
        assertEquals(1, lines.count { it.startsWith("Word ended, pairs: ") })
        assertEquals(
            "Learned after words: 1 new; so far seen/with word/of 1: 0/0/1, 2: 0/0/0, 3: 0/0/0",
            lines.single { it.startsWith("Learned after words") },
        )
    }

    @Test
    fun theFieldStartIsLogged() {
        assertEquals(listOf(fieldStartLine(field.inputType, 0)), bench.logged)
    }

    @Test
    fun lettersLogNothing() {
        assertEquals(emptyList<String>(), loggedBy { bench.type("при") })
    }

    @Test
    fun aBackspaceLogsWhatItDidAndTheStripAfterIt() {
        bench.type("при")

        val lines = loggedBy { bench.press(KeyAction.Backspace) }

        assertEquals(
            listOf(backspaceLine(BackspaceBranch.Deleted), strip(StripTrigger.Backspace, StripSource.Completions)),
            lines,
        )
    }

    @Test
    fun aSpaceLogsTheNextWords() {
        bench.type("ну")

        val lines = loggedBy { bench.press(KeyAction.Space) }

        assertEquals(listOf(strip(StripTrigger.Space, StripSource.NextWords)), lines)
    }

    @Test
    fun aWordChosenLogsTheKeyboardsSpaceAndASpaceAfterItDropped() {
        val lines = loggedBy {
            bench.type("при")
            bench.typing.chose("Привет")
            bench.reported()
            bench.press(KeyAction.Space)
        }

        assertEquals(
            listOf(
                ownSpaceLine(OwnSpaceEvent.Placed),
                strip(StripTrigger.Choice, StripSource.NextWords),
                ownSpaceLine(OwnSpaceEvent.Dropped),
            ),
            lines,
        )
    }

    @Test
    fun aMarkTakingTheKeyboardsSpaceAndTheBackspaceThatBringsItBackAreLogged() {
        bench.type("при")
        bench.typing.chose("Привет")
        bench.reported()

        val lines = loggedBy {
            bench.press(KeyAction.Letter(','))
            bench.press(KeyAction.Backspace)
        }

        assertEquals(ownSpaceLine(OwnSpaceEvent.MarkTookPlace), lines.first())
        assertEquals(ownSpaceLine(OwnSpaceEvent.BroughtBack), lines[1])
        assertEquals(backspaceLine(BackspaceBranch.OwnSpaceUndone), lines[2])
    }

    @Test
    fun aGlideAndTheBackspaceThatErasesItAreLoggedWithoutTheWord() {
        bench.type("ну ")

        val lines = loggedBy {
            bench.glide("привет")
            val afterGlide = strip(StripTrigger.Glide, StripSource.GlideAlternatives)
            bench.press(KeyAction.Backspace)
            assertEquals(afterGlide, bench.logged.first())
        }

        assertEquals(
            listOf(backspaceLine(BackspaceBranch.GlideErased), strip(StripTrigger.Backspace, StripSource.NextWords)),
            lines.drop(1),
        )
        assertFalse(lines.any { "привет" in it })
    }

    @Test
    fun aFieldThatAsksForNoSuggestionsSuggestsTheNextWordsAfterAGlideIsErased() {
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        bench.session.start(field.inputType, 0, 0)

        // A word first: a field left empty would offer the words to begin it with.
        val lines = loggedBy {
            bench.type("ну ")
            bench.glide("привет")
            bench.press(KeyAction.Backspace)
        }

        assertEquals(strip(StripTrigger.Backspace, StripSource.NextWords), lines.last())
    }
}
