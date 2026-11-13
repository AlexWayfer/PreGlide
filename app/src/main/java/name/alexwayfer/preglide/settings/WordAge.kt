package name.alexwayfer.preglide.settings

import java.time.LocalDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

internal enum class AgeUnit { Day, Week, Month, Year }
internal data class WordAge(val unit: AgeUnit, val count: Long)

private const val DAYS_IN_WEEK = 7
private const val MONTHS_IN_YEAR = 12

internal fun wordAge(used: LocalDate, today: LocalDate): WordAge? {
    val days = ChronoUnit.DAYS.between(used, today)
    val monthDifference = today.getLong(ChronoField.PROLEPTIC_MONTH) - used.getLong(ChronoField.PROLEPTIC_MONTH)
    val months = if (used.plusMonths(monthDifference) > today) monthDifference - 1 else monthDifference
    return when {
        days < 0 -> null
        days < 2 * DAYS_IN_WEEK -> WordAge(AgeUnit.Day, days)
        months == 0L -> WordAge(AgeUnit.Week, days / DAYS_IN_WEEK)
        months < MONTHS_IN_YEAR -> WordAge(AgeUnit.Month, months)
        else -> WordAge(AgeUnit.Year, months / MONTHS_IN_YEAR)
    }
}
