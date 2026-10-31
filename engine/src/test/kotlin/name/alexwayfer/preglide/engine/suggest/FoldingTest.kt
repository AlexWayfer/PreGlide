package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldingTest {
    @Test
    fun `case and yo do not matter to a search`() {
        assertEquals('е', fold('Ё'))
        assertEquals('е', fold('ё'))
        assertEquals('ж', fold('Ж'))
        assertEquals('q', fold('Q'))
    }

    @Test
    fun `every spelling of a word shares its folded key`() {
        assertEquals("все", foldedKey("Всё"))
        assertEquals("dont", foldedKey("Don't"))
        assertEquals("кто-то", foldedKey("Кто-то"))
    }

    @Test
    fun `a prefix matches whatever its case, yo and apostrophes`() {
        assertTrue(startsWithFolded("ещё", "Ещ"))
        assertTrue(startsWithFolded("всё", "все"))
        assertTrue(startsWithFolded("don't", "dont"))
        assertTrue(startsWithFolded("dont", "don'"))
        assertTrue(startsWithFolded("кто-то", "кто-"))
        assertTrue(startsWithFolded("слово", ""))
    }

    @Test
    fun `a typed yo is no start of a word with e there`() {
        assertFalse(keepsTypedYo("через", "чё"))
        assertFalse(keepsTypedYo("Елка", "Ёл"))
        assertTrue(keepsTypedYo("чём", "чё"))
        assertTrue(keepsTypedYo("ЁЛКА", "ёл"))
        assertTrue(keepsTypedYo("don't", "dont"))
        assertTrue(keepsTypedYo("колёсико", "колёс"))
    }

    @Test
    fun `a typed e starts a word with yo as well as one with e`() {
        assertTrue(keepsTypedYo("ещё", "еще"))
        assertTrue(keepsTypedYo("колесико", "колес"))
        assertTrue(keepsTypedYo("слово", ""))
    }

    @Test
    fun `a word starts like the typed one when it starts so folded and keeps its yo`() {
        assertTrue(startsLikeTyped("чём", "Чё"))
        assertTrue(startsLikeTyped("через", "Че"))
        assertFalse(startsLikeTyped("через", "чё"))
        assertFalse(startsLikeTyped("привет", "пока"))
    }

    @Test
    fun `a word typed without its yo is that word, but not the other way round`() {
        assertTrue(isWithoutYo("еще", "ещё"))
        assertTrue(isWithoutYo("ЕЩЕ", "ещё"))
        assertTrue(isWithoutYo("всё", "всё"))
        assertFalse(isWithoutYo("колёсики", "колесики"))
        assertFalse(isWithoutYo("ещ", "ещё"))
        assertFalse(isWithoutYo("ешё", "ещё"))
    }

    @Test
    fun `a prefix does not match a shorter or a different word`() {
        assertFalse(startsWithFolded("при", "привет"))
        assertFalse(startsWithFolded("кото", "кто"))
        assertFalse(startsWithFolded("ктото", "кто-"))
    }

    @Test
    fun `search order puts a word before the longer words it starts`() {
        val words = listOf("привет", "При", "приветствие", "приз", "прив", "Ёж", "ель")

        assertEquals(
            listOf("Ёж", "ель", "При", "прив", "привет", "приветствие", "приз"),
            words.sortedWith(::compareFolded),
        )
    }

    @Test
    fun `words that differ in case, yo or apostrophes are equal in search order`() {
        assertEquals(0, compareFolded("Всё", "все"))
        assertEquals(0, compareFolded("don't", "dont"))
        assertTrue(compareFolded("don't", "done") > 0)
    }

    @Test
    fun `a folded char is the char of the folded key at its position`() {
        assertEquals('е', foldedCharAt("Ёж", 0))
        assertEquals('t', foldedCharAt("don't", 3))
        assertEquals(null, foldedCharAt("don't", 4))
    }

    @Test
    fun `words fold the same when they differ only in case and yo`() {
        assertTrue(foldsTheSame("Ещё", "еще"))
        assertFalse(foldsTheSame("don't", "dont"))
        assertFalse(foldsTheSame("кот", "кит"))
    }
}
