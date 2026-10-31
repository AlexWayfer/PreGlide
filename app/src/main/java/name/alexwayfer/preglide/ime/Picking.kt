package name.alexwayfer.preglide.ime

import name.alexwayfer.preglide.engine.suggest.currentWord
import name.alexwayfer.preglide.engine.suggest.wordRest

/** How much text after the cursor to read for the rest of the word there: more than the longest dictionary word. */
const val WORD_AFTER_CURSOR = 64

/**
 * What picking a suggestion does to the text: deletes [deleteBefore] chars before the cursor and [deleteAfter] after
 * it, and types [text].
 */
data class SuggestionEdit(val deleteBefore: Int, val text: String, val deleteAfter: Int = 0)

/**
 * Picking [suggestion] between [textBefore] and [textAfter], the text around the cursor, replaces the word the
 * cursor is in, both its halves, and adds a space for the next word when nothing follows the word: text that does,
 * as when the user moved the cursor back onto an earlier word, has its space or mark after it already. A word of its
 * own the user typed right before, [wordAhead], stays (see [replacedRest]). Right after a mark a word goes a space
 * after, a space goes before it too (see [choiceNeedsSpace]).
 */
fun pick(
    textBefore: CharSequence,
    suggestion: String,
    textAfter: CharSequence = "",
    wordAhead: String? = null,
): SuggestionEdit {
    val spaceBefore = if (choiceNeedsSpace(textBefore)) " " else ""
    val rest = replacedRest(suggestion, textAfter, wordAhead)
    val spaceAfter = if (textAfter.length == rest.length) " " else ""
    return SuggestionEdit(currentWord(textBefore).length, "$spaceBefore$suggestion$spaceAfter", rest.length)
}

/**
 * The part of [textAfter], the text after the cursor, that picking [suggestion] replaces: the rest of the word the
 * cursor is in (see [wordRest]). When that rest is [wordAhead], a word the cursor was put right before, the user
 * typed a word of their own in front of it, as "htt" before "alexwayfer.name" on the way to "https", and it stays;
 * unless [suggestion] ends with it, as "привет" for "п" typed before "ривет".
 */
internal fun replacedRest(suggestion: String, textAfter: CharSequence, wordAhead: String?): String {
    val rest = wordRest(textAfter)
    val ownWord = rest == wordAhead && !suggestion.endsWith(rest, ignoreCase = true)
    return if (ownWord) "" else rest
}

/**
 * The word the user put the cursor right before in [field], if any: a word typed in front of it leaves it in place
 * (see [pick]).
 */
class WordAhead(private val field: SuggestionField) {
    var word: String? = null
        private set

    /**
     * The user put the cursor where it is now, or the field started with it there: a word right after it, with none
     * before it, is a word of its own.
     */
    fun placed() {
        val before = field.textBeforeCursor(TEXT_BEFORE_CURSOR) ?: ""
        val rest = wordRest(field.textAfterCursor(WORD_AFTER_CURSOR) ?: "")
        word = rest.takeIf { it.isNotEmpty() && currentWord(before).isEmpty() }
    }

    fun forget() {
        word = null
    }
}
