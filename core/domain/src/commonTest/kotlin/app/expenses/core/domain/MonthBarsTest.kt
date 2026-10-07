package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthBarsTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    @Test
    fun aRangeIsCutAtMonthEndsAndEndsStayPartial() {
        assertEquals(
            listOf(DatePeriod(d(2026, 10, 20), d(2026, 11, 1)), DatePeriod(d(2026, 11, 1), d(2026, 12, 1)), DatePeriod(d(2026, 12, 1), d(2026, 12, 6))),
            ReportMath.monthBars(DatePeriod(d(2026, 10, 20), d(2026, 12, 6))),
        )
        assertEquals(listOf(DatePeriod(d(2026, 10, 9), d(2026, 10, 10))), ReportMath.monthBars(DatePeriod(d(2026, 10, 9), d(2026, 10, 10))), "a single day")
        assertEquals(12, ReportMath.monthBars(BudgetPeriod.Yearly().containing(d(2026, 7, 1))).size, "a financial year is twelve bars")
        assertEquals(DatePeriod(d(2027, 3, 1), d(2027, 4, 1)), ReportMath.monthBars(BudgetPeriod.Yearly().containing(d(2026, 7, 1))).last())
    }

    @Test
    fun barsTileAnyRangeExactlyOnceAndLongRangesKeepTheLastTwentyFour() = runTest {
        checkAll(Arb.int(0, 4000), Arb.int(1, 1500)) { offset, length ->
            val start = d(2010, 1, 1).plus(offset, DateTimeUnit.DAY)
            val range = DatePeriod(start, start.plus(length, DateTimeUnit.DAY))
            val bars = ReportMath.monthBars(range)
            assertTrue(bars.size <= 24)
            if (length < 28 * 24) {
                assertEquals(range.start, bars.first().start); assertEquals(range.endExclusive, bars.last().endExclusive)
                assertEquals(range.days, bars.sumOf { it.days }, "no day twice, none missed")
            }
            assertEquals(range.endExclusive, bars.last().endExclusive, "the most recent months are kept")
            bars.zipWithNext { a, b -> assertEquals(a.endExclusive, b.start) }
        }
    }
}
