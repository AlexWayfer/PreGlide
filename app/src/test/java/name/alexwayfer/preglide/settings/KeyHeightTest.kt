package name.alexwayfer.preglide.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyHeightTest {
    @Test
    fun theHeightsRunFromCompactToRoomyThroughTheOldDefault() {
        assertEquals(40, KeyHeights.first)
        assertEquals(64, KeyHeights.last)
        assertEquals(2, KeyHeights.step)
        assertEquals(true, DEFAULT_KEY_HEIGHT in KeyHeights)
    }

    @Test
    fun aValueBetweenStopsGoesToTheNearestOne() {
        assertEquals(46, nearestKeyHeight(46.4f))
        assertEquals(48, nearestKeyHeight(47.6f))
        assertEquals(40, nearestKeyHeight(10f))
        assertEquals(64, nearestKeyHeight(90f))
    }

    @Test
    fun theDefaultHoldsUntilTheUserPicksAHeight() {
        assertEquals(DEFAULT_KEY_HEIGHT, keyHeight(null))
        assertEquals(44, keyHeight(44))
        // A height kept before the stops changed lands on the nearest one.
        assertEquals(64, keyHeight(72))
    }
}
