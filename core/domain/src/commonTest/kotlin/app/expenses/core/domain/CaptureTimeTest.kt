package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureTimeTest {
    private val ist = TimeZone.of("Asia/Kolkata")
    private val day = LocalDate(2026, 10, 5)
    private val start = day.atStartOfDayIn(ist).toEpochMilliseconds()

    @Test fun aTextArrivingOnTheBanksDayKeepsItsOwnTime() {
        assertEquals(start + 9 * 3_600_000L + 41 * 60_000L, CaptureTime.entryTime(day, start + 9 * 3_600_000L + 41 * 60_000L, ist))
        assertEquals(start + 23 * 3_600_000L + 59 * 60_000L, CaptureTime.entryTime(day, start + 23 * 3_600_000L + 59 * 60_000L, ist), "the last minute of the day")
        assertEquals(start, CaptureTime.entryTime(day, start, ist), "midnight sharp")
    }

    @Test fun aDelayedTextIsPutAtNoonOnTheBanksDateAndNoDateMeansTheArrivalTime() {
        val noon = start + 12 * 3_600_000L
        assertEquals(noon, CaptureTime.entryTime(day, start + 26 * 3_600_000L, ist), "arrived the next day")
        assertEquals(noon, CaptureTime.entryTime(day, start - 1, ist), "arrived the evening before")
        assertEquals(12345L, CaptureTime.entryTime(null, 12345L, ist))
    }

    @Test fun theDayIsTheLocalDayNotUtc() {
        val lateEvening = start + 23 * 3_600_000L // 23:00 IST is 17:30 UTC the same date; 01:00 IST the next day would be 19:30 UTC the same date
        assertEquals(lateEvening, CaptureTime.entryTime(day, lateEvening, ist))
        assertEquals(start + 12 * 3_600_000L, CaptureTime.entryTime(day, start + 25 * 3_600_000L, ist), "01:00 IST next day is another local day though the UTC date matches")
    }
}
