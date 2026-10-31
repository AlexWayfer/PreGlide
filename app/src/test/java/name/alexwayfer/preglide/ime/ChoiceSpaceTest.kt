package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoiceSpaceTest {
    private val field = KeyboardBench.Field()
    private val space = ChoiceSpace(field)

    @Test
    fun onlyAClosingMarkAfterAChosenWordDefersASpace() {
        for ((before, after) in listOf(
            "слово" to ".", "слово" to " всем", "слово " to ".", "" to ".",
            "слово" to "", "слово" to "другое", "слово" to "\"", "\"слово" to "\"",
        )) {
            field.text = before
            field.after = after
            space.chosen()
            val expected = before == "слово" && after == "." || before == "\"слово"
            assertEquals(expected, space.take('б'))
            assertFalse(space.take('ы'))
        }
    }

    @Test
    fun changedTextOnEitherSideCancelsTheSpace() {
        for (before in listOf("слово", "другое")) {
            field.text = "слово"
            field.after = "."
            space.chosen()
            field.text = before
            field.after = if (before == "слово") "," else "."
            assertFalse(space.take('б'))
        }
    }

    @Test
    fun aNonLetterConsumesThePendingSpaceAndShiftKeepsIt() {
        for (char in listOf('1', '-', ',', ' ')) {
            field.text = "слово"
            field.after = "."
            space.chosen()
            space.beforeKey(KeyAction.Letter(char))
            assertFalse(space.take(char))
            assertFalse(space.take('б'))
        }
        space.chosen()
        space.beforeKey(KeyAction.Shift)
        assertTrue(space.take('Б'))
    }

    @Test
    fun anotherActionOrExplicitResetCancelsTheSpace() {
        field.text = "слово"
        field.after = "."
        space.chosen()
        space.beforeKey(KeyAction.Backspace)
        assertFalse(space.take('б'))
        space.chosen()
        space.forget()
        assertFalse(space.take('б'))
    }
}
