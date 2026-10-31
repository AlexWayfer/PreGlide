package name.alexwayfer.preglide.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class StepSliderTest {
    @Test
    fun theSliderStopsAtEveryValue() {
        // Material's slider counts the stops between its ends.
        assertEquals(KeyCornerRadii.count() - 2, sliderSteps(KeyCornerRadii))
        assertEquals(1, sliderSteps(0..4 step 2))
    }

    @Test
    fun aValueBetweenStopsGoesToTheNearestOne() {
        val stops = 10..20 step 5
        assertEquals(15, nearestStop(stops, 14f))
        assertEquals(10, nearestStop(stops, 12f))
        assertEquals(10, nearestStop(stops, -3f))
        assertEquals(20, nearestStop(stops, 99f))
    }
}
