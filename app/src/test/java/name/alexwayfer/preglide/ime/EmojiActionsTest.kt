package name.alexwayfer.preglide.ime

import androidx.compose.ui.Alignment
import name.alexwayfer.preglide.engine.emoji.Emoji
import name.alexwayfer.preglide.engine.emoji.EmojiGroup
import name.alexwayfer.preglide.engine.emoji.EmojiHistory
import name.alexwayfer.preglide.engine.emoji.EmojiPalette
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiActionsTest {
    private val keyboard = KeyboardState()
    private val emoji = keyboard.emoji
    private val typed = mutableListOf<String>()
    private val typedAfterWord = mutableListOf<String>()
    private var wordBefore: String? = null
    private var loads = 0
    private var saves = 0
    private var backToKeys = 0
    private val actions = EmojiActions(
        keyboard,
        object : EmojiOutput {
            override fun load() {
                loads++
            }

            override fun save() {
                saves++
            }

            override fun type(emoji: String) {
                typed += emoji
            }

            override fun wordBefore(): String? = wordBefore

            override fun typeAfterWord(emoji: String) {
                typedAfterWord += emoji
            }

            override fun backToKeys() {
                backToKeys++
            }
        },
    ) { emojiKey }
    private var emojiKey = false

    private fun press(action: KeyAction, longPress: Boolean = false) = actions.key(action, longPress)

    @Test
    fun theCommasLongPressOpensThePanelOnEveryPage() {
        for (page in listOf(Page.Letters, Page.Symbols, Page.MoreSymbols)) {
            assertTrue(opensEmoji(KeyAction.Letter(','), page))
        }
        assertFalse(opensEmoji(KeyAction.Letter('.', listOf('…')), Page.Letters))
        assertFalse(opensEmoji(KeyAction.Space, Page.Letters))
        // Its hint stands in the middle of the key's top, a digit's in the corner.
        assertEquals(Alignment.TopCenter, hintAlignment(KeyAction.Letter(','), Page.Letters))
        assertEquals(Alignment.TopEnd, hintAlignment(KeyAction.Letter('й', hint = '1'), Page.Letters))

        keyboard.removing = "привет"
        assertTrue(press(KeyAction.Letter(','), longPress = true))

        assertTrue(emoji.open)
        assertNull(keyboard.removing)
        assertEquals(1, loads)
    }

    @Test
    fun aNumberPadsCommaTypesAndOpensNoPanel() {
        keyboard.page = Page.Digits

        assertFalse(opensEmoji(KeyAction.Letter(','), Page.Digits))
        assertEquals(Alignment.TopEnd, hintAlignment(KeyAction.Letter(','), Page.Digits))
        assertFalse(press(KeyAction.Letter(','), longPress = true))
        assertFalse(emoji.open)
    }

    @Test
    fun theEmojiKeyOpensThePanelByATapOrALongPress() {
        keyboard.removing = "привет"
        assertTrue(press(KeyAction.Emoji))

        assertTrue(emoji.open)
        assertNull(keyboard.removing)
        assertEquals(1, loads)

        actions.close()
        assertTrue(press(KeyAction.Emoji, longPress = true))
        assertTrue(emoji.open)
    }

    @Test
    fun withTheEmojiKeyTheCommasLongPressOpensNoPanel() {
        emojiKey = true

        assertFalse(opensEmoji(KeyAction.Letter(','), Page.Letters, emojiKey = true))
        assertFalse(press(KeyAction.Letter(','), longPress = true))
        assertFalse(emoji.open)
    }

    @Test
    fun theEmojiKeyInTheSearchGoesBackToThePanel() {
        actions.open()
        actions.startSearch()
        press(KeyAction.Letter('к'))

        assertTrue(press(KeyAction.Emoji))

        assertTrue(emoji.open)
        assertFalse(emoji.searching)
        assertEquals(0, backToKeys)
    }

    @Test
    fun otherKeysAndTheCommasTapTypeAsUsual() {
        assertFalse(press(KeyAction.Letter(',')))
        assertFalse(press(KeyAction.Letter('а'), longPress = true))
        assertFalse(emoji.open)
    }

    @Test
    fun thePanelOpensOnTheRecentOnesInTheOrderTheyWere() {
        emoji.history = EmojiHistory(listOf("🐱", "😀"))

        actions.open()

        assertEquals(EmojiTab.Recent, emoji.tab)
        assertEquals(listOf("🐱", "😀"), emoji.shownRecent)
    }

    @Test
    fun everyOpeningStartsOnItsTabAnew() {
        emoji.history = EmojiHistory(listOf("🐱"))
        actions.open()
        // The user pulled the pages over to the animals.
        emoji.tab = EmojiTab.Group(EmojiGroup.Animals)
        actions.close()

        actions.open()

        assertEquals(EmojiTab.Recent, emoji.tab)
        assertEquals(2, emoji.openings)
    }

    @Test
    fun thePanelOpensOnTheSmileysWithNothingRecent() {
        actions.open()

        assertEquals(EmojiTab.Group(EmojiGroup.Smileys), emoji.tab)
    }

    @Test
    fun anEmojiTypedGoesFirstInTheHistoryButNotInTheShownOrder() {
        emoji.history = EmojiHistory(listOf("🐱", "😀"))
        actions.open()

        actions.type(EmojiCell("😀", Emoji("😀")))

        assertEquals(listOf("😀"), typed)
        assertEquals(listOf("😀", "🐱"), emoji.history.recent)
        assertEquals(listOf("🐱", "😀"), emoji.shownRecent)
        assertEquals(1, saves)
    }

    @Test
    fun anEmojiTypedRightAfterAWordIsPairedWithIt() {
        wordBefore = "ой"

        actions.type(EmojiCell("👋🏾", wave))

        assertEquals(listOf("👋"), emoji.history.pairs.forWord("ой"))
    }

    @Test
    fun anEmojiTypedAfterNoWordIsPairedWithNone() {
        actions.type(EmojiCell("😀", Emoji("😀")))

        assertEquals(EmojiHistory(listOf("😀")), emoji.history)
    }

    @Test
    fun anEmojiFromTheStripGoesAfterTheWordAndIsPairedWithIt() {
        emoji.palette = EmojiPalette(mapOf(EmojiGroup.People to listOf(wave)))
        wordBefore = "привет"

        actions.chose("👋🏾")

        assertEquals(listOf("👋🏾"), typedAfterWord)
        assertEquals(emptyList<String>(), typed)
        assertEquals(listOf("👋🏾"), emoji.history.recent)
        assertEquals(listOf("👋"), emoji.history.pairs.forWord("привет"))
        assertEquals(1, saves)
    }

    @Test
    fun aToneChosenIsTypedAndKeptForTheNextTap() {
        actions.type(EmojiCell("👋", wave), tone = 4)

        assertEquals(listOf("👋🏾"), typed)
        assertEquals(4, emoji.history.toneOf(wave))
    }

    @Test
    fun closingShowsTheKeysForTheField() {
        actions.open()
        actions.startSearch()

        actions.close()

        assertFalse(emoji.open)
        assertNull(emoji.query)
        assertEquals(1, backToKeys)
    }

    @Test
    fun theSearchStartsOnTheLettersWithNoCapital() {
        actions.open()
        keyboard.page = Page.Symbols
        keyboard.shift = ShiftState.Once

        actions.startSearch()

        assertEquals("", emoji.query)
        assertTrue(emoji.searching)
        assertEquals(Page.Letters, keyboard.page)
        assertEquals(ShiftState.Off, keyboard.shift)
    }

    @Test
    fun theKeysTypeIntoTheSearch() {
        actions.open()
        actions.startSearch()
        keyboard.shift = ShiftState.Once

        listOf('к', 'о', 'т').forEach { assertTrue(press(KeyAction.Letter(it))) }
        press(KeyAction.Space)
        press(KeyAction.Letter('ы'))
        press(KeyAction.Backspace)

        assertEquals("Кот ", emoji.query)
        assertEquals(ShiftState.Off, keyboard.shift)
        assertEquals(emptyList<String>(), typed)
    }

    @Test
    fun theSearchTakesShiftAndThePagesButNotTheSpacesLongPress() {
        actions.open()
        actions.startSearch()

        press(KeyAction.Shift)
        assertEquals(ShiftState.Once, keyboard.shift)
        press(KeyAction.ShowPage(Page.Symbols))
        assertEquals(Page.Symbols, keyboard.page)
        assertTrue(press(KeyAction.Gap))
        assertFalse(press(KeyAction.Space, longPress = true))
    }

    @Test
    fun aCommasLongPressInTheSearchTypesTheComma() {
        actions.open()
        actions.startSearch()

        assertTrue(press(KeyAction.Letter(','), longPress = true))

        assertEquals(",", emoji.query)
    }

    @Test
    fun enterEndsTheSearchAndThePanel() {
        actions.open()
        actions.startSearch()

        press(KeyAction.Enter)

        assertFalse(emoji.open)
        assertEquals(1, backToKeys)
    }

    @Test
    fun backGoesFromTheSearchToThePanelAndThenToTheKeys() {
        actions.open()
        actions.startSearch()

        assertTrue(actions.backDown())
        assertTrue(actions.backUp())
        assertTrue(emoji.open)
        assertNull(emoji.query)

        assertTrue(actions.backDown())
        assertTrue(actions.backUp())
        assertFalse(emoji.open)

        assertFalse(actions.backDown())
        assertFalse(actions.backUp())
    }

    @Test
    fun endingTheSearchShowsThePanelAgain() {
        actions.open()
        actions.startSearch()

        actions.endSearch()

        assertTrue(emoji.open)
        assertFalse(emoji.searching)
    }

    @Test
    fun backspaceInTheSearchSelectsNothingInTheField() {
        val backspace = QueryBackspace(emoji)
        emoji.query = ""
        assertFalse(backspace.canDelete())

        emoji.query = "к"
        assertTrue(backspace.canDelete())
        assertEquals(0, backspace.startSwipe())
        backspace.select(2)
        backspace.endSwipe(lifted = true)
        assertEquals("к", emoji.query)
    }
}
