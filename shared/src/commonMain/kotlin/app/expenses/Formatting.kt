package app.expenses

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * Shared date helpers, English names, in the phone's time zone. They replace java.time.LocalDate / DateTimeFormatter in shared code.
 *
 * Patterns understood by [formatDate] and [formatMillis] (the subset the app uses, same meaning as java.time):
 *   d  dd  day of month        MMM  MMMM  month name        M  MM  month number       yyyy  year
 *   EEE  EEEE  weekday name    HH  hour 00-23               mm  minute 00-59
 * Any other character is copied as it is (spaces, commas, dots, ':', '·', '–').
 */

private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** The phone's current time zone. */
fun systemZone(): TimeZone = TimeZone.currentSystemDefault()

/** Today's date in [zone]. */
fun todayLocal(zone: TimeZone = systemZone()): LocalDate = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(zone).date

/** The day number (days since 1970-01-01) of a date, as the Long the view model passes around. */
fun LocalDate.epochDay(): Long = toEpochDays()

fun localDateOfEpochDay(epochDay: Long): LocalDate = LocalDate.fromEpochDays(epochDay)

/** The calendar day [millis] falls on in [zone]. */
fun dateOfMillis(millis: Long, zone: TimeZone = systemZone()): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date

fun LocalDate.plusDays(n: Long): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Long): LocalDate = plus(-n, DateTimeUnit.DAY)

/** First millisecond of this date in [zone]. */
fun LocalDate.startOfDayMillis(zone: TimeZone = systemZone()): Long = atStartOfDayIn(zone).toEpochMilliseconds()

fun formatDate(date: LocalDate, pattern: String): String = formatDateTime(LocalDateTime(date.year, date.month.number, date.day, 0, 0), pattern)

fun formatMillis(millis: Long, pattern: String, zone: TimeZone = systemZone()): String =
    formatDateTime(Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone), pattern)

fun formatEpochDay(epochDay: Long, pattern: String): String = formatDate(localDateOfEpochDay(epochDay), pattern)

fun formatDateTime(t: LocalDateTime, pattern: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (!c.isLetter()) { out.append(c); i++; continue }
        var j = i
        while (j < pattern.length && pattern[j] == c) j++
        val n = j - i
        when (c) {
            'd' -> out.append(t.day.toString().padStart(n, '0'))
            'M' -> out.append(when { n >= 4 -> MONTHS[t.month.number - 1]; n == 3 -> MONTHS[t.month.number - 1].take(3); else -> t.month.number.toString().padStart(n, '0') })
            'y' -> out.append(if (n == 2) (t.year % 100).toString().padStart(2, '0') else t.year.toString())
            'E' -> out.append(WEEKDAYS[t.dayOfWeek.ordinal].let { if (n >= 4) it else it.take(3) })
            'H' -> out.append(t.hour.toString().padStart(n, '0'))
            'm' -> out.append(t.minute.toString().padStart(n, '0'))
            else -> throw IllegalArgumentException("Unsupported date pattern letter '$c' in \"$pattern\"")
        }
        i = j
    }
    return out.toString()
}

/** Monday-first weekday number 1..7 (java.time's DayOfWeek.getValue). */
val DayOfWeek.isoNumber: Int get() = ordinal + 1

/** A date pattern kept for reuse; the replacement for a java.time DateTimeFormatter field. Dates and instants are shown in the phone's time zone. */
class DatePattern(private val pattern: String) {
    fun format(date: LocalDate): String = formatDate(date, pattern)
    fun format(millis: Long): String = formatMillis(millis, pattern)
}
