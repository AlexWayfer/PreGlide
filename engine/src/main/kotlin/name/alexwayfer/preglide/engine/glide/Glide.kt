package name.alexwayfer.preglide.engine.glide

import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import name.alexwayfer.preglide.engine.suggest.DEFAULT_ROW_HEIGHT

/**
 * How far apart the points a word's keys are matched to lie, in key widths: a little closer than the keys, so a
 * key's best point lies at most some third of a key off the finger's way; closer points cost a long glide's
 * search more than they gained.
 */
private const val COURSE_STEP = 0.75f

/** The most points a word's keys are matched to: a glide across the keys many times over gets them farther apart. */
private const val MAX_COURSE_POINTS = 40

/** The chars below this code, Latin and Cyrillic among them, have their keys found in arrays rather than a map. */
private const val ARRAY_CHARS = 0x500

/**
 * A finger's glide over a keyboard's keys, from where it came down to where it lifted: its [points] in key widths
 * and rows (see [KeyPoint]), and how tall a row is, [rowHeight] key widths, so a stray up or down weighs as far as
 * it goes.
 */
class Glide(points: List<KeyPoint>, val rowHeight: Float = DEFAULT_ROW_HEIGHT) {
    init {
        require(points.isNotEmpty()) { "A glide has a point at least" }
    }

    /** The glide on a plane measured in key widths both ways. */
    internal val line = Polyline(
        FloatArray(points.size) { points[it].x },
        FloatArray(points.size) { points[it].y * rowHeight },
    )

    /** The glide as the evenly spread points a word's keys are matched to (see [GlideRows]). */
    internal val course: Polyline = line.spaced(maxOf(COURSE_STEP, line.length / (MAX_COURSE_POINTS - 1)))

    /** How far apart the points of the [course] lie, in key widths. */
    internal val courseSpacing: Float = if (course.size > 1) course.length / (course.size - 1) else 0f

    /** The glide as evenly spread points its shape is compared to a word's by. */
    internal val shape: Polyline = line.evenly(SHAPE_POINTS)

    /** How far the finger went, in key widths, a row's way down weighing [rowHeight]. */
    val length: Float get() = line.length
}

/** Where [layout]'s keys lie on the plane of [glide], by the char each types. */
internal class GlideKeys(layout: KeyboardLayout, val glide: Glide) {
    private val xs = FloatArray(ARRAY_CHARS) { Float.NaN }
    private val ys = FloatArray(ARRAY_CHARS) { Float.NaN }

    init {
        for ((char, place) in layout.glidePlaces) {
            if (char.code < ARRAY_CHARS) {
                xs[char.code] = place.center.x
                ys[char.code] = place.center.y * glide.rowHeight
            }
        }
    }

    /** Whether [char], lowercase and with ё as е, as a search sees it, has a key. */
    fun has(char: Char): Boolean = char.code < ARRAY_CHARS && !xs[char.code].isNaN()

    /** Where the middle of [char]'s key lies across, which must [has] a key. */
    fun x(char: Char): Float = xs[char.code]

    /** Where the middle of [char]'s key lies down, which must [has] a key. */
    fun y(char: Char): Float = ys[char.code]

    /** How words read from the glide over these keys, made once for every word read. */
    val rows: GlideRows by lazy { GlideRows(this) }
}
