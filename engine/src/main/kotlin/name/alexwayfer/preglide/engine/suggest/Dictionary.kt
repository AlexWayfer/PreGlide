package name.alexwayfer.preglide.engine.suggest

import java.io.Reader

/**
 * A language's words and how often each is used, searched by how a word starts. Words are kept in the order a
 * search sees them (see [compareFolded]), so the words that start with a prefix lie next to each other.
 * [positionOfLine] maps a line of the file to its word's place, for a file that was not in that order already.
 */
class Dictionary private constructor(
    /** The words in search order, for a walk through them such as [near]. */
    internal val words: Array<String>,
    internal val frequencies: LongArray,
    private val positionOfLine: IntArray?,
) {
    val size: Int get() = words.size

    /** The frequencies of all the words added up, per billion: what the words of the dictionary cover. */
    val totalFrequency: Long by lazy { (0 until size).sumOf { frequencies[it] } }

    private val lazyTree = lazy { WordTree(words, frequencies) }

    /**
     * The words as a tree of their starts, for a walk through them such as [near] or [meant], built at the first
     * walk unless [prepareWalks] built it before.
     */
    internal val tree: WordTree by lazyTree

    /** Whether [tree] is built. */
    internal val isTreeBuilt: Boolean get() = lazyTree.isInitialized()

    /**
     * Builds the tree a walk through the words goes by now, off the main thread when called there: the keyboard's
     * first glide or typed word would wait for it otherwise, some hundred milliseconds for a large dictionary.
     */
    fun prepareWalks() {
        tree
    }

    /** The word on [line] of the dictionary file, counting from 0 and leaving blank lines out; null past the end. */
    fun atLine(line: Int): String? {
        val position = if (positionOfLine == null) line else positionOfLine.getOrNull(line) ?: return null
        return words.getOrNull(position)
    }

    /**
     * Up to [limit] words that start with [prefix], ignoring case and apostrophes, the most frequent first. An е
     * typed finds ё too, but a ё typed only ё (see [keepsTypedYo]). An empty [prefix] completes nothing: what comes
     * next is for predictions, not completions.
     */
    fun complete(prefix: String, limit: Int): List<String> = completeScored(prefix, limit).map { it.word }

    /** [complete] with each word's frequency, per billion words. */
    fun completeScored(prefix: String, limit: Int, preferred: (String) -> Boolean = { false }): List<Scored> {
        if (prefix.isEmpty() || limit <= 0) return emptyList()
        val best = ArrayList<Int>(limit + 1)
        val order = compareByDescending<Int> { preferred(words[it]) }.thenByDescending { frequencies[it] }
        var index = firstAtOrAfter(prefix)
        while (index < words.size && startsWithFolded(words[index], prefix)) {
            if (keepsTypedYo(words[index], prefix)) insertByFrequency(best, index, limit, order)
            index++
        }
        return best.map { Scored(words[it], frequencies[it]) }
    }

    /**
     * The dictionary's own copy of [word], spelled exactly so, or null when the dictionary lacks it. Lists that
     * point at dictionary words keep this copy, so a word is in memory once.
     */
    fun find(word: String): String? = indexWhere(word) { it == word }?.let { words[it] }

    /**
     * How the dictionary spells [word], whatever case it is typed in, or null when the dictionary lacks it. A word
     * typed with е where the dictionary has it only with ё, as "еще", is the dictionary's "ещё": typing often
     * drops ё. A typed ё stays: "колёсики" is not the dictionary's "колесики".
     */
    fun spellingOf(word: String): String? =
        (indexWhere(word) { it.equals(word, ignoreCase = true) } ?: indexWhere(word) { isWithoutYo(word, it) })
            ?.let { words[it] }

    /** How often [word], spelled exactly so, is used per billion words; 0 for a word the dictionary lacks. */
    fun frequency(word: String): Long = indexWhere(word) { it == word }?.let { frequencies[it] } ?: 0

    /**
     * Whether the dictionary has [word] in any case, with ё typed as е or the other way round. An apostrophe counts:
     * "dont" is not "don't".
     */
    fun knows(word: String): Boolean = indexWhere(word) { foldsTheSame(it, word) } != null

    /** The index of a word a search sees as [word] and that [matches], or null when there is none. */
    private fun indexWhere(word: String, matches: (String) -> Boolean): Int? {
        var index = firstAtOrAfter(word)
        while (index < words.size && compareFolded(words[index], word) == 0) {
            if (matches(words[index])) return index
            index++
        }
        return null
    }

    /** Puts [index] into [best], kept sorted by frequency and at most [limit] long; earlier words win ties. */
    private fun insertByFrequency(best: MutableList<Int>, index: Int, limit: Int, order: Comparator<Int>) {
        var position = best.size
        while (position > 0 && order.compare(index, best[position - 1]) < 0) position--
        if (position < limit) {
            best.add(position, index)
            if (best.size > limit) best.removeAt(best.lastIndex)
        }
    }

    /** The index of the first word that does not come before [prefix] in search order. */
    private fun firstAtOrAfter(prefix: String): Int {
        var low = 0
        var high = words.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (compareFolded(words[middle], prefix) < 0) low = middle + 1 else high = middle
        }
        return low
    }

    companion object {
        /**
         * A dictionary from the lines of a dictionary file: a word and its frequency, separated by a tab. The
         * dictionary builder writes them in search order, which leaves nothing to sort on a phone; any other order
         * is sorted here.
         */
        fun parse(lines: Sequence<String>): Dictionary {
            val words = ArrayList<String>()
            var frequencies = LongArray(INITIAL_SIZE)
            for (line in lines) {
                if (line.isBlank()) continue
                val tab = line.indexOf('\t')
                require(tab > 0) { "A dictionary line needs a word, a tab and a frequency: $line" }
                if (words.size == frequencies.size) frequencies = frequencies.copyOf(frequencies.size * 2)
                frequencies[words.size] = line.substring(tab + 1).trim().toLong()
                words += line.substring(0, tab)
            }
            val inOrder = (1 until words.size).all { compareFolded(words[it - 1], words[it]) <= 0 }
            if (inOrder) return Dictionary(words.toTypedArray(), frequencies.copyOf(words.size), null)
            // A stable sort: words that a search sees as the same, as все and всё, keep the file's order.
            val sortedLines = words.indices.sortedWith { a, b -> compareFolded(words[a], words[b]) }
            val positionOfLine = IntArray(words.size)
            sortedLines.forEachIndexed { position, line -> positionOfLine[line] = position }
            return Dictionary(
                Array(words.size) { words[sortedLines[it]] },
                LongArray(words.size) { frequencies[sortedLines[it]] },
                positionOfLine,
            )
        }

        fun read(reader: Reader): Dictionary = reader.buffered().useLines(::parse)

        private const val INITIAL_SIZE = 1024
    }
}
