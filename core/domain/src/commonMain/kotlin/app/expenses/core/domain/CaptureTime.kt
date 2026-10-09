package app.expenses.core.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

object CaptureTime {
    private const val NOON_MS = 12 * 3_600_000L

    /**
     * When a captured transaction happened, for the entry. The bank's own date is the day; the time of day comes from when the text arrived when
     * that is the same local day (the usual case), so entries keep their real order within a day and the duplicate windows have a time to work with.
     * A text that arrives on another day than the bank's date (a delayed alert) is placed at noon on the bank's date. With no date in the text, the
     * arrival time is used.
     */
    fun entryTime(bankDate: LocalDate?, receivedAt: Long, zone: TimeZone): Long {
        if (bankDate == null) return receivedAt
        val arrivedOn = Instant.fromEpochMilliseconds(receivedAt).toLocalDateTime(zone).date
        return if (arrivedOn == bankDate) receivedAt else bankDate.atStartOfDayIn(zone).toEpochMilliseconds() + NOON_MS
    }
}
