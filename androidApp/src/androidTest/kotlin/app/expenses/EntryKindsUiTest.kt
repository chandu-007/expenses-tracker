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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.Account
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

/** Income, transfers, splits and refunds through the real form, checked against the ledger underneath. */
@RunWith(AndroidJUnit4::class)
class EntryKindsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { scenario?.close(); resetApp() }

    private fun launch() { scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = dumpOnTimeout { compose.waitUntil(10_000) { has(tag) } }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    // The keyboard shrinks the form, so a control may be below the visible part: scroll to it first (dialogs and menus do not scroll).
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun waitText(t: String) = dumpOnTimeout { compose.waitUntil(10_000) { compose.onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun dumpOnTimeout(block: () -> Unit) = try { block() } catch (e: Throwable) { compose.onRoot(useUnmergedTree = true).printToLog("DUMP"); throw e }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun kinds() = db { ok -> ok.db.schemaQueries.recentEntries(100).executeAsList().map { it.kind } }
    private fun balance(id: String) = db { ok -> ok.store.balances().getValue(AccountId(id)).minorUnits }
    private fun spendTotal() = db { ok -> ok.store.spendByCategory(inr).values.sumOf { it.minorUnits } }
    private fun addSaved() { tap("save"); waitFor("add") }

    @Test
    fun incomeIsRecordedWithACategoryRaisesTheBalanceAndIsNotSpending() {
        launch()
        db { ok -> ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, 0, false)) }
        launch()
        tap("add"); tap("kind_INCOME"); type("amount", "50000"); tap("account_chip_Bank")
        compose.onNodeWithTag("save").assertIsEnabled()
        addSaved()
        assertEquals(listOf("INCOME"), kinds())
        assertEquals(5_000_000L, balance("bank"))
        assertEquals(0L, spendTotal(), "income is not spending")
        waitText("+₹50,000.00")
    }

    @Test
    fun anAtmWithdrawalMovesMoneyFromBankToCashAndANoOpTransferIsRefused() {
        launch()
        db { ok -> ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, 1_000_000, false)) }
        launch()
        tap("add"); tap("kind_TRANSFER"); type("amount", "5000"); tap("account_chip_Bank"); tap("to_chip_Bank")
        waitFor("form_problem"); compose.onNodeWithTag("save").assertIsNotEnabled()
        waitText("Choose two different accounts")
        tap("to_chip_Cash")
        compose.onNodeWithTag("save").assertIsEnabled()
        addSaved()
        assertEquals(listOf("TRANSFER"), kinds())
        assertEquals(500_000L, balance("bank")); assertEquals(500_000L, balance("cash"))
        assertEquals(0L, spendTotal(), "an ATM withdrawal is not spending")
        compose.onNodeWithTag("month_spend").assertTextEquals("₹0.00")
    }

    @Test
    fun payingACardBillClearsWhatTheCardOwedAndOnlyThePurchaseIsSpending() {
        launch()
        db { ok ->
            ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, 1_000_000, false))
            ok.store.saveAccount(Account(AccountId("card"), "Card", AccountType.CREDIT_CARD, inr, 0, false))
            ok.store.save(Expense(EntryId("buy"), System.currentTimeMillis() - 1000, AccountId("card"), Money(120_000, inr), listOf(CategorySplit(CategoryId("Shopping"), Money(120_000, inr)))), EntryDetails(merchant = "Phone case"))
        }
        launch()
        assertEquals(-120_000L, balance("card"))
        tap("add"); tap("kind_TRANSFER"); type("amount", "1200"); tap("account_chip_Bank"); tap("to_chip_Card"); addSaved()
        assertEquals(0L, balance("card")); assertEquals(880_000L, balance("bank"))
        assertEquals(120_000L, spendTotal(), "the bill payment does not count as spending again")
    }

    @Test
    fun aSplitNeedsToAddUpAndIsStoredPartByPartAndCountedPerCategory() {
        launch()
        tap("add"); type("amount", "1000"); tap("split_start")
        tap("split_category_0"); tap("split_pick_0_Food"); type("split_amount_0", "600")
        tap("split_category_1"); tap("split_pick_1_Bills"); type("split_amount_1", "300")
        waitText("₹100.00 left to assign")
        compose.onNodeWithTag("save").assertIsNotEnabled()
        type("split_amount_1", "400"); waitText("All assigned")
        compose.onNodeWithTag("save").assertIsEnabled()
        addSaved()
        val perCategory = db { ok -> ok.store.spendByCategory(inr).entries.associate { it.key?.value to it.value.minorUnits } }
        assertEquals(mapOf<String?, Long>("Food" to 60_000L, "Bills" to 40_000L), perCategory)
        compose.onNodeWithTag("month_spend").assertTextEquals("₹1,000.00")
    }

    @Test
    fun aSplitExpenseReopensAsTheSameSplitAndCanBeChangedBackToOneCategory() {
        launch()
        db { ok -> ok.store.save(Expense(EntryId("s"), System.currentTimeMillis(), AccountId("cash"), Money(100_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(60_000, inr)), CategorySplit(CategoryId("Bills"), Money(40_000, inr)))), EntryDetails(merchant = "Mall")) }
        launch(); tap("row_Mall")
        waitFor("split_amount_0")
        compose.onNodeWithTag("split_amount_0").assertTextEquals("₹", "600.00")
        tap("split_remove_1")
        waitFor("category_chip_Food")
        tap("category_chip_Food"); addSaved()
        assertEquals(mapOf<String?, Long>("Food" to 100_000L), db { ok -> ok.store.spendByCategory(inr).entries.associate { it.key?.value to it.value.minorUnits } })
    }

    @Test
    fun aRefundLowersSpendingIsBoundedByTheExpenseAndCanBeEditedAfterwards() {
        launch()
        db { ok -> ok.store.save(Expense(EntryId("buy"), System.currentTimeMillis() - 5000, AccountId("cash"), Money(300_000, inr), listOf(CategorySplit(CategoryId("Shopping"), Money(300_000, inr)))), EntryDetails(merchant = "Shoes")) }
        launch(); tap("row_Shoes"); tap("refund_start")
        waitFor("refund_of"); waitText("Up to ₹3,000.00 can still be refunded")
        type("amount", "4000"); waitFor("form_problem"); compose.onNodeWithTag("save").assertIsNotEnabled()
        type("amount", "1000"); type("note", "Return"); compose.onNodeWithTag("save").assertIsEnabled(); addSaved()
        assertEquals(200_000L, spendTotal(), "net spend dropped by the refund")
        assertTrue(kinds().contains("REFUND") && !kinds().contains("INCOME"), "a refund is not income")
        // a second refund may take only what is left
        tap("row_Return"); waitFor("refund_of")
        compose.onNodeWithTag("amount").assertTextContains("1000.00")
        waitText("Up to ₹3,000.00 can still be refunded") // editing it: its own amount is not counted against it
        type("amount", "1500"); addSaved()
        assertEquals(150_000L, spendTotal(), "editing the refund changed what was taken back")
        assertEquals(1, db { ok -> ok.db.schemaQueries.refundsOf("buy").executeAsList().size })
        tap("row_Shoes"); waitFor("read_only_reason") // an expense with a refund is read-only until the refund is removed
    }

    @Test
    fun theFormKeepsWhatWasTypedWhenTheScreenIsRecreated() {
        launch()
        tap("add"); type("amount", "1000"); tap("split_start")
        tap("split_category_0"); tap("split_pick_0_Food"); type("split_amount_0", "250")
        scenario!!.recreate()
        waitFor("split_amount_0")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "1000")
        compose.onNodeWithTag("split_amount_0").assertTextEquals("₹", "250")
        waitText("₹750.00 left to assign")
    }

    @Test
    fun incomeAndTransfersOpenInTheEditorWithTheirValuesAndSaveChanges() {
        launch()
        db { ok ->
            ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, 0, false))
            ok.store.save(app.expenses.core.domain.ledger.Income(EntryId("pay"), System.currentTimeMillis(), AccountId("bank"), Money(80_000, inr), CategoryId("Salary")), EntryDetails(merchant = "Acme"))
            ok.store.save(app.expenses.core.domain.ledger.Transfer(EntryId("atm"), System.currentTimeMillis() - 10, AccountId("bank"), AccountId("cash"), Money(20_000, inr)), EntryDetails(merchant = "ATM"))
        }
        launch(); tap("row_Acme"); waitFor("amount")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "800.00")
        type("amount", "900"); addSaved()
        assertEquals(90_000L + 0, db { ok -> ok.store.balances().getValue(AccountId("bank")).minorUnits } + 20_000L)
        tap("row_ATM"); waitFor("to_chip_Cash"); type("amount", "300"); addSaved()
        assertEquals(30_000L, balance("cash"))
    }
}
