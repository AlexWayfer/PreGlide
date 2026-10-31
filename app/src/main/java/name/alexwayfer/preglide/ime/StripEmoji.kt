package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.emoji.emojiForWord
import name.alexwayfer.preglide.engine.emoji.wordBeforeEmoji
import name.alexwayfer.preglide.engine.suggest.wordRest

/** The slot of the strip the emoji for a word take, in place of its word: the right one, the least likely word. */
const val EMOJI_SLOT = 2

/** What a slot of the strip shows: a word, or the emoji for the word typed. */
sealed interface SlotContent {
    /** A [word] to pick, which the slot shows as [label]. */
    data class Word(val word: String, val label: String = word) : SlotContent

    data class Emoji(val emoji: List<String>) : SlotContent
}

/**
 * The strip's slots as [stripSlots] places [suggestions] and the [typedWord], with [emoji], when there are any, in
 * the right slot instead of its word; the other words stay where they are, so none moves under the finger. A word
 * of [glued] shows as [shownInStrip] has it.
 */
fun stripSlotContents(
    suggestions: List<String>,
    typedWord: String?,
    emoji: List<String>,
    glued: GluedWords? = null,
): List<SlotContent?> =
    stripSlots(suggestions, typedWord).mapIndexed { slot, word ->
        if (slot == EMOJI_SLOT && emoji.isNotEmpty()) {
            SlotContent.Emoji(emoji)
        } else {
            word?.let { SlotContent.Word(it, shownInStrip(it, glued)) }
        }
    }

/**
 * How the strip shows [word]: what comes after the hyphen when it is one of the [glued] words, as "помаленьку" put on
 * "потихоньку-"; whole otherwise, as a word known whole, "кто-то" for "кто-", is.
 */
fun shownInStrip(word: String, glued: GluedWords?): String =
    if (glued != null && word in glued.words) word.substring(glued.head.length) else word

/**
 * The emoji the strip offers for the word right before the cursor, or before a space after it, by [textBefore] and
 * [textAfter], the text around the cursor (see [emojiForWord]): none with the cursor inside a word, or before the
 * emoji are read.
 */
fun emojiForStrip(textBefore: CharSequence, textAfter: CharSequence, emoji: EmojiState): List<String> {
    val palette = emoji.palette
    val names = emoji.names
    val word = wordBeforeEmoji(textBefore)?.takeIf { wordRest(textAfter).isEmpty() }
    return if (palette != null && names != null && word != null) {
        emojiForWord(word, emoji.history, names, palette)
    } else {
        emptyList()
    }
}

/**
 * Typing [emoji] from the strip after [textBefore], the text before the cursor: it goes a space after the word being
 * typed, or right after the space, and the keyboard's own space follows it when nothing follows in [textAfter], as
 * after a chosen word.
 */
fun emojiEdit(textBefore: CharSequence, emoji: String, textAfter: CharSequence): SuggestionEdit {
    val spaceBefore = if (textBefore.endsWith(' ')) "" else " "
    val spaceAfter = if (textAfter.isEmpty()) " " else ""
    return SuggestionEdit(0, "$spaceBefore$emoji$spaceAfter")
}

/**
 * The word an emoji typed after [textBefore], the text before the cursor, is paired with, to offer it for that word
 * later (see [wordBeforeEmoji]): none in a field of [inputType] with [imeOptions] that the keyboard does not learn
 * from, as a password or an incognito tab (see [learnsFrom]).
 */
fun emojiPairWord(textBefore: CharSequence, inputType: Int, imeOptions: Int): String? =
    if (learnsFrom(inputType, imeOptions)) wordBeforeEmoji(textBefore) else null
