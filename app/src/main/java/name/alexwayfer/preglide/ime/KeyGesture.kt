package name.alexwayfer.preglide.ime

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.KeyTap
import name.alexwayfer.preglide.engine.layout.keyTapAt

internal const val LONG_PRESS_MS = 350L

/** What a key shows while touched: whether it is pressed, and the long press popup with the option under the finger. */
internal class KeyTouch {
    var pressed by mutableStateOf(false)

    /** Where the long press popup's options stand; null while it is closed. */
    var popup by mutableStateOf<OptionsLayout?>(null)
    var selected by mutableIntStateOf(0)

    /** How far the finger pulls from the selected cell's middle toward a neighbor, in cells. */
    var pull by mutableFloatStateOf(0f)

    /** Whether the space bar's long press moves the cursor now, which the bar's arrows show. */
    var cursorMode by mutableStateOf(false)
}

/** The [trail] a glide that starts on a key draws, and what gets the finger's whole path, in the window, [onLift]. */
internal class GlideStart(val trail: GlideTrail, val onLift: (List<Offset>) -> Unit)

/**
 * What a touch of a key or an emoji's cell works on: the [touch] it shows, the [geometry] that places it in the
 * window and lays out its long press popup, and the [haptics] it is felt by.
 */
internal class TouchParts(val touch: KeyTouch, val geometry: () -> KeyGeometry, val haptics: KeyHaptics)

/**
 * What backspace and the space bar do besides their keys: [backspace], in the field or the emoji search, and the
 * bar [space] gives, if any; and the [menu] a key's long press offers, if any. Each is read once the finger comes
 * down, not as the touch before it ended, so a change meanwhile, as the search closing or a gesture changed in the
 * settings, counts from the very next touch.
 */
internal class SpecialKeys(
    val backspace: () -> Backspace,
    val space: () -> SpaceBar? = { null },
    val menu: () -> KeyMenu? = { null },
)

/**
 * Runs one touch of a key with [action], on its [parts]: a tap, a long press, a held backspace, a swipe from it, or a
 * choice from the long press popup. [onKey] gets the action, whether it was a long press, and for a tap, where it
 * landed. [special] tells what backspace and the space bar do. With [glide], a finger that leaves the key before the
 * long press glides instead (see [glideDistance]). With a space bar, the bar does its gestures, and with [glide], a
 * finger that goes up from the bar glides (see [handleSpace]).
 */
internal suspend fun AwaitPointerEventScope.handleKeyGesture(
    action: KeyAction,
    parts: TouchParts,
    onKey: (KeyAction, Boolean, KeyTap?) -> Unit,
    special: SpecialKeys,
    glide: GlideStart? = null,
) {
    val touch = parts.touch
    val down = awaitFirstDown()
    touch.pressed = true
    parts.haptics.press()
    val bar = special.space()
    val menu = special.menu()
    try {
        if (action == KeyAction.Backspace) {
            handleBackspace(down, parts.haptics, { onKey(action, false, null) }, special.backspace())
        } else if (action == KeyAction.Space && (bar != null || glide != null)) {
            val glides = handleSpace(down, parts, onKey, bar, glides = glide != null)
            if (glides && glide != null) glideFrom(down, glide, touch, parts.geometry)
        } else {
            val press = if (glide != null) awaitPressOrGlide(down, size.width) else awaitPress()
            when (press) {
                // Where the finger came down: it aims then, and may roll on before it lifts.
                Press.Tap -> onKey(action, false, keyTapAt(down.position.x, down.position.y, size.width, size.height))
                Press.Cancelled -> Unit
                Press.Hold -> holdKey(action, down.id, parts, onKey, menu)
                // Only a key with a glide to start reads a press as one.
                Press.Glide -> glide?.let { glideFrom(down, it, touch, parts.geometry) }
            }
        }
    } finally {
        touch.pressed = false
        touch.popup = null
        touch.cursorMode = false
    }
}

/** Glides on from the finger [down] on the key [touch] shows, which [geometry] places in the window, along [glide]. */
private suspend fun AwaitPointerEventScope.glideFrom(
    down: PointerInputChange,
    glide: GlideStart,
    touch: KeyTouch,
    geometry: () -> KeyGeometry,
) {
    // The finger types no key now, but a word along its way.
    touch.pressed = false
    val origin = touchOrigin(geometry().bounds, size)
    followGlide(down, glide) { point -> origin + point }
}

/**
 * A long press on the key's [parts]: the popup when the key has options, where nothing is typed until the finger
 * lifts on one; the popup of the [menu], on its first item, which does the item lifted on; or else the key's own
 * long press action.
 */
private suspend fun AwaitPointerEventScope.holdKey(
    action: KeyAction,
    pointer: PointerId,
    parts: TouchParts,
    onKey: (KeyAction, Boolean, KeyTap?) -> Unit,
    menu: KeyMenu?,
) {
    val choices = (action as? KeyAction.Letter)?.options.orEmpty()
    when {
        choices.isNotEmpty() -> {
            val chosen = chooseOption(pointer, choices.size, PRESELECTED_OPTION, parts)
            if (chosen != null) onKey(KeyAction.Letter(choices[chosen]), false, null)
        }
        menu != null -> {
            val chosen = chooseOption(pointer, menu.items.size, preselected = 0, parts)
            if (chosen != null) menu.choose(menu.items[chosen])
        }
        else -> {
            onKey(action, true, null)
            waitForUpOrCancellation()
        }
    }
}

/**
 * Opens the popup of [count] options of the key or cell of [parts] for the finger [pointer], laid out by its
 * geometry with [preselected] over the key, and follows the finger through it, each move to another option felt.
 * Returns the option it lifted on, or null when the gesture ended otherwise. The popup stays open until the
 * caller's touch ends.
 */
internal suspend fun AwaitPointerEventScope.chooseOption(
    pointer: PointerId,
    count: Int,
    preselected: Int,
    parts: TouchParts,
): Int? {
    val touch = parts.touch
    val haptics = parts.haptics
    val geometry = parts.geometry()
    val (roomLeft, roomRight) = geometry.room
    val layout = arrangeOptions(count, roomLeft, roomRight, preselected)
    touch.popup = layout
    touch.selected = preselected
    touch.pull = 0f
    // The key press haptic: the popup opening is felt like a press, stronger than the ticks of moving through it.
    haptics.press()
    return trackOption(pointer, layout, geometry, preselected) { option, pull ->
        if (option != touch.selected) {
            touch.selected = option
            haptics.tick()
        }
        touch.pull = pull
    }
}

/**
 * Follows [pointer] while the popup is open, from the [start] option, and reports the option under the finger and
 * the finger's pull from its middle to [onMove]. Returns the option the finger lifted on, or null when the gesture
 * ended otherwise.
 */
private suspend fun AwaitPointerEventScope.trackOption(
    pointer: PointerId,
    layout: OptionsLayout,
    geometry: KeyGeometry,
    start: Int,
    onMove: (option: Int, pull: Float) -> Unit,
): Int? {
    var option = start
    while (true) {
        val change = awaitFinger(pointer) ?: return null
        if (!change.pressed) return option
        val column = fingerColumn(change.position.x, size.width.toFloat(), geometry.cellSize.width)
        option = optionAt(column, layout)
        onMove(option, pullFrom(option, column, layout))
        change.consume()
    }
}

internal enum class Press { Tap, Hold, Cancelled, Glide }

/** How far the finger must go from where it came down for a glide, in widths of the key it came down on. */
private const val GLIDE_START = 0.5f

/**
 * How far, in pixels, a finger must go from where it came down on a key [keyWidth] pixels wide to glide: half a key
 * away, farther than a tap rolls.
 */
internal fun glideDistance(keyWidth: Int): Float = keyWidth * GLIDE_START

/** Whether a finger that came down at [from] and is now at [to] went farther than [distance] pixels. */
internal fun movedPast(from: Offset, to: Offset, distance: Float): Boolean = (to - from).getDistance() > distance

/**
 * Waits for the finger [down] on a key [keyWidth] pixels wide to lift within the long press time, to stay down past
 * it, or to glide away (see [glideDistance]).
 */
private suspend fun AwaitPointerEventScope.awaitPressOrGlide(down: PointerInputChange, keyWidth: Int): Press =
    withTimeoutOrNull(LONG_PRESS_MS) { awaitTapOrGlide(down, glideDistance(keyWidth)) } ?: Press.Hold

/**
 * Waits for the finger [down] to lift, or to glide away, farther than [distance] pixels: half the key's width for
 * a key's glide (see [glideDistance]).
 */
internal suspend fun AwaitPointerEventScope.awaitTapOrGlide(down: PointerInputChange, distance: Float): Press {
    var press: Press? = null
    while (press == null) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
        press = when {
            change == null || !change.pressed && change.isConsumed -> Press.Cancelled
            !change.pressed -> Press.Tap
            movedPast(down.position, change.position, distance) -> Press.Glide
            else -> null
        }
    }
    return press
}

/**
 * Follows the gliding finger [down] until it lifts, drawing its way on [glide]'s trail, each point placed in the
 * window by [toWindow], and hands the whole way over when it lifts; a canceled glide types nothing.
 */
private suspend fun AwaitPointerEventScope.followGlide(
    down: PointerInputChange,
    glide: GlideStart,
    toWindow: (Offset) -> Offset,
) {
    val trail = glide.trail
    trail.start(toWindow(down.position))
    var lifted = false
    try {
        while (!lifted) {
            val change = awaitFinger(down.id) ?: break
            // The points between events too, so a fast finger draws a smooth line.
            for (point in change.historical) trail.move(toWindow(point.position))
            trail.move(toWindow(change.position))
            change.consume()
            lifted = !change.pressed
        }
    } finally {
        if (!lifted) trail.cancel()
    }
    if (lifted) glide.onLift(trail.lift())
}

/**
 * Waits for the finger to lift within the long press time, to stay down past it, or to leave the key, or to be
 * taken by a scroll of what holds the key.
 */
internal suspend fun AwaitPointerEventScope.awaitPress(): Press {
    // True when released, false when the gesture was canceled, null when the wait timed out.
    val released = withTimeoutOrNull(LONG_PRESS_MS) { waitForUpOrCancellation() != null }
    return when (released) {
        true -> Press.Tap
        false -> Press.Cancelled
        null -> Press.Hold
    }
}
