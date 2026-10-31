package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class YoTest {
    private val yo = yoWords(
        sequenceOf(
            "ещё",
            "колёс(|ам|ами|ах)",
            "Королёв(|а|у)",
            "приозёрск(|а)# Приозе́рск, a town",
            "Аксёновен",
            "",
        ),
    )

    @Test
    fun `a list line gives its word with each of its endings, keyed by its spelling with e`() {
        assertEquals("ещё", yo["еще"])
        assertEquals(
            listOf("колёс", "колёсам", "колёсами", "колёсах"),
            listOf("", "ам", "ами", "ах").map { yo["колес$it"] },
        )
        assertEquals("приозёрска", yo["приозерска"])
        assertEquals(11, yo.size)
    }

    @Test
    fun `yo goes where the list has it, in the case of each letter`() {
        assertEquals("ещё", withYo("еще", yo))
        assertEquals("ЕЩЁ", withYo("ЕЩЕ", yo))
        assertEquals("Королёва", withYo("Королева", yo))
        assertEquals("колёсами", withYo("колёсами", yo))
    }

    @Test
    fun `a word not on the list stays as it is`() {
        assertEquals("все", withYo("все", yo))
        assertEquals("колесики", withYo("колесики", yo))
    }

    @Test
    fun `two spellings of a word become one with both counts, the most frequent first`() {
        val words = listOf(Word("еще", 20), Word("мир", 25), Word("ещё", 10), Word("колес", 5))

        assertEquals(listOf(Word("ещё", 30), Word("мир", 25), Word("колёс", 5)), withYo(words, yo))
        assertEquals(2, yoCount(words, yo))
    }
}
