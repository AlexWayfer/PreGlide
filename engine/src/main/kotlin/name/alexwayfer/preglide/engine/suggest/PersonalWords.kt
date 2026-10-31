package name.alexwayfer.preglide.engine.suggest

import java.util.Locale

/**
 * A word the user typed: how it is spelled, how many times it was typed, and when last, in epoch milliseconds; and
 * how many of those times the user [kept] it as typed on purpose, picking it from the strip although the keyboard
 * did not know it.
 */
data class Learned(val spelling: String, val count: Int, val lastUsed: Long, val kept: Int = 0)

/** A word [PersonalWords.forget] took, with the field starts and contexts it was learned in. */
class Forgotten internal constructor(private val putBack: () -> Unit) {
    /** Puts the word back, added up with what the user typed of it since, as the user undoes forgetting it; once. */
    fun restore() = putBack()
}

/**
 * What the keyboard learned from the user's typing in one language: the words, the words after one to
 * [LONGEST_CONTEXT] words before them, the words the user began a field with, and the words the user removed, which
 * are never suggested until typed again.
 * Every change counts in [version], so whoever saves it knows when there is something new to save. The times, the
 * removals and the forgotten words are kept so that what other devices learned merges in (see [mergeOthers]).
 */
class PersonalWords internal constructor(internal val next: LearnedNext) {
    constructor() : this(LearnedNext())

    internal val words = HashMap<String, Learned>()
    internal val removed = HashMap<String, Long>()

    /** When each word was forgotten last: its uses on any device from before then are left out. */
    internal val forgotten = HashMap<String, Long>()

    internal var others = OtherDevices(this, emptyMap())
        private set
    private var allAt: Pair<Int, List<Learned>>? = null

    var version: Int = 0
        private set

    /** The words the user began a field with; each change counts in [version] too. */
    val starts = FieldStarts { version++ }

    /**
     * Learns that the user typed [word], after [context], the words of the sentence right before it, the nearest
     * last; [kept] when they kept it as typed on purpose.
     */
    fun learn(word: String, context: List<String>, time: Long, kept: Boolean = false) {
        val key = keyOf(word)
        removed.remove(key)
        words[key] = learnedOnce(words[key], word, time, kept)
        version++
        learnNext(word, context, time)
    }

    /** Learns only that [word] came after [context], as when the word before it changed to the last of [context]. */
    fun learnNext(word: String, context: List<String>, time: Long) {
        next.learn(word, context, time)
        version++
    }

    /** Takes back a [learn] of the same [word] after the same [context], [kept] or not: the user changed the word. */
    fun unlearn(word: String, context: List<String>, kept: Boolean = false) {
        words.decrease(keyOf(word), kept)
        next.unlearn(word, context)
        version++
    }

    /**
     * Forgets [word] with what it was learned after, as if the user never typed it: a dictionary word is still
     * suggested as the dictionary has it. What it gives back can put it all back.
     */
    fun forget(word: String, time: Long): Forgotten {
        val key = keyOf(word)
        val learned = words.remove(key)
        val start = starts.forget(word)
        val after = next.forget(word)
        val forgottenBefore = forgotten.put(key, maxOf(time, forgotten[key] ?: time))
        version++
        return Forgotten {
            learned?.let { words.merge(keyOf(it.spelling), it, ::restored) }
            start?.let(starts::restore)
            next.restore(after)
            if (forgottenBefore == null) forgotten.remove(key) else forgotten[key] = forgottenBefore
            version++
        }
    }

    /** Forgets [word] and keeps it out of suggestions, the dictionary's words included, until the user types it. */
    fun remove(word: String, time: Long) {
        forget(word, time)
        removed[keyOf(word)] = time
    }

    /** How many words the user removed. */
    val removedCount: Int get() = removedWords.size

    /** The words the user removed, in lowercase, copied. */
    val removedWords: Set<String> get() = others.removed()

    /** How [word] was learned on all the devices, or null when it was not. */
    fun learned(word: String): Learned? = others.learned(keyOf(word))

    /** The learned words that came after the last [length] words of [context], the nearest last. */
    fun after(context: List<String>, length: Int = context.size): List<Learned> = others.after(context, length)

    /**
     * Merges in what [devices], the other devices by their IDs, learned, in place of what came of them before: their
     * uses add up with this device's in what is read, but only this device's are written. What this device learned of
     * a word before another device forgot or removed it goes, as forgetting it here would take it.
     */
    fun mergeOthers(devices: Map<String, PersonalWords>) {
        others = OtherDevices(this, devices)
        others.dropForgottenFromOwn()
        version++
    }

    /**
     * Takes all of [other] in place of what is here, with its other devices, as when another account's words come in:
     * whoever holds this one reads them from now on.
     */
    fun replaceWith(other: PersonalWords) {
        words.takeAll(other.words)
        removed.takeAll(other.removed)
        forgotten.takeAll(other.forgotten)
        next.replaceWith(other.next)
        starts.replaceWith(other.starts)
        others = OtherDevices(this, other.others.devices)
        version++
    }

    /** Every learned word, on all the devices, in no order. */
    fun all(): List<Learned> =
        allAt?.takeIf { it.first == version }?.second ?: others.all().also { allAt = version to it }

    /**
     * Writes what this device learned, a line per word, next word after a context, word a field began with, removal,
     * or forgotten word: what [read] reads back.
     */
    fun write(out: Appendable) {
        for (word in words.values) {
            with(word) { out.append("$WORD\t$spelling\t$count\t$lastUsed\t$kept\n") }
        }
        next.write(out, NEXT)
        starts.write(out, START)
        for ((word, time) in removed) out.append("$REMOVED\t$word\t$time\n")
        for ((word, time) in forgotten) out.append("$FORGOTTEN\t$word\t$time\n")
    }

    companion object {
        private const val WORD = "w"
        private const val NEXT = "n"

        /** A next word after a single word, as files before longer contexts had them: read as [NEXT] is. */
        private const val PAIR = "p"
        private const val START = "s"
        private const val REMOVED = "r"
        private const val FORGOTTEN = "f"

        /**
         * A copy of what reading a word after [context] in [personal] needs, for another thread to read while
         * [personal] changes on: the learned words, the words fields began with, the removed ones, and the next words
         * after [context] alone.
         */
        fun snapshot(personal: PersonalWords, context: List<String>): PersonalWords =
            PersonalWords(personal.next.copyFor(context)).also { copy ->
                copy.words.putAll(personal.words)
                copy.starts.putAll(personal.starts)
                copy.removed.putAll(personal.removed)
                copy.forgotten.putAll(personal.forgotten)
                copy.others = OtherDevices(copy, personal.others.devices)
            }

        /** Reads what [write] wrote. A line it cannot read, as from a newer version, is skipped. */
        fun read(lines: Sequence<String>): PersonalWords = PersonalWords().apply {
            for (fields in lines.map { it.split('\t') }) {
                when (fields.first()) {
                    WORD -> readWord(fields)?.let { words[keyOf(it.spelling)] = it }
                    NEXT, PAIR -> readWord(fields.drop(1))?.let { next.put(fields[1], it) }
                    START -> readWord(fields)?.let(starts::put)
                    REMOVED -> fields.getOrNull(2)?.toLongOrNull()?.let { removed[fields[1]] = it }
                    FORGOTTEN -> fields.getOrNull(2)?.toLongOrNull()?.let { forgotten[fields[1]] = it }
                }
            }
        }

        private fun readWord(fields: List<String>): Learned? {
            val count = fields.getOrNull(2)?.toIntOrNull()
            val time = fields.getOrNull(3)?.toLongOrNull()
            val kept = fields.getOrNull(4)?.toIntOrNull() ?: 0
            return if (count != null && time != null) Learned(fields[1], count, time, kept) else null
        }
    }
}

/** [PersonalWords.learn] after [previous], the one word before [word], if any. */
fun PersonalWords.learn(word: String, previous: String?, time: Long, kept: Boolean = false) =
    learn(word, listOfNotNull(previous), time, kept)

/** [PersonalWords.unlearn] after [previous], the one word before [word], if any. */
fun PersonalWords.unlearn(word: String, previous: String?, kept: Boolean = false) =
    unlearn(word, listOfNotNull(previous), kept)

/** The learned words that came after [previous]. */
fun PersonalWords.after(previous: String): List<Learned> = after(listOf(previous))

/**
 * The learned words after the last word of [context], after its last two, and so on, as far as suggestions read
 * (see [RANKED_CONTEXT]): a list for each, the shortest context's first.
 */
fun PersonalWords.rankedAfter(context: List<String>): List<List<Learned>> =
    (1..minOf(context.size, RANKED_CONTEXT)).map { after(context, it) }

/**
 * The learned words that start with [prefix], as a search sees them, with ё where it has ё. A word with a digit, as
 * "топ-3", comes only once [trusted]: a code typed once, as "SALE2024", stays out of the strip.
 */
fun PersonalWords.completions(prefix: String): List<Learned> =
    all().filter { startsLikeTyped(it.spelling, prefix) && (it.trusted || it.spelling.none(Char::isDigit)) }

/**
 * The [learned] words [dictionary] does not have spelled exactly so: names, slang, the user's own words, and the
 * user's own case or ё of a dictionary word, as "API" where it has "api". The rest are learned only to count how
 * often the user types them.
 */
fun beyondDictionary(learned: List<Learned>, dictionary: Dictionary): List<Learned> =
    learned.filter { dictionary.find(it.spelling) == null }

/** Words are kept by their lowercase: "Привет" at a sentence start and "привет" are one word. */
internal fun keyOf(word: String): String = word.lowercase(Locale.ROOT)

internal fun learnedOnce(before: Learned?, spelling: String, time: Long, kept: Boolean = false): Learned =
    Learned(spelling, (before?.count ?: 0) + 1, time, (before?.kept ?: 0) + if (kept) 1 else 0)

/** [back], a word forgotten and restored, added up with [since], what was learned of it in between: spelled as last. */
internal fun restored(since: Learned, back: Learned): Learned =
    Learned(since.spelling, since.count + back.count, maxOf(since.lastUsed, back.lastUsed), since.kept + back.kept)

/** Holds what [other] holds, and nothing else. */
internal fun <V> HashMap<String, V>.takeAll(other: Map<String, V>) {
    clear()
    putAll(other)
}

internal fun HashMap<String, Learned>.decrease(key: String, kept: Boolean = false) {
    val learned = get(key) ?: return
    val keptLeft = if (kept) maxOf(learned.kept - 1, 0) else learned.kept
    if (learned.count > 1) put(key, learned.copy(count = learned.count - 1, kept = keptLeft)) else remove(key)
}
