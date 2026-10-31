package name.alexwayfer.preglide.ime

import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.layout.Language
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiKeyTest {
    private fun look(emojiKey: Float) = KeyLook(
        hints = true,
        backgrounds = 1f,
        dividers = 0f,
        cornerRadius = 8.dp,
        height = 50.dp,
        stripHeight = 40.dp,
        emojiKey = emojiKey,
    )

    @Test
    fun theSettingTurnsTheEmojiKeyOnInTheLook() {
        val look = { emojiKey: Boolean -> keyLook(defaultLookSettings.copy(emojiKey = emojiKey)) }

        assertEquals(1f, look(true).emojiKey)
        assertEquals(0f, look(false).emojiKey)
    }

    @Test
    fun theEmojiKeyShowsLeftOfTheCommaOnTheLettersAndTheSymbols() {
        for (page in listOf(Page.Letters, Page.Symbols, Page.MoreSymbols)) {
            val layout = KeyboardLayout.of(Language.Russian, page)
            val rows = emojiKeyRows(layout, look(1f))

            assertEquals(page.name, layout.rows.dropLast(1), rows.dropLast(1))
            assertEquals(page.name, KeyAction.Emoji, rows.last()[1].action)
            assertEquals(page.name, KeyAction.Letter(','), rows.last()[2].action)
        }
    }

    @Test
    fun aKeypadGetsNoEmojiKey() {
        for (page in listOf(Page.Digits, Page.Phone)) {
            val layout = KeyboardLayout.of(Language.English, page)

            assertEquals(page.name, layout.rows, emojiKeyRows(layout, look(1f)))
        }
    }

    @Test
    fun theEmojiKeyGrowsAsItComesAndIsGoneWithNothingOfIt() {
        val layout = KeyboardLayout.of(Language.English)

        assertEquals(0.4f, emojiKeyRows(layout, look(0.4f)).last()[1].width)
        assertEquals(layout.rows, emojiKeyRows(layout, look(0f)))
    }

    @Test
    fun theCommasSmileFadesAsTheEmojiKeyComes() {
        assertEquals(1f, emojiHintAlpha(look(0f)))
        assertEquals(0.75f, emojiHintAlpha(look(0.25f)))
        assertEquals(0f, emojiHintAlpha(look(1f)))
    }
}
