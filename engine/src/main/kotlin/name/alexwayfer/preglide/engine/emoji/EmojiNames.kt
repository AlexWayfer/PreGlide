package name.alexwayfer.preglide.engine.emoji

/** What an [emoji], in its base form, is called in a language: its [name], as a screen reader says it, and keywords. */
data class EmojiName(val emoji: String, val name: String, val keywords: List<String> = emptyList())

/** How many emoji a search finds at most. */
const val SEARCH_LIMIT = 60

/**
 * The names of the emoji, in one language or several, to name an emoji and to find it by a word: [names] in the
 * order to offer them in, as the palette's, the language to name them in first.
 */
class EmojiNames(private val names: List<EmojiName>) {
    private val byEmoji: Map<String, EmojiName> = names.reversed().associateBy { it.emoji }
    private val words = names.map { name -> Words(wordsOf(name.name), name.keywords.flatMap(::wordsOf)) }
    private val byWholeName: Map<String, List<String>> =
        names.groupBy({ wordsOf(it.name).joinToString(" ") }, { it.emoji }).mapValues { it.value.distinct() }

    /** What [emoji] is called, by the first of the names that has it; null for an emoji none names. */
    fun nameOf(emoji: String): String? = byEmoji[emoji]?.name

    /**
     * The emoji whose whole name is [word], case and ё aside, as 🍕's is "пицца". A keyword or a word of a longer
     * name would match too loosely to offer the emoji unasked: "ой" is a keyword of the jellyfish.
     */
    fun named(word: String): List<String> = byWholeName[wordsOf(word).joinToString(" ")].orEmpty()

    /**
     * The emoji whose names hold a word starting with each word of [query], case and ё aside, the best match first:
     * a word of its name before one of its keywords, a whole word before its start; at most [limit] of them, and
     * none for a query with no word.
     */
    fun search(query: String, limit: Int = SEARCH_LIMIT): List<String> {
        val asked = wordsOf(query)
        if (asked.isEmpty()) return emptyList()
        val best = mutableMapOf<String, Int>()
        names.forEachIndexed { index, name ->
            val cost = words[index].match(asked) ?: return@forEachIndexed
            best.merge(name.emoji, cost, ::minOf)
        }
        // A stable sort: emoji of the same cost keep the order of the names.
        return best.entries.sortedBy { it.value }.take(limit).map { it.key }
    }
}

/** How a query's word matched a name's: the lower, the better. */
private enum class Match { NameWord, NameStart, KeywordWord, KeywordStart }

/** The words of a name and of its keywords, folded as [wordsOf] folds them. */
private class Words(val name: List<String>, val keywords: List<String>) {
    /** The sum of how well each of [asked] matches, or null when one of them matches none. */
    fun match(asked: List<String>): Int? {
        var cost = 0
        for (word in asked) cost += (matchOf(word) ?: return null).ordinal
        return cost
    }

    private fun matchOf(word: String): Match? = when {
        word in name -> Match.NameWord
        name.any { it.startsWith(word) } -> Match.NameStart
        word in keywords -> Match.KeywordWord
        keywords.any { it.startsWith(word) } -> Match.KeywordStart
        else -> null
    }
}

/** The words of [text] in lowercase, ё as е, so "Ёлка" and "елка" match: cut at anything but a letter or a digit. */
fun wordsOf(text: String): List<String> =
    text.lowercase().replace('ё', 'е').split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

/**
 * [names] as the app's assets hold them: a line each, the emoji, its name and its keywords apart by tabs, `|`
 * between the keywords.
 */
fun emojiNamesText(names: List<EmojiName>): String = names.joinToString("") { name ->
    "${name.emoji}\t${name.name}\t${name.keywords.joinToString(KEYWORD_SEPARATOR)}\n"
}

/** The names of [lines] of [emojiNamesText]; a line without a name is skipped. */
fun readEmojiNames(lines: Sequence<String>): List<EmojiName> = lines.mapNotNull { line ->
    val fields = line.split('\t')
    val name = fields.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    val keywords = fields.getOrNull(2).orEmpty().split(KEYWORD_SEPARATOR).filter { it.isNotEmpty() }
    EmojiName(fields[0], name, keywords)
}.toList()

private const val KEYWORD_SEPARATOR = "|"
