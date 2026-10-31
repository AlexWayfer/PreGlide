package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GlideNextWordsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bench = KeyboardBench { folder.root }.apply { glideStrip = GlideStrip.NextWords }
    private val field = bench.field
    private val keyboard = bench.keyboard

    @Before
    fun startTheField() {
        bench.session.start(field.inputType, 0, 0)
    }

    private fun choose(word: String) {
        bench.typing.chose(word)
        bench.reported()
    }

    @Test
    fun theNextWordsAreSuggestedForTheGlidedWordAndASpace() {
        assertEquals("ну привет ", textToSuggestFor("ну привет", suggests = true, afterGlide = true))
        assertEquals("ну прив", textToSuggestFor("ну прив", suggests = true, afterGlide = false))
        assertEquals("", textToSuggestFor("ну прив", suggests = false, afterGlide = false))
    }

    @Test
    fun aWordFromTheStripGoesAfterTheGlideOnlyOnceTheNextWordsShow() {
        var last: Glided? = Glided("привет", "привет", 0, listOf("приват"), nextWords = true)
        val next = GlideNextWords(last = { last })

        next.typed()
        assertTrue(next.due())
        // The prediction is on its way: the strip still shows the glide's words.
        assertFalse(next.showing())
        assertTrue(next.shown(listOf("как")))
        assertTrue(next.showing())

        next.typed()
        assertFalse(next.showing())
        last = last?.copy(nextWords = false)
        next.shown(listOf("как"))
        assertFalse(next.due())
        assertFalse(next.showing())
        last = null
        assertFalse(next.due())
    }

    @Test
    fun withNoNextWordsTheGlidesOwnShowUntilTheNextGlide() {
        val glided = Glided("привет", "привет", 0, listOf("приват"), nextWords = true)
        var turns = 0
        val next = GlideNextWords(last = { glided }) { turns++ }

        next.typed()
        assertFalse(next.shown(emptyList()))
        assertFalse(next.due())
        assertFalse(next.showing())

        assertEquals(0, turns)

        next.typed()
        assertTrue(next.shown(listOf("как")))
        assertTrue(next.showing())
        assertTrue(next.shown(listOf("как")))
        assertEquals(1, turns)
    }

    @Test
    fun theGlidedWordIsMarkedAmongItsOthersButNotBeforeTheNextWords() {
        bench.glideStrip = GlideStrip.GlideWords
        bench.type("ну ")

        bench.glide("привет")
        assertEquals("привет", keyboard.glidedWord)

        // The finger glides on: the words it shows are no glided word's.
        bench.glideTyping.preview(glideThrough("как"))
        assertEquals(null, keyboard.glidedWord)

        bench.glide("привет")
        bench.press(KeyAction.Space)
        assertEquals(null, keyboard.glidedWord)

        bench.glideStrip = GlideStrip.NextWords
        bench.glide("привет")
        assertEquals(null, keyboard.glidedWord)
    }

    @Test
    fun theNextWordsTurnInOnceForEachGlide() {
        bench.type("привет как ")
        val turns = keyboard.nextWordsTurns

        bench.glide("привет")
        assertEquals(turns + 1, keyboard.nextWordsTurns)
        // The strip suggests anew for the same glide, as after shift: no turn again.
        bench.press(KeyAction.Shift)
        assertEquals(turns + 1, keyboard.nextWordsTurns)

        bench.glide("как")
        assertEquals(turns + 2, keyboard.nextWordsTurns)
    }

    @Test
    fun withNoNextWordsTheStripOffersTheGlidesWordsToSwapOneIn() {
        // A field with no word before the glide: nothing is known to follow привет yet.
        bench.glide("привет")
        assertEquals(listOf("Привет", "Приват"), keyboard.suggestions.take(2))
        assertEquals("Привет", keyboard.glidedWord)

        choose("Приват")
        assertEquals("Приват ", field.text)
    }

    @Test
    fun aGlideOffersTheWordsLikelyNextAndATapPutsOneAfterIt() {
        bench.type("привет как ")

        bench.glide("привет")
        assertEquals("Привет как привет", field.text)
        assertTrue("как" in keyboard.suggestions)
        assertFalse("приват" in keyboard.suggestions)

        choose("как")
        assertEquals("Привет как привет как ", field.text)
        // The glided word stays as it went in.
        assertEquals(2, bench.model.personal.learned("привет")?.count)
        assertEquals(2, bench.model.personal.learned("как")?.count)
    }

    @Test
    fun aNextWordTheGlideMayHaveMeantTooGoesAfterIt() {
        bench.type("привет приват ")

        bench.glide("привет")
        choose("приват")

        assertEquals("Привет приват привет приват ", field.text)
    }

    @Test
    fun aBackspaceStillErasesTheGlidedWordWhole() {
        bench.type("ну ")
        bench.glide("привет")

        bench.press(KeyAction.Backspace)

        assertEquals("Ну ", field.text)
    }

    @Test
    fun theGlidesWordsComeBackWithTheSpaceErasedToSwapOneIn() {
        bench.type("ну ")
        bench.glide("привет")
        bench.press(KeyAction.Space)

        bench.press(KeyAction.Backspace)
        assertEquals(listOf("привет", "приват"), keyboard.suggestions.take(2))

        choose("приват")
        assertEquals("Ну приват ", field.text)
    }

    @Test
    fun theGlidesWordsChoiceOffersThemAfterEveryGlide() {
        bench.glideStrip = GlideStrip.GlideWords
        bench.type("привет как ")

        bench.glide("привет")

        assertEquals(listOf("привет", "приват"), keyboard.suggestions.take(2))
    }

    @Test
    fun autoOffersTheNextWordsAfterAGlideSureOfItsWord() {
        bench.glideStrip = GlideStrip.Auto
        bench.type("как ну ")

        // No other word of the few the keyboard knows glides as как.
        bench.glide("как")

        assertTrue("ну" in keyboard.suggestions)
    }
}
