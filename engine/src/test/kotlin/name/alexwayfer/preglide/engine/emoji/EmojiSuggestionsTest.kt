package name.alexwayfer.preglide.engine.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiSuggestionsTest {
    private val wave = Emoji("👋", listOf("👋🏻", "👋🏼", "👋🏽", "👋🏾", "👋🏿"))
    private val palette = EmojiPalette(
        mapOf(
            EmojiGroup.Smileys to listOf(Emoji("😀"), Emoji("😁"), Emoji("😅"), Emoji("😋"), Emoji("🙈")),
            EmojiGroup.People to listOf(wave),
            EmojiGroup.Food to listOf(Emoji("🍕")),
        ),
    )
    private val names = EmojiNames(listOf(EmojiName("🍕", "пицца"), EmojiName("👋", "машет рукой", listOf("привет"))))

    private fun offered(word: String, history: EmojiHistory = EmojiHistory()) =
        emojiForWord(word, history, names, palette)

    @Test
    fun anEmojiGoesAfterTheWordRightBeforeTheCursorOrBeforeOneSpace() {
        assertEquals("ой", wordBeforeEmoji("Ну ой"))
        assertEquals("ой", wordBeforeEmoji("Ну ой "))
        assertEquals("ой-ой", wordBeforeEmoji("ой-ой"))
    }

    @Test
    fun noWordIsBeforeTheCursorAfterAMarkAnEmojiOrMoreSpace() {
        assertNull(wordBeforeEmoji(""))
        assertNull(wordBeforeEmoji(" "))
        assertNull(wordBeforeEmoji("ой  "))
        assertNull(wordBeforeEmoji("ой."))
        assertNull(wordBeforeEmoji("ой. "))
        assertNull(wordBeforeEmoji("ой 😁"))
        assertNull(wordBeforeEmoji("ой 😁 "))
        assertNull(wordBeforeEmoji("2024 "))
    }

    @Test
    fun theUsersOwnEmojiForTheWordComeFirst() {
        val history = EmojiHistory().typed("😋", after = "пицца")

        assertEquals(listOf("😋", "🍕"), offered("Пицца", history))
    }

    @Test
    fun aWordThatNamesAnEmojiGetsItWithNothingTypedAfterIt() {
        assertEquals(listOf("🍕"), offered("пицца"))
        // "привет" is only a keyword of 👋: no suggestion for it.
        assertEquals(emptyList<String>(), offered("привет"))
    }

    @Test
    fun anEmojiShowsInTheToneChosenForItLast() {
        val history = EmojiHistory().typed("👋🏽", wave, tone = 3, after = "привет")

        assertEquals(listOf("👋🏽"), offered("привет", history))
    }

    @Test
    fun anEmojiThePhoneCannotDrawIsLeftOut() {
        val history = EmojiHistory().typed("🫩", after = "ой").typed("😁", after = "ой")

        assertEquals(listOf("😁"), offered("ой", history))
    }

    @Test
    fun anEmojiTypedAndNamedComesOnce() {
        val history = EmojiHistory().typed("🍕", after = "пицца")

        assertEquals(listOf("🍕"), offered("пицца", history))
    }

    @Test
    fun theStripGetsAtMostItsLimit() {
        val history = listOf("😀", "😁", "😅", "🙈").fold(EmojiHistory()) { history, emoji ->
            history.typed(emoji, after = "ой")
        }

        assertEquals(listOf("🙈", "😅", "😁"), offered("ой", history))
        assertEquals(STRIP_EMOJI_LIMIT, offered("ой", history).size)
    }
}
