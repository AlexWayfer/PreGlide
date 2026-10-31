package name.alexwayfer.preglide.engine.suggest

/** The fewest times the user must have begun a field with a word before an empty field offers it. */
const val FIELD_START_USES = 3

/**
 * The least share of all the fields the user began that a word must have begun for an empty field to offer it.
 * The prediction model alone guesses a field's first word among its 3 likeliest only about one time in eight, so
 * an empty field offers only the user's own frequent openers, as a greeting, and stays clear otherwise.
 */
const val FIELD_START_SHARE = 0.1

/**
 * The words the user began a field with, the first word of all its text, as a message starts, counted and timed as
 * [Learned] words are; [changed] hears of every change. A word at the start of a sentence further on is not one:
 * what came before it tells more about it.
 */
class FieldStarts internal constructor(private val changed: () -> Unit) {
    private val words = HashMap<String, Learned>()

    /** Learns that the user began a field with [word]. */
    fun learn(word: String, time: Long) {
        val key = keyOf(word)
        words[key] = learnedOnce(words[key], word, time)
        changed()
    }

    /** Takes back a [learn] of [word]: the user changed the word. */
    fun unlearn(word: String) {
        words.decrease(keyOf(word))
        changed()
    }

    /** The words the user began a field with, in no order. */
    fun all(): List<Learned> = words.values.toList()

    /** Forgets [word], as [PersonalWords.forget] does, and gives what it was: [PersonalWords] tells of the change. */
    internal fun forget(word: String): Learned? = words.remove(keyOf(word))

    /** Puts back [forgotten], added up with what was learned of it since, as [Forgotten.restore] does. */
    internal fun restore(forgotten: Learned) {
        words.merge(keyOf(forgotten.spelling), forgotten, ::restored)
    }

    /** Drops [key] when it was last used at [time] or before, as [PersonalWords.dropOlder] does. */
    internal fun dropOlder(key: String, time: Long) {
        if ((words[key]?.lastUsed ?: Long.MAX_VALUE) <= time) words.remove(key)
    }

    /** Puts [learned] back, as [write] wrote it. */
    internal fun put(learned: Learned) {
        words[keyOf(learned.spelling)] = learned
    }

    /** Holds [other]'s words, and nothing else. */
    internal fun replaceWith(other: FieldStarts) {
        words.takeAll(other.words)
        changed()
    }

    /** Puts in all of [other]'s words, for a snapshot. */
    internal fun putAll(other: FieldStarts) {
        words.putAll(other.words)
    }

    /** Writes every word, a line each, with [type] first: what [put] takes back from each. */
    internal fun write(out: Appendable, type: String) {
        for ((spelling, count, lastUsed) in words.values) out.append("$type\t$spelling\t$count\t$lastUsed\n")
    }
}

/**
 * Up to [count] words for an empty field: those the user began a field with often enough (see [FIELD_START_USES]
 * and [FIELD_START_SHARE]), the most used first, the latest used first among equals.
 */
fun fieldStartWords(personal: PersonalWords, count: Int): List<String> {
    val starts = personal.allStarts()
    val total = starts.sumOf { it.count }
    return starts
        .filter { it.count >= FIELD_START_USES && it.count >= total * FIELD_START_SHARE }
        .sortedWith(compareByDescending<Learned> { it.count }.thenByDescending { it.lastUsed })
        .take(count)
        .map { it.spelling }
}
