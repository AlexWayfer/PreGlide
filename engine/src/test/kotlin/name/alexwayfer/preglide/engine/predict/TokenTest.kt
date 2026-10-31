package name.alexwayfer.preglide.engine.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenTest {
    private fun words(vararg texts: String): List<Token> = texts.map { Token.Word(it) }

    @Test
    fun `words come lowercase, with hyphens and apostrophes inside them`() {
        assertEquals(words("кто-то", "don't", "привет"), tokens("Кто-то Don’t ПРИВЕТ"))
    }

    @Test
    fun `a run of apostrophes inside a word joins it as one`() {
        assertEquals(words("you're", "i'm", "don't"), tokens("You''re I’’m don'’t"))
    }

    @Test
    fun `a run of apostrophes at a word's edge is no part of it`() {
        assertEquals(listOf(Token.Word("yes"), Token.Mark('"'), Token.Word("no")), tokens("yes'' ''no"))
    }

    @Test
    fun `a hyphen or an apostrophe at a word's edge is no part of it`() {
        assertEquals(
            listOf(Token.Word("из"), Token.Mark('—'), Token.Mark('"'), Token.Word("em")),
            tokens("из- 'em"),
        )
    }

    @Test
    fun `marks are brought to the few the model tells apart`() {
        assertEquals(
            listOf('"', '.', '—', '(', ')', '"', ',', '!', '?', ':', ';').map { Token.Mark(it) },
            tokens("« … – [ ] ” , ! ? : ;"),
        )
    }

    @Test
    fun `a repeated mark and blank lines count once`() {
        assertEquals(
            listOf(Token.Word("ну"), Token.Mark('.'), Token.LineBreak, Token.Word("да"), Token.Mark('!')),
            tokens("ну...\r\n\r\n\nда!!!"),
        )
    }

    @Test
    fun `a different mark after one is kept`() {
        assertEquals(listOf(Token.Mark('?'), Token.Mark('!')), tokens("?!"))
    }

    @Test
    fun `numbers keep their points, commas and colons, and stop at letters`() {
        assertEquals(
            listOf(Token.Number, Token.Number, Token.Number, Token.Word("км"), Token.Number, Token.Mark('.')),
            tokens("3.14 1,000 12:30км 5."),
        )
    }

    @Test
    fun `repeated words and numbers are all kept`() {
        assertEquals(words("да", "да") + Token.Number + Token.Number, tokens("да да 1 2"))
    }

    @Test
    fun `spaces, emoji and other symbols are left out`() {
        assertEquals(words("привет", "мир"), tokens("  привет 😀 @#% мир\t"))
    }

    @Test
    fun `an empty text has no tokens`() {
        assertEquals(emptyList<Token>(), tokens(""))
    }

    @Test
    fun `chars that are no marks map to none`() {
        assertNull(markOf('@'))
        assertNull(markOf(' '))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a mark the model does not know is refused`() {
        Token.Mark('@')
    }
}
