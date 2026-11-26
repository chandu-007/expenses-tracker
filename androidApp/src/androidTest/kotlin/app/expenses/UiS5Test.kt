package app.expenses

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.PaceLevel
import app.expenses.core.domain.PaceMath
import app.expenses.core.domain.ReportMath
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UI slice S5: pace on the month card and the bars (one "a day" figure, the words, the ghost line), the Budget screen's ring and limits, goals' rings, categories' spend and drag, Customise home's drag. */
@RunWith(AndroidJUnit4::class)
class UiS5Test {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1"); it.templates.putSetting("category_look_notice", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun textOf(tag: String) = node(tag).fetchSemanticsNode().config.let { c -> c.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() }
    private fun texts(tag: String): List<String> = node(tag).fetchSemanticsNode().let { n ->
        fun walk(x: androidx.compose.ui.semantics.SemanticsNode): List<String> = (x.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + x.children.flatMap { walk(it) }
        walk(n)
    }
    private fun noonOf(d: LocalDate) = d.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L
    private fun spend(id: String, rupees: Long, category: String, at: Long = System.currentTimeMillis()) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), at, AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))), EntryDetails(merchant = id)) }
    }
    private fun limit(rupees: Long, category: String? = null) = runBlocking { container.write { it.budgets.setLimit(category, Money(rupees * 100, inr)) } }
    private val period get() = ReportMath.periodAt(BudgetPeriod.Monthly(1), today, 0)

    @Test
    fun theMonthCardHasOneADayFigureAndHowLongItHasToLastAndTheDayOfThePeriod() {
        limit(30_000); spend("a", 1_000, "Food"); launch(); waitFor("hero_pace_line")
        val line = textOf("hero_pace_line")
        assertTrue(line.contains(" a day · ") && line.contains("month end"), "one figure and what it has to last: $line")
        assertEquals("Day ${PaceMath.daysIn(period, today)} of ${period.days}", textOf("hero_day_of"))
        assertFalse(has("budget_per_day") || has("home_safe_to_spend_line_per_day"), "no second a-day figure")
    }

    @Test
    fun aSalaryDayStartMakesItPayday() {
        runBlocking { container.write { it.budgets.setPeriod(BudgetPeriod.Monthly(15)) } }
        limit(30_000); spend("a", 1_000, "Food"); launch(); waitFor("hero_pace_line")
        assertTrue(textOf("hero_pace_line").contains("payday"), textOf("hero_pace_line"))
    }

    @Test
    fun overTheLimitTheCardSaysByHowMuchInsteadOfADayFigure() {
        limit(100); spend("a", 500, "Food"); launch(); waitFor("hero_pace_line")
        assertEquals("Over by ₹400.00", textOf("hero_pace_line"))
    }

    @Test
    fun withoutABudgetOrASafeToSpendLineThereIsNoPaceLine() {
        spend("a", 1_000, "Food"); launch(); waitFor("month_spend")
        assertFalse(has("hero_pace_line") || has("hero_day_of"))
    }

    @Test
    fun theWordOnTheBudgetTileIsTheOneThePaceMathGives() {
        limit(10_000); spend("a", 100, "Food"); launch(); waitFor("budget_pace_word")
        val spent = 10_000L; val lim = 1_000_000L
        val level = PaceMath.level(spent, lim, PaceMath.elapsed(period, today), PaceMath.daysIn(period, today), period.days)
        assertEquals(PaceMath.statusWord(level, PaceMath.isWellUnder(spent.toFloat() / lim, PaceMath.elapsed(period, today))), textOf("budget_pace_word"))
        assertTrue(level == PaceLevel.ON_PACE || level == PaceLevel.EARLY, "1% of the limit is never ahead of any day's pace")
    }

    @Test
    fun theMonthLinesDashedGhostIsLastMonthsSameDaysAndTheTableHasItsColumn() {
        val prev = ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -1)
        val shift = prev.start.daysUntil(period.start)
        spend("now", 250, "Food")
        spend("then", 90, "Food", noonOf(LocalDate.fromEpochDays(today.toEpochDays() - shift)))
        launch(); waitFor("home_spark")
        node("home_spark").performTouchInput { longClick() }
        waitFor("spark_table")
        assertEquals(3, texts("spark_table_row_13").size, "day, spent and last month: ${texts("spark_table_row_13")}")
        assertTrue(texts("spark_table_row_13").any { it.contains("90.00") }, "today's same day last month: ${texts("spark_table_row_13")}")
    }

    @Test
    fun theBudgetScreenOpensOnARingAndTheLimitsWithTheirWordsAndFoldsTheRest() {
        limit(50_000); limit(2_000, "Food"); spend("a", 500, "Food"); launch()
        tap("budget_summary"); waitFor("budget_ring")
        assertTrue(has("budget_pace_word") && has("budget_overall_note") && has("safe_to_spend"), "the ring, the word and the sentence")
        assertEquals("₹49,500.00 left of ₹50,000.00", textOf("budget_overall_note"))
        waitFor("budget_row_Food"); assertTrue(textOf("budget_row_word_Food").isNotEmpty())
        assertFalse(has("budget_row_Groceries"), "categories without a limit are folded")
        assertTrue(texts("budget_no_limit_toggle").joinToString(" ").contains("more categories have no limit"), texts("budget_no_limit_toggle").toString())
        tap("budget_no_limit_toggle"); waitFor("budget_row_Groceries")
        assertFalse(has("period_monthly") || has("budget_rollover"), "the period and carry over are behind the pencil")
        tap("budget_settings"); waitFor("period_monthly"); assertTrue(has("budget_rollover") && has("period_range"))
        tap("budget_settings_done")
    }

    @Test
    fun withoutAnOverallLimitTheBudgetScreenInvitesOne() {
        launch(); tap("budget_summary"); waitFor("budget_ring_empty")
        tap("budget_row_overall"); waitFor("limit_amount")
    }

    @Test
    fun hideAmountsMasksTheMonthCardsLineAndTheBudgetFigures() {
        limit(30_000); spend("a", 1_000, "Food"); runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        launch(); waitFor("hero_pace_line")
        assertFalse(Regex("₹\\d").containsMatchIn(textOf("hero_pace_line")), textOf("hero_pace_line"))
        tap("budget_summary"); waitFor("budget_ring")
        assertFalse(Regex("\\d{3}").containsMatchIn(textOf("budget_overall_note")), textOf("budget_overall_note"))
    }

    @Test
    fun aCategoryShowsWhatItTookThisPeriodAndADragOfItsHandleMovesIt() {
        spend("a", 500, "Food")
        launch(); compose.openMore(); tap("manage_categories"); waitFor("cat_note_Food")
        runCatching { compose.waitUntil(15_000) { textOf("cat_note_Food").contains("₹500.00 this period") } }
        assertTrue(textOf("cat_note_Food").contains("₹500.00 this period"), "the note says: ${textOf("cat_note_Food")}")
        assertTrue(has("cat_bar_Food"), "a mini bar for the one that took money")
        val before = node("cat_row_Food").getUnclippedBoundsInRoot().top.value
        val groceries = node("cat_row_Groceries").getUnclippedBoundsInRoot().top.value
        assertTrue(before < groceries)
        val rowPx = (node("cat_row_Food").fetchSemanticsNode().size.height).toFloat()
        node("drag_Food").performTouchInput { down(center); advanceEventTime(900); moveBy(Offset(0f, rowPx * 1.2f), delayMillis = 100); up() }
        // the list keeps the first row in view where it was, so the row that went up may be above the top: the order is read from the store
        fun order() = runBlocking { container.write { ok -> ok.categories.list().filter { it.parentId == null }.sortedBy { it.position }.map { it.id } } }!!
        runCatching { compose.waitUntil(10_000) { order().indexOf("Food") > order().indexOf("Groceries") } }
        assertTrue(order().indexOf("Food") > order().indexOf("Groceries"), "Food is now below Groceries: ${order().take(4)}")
        // the arrows are still there for anyone who cannot drag
        assertTrue(has("up_Food") && has("down_Food"))
    }

    @Test
    fun customiseHomeBlocksMoveByDragAsWellAsByArrows() {
        launch(); compose.openMore(); tap("open_home_layout"); waitFor("home_row_BUDGET")
        val first = app.expenses.core.domain.HomeLayout.decode(null).order.first()
        val second = app.expenses.core.domain.HomeLayout.decode(null).order[1]
        val rowPx = node("home_row_${first.name}").fetchSemanticsNode().size.height.toFloat()
        node("home_drag_${first.name}").performTouchInput { down(center); advanceEventTime(900); moveBy(Offset(0f, rowPx * 1.1f), delayMillis = 100); up() }
        compose.waitUntil(15_000) {
            val order = runBlocking { container.write { app.expenses.core.domain.HomeLayout.decode(it.templates.setting("home_layout")).order } }!!
            order.indexOf(first) == 1 && order.indexOf(second) == 0
        }
        assertTrue(has("home_up_${first.name}") && has("home_down_${first.name}"))
    }
}
