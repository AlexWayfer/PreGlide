package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyTest {
    private val dictionary = Dictionary.parse(
        sequenceOf(
            "иронично\t1825",
            "иронии\t900",
            "ирония\t800",
            "привет\t100",
            "приветы\t20",
            "пират\t30",
            "Москва\t70",
            "мост\t60",
            "ещё\t500",
            "don't\t400",
            "dont\t1",
        ),
    )

    @Test
    fun `an edit is a char typed in, left out, replaced or swapped with its neighbor`() {
        assertEquals(1, editDistance("привт", "привет"))
        assertEquals(1, editDistance("приввет", "привет"))
        assertEquals(1, editDistance("прибет", "привет"))
        assertEquals(1, editDistance("пирвет", "привет"))
        assertEquals(2, editDistance("пирвте", "привет"))
        assertEquals(0, editDistance("привет", "привет"))
    }

    @Test
    fun `an edit distance ignores case, yo and apostrophes, as a search does`() {
        assertEquals(0, editDistance("Еще", "ещё"))
        assertEquals(0, editDistance("dont", "don't"))
        assertEquals(1, editDistance("ПРИВЕТ", "привт"))
    }

    @Test
    fun `an edit distance counts every char of a word with nothing in common`() {
        assertEquals(3, editDistance("абв", ""))
        assertEquals(4, editDistance("абвг", "деёж"))
    }

    @Test
    fun `near words are those within the edits allowed`() {
        assertEquals(
            setOf(Near("привет", 1, 100)),
            dictionary.near("привт", 1).toSet(),
        )
        assertEquals(
            setOf(Near("привет", 1, 100), Near("приветы", 2, 20), Near("пират", 2, 30)),
            dictionary.near("привт", 2).toSet(),
        )
    }

    @Test
    fun `near words are found whatever their case, yo and apostrophes`() {
        assertEquals(setOf(Near("Москва", 1, 70)), dictionary.near("моква", 1).toSet())
        assertEquals(setOf(Near("ещё", 0, 500)), dictionary.near("еще", 0).toSet())
        assertEquals(setOf(Near("don't", 0, 400), Near("dont", 0, 1)), dictionary.near("dont", 0).toSet())
    }

    @Test
    fun `near words include a swap of two neighbors`() {
        assertEquals(setOf(Near("иронично", 1, 1825)), dictionary.near("ирончино", 1).toSet())
    }

    @Test
    fun `nothing is near a word far from every word`() {
        assertEquals(emptyList<Near>(), dictionary.near("зззззз", 2))
        assertEquals(emptyList<Near>(), Dictionary.parse(emptySequence()).near("мост", 2))
    }
}
