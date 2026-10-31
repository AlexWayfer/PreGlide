package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsTest {
    @Test
    fun `a word that comes in lowercase often enough is spelled in lowercase`() {
        assertEquals("привет", spelling(listOf(Form("Привет", 70), Form("привет", 30))))
    }

    @Test
    fun `a name keeps its capital letter`() {
        assertEquals("Москва", spelling(listOf(Form("Москва", 95), Form("москва", 3), Form("МОСКВА", 2))))
    }

    @Test
    fun `a word never in lowercase takes its most frequent spelling`() {
        assertEquals("iPhone", spelling(listOf(Form("IPhone", 10), Form("iPhone", 40), Form("IPHONE", 5))))
    }

    @Test
    fun `equally frequent spellings pick the same one every time`() {
        assertEquals("NASA", spelling(listOf(Form("NASA", 10), Form("Nasa", 10))))
    }

    @Test
    fun `within sentences a greeting is lowercase and a name capitalized`() {
        // Subtitle lines start with "Привет", so it is capitalized more often in all, but not within sentences.
        assertEquals("привет", spelling(listOf(Form("Привет", 900, 10), Form("привет", 100, 90))))
        assertEquals("Москва", spelling(listOf(Form("Москва", 100, 80), Form("москва", 30, 20))))
    }

    @Test
    fun `spellings that differ in case only merge into one word`() {
        val words = mergeCase(listOf(Form("Привет", 70), Form("привет", 30), Form("мир", 5)))

        assertEquals(setOf(Word("привет", 100), Word("мир", 5)), words.toSet())
    }

    @Test
    fun `one-letter words are kept and initials dropped`() {
        assertTrue(keeps(Word("в", 1), Corpus.Russian))
        assertTrue(keeps(Word("I", 1), Corpus.English))
        assertFalse(keeps(Word("П", 1), Corpus.Russian))
        assertFalse(keeps(Word("x", 1), Corpus.English))
    }

    @Test
    fun `run-together text is dropped`() {
        assertTrue(keeps(Word("a".repeat(32), 1), Corpus.English))
        assertFalse(keeps(Word("a".repeat(33), 1), Corpus.English))
    }

    @Test
    fun `the dictionary keeps the most frequent words up to its size`() {
        val corpus = Corpus.English.copy(size = 3)
        val forms = listOf(
            Form("the", 50),
            Form("The", 50),
            Form("cat", 20),
            Form("bat", 20),
            Form("x", 90),
            Form("dog", 10),
        )

        assertEquals(listOf(Word("the", 100), Word("bat", 20), Word("cat", 20)), dictionary(forms, corpus))
    }

    @Test
    fun `below its size the dictionary keeps the words its spelling dictionary has, down to the floor`() {
        val spelling = SpellingDictionary("https://example.com/ru.aff", "https://example.com/ru.dic", floor = 50)
        val corpus = Corpus.Russian.copy(size = 1, spelling = spelling)
        val forms = listOf(
            Form("мир", 1000),
            Form("ЯЮЛЮ", 300),
            Form("Нахваливают", 10),
            Form("нахваливают", 109),
            Form("завезёшь", 60),
            Form("нахваливала", 49),
        )

        // The forms are keyed with е for ё, so the text's "завезёшь" matches "завезешь".
        assertEquals(
            listOf(Word("мир", 1000), Word("нахваливают", 119), Word("завезёшь", 60)),
            dictionary(forms, corpus, spelled = setOf("нахваливают", "нахваливала", "завезешь")),
        )
    }

    @Test
    fun `blending weighs each text by its share, whatever its size`() {
        val web = Counts(listOf(Form("приводит", 60, 50), Form("привет", 40, 30)), total = 100)
        val subtitles = Counts(listOf(Form("привет", 9), Form("Привет", 1)), total = 10)

        val forms = blend(listOf(web to 0.5, subtitles to 0.5))

        assertEquals(
            setOf(
                Form("приводит", 300_000_000, 50),
                Form("привет", 650_000_000, 30),
                Form("Привет", 50_000_000, 0),
            ),
            forms.toSet(),
        )
    }

    @Test
    fun `an empty text adds nothing to a blend`() {
        val sources = listOf(Counts(emptyList(), 0) to 0.5, Counts(listOf(Form("да", 3)), 3) to 1.0)

        assertEquals(listOf(Form("да", PER_BILLION)), blend(sources))
    }

    @Test
    fun `a subtitle list gives the language's words and their total`() {
        val lines = sequenceOf("68949491 .", "  535832 Привет", "   54212 привет", " 900 hello", " 40 Don’t", "broken")

        assertEquals(
            Counts(listOf(Form("Привет", 535_832), Form("привет", 54_212)), total = 590_044),
            subtitleCounts(lines, Corpus.Russian),
        )
        assertEquals(listOf(Form("hello", 900), Form("Don't", 40)), subtitleCounts(lines, Corpus.English).forms)
    }

    @Test
    fun `coverage is the share of all occurrences the words take`() {
        assertEquals(0.75, coverage(listOf(Word("a", 50), Word("b", 25)), 100), 1e-9)
        assertEquals(0.0, coverage(emptyList(), 0), 0.0)
    }

    @Test
    fun `the dictionary file has a word and its count on each line, in search order`() {
        val words = listOf(Word("привет", 100), Word("всё", 50), Word("Мир", 20), Word("все", 5))

        // "всё" and "все" are one to a search: they keep their order, the more frequent first.
        assertEquals("всё\t50\nвсе\t5\nМир\t20\nпривет\t100\n", dictionaryText(words))
    }
}
