package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldStartTest {
    private val personal = PersonalWords()

    private fun began(word: String, times: Int, at: Long = 1) = repeat(times) { personal.starts.learn(word, at) }

    @Test
    fun `an empty field offers the words the user often begins with, the most used first`() {
        began("да", times = 4)
        began("привет", times = 6)
        began("слушай", times = 3)

        assertEquals(listOf("привет", "да", "слушай"), fieldStartWords(personal, count = 3))
    }

    @Test
    fun `a word begun with too few times is not offered`() {
        began("привет", times = FIELD_START_USES - 1)

        assertEquals(emptyList<String>(), fieldStartWords(personal, count = 3))
    }

    @Test
    fun `a word that begins too small a share of the fields is not offered`() {
        began("привет", times = 40)
        began("да", times = 5)
        began("ну", times = 3)
        began("ок", times = 1)
        began("ага", times = 1)

        // 50 fields in all: "да" begins one in ten, at the bar; "ну", used often enough, stays below it.
        assertEquals(listOf("привет", "да"), fieldStartWords(personal, count = 3))
    }

    @Test
    fun `equal uses put the latest used first, and only as many as asked for`() {
        began("да", times = 3, at = 1)
        began("ага", times = 3, at = 2)

        assertEquals(listOf("ага"), fieldStartWords(personal, count = 1))
    }
}
