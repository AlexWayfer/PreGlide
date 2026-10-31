package name.alexwayfer.preglide.engine.glide

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil

/*
 * Costs are in nats, as a typed word's are (see name.alexwayfer.preglide.engine.suggest.Typing): a word's keys are
 * matched to points along the glide in order, and each match, each turn of the finger no key explains, and the way
 * the finger ran on past the last key cost what they make the reading less likely.
 */

/**
 * How far the finger strays from a key's middle as it glides over it, in key widths: the spread of a bell curve. A
 * key one key width off the glide costs about 1.4, one and a half off about 3.
 */
private const val PASS_SPREAD = 0.6f

/**
 * What a turn of the finger that no key of the word explains costs, by the radian: a right angle about 4.7, so a
 * word that skips a corner of the glide loses to the one with a key there.
 */
private const val TURN_COST = 3f

/** How far around the point a key is matched to its turning counts as explained by the key, in key widths. */
private const val EXPLAINED_AROUND = 0.75f

/** What the finger's way past the word's last key costs, by the key width: a fast finger runs a little past it. */
private const val RUN_ON_COST = 1.5f

/**
 * What a word likely costs further for each key width of the glide its start has not explained yet: about what the
 * keys of a word glided well cost along the way. It ranks a start that explains only the beginning of a long glide
 * below one that explains more, so a search goes down the likely words first.
 */
private const val AHEAD_COST = 0.35f

/** A cost no reading reaches. */
internal const val UNREACHED = Float.MAX_VALUE

/**
 * How a word's start reads from a glide over [keys], a char at a time: for each point of the glide's
 * [Glide.course], the least cost of the start with its last key matched there, as a row. [start] makes the row of a
 * first key and [follow] the row after one more; [whole] tells what the word costs as it ends. A word that starts
 * alike shares the rows of its start, as name.alexwayfer.preglide.engine.suggest.TypingRows does for taps.
 */
internal class GlideRows(private val keys: GlideKeys) {
    private val course = keys.glide.course
    private val points = course.size

    // How many points around a matched one its key explains the turning of.
    private val around = if (keys.glide.courseSpacing > 0f) {
        ceil(EXPLAINED_AROUND / keys.glide.courseSpacing).toInt()
    } else {
        0
    }

    // The turning of the course up to and with each point, in radians.
    private val turned = FloatArray(points).also { sums ->
        var sum = 0f
        for (point in 0 until points) {
            sum += turnAt(point)
            sums[point] = sum
        }
    }

    /** The row of a word's first key, [char]'s, which must have a key: matched where the glide starts. */
    fun start(char: Char): FloatArray = FloatArray(points) { UNREACHED }.also { it[0] = passCost(char, 0) }

    /**
     * The row after [row], whose last key is [char]'s, which must have a key, with [char]'s key matched at each
     * point at or after the last one's, the turning between them that neither explains costing besides.
     */
    fun follow(row: FloatArray, char: Char): FloatArray {
        val next = FloatArray(points)
        // The least of row[before] less the turning up to what its key explains, over points far enough back.
        var farLeast = UNREACHED
        for (point in 0 until points) {
            val far = point - 2 * around - 1
            if (far >= 0 && row[far] != UNREACHED) {
                farLeast = minOf(farLeast, row[far] - TURN_COST * turned[minOf(far + around, points - 1)])
            }
            var nearLeast = UNREACHED
            for (before in maxOf(0, far + 1)..point) nearLeast = minOf(nearLeast, row[before])
            val viaFar = if (farLeast == UNREACHED) UNREACHED else farLeast + TURN_COST * turned[point - around - 1]
            val least = minOf(nearLeast, viaFar)
            next[point] = if (least == UNREACHED) UNREACHED else least + passCost(char, point)
        }
        return next
    }

    /**
     * What a word whose start ends with [row] costs read whole from the glide: its last key matched to a point, with
     * the turning after it that no key explains and the finger's way past it.
     */
    fun whole(row: FloatArray): Float {
        var least = UNREACHED
        for (point in 0 until points) {
            if (row[point] == UNREACHED) continue
            val unexplained = turnedBetween(point + around, points - 1)
            val runOn = (points - 1 - point) * keys.glide.courseSpacing * RUN_ON_COST
            least = minOf(least, row[point] + TURN_COST * unexplained + runOn)
        }
        return least
    }

    /**
     * What a word whose start ends with [row] likely costs, the way ahead of its last key costing [AHEAD_COST] by the
     * key width: for ranking starts, not an exact bound.
     */
    fun ahead(row: FloatArray): Float {
        var least = UNREACHED
        for (point in 0 until points) {
            if (row[point] == UNREACHED) continue
            least = minOf(least, row[point] + (points - 1 - point) * keys.glide.courseSpacing * AHEAD_COST)
        }
        return least
    }

    /**
     * The rows of [word] spelled in from the start, chars as a search sees them: the last row, or null before any
     * key, or once its start costs more than [most]. A char without a key, as a hyphen, and a key twice in a row
     * change nothing.
     */
    fun of(word: CharSequence, most: Float = UNREACHED): FloatArray? {
        var row: FloatArray? = null
        var last = '\u0000'
        for (char in word) {
            if (!keys.has(char) || char == last) continue
            val next = row?.let { follow(it, char) } ?: start(char)
            if (next.min() > most) return null
            row = next
            last = char
        }
        return row
    }

    /** What matching [char]'s key to [point] costs, by how far the point lies from the key's middle. */
    private fun passCost(char: Char, point: Int): Float =
        course.squaredDistance(point, keys.x(char), keys.y(char)) / (2 * PASS_SPREAD * PASS_SPREAD)

    /** The turning of the course after point [from] up to and with point [to]; nothing when [to] comes first. */
    private fun turnedBetween(from: Int, to: Int): Float =
        if (from >= to) 0f else turned[to] - turned[minOf(from, points - 1)]

    /** How far the course turns at [point], in radians: nothing at its ends. */
    private fun turnAt(point: Int): Float {
        if (point == 0 || point >= points - 1) return 0f
        val inAngle = atan2(course.ys[point] - course.ys[point - 1], course.xs[point] - course.xs[point - 1])
        val outAngle = atan2(course.ys[point + 1] - course.ys[point], course.xs[point + 1] - course.xs[point])
        val turn = abs(outAngle - inAngle)
        return if (turn > Math.PI) (2 * Math.PI - turn).toFloat() else turn
    }
}
