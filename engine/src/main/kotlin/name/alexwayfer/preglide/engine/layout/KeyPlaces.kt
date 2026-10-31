package name.alexwayfer.preglide.engine.layout

private const val MIDDLE = 0.5f

/** A point on a keyboard: [x] in key widths from its left edge, [y] in rows from its top. */
data class KeyPoint(val x: Float, val y: Float)

/** Where a key lies on its keyboard: from [left] for [width], in key widths, on [row], counted from the top. */
data class KeyPlace(val left: Float, val width: Float, val row: Int) {
    val center: KeyPoint = pointAt(MIDDLE, MIDDLE)

    /** The point [across] the key's width and [down] its height, each from 0 at its left or top edge to 1. */
    fun pointAt(across: Float, down: Float): KeyPoint = KeyPoint(left + across * width, row + down)

    /**
     * Whether [other] shares an edge with this key, or is this key: beside it on its row, or above or below it on the
     * row next to it. A key that touches only a corner is not next to it.
     */
    fun isNextTo(other: KeyPlace): Boolean = when (other.row - row) {
        0 -> other.left <= right + EDGE_TOLERANCE && left <= other.right + EDGE_TOLERANCE
        -1, 1 -> other.left < right - EDGE_TOLERANCE && left < other.right - EDGE_TOLERANCE
        else -> false
    }

    private val right: Float get() = left + width
}

/** How far apart, in key widths, two edges may lie and still be one, as the sums of narrower keys' widths drift. */
private const val EDGE_TOLERANCE = 0.001f

/**
 * A tap on a key: where it landed, [across] the key's width and [down] its height, each from 0 at its left or top
 * edge to 1, and how large the key is on screen, in pixels, which tells how tall a row is next to a key width.
 */
data class KeyTap(val across: Float, val down: Float, val width: Int, val height: Int)

/**
 * A tap [x] and [y] pixels from the top left corner of a key [width] by [height] pixels large. A point past an edge
 * counts at the edge; a tap on a key not laid out yet, with no size, at its middle.
 */
fun keyTapAt(x: Float, y: Float, width: Int, height: Int): KeyTap =
    KeyTap(fractionOf(x, width), fractionOf(y, height), width, height)

private fun fractionOf(offset: Float, length: Int): Float =
    if (length > 0) (offset / length).coerceIn(0f, 1f) else MIDDLE

/** How tall a row of keys is, in key widths, by [tap] on a key that lies at [place]. */
fun rowHeightOf(tap: KeyTap, place: KeyPlace): Float = tap.height * place.width / tap.width

/** Where each key that types a char lies among [rows], by that char; the first key wins where two type one char. */
internal fun placesOf(rows: List<List<Key>>): Map<Char, KeyPlace> {
    val places = HashMap<Char, KeyPlace>()
    rows.forEachIndexed { row, keys ->
        var left = 0f
        for ((action, width) in keys) {
            if (action is KeyAction.Letter) places.putIfAbsent(action.char, KeyPlace(left, width, row))
            left += width
        }
    }
    return places
}

/**
 * Where a glide finds each letter among [rows]: at its own key, and a letter without one, typed from another key's
 * long press as ъ from ь, at that key, which the finger glides over for it.
 */
internal fun glidePlacesOf(rows: List<List<Key>>): Map<Char, KeyPlace> {
    val places = HashMap(placesOf(rows))
    for ((char, alternates) in rows.flatten().map { it.action }.filterIsInstance<KeyAction.Letter>()) {
        val place = places[char] ?: continue
        for (alternate in alternates.filter { it.isLetter() }) places.putIfAbsent(alternate, place)
    }
    return places
}
