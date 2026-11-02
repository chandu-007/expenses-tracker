package app.expenses

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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A permission or special access given (or taken back) in the phone's own settings shows on the screen that is open the moment the app returns, without leaving the screen
 * and opening it again. The activity is moved to STOPPED ("CREATED" in the scenario's words: what happens while the settings page is in front), the setting is changed from the
 * shell, and the activity is moved back to RESUMED. Nothing is tapped or navigated after that.
 *
 * Notification access is both turned on and turned off here. Runtime permissions can only be granted: `pm revoke` of a granted one kills the app's process, which under
 * instrumentation is the test process (see ZzzSmsGrantedUiTest), so the "taken back" direction of those is not driven here.
 */
@RunWith(AndroidJUnit4::class)
class PermissionResumeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val component get() = "${context.packageName}/${NotificationCaptureService::class.java.name}"
    private var scenario: ActivityScenario<MainActivity>? = null
    private var listenersBefore: String? = null

    private fun shell(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun resetApp() { runBlocking { container.startFresh() } }

    @Before fun fresh() {
        resetApp()
        listenersBefore = shell("settings get secure enabled_notification_listeners").trim().takeIf { it != "null" && it.isNotEmpty() }
        setAccess(false)
    }

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        if (listenersBefore == null) shell("settings delete secure enabled_notification_listeners") else shell("settings put secure enabled_notification_listeners $listenersBefore")
        resetApp()
    }

    private fun setAccess(on: Boolean) {
        if (on) shell("settings put secure enabled_notification_listeners $component") else shell("settings delete secure enabled_notification_listeners")
        val end = System.currentTimeMillis() + 10_000
        while (NotificationCaptureService.accessGranted(context) != on && System.currentTimeMillis() < end) Thread.sleep(100)
        assertEquals(on, NotificationCaptureService.accessGranted(context), "the setting reached the system")
    }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }

    /** What the phone's settings page does to this app: the activity stops, something changes, the activity comes back. */
    private fun awayAnd(change: () -> Unit) {
        scenario!!.moveToState(Lifecycle.State.CREATED)
        change()
        scenario!!.moveToState(Lifecycle.State.RESUMED)
    }

    @Test
    fun notificationAccessGivenAndTakenBackInSettingsShowsOnTheCaptureScreenWithoutLeavingIt() {
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_NOTIF_ON, "1"); it.templates.putSetting(Capture.SETTING_EMAIL_ON, "1") } }
        launch(); compose.openMore(); tap("open_capture"); waitFor("capture_notif_access_missing")
        compose.onNodeWithTag("capture_notif_switch", useUnmergedTree = true).assertIsOff()

        awayAnd { setAccess(true) }
        waitGone("capture_notif_access_missing"); waitGone("email_capture_access_missing")
        compose.onNodeWithTag("capture_notif_switch", useUnmergedTree = true).assertIsOn()
        compose.onNodeWithTag("email_capture_switch", useUnmergedTree = true).assertIsOn()

        awayAnd { setAccess(false) }
        waitFor("capture_notif_access_missing"); waitFor("email_capture_access_missing")
        compose.onNodeWithTag("capture_notif_switch", useUnmergedTree = true).assertIsOff()
    }

    @Test
    fun notificationAccessShowsOnThePrivacyScreenAfterReturningFromSettings() {
        launch(); compose.openMore(); tap("open_privacy"); waitFor("privacy_state_Notification access")
        assertEquals("Not allowed", textOf("privacy_state_Notification access"))
        awayAnd { setAccess(true) }
        compose.waitUntil(10_000) { textOf("privacy_state_Notification access") == "Allowed" }
        awayAnd { setAccess(false) }
        compose.waitUntil(10_000) { textOf("privacy_state_Notification access") == "Not allowed" }
    }

    @Test
    fun backgroundingWithNothingChangedLeavesTheScreenAsItWas() {
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_NOTIF_ON, "1") } }
        launch(); compose.openMore(); tap("open_capture"); waitFor("capture_notif_access_missing")
        awayAnd { }
        waitFor("capture_notif_access_missing")
        assertTrue(has("capture_notif_switch"), "still on the capture screen")
    }
}
