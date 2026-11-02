package app.expenses

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The text permission granted in the phone's settings (here: from the shell, while the activity is stopped) shows on the open Capture screen when the app returns, with no
 * navigation. Like ZzzSmsGrantedUiTest it GRANTS RECEIVE_SMS, which nothing can take back inside the test process, so it sorts late (Zz, before Zzz) and skips when the
 * permission is already there (SmsDisclosureUiTest, which needs it absent, has run before both).
 */
@RunWith(AndroidJUnit4::class)
class ZzSmsResumeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    private fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun text(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }

    @Test
    fun theTextPermissionGivenInSettingsShowsOnTheOpenCaptureAndPrivacyScreensWhenTheAppReturns() {
        assumeTrue("the permission is already granted: this needs it absent", !granted())
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_SMS_ON, "1") } }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); tap("open_capture"); waitFor("capture_permission_missing")
        compose.onNodeWithTag("capture_sms_switch", useUnmergedTree = true).assertIsOff()

        scenario!!.moveToState(Lifecycle.State.CREATED)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.RECEIVE_SMS}")).use { it.readBytes() }
        scenario!!.moveToState(Lifecycle.State.RESUMED)

        compose.waitUntil(10_000) { !has("capture_permission_missing") }
        compose.onNodeWithTag("capture_sms_switch", useUnmergedTree = true).assertIsOn()
    }
}
