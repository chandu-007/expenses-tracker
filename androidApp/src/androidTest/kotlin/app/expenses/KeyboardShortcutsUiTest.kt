package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.getOrNull
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
    /**
     * A key is injected into whichever window has the focus. Right after a launch (or after the keyboard of the page before has gone) another window may still have it, and the system then refuses
     * the key ("Injecting to another application requires INJECT_EVENTS permission"; seen in a long run on an emulator, never alone): wait for this app's window to have the focus, and send the key
     * again if it is refused.
     */
    private fun ctrl(keyCode: Int) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val end = System.currentTimeMillis() + 20_000
        while (true) {
            var focused = false
            scenario!!.onActivity { focused = it.window.decorView.hasWindowFocus() }
            if (focused) {
                val sent = runCatching { inst.sendKeySync(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON)) }
                if (sent.isSuccess) return
                if (System.currentTimeMillis() > end) throw sent.exceptionOrNull()!!
            } else if (System.currentTimeMillis() > end) throw AssertionError("the app's window never had the focus")
            Thread.sleep(300)
        }
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

    private fun seed(id: String, merchant: String, minor: Long) = runBlocking {
        container.write { ok -> ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId(id), System.currentTimeMillis(), app.expenses.core.domain.ledger.AccountId("cash"), app.expenses.core.domain.Money(minor, app.expenses.core.domain.Currency.INR), listOf(app.expenses.core.domain.ledger.CategorySplit(app.expenses.core.domain.ledger.CategoryId("Food"), app.expenses.core.domain.Money(minor, app.expenses.core.domain.Currency.INR)))), app.expenses.core.storage.EntryDetails(merchant = merchant)) }
    }
    private fun focused(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> c.contains(androidx.compose.ui.semantics.SemanticsProperties.Focused) && c[androidx.compose.ui.semantics.SemanticsProperties.Focused] }
    private fun key(code: Int) { InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code) }

    @Test
    fun ctrlNInSearchLeavesTheSearchAndOpensTheAddScreenInsteadOfOpeningItBehindIt() {
        launch(); Thread.sleep(800)
        compose.onNodeWithTag("open_search").performClick(); waitFor("search_text")
        ctrl(android.view.KeyEvent.KEYCODE_N); waitFor("amount")
        assertTrue(!has("search_text"), "the add screen is shown, not waiting behind Search")
        back(); waitGone("amount"); waitFor("add")
        assertTrue(!has("search_text"), "and Search was left")
    }

    @Test
    fun ctrlFInSearchPutsTheCursorInTheBoxEvenWithAnEntryOpenBesideTheResults() {
        seed("e1", "Cafe", 10_000)
        app.expenses.ui.WideLayoutTestHook.widthDp = 1000
        try {
            launch(); waitFor("row_Cafe")
            compose.onNodeWithTag("open_search").performClick(); waitFor("search_text")
            compose.onNodeWithTag("search_text").performTextInput("caf"); waitFor("result_e1")
            compose.onNodeWithTag("result_e1", useUnmergedTree = true).performClick(); waitFor("amount")
            compose.onNodeWithTag("amount").performClick(); compose.waitUntil(10_000) { focused("amount") }
            ctrl(android.view.KeyEvent.KEYCODE_F)
            compose.waitUntil(10_000) { focused("search_text") }
            assertTrue(has("amount"), "the entry stays open beside the results")
        } finally { app.expenses.ui.WideLayoutTestHook.widthDp = null }
    }

    @Test
    fun theArrowKeysAndEnterMoveThroughTheResultsAndOpenTheEntryBesideThem() {
        seed("e1", "Cafe", 10_000); seed("e2", "Cafe Noir", 20_000)
        app.expenses.ui.WideLayoutTestHook.widthDp = 1000
        try {
            launch(); waitFor("row_Cafe Noir")
            compose.onNodeWithTag("open_search").performClick(); waitFor("search_text")
            compose.onNodeWithTag("search_text").performTextInput("caf"); waitFor("result_e1")
            compose.onNodeWithTag("search_text").performClick()
            // Tab leaves the box (its arrow keys move the cursor) and goes on through the page to the first result; Enter opens it; the arrow keys then move between the results
            val seen = StringBuilder()
            fun focusedTag(): String? = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("focused") { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Focused) && it.config[androidx.compose.ui.semantics.SemanticsProperties.Focused] }, useUnmergedTree = true)
                .fetchSemanticsNodes().firstNotNullOfOrNull { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) }
            var n = 0
            while (n++ < 12 && focusedTag()?.startsWith("result_") != true) { key(android.view.KeyEvent.KEYCODE_TAB); Thread.sleep(300); seen.append(focusedTag()).append(' ') }
            val first = focusedTag()
            assertTrue(first?.startsWith("result_") == true, "Tab reached no result; the focus went through: $seen")
            key(android.view.KeyEvent.KEYCODE_DPAD_DOWN); Thread.sleep(400)
            val second = focusedTag()
            assertTrue(second?.startsWith("result_") == true && second != first, "the arrow moved from $first to $second")
            key(android.view.KeyEvent.KEYCODE_ENTER)
            if (!runCatching { compose.waitUntil(10_000) { has("amount") } }.isSuccess) throw AssertionError("Enter did not open the entry (focus was on $second)")
        } finally { app.expenses.ui.WideLayoutTestHook.widthDp = null }
    }
}
