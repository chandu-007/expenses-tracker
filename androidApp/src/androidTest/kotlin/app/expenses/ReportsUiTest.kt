package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun spend(id: String, rupees: Long, category: String, at: Long = System.currentTimeMillis(), note: String = id) = runBlocking {
        container.write { ok ->
            ok.store.save(Expense(EntryId(id), at, AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))), EntryDetails(merchant = note))
        }
    }

    private fun noonOf(d: LocalDate) = d.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L
    private fun previousPeriodNoon() = noonOf(ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -1).start)

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
        waitText("report_comparison", "67% more than the period before")
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
        waitFor("amount")
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
