package app.expenses

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class HealthPrivacyUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val debit = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"
    private fun ingest(sender: String, body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }

    @Test
    fun theAppDeclaresNoInternetPermissionAndEveryPermissionItDeclaresIsDescribedOnThePrivacyScreen() {
        assertFalse(PermissionNotes.canUseInternet(context), "the privacy promise: no network permission in the built app")
        val undescribed = PermissionNotes.declared(context).filter { it !in PermissionNotes.NOTES }
        assertEquals(emptyList(), undescribed, "a permission in the manifest has no description for the privacy screen")
        launch(); tap("more_menu"); tap("open_privacy"); waitFor("privacy_internet")
        assertEquals("This app has no permission to use the internet.", textOf("privacy_internet"))
        val sms = PermissionNotes.NOTES.getValue(Manifest.permission.RECEIVE_SMS).first
        waitFor("privacy_$sms")
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        assertEquals(if (granted) "Allowed" else "Not allowed", textOf("privacy_state_$sms"), "read from Android now")
    }

    @Test
    fun theHealthScreenShowsOnlyCountsAndOffersToAddAnUnknownSenderWhoseTextsAreThenRead() {
        ingest("AX-HDFCBK-S", debit, 1_000)
        ingest("AX-HDFCBK-S", "123456 is the OTP for your transaction of Rs.1,250.00", 2_000)
        ingest("+919876543210", "hello", 3_000)
        ingest("AX-MYCOOP-S", debit, 4_000); ingest("AX-MYCOOP-S", debit, 5_000)
        launch(); tap("more_menu"); tap("open_capture"); tap("open_health"); waitFor("health_headline")
        assertEquals("Capture is off", textOf("health_headline"))
        assertEquals("1", textOf("health_count_Found as transactions")); assertEquals("1", textOf("health_count_One-time passwords ignored")); assertEquals("1", textOf("health_count_Texts from people ignored"))
        waitFor("health_sender_HDFCBK")
        assertTrue(compose.onNodeWithTag("health_sender_HDFCBK").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }.contains("HDFC Bank"), "named, from the built-in list")
        waitFor("health_add_MYCOOP")
        tap("health_add_MYCOOP")
        compose.waitUntil(10_000) { !has("health_add_MYCOOP") }
        assertEquals(CaptureEventOutcome.CAPTURED, ingest("AX-MYCOOP-S", debit, 6_000), "its texts are read from now on")
        val dump = runBlocking { container.write { ok -> ok.db.schemaQueries.captureEventCounts(0).executeAsList().joinToString { it.sender_root + it.outcome } } }!!
        assertFalse(dump.contains("1250") || dump.contains("hello") || dump.contains("9876"), "the health data holds no text")
    }
}
