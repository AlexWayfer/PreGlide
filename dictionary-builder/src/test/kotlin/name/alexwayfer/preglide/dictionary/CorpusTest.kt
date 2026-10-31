package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CorpusTest {
    private fun Corpus.words(vararg candidates: String): List<String> =
        candidates.filter { Regex(wordPattern).matches(it) }

    @Test
    fun `Russian words are Cyrillic, hyphenated ones included`() {
        assertEquals(
            listOf("ёжик", "кто-то", "Санкт-Петербург"),
            Corpus.Russian.words("ёжик", "кто-то", "Санкт-Петербург", "don't", "Moscow", "кто-", "Ёж'ик", "мiр"),
        )
    }

    @Test
    fun `English words are Latin, with apostrophes and hyphens`() {
        assertEquals(
            listOf("don't", "well-known", "I"),
            Corpus.English.words("don't", "well-known", "I", "привет", "'tis", "café", "x--y"),
        )
    }

    @Test
    fun `every language has its own dictionary file`() {
        assertEquals(listOf("ru", "en"), Corpus.all.map { it.code })
    }

    @Test
    fun `no arguments build every language`() {
        assertEquals(Corpus.all, corporaFor(emptyArray()))
    }

    @Test
    fun `arguments pick languages by code`() {
        assertEquals(listOf(Corpus.English), corporaFor(arrayOf("en")))
    }

    @Test
    fun `an unknown language is an error`() {
        assertThrows(IllegalArgumentException::class.java) { corporaFor(arrayOf("de")) }
    }
}
