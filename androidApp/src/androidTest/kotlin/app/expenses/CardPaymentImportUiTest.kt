package app.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A bank statement's credit card payments are offered as a transfer, not counted as spending. The statement is INVENTED. */
@RunWith(AndroidJUnit4::class)
class CardPaymentImportUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp(); files.forEach { it.delete() } }

    private fun file(name: String, content: String): Uri = Uri.fromFile(File(context.cacheDir, name).also { it.writeBytes(content.encodeToByteArray()); files += it })
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitAnyText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!

    private fun card(id: String, name: String, last4: String? = null) = runBlocking {
        container.write { it.store.saveAccount(Account(AccountId(id), name, AccountType.CREDIT_CARD, Currency.INR)); if (last4 != null) it.looks.save(AccountLook(id, last4 = last4)) }
    }

    private fun openImport(csv: String) {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); waitFor("open_data"); compose.onNodeWithTag("open_data").performScrollTo().performClick(); waitFor("import_statement")
        viewModel().startImport(file("bank.csv", csv).toString()); waitFor("import_account_bank")
    }

    private val statement = """
        Date,Narration,Withdrawal Amt.,Deposit Amt.
        01/10/26,UPI-SWIGGY-9876,250.00,
        02/10/26,CREDIT CARD PAYMENT XX1234,"30,000.00",
        03/10/26,SALARY OCT,,"50,000.00"
    """.trimIndent()

    @Test
    fun withOneCardWhoseDigitsTheLineNamesThePaymentIsATransferByDefaultAndImportingAgainAddsNothing() {
        card("hdfccard", "HDFC Regalia", "1234")
        openImport(statement)
        waitFor("import_cardpay"); waitText("import_cardpay_title", "Looks like a card payment")
        waitFor("import_cardpay_row_0")
        compose.onNodeWithTag("import_cardpay_to_hdfccard").assertIsSelected()
        assertTrue(!has("import_cardpay_needed"), "the card is known, nothing to ask")
        waitText("import_summary", "3 transactions: spending ₹250.00, income ₹50,000.00")
        waitFor("import_transfers")
        compose.onNodeWithTag("import_confirm").assertIsEnabled().performClick()
        waitFor("import_result_transfers"); waitAnyText("Imported 3 transactions")
        waitText("import_result_transfers", "₹30,000.00")
        val rows = entries()
        assertEquals(listOf("EXPENSE", "TRANSFER", "INCOME"), rows.map { it.kind })
        val t = rows[1]
        assertEquals("bank", t.account_id); assertEquals("hdfccard", t.counter_account_id); assertEquals(3_000_000L, t.amount_minor)
        val balances = runBlocking { container.write { it.store.balances() } }!!
        assertEquals(3_000_000L, balances.getValue(AccountId("hdfccard")).minorUnits, "the card's balance rises by what was paid into it")
        assertEquals(5_000_000L - 25_000L - 3_000_000L, balances.getValue(AccountId("bank")).minorUnits, "the salary in, the purchase and the payment out")
        compose.onNodeWithTag("import_close").performClick(); waitFor("import_statement")
        viewModel().startImport(file("bank-again.csv", statement).toString()); waitFor("import_account_bank")
        compose.onNodeWithTag("import_confirm").performClick(); waitAnyText("Imported 0 transactions"); waitAnyText("3 were already in the app")
        assertEquals(3, entries().size)
    }

    @Test
    fun withTwoCardsAndNoWayToTellThemApartTheImportStaysOffUntilTheCardIsChosen() {
        card("hdfccard", "HDFC Regalia"); card("icicicard", "ICICI Amazon")
        openImport(statement)
        waitFor("import_cardpay"); waitFor("import_cardpay_needed")
        compose.onNodeWithTag("import_confirm").assertIsNotEnabled()
        tap("import_cardpay_to_icicicard")
        waitGone("import_cardpay_needed")
        compose.onNodeWithTag("import_confirm").assertIsEnabled().performClick()
        waitFor("import_result_transfers")
        assertEquals("icicicard", entries().single { it.kind == "TRANSFER" }.counter_account_id)
    }

    @Test
    fun theLeftOverChoicesAreSpendingAndSkip() {
        card("hdfccard", "HDFC Regalia"); card("icicicard", "ICICI Amazon")
        openImport(statement)
        waitFor("import_cardpay_needed")
        tap("import_cardpay_skip"); waitGone("import_cardpay_needed")
        waitText("import_summary", "2 transactions: spending ₹250.00, income ₹50,000.00")
        tap("import_cardpay_as_is")
        waitText("import_summary", "3 transactions: spending ₹30,250.00, income ₹50,000.00")
        compose.onNodeWithTag("import_confirm").performClick(); waitFor("import_result")
        assertEquals(listOf("EXPENSE", "EXPENSE", "INCOME"), entries().map { it.kind })
    }

    @Test
    fun withNoCardAtAllTheStartingChoiceIsSpendingAndNothingIsAsked() {
        openImport(statement)
        waitFor("import_cardpay")
        compose.onNodeWithTag("import_cardpay_as_is").assertIsSelected()
        assertTrue(!has("import_cardpay_needed"))
        compose.onNodeWithTag("import_confirm").assertIsEnabled().performClick(); waitFor("import_result")
        assertEquals(listOf("EXPENSE", "EXPENSE", "INCOME"), entries().map { it.kind })
    }

    @Test
    fun aTransferThatMarkPaidAlreadyRecordedIsNotAddedAgain() {
        card("hdfccard", "HDFC Regalia", "1234")
        val day = LocalDate.of(2026, 10, 4).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking { container.write { it.store.save(Transfer(EntryId("markpaid"), day, AccountId("bank"), AccountId("hdfccard"), Money(3_000_000, Currency.INR)), EntryDetails(note = "Card bill payment")) } }
        openImport(statement)
        compose.onNodeWithTag("import_confirm").performClick(); waitFor("import_result")
        waitFor("import_matched")
        assertEquals(listOf("TRANSFER", "EXPENSE", "INCOME"), entries().map { it.kind }, "the Mark paid transfer, and the two other lines")
    }

    @Test
    fun aStatementWithoutCardPaymentsShowsNoGroupAndImportsAsBefore() {
        openImport("Date,Narration,Withdrawal Amt.,Deposit Amt.\n01/10/26,UPI-SWIGGY-9876,250.00,\n03/10/26,SALARY OCT,,\"50,000.00\"\n")
        waitText("import_summary", "2 transactions")
        assertTrue(!has("import_cardpay"))
        compose.onNodeWithTag("import_confirm").assertIsEnabled()
    }

    @Test
    fun aMerchantCalledCardWorldIsNotACardPayment() {
        openImport("Date,Narration,Withdrawal Amt.,Deposit Amt.\n01/10/26,UPI-CARD WORLD-PAYTM,250.00,\n")
        waitText("import_summary", "1 transaction")
        assertTrue(!has("import_cardpay"))
    }

    @Test
    fun withOneCardALineForOtherDigitsIsNotSentToItAndAskedAbout() {
        card("hdfccard", "HDFC Regalia", "1234")
        openImport("Date,Narration,Withdrawal Amt.,Deposit Amt.\n01/10/26,CREDIT CARD PAYMENT XX9999,\"30,000.00\",\n02/10/26,UPI-SWIGGY,250.00,\n")
        waitFor("import_cardpay"); waitFor("import_cardpay_needed")
        compose.onNodeWithTag("import_confirm").assertIsNotEnabled()
        tap("import_cardpay_to_hdfccard"); waitGone("import_cardpay_needed")
        compose.onNodeWithTag("import_confirm").assertIsEnabled()
    }

    @Test
    fun everyLineOfTheGroupCanBeListedWhenThereAreMoreThanThree() {
        card("hdfccard", "HDFC Regalia", "1234")
        val rows = (1..6).joinToString("\n") { "0$it/10/26,CC PAYMENT XX1234 N$it,${it}00.00," }
        openImport("Date,Narration,Withdrawal Amt.,Deposit Amt.\n$rows\n")
        waitFor("import_cardpay_show_all"); waitFor("import_cardpay_row_2")
        assertTrue(!has("import_cardpay_row_3"), "three are listed to begin with")
        waitAnyText("Show all 6")
        tap("import_cardpay_show_all"); waitFor("import_cardpay_row_5")
        waitAnyText("Show fewer")
    }

    @Test
    fun aCredAndAnAmexLineAreCardPaymentsButCredNeverDefaultsToTheOnlyCard() {
        card("hdfccard", "HDFC Regalia", "1234")
        openImport("Date,Narration,Withdrawal Amt.,Deposit Amt.\n01/10/26,CRED CLUB,5000.00,\n02/10/26,AMEX PAYMENT,100.00,\n")
        waitText("import_cardpay_count", "2 lines"); waitFor("import_cardpay_needed")
    }
}
