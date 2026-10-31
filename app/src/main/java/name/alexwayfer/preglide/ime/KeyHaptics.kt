package name.alexwayfer.preglide.ime

import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/** The strength that leaves the vibration to the system, as before the user could pick one. */
const val SYSTEM_STRENGTH = 0

/** The strengths a user picks from: the system's, then the keyboard's own, from the weakest to the strongest. */
val VibrationStrengths = SYSTEM_STRENGTH..10

/** Whether the keys vibrate, and how strong: [SYSTEM_STRENGTH] or one of the keyboard's own [VibrationStrengths]. */
data class Vibration(val on: Boolean, val strength: Int) {
    companion object {
        /** The system's haptics, which the keyboard had before the setting. */
        val System = Vibration(on = true, strength = SYSTEM_STRENGTH)
    }
}

/** The vibration the keyboard and its screen follow; the system's until the keyboard provides the user's. */
val LocalVibration = compositionLocalOf { Vibration.System }

/** What the user feels, as the system's haptic [constant] for it names it. */
enum class Haptic(val constant: Int) {
    /** A key pressed. */
    Press(HapticFeedbackConstants.KEYBOARD_PRESS),

    /** A step of a held or sliding finger: a char deleted, the cursor moved, another option. */
    Tick(HapticFeedbackConstants.CLOCK_TICK),

    /** A long press on a suggestion or a clip. */
    LongPress(HapticFeedbackConstants.LONG_PRESS),
}

/** One vibration of the keyboard's own: how long, and how strong from 1 to 255, or the motor's one strength. */
data class Buzz(val millis: Long, val amplitude: Int)

/** What a [Haptic] does: nothing, the system's haptic, or a [Buzz] of the keyboard's own. */
sealed interface Feedback {
    data object None : Feedback

    data class System(val haptic: Haptic) : Feedback

    data class Own(val buzz: Buzz) : Feedback
}

/**
 * What [haptic] does with [vibration]. [amplitudeControl] is whether the motor can vary its strength, and null when
 * there is no motor to drive, where the system's haptic is all there is.
 */
fun feedback(haptic: Haptic, vibration: Vibration, amplitudeControl: Boolean?): Feedback = when {
    !vibration.on -> Feedback.None
    vibration.strength == SYSTEM_STRENGTH || amplitudeControl == null -> Feedback.System(haptic)
    else -> Feedback.Own(buzz(haptic, vibration.strength, amplitudeControl))
}

// A press grows both longer and stronger along the scale; a motor of one strength can only grow longer, and further.
private const val PRESS_SHORTEST_MS = 6f
private const val PRESS_LONGEST_MS = 20f
private const val FIXED_PRESS_SHORTEST_MS = 3f
private const val FIXED_PRESS_LONGEST_MS = 25f
private const val WEAKEST_AMPLITUDE = 40f
private const val STRONGEST_AMPLITUDE = 255f

// A tick is shorter and weaker than a press, so a run of them does not blur into a buzz; a long press is longer.
private const val TICK_SHARE = 0.5f
private const val TICK_AMPLITUDE_SHARE = 0.6f
private const val SHORTEST_TICK_MS = 2L
private const val LONG_PRESS_TIMES = 2

/** The keyboard's own vibration for [haptic] at [strength], one of [VibrationStrengths] above the system's. */
fun buzz(haptic: Haptic, strength: Int, amplitudeControl: Boolean): Buzz {
    // From 0 at the weakest of the keyboard's own to 1 at the strongest.
    val share = (strength - SYSTEM_STRENGTH - 1).toFloat() / (VibrationStrengths.last - SYSTEM_STRENGTH - 1)
    val press = if (amplitudeControl) {
        Buzz(
            lerp(PRESS_SHORTEST_MS, PRESS_LONGEST_MS, share).toLong(),
            lerp(WEAKEST_AMPLITUDE, STRONGEST_AMPLITUDE, share),
        )
    } else {
        Buzz(lerp(FIXED_PRESS_SHORTEST_MS, FIXED_PRESS_LONGEST_MS, share).toLong(), VibrationEffect.DEFAULT_AMPLITUDE)
    }
    return when (haptic) {
        Haptic.Press -> press
        Haptic.Tick -> Buzz(
            (press.millis * TICK_SHARE).toLong().coerceAtLeast(SHORTEST_TICK_MS),
            if (amplitudeControl) (press.amplitude * TICK_AMPLITUDE_SHARE).roundToInt() else press.amplitude,
        )
        Haptic.LongPress -> press.copy(millis = press.millis * LONG_PRESS_TIMES)
    }
}

private fun lerp(from: Float, to: Float, share: Float): Int = (from + (to - from) * share).roundToInt()

/**
 * The keyboard's haptics as [vibration] says: the system's through [view], so they follow the user's touch feedback
 * setting, or the keyboard's own through [vibrator], which the system gives as touch feedback too. The vibration is
 * read at each haptic, so a gesture that runs on feels a new setting at once.
 */
class KeyHaptics(private val view: View, private val vibrator: Vibrator?, private val vibration: () -> Vibration) {
    fun press() = feel(Haptic.Press)

    /** A lighter haptic than the key press, so repeats and popup moves do not blur into a buzz. */
    fun tick() = feel(Haptic.Tick)

    fun longPress() = feel(Haptic.LongPress)

    private fun feel(haptic: Haptic) {
        val motor = vibrator?.takeIf { it.hasVibrator() }
        when (val feedback = feedback(haptic, vibration(), motor?.hasAmplitudeControl())) {
            Feedback.None -> Unit
            is Feedback.System -> view.performHapticFeedback(feedback.haptic.constant)
            is Feedback.Own -> motor?.vibrate(feedback.buzz)
        }
    }
}

private fun Vibrator.vibrate(buzz: Buzz) {
    val effect = VibrationEffect.createOneShot(buzz.millis, buzz.amplitude)
    // Touch feedback, so the system's touch vibration setting turns it down or off; before Android 13 it cannot say.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
    } else {
        vibrate(effect)
    }
}

/** The keyboard's haptics in this view, with the [LocalVibration] the user set. */
@Composable
fun rememberKeyHaptics(): KeyHaptics {
    val view = LocalView.current
    val vibration by rememberUpdatedState(LocalVibration.current)
    return remember(view) { KeyHaptics(view, view.context.getSystemService(Vibrator::class.java)) { vibration } }
}
