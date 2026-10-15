package app.expenses

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.CaptureEventOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A bill in a text: found, announced, added, paid or skipped, and settled by the payment text that follows. */
@RunWith(AndroidJUnit4::class)
class BillsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before fun fresh() {
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get() // the hourly check could post a notice in the middle of a test
        runBlocking { container.startFresh() }
        manager.cancelAll()
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() }; manager.cancelAll() }

    private val due = LocalDate.now().plusDays(12)
    private val dueText = due.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    private fun cardText(amount: String = "12,345.00", min: String = "617.00") = "Your HDFC Bank Credit Card ending 0862 statement is generated. Total Amt Due Rs.$amount, Min Amt Due Rs.$min, Due Date $dueText."
    private val electricity get() = "Dear Customer, your BESCOM electricity bill of Rs 1,842.50 for consumer no 1234567890 is due on $dueText. Pay online to avoid disconnection."
    private fun ingest(body: String, at: Long = System.currentTimeMillis(), sender: String = "AX-HDFCBK-S") = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun bills(status: BillStatus) = runBlocking { container.write { it.bills.list(status) } }!!
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!

    private fun launch(action: String? = null) {
        compose.waitForIdle(); scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).also { if (action != null) it.action = action })
        if (action == null) waitFor("add")
    }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun reveal(tag: String) { if (has("bills_list") && !has(tag)) runCatching { compose.onNodeWithTag("bills_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) } }
    private fun tap(tag: String) { compose.waitUntil(10_000) { reveal(tag); has(tag) }; compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun openBills() { launch(); tap("more_menu"); tap("open_bills"); waitFor("bills_list") }
    private fun shade(): List<Pair<String, String>> = manager.activeNotifications.map { (it.notification.extras.getCharSequence("android.title")?.toString().orEmpty()) to (it.notification.extras.getCharSequence("android.text")?.toString().orEmpty()) }

    @Test
    fun aStatementTextBecomesABillWithItsFiguresAndTheHomeBannerAnnouncesIt() {
        assertEquals(CaptureEventOutcome.BILL, ingest(cardText()))
        val b = bills(BillStatus.FOUND).single()
        assertEquals("HDFC Bank credit card 0862", b.title); assertEquals(1_234_500L, b.amountMinor); assertEquals(61_700L, b.minDueMinor); assertEquals(due.toString(), b.dueDate)
        launch(); waitFor("bills_banner")
        compose.onNodeWithTag("bills_banner_title", useUnmergedTree = true).assertTextContains("1 new bill found", substring = true)
        tap("bills_banner"); waitFor("bill_${b.id}")
        compose.onNodeWithTag("bill_amount_${b.id}", useUnmergedTree = true).assertTextContains("₹12,345.00", substring = true)
        compose.onNodeWithTag("bill_min_${b.id}", useUnmergedTree = true).assertTextContains("₹617.00", substring = true)
        compose.onNodeWithTag("bill_due_${b.id}", useUnmergedTree = true).assertTextContains("in 12 days", substring = true)
        assertEquals(emptyList(), entries(), "nothing is in the ledger until the person says so")
    }

    @Test
    fun addingABillMovesItToComingUpAndPayingItRecordsATransferToTheCardAndMarksItPaid() {
        runBlocking { container.write { it.store.saveAccount(Account(AccountId("amex"), "Amex", AccountType.CREDIT_CARD, Currency.INR)) } }
        ingest(cardText()); val id = bills(BillStatus.FOUND).single().id
        openBills()
        tap("bill_add_$id"); gone("bill_add_$id"); waitFor("bill_pay_$id"); waitFor("bills_coming_title")
        assertEquals(BillStatus.TRACKED, bills(BillStatus.TRACKED).single().status); assertEquals(null, bills(BillStatus.TRACKED).single().body, "the message is forgotten once added")
        tap("bill_pay_$id"); waitFor("bill_pay_amount")
        tap("bill_pay_min")                      // pay the minimum this time
        tap("bill_pay_card_Amex"); tap("bill_pay_ok")
        gone("bill_pay_ok"); waitFor("bills_paid_title")
        val e = entries().single()
        assertEquals("TRANSFER", e.kind); assertEquals(61_700L, e.amount_minor); assertEquals("amex", e.counter_account_id); assertEquals("bank", e.account_id)
        val paid = bills(BillStatus.PAID).single()
        assertEquals(e.id, paid.entryId)
    }

    @Test
    fun anElectricityBillPaidWithRecordingIsAnExpenseInBillsAndMarkingItWithoutRecordingMakesNoEntry() {
        ingest(electricity); ingest(cardText(), at = System.currentTimeMillis() + 1)
        val ids = bills(BillStatus.FOUND).associate { it.kind to it.id }
        openBills()
        tap("bill_add_${ids.getValue("ELECTRICITY")}"); tap("bill_add_${ids.getValue("CREDIT_CARD")}")
        tap("bill_pay_${ids.getValue("ELECTRICITY")}"); waitFor("bill_pay_amount"); tap("bill_pay_ok"); gone("bill_pay_ok")
        val e = entries().single()
        assertEquals("EXPENSE", e.kind); assertEquals(184_250L, e.amount_minor); assertEquals("Bills", e.category_id ?: runBlocking { container.write { ok -> ok.db.schemaQueries.splitsOf(e.id).executeAsList().single().category_id } })
        tap("bill_pay_${ids.getValue("CREDIT_CARD")}"); waitFor("bill_pay_record"); tap("bill_pay_record"); tap("bill_pay_ok"); gone("bill_pay_ok")
        assertEquals(1, entries().size, "the second was only marked paid")
        assertEquals(2, bills(BillStatus.PAID).size)
        assertEquals(null, bills(BillStatus.PAID).first { it.kind == "CREDIT_CARD" }.entryId)
        tap("bill_unpay_${ids.getValue("CREDIT_CARD")}"); gone("bill_unpay_${ids.getValue("CREDIT_CARD")}")
        assertEquals(BillStatus.TRACKED, bills(BillStatus.TRACKED).single().status)
    }

    @Test
    fun aSkippedBillDoesNotComeBackWhenTheSameReminderArrivesAgain() {
        ingest(electricity); val id = bills(BillStatus.FOUND).single().id
        openBills(); tap("bill_skip_$id"); gone("bill_$id")
        assertEquals(CaptureEventOutcome.DUPLICATE, ingest(electricity, at = System.currentTimeMillis() + 5_000), "the same bill reminded later")
        assertEquals(emptyList(), bills(BillStatus.FOUND))
        assertEquals(CaptureEventOutcome.BILL, ingest(electricity.replace("1,842.50", "1,900.00"), at = System.currentTimeMillis() + 6_000), "a revised amount is a new bill")
    }

    @Test
    fun aCardPaymentTextAddedFromReviewSettlesTheTrackedCardBill() {
        ingest(cardText()); val id = bills(BillStatus.FOUND).single().id
        runBlocking { container.write { it.bills.track(id) } }
        ingest("Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card ending 0862. Thank you.", at = System.currentTimeMillis() + 1_000)
        launch(); tap("more_menu"); tap("open_inbox"); waitFor("inbox_list")
        val inboxId = runBlocking { container.write { it.inbox.list().single().id } }!!
        tap("inbox_add_$inboxId"); compose.waitUntil(10_000) { has("inbox_empty") }
        compose.waitUntil(10_000) { bills(BillStatus.PAID).size == 1 }
        assertEquals(entries().single().id, bills(BillStatus.PAID).single().entryId)
    }

    @Test
    fun aPaymentOfADifferentAmountDoesNotSettleTheBill() {
        ingest(cardText()); val id = bills(BillStatus.FOUND).single().id
        runBlocking { container.write { it.bills.track(id) } }
        ingest("Payment of Rs 200.00 received towards your HDFC Bank Credit Card ending 0862. Thank you.", at = System.currentTimeMillis() + 1_000)
        launch(); tap("more_menu"); tap("open_inbox"); waitFor("inbox_list")
        val inboxId = runBlocking { container.write { it.inbox.list().single().id } }!!
        tap("inbox_add_$inboxId"); compose.waitUntil(10_000) { has("inbox_empty") }
        assertEquals(emptyList(), bills(BillStatus.PAID)); assertEquals(1, bills(BillStatus.TRACKED).size)
    }

    private fun settleFromReview(payment: String): Unit {
        ingest(payment, at = System.currentTimeMillis() + 1_000)
        launch(); tap("more_menu"); tap("open_inbox"); waitFor("inbox_list")
        val inboxId = runBlocking { container.write { it.inbox.list().single().id } }!!
        tap("inbox_add_$inboxId"); compose.waitUntil(10_000) { has("inbox_empty") }
    }

    @Test
    fun aPaymentToADifferentCardDoesNotSettleThisCardsBill() {
        ingest(cardText()); runBlocking { container.write { it.bills.track(it.bills.list(BillStatus.FOUND).single().id) } }
        settleFromReview("Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card ending 9999. Thank you.")
        assertEquals(emptyList(), bills(BillStatus.PAID)); assertEquals(1, bills(BillStatus.TRACKED).size)
    }

    @Test
    fun aPaymentLargerThanTheTotalDueDoesNotSettleTheBill() {
        ingest(cardText()); runBlocking { container.write { it.bills.track(it.bills.list(BillStatus.FOUND).single().id) } }
        settleFromReview("Payment of Rs 20,000.00 received towards your HDFC Bank Credit Card ending 0862. Thank you.")
        assertEquals(emptyList(), bills(BillStatus.PAID))
    }

    @Test
    fun anAutoDebitedEmiIsSettledByTheBankDebitForTheSameAmount() {
        ingest("Rs.5,400.00 will be debited from your A/c XX1234 on $dueText towards loan EMI.")
        runBlocking { container.write { it.bills.track(it.bills.list(BillStatus.FOUND).single().id) } }
        settleFromReview("Rs.5,400.00 debited from A/c XX1234 on ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yy"))} towards loan EMI. Avl bal Rs.50,000.00")
        assertEquals(1, bills(BillStatus.PAID).size)
    }

    @Test
    fun aPaymentLongBeforeTheDueDateDoesNotSettleTheBill() {
        val far = LocalDate.now().plusDays(60).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        ingest(cardText().replace(dueText, far)); runBlocking { container.write { it.bills.track(it.bills.list(BillStatus.FOUND).single().id) } }
        settleFromReview("Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card ending 0862. Thank you.")
        assertEquals(emptyList(), bills(BillStatus.PAID), "a payment two months early is not for this statement")
    }

    @Test
    fun aBillInAnotherCurrencyIsNotSettledByARupeePaymentOfTheSameNumber() {
        ingest(cardText()); val b = bills(BillStatus.FOUND).single()
        runBlocking { container.write { it.bills.dismiss(b.id); it.bills.add(b.copy(id = "usd", currency = "USD", status = BillStatus.TRACKED, dedupeKey = "usd")) } }
        settleFromReview("Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card ending 0862. Thank you.")
        assertEquals(emptyList(), bills(BillStatus.PAID))
    }

    @Test
    fun aDeletedPaymentUnpaysItsBillAndUndoPaysItAgainButAPaymentGoneForGoodLeavesItTracked() {
        ingest(electricity); val id = bills(BillStatus.FOUND).single().id
        runBlocking { container.write { ok ->
            ok.store.save(Expense(EntryId("pay"), System.currentTimeMillis() - 5_000, AccountId("cash"), Money(184_250, Currency.INR)), EntryDetails(merchant = "Light"))
            ok.bills.track(id); ok.bills.markPaid(id, "pay", 5)
        } }
        fun status() = runBlocking { container.write { it.bills.get(id)!!.status } }!!
        fun live() = runBlocking { container.write { it.db.schemaQueries.entryById("pay").executeAsOne().deleted == 0L } }!!
        launch(); compose.onNodeWithTag("row_Light", useUnmergedTree = true).performTouchInput { longClick() }; tap("bulk_delete")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Deleted", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(!live())
        compose.waitUntil(10_000) { status() == BillStatus.TRACKED } // whichever refresh runs first, the bill is not shown as paid by a deleted entry
        compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick()
        compose.waitUntil(10_000) { live() }
        compose.waitUntil(10_000) { status() == BillStatus.PAID } // undo brings the payment back, and with it the bill's paid state
        runBlocking { container.write { it.store.delete(EntryId("pay")) } } // deleted with nothing to undo it
        openBills(); tap("bill_pay_$id")
        assertEquals(BillStatus.TRACKED, status())
    }

    @Test
    fun aNewBillIsAnnouncedInTheShadeWithItsAmountAndTheNotificationOpensTheBillsPage() {
        runBlocking { container.write { it.notifications.setPrefs(app.expenses.core.domain.NotificationPrefs(quietEnabled = false)) } }
        ingest(cardText())
        runBlocking { NotificationWork.checkNow(context, container) }
        val deadline = System.currentTimeMillis() + 10_000
        while (shade().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val (title, text) = shade().single()
        assertEquals("Bill found: HDFC Bank credit card 0862", title); assertTrue(text.contains("₹12,345.00") && text.contains("Tap to add it"), text)
        runBlocking { NotificationWork.checkNow(context, container) }; Thread.sleep(500)
        assertEquals(1, shade().size, "told once")
        launch(MainActivity.ACTION_OPEN_BILLS); waitFor("bills_list")
    }

    @Test
    fun withHideAmountsOnTheNotificationAndTheBannerCarryNoFigure() {
        runBlocking { container.write { it.notifications.setPrefs(app.expenses.core.domain.NotificationPrefs(quietEnabled = false)); it.templates.putSetting("hide_amounts", "1") } }
        ingest(cardText())
        runBlocking { NotificationWork.checkNow(context, container) }
        val deadline = System.currentTimeMillis() + 10_000
        while (shade().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(!shade().single().second.contains("12,345"), "the notification text carries no amount: ${shade().single().second}")
        launch(); waitFor("bills_banner")
        assertTrue(!compose.onAllNodes(androidx.compose.ui.test.hasText("12,345", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the banner masks the amount")
    }

    @Test
    fun aBillWithNoDateInItsTextOffersToSetOne() {
        ingest("Your water bill of Rs 300 is due. Please pay.")
        val b = bills(BillStatus.FOUND).single(); assertEquals(null, b.dueDate)
        openBills()
        compose.onNodeWithTag("bill_due_${b.id}", useUnmergedTree = true).assertTextContains("No due date in the message", substring = true)
        waitFor("bill_setdate_${b.id}")
    }
}
