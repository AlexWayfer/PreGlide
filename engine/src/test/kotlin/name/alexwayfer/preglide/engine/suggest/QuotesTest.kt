package name.alexwayfer.preglide.engine.suggest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotesTest {
    @Test
    fun aQuoteBeforeAWordOpensAQuotation() {
        assertTrue(quoteIsOpen('"', "Он сказал \"привет"))
        assertTrue(quoteIsOpen('"', "\"Привет"))
        assertTrue(quoteIsOpen('`', "Запусти `verify"))
    }

    @Test
    fun aQuotationHoldsAPhraseOrSentences() {
        assertTrue(quoteIsOpen('"', "Он сказал \"ну привет. Как дела? Я тут"))
    }

    @Test
    fun aQuoteAfterAWordClosesIt() {
        assertFalse(quoteIsOpen('"', "Он сказал \"привет\" и ушёл"))
        assertFalse(quoteIsOpen('`', "Запусти `verify` и"))
    }

    @Test
    fun manyQuotationsInALineOpenAndCloseInTurn() {
        assertFalse(quoteIsOpen('"', "\"раз\" и \"два\" и"))
        assertTrue(quoteIsOpen('"', "\"раз\" и \"два\" и \"три"))
    }

    @Test
    fun aQuoteOpensAfterAnOpeningBracket() {
        assertTrue(quoteIsOpen('"', "Фильм (\"Матрица"))
        assertTrue(quoteIsOpen('`', "Флаг «`-SkipLint"))
    }

    @Test
    fun quotesInARunOrAmidAWordDoNothing() {
        assertFalse(quoteIsOpen('`', "```kotlin"))
        assertFalse(quoteIsOpen('"', "Пустые \"\" кавычки"))
        assertFalse(quoteIsOpen('"', "Слово\"слово"))
        assertFalse(quoteIsOpen('"', "Одна \" кавычка"))
    }

    @Test
    fun aQuoteBeforeASpaceDoesNotCloseAQuotation() {
        assertTrue(quoteIsOpen('"', "\"раз \" два"))
    }

    @Test
    fun aQuotationEndsWithItsLine() {
        assertFalse(quoteIsOpen('"', "Он сказал \"привет\nИ ушёл"))
    }

    @Test
    fun eachKindOfQuoteCountsApart() {
        assertFalse(quoteIsOpen('`', "Он сказал \"привет"))
        assertTrue(quoteIsOpen('"', "\"Код `x` тут"))
    }
}
