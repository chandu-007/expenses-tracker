package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReportMathTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private fun cat(id: String, parent: String? = null) = Category(id, id.replaceFirstChar { it.uppercase() }, parent, null, 0, false)
    private val cats = listOf(cat("food"), cat("cafe", "food"), cat("fun"), cat("bills"))

    private fun rows(vararg spent: Pair<String?, Long>) = ReportMath.breakdown(spent.associate { it.first to m(it.second) }, cats, inr)

    @Test
    fun subcategoriesRollIntoTheirParentAndRowsAreLargestFirst() {
        val r = rows("food" to 6_000, "cafe" to 2_500, "fun" to 9_000, "bills" to 500)
        assertEquals(listOf("fun", "food", "bills"), r.map { it.categoryId })
        val food = r[1]
        assertEquals(m(8_500), food.total, "food plus cafe")
        assertEquals(listOf("cafe"), food.children.map { it.categoryId })
        assertEquals(m(2_500), food.children.single().total)
        assertEquals(m(18_000), r.fold(Money.zero(inr)) { a, row -> a + row.total }, "rows add up to everything")
    }

    @Test
    fun aSubcategoryWithNoSpendingOfItsParentStillAppearsUnderTheParent() {
        val r = rows("cafe" to 1_000)
        assertEquals("food", r.single().categoryId)
        assertEquals(m(1_000), r.single().total)
        assertEquals("cafe", r.single().children.single().categoryId)
    }

    @Test
    fun sharesAreWholePercentsOfPositiveSpendingAndUncategorisedIsListed() {
        val r = rows("fun" to 7_500, "bills" to 2_500, null to 10_000)
        assertEquals(listOf(50, 37, 12), listOf(null, "fun", "bills").map { id -> r.first { it.categoryId == id }.share })
        assertEquals(ReportMath.UNCATEGORISED, r.first { it.categoryId == null }.name)
        assertTrue(r.sumOf { it.share } <= 100, "rounding down never makes shares exceed 100")
    }

    @Test
    fun refundsPastZeroGiveAnOffsettingNegativeRowWithNoShareAndZeroRowsAreDropped() {
        val r = rows("fun" to -300, "bills" to 1_000, "food" to 0)
        assertEquals(listOf("bills", "fun"), r.map { it.categoryId })
        assertEquals(0, r.first { it.categoryId == "fun" }.share)
        assertEquals(100, r.first { it.categoryId == "bills" }.share, "shares are of positive spending only")
    }

    @Test
    fun unknownCategoriesAreShownByIdInsteadOfBeingDropped() {
        val r = rows("ghost" to 400, "fun" to 100)
        assertEquals(m(500), r.fold(Money.zero(inr)) { a, row -> a + row.total })
        assertEquals("ghost", r.first { it.categoryId == "ghost" }.name)
    }

    @Test
    fun theTotalOfTheRowsAlwaysEqualsTheTotalSpentWhateverTheInput() = runTest {
        val ids = listOf("food", "cafe", "fun", "bills", "ghost", null)
        checkAll(Arb.list(Arb.long(-5_000, 50_000), 0..6)) { amounts ->
            val spent = amounts.mapIndexed { i, a -> ids[i] to m(a) }.toMap()
            val r = ReportMath.breakdown(spent, cats, inr)
            assertEquals(spent.values.sumOf { it.minorUnits }, r.sumOf { it.total.minorUnits })
            assertTrue(r.sumOf { it.share } <= 100)
            r.forEach { row -> if (row.children.isNotEmpty()) assertEquals(row.total.minorUnits, row.children.sumOf { it.total.minorUnits } + (spent[row.categoryId]?.minorUnits ?: 0L)) }
        }
    }

    @Test
    fun percentChangeRoundsToTheNearestWholePercentAndNeedsSomethingToCompareWith() {
        assertEquals(20, ReportMath.percentChange(m(12_000), m(10_000)))
        assertEquals(-25, ReportMath.percentChange(m(7_500), m(10_000)))
        assertEquals(0, ReportMath.percentChange(m(10_000), m(10_000)))
        assertEquals(33, ReportMath.percentChange(m(13_333), m(10_000)))
        assertEquals(67, ReportMath.percentChange(m(16_667), m(10_000)), "66.67 rounds up")
        assertEquals(-67, ReportMath.percentChange(m(3_333), m(10_000)), "-66.67 rounds away from zero, not toward it")
        assertEquals(-100, ReportMath.percentChange(m(0), m(10_000)))
        assertNull(ReportMath.percentChange(m(5_000), m(0)))
        assertNull(ReportMath.percentChange(m(5_000), m(-100)))
    }

    @Test
    fun steppingBackAndForwardWalksTheCalendarWithNoGapForEveryKindOfPeriod() {
        val today = LocalDate(2026, 10, 7)
        for (period in listOf(BudgetPeriod.Monthly(1), BudgetPeriod.Monthly(25), BudgetPeriod.Weekly(kotlinx.datetime.DayOfWeek.WEDNESDAY))) {
            assertEquals(period.containing(today), ReportMath.periodAt(period, today, 0))
            val walked = (-30..5).map { ReportMath.periodAt(period, today, it) }
            walked.zipWithNext().forEach { (a, b) -> assertEquals(a.endExclusive, b.start, "$period: no gap or overlap") }
            assertTrue(today in walked[30])
        }
        assertEquals(DatePeriod(LocalDate(2026, 9, 25), LocalDate(2026, 10, 25)), ReportMath.periodAt(BudgetPeriod.Monthly(25), today, 0))
        assertEquals(DatePeriod(LocalDate(2026, 8, 25), LocalDate(2026, 9, 25)), ReportMath.periodAt(BudgetPeriod.Monthly(25), today, -1))
    }

    @Test
    fun yearsAndTheirMonthsTileExactly() {
        val today = LocalDate(2026, 10, 7)
        assertEquals(DatePeriod(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1)), ReportMath.yearAt(today, 0))
        assertEquals(DatePeriod(LocalDate(2024, 1, 1), LocalDate(2025, 1, 1)), ReportMath.yearAt(today, -2))
        for (year in listOf(2026, 2028)) {
            val months = ReportMath.monthsOf(year)
            assertEquals(12, months.size)
            assertEquals(ReportMath.yearAt(LocalDate(year, 6, 1), 0).start, months.first().start)
            assertEquals(ReportMath.yearAt(LocalDate(year, 6, 1), 0).endExclusive, months.last().endExclusive)
            months.zipWithNext().forEach { (a, b) -> assertEquals(a.endExclusive, b.start) }
            assertEquals(if (year == 2028) 29 else 28, months[1].days, "February in $year")
        }
    }
}
