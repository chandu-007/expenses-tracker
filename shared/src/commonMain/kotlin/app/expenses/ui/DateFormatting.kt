package app.expenses.ui

import app.expenses.dateOfMillis
import app.expenses.formatDate
import app.expenses.minusDays
import app.expenses.systemZone
import app.expenses.todayLocal
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Date helpers for the screens that [app.expenses] `Formatting.kt` does not already cover. English names, the phone's time zone. */

/** The Monday on or before today. */
fun thisMonday(): LocalDate = todayLocal().let { it.minusDays(it.dayOfWeek.ordinal.toLong()) }

/** "Mon, 5 Oct 2026". */
fun LocalDate.fullLabel(): String = formatDate(this, "EEE, d MMM yyyy")

private val MONTH_ABBREVIATIONS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "5 Oct, 3:07 PM" for an epoch-millisecond instant, in the phone's time zone. */
fun dateTimeLabel(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(systemZone())
    val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "${t.day} ${MONTH_ABBREVIATIONS[t.month.number - 1]}, $h12:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "AM" else "PM"}"
}

/** The local time of day of an epoch-millisecond instant. */
fun localTimeOf(ms: Long, zone: TimeZone = systemZone()): LocalTime = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).time

/** UTC midnight of [date], in epoch milliseconds: what the Material date picker takes and returns. */
fun utcMidnightMillis(date: LocalDate): Long = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** The UTC calendar date of a Material date picker selection. */
fun pickerDate(utcMillis: Long): LocalDate = dateOfMillis(utcMillis, TimeZone.UTC)

/** [date] at [time] in [zone], in epoch milliseconds. */
fun millisOf(date: LocalDate, time: LocalTime, zone: TimeZone = systemZone()): Long = date.atTime(time).toInstant(zone).toEpochMilliseconds()
