package name.alexwayfer.preglide.engine.suggest

import name.alexwayfer.preglide.engine.layout.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberEndingsTest {
    @Test
    fun `a number gets the endings that follow its kind most`() {
        assertEquals(listOf("й", "го", "х"), numberEndings("с 2-", Language.Russian, 3))
        assertEquals(listOf("х", "ти", "е"), numberEndings("в 90-", Language.Russian, 3))
        assertEquals(listOf("го", "й", "ти"), numberEndings("до 12-", Language.Russian, 3))
        assertEquals(listOf("го", "м", "х"), numberEndings("в 2020-", Language.Russian, 3))
    }

    @Test
    fun `the endings start as typed, in its case`() {
        assertEquals(listOf("ой", "ом", "ого"), numberEndings("со 2-о", Language.Russian, 3))
        assertEquals(listOf("Го"), numberEndings("С 2-Г", Language.Russian, 3))
        // An ending a number of this kind seldom has comes from the others.
        assertEquals(listOf("ый", "ых"), numberEndings("с 2-ы", Language.Russian, 3))
    }

    @Test
    fun `only a Russian number and a hyphen get endings`() {
        assertEquals(emptyList<String>(), numberEndings("c 2-", Language.English, 3))
        assertEquals(emptyList<String>(), numberEndings("с 2 ", Language.Russian, 3))
        assertEquals(emptyList<String>(), numberEndings("кто-", Language.Russian, 3))
        assertEquals(emptyList<String>(), numberEndings("с 2-щ", Language.Russian, 3))
    }

    @Test
    fun `the words the user typed after a number go among the endings, the most used first`() {
        val personal = PersonalWords()
        personal.learn("комнатная", listOf(AFTER_NUMBER), 0)
        repeat(2) { personal.learn("разовое", listOf(AFTER_NUMBER), 0) }
        personal.learn("гостевой", listOf(AFTER_NUMBER), 0)

        assertEquals(listOf("й", "разовое", "го"), numberEndings("с 2-", Language.Russian, 3, personal))
        assertEquals(listOf("Го", "Гостевой"), numberEndings("С 2-Г", Language.Russian, 3, personal))
        // With no ending to start so, the strip suggests as for any word.
        assertEquals(emptyList<String>(), numberEndings("с 2-р", Language.Russian, 3, personal))
    }

    @Test
    fun `an ending is told from a word typed after a number`() {
        assertTrue(isNumberEndingWord("го"))
        assertTrue(isNumberEndingWord("Ого"))
        assertFalse(isNumberEndingWord("разовое"))
    }

    @Test
    fun `a number's kind is its last digit, a teen, or a year`() {
        assertEquals("2", numberKind("2"))
        assertEquals("1", numberKind("101"))
        assertEquals("teen", numberKind("13"))
        assertEquals("teen", numberKind("113"))
        assertEquals("year", numberKind("1990"))
    }
}
