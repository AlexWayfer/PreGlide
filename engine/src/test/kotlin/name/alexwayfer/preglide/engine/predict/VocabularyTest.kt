package name.alexwayfer.preglide.engine.predict

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VocabularyTest {
    private val vocabulary = Vocabulary(listOf("привет", "мир"))
    private val firstWord = Vocabulary.FIXED.size

    @Test
    fun `words come after the fixed tokens, in their order`() {
        assertEquals(firstWord, vocabulary.id(Token.Word("привет")))
        assertEquals(firstWord + 1, vocabulary.id(Token.Word("мир")))
        assertEquals(firstWord + 2, vocabulary.size)
    }

    @Test
    fun `a word the vocabulary lacks is unknown`() {
        assertEquals(Vocabulary.UNKNOWN, vocabulary.id(Token.Word("кот")))
    }

    @Test
    fun `a word written without its yo is the word with it, unless that spelling is a word of its own`() {
        val yo = Vocabulary(listOf("все", "всё", "ещё"))

        assertEquals(firstWord + 2, yo.id(Token.Word("еще")))
        assertEquals(firstWord, yo.id(Token.Word("все")))
        assertEquals(firstWord + 1, yo.id(Token.Word("всё")))
    }

    @Test
    fun `numbers, line breaks and marks have their fixed ids`() {
        assertEquals(Vocabulary.NUMBER, vocabulary.id(Token.Number))
        assertEquals(Vocabulary.LINE_BREAK, vocabulary.id(Token.LineBreak))
        for (mark in MARKS) assertEquals(mark.toString(), vocabulary.tokens[vocabulary.id(Token.Mark(mark))])
    }

    @Test
    fun `a word is found by its id, and a fixed token is no word`() {
        assertEquals("мир", vocabulary.word(firstWord + 1))
        assertNull(vocabulary.word(Vocabulary.UNKNOWN))
        assertNull(vocabulary.word(firstWord + 2))
    }

    @Test
    fun `a text starts with its start token`() {
        assertArrayEquals(
            intArrayOf(Vocabulary.START, firstWord, vocabulary.id(Token.Mark(',')), firstWord + 1, Vocabulary.UNKNOWN),
            vocabulary.textIds("Привет, мир кот"),
        )
    }

    @Test
    fun `the file lists every token in id order and reads back`() {
        val text = vocabulary.text()

        assertEquals(Vocabulary.FIXED + listOf("привет", "мир"), text.lines().dropLast(1))
        assertEquals(vocabulary.words, Vocabulary.read(text.lineSequence()).words)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a file without the fixed tokens is refused`() {
        Vocabulary.read(sequenceOf("привет", "мир"))
    }
}
