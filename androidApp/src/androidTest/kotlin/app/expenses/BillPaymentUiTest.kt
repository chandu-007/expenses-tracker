package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.Bill
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.CaptureEventOutcome
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A text that confirms a card bill (or a utility bill) was PAID is not a new bill: it waits in To review as a bill payment that asks "Mark the Axis card bill as paid?", Mark paid records
 * the payment (a transfer to the card account, or an expense in Bills) and marks the bill paid, and the card's own text of the same payment joins it instead of being recorded twice.
 * The old bug (the confirmation shown as a NEW bill) is repaired on opening. Banks, digits, names and amounts are invented: 9012 is the paying account, 4321 the card.
 */
@RunWith(AndroidJUnit4::class)
class BillPaymentUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private val owner = "Hi Asha, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811"
    private val kotak = "AX-KOTAKB-S"
    private val axisSender = "AX-AXISBK-S"
    private val now = System.currentTimeMillis()
    private val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH))
    private val cardText get() = "Payment of Rs 12,345.67 received towards your Axis Bank Credit Card ending 4321 on $today"
    private val amount = 1_234_567L
    private val jioText = "Payment of Rs.706.82 successful for your Jio bill"

    // ---- seeding ----
    private fun accounts(card: Boolean = true) = runBlocking { container.write { ok ->
        ok.store.saveAccount(Account(AccountId("kotak"), "Kotak Savings", AccountType.BANK, Currency.INR)); ok.looks.save(AccountLook("kotak", last4 = "9012"))
        if (card) { ok.store.saveAccount(Account(AccountId("axis"), "Axis Magnus", AccountType.CREDIT_CARD, Currency.INR)); ok.looks.save(AccountLook("axis", last4 = "4321")) }
    } }
    private fun bill(
        id: String, status: BillStatus = BillStatus.TRACKED, kind: String = "CREDIT_CARD", issuer: String = "Axis Bank", title: String = "Axis Bank credit card 4321", amountMinor: Long = amount,
        minDue: Long? = 61_728, last4: String? = "4321", sender: String = axisSender, body: String? = "Your Axis Bank credit card statement of Rs 12,345.67 is generated. Total amount due Rs 12,345.67.",
        due: String? = LocalDate.now().plusDays(8).toString(),
    ): Bill {
        val b = Bill(id, Capture.SMS, sender, issuer, kind, title, amountMinor, minDue, "INR", due, last4, null, false, now - 86_400_000, body, BillStatus.FOUND, null, null, "key-$id")
        runBlocking { container.write { ok -> ok.bills.add(b); if (status == BillStatus.TRACKED) ok.bills.track(id) } }
        return b
    }
    private fun ingest(body: String, at: Long, sender: String = kotak) = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }

    // ---- looking ----
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun click(tag: String) { val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun tap(tag: String) { waitFor(tag); click(tag) }
    /** Taps again every two seconds until [done]: a tap can be lost while the list is still moving. */
    private fun tapUntil(tag: String, done: () -> Boolean) {
        var last = 0L
        compose.waitUntil(20_000) {
            if (done()) true else {
                val t = System.nanoTime()
                if (t - last > 2_000_000_000L && has(tag)) { last = t; runCatching { click(tag) } }
                false
            }
        }
    }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString { it.text } else "" } } // merged tree: a button's words sit in its child
    private fun selected(tag: String): Boolean { waitFor(tag); val c = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config; return c.contains(SemanticsProperties.Selected) && c[SemanticsProperties.Selected] }
    private fun hasWords(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back"); waitFor("inbox_list") }
    private fun showInbox() { launch(); openInbox() }
    private fun openBills() { launch(); compose.openMore(); tap("open_bills") }
    private fun revealBill(tag: String) { compose.waitUntil(10_000) { if (has("bills_list") && !has(tag)) runCatching { compose.onNodeWithTag("bills_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) }; has(tag) } }
    private fun tapUndo() { compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }; compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick() }
    private fun vm(): LedgerViewModel { var m: LedgerViewModel? = null; scenario!!.onActivity { m = ViewModelProvider(it)[LedgerViewModel::class.java] }; return m!! }

    // ---- the database ----
    private fun idAt(at: Long) = runBlocking { container.write { it.inbox.list().first { i -> i.receivedAt == at }.id } }!!
    private fun status(id: String) = runBlocking { container.write { it.inbox.get(id)?.status } }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!
    private fun bills(s: BillStatus) = runBlocking { container.write { it.bills.list(s) } }!!
    private fun billOf(id: String) = runBlocking { container.write { it.bills.get(id) } }!!
    private fun origin(entryId: String) = runBlocking { container.write { it.origins.get(entryId) } }
    private fun categoryOf(e: app.expenses.core.storage.db.Entry) = e.category_id ?: runBlocking { container.write { ok -> ok.db.schemaQueries.splitsOf(e.id).executeAsList().single().category_id } }
    private fun billsCount() = BillStatus.entries.sumOf { bills(it).size }

    /** The card of a bank's text: the paying account is chosen on it (the text names none), then Mark paid (or the record button) is tapped until the item is added. */
    private fun payFromKotak(id: String) { tap("inbox_billpay_from_${id}_kotak"); compose.waitUntil(10_000) { selected("inbox_billpay_from_${id}_kotak") } }
    private fun markPaid(id: String) = tapUntil("inbox_billpay_add_$id") { status(id) == InboxStatus.CONFIRMED }
    private fun record(id: String) = tapUntil("inbox_billpay_record_$id") { status(id) == InboxStatus.CONFIRMED }

    /** A bank's text recorded against the tracked Axis bill: the first payment of the join tests. */
    private fun firstPaymentFromBank(withCard: Boolean): String {
        accounts(withCard); bill("axbill"); ingest(owner, now)
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_label_$id")
        payFromKotak(id); markPaid(id)
        assertEquals(1, entries().size)
        return id
    }

    // ---- (1) captured, not a bill ----

    @Test
    fun theOwnerTextOfACardBillPaymentIsPendingNotABillAndItsCardAsksToMarkTheBillPaid() {
        assertEquals(CaptureEventOutcome.CAPTURED, ingest(owner, now))
        assertEquals(0, billsCount(), "no bill, found or otherwise")
        val id = runBlocking { container.write { it.inbox.list().single().id } }!!
        showInbox(); waitFor("inbox_billpay_label_$id")
        assertEquals("Bill payment", textOf("inbox_billpay_label_$id"))
        assertEquals("Mark the Axis card bill as paid?", textOf("inbox_billpay_question_$id"))
        openBills(); waitFor("bills_empty")
    }

    // ---- (2) Mark paid with a tracked bill and a card account ----

    @Test
    fun markPaidMakesATransferToTheCardMarksTheBillPaidAndUndoPutsEverythingBack() {
        accounts(); val b = bill("axbill"); ingest(owner, now)
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_label_$id")
        waitFor("inbox_billpay_pick_${id}_${b.id}"); assertTrue(selected("inbox_billpay_pick_${id}_${b.id}"), "the one bill it may pay starts chosen")
        payFromKotak(id); markPaid(id)
        val e = entries().single()
        assertEquals("TRANSFER", e.kind); assertEquals("kotak", e.account_id); assertEquals("axis", e.counter_account_id); assertEquals(amount, e.amount_minor); assertEquals(now, e.occurred_at, "dated by the text")
        assertEquals(now, origin(e.id)!!.receivedAt); assertTrue(origin(e.id)!!.text.contains("bill payment of Rs. 12,345.67"), "the text is the entry's origin")
        val paid = billOf(b.id)
        assertEquals(BillStatus.PAID, paid.status); assertEquals(e.id, paid.entryId); assertEquals(now, paid.paidAt)
        assertEquals(InboxStatus.CONFIRMED, status(id))
        openBills(); waitFor("bills_paid_title"); revealBill("bill_${b.id}"); assertTrue(has("bill_unpay_${b.id}"), "listed as paid")
    }

    @Test
    fun undoOnTheBarAfterMarkPaidPutsTheBillBackWithItsTextDeletesTheEntryAndTheItemWaitsAgain() {
        accounts(); val b = bill("axbill"); ingest(owner, now)
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_label_$id")
        payFromKotak(id); markPaid(id)
        assertEquals(1, entries().size); val entryId = entries().single().id
        compose.waitUntil(10_000) { hasWords("Marked ${b.title} as paid") }
        tapUndo()
        compose.waitUntil(10_000) { status(id) == InboxStatus.PENDING }
        val back = billOf(b.id)
        assertEquals(BillStatus.TRACKED, back.status); assertEquals(b.body, back.body); assertEquals(null, back.entryId); assertEquals(null, back.paidAt)
        assertEquals(emptyList(), entries(), "the entry is deleted")
        assertEquals(1L, runBlocking { container.write { it.db.schemaQueries.entryById(entryId).executeAsOne().deleted } })
        assertEquals(null, origin(entryId), "its words are gone")
        assertEquals(null, runBlocking { container.write { it.db.schemaQueries.entryById(entryId).executeAsOne().source_ref } }, "and no source reference is held")
        waitFor("inbox_billpay_label_$id")
    }

    // ---- (3) no card account ----

    @Test
    fun withNoCardAccountMarkPaidRecordsAnExpenseInBillsFromThePayingAccount() {
        accounts(card = false); val b = bill("axbill"); ingest(owner, now)
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_label_$id")
        payFromKotak(id)
        val why = textOf("inbox_billpay_records_$id")
        assertTrue(why.contains("Recorded as an expense in Bills") && why.contains("the app has no account for this card"), why)
        markPaid(id)
        val e = entries().single()
        assertEquals("EXPENSE", e.kind); assertEquals("kotak", e.account_id); assertEquals(amount, e.amount_minor); assertEquals("Bills", categoryOf(e))
        assertEquals(BillStatus.PAID, billOf(b.id).status); assertEquals(e.id, billOf(b.id).entryId)
    }

    // ---- (4) no open bill, or only bills of another card ----

    @Test
    fun withNoOpenBillItIsRecordedAsACardBillPaymentWithoutTouchingAnyBill() {
        accounts(); ingest(owner, now)
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_nomatch_$id")
        assertTrue(!has("inbox_billpay_add_$id"))
        payFromKotak(id)
        assertEquals("Record a card bill payment of ₹12,345.67", textOf("inbox_billpay_record_$id"))
        record(id)
        val e = entries().single()
        assertEquals("TRANSFER", e.kind); assertEquals("kotak", e.account_id); assertEquals("axis", e.counter_account_id); assertEquals(amount, e.amount_minor)
        assertEquals(0, billsCount())
    }

    @Test
    fun aBillOfAnotherBankAnotherCardOrAnotherAmountIsNotOffered() {
        accounts()
        val hdfc = bill("hdfc", issuer = "HDFC Bank", title = "HDFC Bank credit card 0862", last4 = "0862", sender = "AX-HDFCBK-S")
        val otherCard = bill("axother", title = "Axis Bank credit card 9999", last4 = "9999")
        val small = bill("axsmall", amountMinor = 500_000, minDue = 10_000, title = "Axis Bank credit card 4321 (small)")
        ingest(owner, now)
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_nomatch_$id")
        listOf(hdfc, otherCard, small).forEach { assertTrue(!has("inbox_billpay_pick_${id}_${it.id}"), "${it.id} is not this payment's bill") }
        assertTrue(!has("inbox_billpay_add_$id"))
        payFromKotak(id); record(id)
        assertEquals(3, bills(BillStatus.TRACKED).size, "no bill was touched"); assertEquals(0, bills(BillStatus.PAID).size)
    }

    // ---- (5) Not this, Ignore ----

    @Test
    fun notThisTurnsTheCardIntoAnOrdinaryOneAndAddMakesAnExpense() {
        accounts(); ingest(owner, now)
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_not_$id")
        assertTrue(!has("inbox_kinds_$id") && !has("inbox_add_$id"), "a bill payment card has no type chips and no plain Add")
        tapUntil("inbox_billpay_not_$id") { has("inbox_kinds_$id") }
        waitFor("inbox_add_$id"); assertTrue(!has("inbox_billpay_question_$id"))
        assertTrue(textOf("inbox_add_$id").startsWith("Add"), textOf("inbox_add_$id"))
        tapUntil("inbox_add_$id") { status(id) == InboxStatus.CONFIRMED }
        val e = entries().single(); assertEquals("EXPENSE", e.kind); assertEquals(amount, e.amount_minor)
    }

    @Test
    fun ignoreSkipsTheTextAndOffersUndo() {
        accounts(); ingest(owner, now)
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_ignore_$id")
        tapUntil("inbox_billpay_ignore_$id") { status(id) == InboxStatus.DISMISSED }
        compose.waitUntil(10_000) { hasWords("Skipped") }
        assertEquals(emptyList(), entries()); assertEquals(0, billsCount())
        tapUndo(); compose.waitUntil(10_000) { status(id) == InboxStatus.PENDING }
    }

    // ---- (6) the card's own text of the same payment ----

    private fun assertSame(id: String) {
        waitFor("inbox_billpay_same_$id")
        val note = textOf("inbox_billpay_same_$id")
        assertTrue(note.contains("Already in your ledger") || note.contains("You already marked"), note)
        waitFor("inbox_billpay_join_$id"); assertTrue(!has("inbox_billpay_add_$id") && !has("inbox_billpay_record_$id"), "nothing new is offered")
    }

    @Test
    fun theCardsOwnTextAfterTheBankPaymentWasMarkedJoinsItAndUndoRestores() {
        firstPaymentFromBank(withCard = true)
        val entryId = entries().single().id
        ingest(cardText, now + 5_000, axisSender)
        showInbox(); val id = idAt(now + 5_000)
        assertSame(id)
        tapUntil("inbox_billpay_join_$id") { status(id) == InboxStatus.CONFIRMED }
        assertEquals(1, entries().size); assertEquals("TRANSFER", entries().single().kind)
        val words = origin(entryId)!!.text
        assertTrue(words.contains("bill payment of Rs. 12,345.67") && words.contains("received towards your Axis Bank Credit Card"), words)
        compose.waitUntil(10_000) { hasWords("Added to the transfer") }
        tapUndo(); compose.waitUntil(10_000) { status(id) == InboxStatus.PENDING }
        assertEquals(1, entries().size)
        assertTrue(!origin(entryId)!!.text.contains("received towards"), "the entry's words are as they were")
        assertEquals(BillStatus.PAID, bills(BillStatus.PAID).single().status)
    }

    @Test
    fun theCardsTextRecordedFirstThenTheBanksTextJoinsIt() {
        accounts(); ingest(cardText, now, axisSender)
        showInbox(); val first = idAt(now); waitFor("inbox_billpay_nomatch_$first")
        assertEquals("Mark the Axis card bill as paid?", textOf("inbox_billpay_question_$first"))
        payFromKotak(first); record(first)
        val e = entries().single(); assertEquals("TRANSFER", e.kind); assertEquals("kotak", e.account_id); assertEquals("axis", e.counter_account_id); assertEquals(amount, e.amount_minor)
        ingest(owner, now + 5_000)
        showInbox(); val second = idAt(now + 5_000)
        assertSame(second)
        tapUntil("inbox_billpay_join_$second") { status(second) == InboxStatus.CONFIRMED }
        assertEquals(1, entries().size)
        val words = origin(e.id)!!.text
        assertTrue(words.contains("received towards your Axis Bank Credit Card") && words.contains("bill payment of Rs. 12,345.67"), words)
        compose.waitUntil(10_000) { hasWords("Added to the transfer") }
        tapUndo(); compose.waitUntil(10_000) { status(second) == InboxStatus.PENDING }
        assertTrue(!origin(e.id)!!.text.contains("T&C -Kotak811"), "the bank's words are taken off again")
    }

    @Test
    fun withNoCardAccountTheCardsCreditTextJoinsTheExpenseInBillsAndIsNeverIncome() {
        firstPaymentFromBank(withCard = false)
        val e = entries().single(); assertEquals("EXPENSE", e.kind); assertEquals("Bills", categoryOf(e))
        ingest(cardText, now + 5_000, axisSender)
        showInbox(); val id = idAt(now + 5_000)
        assertSame(id)
        assertTrue(!has("inbox_kinds_$id"), "no Income choice is offered for the card's text")
        tapUntil("inbox_billpay_join_$id") { status(id) == InboxStatus.CONFIRMED }
        assertEquals(listOf("EXPENSE"), entries().map { it.kind }, "one entry, and no income")
        assertTrue(origin(e.id)!!.text.contains("received towards your Axis Bank Credit Card"))
        compose.waitUntil(10_000) { hasWords("Added to the payment you recorded") }
        tapUndo(); compose.waitUntil(10_000) { status(id) == InboxStatus.PENDING }
        assertEquals(1, entries().size)
    }

    // ---- (7) the old bug: the confirmation was filed as a bill; moving it is the person's tap (Bills, "Move to To review"), never automatic ----

    private fun wrongBill(status: BillStatus, due: String? = null) = bill("wrong", status, issuer = "Kotak Mahindra Bank", title = "Kotak Mahindra Bank credit card", sender = kotak, last4 = null, minDue = null, body = owner, due = due)

    @Test
    fun aFoundBillThatWasReallyAPaymentConfirmationIsMovedToToReviewOnlyByTheTapAndUndoPutsItBack() {
        accounts(); wrongBill(BillStatus.FOUND)
        launch(); vm().loadInbox(); compose.waitForIdle()
        assertEquals(BillStatus.FOUND, billOf("wrong").status, "opening the app or To review changes no bill by itself")
        openBills(); waitFor("bills_repair_card")
        assertTrue(textOf("bills_repair_text").startsWith("1 bill below is really the confirmation of a payment"), textOf("bills_repair_text"))
        tapUntil("bills_repair_move") { billOf("wrong").status == BillStatus.DISMISSED }
        assertEquals(emptyList(), bills(BillStatus.FOUND)); gone("bills_repair_card")
        val item = runBlocking { container.write { it.inbox.list().single() } }!!
        assertTrue(item.body!!.contains("bill payment of Rs. 12,345.67"), "the original words wait in To review"); assertEquals(amount, item.amountMinor); assertEquals(InboxStatus.PENDING, item.status)
        assertEquals(amount, billOf("wrong").amountMinor, "the bill row is skipped, not deleted")
        tapUndo(); compose.waitUntil(10_000) { billOf("wrong").status == BillStatus.FOUND }
        assertEquals(owner, billOf("wrong").body); assertEquals(null, status(item.id), "the untouched item the move made is taken away again"); assertEquals(0, runBlocking { container.write { it.inbox.pendingCount() } }!!.toInt())
        waitFor("bills_repair_card")
        tapUntil("bills_repair_move") { billOf("wrong").status == BillStatus.DISMISSED }
        compose.waitUntil(10_000) { runBlocking { container.write { it.inbox.list().size } } == 1 }
        val again = runBlocking { container.write { it.inbox.list().single() } }!!
        tap("bills_back"); openInbox(); waitFor("inbox_billpay_label_${again.id}")
        assertEquals("Mark the Axis card bill as paid?", textOf("inbox_billpay_question_${again.id}"))
    }

    @Test
    fun aBillThePersonAddedPaidEditedOrThatIsARealBillIsNeverOfferedOrMoved() {
        accounts(); wrongBill(BillStatus.TRACKED)
        bill("paid", BillStatus.TRACKED, issuer = "Kotak Mahindra Bank", sender = kotak, last4 = null, minDue = null, body = owner, due = null).also { runBlocking { container.write { ok -> ok.bills.markPaid("paid", null, now) } } }
        bill("edited", BillStatus.FOUND, issuer = "Kotak Mahindra Bank", sender = kotak, last4 = null, minDue = null, body = owner, due = LocalDate.now().plusDays(20).toString())
        bill("real", BillStatus.FOUND)
        openBills(); waitFor("bills_list")
        compose.waitForIdle(); assertTrue(!has("bills_repair_card"), "none of them is a bill the earlier version made and nobody touched")
        launch(); vm().loadInbox(); compose.waitForIdle()
        assertEquals(BillStatus.TRACKED, billOf("wrong").status); assertEquals(BillStatus.PAID, billOf("paid").status); assertEquals(BillStatus.FOUND, billOf("edited").status); assertEquals(BillStatus.FOUND, billOf("real").status)
        assertEquals(emptyList(), runBlocking { container.write { it.inbox.list() } })
    }

    // ---- (8) a utility ----

    @Test
    fun aJioPaymentMarksTheTrackedJioBillPaidAndRecordsAnExpenseInBills() {
        accounts(card = false)
        val b = bill("jio", kind = "MOBILE", issuer = "Jio", title = "Jio mobile bill", amountMinor = 70_682, minDue = null, last4 = null, sender = "AX-JIOPAY-S", body = "Your Jio bill of Rs 706.82 is due.")
        assertEquals(CaptureEventOutcome.CAPTURED, ingest(jioText, now, "AX-JIOPAY-S"))
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_label_$id")
        assertEquals("Mark this bill as paid?", textOf("inbox_billpay_question_$id"))
        waitFor("inbox_billpay_pick_${id}_${b.id}"); assertTrue(selected("inbox_billpay_pick_${id}_${b.id}"))
        payFromKotak(id); markPaid(id)
        val e = entries().single()
        assertEquals("EXPENSE", e.kind); assertEquals(70_682L, e.amount_minor); assertEquals("kotak", e.account_id); assertEquals("Bills", categoryOf(e))
        assertEquals(BillStatus.PAID, billOf(b.id).status); assertEquals(e.id, billOf(b.id).entryId)
    }

    @Test
    fun aJioPaymentOfAnotherAmountDoesNotOfferTheBill() {
        accounts(card = false)
        val b = bill("jio", kind = "MOBILE", issuer = "Jio", title = "Jio mobile bill", amountMinor = 70_682, minDue = null, last4 = null, sender = "AX-JIOPAY-S", body = "Your Jio bill of Rs 706.82 is due.")
        ingest("Payment of Rs.1,999.00 successful for your Jio bill", now, "AX-JIOPAY-S")
        showInbox(); val id = idAt(now); waitFor("inbox_billpay_nomatch_$id")
        assertTrue(!has("inbox_billpay_pick_${id}_${b.id}")); assertEquals(BillStatus.TRACKED, billOf(b.id).status)
    }

    // ---- a bill that is still a bill ----

    @Test
    fun aGeneratedCardStatementFromTheCardsOwnBankIsStillABillOnTheBillsPageNotInToReview() {
        val statement = "Your Axis Bank credit card statement of Rs 12,345.67 is generated, total amount due Rs 12,345.67, due date 25-Nov-2026"
        assertEquals(CaptureEventOutcome.BILL, ingest(statement, now, axisSender))
        val b = bills(BillStatus.FOUND).single(); assertEquals(amount, b.amountMinor)
        assertEquals(emptyList(), runBlocking { container.write { it.inbox.list() } })
        openBills(); revealBill("bill_${b.id}")
        assertTrue(textOf("bill_amount_${b.id}").contains("₹12,345.67"))
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_empty")
    }

    // ---- the sender: a header the list does not know captures nothing, is offered under Capture, and trusting it captures the next text ----

    @Test
    fun aKotak811HeaderThatIsNotOnTheListCapturesNothingIsOfferedUnderCaptureAndTrustingItCapturesTheNextPaymentText() {
        accounts()
        val sender = "AX-KOTAK811-S"
        assertEquals(CaptureEventOutcome.NOT_ALLOWLISTED, ingest(owner, now, sender), "nothing of the text is read: the header is not on the list")
        assertEquals(emptyList(), runBlocking { container.write { it.inbox.list() } }); assertEquals(0, billsCount())
        launch(); compose.openMore(); tap("open_capture"); waitFor("skipped_sender_KOTAK811")
        assertEquals("KOTAK811", textOf("skipped_sender_KOTAK811")); assertEquals("1 text skipped", textOf("skipped_count_KOTAK811"))
        tapUntil("skipped_trust_KOTAK811") { has("sender_chip_KOTAK811") }
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("$owner ", now + 60_000, sender), "from now on its payment text is read: a payment, never a bill")
        assertEquals(0, billsCount())
        showInbox(); val id = idAt(now + 60_000); waitFor("inbox_billpay_question_$id")
        assertEquals("Mark the Axis card bill as paid?", textOf("inbox_billpay_question_$id"))
    }
}
