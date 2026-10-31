package name.alexwayfer.preglide.ime

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import name.alexwayfer.preglide.engine.layout.KeyAction
import name.alexwayfer.preglide.engine.layout.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionsPopupTest {
    @Test
    fun anIndicatorOnItsCellCoversItWholeAndNoOther() {
        assertEquals(1f, cellCoverage(2, 2f to 3f), 0f)
        assertEquals(0f, cellCoverage(1, 2f to 3f), 0f)
        assertEquals(0f, cellCoverage(3, 2f to 3f), 0f)
    }

    @Test
    fun aPulledIndicatorCoversItsCellAndTheNeighborInPart() {
        assertEquals(0.75f, cellCoverage(2, 2.25f to 3.25f), 0.001f)
        assertEquals(0.25f, cellCoverage(3, 2.25f to 3.25f), 0.001f)
    }

    @Test
    fun indicatorAtRestIsACircleInTheMiddleOfItsCell() {
        val bounds = indicatorBounds(left = 2f, right = 3f, cellSize = IntSize(100, 100))

        assertEquals(bounds.width, bounds.height, 0.001f)
        assertEquals(250f, bounds.center.x, 0.001f)
        assertEquals(50f, bounds.center.y, 0.001f)
    }

    @Test
    fun stretchedIndicatorIsLonger() {
        val atRest = indicatorBounds(left = 0f, right = 1f, cellSize = IntSize(100, 100))
        val stretched = indicatorBounds(left = 0f, right = 1.15f, cellSize = IntSize(100, 100))

        assertEquals(15f, stretched.width - atRest.width, 0.001f)
        assertEquals(atRest.height, stretched.height, 0.001f)
    }

    @Test
    fun indicatorStaysRoundInAWideCell() {
        val bounds = indicatorBounds(left = 0f, right = 1f, cellSize = IntSize(160, 100))

        assertEquals(bounds.width, bounds.height, 0.001f)
        assertEquals(80f, bounds.center.x, 0.001f)
    }

    private fun assertPair(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 0.001f)
        assertEquals(expected.second, actual.second, 0.001f)
    }

    @Test
    fun indicatorWithoutAPullCoversItsCell() {
        assertPair(2f to 3f, indicatorTargets(cell = 2, pull = 0f))
    }

    @Test
    fun indicatorMovesTowardThePull() {
        assertPair(2.05f to 3.05f, indicatorTargets(cell = 2, pull = 0.5f))
        assertPair(1.95f to 2.95f, indicatorTargets(cell = 2, pull = -0.5f))
        assertPair(2.02f to 3.02f, indicatorTargets(cell = 2, pull = 0.2f))
    }

    @Test
    fun pulledIndicatorKeepsItsSize() {
        for (pull in listOf(-0.5f, -0.2f, 0.3f, 0.5f)) {
            val (left, right) = indicatorTargets(cell = 2, pull = pull)

            assertEquals(1f, right - left, 0.001f)
        }
    }

    @Test
    fun crossingABoundaryShiftsBothEdgesAlike() {
        // The finger just before and just after the boundary between cells 2 and 3.
        val (beforeLeft, beforeRight) = indicatorTargets(cell = 2, pull = 0.5f)
        val (afterLeft, afterRight) = indicatorTargets(cell = 3, pull = -0.5f)

        assertEquals(afterLeft - beforeLeft, afterRight - beforeRight, 0.001f)
        assertEquals(beforeRight - beforeLeft, afterRight - afterLeft, 0.001f)
    }

    @Test
    fun edgesNeverMoveInwardWhileTheFingerCrossesForward() {
        val before = indicatorTargets(cell = 2, pull = 0.5f)
        val after = indicatorTargets(cell = 3, pull = -0.5f)

        assertTrue(after.first > before.first)
        assertTrue(after.second > before.second)
    }

    @Test
    fun popupCenteredOverItsKeyStandsRightAboveIt() {
        val position = OptionsPopupPosition(keyCenterOffset = 150).calculatePosition(
            anchorBounds = IntRect(left = 500, top = 1800, right = 600, bottom = 1900),
            windowSize = IntSize(1080, 2400),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(300, 100),
        )

        assertEquals(IntOffset(400, 1700), position)
    }

    @Test
    fun aKeysOptionsShowAsItTypesThem() {
        val state = KeyboardState().apply { shift = ShiftState.Once }

        assertEquals(listOf("Е", "Ё"), optionLabels(KeyAction.Letter('е', listOf('ё')), state))
        assertEquals(emptyList<String>(), optionLabels(KeyAction.Space, state))
    }

    @Test
    fun aKeyWithAMenuShowsItsItemsIcons() {
        val state = KeyboardState()
        val menu = KeyMenu(KeyMenuItem.entries) {}

        assertEquals(
            listOf(OptionFace.MenuIcon(KeyMenuItem.Clipboard), OptionFace.MenuIcon(KeyMenuItem.Settings)),
            optionFaces(KeyAction.ShowPage(Page.Symbols), state, menu),
        )
        assertEquals(
            listOf(OptionFace.Label("е"), OptionFace.Label("ё")),
            optionFaces(KeyAction.Letter('е', listOf('ё')), state, menu = null),
        )
    }

    @Test
    fun keyGeometryGivesSquareCellsOnANarrowKey() {
        val geometry = KeyGeometry(Rect(0f, 0f, 80f, 120f), windowWidth = 1080, popupPadding = 0)

        assertEquals(IntSize(120, 120), geometry.cellSize)
    }
}
