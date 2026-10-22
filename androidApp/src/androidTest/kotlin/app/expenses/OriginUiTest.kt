package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** An entry made from a text or a notification keeps the original words, and the entry editor shows them; an entry typed by hand shows no such section. Not run by the author (no device). */
@RunWith(AndroidJUnit4::class)
class OriginUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val sms = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"
    private val note = "Rs.300.00 debited from A/c XX1234 on 06-10-26 to VPA tea@okaxis (UPI Ref 628374650001). Not you? Call 18002586161"

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun entries() = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(50).executeAsList() } }!!

    /** Opens More, the inbox, adds the item with [at] as its arrival time, and comes back to the list. */
    private fun accept(at: Long) {
        val id = runBlocking { container.write { ok -> ok.inbox.list().first { it.receivedAt == at }.id } }!!
        compose.openMore(); tap("open_inbox"); waitFor("inbox_back")
        waitFor("inbox_$id"); tap("inbox_add_$id")
        compose.waitUntil(10_000) { runBlocking { container.write { it.inbox.get(id)!!.entryId } } != null }
        tap("inbox_back")
    }

    @Test
    fun anAcceptedTextAndNotificationShowTheirOriginalWordsAndAHandTypedEntryShowsNothing() {
        runBlocking {
            Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", sms, 5_000)
            Capture.ingest(container, Capture.NOTIFICATION, "PHONEPE", note, 6_000)
        }
        launch()
        accept(5_000); accept(6_000); compose.closeMore() // More stays open under the inbox, and the list is behind it
        val rows = entries().sortedBy { it.occurred_at }
        assertEquals(2, rows.size)

        val fromText = rows.first { it.merchant == "shop@okhdfc" }
        tap("row_${fromText.merchant}"); waitFor("entry_origin")
        compose.onNodeWithTag("entry_origin_title", useUnmergedTree = true).assertTextContains("Where this came from")
        compose.onNodeWithTag("entry_origin_kind", useUnmergedTree = true).assertTextContains("Text message from AX-HDFCBK-S")
        compose.onNodeWithTag("entry_origin_text", useUnmergedTree = true).assertTextContains("UPI Ref 628374659283", substring = true)
        tap("cancel"); waitFor("add")

        val fromNotification = rows.first { it.merchant == "tea@okaxis" }
        tap("row_${fromNotification.merchant}"); waitFor("entry_origin")
        compose.onNodeWithTag("entry_origin_kind", useUnmergedTree = true).assertTextContains("Notification from PhonePe")
        compose.onNodeWithTag("entry_origin_text", useUnmergedTree = true).assertTextContains("UPI Ref 628374650001", substring = true)
        tap("cancel"); waitFor("add")

        // the words are kept with the entry, in the database, after the inbox has forgotten them
        val origin = runBlocking { container.write { it.origins.get(fromText.id) } }
        assertNotNull(origin); assertEquals(sms, origin.text); assertEquals("AX-HDFCBK-S", origin.sender)

        // an entry typed by hand has no origin, and its editor has no such section
        runBlocking { container.write { ok ->
            val account = ok.db.schemaQueries.allAccounts().executeAsList().first { it.archived == 0L }.id
            ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId("typed1"), System.currentTimeMillis(), app.expenses.core.domain.ledger.AccountId(account), app.expenses.core.domain.Money(4_000, app.expenses.core.domain.BaseCurrency.current)), app.expenses.core.storage.EntryDetails(merchant = "Handtyped"))
        } }
        launch(); tap("row_Handtyped"); waitFor("save")
        assertFalse(has("entry_origin"))
        assertEquals(null, runBlocking { container.write { it.origins.get("typed1") } })
    }
}
