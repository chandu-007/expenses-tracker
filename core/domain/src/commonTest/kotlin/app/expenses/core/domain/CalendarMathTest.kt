package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarMathTest {
    private val ist = TimeZone.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0, zone: TimeZone = ist) = LocalDateTime(y, m, d, h, min).toInstant(zone).toEpochMilliseconds()
    private fun inr(v: Long) = Money(v, Currency.INR)

    @Test
    fun theGridStartsOnMondayAndHasEveryDayOfTheMonth() {
        val oct = CalendarMath.month(2026, 10, ist, emptyList(), emptyList(), LocalDate(2026, 10, 7))
        assertEquals(3, oct.leadingBlanks, "1 Oct 2026 is a Thursday: Mon, Tue, Wed are blank")
        assertEquals(31, oct.days.size)
        assertEquals(0, CalendarMath.month(2026, 6, 1.let { ist }, emptyList(), emptyList(), LocalDate(2026, 6, 1)).leadingBlanks, "1 Jun 2026 is a Monday")
        assertEquals(29, CalendarMath.month(2028, 2, ist, emptyList(), emptyList(), LocalDate(2028, 2, 1)).days.size, "leap February")
        assertEquals(listOf(true), oct.days.filter { it.isToday }.map { true }.take(1))
        assertEquals(LocalDate(2026, 10, 7), oct.days.single { it.isToday }.date)
    }

    @Test
    fun spendIsAddedUpOnTheLocalDayAndLateNightStaysOnItsOwnDay() {
        val pts = listOf(SpendPoint(at(2026, 10, 7, 23, 30), 10_000), SpendPoint(at(2026, 10, 7, 0, 5), 5_000), SpendPoint(at(2026, 10, 8, 0, 10), 7_000), SpendPoint(at(2026, 11, 1, 1), 99_000), SpendPoint(at(2026, 9, 30, 23), 88_000))
        val m = CalendarMath.month(2026, 10, ist, pts, emptyList(), LocalDate(2026, 10, 1))
        assertEquals(15_000L, m.days[6].spentMinor); assertEquals(7_000L, m.days[7].spentMinor)
        assertEquals(22_000L, m.totalSpentMinor, "other months are not counted")
        // 20:00 UTC on the 7th is 01:30 on the 8th in India
        val utc = CalendarMath.month(2026, 10, TimeZone.UTC, listOf(SpendPoint(at(2026, 10, 7, 20, 0, TimeZone.UTC), 1)), emptyList(), LocalDate(2026, 10, 1))
        val kolkata = CalendarMath.month(2026, 10, ist, listOf(SpendPoint(at(2026, 10, 7, 20, 0, TimeZone.UTC), 1)), emptyList(), LocalDate(2026, 10, 1))
        assertEquals(1L, utc.days[6].spentMinor); assertEquals(1L, kolkata.days[7].spentMinor)
    }

    @Test
    fun refundsMakeADayNegativeAndNeverALevel() {
        val m = CalendarMath.month(2026, 10, ist, listOf(SpendPoint(at(2026, 10, 3, 10), -5_000), SpendPoint(at(2026, 10, 4, 10), 2_000)), emptyList(), LocalDate(2026, 10, 1))
        assertEquals(-5_000L, m.days[2].spentMinor); assertEquals(0, m.days[2].level); assertEquals(4, m.days[3].level)
    }

    @Test
    fun levelsScaleToTheHeaviestDayAndAnEmptyMonthHasNone() {
        val pts = listOf(1L to 100L, 2L to 25_000L, 3L to 50_000L, 4L to 75_000L, 5L to 100_000L).map { (d, v) -> SpendPoint(at(2026, 10, d.toInt(), 12), v) }
        val m = CalendarMath.month(2026, 10, ist, pts, emptyList(), LocalDate(2026, 10, 1))
        assertEquals(listOf(1, 1, 2, 3, 4), m.days.take(5).map { it.level })
        assertTrue(CalendarMath.month(2026, 10, ist, emptyList(), emptyList(), LocalDate(2026, 10, 1)).days.all { it.level == 0 })
    }

    @Test
    fun billsAreCountedPerDayWithTheirAmountsAndNoAmountCountsAsOneWithZero() {
        val m = CalendarMath.month(2026, 10, ist, emptyList(), listOf(BillOnDay(LocalDate(2026, 10, 5), 6_000), BillOnDay(LocalDate(2026, 10, 5), null), BillOnDay(LocalDate(2026, 11, 5), 1)), LocalDate(2026, 10, 1))
        assertEquals(2, m.days[4].bills); assertEquals(6_000L, m.days[4].billsMinor); assertEquals(0, m.days[5].bills)
    }

    @Test
    fun monthsAreFoundByOffsetAcrossYears() {
        assertEquals(2026 to 12, CalendarMath.monthAt(LocalDate(2026, 10, 31), 2))
        assertEquals(2025 to 12, CalendarMath.monthAt(LocalDate(2026, 1, 15), -1))
        assertEquals(2026 to 2, CalendarMath.monthAt(LocalDate(2026, 3, 31), -1))
    }

    @Test
    fun compactAmountsAreCutNotRoundedAndUseLakhAndCrore() {
        fun c(minor: Long) = inr(minor).compact()
        assertEquals("₹0", c(0)); assertEquals("₹0.5", c(50)); assertEquals("₹9.5", c(950)); assertEquals("₹9", c(900)); assertEquals("₹10", c(1_000)); assertEquals("₹950", c(95_000))
        assertEquals("₹999", c(99_999)); assertEquals("₹1K", c(100_000)); assertEquals("₹1.2K", c(129_999)); assertEquals("₹12K", c(1_200_000)); assertEquals("₹99.9K", c(9_999_999))
        assertEquals("₹1L", c(10_000_000)); assertEquals("₹1.2L", c(12_999_999)); assertEquals("₹99.9L", c(999_999_999))
        assertEquals("₹1Cr", c(1_000_000_000)); assertEquals("₹3.4Cr", c(3_499_999_999)); assertEquals("-₹1.2K", c(-129_999)); assertEquals("-₹0.05", c(-5))
        assertEquals("USD 1.5M", Money(150_000_000, Currency.USD).compact()); assertEquals("USD 2K", Money(200_000, Currency.USD).compact())
        assertEquals("JPY 1.2K", Money(1_234, Currency("JPY", 0)).compact(), "currencies without decimals work")
    }
}
