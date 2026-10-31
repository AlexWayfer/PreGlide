package name.alexwayfer.preglide.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyCornersTest {
    @Test
    fun theRadiiRunFromSquareToNearlyACircleThroughTheOldDefault() {
        assertEquals(0, KeyCornerRadii.first)
        assertEquals(16, KeyCornerRadii.last)
        assertEquals(true, DEFAULT_KEY_CORNER_RADIUS in KeyCornerRadii)
    }

    @Test
    fun aValueBetweenStopsGoesToTheNearestOne() {
        assertEquals(6, nearestCornerRadius(6.4f))
        assertEquals(8, nearestCornerRadius(7.6f))
        assertEquals(0, nearestCornerRadius(-3f))
        assertEquals(16, nearestCornerRadius(40f))
    }

    @Test
    fun theDefaultHoldsUntilTheUserPicksARadius() {
        assertEquals(DEFAULT_KEY_CORNER_RADIUS, keyCornerRadius(null))
        assertEquals(12, keyCornerRadius(12))
        // A radius kept before the stops changed lands on the nearest one.
        assertEquals(16, keyCornerRadius(20))
    }
}
