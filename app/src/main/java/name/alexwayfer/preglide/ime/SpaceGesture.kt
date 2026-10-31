package name.alexwayfer.preglide.ime

import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.keyTapAt

/**
 * How far the finger goes from where it came down on the space bar before it swipes rather than types: far enough
 * that a glide toward a letter far to a side, which came down at the bar's top edge, leaves the bar first.
 */
private val CURSOR_START = 24.dp

/** How far a swipe from the space bar goes across for each char the cursor moves. */
private val CHAR_STEP = 10.dp

/**
 * The space bar, which does the [space] bar's gestures, if any, with the finger [down]: a slide to a side from where
 * it came down switches the language or moves the cursor in the field, and a long press may move the cursor too,
 * across by chars, and then up and down, by lines, one line for each key's height; the bar's touch of [parts] shows
 * the cursor moving either way. A tap types a space as it lifts, and a long press that moves
 * no cursor does the bar's long press; [onKey] gets either, as it does any key's. Returns whether the finger went
 * above the bar first and, as the bar [glides], glides on from there.
 */
internal suspend fun AwaitPointerEventScope.handleSpace(
    down: PointerInputChange,
    parts: TouchParts,
    onKey: (KeyAction, Boolean, KeyTap?) -> Unit,
    space: SpaceBar?,
    glides: Boolean,
): Boolean {
    val touch = parts.touch
    val haptics = parts.haptics
    val swipe = space?.swipe
    // Where the finger was last, so a long press moves the cursor from where it is held, and a swipe goes its way.
    var at = down.position
    val slide = withTimeoutOrNull(LONG_PRESS_MS) {
        awaitSpaceSlide(down, CURSOR_START.toPx(), swipes = swipe != null, glides) { at = it }
    } ?: SpaceSlide.Hold
    when (slide) {
        SpaceSlide.Tap -> {
            val tap = keyTapAt(down.position.x, down.position.y, size.width, size.height)
            onKey(KeyAction.Space, false, tap)
        }
        SpaceSlide.Cancelled, SpaceSlide.Glide -> Unit
        SpaceSlide.Hold -> holdSpace(down.id, at, parts, onKey, space?.holdCursor)
        SpaceSlide.Swipe -> when (swipe) {
            SpaceSwipe.Cursor -> space.cursor?.let {
                // The bar shows the cursor moving at once, as after a long press.
                touch.cursorMode = true
                moveCursor(down.id, down.position, haptics, it)
            }
            SpaceSwipe.Language -> swipeLanguage(down.id, swipesForward(at.x - down.position.x), haptics, space)
            null -> Unit
        }
    }
    return slide == SpaceSlide.Glide
}

/**
 * Waits for the finger [down] to lift, or to show what else it does (see [spaceSlideOf]): to slide to a side farther
 * than [start] pixels when the bar [swipes], or to leave the bar, upward to glide when it [glides]. [onMove] hears
 * where the finger goes, before it tells what the finger does.
 */
private suspend fun AwaitPointerEventScope.awaitSpaceSlide(
    down: PointerInputChange,
    start: Float,
    swipes: Boolean,
    glides: Boolean,
    onMove: (Offset) -> Unit,
): SpaceSlide {
    var slide: SpaceSlide? = null
    while (slide == null) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        slide = when {
            change == null || !change.pressed && change.isConsumed -> SpaceSlide.Cancelled
            !change.pressed -> SpaceSlide.Tap
            else -> {
                onMove(change.position)
                val way = change.position - down.position
                spaceSlideOf(way, barPlaceOf(change), start, swipes, glides)
            }
        }
    }
    return slide
}

/**
 * A long press of the space bar by the finger [pointer], held [at] a place: with a [cursor], the bar's touch of
 * [parts] shows its arrows, felt as a long press, and the cursor moves after the finger from there until it lifts;
 * without, or when the finger lifts where it was held, [onKey] gets the bar's long press.
 */
private suspend fun AwaitPointerEventScope.holdSpace(
    pointer: PointerId,
    at: Offset,
    parts: TouchParts,
    onKey: (KeyAction, Boolean, KeyTap?) -> Unit,
    cursor: Cursor?,
) {
    if (cursor == null) {
        onKey(KeyAction.Space, true, null)
        waitForUpOrCancellation()
        return
    }
    parts.touch.cursorMode = true
    parts.haptics.longPress()
    // A long press held in place and let go: the bar's own long press, the keyboard picker, which some phones show
    // no button for.
    if (moveCursor(pointer, at, parts.haptics, cursor)) onKey(KeyAction.Space, true, null)
}

/**
 * A swipe along the space bar that switches [space]'s language [forward] or back, once, felt when there was another
 * language; the finger [pointer] then switches nothing more until it lifts.
 */
private suspend fun AwaitPointerEventScope.swipeLanguage(
    pointer: PointerId,
    forward: Boolean,
    haptics: KeyHaptics,
    space: SpaceBar,
) {
    if (space.languages.switch(forward)) haptics.press()
    awaitRelease(pointer)
}

/** Where the finger of [change] is against the space bar. */
private fun AwaitPointerEventScope.barPlaceOf(change: PointerInputChange): BarPlace = when {
    change.position.y < 0 -> BarPlace.Above
    change.isOutOfBounds(size, extendedTouchPadding) -> BarPlace.Off
    else -> BarPlace.On
}

/**
 * Follows the finger [pointer] on the space bar until it lifts, and moves [cursor] after it, from where it was
 * [from], each move felt; a cursor stopped at the field's end is not. The cursor then hears how far it went in all.
 * Returns whether the finger lifted without going a step, whatever the cursor did; a canceled touch did not lift.
 */
private suspend fun AwaitPointerEventScope.moveCursor(
    pointer: PointerId,
    from: Offset,
    haptics: KeyHaptics,
    cursor: Cursor,
): Boolean {
    val charStep = CHAR_STEP.toPx()
    val lineStep = size.height.toFloat()
    var at = CursorSteps(0, 0)
    var moved = CursorSteps(0, 0)
    var stepped = false
    var lifted = false
    while (!lifted) {
        val change = awaitFinger(pointer) ?: break
        change.consume()
        lifted = !change.pressed
        val way = change.position - from
        val now = cursorSteps(way.x, way.y, charStep, lineStep)
        // Every move is tried, so a cursor stopped at one end still goes back as soon as the finger does.
        val made = cursorMoves(at, now).filter { cursor.move(it) }
        at = now
        stepped = stepped || now != CursorSteps(0, 0)
        moved = made.fold(moved, CursorSteps::plus)
        if (made.isNotEmpty()) haptics.tick()
    }
    cursor.slid(moved.chars, moved.lines)
    return lifted && !stepped
}
