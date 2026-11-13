package name.alexwayfer.preglide.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WordAgeTest {
    private val today = LocalDate.of(2024, 2, 29)

    @Test
    fun relativeDatesUseLocalDaysAndCompletedCalendarMonths() {
        assertEquals(WordAge(AgeUnit.Day, 0), wordAge(today, today))
        assertEquals(WordAge(AgeUnit.Day, 1), wordAge(today.minusDays(1), today))
        assertEquals(WordAge(AgeUnit.Day, 13), wordAge(today.minusDays(13), today))
        assertEquals(WordAge(AgeUnit.Week, 2), wordAge(today.minusDays(14), today))
        assertEquals(WordAge(AgeUnit.Month, 1), wordAge(LocalDate.of(2024, 1, 31), today))
        assertEquals(WordAge(AgeUnit.Year, 1), wordAge(today.minusYears(1), today))
        assertEquals(null, wordAge(today.plusDays(1), today))
    }
}
