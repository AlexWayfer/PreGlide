package name.alexwayfer.preglide.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Where trying the keyboard is: from the button to the field above the open keyboard, through turning the keyboard
 * on and choosing it when it is not yet. The steps that send the user to the system ([Enabling], [Choosing]) wait
 * first for the screen to lose its focus, then for it to come back, and read the outcome then.
 */
internal enum class TryStep {
    Closed,

    /** The system keyboard list is asked to open, to turn the keyboard on. */
    Enabling,

    /** The user is in the system keyboard list. */
    EnablingAway,

    /** The system keyboard picker is asked to open, to choose the keyboard. */
    Choosing,

    /** The picker is open. */
    ChoosingAway,

    /** The field is out and asks for the keyboard, which is still coming. */
    Opening,

    /** The field is out over the keyboard. */
    Open,

    /**
     * The field stays out while one keyboard goes down and the one the user switched to comes up: any keyboard, so
     * this one can be compared with others.
     */
    Switching,
}

/**
 * What the steps follow: whether the keyboard is [enabled] and [selected], the screen [focused], a keyboard up, and
 * whether the chosen keyboard is [switched] from the one last up.
 */
internal data class TrySignals(
    val enabled: Boolean,
    val selected: Boolean,
    val focused: Boolean,
    val imeVisible: Boolean,
    val switched: Boolean = false,
)

/**
 * Where the button leads, by [status]: null while the keyboard is off, which the user is asked to turn on first;
 * to the picker while another keyboard is chosen; to the field when the keyboard is ready.
 */
internal fun startTry(status: KeyboardStatus): TryStep? = when {
    !status.enabled -> null
    !status.selected -> TryStep.Choosing
    else -> TryStep.Opening
}

/** The step after [step] given [signals]; the same step while nothing it waits for has happened. */
internal fun nextTryStep(step: TryStep, signals: TrySignals): TryStep = when (step) {
    TryStep.Closed -> step
    TryStep.Enabling, TryStep.EnablingAway -> nextEnablingStep(step, signals)
    TryStep.Choosing, TryStep.ChoosingAway -> nextChoosingStep(step, signals)
    TryStep.Opening, TryStep.Open, TryStep.Switching -> nextFieldStep(step, signals)
}

/**
 * Off to the system keyboard list and back: on to choosing the keyboard, or to the field, or nowhere if it is
 * still off.
 */
private fun nextEnablingStep(step: TryStep, signals: TrySignals): TryStep = when {
    step == TryStep.Enabling -> if (signals.focused) step else TryStep.EnablingAway
    !signals.focused -> step
    !signals.enabled -> TryStep.Closed
    !signals.selected -> TryStep.Choosing
    else -> TryStep.Opening
}

/**
 * The picker: the field once the keyboard is chosen, even where the picker takes no focus; nothing when the screen
 * gets its focus back with another keyboard chosen.
 */
private fun nextChoosingStep(step: TryStep, signals: TrySignals): TryStep = when {
    signals.selected -> TryStep.Opening
    !signals.focused -> TryStep.ChoosingAway
    step == TryStep.Choosing -> step
    else -> TryStep.Closed
}

/**
 * The field: open once a keyboard is up, and gone with it when it goes down, by back or by its own key; but kept while
 * it goes down because the user switched to another, until that one comes up.
 */
private fun nextFieldStep(step: TryStep, signals: TrySignals): TryStep = when {
    signals.imeVisible -> TryStep.Open
    step == TryStep.Opening || step == TryStep.Switching -> step
    signals.switched -> TryStep.Switching
    else -> TryStep.Closed
}

/**
 * The step after [step] as the screen stops showing, as the display turns off or the user leaves the app: the field
 * closes, and the user taps the button again on coming back. Once the display has slept, the system brings the
 * screen back with the field still focused but without the keyboard, which then never comes. The steps that send
 * the user to the system's keyboard list stop the screen too, and stay.
 */
internal fun stepOnStop(step: TryStep): TryStep = if (fieldOut(step)) TryStep.Closed else step

/** Whether the field is out at [step]: while it waits for the keyboard and over it. */
internal fun fieldOut(step: TryStep): Boolean =
    step == TryStep.Opening || step == TryStep.Open || step == TryStep.Switching

/**
 * Whether back closes the field at [step]: all the while it is out, and not only while the keyboard is coming. With
 * the keyboard up, back reaches the keyboard or the screen as they last set their callbacks on the window, and the
 * screen sets its own again and again; either way the field and the keyboard go, and the page stays.
 */
internal fun backClosesField(step: TryStep): Boolean = fieldOut(step)

/** Whether the button shows at [step]: everywhere but where the field is out. */
internal fun showsTryButton(step: TryStep): Boolean = !fieldOut(step)

/**
 * The room a page needs below its last row to scroll it out from under what floats over its bottom: the button's
 * [buttonRoom] above the system bars' [barsBottom], or the field's [fieldHeight] above the keyboard's
 * [keyboardHeight], whichever reaches higher. The keyboard's height follows it as it slides, so the room grows and
 * shrinks with it, and none is left once it is down.
 */
internal fun pageBottom(barsBottom: Dp, keyboardHeight: Dp, fieldHeight: Dp, buttonRoom: Dp): Dp =
    maxOf(barsBottom + buttonRoom, keyboardHeight + fieldHeight)

/** [padding] with [bottom] below instead of its own. */
internal fun withBottom(padding: PaddingValues, bottom: Dp, direction: LayoutDirection): PaddingValues = PaddingValues(
    start = padding.calculateStartPadding(direction),
    top = padding.calculateTopPadding(),
    end = padding.calculateEndPadding(direction),
    bottom = bottom,
)
