package name.alexwayfer.preglide.ime

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class KeyHapticsTest {
    private val strongest = VibrationStrengths.last
    private val weakest = SYSTEM_STRENGTH + 1

    @Test
    fun vibrationOffFeelsNothing() {
        Haptic.entries.forEach { haptic ->
            assertEquals(Feedback.None, feedback(haptic, Vibration(on = false, strength = strongest), true))
        }
    }

    @Test
    fun theSystemStrengthLeavesTheHapticToTheSystem() {
        assertEquals(Feedback.System(Haptic.Press), feedback(Haptic.Press, Vibration.System, true))
        assertEquals(Feedback.System(Haptic.Tick), feedback(Haptic.Tick, Vibration.System, false))
    }

    @Test
    fun withoutAMotorAnOwnStrengthFallsBackToTheSystem() {
        assertEquals(Feedback.System(Haptic.Press), feedback(Haptic.Press, Vibration(true, strongest), null))
    }

    @Test
    fun anOwnStrengthBuzzesOnItsOwn() {
        val feedback = feedback(Haptic.Press, Vibration(true, strongest), true)
        assertEquals(Feedback.Own(buzz(Haptic.Press, strongest, true)), feedback)
    }

    @Test
    fun aPressGrowsLongerAndStrongerAlongTheScale() {
        assertEquals(Buzz(6, 40), buzz(Haptic.Press, weakest, true))
        assertEquals(Buzz(20, 255), buzz(Haptic.Press, strongest, true))
    }

    @Test
    fun aMotorOfOneStrengthOnlyGrowsLonger() {
        assertEquals(Buzz(3, VibrationEffect.DEFAULT_AMPLITUDE), buzz(Haptic.Press, weakest, false))
        assertEquals(Buzz(25, VibrationEffect.DEFAULT_AMPLITUDE), buzz(Haptic.Press, strongest, false))
    }

    @Test
    fun aTickIsShorterAndWeakerThanAPress() {
        assertEquals(Buzz(10, 153), buzz(Haptic.Tick, strongest, true))
        assertEquals(Buzz(12, VibrationEffect.DEFAULT_AMPLITUDE), buzz(Haptic.Tick, strongest, false))
    }

    @Test
    fun aTickStaysLongEnoughToFeel() {
        assertEquals(2L, buzz(Haptic.Tick, weakest, false).millis)
    }

    @Test
    fun aLongPressIsLongerThanAPress() {
        assertEquals(Buzz(40, 255), buzz(Haptic.LongPress, strongest, true))
    }

    @Test
    fun anOwnStrengthDrivesTheMotor() {
        val vibrator = vibrator()
        KeyHaptics(view(), vibrator) { Vibration(true, strongest) }.longPress()
        assertTrue(shadowOf(vibrator).isVibrating)
        assertEquals(40L, shadowOf(vibrator).milliseconds)
    }

    @Test
    fun theSystemStrengthLeavesTheMotorToTheSystem() {
        val vibrator = vibrator()
        val view = view()
        KeyHaptics(view, vibrator) { Vibration.System }.press()
        assertFalse(shadowOf(vibrator).isVibrating)
        assertEquals(HapticFeedbackConstants.KEYBOARD_PRESS, shadowOf(view).lastHapticFeedbackPerformed())
    }

    @Test
    fun vibrationOffKeepsAllStill() {
        val vibrator = vibrator()
        val view = view()
        KeyHaptics(view, vibrator) { Vibration(false, strongest) }.tick()
        assertFalse(shadowOf(vibrator).isVibrating)
        assertNotEquals(HapticFeedbackConstants.CLOCK_TICK, shadowOf(view).lastHapticFeedbackPerformed())
    }

    private fun view() = View(ApplicationProvider.getApplicationContext())

    private fun vibrator(): Vibrator =
        ApplicationProvider.getApplicationContext<Application>().getSystemService(Vibrator::class.java).also {
            shadowOf(it).setHasAmplitudeControl(true)
        }
}
