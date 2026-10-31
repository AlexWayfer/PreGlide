package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader

class NextWordsTest {
    private val dictionary = Dictionary.parse(sequenceOf("как\t90", "дела\t10", "ты\t40", "всё\t30", "хорошо\t20"))

    @Test
    fun `next words keep the file's order and skip words the dictionary lacks`() {
        val next = NextWords.read(StringReader("как\tдела\t50\nкак\tнету\t45\nкак\tты\t40\n\n"), dictionary)

        assertEquals(listOf("дела", "ты"), next.after("как"))
        assertEquals(1, next.size)
    }

    @Test
    fun `a next word may come as its line in the dictionary file`() {
        // The dictionary's lines: как, дела, ты, всё, хорошо.
        val next = NextWords.parse(sequenceOf("как\t1\t50", "как\t2\t40", "как\t99\t30"), dictionary)

        assertEquals(listOf("дела", "ты"), next.after("как"))
        assertEquals(listOf(Scored("дела", 50), Scored("ты", 40)), next.scoredAfter("как"))
    }

    @Test
    fun `a word finds its next words whatever its case and yo`() {
        val next = NextWords.parse(sequenceOf("всё\tхорошо\t9", "все\tхорошо\t5", "все\tты\t3"), dictionary)

        assertEquals(listOf("хорошо", "ты"), next.after("Всё"))
        assertEquals(listOf("хорошо", "ты"), next.after("все"))
    }

    @Test
    fun `next words are the dictionary's own copies`() {
        val next = NextWords.parse(sequenceOf("как\tдела\t50"), dictionary)

        assertSame(dictionary.find("дела"), next.after("как").single())
    }

    @Test
    fun `a word with no next words known has none`() {
        assertEquals(emptyList<String>(), NextWords.None.after("как"))
    }

    @Test
    fun `a line without three fields is an error`() {
        assertThrows(IllegalArgumentException::class.java) {
            NextWords.parse(sequenceOf("как\tдела"), dictionary)
        }
    }

    @Test
    fun `the app's next words load and know common pairs`() {
        val assets = File("../app/src/main/assets/dictionaries")
        val russian = File(assets, "ru.tsv").reader().use(Dictionary::read)
        val next = File(assets, "ru.next.tsv").reader().use { NextWords.read(it, russian) }
        val english = File(assets, "en.tsv").reader().use(Dictionary::read)
        val englishNext = File(assets, "en.next.tsv").reader().use { NextWords.read(it, english) }

        assertTrue(next.size > 10_000)
        assertTrue(next.after("как").toString(), "дела" in next.after("как"))
        assertTrue(englishNext.after("thank").toString(), "you" in englishNext.after("thank"))
    }
}
