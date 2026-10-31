package name.alexwayfer.preglide.engine.glide

import kotlin.math.ceil
import kotlin.math.hypot

/**
 * A line through points on a plane where both ways are measured in key widths: a keyboard's point with its row
 * stretched by how tall a row is, so a step up or down weighs as far as it goes.
 */
internal class Polyline(val xs: FloatArray, val ys: FloatArray) {
    init {
        require(xs.isNotEmpty() && xs.size == ys.size) { "A line needs as many x as y, and one point at least" }
    }

    val size: Int get() = xs.size

    /** How long the line is from its first point to its last, along every point between. */
    val length: Float by lazy { (1 until size).sumOf { segment(it).toDouble() }.toFloat() }

    /** How far point [index] lies from [x], [y], squared. */
    fun squaredDistance(index: Int, x: Float, y: Float): Float {
        val across = xs[index] - x
        val down = ys[index] - y
        return across * across + down * down
    }

    /** How far point [index] of this line lies from point [other] of [line], squared. */
    fun squaredDistance(index: Int, line: Polyline, other: Int): Float =
        squaredDistance(index, line.xs[other], line.ys[other])

    /**
     * [count] points spread evenly along the line, its first and last among them; all at the first point for a line
     * of no length.
     */
    fun evenly(count: Int): Polyline {
        require(count >= 2) { "Spreading points along a line takes two at least" }
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val total = length
        // The segment the next point lies on ends at point segmentEnd, at reached along the line.
        var segmentEnd = 1
        var reached = if (size > 1) segment(1) else 0f
        for (point in 0 until count) {
            val wanted = total * point / (count - 1)
            while (segmentEnd < size - 1 && reached < wanted) {
                segmentEnd++
                reached += segment(segmentEnd)
            }
            if (size == 1 || total == 0f) {
                xs[point] = this.xs[0]
                ys[point] = this.ys[0]
            } else {
                val length = segment(segmentEnd)
                // How far back from the segment's end the point lies, as a share of the segment.
                val back = if (length > 0f) ((reached - wanted) / length).coerceIn(0f, 1f) else 0f
                xs[point] = this.xs[segmentEnd] - back * (this.xs[segmentEnd] - this.xs[segmentEnd - 1])
                ys[point] = this.ys[segmentEnd] - back * (this.ys[segmentEnd] - this.ys[segmentEnd - 1])
            }
        }
        return Polyline(xs, ys)
    }

    /** Points spread evenly along the line about [step] apart, its first and last among them. */
    fun spaced(step: Float): Polyline = evenly(maxOf(2, ceil(length / step).toInt() + 1))

    /** How long the segment from point [end] - 1 to point [end] is. */
    private fun segment(end: Int): Float = hypot(xs[end] - xs[end - 1], ys[end] - ys[end - 1])
}
