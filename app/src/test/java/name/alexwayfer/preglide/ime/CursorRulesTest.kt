package name.alexwayfer.preglide.ime

import android.text.InputType
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorRulesTest {
    @Test
    fun theCursorStartsOnlyFromASlideToASide() {
        assertFalse(startsCursorSlide(16f, 0f, start = 16f))
        assertTrue(startsCursorSlide(17f, 0f, start = 16f))
        assertTrue(startsCursorSlide(-17f, 10f, start = 16f))
        // Up or down more than across: aimed at a letter above, or at nothing.
        assertFalse(startsCursorSlide(17f, -20f, start = 16f))
        assertFalse(startsCursorSlide(0f, -40f, start = 16f))
    }

    @Test
    fun aFingerThatGoesAboveTheBarBeforeTheCursorStartsGlides() {
        // More across than up, as toward a letter far to a side, but out of the bar's top first.
        val across = Offset(20f, -8f)
        assertEquals(SpaceSlide.Glide, spaceSlideOf(across, BarPlace.Above, 24f, swipes = true, glides = true))
        // Even past the start: it left the bar upward before this event told.
        assertEquals(
            SpaceSlide.Glide,
            spaceSlideOf(Offset(30f, -8f), BarPlace.Above, 24f, swipes = true, glides = true),
        )
        assertEquals(SpaceSlide.Cancelled, spaceSlideOf(across, BarPlace.Above, 24f, swipes = true, glides = false))
    }

    @Test
    fun aFingerThatStaysOnTheBarSwipesOnlyPastTheStart() {
        val pastStart = Offset(25f, -8f)
        assertNull(spaceSlideOf(Offset(20f, -8f), BarPlace.On, 24f, swipes = true, glides = true))
        assertEquals(SpaceSlide.Swipe, spaceSlideOf(pastStart, BarPlace.On, 24f, swipes = true, glides = true))
        // Without a swipe to do, the finger may still lift on the bar and type a space.
        assertNull(spaceSlideOf(pastStart, BarPlace.On, 24f, swipes = false, glides = true))
    }

    @Test
    fun aFingerThatLeavesTheBarOtherwiseTypesNothing() {
        val down = Offset(0f, 30f)
        val across = Offset(30f, 0f)
        assertEquals(SpaceSlide.Cancelled, spaceSlideOf(down, BarPlace.Off, 24f, swipes = true, glides = true))
        assertEquals(SpaceSlide.Cancelled, spaceSlideOf(across, BarPlace.Off, 24f, swipes = false, glides = true))
    }

    @Test
    fun aMoveAddsAStepItsWay() {
        assertEquals(CursorSteps(-1, 0), CursorSteps(0, 0) + CursorMove.Left)
        assertEquals(CursorSteps(1, 0), CursorSteps(0, 0) + CursorMove.Right)
        assertEquals(CursorSteps(2, -1), CursorSteps(2, 0) + CursorMove.Up)
        assertEquals(CursorSteps(2, 1), CursorSteps(2, 0) + CursorMove.Down)
    }

    @Test
    fun theFieldsCursorLogsHowFarASwipeTookIt() {
        val lines = mutableListOf<String>()
        val cursor = FieldCursor(Field(InputType.TYPE_CLASS_TEXT, "ab", ""), { lines += it }) {}

        cursor.slid(-3, 1)

        assertEquals(listOf("Space swipe moved the cursor -3 chars, 1 lines"), lines)
    }

    @Test
    fun stepsCountWholeCharsAndLinesTowardZero() {
        assertEquals(CursorSteps(0, 0), cursorSteps(9f, 49f, charStep = 10f, lineStep = 50f))
        assertEquals(CursorSteps(2, 1), cursorSteps(25f, 50f, charStep = 10f, lineStep = 50f))
        assertEquals(CursorSteps(-1, -2), cursorSteps(-19f, -100f, charStep = 10f, lineStep = 50f))
    }

    @Test
    fun movesGoTheLinesFirstThenTheChars() {
        assertEquals(
            listOf(CursorMove.Up, CursorMove.Right, CursorMove.Right),
            cursorMoves(CursorSteps(0, 0), CursorSteps(2, -1)),
        )
    }

    @Test
    fun comingBackMovesTheOtherWay() {
        assertEquals(
            listOf(CursorMove.Down, CursorMove.Left, CursorMove.Left, CursorMove.Left),
            cursorMoves(CursorSteps(2, -1), CursorSteps(-1, 0)),
        )
        assertEquals(emptyList<CursorMove>(), cursorMoves(CursorSteps(3, 1), CursorSteps(3, 1)))
    }

    @Test
    fun theCursorGoesAcrossOnlyWhereTextIs() {
        assertFalse(cursorCanMove(CursorMove.Left, "", "a", multiLine = false))
        assertTrue(cursorCanMove(CursorMove.Left, "a", "", multiLine = false))
        assertFalse(cursorCanMove(CursorMove.Right, "a", "", multiLine = false))
        assertTrue(cursorCanMove(CursorMove.Right, "", "a", multiLine = false))
        // A field that cannot tell gets no arrow key, which could move the focus away.
        assertFalse(cursorCanMove(CursorMove.Left, null, null, multiLine = true))
    }

    @Test
    fun theCursorGoesUpAndDownOnlyInAFieldOfLines() {
        assertFalse(cursorCanMove(CursorMove.Up, "a", "a", multiLine = false))
        assertFalse(cursorCanMove(CursorMove.Down, "a", "a", multiLine = false))
        assertTrue(cursorCanMove(CursorMove.Up, "a", "", multiLine = true))
        assertFalse(cursorCanMove(CursorMove.Up, "", "a", multiLine = true))
        assertTrue(cursorCanMove(CursorMove.Down, "", "a", multiLine = true))
        assertFalse(cursorCanMove(CursorMove.Down, "a", "", multiLine = true))
    }

    private class Field(override val inputType: Int, val before: String, val after: String) : SuggestionField {
        override val imeOptions = 0

        override fun textBeforeCursor(length: Int) = before.takeLast(length)

        override fun textAfterCursor(length: Int) = after.take(length)

        override fun replace(deleteBefore: Int, text: String, deleteAfter: Int) = Unit
    }

    @Test
    fun theFieldsCursorSendsTheArrowKeyOnlyWhenItMoves() {
        val sent = mutableListOf<Int>()
        val cursor = FieldCursor(Field(InputType.TYPE_CLASS_TEXT, "ab", "")) { sent += it }

        assertTrue(cursor.move(CursorMove.Left))
        assertFalse(cursor.move(CursorMove.Right))
        assertFalse(cursor.move(CursorMove.Up))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_LEFT), sent)
    }

    @Test
    fun aFieldOfLinesTakesUpAndDown() {
        val sent = mutableListOf<Int>()
        val lines = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val cursor = FieldCursor(Field(lines, "a\nb", "c")) { sent += it }

        assertTrue(cursor.move(CursorMove.Up))
        assertTrue(cursor.move(CursorMove.Down))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN), sent)
    }

    @Test
    fun aNumberFieldTakesNoLinesWhateverItsFlags() {
        val number = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val cursor = FieldCursor(Field(number, "1", "2")) {}

        assertFalse(cursor.move(CursorMove.Up))
    }
}
