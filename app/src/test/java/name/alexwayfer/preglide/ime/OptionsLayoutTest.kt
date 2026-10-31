package name.alexwayfer.preglide.ime

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionsLayoutTest {
    /** The options' chars as the popup shows them, left to right, with the preselected one in brackets. */
    private fun row(chars: String, layout: OptionsLayout): String = (0 until layout.width).joinToString(" ") { cell ->
        val option = layout.optionInCell(cell)
        if (option == PRESELECTED_OPTION) "[${chars[option]}]" else chars[option].toString()
    }

    @Test
    fun withRoomTheKeysOwnCharIsLeftAndTheRestRight() {
        assertEquals(". […] , ; ? !", row(".…,;?!", arrangeOptions(count = 6, roomLeft = 5, roomRight = 5)))
    }

    @Test
    fun nearTheRightEdgeWhatDoesNotFitGoesLeftNearestFirst() {
        assertEquals("! ? ; . […] ,", row(".…,;?!", arrangeOptions(count = 6, roomLeft = 9, roomRight = 1)))
    }

    @Test
    fun atTheRightEdgeEverythingElseIsLeft() {
        assertEquals("! ? ; , . […]", row(".…,;?!", arrangeOptions(count = 6, roomLeft = 9, roomRight = 0)))
    }

    @Test
    fun atTheLeftEdgeTheKeysOwnCharMovesRight() {
        assertEquals("[≈] ~", row("~≈", arrangeOptions(count = 2, roomLeft = 0, roomRight = 9)))
    }

    @Test
    fun withoutRoomOnEitherSideTheRestGoesRight() {
        assertEquals(". […] , ;", row(".…,;", arrangeOptions(count = 4, roomLeft = 1, roomRight = 0)))
    }

    @Test
    fun aFirstOptionPreselectedPutsTheRestRight() {
        val layout = arrangeOptions(count = 6, roomLeft = 5, roomRight = 5, preselected = 0)

        assertEquals(listOf(0, 1, 2, 3, 4, 5), layout.columns)
    }

    @Test
    fun aLaterOptionPreselectedStandsOverTheKeyWithTheEarlierLeft() {
        val layout = arrangeOptions(count = 6, roomLeft = 5, roomRight = 5, preselected = 3)

        assertEquals(listOf(-3, -2, -1, 0, 1, 2), layout.columns)
    }

    @Test
    fun columnsRunWithoutGaps() {
        val layout = arrangeOptions(count = 6, roomLeft = 2, roomRight = 1)

        assertEquals((layout.leftmost until layout.leftmost + layout.width).toSet(), layout.columns.toSet())
    }

    private val threeOptions = arrangeOptions(count = 3, roomLeft = 5, roomRight = 5)

    /** The option under a finger at [x] on an 80 wide key with 100 wide cells. */
    private fun optionUnderFinger(x: Float) = optionAt(fingerColumn(x, areaWidth = 80f, cellWidth = 100), threeOptions)

    private fun pullAt(x: Float): Float {
        val column = fingerColumn(x, areaWidth = 80f, cellWidth = 100)
        return pullFrom(optionAt(column, threeOptions), column, threeOptions)
    }

    @Test
    fun fingerOnTheKeyIsOnThePreselectedOption() {
        assertEquals(PRESELECTED_OPTION, optionUnderFinger(x = 10f))
        assertEquals(PRESELECTED_OPTION, optionUnderFinger(x = 70f))
    }

    @Test
    fun fingerAboveACellChoosesItsOption() {
        assertEquals(2, optionUnderFinger(x = 140f))
        assertEquals(0, optionUnderFinger(x = -60f))
    }

    @Test
    fun fingerPastThePopupStaysOnItsEndOptions() {
        assertEquals(2, optionUnderFinger(x = 900f))
        assertEquals(0, optionUnderFinger(x = -900f))
    }

    @Test
    fun fingerInTheMiddleOfItsCellDoesNotPull() {
        assertEquals(0f, pullAt(x = 40f), 0.001f)
    }

    @Test
    fun fingerPullsTowardTheNeighborItHeadsTo() {
        assertEquals(0.3f, pullAt(x = 70f), 0.001f)
        assertEquals(-0.3f, pullAt(x = 10f), 0.001f)
    }

    @Test
    fun fingerJustPastTheBoundaryPullsBackFromTheNewCell() {
        // 91 is past the boundary at 90, so the finger is on the next option, pulling back toward the first.
        assertEquals(2, optionUnderFinger(x = 91f))
        assertEquals(-0.49f, pullAt(x = 91f), 0.001f)
    }

    @Test
    fun endOptionsDoNotPullOutward() {
        assertEquals(0f, pullAt(x = 900f), 0.001f)
        assertEquals(0f, pullAt(x = -900f), 0.001f)
    }

    @Test
    fun endOptionsStillPullTowardTheirNeighbor() {
        // 120 is on the right end option, a fifth of a cell short of its middle at 140.
        assertEquals(-0.2f, pullAt(x = 120f), 0.001f)
    }

    @Test
    fun theAreaThatTakesAKeysTouchesStartsOutsideTheKeyItSharesItsMiddleWith() {
        // An 80 by 100 key inset by 4 and 6 in its cell.
        val key = Rect(left = 810f, top = 20f, right = 890f, bottom = 120f)

        assertEquals(Offset(806f, 14f), touchOrigin(key, IntSize(88, 112)))
        assertEquals(key.topLeft, touchOrigin(key, IntSize(80, 100)))
    }

    @Test
    fun roomCountsWholeCellsBesideTheCellOverTheKey() {
        // A 100 wide cell centered at 850 leaves 800 on the left and 180 on the right of a 1080 window.
        val key = Rect(left = 810f, top = 0f, right = 890f, bottom = 100f)

        assertEquals(8 to 1, KeyGeometry(key, windowWidth = 1080, popupPadding = 0).room)
    }

    @Test
    fun roomLeavesSpaceForThePopupPadding() {
        val key = Rect(left = 810f, top = 0f, right = 890f, bottom = 100f)

        assertEquals(7 to 1, KeyGeometry(key, windowWidth = 1080, popupPadding = 10).room)
    }

    @Test
    fun keyAtTheEdgeHasNoRoomOnThatSide() {
        val key = Rect(left = 0f, top = 0f, right = 80f, bottom = 100f)

        assertEquals(0, KeyGeometry(key, windowWidth = 1080, popupPadding = 0).room.first)
    }

    @Test
    fun keyCenterInPopupCountsThePaddingAndTheCellsOnTheLeft() {
        // 10 of padding, one cell on the left and half the cell over the key.
        assertEquals(160, keyCenterInPopup(threeOptions, cellWidth = 100, padding = 10))
    }

    @Test
    fun popupCentersTheCellOverTheKey() {
        val key = IntRect(left = 500, top = 0, right = 580, bottom = 100)

        assertEquals(380, popupLeft(key, keyCenterOffset = 160, popupWidth = 320, windowWidth = 1080))
    }

    @Test
    fun popupThatCannotFitShiftsInward() {
        val key = IntRect(left = 0, top = 0, right = 80, bottom = 100)

        assertEquals(0, popupLeft(key, keyCenterOffset = 160, popupWidth = 420, windowWidth = 1080))
        assertEquals(0, popupLeft(key, keyCenterOffset = 160, popupWidth = 2000, windowWidth = 1080))
    }
}
