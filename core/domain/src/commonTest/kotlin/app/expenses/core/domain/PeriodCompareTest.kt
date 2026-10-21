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

class SpendingInsightTest {
    private val rupees: (Long) -> String = { "₹${it / 100}" }

    @Test
    fun theCategoryFurthestAboveItsUsualIsNamedWithTheNumbers() {
        val lines = listOf(InsightLine("Food", 420_000, 300_000), InsightLine("Fun", 200_000, 100_000), InsightLine("Bills", 100_000, 100_000))
        assertEquals("Fun is 100% above your usual (₹2000 against ₹1000 by now)", SpendingInsight.best(lines.filter { it.name != "Food" }, rupees))
        assertEquals("Food is 40% above your usual (₹4200 against ₹3000 by now)", SpendingInsight.best(listOf(lines[0], lines[2]), rupees), "the biggest rise in money, not in percent, wins among those that qualify")
    }

    @Test
    fun nothingIsSaidForSmallOrNewOrUnderspentCategories() {
        assertNull(SpendingInsight.best(listOf(InsightLine("Tea", 20_000, 5_000)), rupees), "300 more is below the 500 floor")
        assertNull(SpendingInsight.best(listOf(InsightLine("Food", 110_000, 100_000)), rupees), "10% is not much")
        assertNull(SpendingInsight.best(listOf(InsightLine("New", 900_000, 0)), rupees), "no usual to compare with")
        assertNull(SpendingInsight.best(listOf(InsightLine("Food", 50_000, 300_000)), rupees), "below usual is good news, not an insight")
        assertNull(SpendingInsight.best(emptyList(), rupees))
    }
}
