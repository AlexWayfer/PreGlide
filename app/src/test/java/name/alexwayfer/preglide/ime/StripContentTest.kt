package name.alexwayfer.preglide.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StripContentTest {
    private val state = KeyboardState()

    @Test
    fun theEmojiPanelAndItsSearchHaveTheirOwnRows() {
        state.suggestions = listOf("привет")
        state.emoji.open = true
        assertEquals(StripContent.Emoji, stripContent(state))
        assertEquals(KeysPanel.Emoji, shownPanel(state))

        state.emoji.query = ""
        assertEquals(StripContent.EmojiSearch, stripContent(state))
        // The search types on the keys.
        assertEquals(KeysPanel.Keys, shownPanel(state))
    }

    @Test
    fun emojiForTheWordAloneShowInTheWordsRow() {
        state.stripEmoji = listOf("😁")

        assertEquals(StripContent.Suggestions, stripContent(state))
    }

    @Test
    fun theQuestionToRemoveAWordComesOverTheEmoji() {
        state.emoji.open = true
        state.removing = "тадалафил"

        assertEquals(StripContent.Removing("тадалафил"), stripContent(state))
    }

    @Test
    fun theKeysShowWithNoPanelOpen() {
        assertEquals(KeysPanel.Keys, shownPanel(state))

        state.clipboardOpen = true
        assertEquals(KeysPanel.Clipboard, shownPanel(state))
    }

    @Test
    fun theStripShowsTheSuggestions() {
        state.suggestions = listOf("привет")
        assertEquals(StripContent.Suggestions, stripContent(state))

        state.suggestions = emptyList()
        state.typedWord = "кринж"
        assertEquals(StripContent.Suggestions, stripContent(state))
    }

    @Test
    fun aStripWithNoWordHasTheClipboardsButton() {
        assertEquals(StripContent.Empty, stripContent(state))
    }

    @Test
    fun theClipboardsPanelHasItsOwnRow() {
        state.clip = OfferedClip(Clip.Text("текст", sensitive = false, copiedAt = 0))
        state.suggestions = listOf("привет")
        state.clipboardOpen = true
        assertEquals(StripContent.Clipboard, stripContent(state))

        state.clearing = true
        assertEquals(StripContent.Clearing, stripContent(state))
    }

    @Test
    fun aClipComesAndGoesInTheRowOfTheClipboardsButton() {
        val clip = StripContent.Clip(OfferedClip(Clip.Text("текст", sensitive = false, copiedAt = 0)))

        assertEquals(stripRow(StripContent.Empty), stripRow(clip))
        assertEquals(StripContent.Suggestions::class, stripRow(StripContent.Suggestions))
    }

    @Test
    fun aLoadingDictionaryIsShownInAnEmptyStrip() {
        state.loading = true
        assertEquals(StripContent.Loading, stripContent(state))

        // Addresses or words already there are worth more than the word "loading".
        state.suggestions = listOf("привет")
        assertEquals(StripContent.Suggestions, stripContent(state))
    }

    @Test
    fun theQuestionToRemoveAWordComesOverAll() {
        state.loading = true
        state.removing = "тадалафил"

        assertEquals(StripContent.Removing("тадалафил"), stripContent(state))
    }

    @Test
    fun whatWasCopiedFillsAStripWithNoWord() {
        val copied = OfferedClip(Clip.Text("https://example.com", sensitive = false, copiedAt = 0))
        state.clip = copied
        // Pasting is worth more than the word "loading".
        state.loading = true
        assertEquals(StripContent.Clip(copied), stripContent(state))

        state.suggestions = listOf("привет")
        assertEquals(StripContent.Suggestions, stripContent(state))

        state.suggestions = emptyList()
        state.loading = false
        state.typedWord = "кринж"
        assertEquals(StripContent.Suggestions, stripContent(state))
    }

    @Test
    fun theQuestionToRemoveAWordComesOverWhatWasCopied() {
        state.clip = OfferedClip(Clip.Text("текст", sensitive = false, copiedAt = 0))
        state.removing = "тадалафил"

        assertEquals(StripContent.Removing("тадалафил"), stripContent(state))
    }

    @Test
    fun aBlankTextCopiedIsNotOffered() {
        state.clip = OfferedClip(Clip.Text(" \n", sensitive = false, copiedAt = 0))

        assertEquals(StripContent.Empty, stripContent(state))
    }

    @Test
    fun pickingTheTypedWordKeepsIt() {
        state.typedWord = "кринж"

        assertTrue(state.keepsTyped("кринж"))
        assertFalse(state.keepsTyped("кринжово"))
    }
}
