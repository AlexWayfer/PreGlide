package name.alexwayfer.preglide.engine.emoji

/** How many emoji the recent tab keeps: about four rows of a phone's panel. */
const val RECENT_LIMIT = 36

/**
 * What the user did with emoji, to offer it again: the [recent] emoji, in the forms typed, the latest first, the
 * skin tone chosen last for each emoji that takes one, by its base form ([tones]), and the emoji typed right after a
 * word ([pairs]).
 */
data class EmojiHistory(
    val recent: List<String> = emptyList(),
    val tones: Map<String, Int> = emptyMap(),
    val pairs: EmojiPairs = EmojiPairs(),
) {
    /** The tone a tap types [emoji] in: the one chosen for it last, or its base. */
    fun toneOf(emoji: Emoji): Int = tones[emoji.base]?.takeIf { it in 0..emoji.tones.size } ?: 0

    /** The form a tap types [emoji] in, the tone chosen for it last. */
    fun shown(emoji: Emoji): String = emoji.toned(toneOf(emoji))

    /**
     * After the user typed [form]: it goes first among the recent ones. With [tone], chosen for [emoji] from a long
     * press, the next tap on that emoji types it in that tone. Typed right [after] a word, it is offered for that word
     * later, in its base form, so a tone chosen anew shows in the offer too.
     */
    fun typed(form: String, emoji: Emoji? = null, tone: Int? = null, after: String? = null): EmojiHistory {
        val recent = (listOf(form) + (recent - form)).take(RECENT_LIMIT)
        val tones = if (emoji != null && tone != null) tones + (emoji.base to tone) else tones
        val pairs = if (after != null) pairs.learned(after, emoji?.base ?: form) else pairs
        return EmojiHistory(recent, tones, pairs)
    }

    /**
     * This history, of what was typed while [earlier] could not be read yet, as before the first unlock, on top of
     * it: its recent emoji before the earlier ones, its tones over theirs, its pairs added to theirs.
     */
    fun after(earlier: EmojiHistory): EmojiHistory = EmojiHistory(
        (recent + (earlier.recent - recent.toSet())).take(RECENT_LIMIT),
        earlier.tones + tones,
        pairs.after(earlier.pairs),
    )

    /**
     * The history as its file holds it: a line per recent emoji, the latest first, then a line per tone, then a line
     * per pair, the words used longest ago first and each word's emoji the latest first.
     */
    fun text(): String = buildString {
        for (form in recent) append(RECENT).append('\t').append(form).append('\n')
        for ((base, tone) in tones) append(TONE).append('\t').append(base).append('\t').append(tone).append('\n')
        for ((word, counts) in pairs.words) {
            for ((emoji, count) in counts) {
                append(PAIR).append('\t').append(word).append('\t').append(emoji).append('\t').append(count)
                append('\n')
            }
        }
    }

    companion object {
        /** The history of [lines] of [text]; a line it cannot read is skipped. */
        fun read(lines: Sequence<String>): EmojiHistory {
            val recent = mutableListOf<String>()
            val tones = mutableMapOf<String, Int>()
            val pairs = linkedMapOf<String, MutableMap<String, Int>>()
            for (line in lines) {
                val fields = line.split('\t')
                when (fields.first()) {
                    RECENT -> fields.getOrNull(1)?.let(recent::add)
                    TONE -> fields.getOrNull(2)?.toIntOrNull()?.let { tones[fields[1]] = it }
                    PAIR -> fields.getOrNull(PAIR_COUNT)?.toIntOrNull()?.let {
                        pairs.getOrPut(fields[1]) { linkedMapOf() }[fields[2]] = it
                    }
                }
            }
            return EmojiHistory(recent.distinct().take(RECENT_LIMIT), tones, EmojiPairs(pairs))
        }
    }
}

private const val RECENT = "recent"
private const val TONE = "tone"
private const val PAIR = "pair"

/** The field of a pair's line with the count: after its mark, word and emoji. */
private const val PAIR_COUNT = 3
