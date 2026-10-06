package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Moves [epochMillis] to [date], keeping its time of day in [zone]. Used when the user changes the date of an entry:
 * the entry keeps its place within the day. If that wall-clock time does not exist on the new date (a daylight-saving
 * gap) the time moves forward to the next valid one.
 */
fun withDate(epochMillis: Long, date: LocalDate, zone: TimeZone): Long {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).time
    return LocalDateTime(date, time).toInstant(zone).toEpochMilliseconds()
}

/** The calendar date of [epochMillis] in [zone]. */
fun dateOf(epochMillis: Long, zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date

/** Midnight at the start of [date] in [zone], as epoch milliseconds. */
fun startOfDay(date: LocalDate, zone: TimeZone): Long = date.atStartOfDayIn(zone).toEpochMilliseconds()
