package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
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

/**
 * "Senders we skipped" in More, Capture: the NAMES of senders that are not on the list of banks, with how many of their texts were skipped. Only names whose header ends in -T or -S are
 * shown (a promotional -P, a government -G, a phone number or an email never is; a name with no suffix only after three texts), with one button that trusts the name. The app looks at no
 * word of those texts, so the texts used here are plain. Every name and text is invented.
 */
@RunWith(AndroidJUnit4::class)
class SkippedSendersUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val payment = "Rs.7,418.00 debited from A/c XX5521 on 05-10-26 to PINEAPPLEMART (UPI Ref 628374659283). Not you? Call 18002586161"
    private fun ingest(sender: String, at: Long, body: String = payment) = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun stored() = runBlocking { container.write { it.templates.setting(Capture.SETTING_EXTRA_ROOTS) } }.orEmpty()
    private fun openCapture() { compose.openMore(); tap("open_capture"); waitFor("capture_headers") }
    private fun pendingIssuers() = runBlocking { container.write { it.inbox.list() } }!!.map { it.issuer }

    @Test
    fun aServiceOrTransactionalNameShowsItsCountAndOneTapTrustsItForTheNextTextWhilePromotionalGovernmentAndPhoneSendersNeverShow() {
        ingest("VM-MYCOOP-S", 1_000); ingest("VM-MYCOOP-T", 2_000); ingest("AX-ICICIX-T", 2_500)
        ingest("AX-PROMOZ-P", 3_000); ingest("DL-GOVTIN-G", 3_500); ingest("+919876543210", 4_000); ingest("AX-NOSUFF", 4_500); ingest("AX-NOSUFF", 4_600)
        launch(); openCapture()
        waitFor("skipped_sender_MYCOOP")
        assertTrue(has("skipped_senders"))
        assertEquals("MYCOOP", textOf("skipped_sender_MYCOOP")); assertEquals("2 texts skipped", textOf("skipped_count_MYCOOP")); assertEquals("1 text skipped", textOf("skipped_count_ICICIX"))
        assertTrue(textOf("skipped_last_MYCOOP").startsWith("Last one: "), textOf("skipped_last_MYCOOP"))
        assertEquals("Banks send alerts from names like ICICIT. If one of these is your bank, tap Treat as my bank; only new texts from it will be read. We never read the texts of other senders.", textOf("skipped_explain"))
        assertTrue(!has("skipped_sender_PROMOZ") && !has("skipped_sender_GOVTIN") && !has("skipped_sender_NOSUFF"), "promotional, government and unclassified (two texts) names are hidden")
        assertTrue(!has("skipped_hint_MYCOOP") && !has("skipped_shape_MYCOOP"), "no outline of any text exists")
        assertEquals("Treat MYCOOP as my bank", compose.onNodeWithTag("skipped_trust_MYCOOP").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }, "the button's own words")
        val button = compose.onNodeWithTag("skipped_trust_MYCOOP", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(button.size.height >= with(androidx.compose.ui.unit.Density(context)) { 48.dp.roundToPx() }, "at least 48 dp tall")
        tap("skipped_trust_MYCOOP")
        waitFor("sender_chip_MYCOOP"); gone("skipped_sender_MYCOOP")
        assertEquals("Added MYCOOP", textOf("skipped_added_notice")); assertTrue(has("skipped_sender_ICICIX"), "the other name stays")
        compose.waitUntil(10_000) { stored() == "MYCOOP" }
        assertEquals(emptyList(), pendingIssuers(), "the skipped texts were not kept, so nothing appears for them")
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("VM-MYCOOP-S", 5_000), "the next text from the name is read")
        assertEquals(listOf("MYCOOP"), pendingIssuers())
        assertEquals(null, runBlocking { container.write { it.templates.setting("skipped_sender:MYCOOP") } }, "its counter went with the trust")
        launch(); openCapture(); waitFor("sender_chip_MYCOOP")
        assertTrue(!has("skipped_sender_MYCOOP") && has("skipped_sender_ICICIX"))
    }

    @Test
    fun aNameWithNoSuffixAppearsOnlyAfterThreeTexts() {
        ingest("AX-NOSUFF", 1_000); ingest("AX-NOSUFF", 2_000)
        launch(); openCapture(); assertTrue(!has("skipped_senders"))
        ingest("AX-NOSUFF", 3_000)
        launch(); openCapture(); waitFor("skipped_sender_NOSUFF"); assertEquals("3 texts skipped", textOf("skipped_count_NOSUFF"))
    }

    @Test
    fun theListIsStillThereAfterARelaunchAndANameTypedByHandTakesItsRowAway() {
        ingest("VM-MYCOOP-S", 1_000)
        launch(); openCapture(); waitFor("skipped_sender_MYCOOP"); assertEquals("1 text skipped", textOf("skipped_count_MYCOOP"))
        launch(); openCapture(); waitFor("skipped_sender_MYCOOP")
        val box = compose.onNodeWithTag("capture_headers", useUnmergedTree = true)
        runCatching { box.performScrollTo() }
        box.performTextInput("mycoop")
        // Save can be lost while the keyboard moves the page: press it again until the name is a chip (saving the same name twice changes nothing)
        repeat(4) { if (!has("sender_chip_MYCOOP")) { runCatching { tap("capture_headers_save") }; compose.waitForIdle(); Thread.sleep(500) } }
        waitFor("sender_chip_MYCOOP"); gone("skipped_sender_MYCOOP")
        compose.waitUntil(10_000) { stored() == "MYCOOP" }
    }

    @Test
    fun nothingIsShownWhenOnlyPromotionalGovernmentOrPhoneSendersWereSkipped() {
        ingest("AX-PROMOZ-P", 1_000); ingest("DL-GOVTIN-G", 2_000); ingest("+919876543210", 3_000)
        launch(); openCapture()
        assertTrue(!has("skipped_senders"))
    }
}
