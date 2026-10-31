package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardStateTest {
    @Test
    fun spaceAfterASymbolReturnsToTheLetters() {
        val state = KeyboardState().apply { page = Page.Symbols }

        state.afterTyping('%')
        state.afterTyping(' ')

        assertEquals(Page.Letters, state.page)
    }

    @Test
    fun spaceAfterASymbolOnTheSecondPageReturnsToTheLetters() {
        val state = KeyboardState().apply { page = Page.MoreSymbols }

        state.afterTyping('π')
        state.afterTyping(' ')

        assertEquals(Page.Letters, state.page)
    }

    @Test
    fun symbolTypedBeforeMovingToTheSecondPageCounts() {
        val state = KeyboardState().apply { page = Page.Symbols }

        state.afterTyping('1')
        state.page = Page.MoreSymbols
        state.afterTyping(' ')

        assertEquals(Page.Letters, state.page)
    }

    @Test
    fun lineBreakAfterASymbolReturnsToTheLetters() {
        val state = KeyboardState().apply { page = Page.Symbols }

        state.afterTyping(')')
        state.afterTyping('\n')

        assertEquals(Page.Letters, state.page)
    }

    @Test
    fun lineBreakAloneKeepsTheSymbols() {
        val state = KeyboardState().apply { page = Page.Symbols }

        state.afterTyping('\n')
        state.afterTyping('\n')

        assertEquals(Page.Symbols, state.page)
    }

    @Test
    fun spaceAloneKeepsTheSymbols() {
        val state = KeyboardState().apply { page = Page.Symbols }

        state.afterTyping(' ')
        state.afterTyping(' ')

        assertEquals(Page.Symbols, state.page)
    }

    @Test
    fun symbolsOpenedAgainWaitForANewSymbol() {
        val state = KeyboardState().apply { page = Page.Symbols }
        state.afterTyping('%')
        state.page = Page.Letters

        state.page = Page.Symbols
        state.afterTyping(' ')

        assertEquals(Page.Symbols, state.page)
    }

    @Test
    fun lettersTypedOnTheLettersPageDoNotCountAsSymbols() {
        val state = KeyboardState()
        state.afterTyping('а')

        state.page = Page.Symbols
        state.afterTyping(' ')

        assertEquals(Page.Symbols, state.page)
    }

    @Test
    fun switchingTheLanguageShowsTheLetters() {
        val state = KeyboardState().apply { page = Page.MoreSymbols }

        state.language = Language.English

        assertEquals(Page.Letters, state.page)
    }

    @Test
    fun theLanguageASwipeBringsKeepsWhichWayItWentUntilTheNextOne() {
        val state = KeyboardState()

        state.swipingLanguage = false
        // The same language again, as a field restarts: the swipe still waits for its language.
        state.language = state.language
        state.language = Language.English
        assertEquals(false, state.languageSwiped)

        state.language = Language.Russian
        assertEquals(null, state.languageSwiped)
    }

    @Test
    fun theSameLanguageKeepsTheSymbols() {
        val state = KeyboardState().apply { page = Page.Symbols }

        state.language = state.language

        assertEquals(Page.Symbols, state.page)
    }

    @Test
    fun aKeypadStaysThroughSpacesAndLanguageSwitches() {
        val state = KeyboardState().apply { page = Page.Digits }

        state.afterTyping('1')
        state.afterTyping(' ')
        state.language = Language.English

        assertEquals(Page.Digits, state.page)
    }
}
