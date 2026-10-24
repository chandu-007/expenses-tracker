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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** Google Play's condition for the text permission: a disclosure, answered by a tap, comes before Android's own question. */
@RunWith(AndroidJUnit4::class)
class SmsDisclosureUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        runBlocking { container.startFresh() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm revoke ${context.packageName} ${Manifest.permission.RECEIVE_SMS}")).use { it.readBytes() }
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
