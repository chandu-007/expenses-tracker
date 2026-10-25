package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Faster approving in To review: swiping a card that needs no decision, and "Add all from <payee>". Written to run on a device; not run when written. */
@RunWith(AndroidJUnit4::class)
class InboxSwipeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun to(vpa: String, ref: String, card: String = "1234") = "Rs.200.00 debited from A/c XX$card on 05-10-26 to VPA $vpa (UPI Ref $ref). Not you? Call 18002586161"
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }
    private fun idAt(at: Long) = runBlocking { container.write { ok -> ok.inbox.list().first { it.receivedAt == at }.id } }!!
    private fun waiting() = runBlocking { container.write { ok -> ok.inbox.list().map { it.id } } }!!
    private fun entries() = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(50).executeAsList() } }!!
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    /** The list is lazy: a card below the fold is not composed until the list is scrolled to it. */
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) || run { runCatching { compose.onNodeWithTag("inbox_list").performScrollToNode(hasTestTag(tag)) }; has(tag) } }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back") }
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    /** The list is lazy: bring the card on screen first. */
    private fun card(id: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        waitFor("inbox_list")
        compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_$id"))
        return compose.onNodeWithTag("inbox_$id", useUnmergedTree = true)
    }

    /** The account for this bank card is known (used before): these texts need no decision. */
    private fun knowCard() = runBlocking { container.write { it.templates.putSetting("capture_acct:HDFC Bank:1234", "bank") } }

    @Test
    fun swipingAClearCardRightAddsItLikeTheAddButtonAndLeftSkipsIt() {
        knowCard(); ingest(to("swiggy@icici", "100000000101"), 1_000); ingest(to("randomshop@okaxis", "100000000102"), 2_000)
        launch(); openInbox(); waitFor("inbox_swipe_hint")
        val add = idAt(1_000); val skip = idAt(2_000)
        card(add).performTouchInput { swipeRight() }
        compose.waitUntil(10_000) { entries().size == 1 && add !in waiting() }
        assertEquals(20_000, entries().single().amount_minor); assertEquals("EXPENSE", entries().single().kind)
        card(skip).performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { skip !in waiting() }
        assertEquals(1, entries().size, "skipping adds nothing")
        compose.waitUntil(10_000) { has("inbox_empty") }
    }

    @Test
    fun aCardThatNeedsADecisionDoesNotSwipe() {
        // the account for this card (XX9999) is not known, so the card asks where it goes
        knowCard(); ingest(to("shop@okhdfc", "100000000103", card = "9999"), 1_000)
        launch(); openInbox()
        val id = idAt(1_000)
        card(id).performTouchInput { swipeRight() }
        compose.waitForIdle(); Thread.sleep(700)
        assertTrue(id in waiting() && entries().isEmpty(), "swiping right did nothing")
        card(id).performTouchInput { swipeLeft() }
        compose.waitForIdle(); Thread.sleep(700)
        assertTrue(id in waiting(), "swiping left did nothing")
        assertTrue(!has("inbox_swipe_hint"), "no card to swipe, no hint")
    }

    @Test
    fun addAllFromAPayeeAddsTheClearOnesFromThatPayeeOnly() {
        knowCard()
        ingest(to("swiggy@icici", "100000000111"), 1_000); ingest(to("swiggy@icici", "100000000112"), 2_000); ingest(to("randomshop@okaxis", "100000000113"), 3_000)
        launch(); openInbox()
        val a = idAt(1_000); val b = idAt(2_000); val other = idAt(3_000)
        waitFor("inbox_add_payee_$a"); waitFor("inbox_add_payee_$b")
        assertTrue(!has("inbox_add_payee_$other"), "a payee with one item has no button")
        tap("inbox_add_payee_$a")
        compose.waitUntil(10_000) { entries().size == 2 && waiting() == listOf(other) }
        assertTrue(entries().all { it.merchant?.contains("swiggy", ignoreCase = true) == true }, "only the payee's items were added")
    }

    @Test
    fun addAllFromAPayeeLeavesOutItemsThatNeedALook() {
        knowCard()
        ingest(to("swiggy@icici", "100000000121"), 1_000); ingest(to("swiggy@icici", "100000000122"), 2_000)
        // a third swiggy text from a card whose account is not known: it needs a look
        ingest(to("swiggy@icici", "100000000123", card = "9999"), 3_000)
        launch(); openInbox()
        val a = idAt(1_000); val unknown = idAt(3_000)
        tap("inbox_add_payee_$unknown") // also offered on the card that itself needs a look, and adds the clear ones
        compose.waitUntil(10_000) { entries().size == 2 }
        assertEquals(listOf(unknown), waiting())
        assertTrue(a !in waiting())
    }
}
