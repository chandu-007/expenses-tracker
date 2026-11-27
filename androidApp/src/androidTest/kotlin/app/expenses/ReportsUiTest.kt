package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.DatePattern
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ReportMath
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReportsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    private fun resetApp() {
        runBlocking { container.startFresh() }
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
    }

    private fun openReports() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        compose.onNodeWithTag("open_reports").performClick()
        waitFor("report_spent")
    }

    // the page is a lazy list and the rows are below the summary, the ring and the chart: scroll to a row before waiting for it
    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        if (compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() && compose.onAllNodes(hasTestTag("report_list"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            runCatching { compose.onNodeWithTag("report_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) }
            runCatching { clearTheStickyHeader(tag) }
        }
        compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** A lazy list scrolls an item to the very top, which is under the period row that stays on screen: move the page down by what the row covers, so a click on the item reaches it. */
    private fun clearTheStickyHeader(tag: String) {
        fun bottomOf(t: String) = compose.onAllNodes(hasTestTag(t), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.bottom
        val header = listOfNotNull(bottomOf("report_title"), bottomOf("report_open_note")).maxOrNull() ?: return
        val top = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top ?: return
        val need = header + 40f - top
        if (need > 0f) compose.onNodeWithTag("report_list", useUnmergedTree = true).performTouchInput { swipeDown(startY = centerY, endY = centerY + need + 30f, durationMillis = 300) }
    }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) {
        // a lazy list composes only what is on screen: the title above the summary is not there once the list has scrolled to the empty message below it
        if (compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() && compose.onAllNodes(hasTestTag("report_list"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            runCatching { compose.onNodeWithTag("report_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) }
        compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun spend(id: String, rupees: Long, category: String, at: Long = System.currentTimeMillis(), note: String = id) = runBlocking {
        container.write { ok ->
            ok.store.save(Expense(EntryId(id), at, AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))), EntryDetails(merchant = note))
        }
    }

    private fun noonOf(d: LocalDate) = d.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L
    private fun previousPeriodNoon() = noonOf(ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -1).start)

    @Test
    fun theRingNamesTheBiggestSlicesWithTheirSharesGroupsTheRestAndEachSliceOpensItsEntries() {
        // seven categories: the ring shows the five biggest and "Others"; shares are of the 2,100 spent (1,000 + 400 + 300 + 200 + 100 + 60 + 40), rounded down
        listOf("Food" to 1000L, "Groceries" to 400L, "Transport" to 300L, "Bills" to 200L, "Shopping" to 100L, "Health" to 60L, "Fun" to 40L).forEachIndexed { i, (c, r) -> spend("r$i", r, c) }
        openReports()
        waitFor("breakdown_Food")
        fun hasShare(tag: String, text: String) = compose.onAllNodes(hasText(text) and androidx.compose.ui.test.hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue(hasShare("breakdown_Food", "47%"), "1000 of 2100, rounded down like every share")
        assertTrue(hasShare("breakdown_Groceries", "19%") && hasShare("breakdown_Transport", "14%"), "the key names the three biggest")
        assertTrue(compose.onAllNodes(hasTestTag("breakdown_Bills"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "and only those: the rest are in the ring and the list")
        assertTrue(compose.onAllNodes(hasTestTag("breakdown_Fun"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "the smallest are inside Others, not slices of their own")
        waitFor("breakdown_all"); assertTrue(textsOf("breakdown_all").any { it.startsWith("All 7 categories") }, "the link names how many: ${textsOf("breakdown_all")}")
        waitFor("report_row_Bills")
        assertTrue(compose.onAllNodes(hasTestTag("report_row_Fun"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "the list shows the six biggest; the seventh is behind Show all")
        compose.onNodeWithTag("breakdown_all", useUnmergedTree = true).performClick()
        waitFor("report_row_Fun")
        compose.onNodeWithTag("ring_table_toggle", useUnmergedTree = true).performScrollTo().performClick(); waitFor("ring_table")
        assertTrue(hasShare("ring_table_row_5", "7%"), "what the five biggest (47 + 19 + 14 + 9 + 4) leave of 100, in the table of the ring")
        waitFor("breakdown_Food"); clearTheStickyHeader("breakdown_Food")
        compose.onNodeWithTag("breakdown_Food", useUnmergedTree = true).performClick()
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹1,000.00")
    }

    @Test
    fun theTotalsAndCategoryRowsAreRightAndEveryRowOpensIntoEntriesThatAddUpToIt() {
        spend("a", 600, "Food"); spend("b", 400, "Food"); spend("c", 300, "Fun")
        runBlocking { container.write { it.store.save(Income(EntryId("pay"), System.currentTimeMillis(), AccountId("bank"), Money(500_000, inr), CategoryId("Other")), EntryDetails(merchant = "Salary")) } }
        openReports()
        compose.onNodeWithTag("report_spent", useUnmergedTree = true).assertTextEquals("₹1,300.00")
        compose.onNodeWithTag("report_income", useUnmergedTree = true).assertTextEquals("₹5,000.00")
        compose.onNodeWithTag("report_net", useUnmergedTree = true).assertTextEquals("₹3,700.00")
        waitFor("report_row_Food")
        compose.onNodeWithTag("report_row_Food").performClick()
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹1,000.00")
        waitText("drill_count", "2 entries")
        compose.onNodeWithTag("drill_back").performClick()
        waitFor("report_spent")
        compose.onNodeWithTag("report_spent_card").performClick()
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹1,300.00")
        compose.onNodeWithTag("drill_back").performClick()
        waitFor("report_income_card")
        compose.onNodeWithTag("report_income_card").performClick()
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹5,000.00")
    }

    private fun textsOf(tag: String): List<String> {
        fun walk(x: androidx.compose.ui.semantics.SemanticsNode): List<String> = (x.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + x.children.flatMap { walk(it) }
        return walk(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode())
    }

    private fun vm() = androidx.lifecycle.ViewModelProvider(run { var a: MainActivity? = null; scenario!!.onActivity { a = it }; a!! })[LedgerViewModel::class.java]

    @Test
    fun groupingByAccountTagAndMerchantEachAddsUpAndEveryRowDrillsToItsEntries() {
        runBlocking {
            container.write { ok ->
                fun e(id: String, rupees: Long, account: String, merchant: String, tags: List<String>) =
                    ok.store.save(Expense(EntryId(id), System.currentTimeMillis(), AccountId(account), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId("Food"), Money(rupees * 100, inr)))), EntryDetails(merchant = merchant, tags = tags))
                e("a", 600, "cash", "Swiggy", listOf("Trip")); e("b", 400, "bank", "swiggy", listOf("Trip", "Tax")); e("c", 300, "bank", "Uber", emptyList())
            }
        }
        openReports()
        waitFor("group_ACCOUNT") // the grouping sits above the ring, below the bars: the page is scrolled to it
        compose.onNodeWithTag("group_ACCOUNT").performClick()
        waitFor("report_group_row_bank"); waitFor("report_group_row_cash")
        compose.onNodeWithTag("report_group_row_bank").performClick(); waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹700.00"); waitText("drill_count", "2 entries")
        compose.onNodeWithTag("drill_back").performClick(); waitFor("group_TAG")
        compose.onNodeWithTag("group_TAG").performClick(); waitFor("report_group_row_Trip"); waitFor("report_group_row_Tax"); waitFor("report_group_row_none")
        compose.onNodeWithTag("report_group_row_Trip").performClick(); waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹1,000.00")
        compose.onNodeWithTag("drill_back").performClick(); waitFor("group_MERCHANT")
        compose.onNodeWithTag("group_MERCHANT").performClick(); waitFor("report_group_row_swiggy")
        compose.onNodeWithTag("report_group_row_swiggy").performClick(); waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹1,000.00") // "Swiggy" and "swiggy" are one merchant
    }

    @Test
    fun theFinancialYearAndAnyDatesGiveTheirOwnTitlesTotalsAndMonthlyBars() {
        val fy = BudgetPeriod.Yearly().containing(today)
        spend("in", 500, "Food", noonOf(fy.start)); spend("before", 700, "Food", noonOf(fy.start) - 5 * 86_400_000L)
        val lastMonth = ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -1)
        openReports()
        compose.onNodeWithTag("mode_fy").performClick()
        waitText("report_title", "FY ${fy.start.year}–${(fy.start.year + 1) % 100}")
        waitText("report_spent", "₹500.00")
        compose.onNodeWithTag("report_next").assertIsNotEnabled()                   // this financial year is the latest
        compose.onNodeWithTag("report_prev").performClick()
        waitText("report_spent", "₹700.00"); waitText("report_title", "FY ${fy.start.year - 1}–${fy.start.year % 100}")
        // any dates: exactly the days chosen, inclusive
        vm().setReportRange(fy.start.toEpochDays().toLong() - 5, fy.start.toEpochDays().toLong() - 5)
        waitText("report_spent", "₹700.00")
        vm().setReportRange(fy.start.toEpochDays().toLong() - 4, fy.start.toEpochDays().toLong() + 3)
        waitText("report_spent", "₹500.00")
        vm().setReportRange(fy.start.toEpochDays().toLong() + 3, fy.start.toEpochDays().toLong() - 5) // end before start: swapped, not an error
        waitText("report_spent", "₹1,200.00")
        waitFor("custom_from"); waitFor("trend_bar_0")
        assertTrue(lastMonth.days > 0)
    }

    @Test
    fun aSubcategoryIsRolledIntoItsParentAndHasItsOwnRow() {
        val cafe = runBlocking { container.write { it.categories.create("Cafe", parentId = "Food").id } }!!
        spend("a", 600, "Food"); spend("b", 250, cafe)
        openReports()
        waitFor("report_row_Food")
        assertTrue(compose.onAllNodes(hasTestTag("report_row_$cafe"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "subcategories start collapsed under their category")
        waitFor("report_row_toggle_Food"); compose.onNodeWithTag("report_row_toggle_Food", useUnmergedTree = true).performClick()
        waitFor("report_row_$cafe")
        compose.onNodeWithTag("report_row_$cafe").performClick()
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹250.00")
        compose.onNodeWithTag("drill_back").performClick()
        waitFor("report_row_Food")
        compose.onNodeWithTag("report_row_Food").performClick()
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹850.00")
    }

    @Test
    fun steppingBackShowsTheEarlierPeriodAndTheTrendBarOpensItsEntries() {
        spend("now", 500, "Food")
        spend("before", 300, "Fun", at = previousPeriodNoon())
        openReports()
        compose.onNodeWithTag("report_next").assertIsNotEnabled()
        // the period is still going on: the 500 so far is set against the same days of the period before (its 300 is on its first day), the words say so, and the percent is Home's
        waitText("report_comparison", "₹200.00 more than ${DatePattern("MMM").format(ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -1).start)} so far (+66%)") // the node says the words, not the arrow
        compose.waitUntil(10_000) { textsOf("report_spent_card").contains("Spent so far") }
        waitFor("report_open_note")
        compose.onNodeWithTag("report_prev").performClick()
        waitText("report_spent", "₹300.00")
        compose.onNodeWithTag("report_next").assertIsEnabled()
        compose.onNodeWithTag("report_next").performClick()
        waitText("report_spent", "₹500.00")
        // the bar for the period before opens that period's entries, whichever period is selected
        compose.onNodeWithTag("trend_bar_4").performClick()
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹300.00")
    }

    @Test
    fun aFinishedPeriodIsComparedWholeWithWholeAndIsNotSoFar() {
        spend("now", 500, "Food"); spend("before", 300, "Fun", at = previousPeriodNoon()); spend("older", 900, "Fun", at = noonOf(ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -2).start))
        openReports()
        compose.onNodeWithTag("report_prev").performClick()
        waitText("report_spent", "₹300.00")
        val older = DatePattern("MMM").format(ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -2).start)
        waitText("report_comparison", "₹600.00 less than $older (−66%)")
        assertTrue(compose.onAllNodes(hasTestTag("report_open_note"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "a period that is over has no 'so far' note")
        assertTrue(compose.onAllNodes(hasTestTag("report_comparison") and hasText("so far", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "and the comparison does not say so far")
    }

    @Test
    fun theOpenPeriodIsNotComparedWithTheLateDaysOfThePeriodBefore() {
        val before = ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -1)
        val lived = today.toEpochDays() - ReportMath.periodAt(BudgetPeriod.Monthly(1), today, 0).start.toEpochDays() + 1
        org.junit.Assume.assumeTrue("on the last days of a month the same point is the whole of the month before", lived < before.days)
        spend("now", 500, "Food"); spend("early", 300, "Fun", at = noonOf(before.start)); spend("late", 700, "Fun", at = noonOf(before.endExclusive.minus(1, kotlinx.datetime.DateTimeUnit.DAY)))
        openReports()
        // against the first days only (300), not against all of it (1,000): "more", not "less"
        waitText("report_comparison", "₹200.00 more than")
    }

    @Test
    fun theHeroSaysWhatWasKeptAndTheBudgetLineIsThereOnlyForThePeriodBeingLivedAndOpensTheBudget() {
        spend("a", 600, "Food"); spend("b", 400, "Fun", at = previousPeriodNoon())
        runBlocking { container.write { ok ->
            ok.store.save(Income(EntryId("pay"), System.currentTimeMillis(), AccountId("bank"), Money(500_000, inr), CategoryId("Other")), EntryDetails(merchant = "Salary"))
            ok.budgets.setLimit(null, Money(200_000, inr))
        } }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add"); compose.waitForIdle()
        compose.onNodeWithTag("open_reports").performClick()
        waitFor("report_spent")
        compose.onNodeWithTag("report_net", useUnmergedTree = true).assertTextEquals("₹4,400.00")
        compose.waitUntil(10_000) { textsOf("report_income_card").any { it == "(88%)" } } // 4,400 of 5,000, rounded down
        waitFor("report_budget_line")
        val line = textsOf("report_budget_line").joinToString(" ")
        assertTrue(line.contains("₹1,400.00 left of ₹2,000.00"), "the exact amounts are what the node says: $line")
        compose.onNodeWithTag("report_prev").performClick()
        waitText("report_spent", "₹400.00")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("report_budget_line"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("report_next").performClick()
        waitText("report_spent", "₹600.00"); waitFor("report_budget_line")
        compose.onNodeWithTag("report_budget_line", useUnmergedTree = true).performClick()
        waitFor("budget_back") // the Budget screen
    }

    @Test
    fun aLedgerWithoutALimitHasNoBudgetLine() {
        spend("a", 600, "Food")
        openReports()
        assertTrue(compose.onAllNodes(hasTestTag("report_budget_line"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag("report_net"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "and with no income there is no 'Saved' or 'Over by': nothing came in to keep or overspend")
        assertTrue(textsOf("report_income_card").contains("Income"), "the income is still said")
    }

    @Test
    fun anOverBudgetLineSaysTheExactOverrunAndTheLimitAndHideAmountsMasksEveryFigureOfThePage() {
        listOf("Food" to 1000L, "Groceries" to 400L, "Transport" to 300L, "Bills" to 200L, "Shopping" to 100L, "Health" to 60L, "Fun" to 40L).forEachIndexed { i, (c, r) -> spend("r$i", r, c) }
        spend("before", 300, "Fun", at = previousPeriodNoon()); spend("older", 500, "Fun", at = noonOf(ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -2).start)) // two finished periods: the average has something to be of
        runBlocking { container.write { ok ->
            ok.store.save(Income(EntryId("pay"), System.currentTimeMillis(), AccountId("bank"), Money(500_000, inr), CategoryId("Other")), EntryDetails(merchant = "Salary"))
            ok.budgets.setLimit(null, Money(100_000, inr))
        } }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add"); compose.waitForIdle()
        compose.onNodeWithTag("open_reports").performClick()
        waitFor("report_budget_line")
        assertTrue(textsOf("report_budget_line").joinToString(" ").contains("Over by ₹1,100.00 · limit ₹1,000.00"), "the overrun is exact and the limit is named: ${textsOf("report_budget_line")}")
        // now hidden: no figure anywhere on the page, the percent and the words stay
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        scenario!!.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add"); compose.waitForIdle()
        compose.onNodeWithTag("open_reports").performClick()
        waitFor("report_budget_line")
        val figure = Regex("₹\\s*[0-9]")
        for (tag in listOf("report_spent", "report_comparison", "report_income", "report_net", "report_budget_line", "report_headline", "breakdown_Food", "breakdown_Groceries", "report_row_Food", "trend_average")) {
            waitFor(tag)
            assertTrue(!figure.containsMatchIn(textsOf(tag).joinToString(" ")), "$tag shows no figure: ${textsOf(tag)}")
        }
        waitFor("report_comparison") // the page is a lazy list and the loop above has scrolled down: the chip at the top is brought back first
        assertTrue(textsOf("report_comparison").joinToString(" ").contains("%"), "the percent is not an amount and stays")
    }

    @Test
    fun theYearViewAddsUpTheWholeYearAndOffersItsMonths() {
        val jan2 = noonOf(LocalDate(today.year, 1, 2))
        spend("now", 500, "Food"); spend("jan", 700, "Fun", at = jan2)
        openReports()
        compose.onNodeWithTag("mode_year").performClick()
        waitText("report_title", today.year.toString())
        waitText("report_spent", "₹1,200.00")
        compose.onNodeWithTag("trend_bar_0").performClick() // January
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals(if (today.monthNumber == 1) "₹1,200.00" else "₹700.00")
    }

    @Test
    fun editingAnEntryFromTheListUpdatesTheOpenReport() {
        spend("a", 600, "Food", note = "Biryani")
        openReports()
        waitFor("report_row_Food")
        compose.onNodeWithTag("report_row_Food").performClick()
        waitFor("drill_row_a")
        compose.onNodeWithTag("drill_row_a").performClick()
        compose.continueToForm() // the entry's detail first (UI S2), then its form
        compose.onNodeWithTag("amount").performTextClearance()
        compose.onNodeWithTag("amount").performTextInput("450")
        compose.onNodeWithTag("save").performClick()
        waitFor("drill_total")
        waitText("drill_total", "₹450.00")
        compose.onNodeWithTag("drill_back").performClick()
        waitText("report_spent", "₹450.00")
    }

    @Test
    fun anEmptyPeriodSaysSoAndABackStepOutOfTheListGoesToTheReport() {
        openReports()
        waitFor("report_empty")
        compose.onNodeWithTag("report_spent", useUnmergedTree = true).assertTextEquals("₹0.00")
        waitText("report_title", "–")
        compose.onNodeWithTag("report_spent_card").performClick()
        waitFor("drill_back")
        compose.onNodeWithTag("drill_back").performClick()
        waitFor("report_spent")
        compose.onNodeWithTag("reports_back").performClick()
        waitGone("report_spent")
        waitFor("add")
    }
}
