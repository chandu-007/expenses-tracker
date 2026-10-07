package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A stretch of whole days, from [start] up to but not including [endExclusive]. */
data class DatePeriod(val start: LocalDate, val endExclusive: LocalDate) {
    init {
        require(start < endExclusive) { "A period must cover at least one day" }
    }

    val days: Int get() = start.daysUntil(endExclusive)

    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive

    /** The period as epoch milliseconds in [zone], for filtering entries: `startMillis <= occurredAt < endMillis`. */
    fun startMillis(zone: TimeZone): Long = start.atStartOfDayIn(zone).toEpochMilliseconds()

    fun endMillis(zone: TimeZone): Long = endExclusive.atStartOfDayIn(zone).toEpochMilliseconds()
}

/**
 * How time is cut into budget periods. [Monthly] can start on any day from 1 to 28, so a salary cycle that starts on the
 * 25th works; days 29 to 31 are not allowed because some months do not have them. [Weekly] starts on a chosen weekday.
 * Periods always tile the calendar: every date is in exactly one period and each period starts where the last ended.
 */
sealed interface BudgetPeriod {
    fun containing(date: LocalDate): DatePeriod

    data class Monthly(val startDay: Int = 1) : BudgetPeriod {
        init {
            require(startDay in 1..MAX_START_DAY) { "A monthly period can start on day 1 to $MAX_START_DAY" }
        }

        override fun containing(date: LocalDate): DatePeriod {
            val thisMonth = LocalDate(date.year, date.month, startDay)
            val start = if (date >= thisMonth) thisMonth else thisMonth.minus(1, DateTimeUnit.MONTH)
            return DatePeriod(start, start.plus(1, DateTimeUnit.MONTH))
        }

        companion object {
            const val MAX_START_DAY = 28
        }
    }

    data class Weekly(val startsOn: DayOfWeek = DayOfWeek.MONDAY) : BudgetPeriod {
        override fun containing(date: LocalDate): DatePeriod {
            val back = (date.dayOfWeek.isoDayNumber - startsOn.isoDayNumber + 7) % 7
            val start = date.minus(back, DateTimeUnit.DAY)
            return DatePeriod(start, start.plus(7, DateTimeUnit.DAY))
        }
    }

    /** Every two weeks, counted from [anchor] (the first day of any one of the fortnights; which one does not matter). */
    data class BiWeekly(val anchor: LocalDate) : BudgetPeriod {
        override fun containing(date: LocalDate): DatePeriod {
            val daysSince = anchor.daysUntil(date)
            val back = ((daysSince % 14) + 14) % 14 // floor mod, so dates before the anchor tile too
            val start = date.minus(back, DateTimeUnit.DAY)
            return DatePeriod(start, start.plus(14, DateTimeUnit.DAY))
        }
    }

    /** Twice a month: the 1st to the 15th, and the 16th to the end of the month. */
    data object SemiMonthly : BudgetPeriod {
        override fun containing(date: LocalDate): DatePeriod {
            val firstHalf = date.day <= 15
            val start = LocalDate(date.year, date.month, if (firstHalf) 1 else 16)
            val end = if (firstHalf) LocalDate(date.year, date.month, 16) else LocalDate(date.year, date.month, 1).plus(1, DateTimeUnit.MONTH)
            return DatePeriod(start, end)
        }
    }

    /**
     * A year that starts on the 1st of [startMonth]. The Indian financial year is April to March, the default. January gives the calendar year.
     */
    data class Yearly(val startMonth: Int = FINANCIAL_YEAR_START_MONTH) : BudgetPeriod {
        init {
            require(startMonth in 1..12) { "A year starts in month 1 to 12" }
        }

        override fun containing(date: LocalDate): DatePeriod {
            val startYear = if (date.monthNumber >= startMonth) date.year else date.year - 1
            val start = LocalDate(startYear, startMonth, 1)
            return DatePeriod(start, LocalDate(startYear + 1, startMonth, 1))
        }

        companion object {
            const val FINANCIAL_YEAR_START_MONTH = 4
        }
    }
}
