package name.alexwayfer.preglide.engine.suggest

/**
 * [values], and the largest of any run of them, found in a few steps however long the run: a tree whose every node
 * holds the larger of its two children, the values being its leaves.
 */
class RangeMax(values: FloatArray) {
    private val size = values.size
    private val tree = FloatArray(2 * size).also { tree ->
        values.copyInto(tree, size)
        for (node in size - 1 downTo 1) tree[node] = maxOf(tree[2 * node], tree[2 * node + 1])
    }

    /** The value at [index]. */
    operator fun get(index: Int): Float = tree[size + index]

    /** The largest of the values from [from] to [to], [to] left out; negative infinity for none. */
    fun max(from: Int, to: Int): Float {
        var largest = Float.NEGATIVE_INFINITY
        var left = from + size
        var right = to + size
        while (left < right) {
            if (left % 2 == 1) largest = maxOf(largest, tree[left++])
            if (right % 2 == 1) largest = maxOf(largest, tree[--right])
            left /= 2
            right /= 2
        }
        return largest
    }
}
