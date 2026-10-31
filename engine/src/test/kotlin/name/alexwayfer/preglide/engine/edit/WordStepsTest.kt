package name.alexwayfer.preglide.engine.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class WordStepsTest {
    @Test
    fun `each step goes back to the start of a word, its marks with it`() {
        assertEquals(listOf(12, 8, 0), wordStepsBefore("Привет. Как дела?", cut = false))
    }

    @Test
    fun `a step takes the spaces after its word`() {
        assertEquals(listOf(4, 0), wordStepsBefore("Как дела?  ", cut = false))
        assertEquals(listOf(8, 0), wordStepsBefore("Привет\n\nдела", cut = false))
    }

    @Test
    fun `spaces at the start of the text are a step of their own`() {
        assertEquals(listOf(2, 0), wordStepsBefore("  дела", cut = false))
    }

    @Test
    fun `an empty text has no steps`() {
        assertEquals(emptyList<Int>(), wordStepsBefore("", cut = false))
    }

    @Test
    fun `the start of a cut text is no step, as its word may go on before it`() {
        assertEquals(listOf(4), wordStepsBefore("вет дела", cut = true))
        assertEquals(emptyList<Int>(), wordStepsBefore("ривет", cut = true))
    }

    @Test
    fun `no word is selected until the finger goes the first way`() {
        assertEquals(0, wordsSwiped(left = 29f, first = 30f, step = 40f))
        assertEquals(0, wordsSwiped(left = -100f, first = 30f, step = 40f))
    }

    @Test
    fun `one more word is selected every step after the first`() {
        assertEquals(1, wordsSwiped(left = 30f, first = 30f, step = 40f))
        assertEquals(1, wordsSwiped(left = 69f, first = 30f, step = 40f))
        assertEquals(2, wordsSwiped(left = 70f, first = 30f, step = 40f))
        assertEquals(4, wordsSwiped(left = 150f, first = 30f, step = 40f))
    }
}
