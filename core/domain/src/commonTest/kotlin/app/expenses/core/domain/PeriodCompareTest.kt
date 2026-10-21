package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeriodCompareTest {
    private val rupees: (Long) -> String = { "₹${it / 100}" }

    @Test
    fun moreLessAndTheSameAreWordedWithTheDifferenceAndItsPercent() {
        assertEquals("▲ ₹250 more than last month so far (+25%)", PeriodCompare.describe(125_000, 100_000, "last month", rupees))
        assertEquals("▼ ₹300 less than last month so far (−30%)", PeriodCompare.describe(70_000, 100_000, "last month", rupees))
        assertEquals("Same as last month so far", PeriodCompare.describe(100_000, 100_000, "last month", rupees))
    }

    @Test
    fun nothingEarlierMeansNothingToSayAndAHugeJumpIsCapped() {
        assertNull(PeriodCompare.describe(50_000, 0, "last month", rupees))
        assertNull(PeriodCompare.describe(0, 0, "last month", rupees))
        assertEquals("▲ ₹99999 more than last month so far (+999%)", PeriodCompare.describe(10_000_000, 100, "last month", rupees))
        assertEquals("▼ ₹1000 less than last month so far (−100%)", PeriodCompare.describe(0, 100_000, "last month", rupees))
    }

    @Test
    fun everyKindOfPeriodHasANameExceptTheYear() {
        assertEquals("last month", PeriodCompare.lastName(BudgetPeriod.Monthly(1)))
        assertEquals("the last cycle", PeriodCompare.lastName(BudgetPeriod.Monthly(15)))
        assertEquals("last week", PeriodCompare.lastName(BudgetPeriod.Weekly(kotlinx.datetime.DayOfWeek.MONDAY)))
        assertNull(PeriodCompare.lastName(BudgetPeriod.Yearly(4)))
    }
}
