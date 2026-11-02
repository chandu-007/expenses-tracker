package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.CaptureEventOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The sender names the person adds by hand: chips with a remove button, a line of feedback for every kind of try, and the names still there (and still read) afterwards. */
@RunWith(AndroidJUnit4::class)
class TrustedSendersUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val debit = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"
    private fun ingest(sender: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, sender, debit, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun typed() = compose.onNodeWithTag("capture_headers", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun add(text: String) {
        waitFor("capture_headers")
        val box = compose.onNodeWithTag("capture_headers", useUnmergedTree = true)
        runCatching { box.performScrollTo() }
        box.performTextClearance(); if (text.isNotEmpty()) box.performTextInput(text)
        tap("capture_headers_save")
    }
    private fun stored() = runBlocking { container.write { it.templates.setting(Capture.SETTING_EXTRA_ROOTS) } }.orEmpty()
    private fun openCapture() { compose.openMore(); tap("open_capture"); waitFor("capture_headers") }

    @Test
    fun addingShowsAChipAndAConfirmationClearsTheBoxAndSavesTheName() {
        launch(); openCapture()
        add("mycoop")
        waitFor("sender_chip_MYCOOP"); waitFor("sender_remove_MYCOOP")
        assertEquals("Added MYCOOP", textOf("sender_added_notice"))
        assertEquals("", typed())
        assertTrue(!has("sender_input_error"))
        compose.waitUntil(10_000) { stored() == "MYCOOP" }
        add("ax-otherco-s")
        waitFor("sender_chip_OTHERCO")
        assertEquals("Added OTHERCO", textOf("sender_added_notice"))
        compose.waitUntil(10_000) { stored() == "MYCOOP\nOTHERCO" }
        compose.onNodeWithTag("sender_remove_MYCOOP", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasContentDescription("Remove MYCOOP"), useUnmergedTree = true).fetchSemanticsNodes().also { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun aDuplicateAnEmptyBoxAnInvalidNameAndABuiltInBankEachGetAShortMessageAndChangeNothing() {
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_EXTRA_ROOTS, "mycoop, otherco") } }
        launch(); openCapture()
        waitFor("sender_chip_MYCOOP"); waitFor("sender_chip_OTHERCO")
        add("MyCoop"); assertEquals("Already added", textOf("sender_input_error")); assertTrue(!has("sender_added_notice"))
        add(""); assertEquals("Type a sender name first", textOf("sender_input_error"))
        add("   "); assertEquals("Type a sender name first", textOf("sender_input_error"))
        add("my bank"); assertEquals("Use 3 to 12 letters or digits, like MYBANK", textOf("sender_input_error"))
        assertEquals("my bank", typed(), "a name that was refused stays in the box to be fixed")
        add("+919800000000"); assertEquals("Use 3 to 12 letters or digits, like MYBANK", textOf("sender_input_error"))
        add("AX-HDFCBK-S"); assertEquals("HDFC Bank is already read", textOf("sender_input_error"))
        assertEquals("mycoop, otherco", stored(), "nothing was written by a refused try")
    }

    @Test
    fun removingTakesOnlyThatNameAndALaterTextFromItIsNoLongerCaptured() {
        launch(); openCapture()
        add("mycoop"); waitFor("sender_chip_MYCOOP"); add("otherco"); waitFor("sender_chip_OTHERCO")
        compose.waitUntil(10_000) { stored() == "MYCOOP\nOTHERCO" }
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("VM-MYCOOP-S", 2_000))
        tap("sender_remove_MYCOOP")
        gone("sender_chip_MYCOOP"); waitFor("sender_chip_OTHERCO")
        compose.waitUntil(10_000) { stored() == "OTHERCO" }
        assertEquals(CaptureEventOutcome.UNLISTED_PAYMENT, ingest("VM-MYCOOP-S", 3_000), "a payment-shaped text from a name that is not on the list is skipped, and counted as such")
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("VM-OTHERCO-S", 4_000))
        assertEquals(1, runBlocking { container.write { it.inbox.list() } }!!.count { it.issuer == "MYCOOP" }, "the earlier capture is not touched by removing the name")
    }

    @Test
    fun theNamesSurviveLeavingTheScreenAndRelaunchingAndAnAddedSenderIsCaptured() {
        launch(); openCapture()
        assertEquals(CaptureEventOutcome.UNLISTED_PAYMENT, ingest("VM-MYCOOP-S", 1_000))
        add("mycoop"); waitFor("sender_chip_MYCOOP")
        compose.waitUntil(10_000) { stored() == "MYCOOP" }
        tap("capture_back"); gone("capture_headers")
        compose.openMore(); tap("open_capture"); waitFor("sender_chip_MYCOOP")
        launch(); openCapture(); waitFor("sender_chip_MYCOOP")
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("VM-MYCOOP-S", 2_000))
    }

    @Test
    fun valuesOlderVersionsWroteShowAsChipsToo() {
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_EXTRA_ROOTS, "mycoop, OtherCo\n\nmycoop") } }
        launch(); openCapture()
        waitFor("sender_chip_MYCOOP"); waitFor("sender_chip_OTHERCO")
        tap("sender_remove_OTHERCO"); gone("sender_chip_OTHERCO")
        compose.waitUntil(10_000) { stored() == "MYCOOP" }
    }

    @Test
    fun theRemoveButtonIsLabelledAndAtLeast48dpSquare() {
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_EXTRA_ROOTS, "mycoop") } }
        launch(); openCapture(); waitFor("sender_remove_MYCOOP")
        val node = compose.onNodeWithTag("sender_remove_MYCOOP", useUnmergedTree = true).fetchSemanticsNode()
        val minPx = 48 * context.resources.displayMetrics.density - 1
        assertTrue(node.size.width >= minPx && node.size.height >= minPx, "touch target ${node.size}")
        assertTrue(compose.onAllNodes(hasContentDescription("Remove MYCOOP"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
