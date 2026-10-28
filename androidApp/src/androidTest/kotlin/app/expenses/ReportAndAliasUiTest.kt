package app.expenses

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
import kotlin.test.assertTrue

/** "Share this message for a fix" (a redacted report the person copies or mails) and the clean merchant name a starter-list alias gives a captured payment. All texts, names and numbers are invented. */
@RunWith(AndroidJUnit4::class)
class ReportAndAliasUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val missed = "Rs.2,450.00 debited from A/c XX1234 on 05-10-26 to RAVI KUMAR ravi.k@okaxis (UPI Ref 628374659283). Not you? Call 18002586161"
    private fun ingest(sender: String, body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun openHealth() { launch(); compose.openMore(); tap("open_capture"); tap("open_health"); waitFor("health_headline") }
    private fun clipboard(): String? {
        var text: String? = null
        instrumentation.runOnMainSync { text = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString() }
        return text
    }

    @Test
    fun anUnlistedSendersMessageCanBePastedSeenRedactedAndCopiedAndNothingIsStored() {
        ingest("AX-MYCOOP-S", missed, 4_000)
        openHealth()
        tap("health_report_MYCOOP"); waitFor("report_body")
        assertTrue(has("report_empty"), "nothing is shown to share before there is a message")
        compose.onNodeWithTag("report_body", useUnmergedTree = true).performTextInput(missed)
        waitFor("report_preview")
        val shown = textOf("report_preview")
        assertTrue(shown.contains("Sender: MYCOOP") && shown.contains("sender is not on the list"), shown)
        assertTrue(shown.contains("Rs.N,NNN.NN debited from A/c XX0000") && shown.contains("xxxx@okaxis") && shown.contains("NAME"), shown)
        for (secret in listOf("RAVI", "KUMAR", "ravi.k", "628374659283", "18002586161", "2,450", "1234")) assertFalse(shown.contains(secret), "'$secret' is in what would be shared: $shown")
        tap("report_keep_amounts")
        compose.waitUntil(10_000) { textOf("report_preview").contains("Rs.2,450.00") }
        assertFalse(textOf("report_preview").contains("628374659283") || textOf("report_preview").contains("RAVI"), "keeping the amounts keeps nothing else")
        tap("report_keep_amounts")
        compose.waitUntil(10_000) { !textOf("report_preview").contains("2,450") }
        tap("report_copy"); waitFor("report_note")
        assertTrue(textOf("report_note").startsWith("Copied"), textOf("report_note"))
        assertEquals(textOf("report_preview").trim(), clipboard()?.trim(), "the clipboard holds what was shown")
        tap("report_close"); compose.waitUntil(10_000) { !has("report_body") }
        val stored = runBlocking { container.write { ok -> ok.db.schemaQueries.captureEventCounts(0).executeAsList().joinToString { it.sender_root + it.outcome } } }!!
        assertFalse(stored.contains("RAVI") || stored.contains("2450") || stored.contains("6283"), "the health data still holds no text")
    }

    @Test
    fun theGeneralButtonOpensTheWindowWithoutASenderAndAPastedFromBankSenderIsReadByTheCaptureRules() {
        openHealth()
        tap("health_report_open"); waitFor("report_body")
        compose.onNodeWithTag("report_sender", useUnmergedTree = true).performTextInput("VM-HDFCBK-S")
        compose.onNodeWithTag("report_body", useUnmergedTree = true).performTextInput("Your statement is ready, see the app")
        waitFor("report_preview")
        val shown = textOf("report_preview")
        assertTrue(shown.contains("Sender: VM-HDFCBK-S") && shown.contains("from a listed sender but no amount or direction was found"), shown)
        tap("report_close")
    }

    @Test
    fun aMessageInToReviewCanBeSharedForAFixWithItsReadingAndItsDetailsRemoved() {
        ingest("AX-HDFCBK-S", missed, 5_000)
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_back")
        val id = runBlocking { container.write { it.inbox.list().single().id } }!!
        compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_text_toggle_$id"))
        tap("inbox_text_toggle_$id"); tap("inbox_report_$id"); waitFor("report_preview")
        val shown = textOf("report_preview")
        assertTrue(shown.contains("The app: added to To review as money out, read by the generic rules (low confidence)"), shown)
        assertTrue(shown.contains("Sender: HDFC Bank (an item in To review)"), shown)
        for (secret in listOf("RAVI", "ravi.k", "628374659283", "18002586161", "2,450")) assertFalse(shown.contains(secret), "'$secret' is in what would be shared: $shown")
        assertFalse(has("report_body") || has("report_sender"), "the words are the item's own, not typed")
        tap("report_close")
    }

    @Test
    fun aCapturedPaymentToAKnownMerchantAddressIsFiledUnderItsCleanNameAndTheTextIsKeptAsItWas() {
        val sms = "Rs.200.00 debited from A/c XX1234 on 05-10-26 to VPA swiggyupi@axb (UPI Ref 100000000001). Not you? Call 18002586161"
        ingest("AX-HDFCBK-S", sms, 6_000)
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_back")
        val id = runBlocking { container.write { it.inbox.list().single().id } }!!
        tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        val e = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(50).executeAsList() } }!!.single()
        assertEquals("Swiggy", e.merchant, "a clean name, not the address")
        assertEquals(sms, runBlocking { container.write { it.origins.get(e.id) } }!!.text, "the stored words are the original ones")
    }
}
