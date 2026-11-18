package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Tips
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UI slice S6: the one-time tips: shown once at the top of their screen, gone after "Got it" or after the screen was left, brought back from Appearance, never over the welcome or the tour. */
@RunWith(AndroidJUnit4::class)
class UiS6TipsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); shell("settings put system font_scale 1.0"); runBlocking { container.startFresh() } }

    private fun shell(cmd: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes() }; Thread.sleep(1_500) }
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun put(key: String, value: String) { runBlocking { container.write { it.templates.putSetting(key, value) } } }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(20_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(20_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun unseenAll() = put(Tips.SETTING, "")
    private fun openReports() { tap("open_reports"); waitFor("report_list") }
    private fun backFromReports() { tap("reports_back"); waitFor("add") }

    @Test
    fun theReportsTipShowsOnceAndGotItRemovesItForGood() {
        unseenAll(); launch(); openReports()
        waitFor("tip_reports"); assertTrue(has("tip_gotit_reports"))
        tap("tip_gotit_reports"); gone("tip_reports")
        compose.waitUntil(10_000) { setting(Tips.SETTING)?.split(',')?.contains("reports") == true }
        backFromReports(); openReports()
        compose.waitForIdle(); assertFalse(has("tip_reports"), "after Got it the tip does not come back")
        launch(); openReports(); compose.waitForIdle(); assertFalse(has("tip_reports"), "nor after reopening the app")
    }

    @Test
    fun aTipNotDismissedIsStillSeenOnceItsScreenWasLeft() {
        unseenAll(); launch(); openReports(); waitFor("tip_reports")
        backFromReports(); openReports()
        compose.waitForIdle(); assertFalse(has("tip_reports"), "leaving the screen counted as seen")
    }

    @Test
    fun startingFreshMarksEveryTipSeen() {
        unseenAll()
        runBlocking { container.startFresh() }
        assertEquals(Tips.IDS, Tips.decode(setting(Tips.SETTING)))
        launch(); openReports(); compose.waitForIdle(); assertFalse(has("tip_reports"))
    }

    @Test
    fun showTipsAgainBringsThemBack() {
        launch(); openReports(); compose.waitForIdle(); assertFalse(has("tip_reports"))
        backFromReports()
        compose.openMore(); tap("open_appearance"); tap("tips_reset"); waitFor("tips_reset_done")
        compose.waitUntil(10_000) { setting(Tips.SETTING).isNullOrEmpty() }
        tap("appearance_back"); waitFor("add")
        openReports(); waitFor("tip_reports")
    }

    @Test
    fun noTipIsShownOverTheWelcome() {
        put("welcome_dismissed", "0"); unseenAll(); launch(); waitFor("welcome")
        openReports(); compose.waitForIdle()
        assertFalse(has("tip_reports"), "the welcome is up: no tip")
    }

    @Test
    fun noTipIsShownWhileTheTourIsShowing() {
        put("guide_seen", "0"); unseenAll(); launch(); waitFor("guide")
        compose.waitForIdle()
        for (id in Tips.IDS) assertFalse(has("tip_$id"), "tip $id during the tour")
        while (has("guide_next")) { tap("guide_next"); compose.waitForIdle(); for (id in Tips.IDS) assertFalse(has("tip_$id"), "tip $id during the tour") }
    }

    @Test
    fun atTwoHundredPercentTheScreensOwnFirstControlIsStillReachableAndTheTipFitsTheWidth() {
        unseenAll(); shell("settings put system font_scale 2.0"); launch(); openReports()
        waitFor("tip_reports"); node("tip_reports").assertIsDisplayed(); node("tip_gotit_reports").assertIsDisplayed()
        waitFor("mode_period"); node("mode_period").performScrollTo(); node("mode_period").assertIsDisplayed()
        val card = node("tip_reports").fetchSemanticsNode().boundsInRoot
        val button = node("tip_gotit_reports").fetchSemanticsNode().boundsInRoot
        assertTrue(card.left >= 0f && card.right <= node("report_list").fetchSemanticsNode().boundsInRoot.right + 1f, "the card stays inside the screen")
        assertTrue(button.left >= card.left - 1f && button.right <= card.right + 1f, "the button stays inside the card")
    }
}
