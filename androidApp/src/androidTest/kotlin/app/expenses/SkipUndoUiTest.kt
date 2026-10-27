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
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.InboxStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Skipped X · Undo" on To review and on Bills: skipping a captured item or a bill forgets its text in the database, so the screen keeps the item in memory for the
 * few seconds Undo is offered and gives it back exactly as it was. Also: with no Undo, the skip stays.
 */
@RunWith(AndroidJUnit4::class)
class SkipUndoUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private val debit = "Rs.200.00 debited from A/c XX1234 on 05-10-26 to VPA swiggy@icici (UPI Ref 100000000101). Not you? Call 18002586161"
    private val due = LocalDate.now().plusDays(12).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    private val electricity get() = "Dear Customer, your BESCOM electricity bill of Rs 1,842.50 for consumer no 1234567890 is due on $due. Pay online to avoid disconnection."

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasWords(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun tapUndo() { compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }; compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick() }

    private fun inboxItem() = runBlocking { container.write { it.inbox.list().firstOrNull() } }
    private fun inboxAll() = runBlocking { container.write { ok -> ok.inbox.list().map { it.id } } }!!
    private fun inboxStatus(id: String) = runBlocking { container.write { it.inbox.get(id)?.status } }
    private fun inboxBody(id: String) = runBlocking { container.write { it.inbox.get(id)?.body } }

    @Test
    fun skippingAnItemInToReviewOffersUndoWhichBringsItBackWithItsText() {
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", debit, 1_000) }
        val id = inboxItem()!!.id
        val original = inboxBody(id)
        assertTrue(original != null && original.contains("swiggy"))
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_back")
        tap("inbox_skip_$id")
        compose.waitUntil(10_000) { inboxStatus(id) == InboxStatus.DISMISSED }
        compose.waitUntil(10_000) { hasWords("Skipped") }
        assertNull(inboxBody(id), "a skip forgets the text in the database")
        tapUndo()
        compose.waitUntil(10_000) { inboxStatus(id) == InboxStatus.PENDING }
        assertEquals(original, inboxBody(id), "the text comes back with the item")
        waitFor("inbox_$id")
        assertEquals(listOf(id), inboxAll(), "and it is listed again")
    }

    @Test
    fun aSkipThatIsNotUndoneStays() {
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", debit, 1_000) }
        val id = inboxItem()!!.id
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_back")
        tap("inbox_skip_$id")
        compose.waitUntil(10_000) { hasWords("Skipped") }
        compose.mainClock.advanceTimeBy(11_000) // the bar times out (Long is ten seconds)
        compose.waitUntil(10_000) { !hasWords("Skipped") }
        assertEquals(InboxStatus.DISMISSED, inboxStatus(id)); assertNull(inboxBody(id))
        waitFor("inbox_empty")
    }

    @Test
    fun skippingABillOffersUndoWhichPutsItBackAsItWas() {
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", electricity, System.currentTimeMillis()) }
        val bill = runBlocking { container.write { it.bills.list(BillStatus.FOUND).single() } }!!
        launch(); compose.openMore(); tap("open_bills"); waitFor("bills_list")
        tap("bill_skip_${bill.id}")
        compose.waitUntil(10_000) { hasWords("Skipped") }
        assertEquals(BillStatus.DISMISSED, runBlocking { container.write { it.bills.get(bill.id)!!.status } })
        tapUndo()
        compose.waitUntil(10_000) { runBlocking { container.write { it.bills.get(bill.id)!!.status } } == BillStatus.FOUND }
        val back = runBlocking { container.write { it.bills.get(bill.id)!! } }!!
        assertEquals(bill.body, back.body); assertEquals(bill.amountMinor, back.amountMinor); assertEquals(bill.dueDate, back.dueDate)
        waitFor("bill_${bill.id}")
    }

    @Test
    fun removingATrackedBillAndUndoingItGoesBackToTracked() {
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", electricity, System.currentTimeMillis()) }
        val bill = runBlocking { container.write { ok -> ok.bills.list(BillStatus.FOUND).single().also { ok.bills.track(it.id) } } }!!
        launch(); compose.openMore(); tap("open_bills"); waitFor("bills_list")
        tap("bill_remove_${bill.id}")
        compose.waitUntil(10_000) { hasWords("Skipped") }
        tapUndo()
        compose.waitUntil(10_000) { runBlocking { container.write { it.bills.get(bill.id)!!.status } } == BillStatus.TRACKED }
        assertEquals(bill.body, runBlocking { container.write { it.bills.get(bill.id)!!.body } }, "a tracked bill keeps its message")
    }
}
