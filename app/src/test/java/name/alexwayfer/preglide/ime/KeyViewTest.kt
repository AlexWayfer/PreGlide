package name.alexwayfer.preglide.ime

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import name.alexwayfer.preglide.R
import name.alexwayfer.preglide.engine.layout.Key
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyViewTest {
    @Test
    fun shiftCapitalizesLettersOnTheLettersPage() {
        val state = KeyboardState().apply { shift = ShiftState.Once }

        assertEquals("Ж", label(KeyAction.Letter('ж'), state))
        assertEquals('Ё', state.typed('ё'))
    }

    @Test
    fun shiftLeavesSymbolsAlone() {
        val state = KeyboardState().apply {
            shift = ShiftState.Locked
            page = Page.MoreSymbols
        }

        assertEquals("π", label(KeyAction.Letter('π'), state))
    }

    @Test
    fun spaceShowsTheLanguageInItself() {
        assertEquals("Русский", label(KeyAction.Space, KeyboardState()))
        assertEquals("English", label(KeyAction.Space, KeyboardState().apply { language = Language.English }))
    }

    @Test
    fun lettersKeyShowsTheLanguagesAlphabet() {
        val toLetters = KeyAction.ShowPage(Page.Letters)

        assertEquals("АБВ", label(toLetters, KeyboardState()))
        assertEquals("ABC", label(toLetters, KeyboardState().apply { language = Language.English }))
    }

    @Test
    fun symbolKeysShowWhatTheyOpen() {
        assertEquals("?123", label(KeyAction.ShowPage(Page.Symbols), KeyboardState()))
        assertEquals("=\\<", label(KeyAction.ShowPage(Page.MoreSymbols), KeyboardState()))
    }

    @Test
    fun everyPageKeyHasItsOwnDescription() {
        val descriptions = Page.entries.filterNot { it.keypad }.map { descriptionRes(KeyAction.ShowPage(it)) }

        assertEquals(listOf(R.string.key_letters, R.string.key_symbols, R.string.key_more_symbols), descriptions)
    }

    @Test
    fun noKeyLeadsToAKeypadButOneWouldStillReadAsDigits() {
        for (page in listOf(Page.Digits, Page.Phone)) {
            assertNull(descriptionRes(KeyAction.ShowPage(page)))
            assertEquals("123", label(KeyAction.ShowPage(page), KeyboardState()))
        }
    }

    @Test
    fun aKeypadsNarrowSpaceBarShowsItsSignInsteadOfTheLanguage() {
        for (page in listOf(Page.Digits, Page.Phone)) {
            assertEquals(R.drawable.ic_key_space, icon(KeyAction.Space, KeyboardState().apply { this.page = page }))
        }
    }

    @Test
    fun aKeypadFadingOutKeepsItsSpaceSignWhileTheLettersShow() {
        val letters = KeyboardState()
        assertEquals(R.drawable.ic_key_space, icon(KeyAction.Space, letters, Page.Digits))
        assertNull(icon(KeyAction.Space, KeyboardState().apply { page = Page.Digits }, Page.Letters))
    }

    @Test
    fun gapIsEmpty() {
        assertEquals("", label(KeyAction.Gap, KeyboardState()))
        assertNull(descriptionRes(KeyAction.Gap))
        assertNull(icon(KeyAction.Gap, KeyboardState()))
    }

    @Test
    fun aKeysOptionsHintStandsInTheCornerAsADigit() {
        val softSign = KeyAction.Letter('ь', listOf('ъ'), optionsHint = "ъ")

        assertEquals(Alignment.TopEnd, hintAlignment(softSign, Page.Letters))
    }

    @Test
    fun theEmojiKeyIsTheCommasSmileAsAnIcon() {
        assertEquals(R.drawable.ic_mood, icon(KeyAction.Emoji, KeyboardState()))
        assertEquals("", label(KeyAction.Emoji, KeyboardState()))
        assertEquals(R.string.key_emoji, descriptionRes(KeyAction.Emoji))
    }

    @Test
    fun backspaceAndEnterAreIcons() {
        assertEquals(R.drawable.ic_key_backspace, icon(KeyAction.Backspace, KeyboardState()))
        assertEquals(R.drawable.ic_key_enter, icon(KeyAction.Enter, KeyboardState()))
        assertNull(icon(KeyAction.Space, KeyboardState()))
    }

    @Test
    fun shiftCyclesFromLowercaseToOneCapitalToCapsLock() {
        assertEquals(ShiftState.Once, ShiftState.Off.next())
        assertEquals(ShiftState.Locked, ShiftState.Once.next())
        assertEquals(ShiftState.Off, ShiftState.Locked.next())
    }

    @Test
    fun shiftIconFollowsItsState() {
        val state = KeyboardState()
        val icons = ShiftState.entries.map { shift ->
            state.shift = shift
            icon(KeyAction.Shift, state)
        }

        assertEquals(
            listOf(R.drawable.ic_key_shift, R.drawable.ic_key_shift_once, R.drawable.ic_key_shift_locked),
            icons,
        )
    }

    @Test
    fun keysWithoutATextLabelHaveADescription() {
        for (action in listOf(KeyAction.Shift, KeyAction.Backspace, KeyAction.Enter)) {
            assertEquals("", label(action, KeyboardState()))
            assertNotNull("$action has no description", descriptionRes(action))
        }
    }

    @Test
    fun spaceIsDescribedAsSpaceNotByTheLanguageOnIt() {
        assertEquals(R.string.key_space, descriptionRes(KeyAction.Space))
    }

    @Test
    fun lettersNeedNoDescription() {
        assertNull(descriptionRes(KeyAction.Letter('а')))
    }

    @Test
    fun keysThatStandOutAreBrighterThanFunctionKeys() {
        val colors = lightColorScheme()

        assertEquals(colors.surfaceBright, keyBackground(Key(KeyAction.Letter('а')), pressed = false, colors))
        assertEquals(colors.surfaceBright, keyBackground(Key(KeyAction.Space), pressed = false, colors))
        assertEquals(colors.surfaceContainerHighest, keyBackground(Key(KeyAction.Shift), pressed = false, colors))
        val comma = Key(KeyAction.Letter(','), standsOut = false)
        assertEquals(colors.surfaceContainerHighest, keyBackground(comma, pressed = false, colors))
    }

    @Test
    fun pressedKeyLightsUp() {
        val colors = lightColorScheme()
        val pressed = pressedColors(colors).background

        assertEquals(pressed, keyBackground(Key(KeyAction.Letter('а')), pressed = true, colors))
        assertEquals(pressed, keyBackground(Key(KeyAction.Enter), pressed = true, colors))
        assertEquals(pressed, keyBackground(Key(KeyAction.Letter('.'), standsOut = false), pressed = true, colors))
    }

    @Test
    fun spaceLabelIsSmallerThanLetters() {
        assertTrue(labelSize(KeyAction.Space).value < labelSize(KeyAction.Letter('а')).value)
        assertTrue(labelSize(KeyAction.Space).value < labelSize(KeyAction.ShowPage(Page.Symbols)).value)
    }
}
