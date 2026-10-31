package name.alexwayfer.preglide.engine.emoji

/** How many words the pairs keep emoji for: past it, the word used longest ago is forgotten. */
const val PAIR_WORDS_LIMIT = 2000

/** How many emoji a word keeps: twice what the strip shows, so a new one can rise past an old one. */
const val PAIR_EMOJI_LIMIT = 6

/**
 * The emoji the user typed right after a word, to offer them for it again: by the word folded as [pairKey] folds it,
 * the words used longest ago first, and each word's emoji with how many times each was typed after it, the latest
 * first.
 */
data class EmojiPairs(val words: Map<String, Map<String, Int>> = emptyMap()) {
    /** The emoji typed after [word], the one typed most first, and of two typed as often, the latest. */
    fun forWord(word: String): List<String> =
        words[pairKey(word)].orEmpty().entries.sortedByDescending { it.value }.map { it.key }

    /** After the user typed [emoji], in its base form, right after [word]. */
    fun learned(word: String, emoji: String): EmojiPairs = added(pairKey(word), emoji, 1)

    /**
     * These pairs, of what was typed while [earlier] could not be read yet, as before the first unlock, on top of
     * them: the counts add up, and these words and emoji are the later ones.
     */
    fun after(earlier: EmojiPairs): EmojiPairs = words.entries.fold(earlier) { pairs, (word, emoji) ->
        emoji.entries.reversed().fold(pairs) { added, (each, count) -> added.added(word, each, count) }
    }

    private fun added(key: String, emoji: String, count: Int): EmojiPairs {
        val old = words[key].orEmpty()
        val counts = linkedMapOf(emoji to (old[emoji] ?: 0) + count)
        counts.putAll(old - emoji)
        // The least typed goes, and of those, the one typed longest ago, the last; never the one just typed, which
        // would never get in past the ones typed more.
        if (counts.size > PAIR_EMOJI_LIMIT) counts.remove(counts.entries.drop(1).reversed().minBy { it.value }.key)
        val all = LinkedHashMap(words - key)
        all[key] = counts
        return EmojiPairs(all.entries.drop((all.size - PAIR_WORDS_LIMIT).coerceAtLeast(0)).associate { it.toPair() })
    }
}

/** [word] as the pairs keep it: in lowercase, ё as е, so "Всё" and "все" are one word. */
fun pairKey(word: String): String = word.lowercase().replace('ё', 'е')
