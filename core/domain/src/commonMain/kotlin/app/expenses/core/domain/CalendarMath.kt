package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.Instant

/** What was spent at one moment (net of refunds, as the reports count it), as epoch milliseconds and minor units. */
data class SpendPoint(val atMillis: Long, val minor: Long)

/** A bill still to come on a day. [minor] is null when its amount differs each time. */
data class BillOnDay(val date: LocalDate, val minor: Long?)

/** One day of the month grid. [level] is 0 for a day with nothing spent and 1 to 4 for how heavy the day was compared with the heaviest day of the month. */
data class CalendarDay(val date: LocalDate, val spentMinor: Long, val bills: Int, val billsMinor: Long, val level: Int, val isToday: Boolean)

/** A month as a grid starting on Monday: [leadingBlanks] empty cells, then the days. */
data class CalendarMonth(val year: Int, val month: Int, val leadingBlanks: Int, val days: List<CalendarDay>, val totalSpentMinor: Long)

object CalendarMath {
    fun month(year: Int, month: Int, zone: TimeZone, points: List<SpendPoint>, bills: List<BillOnDay>, today: LocalDate): CalendarMonth {
        val first = LocalDate(year, month, 1)
        val length = first.daysUntil(first.plus(1, DateTimeUnit.MONTH))
        val spent = LongArray(length)
        for (p in points) {
            val d = Instant.fromEpochMilliseconds(p.atMillis).toLocalDateTime(zone).date
            if (d.year == year && d.monthNumber == month) spent[d.dayOfMonth - 1] += p.minor
        }
        val billCount = IntArray(length); val billMinor = LongArray(length)
        for (b in bills) if (b.date.year == year && b.date.monthNumber == month) { billCount[b.date.dayOfMonth - 1]++; billMinor[b.date.dayOfMonth - 1] += b.minor ?: 0L }
        val max = spent.maxOrNull()?.takeIf { it > 0 } ?: 0L
        val days = (0 until length).map { i ->
            val s = spent[i]
            val level = if (s <= 0 || max == 0L) 0 else (1 + ((s - 1) * 4 / max).toInt()).coerceIn(1, 4)
            val date = LocalDate(year, month, i + 1)
            CalendarDay(date, s, billCount[i], billMinor[i], level, date == today)
        }
        return CalendarMonth(year, month, first.dayOfWeek.isoDayNumber - 1, days, spent.sum())
    }

    /** The month [offset] months from [today]'s (negative is the past). */
    fun monthAt(today: LocalDate, offset: Int): Pair<Int, Int> = LocalDate(today.year, today.monthNumber, 1).plus(offset, DateTimeUnit.MONTH).let { it.year to it.monthNumber }
}
