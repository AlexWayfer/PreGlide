package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class PairsTest {
    private val words = listOf(Word("как", 90), Word("всё", 50), Word("все", 40), Word("дела", 30), Word("Москва", 20))

    @Test
    fun `texts blend by their shares, whatever their size`() {
        val web = PairCounts(listOf(WordPair("как", "дела", 10)), total = 100)
        val subtitles = PairCounts(listOf(WordPair("как", "дела", 30), WordPair("как", "москва", 10)), total = 50)

        assertEquals(
            listOf(NextWord("как", "дела", 350_000_000), NextWord("как", "Москва", 100_000_000)),
            nextWords(listOf(web to 0.5, subtitles to 0.5), words),
        )
    }

    @Test
    fun `spellings a search sees as one pool their next words`() {
        val counts = PairCounts(listOf(WordPair("всё", "дела", 1), WordPair("все", "дела", 1)), total = 2)

        assertEquals(listOf(NextWord("все", "дела", PER_BILLION)), nextWords(listOf(counts to 1.0), words))
    }

    @Test
    fun `next words spelled with and without yo pool under the spelling with it`() {
        val yo = mapOf("еще" to "ещё")
        val withAndWithout = listOf(Word("как", 90), Word("еще", 50), Word("ещё", 40))
        val counts = PairCounts(listOf(WordPair("как", "еще", 1), WordPair("как", "ещё", 1)), total = 2)

        assertEquals(listOf(NextWord("как", "ещё", PER_BILLION)), nextWords(listOf(counts to 1.0), withAndWithout, yo))
    }

    @Test
    fun `words come in dictionary order, each with its most frequent next words first`() {
        val pairs = listOf(WordPair("дела", "как", 5), WordPair("как", "всё", 2), WordPair("как", "дела", 3))

        val next = nextWords(listOf(PairCounts(pairs, total = 10) to 1.0), words)

        assertEquals(listOf("как" to "дела", "как" to "всё", "дела" to "как"), next.map { it.previous to it.next })
    }

    @Test
    fun `a word repeated is no next word`() {
        val pairs = listOf(WordPair("как", "как", 9), WordPair("всё", "все", 8), WordPair("как", "дела", 1))
        val counts = PairCounts(pairs, total = 18)

        assertEquals(listOf("дела"), nextWords(listOf(counts to 1.0), words).map { it.next })
    }

    @Test
    fun `next words the dictionary lacks and empty texts are skipped`() {
        val counts = PairCounts(listOf(WordPair("как", "нету", 5)), total = 5)
        val empty = PairCounts(emptyList(), total = 0)

        assertEquals(emptyList<NextWord>(), nextWords(listOf(counts to 1.0, empty to 1.0), words))
    }

    @Test
    fun `a word keeps at most its share of next words`() {
        val many = (1..NEXT_WORDS + 3).map { Word("слово$it", 1) }
        val counts = PairCounts(many.map { WordPair("как", it.spelling, 1) }, total = many.size.toLong())

        assertEquals(NEXT_WORDS, nextWords(listOf(counts to 1.0), words + many).size)
    }

    @Test
    fun `the next words file has the word, the next word's line and the frequency on each line`() {
        val next = listOf(NextWord("как", "дела", 5), NextWord("все", "Москва", 3))

        // In search order the dictionary is всё, все, дела, как, Москва.
        assertEquals("как\t2\t5\nвсе\t4\t3\n", nextWordsText(next, words))
    }
}
