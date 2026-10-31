package name.alexwayfer.preglide.engine.suggest

import java.util.BitSet
import kotlin.math.ln

/**
 * A dictionary's [words], in search order (see [compareFolded]), as a tree of their starts: the words that start
 * alike up to a depth lie in a run, and those of them with one char next are a branch of it. A walk through them
 * such as [near] or [meant] goes branch by branch, and leaves a whole branch it does not need.
 */
internal class WordTree(private val words: Array<String>, frequencies: LongArray) {
    /**
     * The log of each word's frequency, one more so a word of none gets 0, by its place: a walk learns from it how
     * frequent the most frequent word of a branch is, and leaves a branch with none it needs.
     */
    val logFrequencies = RangeMax(FloatArray(words.size) { ln(frequencies[it] + 1.0).toFloat() })

    // The words with a char a search skips, as an apostrophe: few, and only theirs need a slow look at a char.
    private val withSkipped = BitSet(words.size).apply {
        words.forEachIndexed { index, word -> if (word.any(::isSkipped)) set(index) }
    }

    /**
     * The char of the word at [index] at [depth], as a search sees it, or [WORD_END] past its end: see
     * [foldedCharAt]. Not null, which would box every char a walk looks at.
     */
    fun charAt(index: Int, depth: Int): Char {
        val word = words[index]
        if (withSkipped[index]) return foldedCharAt(word, depth) ?: WORD_END
        return if (depth < word.length) fold(word[depth]) else WORD_END
    }

    /**
     * The end of the words from [from] to [to] that have [char] at [depth], [from] being the first of them: the end
     * of its branch. The search gallops from [from] first, as a branch is often far shorter than the run it is in.
     */
    fun branchEnd(from: Int, to: Int, depth: Int, char: Char): Int {
        // The last word known to be in the branch, and how far past it to look next.
        var known = from
        var step = 1
        while (known + step < to && inBranch(known + step, depth, char)) {
            known += step
            step *= 2
        }
        var low = known + 1
        var high = minOf(known + step, to)
        while (low < high) {
            val middle = (low + high) ushr 1
            if (inBranch(middle, depth, char)) low = middle + 1 else high = middle
        }
        return low
    }

    /** Whether the word at [index], one of a run that all start alike up to [depth], is in the branch of [char]. */
    private fun inBranch(index: Int, depth: Int, char: Char): Boolean {
        // The words that end before depth come first in the run, so none of them is after the branch's first word.
        val next = charAt(index, depth)
        return next != WORD_END && next <= char
    }

    companion object {
        /** What [charAt] gives past a word's end: a char no word has. */
        const val WORD_END = '\u0000'
    }
}
