package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.BudgetLimit
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BudgetUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
    }

    private fun launch() {
        compose.waitForIdle(); scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitForText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** The summary is one merged node for screen readers, so its combined text is only in the merged tree. */
    private fun waitForMergedText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun spend(id: String, rupees: Long, category: String) = runBlocking {
        container.write { ok ->
            ok.store.save(
                Expense(EntryId(id), System.currentTimeMillis(), AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))),
                EntryDetails(merchant = id),
            )
        }
    }

    private fun limits(): List<BudgetLimit> = runBlocking { container.write { it.budgets.limits() } }!!
    private fun period(): BudgetPeriod = runBlocking { container.write { it.budgets.period() } }!!

    private fun openBudget() {
        compose.onNodeWithTag("budget_summary").performClick()
        waitFor("budget_row_overall")
    }

    /** Opens the limit dialog for a row, types [rupees] and saves. */
    private fun setLimit(row: String, rupees: String) {
        compose.onNodeWithTag("budget_row_$row").performClick()
        waitFor("limit_amount")
        compose.onNodeWithTag("limit_amount").performTextInput(rupees)
        compose.onNodeWithTag("limit_save").performClick()
        waitGone("limit_amount")
    }

    @Test
    fun anOverallLimitShowsWhatIsLeftAndAPerDayFigureOnTheListAndTheBudgetScreen() {
        spend("Lunch", 1_000, "Food")
        launch()
        compose.onNodeWithTag("budget_set_prompt", useUnmergedTree = true).assertTextContains("Set a budget", substring = true)
        openBudget()
        setLimit("overall", "5000")
        assertEquals(listOf(BudgetLimit(null, Money(500_000, inr))), limits())
        waitFor("safe_to_spend")
        compose.onNodeWithTag("budget_back").performClick()
        waitFor("budget_overall_note")
        compose.onNodeWithTag("budget_overall_note", useUnmergedTree = true).assertTextEquals("₹4,000.00 left of ₹5,000.00")
        compose.onNodeWithTag("budget_per_day", useUnmergedTree = true).assertTextContains("a day", substring = true)
    }

    @Test
    fun goingOverALimitSaysSoAndStopsOfferingAPerDayFigure() {
        spend("Dinner", 900, "Food")
        launch()
        openBudget()
        setLimit("overall", "1000")
        compose.onNodeWithTag("budget_back").performClick()
        waitFor("budget_per_day") // under the limit but near it: still gets a per-day figure
        compose.onNodeWithTag("budget_summary").performClick()
        waitFor("budget_row_overall")
        setLimit("overall", "800")
        compose.onNodeWithTag("budget_back").performClick()
        waitForText("budget_overall_note", "₹100.00 over")
        compose.onNodeWithTag("budget_overall_note", useUnmergedTree = true).assertTextEquals("₹100.00 over of ₹800.00")
        waitGone("budget_per_day")
    }

    @Test
    fun aCategoryLimitIsFlaggedOnTheListWhenItIsOver() {
        spend("Dosa", 600, "Food")
        launch()
        openBudget()
        setLimit("overall", "10000")
        setLimit("Food", "500")
        compose.onNodeWithTag("budget_back").performClick()
        waitForMergedText("budget_summary", "1 category budget is near or over")
    }

    @Test
    fun anAmountThatIsZeroOrHasTooManyDecimalsCannotBeSaved() {
        launch()
        openBudget()
        compose.onNodeWithTag("budget_row_overall").performClick()
        waitFor("limit_amount")
        compose.onNodeWithTag("limit_save").assertIsNotEnabled()
        compose.onNodeWithTag("limit_amount").performTextInput("0")
        compose.onNodeWithTag("limit_save").assertIsNotEnabled()
        compose.onNodeWithTag("limit_amount").performTextInput(".001")
        compose.onNodeWithTag("limit_save").assertIsNotEnabled()
        assertTrue(limits().isEmpty())
    }

    @Test
    fun removingALimitTakesItAwayAndTheListAsksForABudgetAgain() {
        launch()
        openBudget()
        setLimit("overall", "2000")
        compose.onNodeWithTag("budget_row_overall").performClick()
        waitFor("limit_remove")
        compose.onNodeWithTag("limit_remove").performClick()
        waitGone("limit_amount")
        assertTrue(limits().isEmpty())
        compose.onNodeWithTag("budget_back").performClick()
        waitFor("budget_set_prompt")
    }

    @Test
    fun limitsAndThePeriodSurviveClosingAndReopeningTheApp() {
        launch()
        openBudget()
        setLimit("overall", "3000")
        compose.onNodeWithTag("period_weekly").performClick()
        waitForText("period_range", "–")
        assertEquals(BudgetPeriod.Weekly(kotlinx.datetime.DayOfWeek.MONDAY), period())
        launch()
        waitFor("budget_overall_note")
        compose.onNodeWithTag("budget_overall_note", useUnmergedTree = true).assertTextEquals("₹3,000.00 left of ₹3,000.00")
        compose.onNodeWithTag("budget_summary").performClick()
        waitFor("period_weekly")
        compose.onNodeWithTag("period_weekly").assertIsSelected()
    }

    @Test
    fun theFinancialYearFortnightAndHalfMonthPeriodsCanBeChosenAndAreNamedAndKept() {
        launch(); openBudget()
        compose.onNodeWithTag("period_yearly").performClick()
        compose.waitUntil(10_000) { period() == BudgetPeriod.Yearly() }
        waitForText("period_range", "1 Apr"); waitForText("period_range", "31 Mar") // the financial year is April to March
        compose.onNodeWithTag("period_semimonthly").performClick()
        compose.waitUntil(10_000) { period() == BudgetPeriod.SemiMonthly }
        compose.onNodeWithTag("period_biweekly").performClick()
        compose.waitUntil(10_000) { period() is BudgetPeriod.BiWeekly }
        val anchor = (period() as BudgetPeriod.BiWeekly).anchor
        assertEquals(kotlinx.datetime.DayOfWeek.MONDAY, anchor.dayOfWeek)
        launch(); waitFor("budget_summary")
        compose.onNodeWithTag("budget_summary").performClick(); waitFor("period_biweekly")
        compose.onNodeWithTag("period_biweekly").assertIsSelected()
    }

    @Test
    fun aCustomLengthPeriodCanBeChosenLengthenedAndShortenedAndEveryQuickTapCounts() {
        launch(); openBudget()
        compose.onNodeWithTag("period_custom").performClick()
        compose.waitUntil(10_000) { period() is BudgetPeriod.EveryDays }
        assertEquals(10, (period() as BudgetPeriod.EveryDays).days)
        val vm = androidx.lifecycle.ViewModelProvider(run { var a: MainActivity? = null; scenario!!.onActivity { a = it }; a!! })[LedgerViewModel::class.java]
        repeat(5) { vm.shiftCustomDays(1) }
        compose.waitUntil(10_000) { (period() as? BudgetPeriod.EveryDays)?.days == 15 }
        compose.onNodeWithTag("custom_days_down").performClick()
        compose.waitUntil(10_000) { (period() as? BudgetPeriod.EveryDays)?.days == 14 }
        waitForText("custom_days", "14")
        repeat(20) { vm.shiftCustomDays(-1) }
        compose.waitUntil(10_000) { (period() as? BudgetPeriod.EveryDays)?.days == 1 }
        compose.onNodeWithTag("custom_days_down").assertIsNotEnabled()
    }

    @Test
    fun manyQuickChangesToTheStartDayAreAllCountedNoneLost() {
        launch()
        val vm = androidx.lifecycle.ViewModelProvider(run { var a: MainActivity? = null; scenario!!.onActivity { a = it }; a!! })[LedgerViewModel::class.java]
        repeat(10) { vm.shiftBudgetStartDay(1) } // fired back to back, before the screen could redraw in between
        compose.waitUntil(10_000) { period() == BudgetPeriod.Monthly(11) }
        repeat(30) { vm.shiftBudgetStartDay(1) }
        compose.waitUntil(10_000) { period() == BudgetPeriod.Monthly(28) }
        repeat(40) { vm.shiftBudgetStartDay(-1) }
        compose.waitUntil(10_000) { period() == BudgetPeriod.Monthly(1) }
        runBlocking { container.write { it.budgets.setPeriod(BudgetPeriod.Weekly(kotlinx.datetime.DayOfWeek.MONDAY)) } }
        vm.shiftBudgetStartDay(1) // a weekly period has no start day to move
        Thread.sleep(500)
        assertEquals(BudgetPeriod.Weekly(kotlinx.datetime.DayOfWeek.MONDAY), period())
    }

    @Test
    fun aSalaryCycleRenamesThePeriodAndTheStartDayStaysBetween1And28() {
        launch()
        openBudget()
        compose.onNodeWithTag("start_day_down").assertIsNotEnabled()
        repeat(24) { compose.onNodeWithTag("start_day_up").performClick() }
        waitForText("start_day", "25")
        assertEquals(BudgetPeriod.Monthly(25), period())
        repeat(3) { compose.onNodeWithTag("start_day_up").performClick() }
        waitForText("start_day", "28")
        compose.onNodeWithTag("start_day_up").assertIsNotEnabled()
        compose.onNodeWithTag("start_day_down").assertIsEnabled()
        compose.onNodeWithTag("budget_back").performClick()
        waitFor("budget_summary")
        compose.onNodeWithTag("budget_set_prompt", useUnmergedTree = true).assertTextContains("this cycle", substring = true)
    }
}
