package name.alexwayfer.preglide.ime

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftRulesTest {
    private val lines = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE

    @Test
    fun aNewLineWantsACapitalWhereTheFieldAsksForCapitalsOfAnyKind() {
        assertEquals(true, wantsCapitalOnNewLine(lines or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertEquals(true, wantsCapitalOnNewLine(lines or InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        assertEquals(true, wantsCapitalOnNewLine(lines or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS))
        assertEquals(false, wantsCapitalOnNewLine(lines))
    }

    @Test
    fun shiftFollowsWhatTheFieldWantsButCapsLockStays() {
        assertEquals(ShiftState.Once, shiftFor(true, ShiftState.Off))
        assertEquals(ShiftState.Off, shiftFor(false, ShiftState.Once))
        assertEquals(ShiftState.Locked, shiftFor(false, ShiftState.Locked))
        assertEquals(ShiftState.Once, shiftFor(null, ShiftState.Once))
    }

    @Test
    fun aFieldOfOneLineTakesNoNewLine() {
        assertNull(wantsCapitalOnNewLine(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
    }

    @Test
    fun aCapitalThatBeganAWordIsErasedBackToItsStart() {
        assertTrue(erasesWordCapital(" М", 1))
        assertTrue(erasesWordCapital("М", 0))
        assertTrue(erasesWordCapital("город Москва", 6))
        assertTrue(erasesWordCapital("«М", 1))
        assertTrue(erasesWordCapital("-Й", 1))
    }

    @Test
    fun aLowercaseLetterOrACapitalInsideAWordIsNot() {
        assertFalse(erasesWordCapital(" м", 1))
        assertFalse(erasesWordCapital("ОС", 1))
        assertFalse(erasesWordCapital("1А", 1))
        assertFalse(erasesWordCapital("а ", 1))
        assertFalse(erasesWordCapital("", 0))
    }

    @Test
    fun anErasedCapitalTurnsShiftOnForOneLetterButKeepsCapsLock() {
        assertEquals(ShiftState.Once, shiftAfterCapitalErased(ShiftState.Off))
        assertEquals(ShiftState.Once, shiftAfterCapitalErased(ShiftState.Once))
        assertEquals(ShiftState.Locked, shiftAfterCapitalErased(ShiftState.Locked))
    }
}
