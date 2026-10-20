package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assert
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Transfer
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
class AccountsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() { resetApp() }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    /** Opens the Accounts page; with several accounts the cards start stacked like a wallet, so one tap spreads them out before any card is tapped. */
    private fun openAccounts() {
        compose.openMore(); tap("manage_accounts"); waitFor("accounts_list"); waitFor("accounts_stack")
        val several = (runBlocking { container.write { it.db.schemaQueries.selectAccounts().executeAsList().count { a -> a.archived == 0L } } } ?: 0) > 1
        if (several) { tap("accounts_stack"); waitFor("accounts_stack_close") }
    }
    private fun create(name: String, type: String = "BANK", opening: String = "") {
        tap("add_account"); type("account_name", name); tap("type_$type"); if (opening.isNotEmpty()) type("account_opening", opening); tap("account_save")
    }
    private fun balanceOf(name: String): String? = runBlocking { container.write { ok -> ok.store.balances().entries.firstOrNull { ok.db.schemaQueries.accountById(it.key.value).executeAsOne().name == name }?.value?.minorUnits?.toString() } }
    private fun idOf(name: String) = runBlocking { container.write { ok -> ok.db.schemaQueries.selectAccounts().executeAsList().first { it.name == name }.id } }!!
    private fun text(t: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun anAccountIsCreatedWithAnOpeningBalanceAndShowsItAndTheNetWorth() {
        launch(); openAccounts()
        create("HDFC Savings", "BANK", "25000")
        waitFor("account_HDFC Savings")
        compose.onNodeWithTag("balance_HDFC Savings", useUnmergedTree = true).assertTextEquals("₹25,000.00")
        assertEquals("2500000", balanceOf("HDFC Savings"))
        val net = runBlocking { container.write { ok -> ok.store.balances().values.sumOf { it.minorUnits } } }
        compose.onNodeWithTag("net_worth", useUnmergedTree = true).assertTextEquals(Money(net!!, Currency.INR).formatted())
    }

    @Test
    fun aCardIsEnteredAsAmountOwedAndCountsAgainstNetWorthAndEditingShowsTheSameNumber() {
        launch(); openAccounts()
        create("Amex", "CREDIT_CARD", "12000")
        waitFor("account_Amex")
        assertEquals("-1200000", balanceOf("Amex"))
        compose.onNodeWithTag("balance_Amex", useUnmergedTree = true).assertTextEquals("-₹12,000.00")
        text("owed")
        tap("account_Amex"); waitFor("account_opening")
        compose.onNodeWithTag("account_opening", useUnmergedTree = true).assert(androidx.compose.ui.test.hasText("12000.00"))
    }

    @Test
    fun aDuplicateOrEmptyNameOrBadAmountIsRefusedWithAReasonAndTheFormStaysOpen() {
        launch(); openAccounts()
        create("Wallet X", "WALLET")
        waitFor("account_Wallet X")
        tap("add_account"); type("account_name", "  wallet   x "); tap("account_save")
        waitFor("account_problem"); text("already have an account called")
        type("account_name", ""); tap("account_save"); text("Give the account a name")
        type("account_name", "Fresh"); type("account_opening", "12abc"); tap("account_save"); text("as a number")
        assertTrue(runBlocking { container.write { ok -> ok.db.schemaQueries.selectAccounts().executeAsList().none { it.name == "Fresh" } } }!!, "nothing was saved")
    }

    @Test
    fun anAccountWithEntriesCanBeArchivedNotDeletedAndDisappearsFromThePickersButNotFromItsOwnEntry() {
        launch()
        runBlocking { container.write { ok ->
            ok.store.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("old"), "Old bank", app.expenses.core.domain.ledger.AccountType.BANK, Currency.INR, 10_000, false))
            ok.store.save(Expense(EntryId("e1"), System.currentTimeMillis(), AccountId("old"), Money(2_500, Currency.INR)), EntryDetails(merchant = "Kirana"))
        } }
        launch(); openAccounts()
        tap("account_Old bank"); waitFor("account_archive")
        assertTrue(!has("account_delete"), "an account with entries has no delete")
        tap("account_archive")
        waitGone("account_archive")
        assertEquals("7500", balanceOf("Old bank"), "archiving keeps the balance")
        val pickable = runBlocking { container.write { ok -> ok.db.schemaQueries.selectAccounts().executeAsList().filter { it.archived == 0L }.map { it.name } } }!!
        assertTrue("Old bank" !in pickable)
        tap("back"); compose.closeMore(); waitFor("add"); tap("add"); waitFor("amount")
        assertTrue(has("account_chip_Bank") && !has("account_chip_Old bank"), "the add form offers Bank but no longer the archived account")
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }; waitFor("add")
        openAccounts()
        tap("account_Old bank"); tap("account_unarchive"); waitGone("account_unarchive")
        assertTrue(runBlocking { container.write { ok -> ok.db.schemaQueries.accountById("old").executeAsOne().archived } } == 0L)
    }

    @Test
    fun anAccountThatARepeatingItemOrQuickEntryPostsToCannotBeArchivedAndSaysWhy() {
        launch()
        runBlocking { container.write { ok -> ok.templates.save(app.expenses.core.domain.Template("t", "Chai", Money(2_000, Currency.INR), app.expenses.core.domain.ledger.CategoryId("Food"), AccountId("cash"), null)) } }
        launch(); openAccounts(); tap("account_Cash"); waitFor("account_saved_note")
        compose.onNodeWithTag("account_archive").assertIsNotEnabled()
        runBlocking { container.write { ok -> ok.templates.delete("t") } }
        launch(); openAccounts(); tap("account_Cash"); waitFor("account_archive")
        assertTrue(!has("account_saved_note"))
        tap("account_archive"); waitGone("account_archive")
    }

    @Test
    fun anUnusedAccountCanBeDeletedButTheLastActiveOneCannotBeArchivedOrDeleted() {
        launch(); openAccounts()
        create("Spare", "CASH"); waitFor("account_Spare")
        tap("account_Spare"); tap("account_delete"); waitGone("account_Spare")
        // the app starts with Bank and Cash; archive one, then the other must refuse to go
        tap("account_Cash"); tap("account_archive"); waitGone("account_archive")
        tap("account_Bank"); waitFor("account_archive")
        val enabled = runCatching { compose.onNodeWithTag("account_archive", useUnmergedTree = true).assertIsEnabled() }.isSuccess
        assertTrue(!enabled, "the last active account cannot be archived")
    }

    @Test
    fun transfersBetweenAccountsMoveTheBalanceButNeverTheNetWorth() {
        launch()
        runBlocking { container.write { ok ->
            ok.store.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("a"), "A", app.expenses.core.domain.ledger.AccountType.BANK, Currency.INR, 100_000, false))
            ok.store.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("b"), "B", app.expenses.core.domain.ledger.AccountType.CASH, Currency.INR, 0, false))
            ok.store.save(Transfer(EntryId("t"), System.currentTimeMillis(), AccountId("a"), AccountId("b"), Money(40_000, Currency.INR)), EntryDetails())
        } }
        launch(); openAccounts()
        compose.onNodeWithTag("balance_A", useUnmergedTree = true).assertTextEquals("₹600.00")
        compose.onNodeWithTag("balance_B", useUnmergedTree = true).assertTextEquals("₹400.00")
    }

    private fun statusOf(name: String): String = compose.onNodeWithTag("card_status_$name", useUnmergedTree = true).fetchSemanticsNode().config.let { c -> c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } }

    @Test
    fun aCardGetsBillingDatesShowsWhatIsLeftOnTheStatementAndPayingItOffClearsItAndRemovingThemWorks() {
        launch(); openAccounts()
        create("Amex", "CREDIT_CARD"); waitFor("account_Amex")
        val cardId = idOf("Amex"); val bankId = idOf("Bank")
        val sixtyDaysAgo = System.currentTimeMillis() - 60L * 86_400_000
        runBlocking { container.write { ok -> ok.store.save(Expense(EntryId("big"), sixtyDaysAgo, AccountId(cardId), Money(40_000, Currency.INR)), EntryDetails(merchant = "Phone")) } }
        tap("account_Amex"); tap("card_dates")
        // refused values are explained in the dialog, which stays open
        type("card_statement_day", "31"); type("card_due_day", "5"); tap("card_save")
        waitFor("card_problem")
        // not today's date: a payment made on the statement day itself belongs to the statement before it, so on the 15th a fixed "15" made this test fail
        val statementDay = if (java.time.LocalDate.now().dayOfMonth == 14) 13 else 14
        type("card_statement_day", statementDay.toString()); tap("card_save")
        waitFor("card_status_Amex")
        text("Statement on the ${statementDay}th")
        assertTrue(statusOf("Amex").contains("₹400.00 left · minimum about ₹20.00"), statusOf("Amex"))
        runBlocking { container.write { ok -> ok.store.save(app.expenses.core.domain.ledger.Transfer(EntryId("pay"), System.currentTimeMillis(), AccountId(bankId), AccountId(cardId), Money(40_000, Currency.INR))) } }
        tap("back"); openAccounts()
        compose.waitUntil(10_000) { has("card_status_Amex") && statusOf("Amex").contains("is paid") }
        tap("account_Amex"); tap("card_dates"); tap("card_clear")
        compose.waitUntil(10_000) { !has("card_status_Amex") }
        assertEquals(null, runBlocking { container.write { it.cards.terms(cardId) } })
    }
}
