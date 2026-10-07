package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.storage.EncryptedDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RecurringUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val today: LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        scenario?.close()
        resetApp()
    }

    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitSpend(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("month_spend") and hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun item(id: String, name: String, rupees: Long?, auto: Boolean = false, start: LocalDate = today, end: ScheduleEnd = ScheduleEnd.Never) = RecurringItem(
        id, name, rupees?.let { Money(it * 100, inr) }, CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, start, end), auto,
    )

    private fun seed(vararg items: RecurringItem) = runBlocking { container.write { ok -> items.forEach(ok.recurring::save) } }

    private fun dueTag(id: String, prefix: String) = "due_${prefix}_${id}_${today.toEpochDays()}"

    @Test
    fun anItemCreatedOnTheScreenIsDueTodayAndPostingItCountsItOnce() {
        launch()
        compose.onNodeWithTag("open_recurring").performClick()
        waitFor("new_recurring")
        compose.onNodeWithTag("recurring_empty", useUnmergedTree = true).assertTextContains("Nothing repeats yet", substring = true)
        compose.onNodeWithTag("new_recurring").performClick()
        waitFor("rec_name")
        compose.onNodeWithTag("rec_save").assertIsNotEnabled()
        compose.onNodeWithTag("rec_name").performTextInput("Rent")
        compose.onNodeWithTag("rec_amount").performTextInput("15000")
        compose.onNodeWithTag("rec_save").assertIsEnabled()
        compose.onNodeWithTag("rec_save").performClick()
        waitFor("rec_row_Rent")
        val id = runBlocking { container.write { it.recurring.list().single().id } }!!
        waitFor(dueTag(id, "card")) // it starts today, so it is waiting on this screen
        compose.onNodeWithTag(dueTag(id, "post")).performClick()
        waitGone(dueTag(id, "card"))
        compose.onNodeWithTag("recurring_back").performClick()
        waitSpend("₹15,000.00")
        assertEquals(1L, runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } })
    }

    @Test
    fun skippingTakesItOffTheListWithoutSpendingAndUndoBringsItBack() {
        seed(item("gym", "Gym", 1_000))
        launch()
        waitFor(dueTag("gym", "card"))
        compose.onNodeWithTag(dueTag("gym", "skip")).performClick()
        waitGone(dueTag("gym", "card"))
        compose.onNodeWithTag("month_spend").assertTextEquals("₹0.00")
        compose.onNodeWithText("Undo").performClick()
        waitFor(dueTag("gym", "card"))
    }

    @Test
    fun undoingAPostRemovesTheExpenseAndMakesItDueAgain() {
        seed(item("gym", "Gym", 1_000))
        launch()
        compose.onNodeWithTag(dueTag("gym", "post")).performClick()
        waitSpend("₹1,000.00")
        compose.onNodeWithText("Undo").performClick()
        waitSpend("₹0.00")
        waitFor(dueTag("gym", "card"))
    }

    @Test
    fun anItemWithoutAFixedAmountAsksForItWhenPosted() {
        seed(item("power", "Electricity", null))
        launch()
        waitFor(dueTag("power", "card"))
        compose.onNodeWithTag(dueTag("power", "post")).performClick()
        waitFor("due_amount")
        compose.onNodeWithTag("due_amount_ok").assertIsNotEnabled()
        compose.onNodeWithTag("due_amount").performTextInput("2345.50")
        compose.onNodeWithTag("due_amount_ok").performClick()
        waitSpend("₹2,345.50")
    }

    @Test
    fun anItemThatPostsByItselfIsPostedWhenTheAppOpensAndNeverTwice() {
        seed(item("nf", "Netflix", 649, auto = true), item("ask", "Rent", 1_000, auto = false))
        launch()
        waitSpend("₹649.00")
        waitGone(dueTag("nf", "card"))
        waitFor(dueTag("ask", "card"))
        launch() // opening again must not post it again
        waitFor(dueTag("ask", "card"))
        waitSpend("₹649.00")
        assertEquals(1L, runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } })
    }

    @Test
    fun billsStillToComeAreSetAsideInTheSafeToSpendFigure() {
        seed(item("rent", "Rent", 3_000))
        runBlocking { container.write { it.budgets.setLimit(null, Money(1_000_000, inr)) } }
        launch()
        compose.onNodeWithTag("budget_summary").performClick()
        waitFor("safe_to_spend")
        compose.onNodeWithTag("safe_to_spend", useUnmergedTree = true).assertTextContains("After ₹3,000.00 of repeating bills", substring = true)
    }

    @Test
    fun billsThatUseUpWhatIsLeftAreSaidInWords() {
        seed(item("rent", "Rent", 3_000))
        runBlocking { container.write { it.budgets.setLimit(null, Money(200_000, inr)) } }
        launch()
        waitFor("budget_bills_use_all")
        assertTrue(runBlocking { container.write { it.budgets.status(today, kotlinx.datetime.TimeZone.currentSystemDefault(), inr).safeToSpend!!.perDay.minorUnits } } == 0L)
    }

    @Test
    fun editingChangesTheSummaryAndTheEditorRefusesWhatCannotBeSaved() {
        seed(item("tv", "Cable", 500))
        launch()
        compose.onNodeWithTag("open_recurring").performClick()
        waitFor("rec_row_Cable")
        compose.onNodeWithTag("rec_row_Cable").performClick()
        waitFor("rec_amount")
        compose.onNodeWithTag("rec_auto").performScrollTo()
        compose.onNodeWithTag("rec_amount").performTextInput(".001") // 500 followed by .001: three decimals
        compose.onNodeWithTag("rec_save").assertIsNotEnabled()
        compose.onNodeWithTag("rec_cancel").performClick()
        waitFor("rec_row_Cable")
        // a fixed amount is needed for "post by itself"
        compose.onNodeWithTag("rec_row_Cable").performClick()
        waitFor("rec_auto")
        compose.onNodeWithTag("rec_auto").performScrollTo().performClick()
        compose.onNodeWithTag("rec_save").performClick()
        waitFor("rec_row_Cable")
        assertTrue(runBlocking { container.write { it.recurring.list().single().auto } }!!)
        compose.onNodeWithTag("rec_row_Cable").assertTextContains("posts by itself", substring = true)
    }

    @Test
    fun deletingStopsItRepeatingAndKeepsWhatWasPosted() {
        seed(item("gym", "Gym", 1_000))
        launch()
        compose.onNodeWithTag(dueTag("gym", "post")).performClick()
        waitSpend("₹1,000.00")
        compose.onNodeWithTag("open_recurring").performClick()
        waitFor("rec_row_Gym")
        compose.onNodeWithTag("rec_row_Gym").performClick()
        waitFor("rec_delete")
        compose.onNodeWithTag("rec_delete").performScrollTo().performClick()
        waitFor("rec_delete_confirm")
        compose.onNodeWithTag("rec_delete_confirm").performClick()
        waitFor("recurring_empty")
        compose.onNodeWithTag("recurring_back").performClick()
        waitSpend("₹1,000.00")
    }

    @Test
    fun olderOccurrencesThatAreNoLongerOfferedAreCountedOnTheScreen() {
        seed(item("old", "Old bill", 100, start = LocalDate.fromEpochDays(today.toEpochDays() - 100)))
        launch()
        compose.onNodeWithTag("open_recurring").performClick()
        waitFor("hidden_older")
        compose.onNodeWithTag("hidden_older", useUnmergedTree = true).assertTextContains("older", substring = true)
    }
}
