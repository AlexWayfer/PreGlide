package name.alexwayfer.preglide.engine.suggest

/**
 * The most words before a next word that the user's next words are learned after: one, two and three words. Three
 * are counted, not suggested by, for now (see [RANKED_CONTEXT]): [PersonalWords.seen] tells how often they would
 * have come again.
 */
const val LONGEST_CONTEXT = 3

/** The most words before a next word that suggestions read the user's next words after: pairs and triples. */
const val RANKED_CONTEXT = 2

/**
 * How many next words after contexts of each length past one word are kept at most, a few megabytes of memory for
 * each length: most such contexts come once and never again. Next words after a single word are not limited.
 */
const val CONTEXT_LIMIT = 20_000

/** What pruning leaves of [CONTEXT_LIMIT]: nine tenths, so it comes once in many words learned. */
private const val PRUNED_PART = 10

/**
 * Whether the [length] last words before a word had come before it in what the user typed: with any next word,
 * and with this one.
 */
data class ContextSeen(val length: Int, val seen: Boolean, val withWord: Boolean)

/**
 * The words the user typed after one to [LONGEST_CONTEXT] words of the same sentence before them, counted and timed
 * as [Learned] words are. A context is kept by its words' [foldedKey]s, so "Как дела" and "как дела" are one. Past
 * a single word, a length of context keeps at most [limit] next words: past it, those typed the fewest times go,
 * the oldest of them first.
 */
internal class LearnedNext(private val limit: Int = CONTEXT_LIMIT) {
    /** By context length, from one word: each context's next words by their lowercase. */
    private val tables = List(LONGEST_CONTEXT) { HashMap<String, HashMap<String, Learned>>() }

    /** How many next words each table holds, all its contexts together. */
    private val sizes = IntArray(LONGEST_CONTEXT)

    /**
     * Learns that [word] came after [context], the words before it, the nearest last: after its last word, its last
     * two, and so on.
     */
    fun learn(word: String, context: List<String>, time: Long) {
        val key = keyOf(word)
        for (length in lengthsOf(context)) {
            val after = tables[length - 1].getOrPut(contextKey(context, length)) { HashMap() }
            if (key !in after) sizes[length - 1]++
            after[key] = learnedOnce(after[key], word, time)
            if (length > 1 && sizes[length - 1] > limit) prune(length)
        }
    }

    /** Takes back a [learn] of [word] after [context]. */
    fun unlearn(word: String, context: List<String>) {
        val key = keyOf(word)
        for (length in lengthsOf(context)) {
            val table = tables[length - 1]
            val contextKey = contextKey(context, length)
            val after = table[contextKey] ?: continue
            val before = after.size
            after.decrease(key)
            sizes[length - 1] -= before - after.size
            if (after.isEmpty()) table.remove(contextKey)
        }
    }

    /**
     * Forgets [word] as a next word after every context it was last used after at [usedUpTo] or before, and gives
     * what it was after each context's key.
     */
    fun forget(word: String, usedUpTo: Long = Long.MAX_VALUE): List<Pair<String, Learned>> {
        val key = keyOf(word)
        val forgotten = mutableListOf<Pair<String, Learned>>()
        for ((index, table) in tables.withIndex()) {
            for ((context, after) in table) {
                val learned = after[key]?.takeIf { it.lastUsed <= usedUpTo } ?: continue
                forgotten += context to learned
                after.remove(key)
                sizes[index]--
            }
            table.values.removeAll { it.isEmpty() }
        }
        return forgotten
    }

    /** Holds the next words [other] holds, and nothing else. */
    fun replaceWith(other: LearnedNext) {
        for (index in tables.indices) {
            tables[index].clear()
            other.tables[index].forEach { (context, after) -> tables[index][context] = HashMap(after) }
            sizes[index] = other.sizes[index]
        }
    }

    /** Detaches uses affected by an edit, including uses whose preceding words change. */
    fun takeEdited(edit: WordEdit): List<WordUse> = buildList {
        for ((index, table) in tables.withIndex()) {
            for ((context, after) in table) {
                val moveContext = edit.changesContext(context)
                after.entries.removeAll { (_, word) ->
                    (moveContext || edit.affects(word.spelling)).also { move ->
                        if (move) {
                            add(WordUse(context, word))
                            sizes[index]--
                        }
                    }
                }
            }
            table.values.removeAll { it.isEmpty() }
        }
    }

    val entries: Sequence<WordUse> get() = tables.asSequence().flatMap { table ->
        table.asSequence().flatMap { (context, after) -> after.values.asSequence().map { WordUse(context, it) } }
    }

    /** Puts back what [forget] gave, added up with what was learned since after the same contexts. */
    fun restore(forgotten: List<Pair<String, Learned>>) {
        for ((context, next) in forgotten) put(context, next, ::restored)
    }

    /** The words learned after the last [length] words of [context]. */
    fun after(context: List<String>, length: Int): List<Learned> =
        if (length in lengthsOf(context)) tables[length - 1][contextKey(context, length)]?.values.orEmpty().toList()
        else emptyList()

    /** For each length of [context] there is, whether it came before, and before [word]. */
    fun seen(word: String, context: List<String>): List<ContextSeen> = lengthsOf(context).map { length ->
        val after = tables[length - 1][contextKey(context, length)]
        ContextSeen(length, after != null, after?.containsKey(keyOf(word)) == true)
    }

    /** A copy of the next words after each end of [context] alone. */
    fun copyFor(context: List<String>): LearnedNext = LearnedNext(limit).also { copy ->
        for (length in lengthsOf(context)) {
            val key = contextKey(context, length)
            tables[length - 1][key]?.let { after ->
                copy.tables[length - 1][key] = HashMap(after)
                copy.sizes[length - 1] += after.size
            }
        }
    }

    /**
     * Puts [next] after the context of [contextKey], as [write] wrote it, or [merged] with what is there already;
     * one of a longer context is skipped.
     */
    fun put(contextKey: String, next: Learned, merged: (Learned, Learned) -> Learned = { _, new -> new }) {
        val length = contextKey.count { it == ' ' } + 1
        if (length > LONGEST_CONTEXT) return
        val after = tables[length - 1].getOrPut(contextKey) { HashMap() }
        val key = keyOf(next.spelling)
        if (key !in after) sizes[length - 1]++
        after.merge(key, next, merged)
        if (length > 1 && sizes[length - 1] > limit) prune(length)
    }

    /** Drops the next words after contexts of [length] typed the fewest times, the oldest first, down to the limit. */
    private fun prune(length: Int) {
        val table = tables[length - 1]
        val entries = table.flatMap { (context, after) -> after.map { (key, learned) -> Entry(context, key, learned) } }
        val dropped = entries.size - (limit - limit / PRUNED_PART)
        for (entry in entries.sortedWith(compareBy({ it.learned.count }, { it.learned.lastUsed })).take(dropped)) {
            val after = table.getValue(entry.context)
            after.remove(entry.key)
            if (after.isEmpty()) table.remove(entry.context)
        }
        sizes[length - 1] -= dropped
    }

    private class Entry(val context: String, val key: String, val learned: Learned)
}

/** Writes every next word, a line each, with [type] first. */
internal fun LearnedNext.write(out: Appendable, type: String) {
    for ((context, word) in entries) {
        out.append("$type\t$context\t${word.spelling}\t${word.count}\t${word.lastUsed}\n")
    }
}

/**
 * For each length of [context] up to [LONGEST_CONTEXT], whether the user typed it before on this device, and [word]
 * after it; asked before [word] is learned after it.
 */
fun PersonalWords.seen(word: String, context: List<String>): List<ContextSeen> = others.seen(word, context)

/** The lengths of context that [context] has, up to [LONGEST_CONTEXT]. */
private fun lengthsOf(context: List<String>): IntRange = 1..minOf(context.size, LONGEST_CONTEXT)

/** The key of the last [length] words of [context]: their [foldedKey]s, a space between. */
private fun contextKey(context: List<String>, length: Int): String =
    context.takeLast(length).joinToString(" ") { foldedKey(it) }
