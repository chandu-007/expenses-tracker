package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Dates picked on the search screen and the month chips on Home. A picked day is a whole calendar day in the given zone (the phone's),
 * so a range includes the first day from its start and the last day up to its end, whatever the time of day an entry has.
 * Days are counted since 1970-01-01 (what the screens keep), and either end may be left open (null).
 */
object SearchDates {
    /** What to tell the user when the first day is after the last one, or null when the two work together (or one is open). */
    fun problem(fromDay: Long?, toDay: Long?): String? =
        if (fromDay != null && toDay != null && fromDay > toDay) "The first date is after the last date. Change one of them." else null

    /** The first instant of [fromDay] in [zone]; [Long.MIN_VALUE] (no lower limit) when open. An entry at this instant is inside. */
    fun fromMillis(fromDay: Long?, zone: TimeZone): Long = fromDay?.let { LocalDate.fromEpochDays(it).atStartOfDayIn(zone).toEpochMilliseconds() } ?: Long.MIN_VALUE

    /** The first instant after [toDay] in [zone] (the start of the next day), so the last day counts fully; [Long.MAX_VALUE] when open. Entries are before it. */
    fun untilMillis(toDay: Long?, zone: TimeZone): Long = toDay?.let { LocalDate.fromEpochDays(it).plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() } ?: Long.MAX_VALUE

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun short(d: LocalDate) = "${d.day} ${MONTHS[d.month.ordinal]}"

    /** A short line for the chosen dates: "1 Oct – 15 Oct 2026", "From 1 Oct 2026", "Up to 15 Oct 2026", or a single day. Null when neither is chosen. */
    fun summary(fromDay: Long?, toDay: Long?): String? {
        val a = fromDay?.let { LocalDate.fromEpochDays(it) }
        val b = toDay?.let { LocalDate.fromEpochDays(it) }
        return when {
            a == null && b == null -> null
            b == null -> "From ${short(a!!)} ${a.year}"
            a == null -> "Up to ${short(b)} ${b.year}"
            a == b -> "${short(a)} ${a.year}"
            a.year == b.year -> "${short(a)} – ${short(b)} ${b.year}"
            else -> "${short(a)} ${a.year} – ${short(b)} ${b.year}"
        }
    }

    /** The calendar month [monthsBack] months before the one [today] is in, as `[start, end)` epoch milliseconds in [zone] (0: this month, 1: last month). */
    fun monthMillis(today: LocalDate, monthsBack: Int, zone: TimeZone): Pair<Long, Long> {
        val first = LocalDate(today.year, today.month, 1).minus(monthsBack, DateTimeUnit.MONTH)
        val next = first.plus(1, DateTimeUnit.MONTH)
        return first.atStartOfDayIn(zone).toEpochMilliseconds() to next.atStartOfDayIn(zone).toEpochMilliseconds()
    }
}
