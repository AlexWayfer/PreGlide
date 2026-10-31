package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.suggest.fold
import name.alexwayfer.preglide.engine.suggest.isSkipped

/** How many points along a glide and along a word's line they are compared by: several to each key of a word. */
internal const val SHAPE_POINTS = 32

/**
 * How far a finger strays from the line through a word's keys, in key widths: the spread of a bell curve. A glide
 * that keeps a third of a key off the line on average costs about half a nat for each of [SHAPE_SAMPLES].
 */
private const val GLIDE_SPREAD = 0.35f

/**
 * How many points of a glide tell apart as much as one, independent each: the points follow one another along the
 * finger's line, so they tell far less than as many taps would. About the keys of a short word.
 */
private const val SHAPE_SAMPLES = 6f

/**
 * The line through the keys of [word]'s chars, as a search sees them, one point for a key twice in a row; null when
 * none of them has a key. A char without a key, as a hyphen, is left out: the finger glides past it.
 */
internal fun GlideKeys.lineOf(word: String): Polyline? {
    val xs = ArrayList<Float>(word.length)
    val ys = ArrayList<Float>(word.length)
    var last = '\u0000'
    for (raw in word) {
        val char = fold(raw)
        if (isSkipped(raw) || !has(char) || char == last) continue
        xs += x(char)
        ys += y(char)
        last = char
    }
    return if (xs.isEmpty()) null else Polyline(xs.toFloatArray(), ys.toFloatArray())
}

/**
 * What reading [shape], a glide's points spread evenly, as the word whose keys [word] passes costs, in nats (see
 * [name.alexwayfer.preglide.engine.suggest.Typing]): by how far the glide strays from the word's line, its points
 * matched to the line's in order, as each side may linger where the other moves on.
 */
internal fun shapeCost(shape: Polyline, word: Polyline): Float {
    val spread = word.evenly(shape.size)
    return SHAPE_SAMPLES * warpedDistance(shape, spread) / (2 * GLIDE_SPREAD * GLIDE_SPREAD)
}

/**
 * How far [a]'s points lie from [b]'s on average, squared, each point matched to one or more of the other's in
 * order, first to first and last to last, the way that makes them closest: dynamic time warping. The sum is taken
 * over as many matches as [a] has points, so lingering costs a little more than going along.
 */
internal fun warpedDistance(a: Polyline, b: Polyline): Float {
    val columns = b.size
    // The least sum of distances to match a's points up to the row's with b's up to each column.
    var above = FloatArray(columns)
    var row = FloatArray(columns)
    for (i in 0 until a.size) {
        for (j in 0 until columns) {
            val before = when {
                i == 0 && j == 0 -> 0f
                i == 0 -> row[j - 1]
                j == 0 -> above[0]
                else -> minOf(above[j], row[j - 1], above[j - 1])
            }
            row[j] = before + a.squaredDistance(i, b, j)
        }
        above = row.also { row = above }
    }
    return above[columns - 1] / a.size
}
