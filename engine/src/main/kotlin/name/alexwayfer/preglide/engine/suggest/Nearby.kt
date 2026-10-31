package name.alexwayfer.preglide.engine.suggest

/** A word near a misspelled one: how many edits away it is, and how often it is used, per billion words. */
data class Near(val word: String, val distance: Int, val frequency: Long)

/**
 * How many edits a typed word is from a word that is spelled into it a char at a time: an edit types a char in,
 * leaves one out, types one in place of another, or swaps two neighbors. [push] adds a char of the spelled word
 * and [pop] takes the last one back, so words that start alike share the work for their start.
 */
class EditRows(private val target: String) {
    // rows[depth][length]: the edits between the first depth chars spelled and the first length chars of target.
    private val rows = arrayListOf(IntArray(target.length + 1) { it })
    private val spelled = StringBuilder()

    /** The edits between what is spelled and the whole target. */
    val distance: Int get() = rows[spelled.length][target.length]

    /**
     * Spells [char] next. Returns the fewest edits any word that starts with what is spelled now can be from the
     * target, at best: once it is too many, so is every longer start.
     */
    fun push(char: Char): Int {
        val depth = spelled.length
        val row = rows[depth]
        if (rows.size == depth + 1) rows += IntArray(target.length + 1)
        val next = rows[depth + 1]
        next[0] = depth + 1
        var fewest = next[0]
        for (length in 1..target.length) {
            val replaced = row[length - 1] + if (target[length - 1] == char) 0 else 1
            var edits = minOf(row[length] + 1, next[length - 1] + 1, replaced)
            if (swapped(char, length)) edits = minOf(edits, rows[depth - 1][length - 2] + 1)
            next[length] = edits
            fewest = minOf(fewest, edits)
        }
        spelled.append(char)
        return fewest
    }

    /** Takes back the last [push]. */
    fun pop() {
        spelled.setLength(spelled.length - 1)
    }

    /** Whether [char], after the last char spelled, swaps the two target chars that end at [length]. */
    private fun swapped(char: Char, length: Int): Boolean =
        spelled.isNotEmpty() && length >= 2 && char == target[length - 2] && spelled.last() == target[length - 1]
}

/** How many edits apart [a] and [b] are, as a search sees them: see [EditRows]. */
fun editDistance(a: String, b: String): Int {
    val rows = EditRows(foldedKey(b))
    for (char in foldedKey(a)) rows.push(char)
    return rows.distance
}

/**
 * The words of the dictionary at most [maxDistance] edits from [typed], as a search sees both: see [EditRows]. The
 * words lie in search order, so the walk goes through them as through a tree of their starts, and leaves a start
 * already too far with every word under it: it reads a small part of the dictionary.
 */
fun Dictionary.near(typed: String, maxDistance: Int): List<Near> =
    NearWalk(this, EditRows(foldedKey(typed)), maxDistance).apply { walk(0, size, 0) }.found

private class NearWalk(private val dictionary: Dictionary, private val rows: EditRows, private val maxDistance: Int) {
    val found = ArrayList<Near>()

    /** Walks the words from [from] to [to], which all start with the [depth] chars spelled in [rows]. */
    fun walk(from: Int, to: Int, depth: Int) {
        var index = from
        while (index < to) {
            // The words that end with the start spelled come first, then a branch for each next char.
            val char = dictionary.tree.charAt(index, depth)
            if (char == WordTree.WORD_END) {
                if (rows.distance <= maxDistance) {
                    found += Near(dictionary.words[index], rows.distance, dictionary.frequencies[index])
                }
                index++
                continue
            }
            val end = dictionary.tree.branchEnd(index, to, depth, char)
            if (rows.push(char) <= maxDistance) walk(index, end, depth + 1)
            rows.pop()
            index = end
        }
    }
}
