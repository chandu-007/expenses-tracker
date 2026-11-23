package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.Bill
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N-18 (feat-016): "already spoken for" beside what is left, on Home's budget tile and the Budget screen; a tap lists the bills. Invented data only. */
@RunWith(AndroidJUnit4::class)
class UiFeat016SpokenForTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val today: LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1"); it.templates.putSetting("category_look_notice", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun textOf(tag: String) = node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun limit(rupees: Long) = runBlocking { container.write { it.budgets.setLimit(null, Money(rupees * 100, inr)) } }
    /** A monthly repeating expense whose first occurrence is today: still due (not posted) in this period. */
    private fun repeating(id: String, name: String, rupees: Long) = runBlocking {
        container.write { it.recurring.save(RecurringItem(id, name, Money(rupees * 100, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, today))) }
    }
    /** A repeating expense whose amount differs each time (an electricity bill): due today, no amount. */
    private fun variableRepeating(id: String, name: String) = runBlocking {
        container.write { it.recurring.save(RecurringItem(id, name, null, CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, today))) }
    }
    /** A tracked bill read from a text; [amountMinor] 0 is a bill whose message had no figure. */
    private fun trackedBill(id: String, title: String, amountMinor: Long, dueInDays: Long, kind: String = "UTILITY") = runBlocking {
        container.write { it.bills.add(Bill(id, "SMS", "AX-$id", title, kind, title, amountMinor, null, "INR", java.time.LocalDate.now().plusDays(dueInDays).toString(), null, null, false, System.currentTimeMillis(), null, BillStatus.TRACKED, null, null, "key-$id")) }
    }
    private fun spend(rupees: Long) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId("s$rupees"), System.currentTimeMillis(), AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId("Food"), Money(rupees * 100, inr)))), EntryDetails(merchant = "Shop")) }
    }

    @Test
    fun billsDueShowWhatOfWhatIsLeftIsSpokenForAndATapListsThem() {
        limit(10_000); repeating("net", "Streaming", 500); repeating("mob", "Mobile plan", 1_600)
        launch()
        waitFor("spoken_for_home")
        assertEquals("₹2,100.00 of what's left is already spoken for by 2 bills due", textOf("spoken_for_home"), "the node says the exact amount")
        tap("spoken_for_home")
        waitFor("spoken_for_list"); waitFor("spoken_for_row_0"); waitFor("spoken_for_row_1")
        tap("spoken_for_close")
        compose.waitUntil(10_000) { !has("spoken_for_list") }
        tap("budget_summary")
        waitFor("spoken_for_budget")
        assertEquals("₹2,100.00 of what's left is already spoken for by 2 bills due", textOf("spoken_for_budget"))
    }

    @Test
    fun noLineWithoutALimitOrWithNothingDue() {
        repeating("net", "Streaming", 500)
        launch(); waitFor("budget_summary"); compose.waitForIdle(); Thread.sleep(2_000); compose.waitForIdle()
        assertFalse(has("spoken_for_home"), "no limit: nothing to be spoken for")
        scenario?.close()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } }
        limit(10_000)
        launch(); waitFor("budget_overall_note"); compose.waitForIdle(); Thread.sleep(2_000); compose.waitForIdle()
        assertFalse(has("spoken_for_home"), "a limit and nothing due: no line")
    }

    @Test
    fun aBillThatWasPaidIsNoLongerCounted() {
        limit(10_000); repeating("net", "Streaming", 500); repeating("mob", "Mobile plan", 1_600)
        runBlocking { container.write { it.recurring.post("mob", today, TimeZone.currentSystemDefault()) } }
        launch()
        waitFor("spoken_for_home")
        assertEquals("₹500.00 of what's left is already spoken for by 1 bill due", textOf("spoken_for_home"))
    }

    @Test
    fun overTheLimitTheBillsAddToWhatIsOver() {
        limit(1_000); spend(1_200); repeating("net", "Streaming", 500)
        launch()
        waitFor("spoken_for_home")
        assertEquals("1 bill still due (₹500.00) will add to what is over", textOf("spoken_for_home"))
    }

    @Test
    fun hideAmountsMasksTheLineAndTheList() {
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        limit(10_000); repeating("net", "Streaming", 500)
        launch()
        waitFor("spoken_for_home")
        val line = textOf("spoken_for_home")
        assertFalse(line.contains("500"), line); assertTrue(line.contains("₹••••"), line)
        tap("spoken_for_home"); waitFor("spoken_for_row_0")
        val row = compose.onNodeWithTag("spoken_for_row_0").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        assertFalse(row.contains("500"), row)
    }

    @Test
    fun anOverdueBillFromBeforeThePeriodCountsAndOneWithNoAmountIsSaidAndListed() {
        limit(10_000); repeating("net", "Streaming", 500)
        trackedBill("old", "Old power bill", 90_000, -45) // fell due long before this budget period began and is still unpaid
        trackedBill("nil", "Water bill", 0, 0)            // its message had no figure
        launch()
        waitFor("spoken_for_home")
        assertEquals("₹1,400.00 of what's left is already spoken for by 2 bills due, plus 1 with no amount yet", textOf("spoken_for_home"))
        tap("spoken_for_home")
        waitFor("spoken_for_row_0"); waitFor("spoken_for_row_1"); waitFor("spoken_for_row_2")
        val rows = (0..2).map { i -> compose.onNodeWithTag("spoken_for_row_$i").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() }
        assertTrue(rows[0].contains("Old power bill") && rows[0].contains("overdue"), "the overdue bill comes first and is marked: ${rows[0]}")
        assertTrue(rows.any { it.contains("Water bill") && it.contains("amount not known") }, "the bill without an amount is listed as such: $rows")
    }

    @Test
    fun aVariableRepeatingItemWithNoAmountIsCountedButAddsNothing() {
        limit(10_000); variableRepeating("power", "Electricity")
        launch()
        waitFor("spoken_for_home")
        assertEquals("1 bill due has no amount yet", textOf("spoken_for_home"))
        repeating("net", "Streaming", 500)
        launch(); waitFor("spoken_for_home")
        assertEquals("₹500.00 of what's left is already spoken for by 1 bill due, plus 1 with no amount yet", textOf("spoken_for_home"))
    }

    @Test
    fun whenTheBannerCountsACardBillTheLineSaysCardBillsAreNotIncluded() {
        limit(10_000); repeating("net", "Streaming", 500)
        trackedBill("card", "Regalia card bill", 500_000, 0, kind = "CREDIT_CARD")
        launch()
        waitFor("spoken_for_home")
        assertEquals("₹500.00 of what's left is already spoken for by 1 bill due (card bills not included)", textOf("spoken_for_home"), "the card bill is left out of the figure on purpose, and the line says so")
    }
}
