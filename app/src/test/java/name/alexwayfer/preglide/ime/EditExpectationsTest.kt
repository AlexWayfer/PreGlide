package name.alexwayfer.preglide.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditExpectationsTest {
    private val expectations = EditExpectations()
    private val letter = SuggestionEdit(0, "а")
    private val backspace = SuggestionEdit(1, "")

    @Test
    fun anEditReportedWhereExpectedIsTheKeyboards() {
        expectations.expect(letter, 5, 5)

        assertTrue(expectations.reported(6))
    }

    @Test
    fun fastEditsEachFollowOnFromTheOneBefore() {
        // Two letters and a backspace before the field reports the first.
        expectations.expect(letter, 5, 5)
        expectations.expect(letter, 5, 5)
        expectations.expect(backspace, 5, 5)

        assertTrue(expectations.reported(6))
        assertTrue(expectations.reported(7))
        assertTrue(expectations.reported(6))
    }

    @Test
    fun theCursorIsWhereTheEditsStillExpectedLeaveIt() {
        assertNull(expectations.cursor)
        expectations.expect(letter, 5, 5)
        expectations.expect(letter, 5, 5)
        assertEquals(7, expectations.cursor)

        expectations.reported(6)
        assertEquals(7, expectations.cursor)
        expectations.reported(7)
        assertNull(expectations.cursor)
    }

    @Test
    fun aFieldMayReportSeveralEditsAtOnce() {
        expectations.expect(letter, 5, 5)
        expectations.expect(letter, 5, 5)

        assertTrue(expectations.reported(7))
        assertFalse(expectations.reported(7))
    }

    @Test
    fun aReportElsewhereIsACursorMoveAndForgetsTheRest() {
        expectations.expect(letter, 5, 5)
        expectations.expect(letter, 5, 5)

        assertFalse(expectations.reported(0))
        assertFalse(expectations.reported(6))
    }

    @Test
    fun clearingForgetsWhatWasExpected() {
        expectations.expect(letter, 5, 5)
        expectations.clear()

        assertFalse(expectations.reported(6))
    }
}
