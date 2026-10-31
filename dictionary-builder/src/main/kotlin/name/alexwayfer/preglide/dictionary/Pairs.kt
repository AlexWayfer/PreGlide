package name.alexwayfer.preglide.dictionary

import name.alexwayfer.preglide.engine.suggest.foldedKey
import java.util.Locale
import kotlin.math.roundToLong

/** How many of the most frequent words get next words: those after rarer words are seldom worth the room. */
const val PREDICTED_AFTER = 20_000

/** How many next words a word keeps: more than the strip shows, so typing the next word's start finds it. */
const val NEXT_WORDS = 20

/** A word, lowercase, and the word right after it in a sentence, with how many times the two came so. */
data class WordPair(val previous: String, val next: String, val occurrences: Long)

/** The pairs kept from one text, and the total of all pairs counted there, the rare ones included. */
data class PairCounts(val pairs: List<WordPair>, val total: Long)

/** A word's next word in the next words file: the word's search key, the next word's spelling, its frequency. */
data class NextWord(val previous: String, val next: String, val frequency: Long)

/**
 * The next words of [words], a dictionary the most frequent word first, from the pairs of several texts, each
 * with its share: a pair's frequency is its weighted share of each text's pairs, per billion. Previous words
 * that a search sees as one, as "все" and "всё", pool their pairs; a next word takes its dictionary spelling,
 * with ё put back by [yo], so "еще" and "ещё" pool theirs too. A word repeated, as "спасибо, спасибо", is no next
 * word to suggest: it was just typed.
 */
fun nextWords(
    sources: List<Pair<PairCounts, Double>>,
    words: List<Word>,
    yo: Map<String, String> = emptyMap(),
): List<NextWord> {
    val spelled = withYo(words, yo).associateBy { it.spelling.lowercase(Locale.ROOT) }
    val spellings = words.associateBy(
        { it.spelling.lowercase(Locale.ROOT) },
        { spelled.getValue(withYo(it.spelling, yo).lowercase(Locale.ROOT)).spelling },
    )
    val rank = HashMap<String, Int>()
    words.forEachIndexed { index, word -> rank.putIfAbsent(foldedKey(word.spelling), index) }
    val frequencies = HashMap<Pair<String, String>, Double>()
    for ((counts, share) in sources) {
        if (counts.total == 0L) continue
        for ((previous, typed, occurrences) in counts.pairs.filter { foldedKey(it.previous) != foldedKey(it.next) }) {
            val next = spellings[typed] ?: continue
            val frequency = share * PER_BILLION * occurrences / counts.total
            frequencies.merge(foldedKey(previous) to next, frequency, Double::plus)
        }
    }
    val mostFrequentFirst = compareByDescending<NextWord> { it.frequency }.thenBy { it.next }
    return frequencies.entries
        .groupBy({ it.key.first }, { NextWord(it.key.first, it.key.second, it.value.roundToLong()) })
        .entries
        .sortedBy { rank[it.key] ?: Int.MAX_VALUE }
        .flatMap { (_, next) -> next.sortedWith(mostFrequentFirst).take(NEXT_WORDS) }
}

/**
 * The next words file: a line per pair, the word's key, the next word and the frequency, separated by tabs. The
 * next word is its line in the dictionary file of [words], so the keyboard finds it without a search.
 */
fun nextWordsText(next: List<NextWord>, words: List<Word>): String {
    val lineOf = searchOrder(words).withIndex().associate { (line, word) -> word.spelling to line }
    return next.joinToString("") { "${it.previous}\t${lineOf.getValue(it.next)}\t${it.frequency}\n" }
}
