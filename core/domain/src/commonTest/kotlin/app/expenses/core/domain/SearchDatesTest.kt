package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Zones are explicit (a fixed offset, so no daylight-saving step) and the answers do not depend on the machine's own zone. */
class SearchDatesTest {
    private val ist = TimeZone.of("Asia/Kolkata")
    private val utc = TimeZone.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).toEpochDays().toLong()
    private val hour = 3_600_000L

    @Test fun theRangeCoversBothDaysWholeInTheGivenZone() {
        val from = day(2026, 10, 1); val to = day(2026, 10, 15)
        val start = SearchDates.fromMillis(from, utc); val until = SearchDates.untilMillis(to, utc)
        assertEquals(LocalDate(2026, 10, 1).toEpochDays() * 86_400_000L, start)
        assertEquals(LocalDate(2026, 10, 16).toEpochDays() * 86_400_000L, until)
        assertEquals(15 * 86_400_000L, until - start, "15 whole days, the last one included")
    }

    @Test fun theSameDayInAnotherZoneStartsWhereThatZoneStarts() {
        val d = day(2026, 10, 1)
        // India is 5h30 ahead of UTC: its 1 October starts at 18:30 UTC on 30 September
        assertEquals(SearchDates.fromMillis(d, utc) - (5 * hour + hour / 2), SearchDates.fromMillis(d, ist))
        assertEquals(SearchDates.untilMillis(d, utc) - (5 * hour + hour / 2), SearchDates.untilMillis(d, ist))
        assertEquals(86_400_000L, SearchDates.untilMillis(d, ist) - SearchDates.fromMillis(d, ist), "one chosen day is 24 hours here (no daylight saving)")
    }

    @Test fun anEndLeftOpenHasNoLimit() {
        assertEquals(Long.MIN_VALUE, SearchDates.fromMillis(null, ist))
        assertEquals(Long.MAX_VALUE, SearchDates.untilMillis(null, ist))
    }

    @Test fun aFirstDayAfterTheLastIsAProblemAndNothingElseIs() {
        val a = day(2026, 10, 15); val b = day(2026, 10, 1)
        assertEquals("The first date is after the last date. Change one of them.", SearchDates.problem(a, b))
        assertNull(SearchDates.problem(b, a)); assertNull(SearchDates.problem(a, a)); assertNull(SearchDates.problem(a, null)); assertNull(SearchDates.problem(null, b)); assertNull(SearchDates.problem(null, null))
    }

    @Test fun theSummaryIsShort() {
        assertEquals("1 Oct – 15 Oct 2026", SearchDates.summary(day(2026, 10, 1), day(2026, 10, 15)))
        assertEquals("28 Dec 2025 – 3 Jan 2026", SearchDates.summary(day(2025, 12, 28), day(2026, 1, 3)))
        assertEquals("15 Oct 2026", SearchDates.summary(day(2026, 10, 15), day(2026, 10, 15)))
        assertEquals("From 1 Oct 2026", SearchDates.summary(day(2026, 10, 1), null))
        assertEquals("Up to 15 Oct 2026", SearchDates.summary(null, day(2026, 10, 15)))
        assertNull(SearchDates.summary(null, null))
    }

    @Test fun monthsAreWholeCalendarMonths() {
        val today = LocalDate(2026, 3, 31)
        assertEquals(LocalDate(2026, 3, 1).toEpochDays() * 86_400_000L to LocalDate(2026, 4, 1).toEpochDays() * 86_400_000L, SearchDates.monthMillis(today, 0, utc))
        assertEquals(LocalDate(2026, 2, 1).toEpochDays() * 86_400_000L to LocalDate(2026, 3, 1).toEpochDays() * 86_400_000L, SearchDates.monthMillis(today, 1, utc))
        // across a year
        assertEquals(LocalDate(2025, 12, 1).toEpochDays() * 86_400_000L to LocalDate(2026, 1, 1).toEpochDays() * 86_400_000L, SearchDates.monthMillis(LocalDate(2026, 1, 20), 1, utc))
    }
}
