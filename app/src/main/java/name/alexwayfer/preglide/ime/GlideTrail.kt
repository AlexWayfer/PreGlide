package name.alexwayfer.preglide.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import name.alexwayfer.preglide.engine.glide.Glide
import name.alexwayfer.preglide.engine.layout.KeyPoint
import name.alexwayfer.preglide.engine.layout.KeyboardLayout
import kotlin.time.Duration.Companion.milliseconds

/** How long a point of the trail takes to fade away, in milliseconds: the tail follows the finger this far behind. */
const val TRAIL_FADE_MS = 200L

/**
 * How often the words a glide may mean are read while the finger goes on: every 100 ms the strip changed faster
 * than its words fade, and flickered.
 */
val PREVIEW_EVERY = 250.milliseconds

/** What follows a glide over the letters: the words it may mean [preview]ed as it goes, and it [glided] whole. */
interface GlideListener {
    fun preview(glide: Glide)

    fun glided(glide: Glide)
}

/** What a point not drawn yet has for its time: the next frame stamps it. */
private const val UNSTAMPED = -1L

/** A point of the trail: where it is in the window, and when, by the frame clock, it was first drawn. */
class TrailPoint(val at: Offset, val time: Long)

/**
 * The line a glide draws over the keys: the points where the finger went, each fading as it ages, and the whole
 * path, kept for reading the word once the finger lifts. Times come from the frame clock, through [frame].
 */
class GlideTrail {
    /** The points still showing, the oldest first. */
    val points = mutableStateListOf<TrailPoint>()

    /** The time of the frame drawn last. */
    var now by mutableLongStateOf(0L)
        private set

    /** How many glides began: a new one starts the frames that fade the trail. */
    var glides by mutableIntStateOf(0)
        private set

    /** Whether a finger is gliding now. */
    var gliding = false
        private set

    private val path = ArrayList<Offset>()

    /** Whether there is anything to draw, or a glide to follow. */
    val showing: Boolean get() = gliding || points.isNotEmpty()

    /** A glide begins at [at], in the window, where the finger came down. */
    fun start(at: Offset) {
        gliding = true
        points.clear()
        path.clear()
        glides++
        move(at)
    }

    /** The finger moved to [to], in the window. */
    fun move(to: Offset) {
        if (!gliding) return
        path += to
        points += TrailPoint(to, UNSTAMPED)
    }

    /** The way the finger went so far, while it glides. */
    fun pathSoFar(): List<Offset> = path.toList()

    /** The finger lifted: the trail fades on, and the whole path it went is returned. */
    fun lift(): List<Offset> {
        gliding = false
        return path.toList()
    }

    /** The glide was canceled: the trail fades on, and nothing is read from it. */
    fun cancel() {
        gliding = false
    }

    /** A frame at [time]: points new since the last one get its time, and the faded ones go. */
    fun frame(time: Long) {
        now = time
        for (index in points.indices.reversed()) {
            if (points[index].time != UNSTAMPED) break
            points[index] = TrailPoint(points[index].at, time)
        }
        while (points.isNotEmpty() && trailFade(time - points.first().time) == 0f) points.removeAt(0)
    }
}

/** How much of a trail point [age] milliseconds old still shows: fully at first, nothing after [TRAIL_FADE_MS]. */
fun trailFade(age: Long): Float = (1f - age.toFloat() / TRAIL_FADE_MS).coerceIn(0f, 1f)

/**
 * Where [at], a point in the window, lies on the keys that fill [keys] in the window, laid out in [rows] rows
 * [rowWidth] key widths wide: in key widths across and rows down, as the glide decoder reads it.
 */
fun keyPointOf(at: Offset, keys: Rect, rowWidth: Float, rows: Int): KeyPoint =
    KeyPoint((at.x - keys.left) / keys.width * rowWidth, (at.y - keys.top) / keys.height * rows)

/** How tall a row of the keys that fill [keys], [rows] rows [rowWidth] key widths wide, is, in key widths. */
fun rowHeightOf(keys: Rect, rowWidth: Float, rows: Int): Float = keys.height / rows / (keys.width / rowWidth)

/** How old [point] is at [now], by the frame clock; a point no frame drew yet is new. */
fun ageOf(point: TrailPoint, now: Long): Long = if (point.time == UNSTAMPED) 0 else now - point.time

/**
 * The glide along [path], points in the window, over [layout]'s keys, which fill [keys] in the window, as the
 * glide decoder reads it.
 */
fun glideOf(path: List<Offset>, keys: Rect, layout: KeyboardLayout): Glide {
    val rows = layout.rows.size
    val points = path.map { keyPointOf(it, keys, layout.rowWidth, rows) }
    return Glide(points, rowHeightOf(keys, layout.rowWidth, rows))
}
