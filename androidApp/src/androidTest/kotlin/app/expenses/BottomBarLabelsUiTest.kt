package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * Slice 132: the second place of the bar is called Activity (its tag `open_search` and its key are unchanged), and from 150% text only the current place has its label (board "LargeText"): the others are
 * an icon that a screen reader still names. The Add button keeps its tag (`add` on Home, `bar_add` elsewhere) and its 48 dp at every text size, and still opens the form. Invented data (none).
 */
@RunWith(AndroidJUnit4::class)
class BottomBarLabelsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val ua = instrumentation.uiAutomation

    private fun shell(cmd: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }
        if (cmd.startsWith("wm ")) Thread.sleep(1_500) else Thread.sleep(2_000)
    }

    @Before fun fresh() {
        shell("settings put system font_scale 1.0")
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        shell("settings put system font_scale 1.0"); shell("wm size reset"); shell("wm density reset"); SystemDialogs.dismissAppErrorDialog()
        runBlocking { container.startFresh() }
    }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(20_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(20_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun density() = context.resources.displayMetrics.density

    /** What the place says to the eye (its text) and to a screen reader (text or description), from the merged tree. */
    private fun texts(tag: String): List<String> = node(tag).fetchSemanticsNode().let { n ->
        fun walk(x: androidx.compose.ui.semantics.SemanticsNode): List<String> = (x.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + x.children.flatMap { walk(it) }
        walk(n)
    }
    private fun described(tag: String): String = node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    private fun spoken(tag: String) = (texts(tag) + described(tag)).joinToString(" ").trim()

    private val places = listOf("nav_home" to "Home", "open_search" to "Activity", "open_reports" to "Reports", "more_menu" to "More")

    @Test
    fun theSecondPlaceIsCalledActivityAndKeepsItsTagAtNormalTextWithEveryLabelShown() {
        launch()
        for ((tag, name) in places) { waitFor(tag); assertEquals(listOf(name), texts(tag), "$tag has its label at normal text") }
        assertTrue(texts("add").contains("Add"), "Add keeps its word at normal text: ${texts("add")}")
       
        tap("open_reports"); waitFor("report_list"); waitFor("bar_add")
        assertEquals(listOf("Activity"), texts("open_search"), "Activity keeps its label away from Home too")
    }

    private fun largeText(scale: String) {
        shell("settings put system font_scale $scale")
        launch()
        // only the current place (Home) has its label; the others are icons that are still named for a screen reader
        assertEquals(listOf("Home"), texts("nav_home"), "the current place keeps its label at $scale")
        for ((tag, name) in places.drop(1)) { assertTrue(texts(tag).isEmpty(), "$tag has no drawn label at $scale: ${texts(tag)}"); assertEquals(name, described(tag), "$tag is still named at $scale") }
        assertTrue(texts("add").isEmpty() && described("add") == "Add", "Add is the plus alone, still named, at $scale")
        for (tag in listOf("nav_home", "open_search", "add", "open_reports", "more_menu")) {
            val b = node(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(b.height >= 47f * density() && b.width >= 47f * density(), "$tag is at least 48 dp at $scale: ${b.width / density()} x ${b.height / density()} dp")
        }
        // the label follows the current place (Reports, not Activity: Activity's box takes the keyboard, which covers the bar on a small screen at this size)
        tap("open_reports"); waitFor("report_list"); waitFor("bar_add")
        compose.waitUntil(20_000) { runCatching { texts("open_reports") == listOf("Reports") && texts("nav_home").isEmpty() && described("nav_home") == "Home" && texts("open_search").isEmpty() && described("open_search") == "Activity" && texts("bar_add").isEmpty() && described("bar_add") == "Add" }.getOrDefault(false) }
        tap("bar_add"); waitFor("save")
    }

    @Test fun at150PercentOnlyTheCurrentPlaceHasALabelAndAddStillOpensTheForm() = largeText("1.5")
    @Test fun at200PercentOnlyTheCurrentPlaceHasALabelAndAddStillOpensTheForm() = largeText("2.0")

    @Test
    fun at200PercentOn360DpTheBarStillFitsTheScreenAndEveryPlaceIsReachable() {
        shell("wm size 1080x2160"); shell("wm density 400"); shell("settings put system font_scale 2.0")
        launch()
        val width = context.resources.displayMetrics.widthPixels.toFloat()
        for (tag in listOf("nav_home", "open_search", "add", "open_reports", "more_menu")) {
            val b = node(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(b.left >= 0f && b.right <= width + 1f, "$tag is inside the screen: ${b.left}..${b.right} of $width")
        }
        tap("open_reports"); waitFor("report_list").also { assertEquals(listOf("Reports"), texts("open_reports")) }
        tap("more_menu"); waitFor("more_search")
        tap("nav_home"); waitFor("add")
    }

    @Test
    fun at100PercentAddAndTheBarTagsStillWorkFromHomeAndFromAnotherPlace() {
        launch()
        tap("add"); waitFor("save"); compose.waitForIdle()
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }; waitFor("add")
        tap("open_reports"); waitFor("bar_add"); tap("bar_add"); waitFor("save")
    }
}
