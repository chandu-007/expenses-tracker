package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * "I expect this back": the toggle on the expense form, the Waiting to get back list on the Lent and borrowed page and the chip on Home, "Got it back" (part and
 * all, recorded as a refund of the expense), "Not expecting it any more", and Undo for each. The expense keeps counting as spending until money comes back.
 */
@RunWith(AndroidJUnit4::class)
class ExpectBackUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun waitText(tag: String, text: String) = compose.waitUntil(15_000) { has(tag) && runCatching { node(tag).assertTextContains(text, substring = true) }.isSuccess }
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun openLoans() { compose.openMore(); tap("open_loans"); waitFor("loans_list") }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!
    private fun undoBar() = compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() }

    /** An expense of ₹[rupees] on Bank that is expected back (from [from], all of it unless [expected] is given), saved the way the form saves it. Returns its id. */
    private fun seedExpected(title: String, rupees: String, from: String = "", expected: String = "", person: String = ""): String = runBlocking {
        container.write { ok ->
            EntryActions(ok).save(EntryDraft(null, DraftKind.EXPENSE, rupees, "Food", emptyList(), "bank", "", title, System.currentTimeMillis(), expectBack = true, expectFrom = from, expectPersonId = person, expectAmount = expected))
        }!!
    }

    @Test
    fun theToggleOnTheFormKeepsWhoFromAndHowMuchAndTheExpenseShowsInTheWaitingList() {
        launch()
        tap("add"); type("amount", "1200"); type("note", "Team lunch")
        tap("expect_toggle"); waitFor("expect_note")
        node("expect_note").assertTextContains("still counts as spending", substring = true) // the reports say so, in the form
        type("expect_from", "the office"); type("expect_amount", "800")
        tap("save"); waitFor("add")
        val e = entries().single()
        val x = runBlocking { container.write { it.expects.get(e.id) } }!!
        assertEquals("the office", x.from); assertEquals(80_000L, x.expectedMinor); assertEquals(false, x.closed)
        // Home: spending counts all of it, and the chip says one is waiting with the amount still to come
        node("month_spend").assertTextContains("₹1,200.00", substring = true)
        waitFor("waiting_banner"); node("waiting_banner_title").assertTextContains("1 expense waiting to come back", substring = true)
        node("waiting_banner_total").assertTextContains("₹800.00", substring = true)
        tap("waiting_banner"); waitFor("waiting_section")
        node("waiting_who_Team lunch").assertTextContains("from the office", substring = true)
        node("waiting_amount_Team lunch").assertTextContains("₹800.00", substring = true)
    }

    @Test
    fun aPersonFromTheListCanBeChosenAndTheEditFormShowsWhatWasSavedAndCanSwitchItOff() {
        runBlocking { container.write { it.people.create("Asha") } }
        val id = seedExpected("Cab", "300")
        launch()
        // edit: the toggle is on; choose Asha and save
        waitFor("add")
        scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java].openEditor(id) }
        waitFor("expect_toggle"); node("expect_toggle").assertIsSelected()
        tap("expect_person_Asha"); tap("save"); waitGone("expect_toggle")
        compose.waitUntil(10_000) { runBlocking { container.write { it.expects.get(id)?.personId } } != null }
        // the list says "from Asha"
        waitFor("waiting_banner"); tap("waiting_banner"); waitFor("waiting_section")
        node("waiting_who_Cab").assertTextContains("from Asha", substring = true)
        tap("loans_back"); compose.closeMore(); waitFor("add")
        // edit again and switch it off: it is forgotten and the chip goes
        scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java].openEditor(id) }
        waitFor("expect_toggle"); tap("expect_toggle"); tap("save"); waitGone("expect_toggle")
        compose.waitUntil(10_000) { runBlocking { container.write { it.expects.get(id) } } == null }
        waitGone("waiting_banner")
    }

    @Test
    fun aPartPaymentComesBackAsARefundShrinksWhatIsWaitingAndUndoTakesItBack() {
        val id = seedExpected("Dinner", "500", from = "Raj")
        launch(); openLoans(); waitFor("waiting_row_Dinner")
        tap("waiting_got_Dinner"); waitFor("got_dialog")
        node("got_amount").assertTextContains("500", substring = true) // starts at what is still expected
        type("got_amount", "200"); tap("got_ok"); waitGone("got_dialog")
        waitText("waiting_amount_Dinner", "₹300.00")
        node("waiting_detail_Dinner").assertTextContains("₹200.00 of ₹500.00 back so far", substring = true)
        val refund = entries().first { it.kind == "REFUND" }
        assertEquals(20_000L, refund.amount_minor); assertEquals(id, refund.refunds_entry); assertEquals("bank", refund.account_id, "into the expense's own account")
        // undo: the refund is gone and all of it is waiting again
        undoBar(); compose.onNodeWithText("Undo").performClick()
        waitText("waiting_amount_Dinner", "₹500.00")
        assertEquals(emptyList(), entries().filter { it.kind == "REFUND" })
    }

    @Test
    fun gettingTheRestBackClosesItTheSpendingGoesDownAndTheChipLeavesHome() {
        seedExpected("Hotel", "1000", from = "the office")
        launch(); waitFor("waiting_banner")
        node("month_spend").assertTextContains("₹1,000.00", substring = true)
        tap("waiting_banner"); waitFor("waiting_row_Hotel")
        tap("waiting_got_Hotel"); waitFor("got_dialog")
        tap("got_account_Cash") // it came in as cash
        tap("got_ok"); waitGone("waiting_row_Hotel")
        val refund = entries().single { it.kind == "REFUND" }
        assertEquals(100_000L, refund.amount_minor); assertEquals("cash", refund.account_id)
        tap("loans_back"); compose.closeMore(); waitFor("add"); tap("nav_home"); waitFor("month_spend")
        waitGone("waiting_banner")
        node("month_spend").assertTextContains("₹0.00", substring = true) // a refund takes it off the spending, as any refund does
    }

    @Test
    fun moreThanIsStillRefundableIsRefusedInTheDialogWhichStaysOpen() {
        seedExpected("Taxi", "400")
        launch(); openLoans(); tap("waiting_got_Taxi"); waitFor("got_dialog")
        type("got_amount", "900"); tap("got_ok"); waitFor("got_problem")
        node("got_problem").assertTextContains("At most", substring = true)
        assertEquals(emptyList(), entries().filter { it.kind == "REFUND" })
        tap("got_cancel"); waitGone("got_dialog")
    }

    @Test
    fun notExpectingItAnyMoreClosesItWithoutMoneyAndUndoBringsItBack() {
        val id = seedExpected("Gift", "700")
        launch(); openLoans(); waitFor("waiting_row_Gift")
        tap("waiting_close_Gift"); waitGone("waiting_row_Gift")
        assertEquals(true, runBlocking { container.write { it.expects.get(id)!!.closed } })
        assertEquals(emptyList(), entries().filter { it.kind == "REFUND" }, "no money was recorded")
        undoBar(); compose.onNodeWithText("Undo").performClick()
        waitFor("waiting_row_Gift")
        assertEquals(false, runBlocking { container.write { it.expects.get(id)!!.closed } })
    }

    @Test
    fun aSharedExpenseCannotBeExpectedBackAndTheToggleIsNotOfferedOnIncome() {
        runBlocking { container.write { it.people.create("Asha") } }
        launch()
        tap("add"); type("amount", "900"); waitFor("expect_toggle")
        tap("split_toggle"); tap("split_person_Asha")
        compose.waitUntil(10_000) { !has("expect_toggle") } // sharing hides it
        tap("split_toggle") // and takes it back
        waitFor("expect_toggle")
        tap("kind_INCOME"); waitGone("expect_toggle")
        tap("cancel")
    }
}
