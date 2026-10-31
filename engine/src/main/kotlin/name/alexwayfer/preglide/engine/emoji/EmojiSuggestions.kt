package name.alexwayfer.preglide.engine.emoji

import name.alexwayfer.preglide.engine.suggest.currentWord

/** How many emoji the strip offers for a word, in its right slot. */
const val STRIP_EMOJI_LIMIT = 3

/**
 * The word an emoji typed at the end of [textBefore], the text before the cursor, goes after: the word right before
 * the cursor, or before a single space. Null after a mark, an emoji, a number or more space: there the emoji goes
 * after no word.
 */
fun wordBeforeEmoji(textBefore: CharSequence): String? {
    val text = if (textBefore.endsWith(' ')) textBefore.dropLast(1) else textBefore
    return currentWord(text).takeIf { it.isNotEmpty() }
}

/**
 * The emoji the strip offers for [word], at most [limit]: the ones the user typed after it, by [history], then the
 * ones it is the whole name of, by [names]; each once, in the tone chosen for it last, and only what the phone draws,
 * as [palette] holds.
 */
fun emojiForWord(
    word: String,
    history: EmojiHistory,
    names: EmojiNames,
    palette: EmojiPalette,
    limit: Int = STRIP_EMOJI_LIMIT,
): List<String> =
    (history.pairs.forWord(word) + names.named(word))
        .mapNotNull(palette::emojiOf)
        .distinct()
        .take(limit)
        .map(history::shown)
