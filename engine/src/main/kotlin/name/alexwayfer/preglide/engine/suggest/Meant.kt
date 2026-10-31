package name.alexwayfer.preglide.engine.suggest

import java.util.PriorityQueue
import kotlin.math.exp
import kotlin.math.roundToLong

/** A word the user may have meant: what its typing costs (see [Typing]), and how often it is used, per billion. */
data class Meant(val word: String, val cost: Float, val frequency: Long)

/**
 * How many of the dictionary's words the user may have meant are looked at, the likeliest by their frequency and
 * cost: enough that the context can still pick a rarer one.
 */
const val MEANT_WORDS = 30

/** [score] made less by [cost], as a reading of the taps that many times less likely than the plain one. */
fun lessBy(score: Double, cost: Float): Long = (score * exp(-cost.toDouble())).roundToLong()

/**
 * Up to [limit] words of the dictionary the user may have meant by [typing] without typing their start as it is,
 * the likeliest first by their frequency and cost; a word with the typed start is a plain completion. The words lie
 * in search order, so the walk goes through them as through a tree of their starts, and leaves a start already too
 * costly with every word under it.
 */
fun Dictionary.meant(typing: Typing, limit: Int): List<Meant> =
    if (limit <= 0) {
        emptyList()
    } else {
        MeantWalk(this, TypingRows(typing), typing.maxCost, limit).apply { walk(0, size, 0) }.found()
    }

/**
 * The words the user may have meant by [typing] without typing their start as it is, each once: the dictionary's
 * likeliest [limit], those of [more], as the words likely to come next, and the user's learned words, in the user's
 * spelling. Each comes with the dictionary's frequency of its spelling.
 */
fun meantWords(model: LanguageModel, typing: Typing, limit: Int, more: List<String> = emptyList()): List<Meant> {
    val dictionary = model.dictionary
    val found = LinkedHashMap<String, Meant>()
    for (meant in dictionary.meant(typing, limit)) found[keyOf(meant.word)] = meant
    for (word in more) {
        if (keyOf(word) !in found) meantOf(word, typing, dictionary)?.let { found[keyOf(word)] = it }
    }
    for ((spelling) in model.personal.all()) {
        meantOf(spelling, typing, dictionary)?.let { found[keyOf(spelling)] = it }
    }
    return found.values.toList()
}

/** [word] as one the user may have meant by [typing], or null when its start was typed as it is or costs too much. */
private fun meantOf(word: String, typing: Typing, dictionary: Dictionary): Meant? =
    typing.costOf(word)?.takeIf { it > 0f }?.let { Meant(word, it, dictionary.frequency(word)) }

private class MeantWalk(
    private val dictionary: Dictionary,
    private val rows: TypingRows,
    private val maxCost: Float,
    private val limit: Int,
) {
    private val tree = dictionary.tree

    // The least likely of the best found on top, to make room for a likelier one.
    private val best = PriorityQueue<Candidate>(limit + 1, compareBy { it.rank })

    /** Walks the words from [from] to [to], which all start with the [depth] chars spelled in [rows]. */
    fun walk(from: Int, to: Int, depth: Int) {
        var index = from
        while (index < to) {
            // The words that end with the start spelled come first, then a branch for each next char.
            val char = tree.charAt(index, depth)
            if (char == WordTree.WORD_END) {
                offer(index, rows.cost)
                index++
                continue
            }
            val end = tree.branchEnd(index, to, depth, char)
            val lowest = rows.push(char)
            when {
                // Not even the branch's most frequent word at its least cost would make it among the best.
                !mayRank(index, end, minOf(lowest, rows.cost)) -> Unit
                // No longer start costs less, so every word in the branch costs what its start does.
                lowest >= rows.cost -> offerAll(index, end, rows.cost)
                lowest <= maxCost -> walk(index, end, depth + 1)
            }
            rows.pop()
            index = end
        }
    }

    fun found(): List<Meant> = best.sortedByDescending { it.rank }.map {
        Meant(dictionary.words[it.index], it.cost, dictionary.frequencies[it.index])
    }

    /** Whether a word from [from] to [to] costing [cost] at least may still make it among the best. */
    private fun mayRank(from: Int, to: Int, cost: Float): Boolean =
        best.size < limit || tree.logFrequencies.max(from, to) - cost > best.peek().rank

    /** Offers the words from [from] to [to], all costing [cost]: none with the typed start or costing too much. */
    private fun offerAll(from: Int, to: Int, cost: Float) {
        if (cost > 0f && cost <= maxCost) for (index in from until to) offer(index, cost)
    }

    /** Keeps the word at [index], costing [cost], among the best, unless it has the typed start or costs too much. */
    private fun offer(index: Int, cost: Float) {
        if (cost <= 0f || cost > maxCost) return
        val rank = tree.logFrequencies[index] - cost
        if (best.size == limit && rank <= best.peek().rank) return
        best += Candidate(index, cost, rank)
        if (best.size > limit) best.poll()
    }
}

/** A word found at [index], its [cost], and its [rank]: the log of its frequency, less its cost. */
private class Candidate(val index: Int, val cost: Float, val rank: Float)
