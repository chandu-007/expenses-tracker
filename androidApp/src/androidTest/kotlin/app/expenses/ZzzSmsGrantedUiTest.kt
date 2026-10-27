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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The text-capture switch once Android has been given the RECEIVE_SMS permission. It sorts LAST (the name: Zzz, like AaaFirstLaunchTest sorts first) because it GRANTS that permission
 * and nothing can take it back inside the test process: `pm revoke` of a granted runtime permission makes Android kill the app's process, which under instrumentation is the test
 * process ("Process crashed", crash buffer empty; found when SmsDisclosureUiTest, which needs the permission NOT granted, ran after the old home of this test in InboxUiTest).
 * So: tests that need "not granted" (SmsDisclosureUiTest) run before this and skip, never revoke, if the permission is already there; this test is the only one that grants.
 * Do not add a test that needs the permission absent to a class that sorts after this one.
 */
@RunWith(AndroidJUnit4::class)
class ZzzSmsGrantedUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() } // scrolls only where the node sits in a scrolling list

    @Test
    fun smsCaptureShowsTheMissingPermissionAndOnceGrantedTheSwitchTurnsItOnAndOff() {
        val granted = { androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        val on = { runBlocking { container.write { it.templates.setting(Capture.SETTING_SMS_ON) } } }
        if (!granted()) {
            // switched on in the settings but Android never allowed it (or the person took it back): the screen says so and the switch is off
            runBlocking { container.write { it.templates.putSetting(Capture.SETTING_SMS_ON, "1") } }
            launch(); compose.openMore(); tap("open_capture"); waitFor("capture_permission_missing")
            compose.onNodeWithTag("capture_sms_switch", useUnmergedTree = true).assertIsOff()
            runBlocking { container.write { it.templates.putSetting(Capture.SETTING_SMS_ON, "0") } }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.RECEIVE_SMS}")).use { it.readBytes() } // grantRuntimePermission needs API 28
        }
        launch(); compose.openMore(); tap("open_capture"); tap("capture_sms_switch")
        compose.waitUntil(10_000) { on() == "1" }
        compose.onNodeWithTag("capture_sms_switch", useUnmergedTree = true).assertIsOn()
        tap("capture_sms_switch"); compose.waitUntil(10_000) { on() == "0" }
    }
}
