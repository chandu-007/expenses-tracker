package app.expenses

import android.content.Intent
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.FrameStats
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * The measurement behind the S0 frame-logger numbers (docs/PERFORMANCE.md): the six interactions of the UI plan, each repeated [REPS] times on a seeded 1,500-entry ledger, every frame of
 * each interaction taken from [FrameLog] (so this runs on the debug build only) and the totals written to the log under the tag `FRAMELOG_RESULT`, one line per interaction in
 * the same fields as the logger's. It asserts nothing about speed (an emulator draws in software: only the same device with another build is a fair comparison); it fails only when the logger saw no frames.
 *
 * Run it alone, on a cold-booted emulator, like the timing classes: `-Pandroid.testInstrumentationRunnerArguments.class=app.expenses.FrameLogDriveTest`, then `adb logcat -d -s FRAMELOG_RESULT`.
 * It is left out of the functional suites (see tools/verify.sh) because its numbers mean something only when the host is quiet.
 */
@RunWith(AndroidJUnit4::class)
class FrameLogDriveTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val results = linkedMapOf<String, FrameStats>()

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private val aRow = SemanticsMatcher("a Home row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("row_") == true }

    /** One interaction: its frames are added to [label]'s total. */
    private fun measure(label: String, action: () -> Unit) {
        compose.waitForIdle(); Thread.sleep(400)
        FrameLog.begin(label)
        action()
        compose.waitForIdle(); Thread.sleep(500) // the frame metrics of the last frames arrive a little after they were drawn
        val s = FrameLog.end()
        results[label] = (results[label] ?: FrameStats()).plus(s)
    }

    private fun flingHome(times: Int) {
        repeat(times) { node("list").performTouchInput { swipeUp(startY = bottom - 50f, endY = top + 50f, durationMillis = 120) } }
        repeat(times) { node("list").performTouchInput { swipeDown(startY = top + 50f, endY = bottom - 50f, durationMillis = 120) } }
    }

    @Test
    fun theSixInteractions() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("seed_rows", 1_500).putExtra("seed_full", true))
        waitFor("add"); compose.waitUntil(60_000) { compose.onAllNodes(aRow, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle(); Thread.sleep(1_500)
        measure("warm-up Home fling") { flingHome(3) }; results.clear()

        repeat(REPS) {
            measure("Home fling") { flingHome(5) }
            // Home to entry: a tap on the first row opens its form; the close is not part of the measurement
            measure("Home to entry") { compose.onAllNodes(aRow, useUnmergedTree = true).onFirst().performClick(); waitFor("save") }
            node("cancel").performClick(); waitFor("add"); compose.waitForIdle()
            measure("Add open") { node("add").performClick(); waitFor("amount") }
            measure("Add close") { node("cancel").performClick(); waitFor("add") }
            // the bar's tabs, one after the other and back (Search is left out here: it raises the keyboard, which is measured by its own scroll below)
            measure("tab switch: Home to Reports") { node("open_reports").performClick(); waitFor("report_list") }
            measure("Reports tab switches") {
                for (tag in listOf("mode_year", "mode_period", "group_ACCOUNT", "group_MERCHANT", "group_CATEGORY")) { node(tag).performClick(); compose.waitForIdle(); Thread.sleep(250) }
            }
            measure("tab switch: Reports to Home") { node("nav_home").performClick(); waitFor("add"); waitFor("list") }
            measure("tab switch: Home to More") { node("more_menu").performClick(); waitFor("more_page") }
            measure("tab switch: More to Home") { node("more_menu").performClick(); compose.waitUntil(10_000) { !has("more_page") } }
            // Search: type a word many of the seeded merchants contain, then scroll the results
            node("open_search").performClick(); waitFor("search_text")
            node("search_text").performTextInput("Merchant 1"); compose.waitUntil(30_000) { has("search_count") }; compose.waitForIdle(); Thread.sleep(500)
            measure("Search results scroll") {
                repeat(4) { node("search_results").performTouchInput { swipeUp(startY = bottom - 50f, endY = top + 50f, durationMillis = 120) } }
                repeat(4) { node("search_results").performTouchInput { swipeDown(startY = top + 50f, endY = bottom - 50f, durationMillis = 120) } }
            }
            node("search_back").performClick(); waitFor("add"); compose.waitForIdle()
        }
        var any = false
        results["tab switch (the four: Home, Reports, Home, More, Home)"] = results.filterKeys { it.startsWith("tab switch: ") }.values.fold(FrameStats()) { a, b -> a.plus(b) }
        for ((label, stats) in results) { Log.i("FRAMELOG_RESULT", stats.line(label)); if (stats.count > 0) any = true }
        assertTrue(any, "the frame logger saw no frames (is this the debug build?)")
    }

    private companion object { const val REPS = 5 }
}
