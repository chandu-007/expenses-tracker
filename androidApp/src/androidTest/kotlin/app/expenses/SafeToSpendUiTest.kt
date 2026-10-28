package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
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
import app.expenses.core.domain.formatted
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.storage.Bill
import app.expenses.core.storage.BillStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertFalse

/** The balance-based "safe to spend", on the Accounts page and under the budget on Home, with its breakdown, the yearly-bill set-aside and the EMI line on the Repeating page. Invented figures. */
@RunWith(AndroidJUnit4::class)
class SafeToSpendUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR
    private fun rupees(r: Long) = Money(r * 100, inr).formatted()

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun waitText(tag: String, text: String) = compose.waitUntil(15_000) { has(tag) && runCatching { node(tag).assertTextContains(text, substring = true) }.isSuccess }
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun openAccounts() { compose.openMore(); tap("manage_accounts"); waitFor("accounts_list") }

    /** Bank holds ₹50,000, an HDFC card owes ₹10,000, a ₹2,000 electricity bill is tracked for 5 days ahead and rent of ₹15,000 is due in 3 days: ₹23,000 is spare for the next 30 days. */
    private fun seedTypicalMonth(withRent: Boolean = true) = runBlocking {
        container.write { ok ->
            ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 5_000_000))
            ok.store.saveAccount(Account(AccountId("hdfc"), "HDFC card", AccountType.CREDIT_CARD, inr, openingMinor = -1_000_000))
            ok.bills.add(Bill("b1", "SMS", "AX-BESCOM", "BESCOM", "ELECTRICITY", "BESCOM bill", 200_000, null, "INR", LocalDate.now().plusDays(5).toString(), null, null, false, System.currentTimeMillis(), null, BillStatus.TRACKED, null, null, "k-b1"))
            if (withRent) ok.recurring.save(RecurringItem("rent", "Rent", Money(1_500_000, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, kotlinx.datetime.LocalDate.fromEpochDays(LocalDate.now().plusDays(3).toEpochDay()))))
        }
    }

    @Test
    fun accountsShowSafeToSpendFromBalancesLessCardBillsAndWhatIsDueWithABreakdown() {
        seedTypicalMonth(); launch(); openAccounts()
        waitText("safe_to_spend_line_text", "${rupees(23_000)} safe to spend until")
        tap("safe_to_spend_line_text"); waitFor("safe_to_spend_dialog")
        waitText("safe_to_spend_total", rupees(23_000))
        node("safe_to_spend_headline").assertTextContains("safe to spend", substring = true)
        node("safe_to_spend_amount_BALANCE").assertTextContains(rupees(50_000), substring = true)
        node("safe_to_spend_amount_CARDS").assertTextContains("−" + rupees(10_000), substring = true)
        node("safe_to_spend_amount_BILLS").assertTextContains("−" + rupees(2_000), substring = true)
        node("safe_to_spend_amount_REPEATING").assertTextContains("−" + rupees(15_000), substring = true)
        node("safe_to_spend_horizon").assertTextContains("30 days", substring = true) // no overall budget: thirty days
        node("safe_to_spend_explain").assertTextContains("An estimate", substring = true)
        tap("safe_to_spend_close"); waitGone("safe_to_spend_dialog")
    }

    @Test
    fun withAnOverallBudgetTheLookAheadEndsWithThePeriod() {
        seedTypicalMonth(withRent = false)
        runBlocking { container.write { it.budgets.setLimit(null, Money(3_000_000, inr)) } }
        launch(); openAccounts(); tap("safe_to_spend_line_text"); waitFor("safe_to_spend_dialog")
        node("safe_to_spend_horizon").assertTextContains("end of your budget period", substring = true)
    }

    @Test
    fun whenTheDuesAreMoreThanTheAccountsHoldItSaysNothingSpareNotANegativeFigure() {
        runBlocking { container.write { ok ->
            ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 100_000))
            ok.store.saveAccount(Account(AccountId("hdfc"), "HDFC card", AccountType.CREDIT_CARD, inr, openingMinor = -1_500_000))
        } }
        launch(); openAccounts()
        waitText("safe_to_spend_line_text", "Nothing spare until")
        tap("safe_to_spend_line_text"); waitFor("safe_to_spend_dialog")
        waitText("safe_to_spend_short", "Short by ${rupees(14_000)}")
        node("safe_to_spend_total").assertTextContains(rupees(0), substring = true)
    }

    @Test
    fun anArchivedCardThatStillOwesIsCountedOnceAndItsTrackedBillIsNotTakenOffAgain() {
        runBlocking { container.write { ok ->
            ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 5_000_000))
            ok.store.saveAccount(Account(AccountId("old"), "Old card", AccountType.CREDIT_CARD, inr, openingMinor = -1_000_000, archived = true))
            ok.bills.add(Bill("cb", "SMS", "AX-HDFCBK", "HDFC Bank", "CREDIT_CARD", "HDFC Bank credit card 0862", 1_000_000, 50_000, "INR", LocalDate.now().plusDays(5).toString(), null, null, false, System.currentTimeMillis(), null, BillStatus.TRACKED, null, null, "k-cb"))
        } }
        launch(); openAccounts()
        waitText("safe_to_spend_line_text", "${rupees(40_000)} safe to spend")
        tap("safe_to_spend_line_text"); waitFor("safe_to_spend_dialog")
        node("safe_to_spend_amount_CARDS").assertTextContains("−" + rupees(10_000), substring = true)
        assertFalse(has("safe_to_spend_amount_BILLS"), "the card's bill is what the card owes, not a second thing to take off")
    }

    @Test
    fun anAccountInAnotherCurrencyIsLeftOutAndTheNoteSaysSo() {
        runBlocking { container.write { ok ->
            ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 5_000_000))
            ok.store.saveAccount(Account(AccountId("usd"), "Dollar account", AccountType.BANK, Currency.USD, openingMinor = 300_000))
        } }
        launch(); openAccounts()
        waitText("safe_to_spend_line_text", "${rupees(50_000)} safe to spend")
        tap("safe_to_spend_line_text"); waitFor("safe_to_spend_dialog")
        waitText("safe_to_spend_not_counted", "1 account is in another currency and is not counted.")
    }

    @Test
    fun theHomeLineAppearsByItselfOnlyWithMoneyAndSomethingDueAndTheSwitchHidesIt() {
        seedTypicalMonth(); launch()
        waitFor("home_safe_to_spend_line_text")
        waitText("home_safe_to_spend_line_text", "${rupees(23_000)} safe to spend")
        tap("home_safe_to_spend_line_text"); waitFor("safe_to_spend_dialog")
        node("safe_to_spend_home_switch").assertIsOn()
        tap("safe_to_spend_home_switch")
        compose.waitUntil(10_000) { runBlocking { container.write { it.templates.setting("safe_to_spend_on_home") } } == "0" }
        tap("safe_to_spend_close"); waitGone("home_safe_to_spend_line_text")
    }

    @Test
    fun theHomeLineStaysHiddenWithNothingDueButTheAccountsPageStillShowsIt() {
        runBlocking { container.write { it.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 5_000_000)) } }
        launch()
        compose.waitForIdle(); Thread.sleep(2_500) // the figure is worked out a moment after the page opens
        assertFalse(has("home_safe_to_spend_line_text"), "no bill, card or repeating payment is due: nothing to take off, so Home leaves it out by itself")
        openAccounts(); waitText("safe_to_spend_line_text", "${rupees(50_000)} safe to spend")
    }

    @Test
    fun aPersonWhoSwitchedItOnSeesItOnHomeEvenWithNothingDue() {
        runBlocking { container.write { ok -> ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 5_000_000)); ok.templates.putSetting("safe_to_spend_on_home", "1") } }
        launch(); waitText("home_safe_to_spend_line_text", "${rupees(50_000)} safe to spend")
    }

    @Test
    fun aYearlyBillShowsWhatToSetAsideAMonthAndTheSwitchTakesItOffSafeToSpend() {
        val first = kotlinx.datetime.LocalDate.fromEpochDays(LocalDate.now().plusMonths(6).toEpochDay()) // ₹12,000.00 in 6 months: ₹2,000.00 a month
        runBlocking { container.write { ok ->
            ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 5_000_000))
            ok.recurring.save(RecurringItem("ins", "Car insurance", Money(1_200_000, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.YEAR, 1, first)))
            ok.recurring.save(RecurringItem("sip", "Phone plan", Money(59_900, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, first))) // monthly: nothing to set aside
        } }
        launch(); compose.openMore(); tap("open_recurring"); waitFor("rec_row_Car insurance")
        node("rec_set_aside_Car insurance").assertTextContains("set aside ${rupees(2_000)} a month", substring = true)
        assertFalse(has("rec_set_aside_Phone plan"))
        // off: the dialog says what it would come to and takes nothing off
        tap("recurring_back"); compose.closeMore(); waitFor("add")
        openAccounts(); waitText("safe_to_spend_line_text", "${rupees(50_000)} safe to spend")
        tap("safe_to_spend_line_text"); waitFor("safe_to_spend_set_aside_hint")
        node("safe_to_spend_set_aside_hint").assertTextContains(rupees(2_000), substring = true)
        tap("safe_to_spend_close")
        // on, from the Repeating page
        tap("back"); compose.openMore(); tap("open_recurring"); waitFor("sinking_funds_switch")
        tap("sinking_funds_switch")
        compose.waitUntil(10_000) { runBlocking { container.write { it.templates.setting("sinking_funds_on") } } == "1" }
        tap("recurring_back"); compose.closeMore(); waitFor("add")
        openAccounts()
        waitText("safe_to_spend_line_text", "${rupees(48_000)} safe to spend")
        tap("safe_to_spend_line_text"); waitFor("safe_to_spend_amount_PUT_ASIDE")
        node("safe_to_spend_amount_PUT_ASIDE").assertTextContains("−" + rupees(2_000), substring = true)
        assertFalse(has("safe_to_spend_set_aside_hint"), "it is taken off now, so there is nothing to hint at")
    }

    @Test
    fun anEmiThatEndsShowsPaymentsLeftTheLastDateAndWhatIsStillToPayWithNoInterest() {
        val start = LocalDate.now().plusDays(2)
        val last = start.plusMonths(11)
        runBlocking { container.write { ok ->
            ok.recurring.save(RecurringItem("emi", "Phone EMI", Money(500_000, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, kotlinx.datetime.LocalDate.fromEpochDays(start.toEpochDay()), ScheduleEnd.AfterCount(12))))
            ok.recurring.save(RecurringItem("rent", "Rent", Money(1_500_000, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, kotlinx.datetime.LocalDate.fromEpochDays(start.toEpochDay())))) // never ends: no EMI line
        } }
        launch(); compose.openMore(); tap("open_recurring"); waitFor("rec_row_Phone EMI")
        val lastText = last.dayOfMonth.toString() + " " + last.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) + " " + last.year
        node("rec_left_Phone EMI").assertTextContains("12 payments left · last on $lastText · ${rupees(60_000)} still to pay", substring = true)
        assertFalse(has("rec_left_Rent"))
        // one is posted: 11 left
        var vm: LedgerViewModel? = null
        scenario!!.onActivity { vm = androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java] }
        vm!!.postDue("emi", start.toEpochDay(), null)
        waitText("rec_left_Phone EMI", "11 payments left")
        node("rec_left_Phone EMI").assertTextContains("${rupees(55_000)} still to pay", substring = true)
    }
}
