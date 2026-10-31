package name.alexwayfer.preglide.ime

import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.unit.dp
import name.alexwayfer.preglide.engine.edit.wordsSwiped

private const val REPEAT_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 50L

/** How far a swipe from backspace goes for each word after the first, which it selects as it starts. */
private val WORD_STEP = 32.dp

/**
 * Backspace, which waits to see what the finger [down] does before it deletes: a tap deletes a char as it lifts,
 * [delete] does; a finger held still deletes chars on and on, even when it slides off the key then; one that leaves
 * the key first selects words back from the cursor in the field, as [backspace] does, and erases them as it lifts.
 */
internal suspend fun AwaitPointerEventScope.handleBackspace(
    down: PointerInputChange,
    haptics: KeyHaptics,
    delete: () -> Unit,
    backspace: Backspace,
) {
    val press = withTimeoutOrNull(REPEAT_DELAY_MS) { awaitTapOrGlide(down, glideDistance(size.width)) } ?: Press.Hold
    when (press) {
        Press.Tap -> delete()
        Press.Cancelled -> Unit
        Press.Hold -> repeatWhileHeld(down.id) {
            // Each deleted char is felt, but a held key with nothing left to delete stays still.
            if (backspace.canDelete()) haptics.tick()
            delete()
        }
        Press.Glide -> swipeWords(down, haptics, backspace)
    }
}

/**
 * Follows the finger [down] that left backspace: it selects the word before the cursor as it starts to the left,
 * one more at each [WORD_STEP] on, and fewer as it comes back, each change felt. The words go as it lifts; back
 * where it started, or canceled, it erases nothing.
 */
private suspend fun AwaitPointerEventScope.swipeWords(
    down: PointerInputChange,
    haptics: KeyHaptics,
    backspace: Backspace,
) {
    val words = backspace.startSwipe()
    val first = glideDistance(size.width)
    val step = WORD_STEP.toPx()
    var selected = 0
    var lifted = false
    try {
        while (!lifted) {
            val change = awaitFinger(down.id) ?: break
            change.consume()
            lifted = !change.pressed
            val now = wordsSwiped(down.position.x - change.position.x, first, step).coerceAtMost(words)
            if (now != selected) {
                selected = now
                backspace.select(now)
                haptics.tick()
            }
        }
    } finally {
        backspace.endSwipe(lifted)
    }
}

/**
 * The next event of the finger [pointer], or null once it is gone or its touch canceled, which comes as a lift
 * already consumed.
 */
internal suspend fun AwaitPointerEventScope.awaitFinger(pointer: PointerId): PointerInputChange? =
    awaitPointerEvent().changes.firstOrNull { it.id == pointer }?.takeUnless { !it.pressed && it.isConsumed }

/** Calls [onRepeat] at once, then at a steady pace while the finger [pointer] stays down, wherever it moves. */
private suspend fun AwaitPointerEventScope.repeatWhileHeld(pointer: PointerId, onRepeat: () -> Unit) {
    // Null only when the wait timed out with the finger still down.
    do {
        onRepeat()
    } while (withTimeoutOrNull(REPEAT_INTERVAL_MS) { awaitRelease(pointer) } == null)
}

/**
 * Waits for the finger [pointer] to lift. Unlike [waitForUpOrCancellation], leaving the key does not end the
 * wait: the key keeps getting the finger's events until it lifts.
 */
internal suspend fun AwaitPointerEventScope.awaitRelease(pointer: PointerId) {
    do {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer }
    } while (change != null && change.pressed)
}
