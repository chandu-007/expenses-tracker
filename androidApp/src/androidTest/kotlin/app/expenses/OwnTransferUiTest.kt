package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.InboxStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two texts a bank sends for one move of money between a person's own accounts (invented digits: 1234 is the Bank account, 5678 the Savings account): one combined card,
 * Add makes ONE transfer, "These are separate" keeps two items, and a transfer whose first text was already added as an expense is offered as a conversion with Undo.
 */
@RunWith(AndroidJUnit4::class)
class OwnTransferUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)).uppercase()
    private val out = "Amt Deducted!\nRs.45,250.75 from your HDFC Bank A/c XX1234 for Money Transfer via HDFC Bank Online Banking.\nNot you?Call 18002586161/SMS BLOCK OB to 7308080808"
    private val into get() = "Update! INR 45,250.75 deposited in HDFC Bank A/c XX5678 on $today for XXXXXXXXXX1234-TPT-Holiday Fund Remembe-SAMPLE TRADERS PRIVATE LIMITED.Avl bal INR 3,21,654.32. Cheque deposits in A/C are subject to clearing"
    private val now = System.currentTimeMillis() - 3_600_000

    private fun twoAccounts(digitsOnSavings: Boolean = true) = runBlocking { container.write { ok ->
        ok.store.saveAccount(Account(AccountId("savings"), "Savings", AccountType.BANK, Currency.INR))
        ok.looks.save(AccountLook("bank", last4 = "1234"))
        if (digitsOnSavings) ok.looks.save(AccountLook("savings", last4 = "5678"))
    } }
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "HDFCBK", body, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasWords(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back") }
    private fun idAt(t: Long) = runBlocking { container.write { it.inbox.list().first { i -> i.receivedAt == t }.id } }!!
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.recentEntries(50).executeAsList() } }!!
    private fun status(id: String) = runBlocking { container.write { it.inbox.get(id)?.status } }
    private fun balance(id: String) = runBlocking { container.write { it.store.balances().getValue(AccountId(id)).minorUnits } }!!
    private fun tapUndo() { compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }; compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick() }

    @Test
    fun theTwoTextsOfOneTransferAreOneCardAndAddMakesOneTransferNotAnExpenseAndIncome() {
        twoAccounts(); ingest(out, now); ingest(into, now + 40_000)
        launch(); openInbox(); val o = idAt(now); val i = idAt(now + 40_000)
        waitFor("inbox_transfer_$o")
        assertTrue(textOf("inbox_transfer_$o").startsWith("Transfer from Bank to Savings"), textOf("inbox_transfer_$o"))
        assertTrue(textOf("inbox_transfer_$o").contains("45,250.75"), textOf("inbox_transfer_$o"))
        assertTrue(!has("inbox_$i"), "the second text has no card of its own")
        assertEquals("2 to review", textOf("inbox_count"))
        tap("inbox_transfer_show_$o")
        assertTrue(textOf("inbox_transfer_text_out_$o").contains("Money Transfer via HDFC Bank Online Banking"))
        assertTrue(textOf("inbox_transfer_text_in_$o").contains("TPT-Holiday Fund Remembe"))
        tap("inbox_transfer_add_$o"); compose.waitUntil(10_000) { has("inbox_empty") }
        val e = entries().single()
        assertEquals("TRANSFER", e.kind); assertEquals("bank", e.account_id); assertEquals("savings", e.counter_account_id); assertEquals(4_525_075, e.amount_minor)
        assertEquals("Holiday Fund Remembe", e.merchant)
        assertEquals(-4_525_075L, balance("bank")); assertEquals(4_525_075L, balance("savings"))
        assertEquals(InboxStatus.CONFIRMED, status(o)); assertEquals(InboxStatus.CONFIRMED, status(i))
        val origin = runBlocking { container.write { it.origins.get(e.id) } }!!
        assertTrue(origin.text.contains("Amt Deducted") && origin.text.contains("deposited in HDFC Bank"), "both texts are kept as they were")
    }

    @Test
    fun theseAreSeparateKeepsTwoItemsAndTheyAreNotPairedAgainAfterARestart() {
        twoAccounts(); ingest(out, now); ingest(into, now + 40_000)
        launch(); openInbox(); val o = idAt(now); val i = idAt(now + 40_000)
        tap("inbox_transfer_apart_$o")
        waitFor("inbox_$i"); waitFor("inbox_add_$o")
        assertTrue(!has("inbox_transfer_$o"))
        assertEquals("2 to review", textOf("inbox_count"))
        launch(); openInbox(); waitFor("inbox_$i"); waitFor("inbox_$o")
        assertTrue(!has("inbox_transfer_$o"), "still two items after the app was opened again")
        // each is still an ordinary item that can be added on its own
        tap("inbox_add_$i"); compose.waitUntil(10_000) { status(i) == InboxStatus.CONFIRMED }
        assertEquals("INCOME", entries().single().kind)
    }

    @Test
    fun aFirstTextAddedAsAnExpenseOffersToMakeTheSecondTextATransferAndUndoPutsItBack() {
        twoAccounts(); ingest(out, now)
        launch(); openInbox(); val o = idAt(now); waitFor("inbox_$o")
        // the first text alone: it may be a transfer to the person's other account, and adding it as a payment stays possible
        assertTrue(textOf("inbox_transfer_hint_$o").startsWith("Might be a transfer to your other account, Savings"), textOf("inbox_transfer_hint_$o"))
        tap("inbox_add_$o"); compose.waitUntil(10_000) { status(o) == InboxStatus.CONFIRMED }
        assertEquals("EXPENSE", entries().single().kind)
        val expenseId = entries().single().id
        ingest(into, now + 120_000)
        launch(); openInbox(); val i = idAt(now + 120_000)
        waitFor("inbox_convert_note_$i")
        assertEquals("Make this a transfer: it matches the earlier ₹45,250.75 expense from Bank", textOf("inbox_convert_note_$i"))
        waitFor("inbox_add_$i") // adding it plainly (as income) stays possible
        tap("inbox_convert_$i")
        compose.waitUntil(10_000) { status(i) == InboxStatus.CONFIRMED }
        val e = entries().single()
        assertEquals("TRANSFER", e.kind); assertEquals("bank", e.account_id); assertEquals("savings", e.counter_account_id)
        assertEquals(1L, runBlocking { container.write { it.db.schemaQueries.entryById(expenseId).executeAsOne().deleted } }, "the earlier expense is soft-deleted, not lost")
        assertEquals(-4_525_075L, balance("bank")); assertEquals(4_525_075L, balance("savings"))
        tapUndo()
        compose.waitUntil(10_000) { status(i) == InboxStatus.PENDING }
        assertEquals(listOf("EXPENSE"), entries().map { it.kind }); assertEquals(expenseId, entries().single().id)
        assertEquals(-4_525_075L, balance("bank")); assertEquals(0L, balance("savings"))
        waitFor("inbox_$i"); waitFor("inbox_convert_$i")
        assertTrue(runBlocking { container.write { it.inbox.get(i)?.body } }!!.contains("deposited in HDFC Bank"), "the text is back with the item")
    }

    @Test
    fun theSecondTextAcceptedFirstOffersTheConversionOnTheFirstText() {
        twoAccounts(); ingest(into, now)
        launch(); openInbox(); val i = idAt(now); waitFor("inbox_$i")
        tap("inbox_add_$i"); compose.waitUntil(10_000) { status(i) == InboxStatus.CONFIRMED }
        assertEquals("INCOME", entries().single().kind)
        ingest(out, now + 90_000)
        launch(); openInbox(); val o = idAt(now + 90_000)
        waitFor("inbox_convert_note_$o")
        assertEquals("Make this a transfer: it matches the earlier ₹45,250.75 income into Savings", textOf("inbox_convert_note_$o"))
        tap("inbox_convert_$o"); compose.waitUntil(10_000) { status(o) == InboxStatus.CONFIRMED }
        val e = entries().single(); assertEquals("TRANSFER", e.kind); assertEquals("bank", e.account_id); assertEquals("savings", e.counter_account_id)
    }

    @Test
    fun withNoDigitsOnTheDestinationAccountNothingIsPairedAndTheCardsAskForTheDigits() {
        twoAccounts(digitsOnSavings = false); ingest(out, now); ingest(into, now + 40_000)
        launch(); openInbox(); val o = idAt(now); val i = idAt(now + 40_000)
        waitFor("inbox_$o"); waitFor("inbox_$i")
        assertTrue(!has("inbox_transfer_$o"), "no combined card")
        assertTrue(textOf("inbox_setup_digits_$i").contains("Set the last four digits"), textOf("inbox_setup_digits_$i"))
        assertTrue(textOf("inbox_setup_digits_$i").contains("recognise transfers"))
    }

    @Test
    fun sameAmountOnADifferentDayOrWithoutTransferWordsOrOnOneAccountIsNotPaired() {
        twoAccounts()
        // a debit and a credit of one amount a minute apart on two accounts, but neither says "transfer" and neither names the other account: two unrelated items
        ingest("Rs.45,250.75 debited from A/c XX1234 on $today to VPA shop@okhdfc (UPI Ref 100000000555). Not you? Call 18002586161", now)
        ingest("Rs.45,250.75 credited to A/c XX5678 on $today by VPA ravi@ybl (UPI Ref 628374659999).", now + 60_000)
        // the same amount, but out of and into the same account
        ingest("Rs.50,000.00 debited from A/c XX1234 for Money Transfer via Online Banking.", now + 5_000_000)
        ingest("Rs.50,000.00 credited to A/c XX1234 on $today for NEFT Cr-SAMPLE0INBX01-Some Remitter-Customer-REF123456789", now + 5_060_000)
        launch(); openInbox()
        val ids = runBlocking { container.write { it.inbox.list().map { i -> i.id } } }!!
        assertEquals(4, ids.size)
        waitFor("inbox_count"); assertEquals("4 to review", textOf("inbox_count"))
        // asked of the logic itself (a long list shows only the cards on screen): nothing is paired, and nothing was offered as a conversion either
        val scan = runBlocking { container.write { ok ->
            val accounts = ok.db.schemaQueries.allAccounts().executeAsList().filter { it.archived == 0L }
            TransferScan.of(ok, ok.inbox.list(), ids.toSet(), accounts)
        } }
        assertTrue(scan != null && scan.pairs.isEmpty() && scan.offers.isEmpty(), "no pair and no offer: ${scan?.pairs}")
        assertTrue(!has("inbox_transfer_add_${ids[0]}"))
    }

    @Test
    fun twoTextsWithTheSameBankReferenceCanBeConvertedUndoneAddedPlainlyAndConvertedAgain() {
        twoAccounts()
        val ref = "628374659283"
        val debit = "HDFC Bank:Rs. 45,250.75 debited from a/c *1234 on $today to a/c **5678 (UPI Ref No. $ref). Not you? Call 18002586161 to report"
        val credit = "Rs.45,250.75 credited to A/c XX5678 on $today from A/c XX1234 (UPI Ref No. $ref)."
        ingest(debit, now)
        launch(); openInbox(); val o = idAt(now); waitFor("inbox_add_$o")
        tap("inbox_add_$o"); compose.waitUntil(10_000) { status(o) == InboxStatus.CONFIRMED }
        val expenseId = entries().single().id
        ingest(credit, now + 120_000)
        launch(); openInbox(); val i = idAt(now + 120_000)
        waitFor("inbox_convert_note_$i")
        assertEquals("You can undo this until you leave this screen.", textOf("inbox_convert_undo_note_$i"))
        tap("inbox_convert_$i")   // the same reference on both texts used to be refused here
        compose.waitUntil(10_000) { status(i) == InboxStatus.CONFIRMED }
        assertEquals(listOf("TRANSFER"), entries().map { it.kind })
        tapUndo()
        compose.waitUntil(10_000) { status(i) == InboxStatus.PENDING }
        assertEquals(expenseId, entries().single().id)
        // after the Undo the restored text can be added as it is (it used to be refused: the deleted transfer held its reference) ...
        waitFor("inbox_add_$i"); tap("inbox_add_$i")
        compose.waitUntil(10_000) { status(i) == InboxStatus.CONFIRMED }
        assertEquals(setOf("EXPENSE", "INCOME"), entries().map { it.kind }.toSet())
    }

    @Test
    fun afterAnUndoTheTextCanBeMadeATransferAgain() {
        twoAccounts(); ingest(out, now)
        launch(); openInbox(); val o = idAt(now); waitFor("inbox_add_$o"); tap("inbox_add_$o")
        compose.waitUntil(10_000) { status(o) == InboxStatus.CONFIRMED }
        ingest(into, now + 120_000)
        launch(); openInbox(); val i = idAt(now + 120_000); waitFor("inbox_convert_$i")
        tap("inbox_convert_$i"); compose.waitUntil(10_000) { status(i) == InboxStatus.CONFIRMED }
        tapUndo(); compose.waitUntil(10_000) { status(i) == InboxStatus.PENDING }
        waitFor("inbox_convert_$i"); tap("inbox_convert_$i")
        compose.waitUntil(10_000) { status(i) == InboxStatus.CONFIRMED }
        assertEquals(listOf("TRANSFER"), entries().map { it.kind })
    }

    @Test
    fun aSalaryCreditByNeftOnTheOtherAccountIsNotPairedWithAnUnrelatedPaymentOfTheSameAmountAndGetsNoTransferHint() {
        twoAccounts()
        ingest("Rs.50,000.00 debited from A/c XX1234 on $today to VPA shop@okhdfc (UPI Ref 100000000555). Not you? Call 18002586161", now)
        ingest("Update! INR 50,000.00 deposited in HDFC Bank A/c XX5678 on $today for NEFT Cr-SAMPLE0INBX01-Acme Payroll-Customer-REF123456789.Avl bal INR 9.00", now + 600_000)
        launch(); openInbox(); val o = idAt(now); val i = idAt(now + 600_000)
        waitFor("inbox_count")
        val scan = runBlocking { container.write { ok -> TransferScan.of(ok, ok.inbox.list(), setOf(o, i), ok.db.schemaQueries.allAccounts().executeAsList().filter { it.archived == 0L }) } }
        assertTrue(scan != null && scan.pairs.isEmpty() && scan.lone.getValue(i).mightBeTo == null, "no pair and no 'might be a transfer' on the salary-like credit")
    }
}
