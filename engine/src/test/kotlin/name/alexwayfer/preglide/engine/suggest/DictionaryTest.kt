package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

class DictionaryTest {
    private val dictionary = Dictionary.read(
        StringReader(
            """
            по	4469959
            после	1408677
            все	2252566
            поэтому	900000
            всё	327859
            всего	800000
            Москва	50000
            мост	40000
            почта	40000

            """.trimIndent(),
        ),
    )

    @Test
    fun `a dictionary knows a word in any case and with yo typed as e, not without its apostrophe`() {
        assertTrue(dictionary.knows("ВСЕ"))
        assertTrue(dictionary.knows("москва"))
        assertTrue(Dictionary.parse(sequenceOf("ещё\t1")).knows("еще"))
        assertTrue(Dictionary.parse(sequenceOf("don't\t1")).knows("Don't"))
        assertFalse(Dictionary.parse(sequenceOf("don't\t1")).knows("dont"))
        assertFalse(dictionary.knows("мос"))
    }

    @Test
    fun `the tree of starts is built at the first walk, or ahead of it on request`() {
        val read = Dictionary.parse(sequenceOf("по\t2", "после\t1"))
        assertFalse(read.isTreeBuilt)
        read.prepareWalks()
        assertTrue(read.isTreeBuilt)
    }

    @Test
    fun `completions are the most frequent words with the prefix`() {
        assertEquals(listOf("по", "после", "поэтому"), dictionary.complete("по", 3))
    }

    @Test
    fun `a completion ignores case, and a typed e finds yo, but a typed yo only yo`() {
        assertEquals(listOf("все", "всего", "всё"), dictionary.complete("Все", 3))
        assertEquals(listOf("всё"), dictionary.complete("всё", 3))
        assertEquals(listOf("Москва", "мост"), dictionary.complete("мос", 3))
    }

    @Test
    fun `equally frequent words keep the dictionary order`() {
        assertEquals(listOf("по", "после", "поэтому", "почта"), dictionary.complete("по", 4))
    }

    @Test
    fun `fewer words than the limit are all there is`() {
        assertEquals(listOf("почта"), dictionary.complete("поч", 3))
        assertEquals(emptyList<String>(), dictionary.complete("щ", 3))
        assertEquals(emptyList<String>(), dictionary.complete("яяя", 3))
    }

    @Test
    fun `nothing completes an empty prefix or for no room`() {
        assertEquals(emptyList<String>(), dictionary.complete("", 3))
        assertEquals(emptyList<String>(), dictionary.complete("по", 0))
    }

    @Test
    fun `finding a word takes its exact spelling`() {
        assertEquals("всё", dictionary.find("всё"))
        assertEquals("все", dictionary.find("все"))
        assertEquals("Москва", dictionary.find("Москва"))
        assertEquals(null, dictionary.find("москва"))
        assertEquals(null, dictionary.find("мос"))
        assertEquals(null, dictionary.find("яяя"))
    }

    @Test
    fun `a word is found by its line in the file, whatever order the file is in`() {
        // The test dictionary is in frequency order, so it was sorted on reading.
        assertEquals("по", dictionary.atLine(0))
        assertEquals("почта", dictionary.atLine(8))
        assertEquals(null, dictionary.atLine(9))
        assertEquals(null, dictionary.atLine(-1))

        val inOrder = Dictionary.parse(sequenceOf("все\t5", "всё\t9", "мост\t1"))
        assertEquals("всё", inOrder.atLine(1))
        assertEquals(null, inOrder.atLine(3))
    }

    @Test
    fun `blank lines are skipped`() {
        assertEquals(9, dictionary.size)
    }

    @Test
    fun `a line without a frequency is an error`() {
        assertThrows(IllegalArgumentException::class.java) { Dictionary.parse(sequenceOf("слово")) }
    }

    @Test
    fun `the app's dictionaries load and complete common words`() {
        val assets = File("../app/src/main/assets/dictionaries")
        val russian = File(assets, "ru.tsv").reader().use(Dictionary::read)
        val english = File(assets, "en.tsv").reader().use(Dictionary::read)

        assertTrue(russian.size > 100_000)
        assertEquals("привет", russian.complete("прив", 3).first())
        assertEquals("Москва", russian.complete("москва", 3).first())
        assertTrue(english.complete("dont", 3).toString(), "don't" in english.complete("dont", 3))
    }
}
