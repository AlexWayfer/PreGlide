package name.alexwayfer.preglide.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProgressTest {
    @Test
    fun `durations read in the largest units that matter`() {
        assertEquals("0 s", duration(400.milliseconds))
        assertEquals("44 s", duration(44.seconds))
        assertEquals("5 min 3 s", duration(5.minutes + 3.seconds))
        assertEquals("1 h 2 min", duration(1.hours + 2.minutes + 30.seconds))
    }

    @Test
    fun `a step says how long it took, or that it was counted before`() {
        assertEquals("ru words: step 3 of 19 in 44 s", progressLine("ru words", CountProgress.Step(3, 19, 44.seconds)))
        assertEquals("ru words: step 1 of 19 counted before", progressLine("ru words", CountProgress.Step(1, 19, null)))
        assertEquals("ru words: steps summed in 2 min 0 s", progressLine("ru words", CountProgress.Merged(2.minutes)))
    }

    @Test
    fun `a log line starts with its time`() {
        assertEquals("09:05:07 ru: built", timestamped("ru: built", LocalTime.of(9, 5, 7, 123)))
    }
}
