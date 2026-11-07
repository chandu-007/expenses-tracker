package app.expenses

import android.content.Intent
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
import kotlin.test.assertTrue

/** Keys from a hardware keyboard (a tablet or a foldable with a keyboard, DeX): Ctrl+N adds, Ctrl+F searches (Activity.onKeyShortcut, so they work whatever Compose has focused). */
@RunWith(AndroidJUnit4::class)
class KeyboardShortcutsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun ctrl(keyCode: Int) {
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.sendKeySync(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON))
    }
    private fun back() { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } }

    @Test
    fun ctrlNOpensTheAddScreenAndCtrlFOpensSearch() {
        launch(); Thread.sleep(800)
        ctrl(android.view.KeyEvent.KEYCODE_N); waitFor("amount")
        back(); waitGone("amount"); waitFor("add")
        ctrl(android.view.KeyEvent.KEYCODE_F); waitFor("search_text")
    }

    @Test
    fun theShortcutsDoNothingWhereTheirScreenWouldStayHidden() {
        launch(); Thread.sleep(800)
        compose.openMore(); waitFor("open_inbox")
        compose.onNodeWithTag("open_inbox", useUnmergedTree = true).performClick(); waitFor("inbox_back")
        ctrl(android.view.KeyEvent.KEYCODE_N); Thread.sleep(1_000)
        assertTrue(!has("amount"), "no add screen opened behind To review")
        ctrl(android.view.KeyEvent.KEYCODE_F); Thread.sleep(1_000)
        assertTrue(!has("search_text"))
        compose.onNodeWithTag("inbox_back").performClick(); waitGone("inbox_back")
        assertTrue(!has("amount") && !has("search_text"), "and nothing waited to open when it was left")
    }

    @Test
    fun theShortcutsAreListedInTheSystemsKeyboardShortcutsList() {
        launch()
        val groups = mutableListOf<android.view.KeyboardShortcutGroup>()
        scenario!!.onActivity { it.onProvideKeyboardShortcuts(groups, null, 0) }
        val labels = groups.flatMap { g -> (0 until g.items.size).map { g.items[it].label.toString() } }
        assertTrue("Add an expense" in labels && "Search" in labels, "listed: $labels")
    }
}
