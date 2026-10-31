package name.alexwayfer.preglide.settings

import name.alexwayfer.preglide.ime.SYSTEM_STRENGTH
import name.alexwayfer.preglide.ime.Vibration
import name.alexwayfer.preglide.ime.VibrationStrengths
import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationSettingsTest {
    @Test
    fun nothingStoredIsTheSystemsVibration() {
        assertEquals(Vibration.System, vibration(null, null))
    }

    @Test
    fun aStoredVibrationIsKept() {
        assertEquals(Vibration(on = false, strength = 4), vibration(false, 4))
    }

    @Test
    fun aStrengthOffTheScaleIsTakenAsItsNearestEnd() {
        assertEquals(VibrationStrengths.last, vibration(true, 99).strength)
        assertEquals(SYSTEM_STRENGTH, vibration(true, -3).strength)
    }

    @Test
    fun theStrongestIsAHundredPercent() {
        assertEquals(10, vibrationPercent(1))
        assertEquals(100, vibrationPercent(VibrationStrengths.last))
    }
}
