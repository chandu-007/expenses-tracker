package app.expenses

import android.Manifest
import android.content.Intent
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
 * The notification permission (Android 13 and later) given in the phone's settings shows on the open Notifications screen when the app returns: the "turned off" notice goes without
 * leaving the screen. It GRANTS POST_NOTIFICATIONS, which nothing can take back inside the test process (`pm revoke` kills it), so it sorts FIRST (Aaa, like AaaFirstLaunchTest, before
 * NotificationsTest and BillsUiTest grant it for their own needs) and skips when the permission is already there.
 */
@RunWith(AndroidJUnit4::class)
class AaaNotifPermissionResumeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    private fun granted() = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }

    @Test
    fun theNotificationPermissionGivenInSettingsRemovesTheTurnedOffNoticeWhenTheAppReturns() {
        assumeTrue("needs Android 13+ and the permission not yet granted", android.os.Build.VERSION.SDK_INT >= 33 && !granted())
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); tap("open_notifications"); waitFor("notif_blocked")

        scenario!!.moveToState(Lifecycle.State.CREATED)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        scenario!!.moveToState(Lifecycle.State.RESUMED)

        compose.waitUntil(10_000) { !has("notif_blocked") }
        waitFor("notif_bills")
    }
}
