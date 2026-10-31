package name.alexwayfer.preglide.ime

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId

/** An emoji's cell to touch, its [parts], and what types its emoji, [onType], with a tone or none. */
internal class EmojiCellTouch(val parts: TouchParts, val onType: (EmojiCell, Int?) -> Unit)

/**
 * Runs one touch of [cell]: a tap types it. A long press on an emoji that takes skin tones opens the popup of its
 * forms, the one the cell shows over it, and the form the finger lifts on is typed, its tone kept for the next tap;
 * on one that takes none, it types as it lifts, as a slow tap. A touch that the panel takes for a scroll before the
 * long press types nothing.
 */
internal suspend fun AwaitPointerEventScope.handleEmojiGesture(cell: EmojiCell, on: EmojiCellTouch) {
    val down = awaitFirstDown()
    val touch = on.parts.touch
    touch.pressed = true
    try {
        when (awaitPress()) {
            Press.Tap -> typeFelt(cell, on)
            Press.Hold -> holdEmoji(cell, down.id, on)
            Press.Cancelled, Press.Glide -> Unit
        }
    } finally {
        touch.pressed = false
        touch.popup = null
    }
}

private suspend fun AwaitPointerEventScope.holdEmoji(cell: EmojiCell, pointer: PointerId, on: EmojiCellTouch) {
    if (cell.emoji.tones.isEmpty()) {
        if (waitForUpOrCancellation() != null) typeFelt(cell, on)
        return
    }
    val chosen = chooseOption(pointer, cell.emoji.forms.size, cell.tone, on.parts)
    // The popup opening was felt; the choice is typed as the finger lifts.
    if (chosen != null) on.onType(cell, chosen)
}

/** Types [cell] as it shows, felt as a key press is. */
private fun typeFelt(cell: EmojiCell, on: EmojiCellTouch) {
    on.parts.haptics.press()
    on.onType(cell, null)
}
