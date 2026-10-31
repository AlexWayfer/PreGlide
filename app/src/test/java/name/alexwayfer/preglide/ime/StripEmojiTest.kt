package name.alexwayfer.preglide.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import name.alexwayfer.preglide.engine.emoji.Emoji
import name.alexwayfer.preglide.engine.emoji.EmojiGroup
import name.alexwayfer.preglide.engine.emoji.EmojiHistory
import name.alexwayfer.preglide.engine.emoji.EmojiName
import name.alexwayfer.preglide.engine.emoji.EmojiNames
import name.alexwayfer.preglide.engine.emoji.EmojiPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StripEmojiTest {
    private val emoji = EmojiState().apply {
        palette = EmojiPalette(mapOf(EmojiGroup.Smileys to listOf(Emoji("😁")), EmojiGroup.Food to listOf(Emoji("🍕"))))
        names = EmojiNames(listOf(EmojiName("🍕", "пицца")))
        history = EmojiHistory().typed("😁", after = "ой")
    }

    @Test
    fun theWordBeforeTheCursorGetsItsEmojiWhileTypedAndAfterASpace() {
        assertEquals(listOf("🍕"), emojiForStrip("Ну пицца", "", emoji))
        assertEquals(listOf("🍕"), emojiForStrip("Ну пицца ", "", emoji))
        assertEquals(listOf("😁"), emojiForStrip("Ну ой", "", emoji))
    }

    @Test
    fun aWordTheCursorIsInsideGetsNone() {
        assertEquals(emptyList<String>(), emojiForStrip("Ну пиц", "ца", emoji))
    }

    @Test
    fun nothingIsOfferedUntilTheEmojiAreRead() {
        assertEquals(emptyList<String>(), emojiForStrip("Ну пицца", "", EmojiState()))
    }

    @Test
    fun anEmojiGoesASpaceAfterTheWordBeingTypedWithTheKeyboardsSpaceAfterIt() {
        assertEquals(SuggestionEdit(0, " 😁 "), emojiEdit("Ну ой", "😁", ""))
        assertEquals(SuggestionEdit(0, "😁 "), emojiEdit("Ну ой ", "😁", ""))
    }

    @Test
    fun anEmojiBeforeTextTakesNoSpaceAfterIt() {
        assertEquals(SuggestionEdit(0, " 😁"), emojiEdit("Ну ой", "😁", " и всё"))
    }

    @Test
    fun anEmojiIsPairedWithTheWordBeforeItWhereTheFieldIsLearnedFrom() {
        val text = InputType.TYPE_CLASS_TEXT

        assertEquals("ой", emojiPairWord("Ну ой ", text, 0))
        assertNull(emojiPairWord("Ну ой ", text or InputType.TYPE_TEXT_VARIATION_PASSWORD, 0))
        assertNull(emojiPairWord("Ну ой ", text, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        assertNull(emojiPairWord("Ну ой. ", text, 0))
    }

    @Test
    fun theEmojiTakeTheRightSlotInPlaceOfItsWord() {
        val words = listOf("привет", "пока", "как")

        assertEquals(
            listOf(SlotContent.Word("пока"), SlotContent.Word("привет"), SlotContent.Word("как")),
            stripSlotContents(words, null, emptyList()),
        )
        assertEquals(
            listOf(SlotContent.Word("пока"), SlotContent.Word("привет"), SlotContent.Emoji(listOf("😁", "🙈"))),
            stripSlotContents(words, null, listOf("😁", "🙈")),
        )
        assertEquals(
            listOf(null, SlotContent.Word("ой"), SlotContent.Emoji(listOf("😁"))),
            stripSlotContents(emptyList(), "ой", listOf("😁")),
        )
    }

    @Test
    fun aGluedWordShowsWhatComesAfterTheHyphenAndAWholeOneShowsWhole() {
        val glued = GluedWords("Кто-", setOf("Кто-тот"))
        assertEquals(
            listOf(
                SlotContent.Word("Кто-тот", "тот"),
                SlotContent.Word("Кто-то"),
                SlotContent.Word("ктото"),
            ),
            stripSlotContents(listOf("Кто-то", "Кто-тот", "ктото"), null, emptyList(), glued),
        )
        assertEquals("потихоньку-помаленьку", shownInStrip("потихоньку-помаленьку", null))
    }
}
