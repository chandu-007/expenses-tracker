package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BudgetTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val dates = Arb.long(0, 20_000).let { a -> object { fun at(n: Long) = LocalDate(1990, 1, 1).plus(n, DateTimeUnit.DAY) } }

    // ---- periods ----

    @Test
    fun monthlyPeriodsTileTheCalendarForEveryStartDay() = runTest {
        checkAll(Arb.long(0, 20_000), Arb.int(1, 28)) { n, startDay ->
            val date = dates.at(n)
            val p = BudgetPeriod.Monthly(startDay).containing(date)
            assertTrue(date in p, "$date must be inside $p")
            assertEquals(startDay, p.start.day)
            assertTrue(p.days in 28..31, "a month-long period had ${p.days} days")
            val next = BudgetPeriod.Monthly(startDay).containing(p.endExclusive)
            assertEquals(p.endExclusive, next.start, "the next period starts where this one ends")
            val before = BudgetPeriod.Monthly(startDay).containing(p.start.plus(-1, DateTimeUnit.DAY))
            assertEquals(p.start, before.endExclusive, "the previous period ends where this one starts")
        }
    }

    @Test
    fun aSalaryCycleStartingOnThe25thSplitsAtTheRightDay() {
        val cycle = BudgetPeriod.Monthly(25)
        assertEquals(DatePeriod(LocalDate(2026, 9, 25), LocalDate(2026, 10, 25)), cycle.containing(LocalDate(2026, 10, 6)))
        assertEquals(DatePeriod(LocalDate(2026, 10, 25), LocalDate(2026, 11, 25)), cycle.containing(LocalDate(2026, 10, 25)))
        assertEquals(DatePeriod(LocalDate(2026, 9, 25), LocalDate(2026, 10, 25)), cycle.containing(LocalDate(2026, 10, 24)))
        assertEquals(DatePeriod(LocalDate(2025, 12, 25), LocalDate(2026, 1, 25)), cycle.containing(LocalDate(2026, 1, 3)), "across a year boundary")
    }

    @Test
    fun aCalendarMonthIncludingFebruaryAndLeapYears() {
        val month = BudgetPeriod.Monthly(1)
        assertEquals(28, month.containing(LocalDate(2027, 2, 10)).days)
        assertEquals(29, month.containing(LocalDate(2028, 2, 10)).days)
        assertEquals(31, month.containing(LocalDate(2026, 10, 31)).days)
        assertEquals(LocalDate(2026, 11, 1), month.containing(LocalDate(2026, 10, 31)).endExclusive)
    }

    @Test
    fun weeklyPeriodsAreSevenDaysAndStartOnTheChosenWeekday() = runTest {
        for (startsOn in DayOfWeek.entries) {
            checkAll(Arb.long(0, 20_000)) { n ->
                val date = dates.at(n)
                val p = BudgetPeriod.Weekly(startsOn).containing(date)
                assertTrue(date in p)
                assertEquals(7, p.days)
                assertEquals(startsOn, p.start.dayOfWeek)
            }
        }
    }

    @Test
    fun invalidPeriodsAreRejected() {
        assertFailsWith<IllegalArgumentException> { BudgetPeriod.Monthly(0) }
        assertFailsWith<IllegalArgumentException> { BudgetPeriod.Monthly(29) }
        assertFailsWith<IllegalArgumentException> { DatePeriod(LocalDate(2026, 1, 2), LocalDate(2026, 1, 2)) }
    }

    @Test
    fun periodBoundsInMillisAreHalfOpenAndFollowTheTimeZone() {
        val kolkata = TimeZone.of("Asia/Kolkata")
        val p = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
        assertEquals(31L * 86_400_000L, p.endMillis(kolkata) - p.startMillis(kolkata))
        assertEquals(p.startMillis(TimeZone.UTC) - 19_800_000L, p.startMillis(kolkata), "midnight in Kolkata is 5h30 before midnight UTC")
    }

    // ---- progress ----

    private val spent = mapOf<String?, Money>("food" to m(6_000), "cafe" to m(2_500), "fun" to m(1_000), null to m(500))
    private val children = mapOf("food" to listOf("cafe"))

    @Test
    fun overallCountsEverythingIncludingUncategorisedAndACategoryCountsItsSubcategories() {
        val out = BudgetMath.progress(listOf(BudgetLimit(null, m(20_000)), BudgetLimit("food", m(10_000)), BudgetLimit("fun", m(900))), spent, children)
        assertEquals(m(10_000), out[0].spent)
        assertEquals(m(10_000), out[0].remaining)
        assertEquals(50, out[0].usedPercent)
        assertEquals(m(8_500), out[1].spent, "food plus its subcategory cafe")
        assertEquals(85, out[1].usedPercent)
        assertEquals(BudgetState.NEAR, out[1].state)
        assertEquals(m(-100), out[2].remaining)
        assertEquals(111, out[2].usedPercent)
        assertEquals(BudgetState.OVER, out[2].state)
    }

    @Test
    fun theStateChangesAtEightyPercentAndWhenSpendingPassesTheLimitNotWhenItReachesIt() {
        fun state(spentMinor: Long) = BudgetMath.progress(listOf(BudgetLimit(null, m(1_000))), mapOf("a" to m(spentMinor))).single().state
        assertEquals(BudgetState.OK, state(799))
        assertEquals(BudgetState.NEAR, state(800))
        assertEquals(BudgetState.NEAR, state(1_000), "exactly at the limit is not over it")
        assertEquals(BudgetState.OVER, state(1_001))
    }

    @Test
    fun refundsThatMakeSpendingNegativeCountAsNothingUsed() {
        val p = BudgetMath.progress(listOf(BudgetLimit("food", m(1_000))), mapOf("food" to m(-300))).single()
        assertEquals(0, p.usedPercent)
        assertEquals(BudgetState.OK, p.state)
        assertEquals(m(1_300), p.remaining)
    }

    @Test
    fun percentDoesNotOverflowOnHugeAmounts() {
        assertEquals(Int.MAX_VALUE, BudgetMath.percentUsed(Long.MAX_VALUE, 1))
        assertEquals(100, BudgetMath.percentUsed(999_999_999_999_999L, 999_999_999_999_999L))
    }

    @Test
    fun aLimitInAnotherCurrencyThanTheSpendingIsRejected() {
        assertFailsWith<IllegalArgumentException> { BudgetMath.progress(listOf(BudgetLimit(null, m(100))), mapOf("a" to Money(5, Currency.USD))) }
        assertFailsWith<IllegalArgumentException> { BudgetLimit("x", m(0)) }
    }

    // ---- safe to spend ----

    private fun overall(limit: Long, spent: Long) = BudgetMath.progress(listOf(BudgetLimit(null, m(limit))), mapOf("a" to m(spent))).single()

    @Test
    fun safeToSpendSpreadsTheRemainderOverTheDaysLeftIncludingToday() {
        val period = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
        val s = BudgetMath.safeToSpend(overall(31_000, 10_000), LocalDate(2026, 10, 11), period)
        assertEquals(21, s.daysLeft, "11 Oct to 31 Oct inclusive")
        assertEquals(m(21_000), s.remaining)
        assertEquals(m(1_000), s.perDay)
        assertEquals(1, BudgetMath.safeToSpend(overall(31_000, 10_000), LocalDate(2026, 10, 31), period).daysLeft)
        assertEquals(m(21_000), BudgetMath.safeToSpend(overall(31_000, 10_000), LocalDate(2026, 10, 31), period).perDay, "the whole remainder on the last day")
    }

    @Test
    fun safeToSpendIsZeroOnceTheLimitIsReachedAndNeverNegative() {
        val period = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
        assertEquals(m(0), BudgetMath.safeToSpend(overall(5_000, 5_000), LocalDate(2026, 10, 5), period).perDay)
        val over = BudgetMath.safeToSpend(overall(5_000, 7_000), LocalDate(2026, 10, 5), period)
        assertEquals(m(0), over.perDay)
        assertEquals(m(-2_000), over.remaining, "the shortfall is still reported")
    }

    @Test
    fun safeToSpendRoundsDownSoItNeverPromisesMoreThanIsLeft() = runTest {
        checkAll(Arb.long(1, 10_000_000), Arb.long(0, 10_000_000), Arb.int(0, 30)) { limit, spentMinor, offset ->
            val period = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
            val s = BudgetMath.safeToSpend(overall(limit, spentMinor), LocalDate(2026, 10, 1).plus(offset, DateTimeUnit.DAY), period)
            assertTrue(s.perDay.minorUnits * s.daysLeft <= maxOf(0L, s.remaining.minorUnits))
            assertTrue(s.perDay.minorUnits >= 0)
        }
    }

    @Test
    fun beforeThePeriodStartsTheWholePeriodIsStillAhead() {
        val period = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
        assertEquals(31, BudgetMath.safeToSpend(overall(3_100, 0), LocalDate(2026, 9, 20), period).daysLeft)
    }

    @Test
    fun upcomingBillsAreSetAsideBeforeSpreadingWhatIsLeft() {
        val period = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
        val s = BudgetMath.safeToSpend(overall(31_000, 10_000), LocalDate(2026, 10, 11), period, upcoming = m(6_300))
        assertEquals(m(21_000), s.remaining)
        assertEquals(m(14_700), s.available)
        assertEquals(m(700), s.perDay, "14,700 over 21 days")
        assertEquals(m(6_300), s.upcoming)
    }

    @Test
    fun billsLargerThanWhatIsLeftLeaveNothingToSpendAndShowTheShortfall() {
        val period = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
        val s = BudgetMath.safeToSpend(overall(10_000, 8_000), LocalDate(2026, 10, 11), period, upcoming = m(5_000))
        assertEquals(m(0), s.perDay)
        assertEquals(m(-3_000), s.available)
        assertFailsWith<IllegalArgumentException> { BudgetMath.safeToSpend(overall(10_000, 0), LocalDate(2026, 10, 11), period, upcoming = m(-1)) }
        assertFailsWith<IllegalArgumentException> { BudgetMath.safeToSpend(overall(10_000, 0), LocalDate(2026, 10, 11), period, upcoming = Money(1, Currency.USD)) }
    }
}
