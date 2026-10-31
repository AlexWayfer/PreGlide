package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.suggest.Dictionary
import name.alexwayfer.preglide.engine.suggest.Meant
import name.alexwayfer.preglide.engine.suggest.WordTree
import name.alexwayfer.preglide.engine.suggest.foldedKey
import java.util.PriorityQueue

/**
 * The most matching a word's keys to a glide may cost (see [GlideRows]), in nats, for a word looked at besides the
 * dictionary's best, as the model's likeliest: past it, the glide went far from the word's keys. The dictionary's
 * best have no such bound: a glide always reads as its likeliest word, which is easier to fix than to type anew,
 * even when the word meant is not in the dictionary.
 */
internal const val MAX_MATCH_COST = 20f

/**
 * How far behind the best word found, in nats, a search stops: a word some 160,000 times less likely is never
 * offered beside it.
 */
private const val FAR_BEHIND = 12f

/**
 * How much of a reading's cost the shape of the whole glide makes (see [shapeCost]), the rest being what matching
 * the word's keys costs (see [GlideRows]). Matching alone forgave short frequent words a long glide's turns; the
 * shape alone sank a word whose keys the finger hit well but whose way it rounded off.
 */
private const val READING_SHARE = 0.5f

/** What a search notes for a start before any key. */
private const val NO_KEY = '\u0000'

/**
 * Up to [limit] words of the dictionary a glide over [keys] may mean, the likeliest by their frequency and what
 * matching their keys to the glide costs, each with what its shape costs.
 */
internal fun Dictionary.glided(keys: GlideKeys, limit: Int): List<Meant> =
    if (limit <= 0) emptyList() else GlideWalk(this, keys, limit).apply { search() }.found()

/** What reading the glide over these keys as [word] costs, or null when matching its keys costs too much. */
internal fun GlideKeys.costOf(word: String): Float? {
    val row = rows.of(foldedKey(word), MAX_MATCH_COST) ?: return null
    return rows.whole(row).takeIf { it <= MAX_MATCH_COST }?.let { readingCost(word, it) }
}

/** What reading the glide as [word], whose keys match it at [matched], costs: see [READING_SHARE]. */
internal fun GlideKeys.readingCost(word: String, matched: Float): Float? =
    shapeOf(word)?.let { matched * (1 - READING_SHARE) + it * READING_SHARE }

/** What [word]'s shape costs read from the glide, or null when it has no key. */
private fun GlideKeys.shapeOf(word: String): Float? = lineOf(word)?.let { shapeCost(glide.shape, it) }

/**
 * A search through the dictionary's words as a tree of their starts, the likeliest start first: a start's rank is
 * the log of the frequency of the most frequent word under it, less what its keys cost so far, which only grows as
 * a word goes on. So the first [limit] words that come out whole are the best, and the starts left in the queue,
 * most of the tree, are never looked into.
 */
internal class GlideWalk(private val dictionary: Dictionary, private val keys: GlideKeys, private val limit: Int) {
    private val tree = dictionary.tree
    private val rows = keys.rows
    private val best = ArrayList<Whole>(limit)

    fun search() {
        val queue = PriorityQueue<Node>(compareByDescending { it.rank })
        queue += Start(0, dictionary.size, 0, null, NO_KEY, Float.MAX_VALUE)
        while (queue.isNotEmpty() && best.size < limit && !farBehind(queue.peek())) {
            when (val node = queue.poll()) {
                is Whole -> best += node
                // Queued by its parent's cost, which it may only add to: read now, and queued again if it fell.
                is Pending -> queue += read(node)
                is Start -> expand(node, queue)
            }
        }
    }

    /**
     * Whether [node], the likeliest start left, ranks so far behind the best word found (see [FAR_BEHIND]) that no
     * word under it would be offered: as for a glide few words fit, which would otherwise be searched to the end.
     */
    private fun farBehind(node: Node): Boolean = best.isNotEmpty() && node.rank < best.first().rank - FAR_BEHIND

    /** The best words found, each with what reading the glide as it costs. */
    fun found(): List<Meant> = best.mapNotNull { node ->
        val word = dictionary.words[node.word]
        keys.readingCost(word, node.whole)?.let { Meant(word, it, dictionary.frequencies[node.word]) }
    }

    /** Queues the words that end at [node]'s start, whole, and the starts one char longer. */
    private fun expand(node: Start, queue: PriorityQueue<Node>) {
        val cost = node.cost
        var index = node.from
        while (index < node.to) {
            // The words that end with the start come first, then a branch for each next char.
            val char = tree.charAt(index, node.depth)
            if (char == WordTree.WORD_END) {
                // A word with no key, as a lone hyphen, is no word to glide.
                val whole = node.row?.let(rows::whole) ?: UNREACHED
                if (whole != UNREACHED) {
                    val rank = tree.logFrequencies[index] - whole
                    queue += Whole(index, whole, rank)
                }
                index++
                continue
            }
            val end = tree.branchEnd(index, node.to, node.depth, char)
            val keyed = keys.has(char) && char != node.last
            // A char that adds no key reads as its start does; a key is read once the start comes out of the queue.
            val rank = tree.logFrequencies.max(index, end) - cost
            queue += if (keyed) {
                Pending(index, end, node.depth + 1, node.row, char, rank)
            } else {
                Start(index, end, node.depth + 1, node.row, node.last, rank)
            }
            index = end
        }
    }

    /** [node] with its pending key read into its row, ranked by what that costs. */
    private fun read(node: Pending): Start {
        val char = node.key
        val row = node.row?.let { rows.follow(it, char) } ?: rows.start(char)
        // Ranked by what its words likely cost, the way ahead too, so a start that explains a long glide's beginning
        // alone waits behind one that explains more.
        val rank = tree.logFrequencies.max(node.from, node.to) - rows.ahead(row)
        return Start(node.from, node.to, node.depth, row, char, rank)
    }

    /**
     * What the search queues: a word whole, or a start of words. [rank] is the highest any word under it may rank:
     * the log of its frequency, less its cost.
     */
    private sealed class Node(val rank: Float)

    /** The word at [word] in the dictionary, whole, whose keys match the glide at [whole]. */
    private class Whole(val word: Int, val whole: Float, rank: Float) : Node(rank)

    /**
     * A start of the words from [from] to [to], [depth] chars long, read from the glide as [row], null before any
     * key, whose last key is [last]'s, [NO_KEY] for none.
     */
    private class Start(
        val from: Int,
        val to: Int,
        val depth: Int,
        val row: FloatArray?,
        val last: Char,
        rank: Float,
    ) : Node(rank) {
        /** What the start costs so far. */
        val cost: Float get() = row?.min() ?: 0f
    }

    /**
     * A start of the words from [from] to [to], [depth] chars long, whose last char adds [key] to the glide's [row]
     * of its parent: ranked by its parent's cost until the key is read.
     */
    private class Pending(
        val from: Int,
        val to: Int,
        val depth: Int,
        val row: FloatArray?,
        val key: Char,
        rank: Float,
    ) : Node(rank)
}
