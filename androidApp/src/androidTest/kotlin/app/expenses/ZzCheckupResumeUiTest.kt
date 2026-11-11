package app.expenses

import android.Manifest
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The checkup against the REAL phone: the text permission granted from the shell, and notification access turned on and off in the system's setting, while the activity is stopped (what Android's settings
 * page does to the app); the open checkup shows the new state when the app returns, with no navigation. Like ZzSmsResumeUiTest it GRANTS RECEIVE_SMS, which nothing can take back inside the test process,
 * so it sorts late and skips when the permission is already there.
 */
@RunWith(AndroidJUnit4::class)
class ZzCheckupResumeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val component get() = "${context.packageName}/${NotificationCaptureService::class.java.name}"
    private var scenario: ActivityScenario<MainActivity>? = null
    private var listenersBefore: String? = null

    private fun shell(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() {
        CheckupTestHook.smsPermission = null; CheckupTestHook.notifAccess = null; CheckupTestHook.phone = null
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
        // Android 14 binds the listener only through the notification service's own command (what the Settings switch does), not from the bare setting
        if (on) { shell("settings put secure enabled_notification_listeners $component"); shell("cmd notification allow_listener $component") }
        else { shell("cmd notification disallow_listener $component"); shell("settings delete secure enabled_notification_listeners") }
        val end = System.currentTimeMillis() + 10_000
        while (NotificationCaptureService.accessGranted(context) != on && System.currentTimeMillis() < end) Thread.sleep(100)
        assertEquals(on, NotificationCaptureService.accessGranted(context), "the setting reached the system")
    }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String {
        waitFor(tag)
        // a tag on a Text is read as it is; a tag on a row or a button that merges its texts is read from the merged tree
        fun read(unmerged: Boolean) = runCatching { compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString("") { it.text } else "" } }.getOrDefault("")
        return read(true).ifEmpty { read(false) }
    }
    private fun waitText(tag: String, expected: String, ms: Long = 20_000) = compose.waitUntil(ms) { has(tag) && textOf(tag) == expected }
    private fun launchCheckup() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); tap("more_capture_checkup"); waitFor("capture_checkup_headline")
    }
    private fun awayAnd(change: () -> Unit) { scenario!!.moveToState(Lifecycle.State.CREATED); change(); scenario!!.moveToState(Lifecycle.State.RESUMED) }

    @Test
    fun notificationAccessGivenAndTakenBackInSettingsShowsOnTheOpenCheckupWithoutLeavingIt() {
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_NOTIF_ON, "1") } }
        launchCheckup()
        waitText("capture_checkup_level_listener", "Fix this"); assertEquals("Notification access is not given", textOf("capture_checkup_title_listener"))
        awayAnd { setAccess(true) }
        // the listener binds a moment after access is given: the page reads the phone again by itself until it does
        waitText("capture_checkup_level_listener", "OK")
        assertTrue(has("capture_checkup_headline"), "still on the checkup")
        awayAnd { setAccess(false) }
        waitText("capture_checkup_level_listener", "Fix this")
    }

    @Test
    fun theTextPermissionGivenInSettingsShowsOnTheOpenCheckupWhenTheAppReturns() {
        assumeTrue("the permission is already granted: this needs it absent", !granted())
        runBlocking { container.write { it.templates.putSetting(Capture.SETTING_SMS_ON, "1") } }
        launchCheckup()
        waitText("capture_checkup_level_sms", "Fix this"); assertEquals("Reading bank texts is not allowed", textOf("capture_checkup_title_sms"))
        awayAnd { shell("pm grant ${context.packageName} ${Manifest.permission.RECEIVE_SMS}") }
        waitText("capture_checkup_level_sms", "OK"); assertEquals("Reading bank texts is on", textOf("capture_checkup_title_sms"))
        assertTrue(has("capture_checkup_headline"), "no navigation happened")
    }
}
