package name.alexwayfer.preglide.ime

import android.text.InputType
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** One step of the cursor in the field, sent as the arrow key with [keyCode]. */
enum class CursorMove(val keyCode: Int) {
    Left(KeyEvent.KEYCODE_DPAD_LEFT),
    Right(KeyEvent.KEYCODE_DPAD_RIGHT),
    Up(KeyEvent.KEYCODE_DPAD_UP),
    Down(KeyEvent.KEYCODE_DPAD_DOWN),
}

/** What a swipe from the space bar moves in the field. */
fun interface Cursor {
    /** Moves the cursor one step [move]. Returns whether it moved, so a cursor at the field's end is not felt. */
    fun move(move: CursorMove): Boolean

    /** Hears that a swipe ended, which moved the cursor [chars] right and [lines] down; negative the other way. */
    fun slid(chars: Int, lines: Int) = Unit
}

/**
 * Whether a finger that went [dx] to the right and [dy] down from where it came down on the space bar swipes along
 * it: farther than [start] pixels to a side, and more to the side than up or down. A finger that goes up first more
 * likely aimed at the letter above and glides from it; once the cursor moves, it goes up and down too.
 */
fun startsCursorSlide(dx: Float, dy: Float, start: Float): Boolean = abs(dx) > start && abs(dx) > abs(dy)

/** Where a finger that came down on the space bar is: still [On] it, [Above] its top edge, or [Off] it otherwise. */
enum class BarPlace { On, Above, Off }

/** What a touch of the space bar turns out to be: a [Swipe] along it moves the cursor or switches the language. */
enum class SpaceSlide { Tap, Hold, Cancelled, Swipe, Glide }

/**
 * What a finger still down, [way] from where it came down on the space bar and now at [place], turns out to be, or
 * null while that is not known yet. Once it goes above the bar before a swipe starts,
 * it aimed at a letter of the row above and glides from it, when the bar [glides]: a glide toward a letter far to a
 * side goes more across than up, so a swipe would start from its first [start] pixels alone. Otherwise it swipes
 * when the bar [swipes] (see [startsCursorSlide]), and leaving the bar types nothing.
 */
fun spaceSlideOf(way: Offset, place: BarPlace, start: Float, swipes: Boolean, glides: Boolean) =
    when {
        place == BarPlace.Above -> if (glides) SpaceSlide.Glide else SpaceSlide.Cancelled
        swipes && startsCursorSlide(way.x, way.y, start) -> SpaceSlide.Swipe
        place == BarPlace.Off -> SpaceSlide.Cancelled
        else -> null
    }

/** The line of the typing log on a swipe from the space bar that moved the cursor [chars] across and [lines] down. */
fun cursorSlideLine(chars: Int, lines: Int): String = "Space swipe moved the cursor $chars chars, $lines lines"

/** How far a swipe from the space bar has gone, in chars to the right and lines down; negative the other way. */
data class CursorSteps(val chars: Int, val lines: Int)

/**
 * The steps of a finger [dx] to the right and [dy] down from where it came down on the space bar: one char for each
 * [charStep] pixels across, one line for each [lineStep] pixels up or down, counted toward zero.
 */
fun cursorSteps(dx: Float, dy: Float, charStep: Float, lineStep: Float): CursorSteps =
    CursorSteps((dx / charStep).toInt(), (dy / lineStep).toInt())

/** These steps and one more, [move]. */
operator fun CursorSteps.plus(move: CursorMove): CursorSteps = when (move) {
    CursorMove.Left -> copy(chars = chars - 1)
    CursorMove.Right -> copy(chars = chars + 1)
    CursorMove.Up -> copy(lines = lines - 1)
    CursorMove.Down -> copy(lines = lines + 1)
}

/** The moves that take the cursor [from] one place of a swipe [to] another: the lines first, then the chars. */
fun cursorMoves(from: CursorSteps, to: CursorSteps): List<CursorMove> {
    val lines = to.lines - from.lines
    val chars = to.chars - from.chars
    return List(abs(lines)) { if (lines < 0) CursorMove.Up else CursorMove.Down } +
        List(abs(chars)) { if (chars < 0) CursorMove.Left else CursorMove.Right }
}

/** The cursor of [field], which [send] moves by sending an arrow key's code, each swipe's moves written to [log]. */
class FieldCursor(
    private val field: SuggestionField,
    private val log: (String) -> Unit = {},
    private val send: (Int) -> Unit,
) : Cursor {
    override fun slid(chars: Int, lines: Int) = log(cursorSlideLine(chars, lines))

    override fun move(move: CursorMove): Boolean {
        val type = field.inputType
        val multiLine = (type and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (type and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        val moves = cursorCanMove(move, field.textBeforeCursor(1), field.textAfterCursor(1), multiLine)
        if (moves) send(move.keyCode)
        return moves
    }
}

/**
 * Whether the arrow key of [move] moves the cursor in a field with [before], the char before it, and [after], the
 * char after it, and only then may it go: an arrow key that cannot move the cursor moves the focus to another view
 * instead. Up and down go only in a [multiLine] field, where the top line's up goes to the text's start and the
 * last line's down to its end.
 */
fun cursorCanMove(move: CursorMove, before: CharSequence?, after: CharSequence?, multiLine: Boolean): Boolean =
    when (move) {
        CursorMove.Left -> !before.isNullOrEmpty()
        CursorMove.Right -> !after.isNullOrEmpty()
        CursorMove.Up -> multiLine && !before.isNullOrEmpty()
        CursorMove.Down -> multiLine && !after.isNullOrEmpty()
    }
