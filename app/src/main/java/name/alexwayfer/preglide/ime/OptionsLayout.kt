package name.alexwayfer.preglide.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A long press popup preselects the option right after the key's own char: the digit or symbol its corner shows,
 * or else its first alternate.
 */
internal const val PRESELECTED_OPTION = 1

private const val HALF = 0.5f

/** Where a key is in the window, which is what the long press popup is laid out from; [popupPadding] in pixels. */
internal data class KeyGeometry(val bounds: Rect, val windowWidth: Int, val popupPadding: Int) {
    /**
     * A popup cell: as tall as the key and at least as wide as tall, so a char has as much room at its sides
     * as above and below it. Never narrower than the key, so the finger starts on the cell over it.
     */
    val cellSize: IntSize
        get() = IntSize(max(bounds.width, bounds.height).roundToInt(), bounds.height.roundToInt())

    /** Whole cells that fit between the cell over the key and each window edge, the padding aside: left, right. */
    val room: Pair<Int, Int>
        get() {
            val cellWidth = cellSize.width.coerceAtLeast(1)
            val center = bounds.center.x
            val left = ((center - cellWidth * HALF - popupPadding) / cellWidth).toInt()
            val right = ((windowWidth - center - cellWidth * HALF - popupPadding) / cellWidth).toInt()
            return left.coerceAtLeast(0) to right.coerceAtLeast(0)
        }
}

/**
 * Where the long press options stand: [columns] holds each option's column, 0 right over the key, negative to
 * its left. The columns run without gaps.
 */
internal data class OptionsLayout(val columns: List<Int>) {
    val leftmost: Int get() = columns.min()
    val width: Int get() = columns.max() - leftmost + 1

    /** The option in [cell], counting from the popup's left edge; a cell past either end gives the end one. */
    fun optionInCell(cell: Int): Int = columns.indexOf(cell.coerceIn(0, width - 1) + leftmost)

    /** The popup cell, counting from its left edge, that shows [option]. */
    fun cellOf(option: Int): Int = columns[option] - leftmost
}

/**
 * Lays [count] options out around the key: the [preselected] one right over it, the ones after it to the right as
 * far as [roomRight] cells allow, the key's own char and anything before it to the left within [roomLeft], and
 * what does not fit on its side on the other one, rightward first. On each side the nearer cell gets the earlier
 * option. So every option stays within the screen, where the finger can reach it.
 */
internal fun arrangeOptions(
    count: Int,
    roomLeft: Int,
    roomRight: Int,
    preselected: Int = PRESELECTED_OPTION,
): OptionsLayout {
    val after = (preselected + 1 until count).toList()
    val before = (preselected - 1 downTo 0).toList()
    val right = after.take(roomRight).toMutableList()
    val left = before.take(roomLeft).toMutableList()
    for (option in after.drop(right.size) + before.drop(left.size)) {
        when {
            right.size < roomRight -> right += option
            left.size < roomLeft -> left += option
            // No room on either side: the popup shifts to stay on screen.
            else -> right += option
        }
    }
    val columns = IntArray(count)
    right.forEachIndexed { index, option -> columns[option] = index + 1 }
    left.forEachIndexed { index, option -> columns[option] = -(index + 1) }
    return OptionsLayout(columns.toList())
}

/**
 * Where the finger at [x], from the left of the area [areaWidth] wide that takes the key's touches, is among the
 * popup's columns: 0 over the key's middle, which the area shares, fractional between the middles of two cells.
 */
internal fun fingerColumn(x: Float, areaWidth: Float, cellWidth: Int): Float =
    (x - areaWidth * HALF) / cellWidth.coerceAtLeast(1)

/**
 * Where the area [areaSize] big that takes a key's touches starts in the window: it shares its middle with the key,
 * whose [keyBounds] in the window lie inset in it.
 */
internal fun touchOrigin(keyBounds: Rect, areaSize: IntSize): Offset =
    keyBounds.center - Offset(areaSize.width * HALF, areaSize.height * HALF)

/** The option in the popup cell under the finger at [column]; past either end, the end one. */
internal fun optionAt(column: Float, layout: OptionsLayout): Int =
    layout.optionInCell(column.roundToInt() - layout.leftmost)

/**
 * How far the finger at [column] pulls away from the middle of [option]'s cell toward a neighbor, in cells:
 * negative to the left, at most half a cell, which is where the next cell begins. An end option has no neighbor
 * on its outer side, so nothing pulls that way.
 */
internal fun pullFrom(option: Int, column: Float, layout: OptionsLayout): Float {
    val own = layout.columns[option]
    val leftmost = if (own == layout.leftmost) 0f else -HALF
    val rightmost = if (own == layout.leftmost + layout.width - 1) 0f else HALF
    return (column - own).coerceIn(leftmost, rightmost)
}

/** From the popup's left edge to the middle of the cell over the key: the padding and the cells on the left. */
internal fun keyCenterInPopup(layout: OptionsLayout, cellWidth: Int, padding: Int): Int =
    padding + ((-layout.leftmost + HALF) * cellWidth).roundToInt()

/**
 * The popup's left edge in the window: the cell over the key centered on [key], with [keyCenterOffset] from
 * [keyCenterInPopup], shifted inward only when the popup could not fit otherwise.
 */
internal fun popupLeft(key: IntRect, keyCenterOffset: Int, popupWidth: Int, windowWidth: Int): Int =
    (key.center.x - keyCenterOffset).coerceIn(0, (windowWidth - popupWidth).coerceAtLeast(0))
