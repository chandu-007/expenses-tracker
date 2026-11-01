package app.expenses.core.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the home-screen widgets say and how much of it fits: pure data, so no phone is needed. All amounts are invented. */
class WidgetSummaryTest {
    private val inr = Currency.INR
    private fun rs(n: Long) = Money(n * 100, inr)
    private val ist = TimeZone.of("Asia/Kolkata")

    // ---- the comparison with last month ----

    @Test
    fun theComparisonSaysLessMoreOrTheSameInWholePercentAndHasNothingWithoutALastMonth() {
        val less = WidgetSummary.delta(rs(8_800), rs(10_000))!!
        assertEquals(DeltaDirection.LESS, less.direction); assertEquals("↓", less.arrow); assertEquals("12%", less.percent); assertEquals("12% less than last month so far", less.spoken)
        val more = WidgetSummary.delta(rs(13_000), rs(10_000))!!
        assertEquals(DeltaDirection.MORE, more.direction); assertEquals("↑", more.arrow); assertEquals("30%", more.percent)
        assertEquals(DeltaDirection.SAME, WidgetSummary.delta(rs(10_000), rs(10_000))!!.direction)
        assertEquals(DeltaDirection.SAME, WidgetSummary.delta(Money(1_004_000, inr), rs(10_000))!!.direction, "a change under half a percent is the same")
        assertNull(WidgetSummary.delta(rs(500), Money.zero(inr)), "nothing last month, nothing to compare")
        assertNull(WidgetSummary.delta(rs(500), Money(-1, inr)))
        assertEquals("999%+", WidgetSummary.delta(rs(1_000_000), rs(10))!!.percent, "a huge jump is capped")
        assertEquals(DeltaDirection.LESS, WidgetSummary.delta(Money.zero(inr), rs(10))!!.direction); assertEquals("100%", WidgetSummary.delta(Money.zero(inr), rs(10))!!.percent)
    }

    // ---- the budget bar ----

    @Test
    fun theBudgetBarIsTheShareUsedNeverMoreThanFullAndSaysWhatIsLeftOrOver() {
        val b = WidgetSummary.budget(rs(20_000), rs(15_000))!!
        assertEquals(750, b.permille); assertEquals(false, b.over); assertEquals("₹5K left of ₹20K", b.text)
        val over = WidgetSummary.budget(rs(20_000), rs(23_500))!!
        assertEquals(1000, over.permille); assertEquals(true, over.over); assertEquals("₹3.5K over ₹20K", over.text)
        assertEquals(1000, WidgetSummary.budget(rs(20_000), rs(20_000))!!.permille); assertEquals(false, WidgetSummary.budget(rs(20_000), rs(20_000))!!.over, "exactly at the limit is not over")
        assertEquals(0, WidgetSummary.budget(rs(20_000), Money.zero(inr))!!.permille)
        assertEquals(0, WidgetSummary.budget(rs(20_000), Money(-500, inr))!!.permille, "a negative total (refunds) draws an empty bar")
        assertNull(WidgetSummary.budget(null, rs(1))); assertNull(WidgetSummary.budget(Money.zero(inr), rs(1)))
    }

    // ---- top categories ----

    @Test
    fun theTopCategoriesAreBiggestFirstWithSubcategoriesUnderTheirParentAndAShareOfTheMonth() {
        val spend = mapOf<String?, Money>("food" to rs(3_000), "groceries" to rs(1_000), "fun" to rs(500), "bills" to rs(4_000), null to rs(1_500))
        val bars = WidgetSummary.topCategories(spend, parentOf = mapOf("groceries" to "food"), names = mapOf("food" to "Food", "fun" to "Fun", "bills" to "Bills"), max = 3)
        assertEquals(listOf("Food", "Bills", "Uncategorised"), bars.map { it.name })
        assertEquals(listOf("₹4K", "₹4K", "₹1.5K"), bars.map { it.amountText })
        assertEquals(listOf(400, 400, 150), bars.map { it.permille }, "food 3,000 + groceries 1,000 is 4,000 of 10,000")
        assertEquals(emptyList(), WidgetSummary.topCategories(emptyMap(), emptyMap(), emptyMap(), 3))
        assertEquals(emptyList(), WidgetSummary.topCategories(spend, emptyMap(), emptyMap(), 0))
        assertEquals(emptyList(), WidgetSummary.topCategories(mapOf("x" to Money.zero(inr)), emptyMap(), emptyMap(), 3), "zero and negative spends are left out")
    }

    // ---- recent entries ----

    @Test
    fun recentEntriesNameTheShopSignTheAmountAndSayWhen() {
        val now = LocalDate(2026, 11, 10).atStartOfDayIn(ist).toEpochMilliseconds() + 14 * 3_600_000L
        fun at(day: Int) = LocalDate(2026, 11, day).atStartOfDayIn(ist).toEpochMilliseconds() + 10 * 3_600_000L
        val rows = listOf(
            RecentInput("EXPENSE", "Greencart", "Groceries", rs(482), at(10)),
            RecentInput("TRANSFER", "Move", null, rs(5_000), at(10)),
            RecentInput("INCOME", null, "Salary", rs(50_000), at(9)),
            RecentInput("EXPENSE", " ", null, Money(123_456_78, inr), at(3)),
            RecentInput("EXPENSE", "Fifth", null, rs(5), at(2)),
        )
        val r = WidgetSummary.recent(rows, now, ist, max = 4)
        assertEquals(4, r.size, "the transfer is not listed"); assertEquals(listOf("Greencart", "Salary", "Expense"), r.take(3).map { it.title })
        assertEquals(listOf("-₹482.00", "+₹50,000.00", "-₹1,23,456.78"), r.map { it.amountText }.take(3), "rupees in lakh grouping, income with a plus")
        assertEquals(listOf("Today", "Yesterday", "3 Nov"), r.map { it.whenText }.take(3))
        assertEquals(listOf(false, true, false), r.map { it.income }.take(3))
        assertEquals(emptyList(), WidgetSummary.recent(rows, now, ist, 0)); assertEquals(emptyList(), WidgetSummary.recent(emptyList(), now, ist, 3))
        assertEquals(1, WidgetSummary.recent(rows, now, ist, 1).size)
    }

    @Test
    fun theDayWordUsesTheDayOnThePhoneNotTheHourAndHandlesMonthEnds() {
        val today = LocalDate(2026, 11, 1)
        assertEquals("Yesterday", WidgetSummary.dayWord(LocalDate(2026, 10, 31), today)); assertEquals("29 Oct", WidgetSummary.dayWord(LocalDate(2026, 10, 29), today))
        assertEquals("Today", WidgetSummary.dayWord(today, today))
    }

    // ---- privacy ----

    @Test
    fun noFigureBeyondTheTotalIsGivenUnlessTheTotalMayBeShown() {
        val d = WidgetSummary.delta(rs(1), rs(2)); val b = WidgetSummary.budget(rs(10), rs(1))
        val cats = listOf(WidgetCategoryBar("Food", "₹1", 1000)); val recent = listOf(WidgetRecent("Greencart", "-₹1.00", "Today", false))
        assertEquals(WidgetInsights.NONE, WidgetSummary.insights(false, d, b, cats, recent), "masked: no comparison, no budget, no categories, no shops")
        val shown = WidgetSummary.insights(true, d, b, cats, recent)
        assertEquals(d, shown.delta); assertEquals(b, shown.budget); assertEquals(cats, shown.categories); assertEquals(recent, shown.recent)
    }

    // ---- what fits ----

    private fun have(chips: Int = 3, cats: Int = 4, recent: Int = 4, delta: Boolean = true, budget: Boolean = true, undo: Boolean = false, revealed: Boolean = true) = WidgetHave(chips, cats, recent, delta, budget, undo, revealed)

    @Test
    fun theSizeDecidesTheTierAndTheLayoutNeverClaimsMoreThanThereIsOrRoomFor() {
        assertEquals(WidgetTier.TINY, WidgetSummary.plan(110, 60, have()).tier); assertEquals(WidgetTier.TINY, WidgetSummary.plan(110, 40, have()).tier)
        assertEquals(WidgetTier.SQUARE, WidgetSummary.plan(110, 110, have()).tier); assertEquals(true, WidgetSummary.plan(110, 110, have()).showDelta)
        assertEquals(false, WidgetSummary.plan(110, 110, have(revealed = false)).showDelta, "a comparison is a figure: not while the total is hidden")
        assertEquals(WidgetTier.WIDE, WidgetSummary.plan(250, 60, have()).tier)
        assertEquals(0, WidgetSummary.plan(250, 60, have()).chips, "four narrow cells have room for the figure, the eye and Add, not for a chip")
        assertEquals(1, WidgetSummary.plan(320, 60, have()).chips); assertEquals(2, WidgetSummary.plan(400, 60, have()).chips, "two chips only when the width allows"); assertEquals(1, WidgetSummary.plan(400, 60, have(chips = 1)).chips)
        assertEquals(true, WidgetSummary.plan(250, 60, have()).chipsInHeader); assertEquals(false, WidgetSummary.plan(250, 60, have()).wideAdd); assertEquals(true, WidgetSummary.plan(320, 60, have()).wideAdd)
    }

    @Test
    fun aCardGivesItsSpaceToChipsThenTheBudgetThenCategoriesThenRecentEntriesAndShowsTheComparisonInItsHeader() {
        val medium = WidgetSummary.plan(320, 110, have())
        assertEquals(WidgetTier.CARD, medium.tier); assertEquals(3, medium.chips, "the default 4 x 2 keeps its chips"); assertEquals(true, medium.showDelta, "the comparison costs no height"); assertEquals(false, medium.showBudget); assertEquals(0, medium.categories); assertEquals(0, medium.recent)
        assertEquals(false, WidgetSummary.plan(250, 146, have()).showDelta, "a narrow header has no room for the comparison")
        val roomier = WidgetSummary.plan(320, 146, have())
        assertEquals(3, roomier.chips); assertEquals(true, roomier.showBudget); assertEquals(0, roomier.categories)
        val tall = WidgetSummary.plan(320, 294, have())
        assertTrue(tall.chips == 3 && tall.showDelta && tall.showBudget && tall.categories == 3 && tall.recent >= 2, "a 4 x 4 holds both lists: $tall")
        val huge = WidgetSummary.plan(320, 420, have())
        assertEquals(3, huge.categories); assertEquals(4, huge.recent)
        assertEquals(4, WidgetSummary.plan(320, 420, have(recent = 0)).categories, "with no recent entries the categories may have a fourth row")
        // never more rows than exist
        val few = WidgetSummary.plan(320, 420, have(cats = 2, recent = 1, chips = 1))
        assertEquals(2, few.categories); assertEquals(1, few.recent); assertEquals(1, few.chips)
        // an undo line takes its room before anything else
        assertEquals(true, WidgetSummary.plan(320, 110, have(undo = true, chips = 0)).showUndo)
        assertEquals(false, WidgetSummary.plan(320, 100, have(undo = true, chips = 3)).showUndo, "100 dp holds the header and one row only")
    }

    @Test
    fun withTheFiguresHiddenOnlyTheChipsAndAQuietNoteAreDrawn() {
        val p = WidgetSummary.plan(320, 294, have(revealed = false))
        assertEquals(WidgetTier.CARD, p.tier); assertEquals(3, p.chips)
        assertEquals(false, p.showDelta); assertEquals(false, p.showBudget); assertEquals(0, p.categories); assertEquals(0, p.recent); assertEquals(true, p.hiddenNote)
        assertEquals(false, WidgetSummary.plan(320, 110, have(revealed = false)).hiddenNote, "no note where there is no room for one")
        assertEquals(false, WidgetSummary.plan(320, 294, have()).hiddenNote)
    }

    @Test
    fun withNoChipsTheCardSaysHowToGetSome() {
        assertEquals(true, WidgetSummary.plan(320, 146, have(chips = 0)).showHint); assertEquals(false, WidgetSummary.plan(320, 146, have(chips = 2)).showHint)
    }

    @Test
    fun anOddOrTinySizeStillGivesAPlan() {
        for (w in listOf(0, 40, 109, 179, 180, 299, 300, 500)) for (h in listOf(0, 30, 99, 100, 109, 500)) {
            val p = WidgetSummary.plan(w, h, have())
            assertTrue(p.chips in 0..3 && p.categories in 0..4 && p.recent in 0..4, "$w x $h: $p")
        }
    }
}
