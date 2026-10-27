package app.expenses

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsOff
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
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Google Play's condition for the text permission: a disclosure, answered by a tap, comes before Android's own question.
 *
 * Needs RECEIVE_SMS NOT granted, and must never try to make it so: `pm revoke` of a granted runtime permission makes Android kill the app's process, which under instrumentation
 * is the test process ("Process crashed", an empty crash buffer; it ended a full suite once). So it only checks, and skips if the permission is there. The one test that grants it
 * is in ZzzSmsGrantedUiTest, which sorts last for that reason.
 */
@RunWith(AndroidJUnit4::class)
class SmsDisclosureUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        Assume.assumeFalse("RECEIVE_SMS is already granted to this install (by an earlier run, or a test that sorts before this one); revoking it would kill the test process. Uninstall the app and run again.", granted)
        runBlocking { container.startFresh() }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun tap(tag: String) { compose.waitUntil(10_000) { has(tag) }; compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }

    @Test
    fun turningOnTextReadingShowsTheDisclosureFirstAndNoThanksChangesNothing() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); compose.waitUntil(10_000) { has("add") }
        compose.openMore(); tap("open_capture")
        tap("capture_sms_switch")
        compose.waitUntil(10_000) { has("sms_disclosure") }
        tap("sms_disclosure_decline")
        compose.waitUntil(10_000) { !has("sms_disclosure") }
        compose.onNodeWithTag("capture_sms_switch", useUnmergedTree = true).assertIsOff()
        val on = runBlocking { container.write { it.templates.setting(Capture.SETTING_SMS_ON) } }
        assertTrue(on != "1", "declining leaves the setting off: $on")
    }
}
