package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MorePeriodsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private val anchor = d(2026, 1, 5)
    private val all = listOf(BudgetPeriod.BiWeekly(anchor), BudgetPeriod.SemiMonthly, BudgetPeriod.Yearly(), BudgetPeriod.Yearly(1), BudgetPeriod.Yearly(10), BudgetPeriod.EveryDays(10, anchor), BudgetPeriod.EveryDays(1, anchor), BudgetPeriod.EveryDays(366, anchor), BudgetPeriod.EveryDays(7, d(2030, 3, 3)))

    @Test
    fun theIndianFinancialYearRunsAprilToMarch() {
        val fy = BudgetPeriod.Yearly()
        assertEquals(DatePeriod(d(2025, 4, 1), d(2026, 4, 1)), fy.containing(d(2026, 3, 31)))
        assertEquals(DatePeriod(d(2026, 4, 1), d(2027, 4, 1)), fy.containing(d(2026, 4, 1)))
        assertEquals(DatePeriod(d(2026, 4, 1), d(2027, 4, 1)), fy.containing(d(2027, 3, 31)))
        assertEquals(DatePeriod(d(2026, 1, 1), d(2027, 1, 1)), BudgetPeriod.Yearly(1).containing(d(2026, 7, 4)), "January start is the calendar year")
        assertFailsWith<IllegalArgumentException> { BudgetPeriod.Yearly(0) }
        assertFailsWith<IllegalArgumentException> { BudgetPeriod.Yearly(13) }
    }

    @Test
    fun semiMonthlyHalvesCoverEveryMonthLengthIncludingLeapFebruary() {
        val s = BudgetPeriod.SemiMonthly
        assertEquals(DatePeriod(d(2026, 10, 1), d(2026, 10, 16)), s.containing(d(2026, 10, 15)))
        assertEquals(DatePeriod(d(2026, 10, 16), d(2026, 11, 1)), s.containing(d(2026, 10, 16)))
        assertEquals(DatePeriod(d(2028, 2, 16), d(2028, 3, 1)), s.containing(d(2028, 2, 29)))
        assertEquals(DatePeriod(d(2026, 12, 16), d(2027, 1, 1)), s.containing(d(2026, 12, 31)))
        assertEquals(15, s.containing(d(2026, 10, 1)).days); assertEquals(13, s.containing(d(2026, 2, 20)).days)
    }

    @Test
    fun fortnightsAreFourteenDaysFromTheAnchorAndTileBeforeAndAfterIt() {
        val b = BudgetPeriod.BiWeekly(anchor)
        assertEquals(DatePeriod(d(2026, 1, 5), d(2026, 1, 19)), b.containing(d(2026, 1, 18)))
        assertEquals(DatePeriod(d(2026, 1, 19), d(2026, 2, 2)), b.containing(d(2026, 1, 19)))
        assertEquals(DatePeriod(d(2025, 12, 22), d(2026, 1, 5)), b.containing(d(2026, 1, 4)), "the day before the anchor is in the fortnight before it")
        assertEquals(DatePeriod(d(2025, 12, 22), d(2026, 1, 5)), b.containing(d(2025, 12, 22)))
        assertEquals(anchor.dayOfWeek, b.containing(d(2030, 6, 6)).start.dayOfWeek, "every fortnight starts on the anchor's weekday")
    }

    @Test
    fun everyKindTilesTheCalendar() = runTest {
        for (p in all) checkAll(Arb.long(-3_000, 25_000)) { n ->
            val date = LocalDate(1990, 1, 1).plus(n.toInt(), DateTimeUnit.DAY)
            val cur = p.containing(date)
            assertTrue(date in cur, "$p $date")
            assertEquals(cur, p.containing(cur.start), "$p starts where it says"); assertEquals(cur, p.containing(cur.endExclusive.minus(1, DateTimeUnit.DAY)))
            assertEquals(cur.start, p.containing(cur.start.minus(1, DateTimeUnit.DAY)).endExclusive, "$p: the one before ends where this starts")
            assertEquals(cur.endExclusive, p.containing(cur.endExclusive).start, "$p: the next starts where this ends")
        }
    }

    @Test
    fun aCustomLengthCountsFromTheAnchorAndRefusesLengthsOutsideOneToAYear() {
        val p = BudgetPeriod.EveryDays(10, d(2026, 10, 1))
        assertEquals(DatePeriod(d(2026, 10, 11), d(2026, 10, 21)), p.containing(d(2026, 10, 20)))
        assertEquals(DatePeriod(d(2026, 9, 21), d(2026, 10, 1)), p.containing(d(2026, 9, 30)), "before the anchor it keeps tiling")
        assertEquals(10, p.containing(d(2031, 1, 1)).days)
        assertEquals(BudgetPeriod.BiWeekly(anchor).containing(d(2026, 6, 6)), BudgetPeriod.EveryDays(14, anchor).containing(d(2026, 6, 6)), "a fortnight is the 14-day case")
        assertFailsWith<IllegalArgumentException> { BudgetPeriod.EveryDays(0, anchor) }
        assertFailsWith<IllegalArgumentException> { BudgetPeriod.EveryDays(367, anchor) }
    }
}
