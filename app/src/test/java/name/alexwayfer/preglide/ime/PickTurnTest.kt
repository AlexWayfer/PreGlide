package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PickTurnTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val bench = KeyboardBench { folder.root }
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
    fun theWordsForTheTextAPickLeftTurnInOnce() {
        val text = KeyboardBench.Field()
        var turns = 0
        val turn = PickTurn(text) { turns++ }

        text.text = "ну привет "
        turn.picked()
        // The prediction is on its way: the strip shows nothing new yet.
        assertEquals(0, turns)
        turn.shown("ну привет ")
        assertEquals(1, turns)
        // The strip suggests anew for the same text, as after shift: no turn again.
        turn.shown("ну привет ")
        assertEquals(1, turns)
    }

    @Test
    fun wordsForTextTypedSinceThePickDoNotTurnIn() {
        val text = KeyboardBench.Field()
        var turns = 0
        val turn = PickTurn(text) { turns++ }

        text.text = "ну привет "
        turn.picked()
        turn.shown("ну привет к")
        turn.shown("ну привет ")
        assertEquals(0, turns)

        turn.picked()
        turn.forget()
        turn.shown("ну привет ")
        assertEquals(0, turns)
    }

    @Test
    fun aNextWordPickedTurnsTheStripToTheWordsAfterIt() {
        bench.type("привет как ")
        val turns = keyboard.nextWordsTurns

        choose("привет")

        assertEquals("Привет как привет ", field.text)
        assertEquals(turns + 1, keyboard.nextWordsTurns)
    }

    @Test
    fun aWordPickedToFinishTheOneTypedTurnsTheStripToo() {
        bench.type("привет как ну прив")
        val turns = keyboard.nextWordsTurns

        choose("привет")

        assertEquals(turns + 1, keyboard.nextWordsTurns)
    }

    @Test
    fun aWordTheGlideMayHaveMeantPickedTurnsTheStripOnce() {
        bench.glideStrip = GlideStrip.GlideWords
        bench.type("привет как ")
        bench.glide("привет")
        val turns = keyboard.nextWordsTurns

        choose("приват")

        assertEquals(turns + 1, keyboard.nextWordsTurns)
    }

    @Test
    fun aNextWordPickedAfterAGlideTurnsTheStripOnceMore() {
        bench.glideStrip = GlideStrip.NextWords
        bench.type("привет как ")
        bench.glide("привет")
        val turns = keyboard.nextWordsTurns

        choose("как")

        assertEquals(turns + 1, keyboard.nextWordsTurns)
    }

    @Test
    fun aSpaceTypedDoesNotTurnTheStrip() {
        bench.type("привет как ну привет")
        val turns = keyboard.nextWordsTurns

        bench.press(KeyAction.Space)

        assertEquals(turns, keyboard.nextWordsTurns)
    }
}
