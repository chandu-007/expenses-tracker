package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CalendarUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val now = LocalDate.now()
    private val zone = ZoneId.systemDefault()

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { scenario?.close(); resetApp() }

    private fun launch() { scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitText(s: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun at(day: Int, hour: Int = 12, month: LocalDate = now) = LocalDateTime.of(month.year, month.monthValue, day, hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun expense(id: String, minor: Long, day: Int, merchant: String, month: LocalDate = now) = db { ok ->
        ok.store.save(Expense(EntryId(id), at(day, 12, month), AccountId("cash"), Money(minor, inr), listOf(CategorySplit(CategoryId("Food"), Money(minor, inr)))), EntryDetails(merchant = merchant))
    }
    private fun open() { launch(); tap("more_menu"); tap("open_calendar"); waitFor("cal_month") }
    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun eachDayShowsWhatWasSpentInShortFormAndTheMonthTotalMatches() {
        expense("a", 12_000, 3, "Alpha"); expense("b", 8_000, 3, "Bravo"); expense("c", 250_000, 20, "Charlie")
        db { ok -> ok.store.save(Income(EntryId("i"), at(5), AccountId("cash"), Money(9_000_000, inr), CategoryId("Other")), EntryDetails(merchant = "Pay")) }
        open()
        text("cal_amount_3").assertTextEquals("₹200")
        text("cal_amount_20").assertTextEquals("₹2.5K")
        text("cal_amount_5").assertTextEquals("")           // income is not spending
        text("cal_total").assertTextEquals("Spent ₹2,700.00")
    }

    @Test
    fun tappingADayListsItsEntriesAndOpeningOneEditsItAndTheCalendarFollows() {
        expense("a", 12_000, 3, "Alpha"); expense("b", 8_000, 3, "Bravo")
        open(); tap("cal_day_3")
        waitFor("cal_day_title"); text("cal_day_total").assertTextEquals("Spent ₹200.00")
        waitFor("cal_line_Alpha"); waitFor("cal_line_Bravo")
        tap("cal_line_Alpha"); waitFor("amount")
        compose.onNodeWithTag("amount").performTextClearance(); compose.onNodeWithTag("amount").performTextInput("500"); tap("save")
        waitFor("cal_month")                                   // back on the calendar, not the list
        compose.waitUntil(10_000) { runCatching { text("cal_amount_3").assertTextEquals("₹580") }.isSuccess }
        text("cal_total").assertTextEquals("Spent ₹580.00")
    }

    @Test
    fun theMonthCanBeSteppedBackAndForthAndEachMonthShowsItsOwnSpending() {
        val last = now.minusMonths(1)
        expense("p", 30_000, 10, "Prev", last); expense("t", 10_000, 10, "This")
        open(); text("cal_amount_10").assertTextEquals("₹100")
        tap("cal_prev")
        compose.waitUntil(10_000) { runCatching { text("cal_amount_10").assertTextEquals("₹300") }.isSuccess }
        text("cal_total").assertTextEquals("Spent ₹300.00")
        tap("cal_next")
        compose.waitUntil(10_000) { runCatching { text("cal_amount_10").assertTextEquals("₹100") }.isSuccess }
    }

    @Test
    fun aBillStillToComeIsMarkedAndListedOnItsDayButAnIncomingRepeatIsNot() {
        val sch = { day: Int -> Schedule(RepeatUnit.MONTH, 1, kotlinx.datetime.LocalDate(now.year, now.monthValue, day), app.expenses.core.domain.ScheduleEnd.AfterCount(1)) }
        db { ok ->
            ok.recurring.save(RecurringItem("rent", "Rent", Money(600_000, inr), CategoryId("Bills"), AccountId("bank"), null, sch(27)))
            ok.recurring.save(RecurringItem("pay", "Salary", Money(5_000_000, inr), CategoryId("Other"), AccountId("bank"), null, sch(26), kind = app.expenses.core.domain.RecurringKind.INCOME))
        }
        open(); waitFor("cal_bill_dot_27")
        assertEquals(false, has("cal_bill_dot_26"), "a repeating salary is not a bill")
        tap("cal_day_27"); waitText("Due: Rent ₹6,000.00")
    }

    @Test
    fun anEmptyDayAndTheHintAreSaidInWords() {
        open(); waitFor("cal_hint"); tap("cal_day_9"); waitFor("cal_empty_day")
        text("cal_total").assertTextEquals("Spent ₹0.00")
    }
}
