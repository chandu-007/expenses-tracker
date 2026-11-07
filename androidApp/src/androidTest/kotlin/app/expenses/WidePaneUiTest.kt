package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import app.expenses.ui.WideLayoutTestHook
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * The two-pane layout. The test display cannot be resized from inside a test, so [WideLayoutTestHook] stands in for the window width: the decision to use
 * two panes follows the forced width, and the panes are laid out inside the real (narrow) window, squeezed but complete. What this proves is the wiring
 * (placeholder, editor beside the list, Back, save, the narrow window unchanged); it does not prove how the panes look on a real tablet.
 */
@RunWith(AndroidJUnit4::class)
class WidePaneUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetDisplay(); WideLayoutTestHook.widthDp = null; WideLayoutTestHook.heightDp = null; runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun back() { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } }

    private val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
    /** Runs a shell command as the shell user and waits for it to finish (`wm size`, `wm density`). */
    private fun shell(cmd: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() } }
    private fun resetDisplay() { shell("wm size reset"); shell("wm density reset") }
    private fun windowDp(): Pair<Int, Int> { var r = 0 to 0; scenario!!.onActivity { val m = it.resources.displayMetrics; r = (m.widthPixels / m.density).toInt() to (m.heightPixels / m.density).toInt() }; return r }

    private fun launch(widthDp: Int?, heightDp: Int? = null) {
        WideLayoutTestHook.widthDp = widthDp; WideLayoutTestHook.heightDp = heightDp
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
    }

    private fun seed(id: String, merchant: String, minor: Long) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis(), AccountId("cash"), Money(minor, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(minor, Currency.INR)))), EntryDetails(merchant = merchant)) }
    }

    private fun left(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
    private fun right(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right

    @Test
    fun aWideWindowShowsHomeWithAPlaceholderBesideItUntilAnEntryIsOpened() {
        seed("e1", "Cafe", 10_000)
        launch(1000); waitFor("row_Cafe")
        waitFor("wide_placeholder"); waitFor("wide_list_pane"); waitFor("wide_detail_pane")
        assertTrue(has("add"), "Home is still whole on the left")
        assertTrue(!has("amount"), "no editor while nothing is open")
        assertTrue(right("wide_list_pane") <= left("wide_detail_pane") + 1f, "the list pane is left of the detail pane")
    }

    @Test
    fun openingARowShowsTheEditorBesideTheListAndCancelOrBackClearsIt() {
        seed("e1", "Cafe", 10_000)
        launch(1000); waitFor("row_Cafe")
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick()
        waitFor("amount"); waitGone("wide_placeholder")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "100.00")
        assertTrue(has("row_Cafe") && has("add"), "the list stays up while the entry is open")
        assertTrue(left("amount") >= right("row_Cafe") - 1f, "the editor is to the right of the row, not over it")

        compose.onNodeWithTag("cancel").performClick()
        waitGone("amount"); waitFor("wide_placeholder")

        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick()
        waitFor("amount")
        back() // Back closes the entry first, it does not leave the app
        waitGone("amount"); waitFor("wide_placeholder")
        assertTrue(has("row_Cafe"), "Home is still there after Back")
    }

    @Test
    fun savingFromThePaneUpdatesTheListAndEmptiesThePane() {
        seed("e1", "Cafe", 10_000)
        launch(1000); waitFor("row_Cafe")
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick()
        waitFor("amount")
        compose.onNodeWithTag("amount").performTextClearance()
        compose.onNodeWithTag("amount").performTextInput("150.5")
        compose.onNodeWithTag("save").performClick()
        waitGone("amount"); waitFor("wide_placeholder")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("month_spend")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("month_spend").assertTextEquals("₹150.50")
    }

    @Test
    fun aNarrowWindowIsUnchangedTheEditorCoversTheListAndThereIsNoPlaceholder() {
        seed("e1", "Cafe", 10_000)
        launch(500); waitFor("row_Cafe")
        assertTrue(!has("wide_placeholder") && !has("wide_detail_pane") && !has("wide_list_pane"), "no panes on a narrow window")
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick()
        waitFor("amount"); waitGone("row_Cafe")
        compose.onNodeWithTag("cancel").performClick()
        waitFor("row_Cafe"); assertTrue(!has("wide_placeholder"))
    }

    @Test
    fun otherScreensKeepTheirFullWidthOnAWideWindow() {
        launch(1000)
        compose.openMore(); waitFor("manage_accounts")
        compose.onNodeWithTag("manage_accounts", useUnmergedTree = true).performClick()
        waitFor("account_Cash")
        assertTrue(!has("wide_placeholder"), "a sub-screen is one pane, with no entry placeholder beside it")
        back(); compose.waitUntil(10_000) { !has("account_Cash") } // More stays open under the screen, so Back lands on More
    }

    @Test
    fun theMorePageTakesTheWholeWindowInsteadOfSittingBesideAnEmptyPlaceholderAndHomeComesBack() {
        seed("e1", "Cafe", 10_000)
        launch(1000); waitFor("row_Cafe"); waitFor("wide_placeholder")
        compose.openMore()
        assertTrue(!has("wide_placeholder") && !has("wide_detail_pane"), "More has nothing to put beside it, so there is no empty pane")
        assertTrue(has("wide_list_pane"), "the page keeps its place in the frame, so Home keeps its scroll position when More closes")
        compose.closeMore(); waitFor("wide_placeholder"); waitFor("row_Cafe")
        // an open entry keeps its pane while More is showing
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick(); waitFor("amount")
        compose.openMore()
        assertTrue(has("amount") && has("wide_detail_pane"), "the entry being edited stays beside More")
    }

    private fun waitWide(wide: Boolean, why: String) {
        try { compose.waitUntil(10_000) { has("wide_list_pane") == wide } } catch (e: Throwable) { throw AssertionError("expected ${if (wide) "two panes" else "one pane"}: $why", e) }
    }

    @Test
    fun theSizesOfRealDevicesDecideTheLayoutLive() {
        // one launch; the forced size changes under the running screen, as a window does when a foldable opens
        launch(344, 882); waitFor("add"); waitWide(false, "Z Fold 5 cover, 344 x 882")
        val cases = listOf(
            Triple(690, 829, true), Triple(829, 690, true), Triple(707, 823, true), Triple(750, 832, true), Triple(832, 750, true),
            Triple(344, 882, false), Triple(411, 1005, false), Triple(851, 393, false), Triple(892, 412, false), Triple(800, 1280, true), Triple(1280, 800, true),
            Triple(414, 690, false), Triple(640, 800, true), Triple(427, 800, false), Triple(1000, 450, false), Triple(600, 480, true), Triple(599, 900, false),
        )
        for ((w, h, wide) in cases) {
            WideLayoutTestHook.widthDp = w; WideLayoutTestHook.heightDp = h
            waitWide(wide, "$w x $h dp")
            if (wide) { assertTrue(has("wide_placeholder"), "the empty pane beside Home at $w x $h") }
        }
    }

    @Test
    fun theRealWindowChoosesTheLayoutAndFoldingOrUnfoldingKeepsTheOpenEntryTheTypedTextAndTheActivity() {
        seed("e1", "Cafe", 10_000)
        // the cover screen of a Galaxy Z Fold 5: 904 x 2316 px at 420 dpi = 344 x 882 dp
        shell("wm size 904x2316"); shell("wm density 420")
        launch(null); waitFor("row_Cafe"); Thread.sleep(800)
        val cover = windowDp(); assertTrue(cover.first in 340..350, "the cover is about 344 dp wide, was ${cover.first}")
        assertTrue(!has("wide_list_pane"), "the cover screen is a phone")
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick(); waitFor("amount")
        compose.onNodeWithTag("amount").performTextClearance(); compose.onNodeWithTag("amount").performTextInput("77.5")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "77.5")
        var before: MainActivity? = null; scenario!!.onActivity { before = it }
        // unfold: the inner screen, 1812 x 2176 px at 420 dpi = 690 x 829 dp
        shell("wm size 1812x2176")
        waitWide(true, "unfolded Z Fold 5, 690 x 829 dp")
        val inner = windowDp(); assertTrue(inner.first in 685..695 && inner.second in 700..835, "the inner screen is about 690 dp wide and up to 829 high (less the taskbar), was $inner")
        waitFor("amount"); waitFor("row_Cafe")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "77.5")
        var after: MainActivity? = null; scenario!!.onActivity { after = it }
        assertTrue(before === after, "the activity was not rebuilt by the unfold")
        assertTrue(left("amount") >= right("row_Cafe") - 1f, "the entry is beside the list now")
        // on its side (2176 x 1812 px), then folded again
        shell("wm size 2176x1812"); waitWide(true, "unfolded and turned, 829 x 690 dp"); compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "77.5")
        shell("wm size 904x2316")
        waitWide(false, "folded again"); waitFor("amount"); compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "77.5")
        scenario!!.onActivity { after = it }; assertTrue(before === after, "nor by the fold")
        // a phone on its side, 2340 x 1080 px at 440 dpi = 851 x 393 dp: wide but short, so still one pane
        shell("wm density 440"); shell("wm size 2340x1080"); Thread.sleep(1500); waitFor("amount")
        assertTrue(!has("wide_list_pane"), "a phone on its side is one pane")
        scenario!!.onActivity { after = it }; assertTrue(before === after, "nor by a turn")
    }

    @Test
    fun unfoldingHomeKeepsItsScrollPositionAndFoldingAgainDoesToo() {
        for (i in 1..40) seed("m$i", "Shop%02d".format(i), 1_000L + i)
        shell("wm size 904x2316"); shell("wm density 420")
        launch(null); waitFor("row_Shop40"); Thread.sleep(600)
        compose.onNodeWithTag("list").performScrollToNode(hasTestTag("row_Shop05")); waitFor("row_Shop05")
        assertTrue(!has("row_Shop40"), "scrolled well away from the top")
        shell("wm size 1812x2176"); waitWide(true, "unfolded"); Thread.sleep(600)
        assertTrue(has("row_Shop05") && !has("row_Shop40"), "still where it was after the unfold")
        shell("wm size 904x2316"); waitWide(false, "folded"); Thread.sleep(600)
        assertTrue(has("row_Shop05") && !has("row_Shop40"), "and after folding again")
    }

    @Test
    fun theKeyboardOpeningOnAShortUnfoldedWindowDoesNotTurnTwoPanesIntoOne() {
        seed("e1", "Cafe", 10_000)
        // a Z Fold 5 unfolded and on its side: 829 dp wide and about 690 high less the taskbar; the keyboard takes about 280 of it
        shell("wm size 2176x1812"); shell("wm density 420")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        launch(null); waitFor("row_Cafe"); Thread.sleep(600); waitWide(true, "unfolded on its side")
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick(); waitFor("amount")
        compose.onNodeWithTag("amount").performClick()
        var shown = false
        compose.waitUntil(10_000) { scenario!!.onActivity { shown = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }; shown }
        Thread.sleep(1_200)
        assertTrue(has("wide_list_pane") && has("wide_detail_pane") && has("amount"), "still two panes with the keyboard up")
    }

    @Test
    fun anEntryOpenedFromSearchOnAWideWindowIsShownAndBackComesBackToTheResults() {
        seed("e1", "Cafe", 10_000)
        launch(1000); waitFor("row_Cafe")
        compose.onNodeWithTag("open_search").performClick(); waitFor("search_text")
        compose.onNodeWithTag("search_text").performTextInput("caf")
        waitFor("result_e1")
        compose.onNodeWithTag("result_e1", useUnmergedTree = true).performClick()
        waitFor("amount")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "100.00")
        back(); waitGone("amount"); waitFor("result_e1")
    }

    @Test
    fun aHalfOpenFoldPutsHomeAndTheEntryEitherSideOfTheHingeAndTableTopKeepsTheAppAboveIt() {
        seed("e1", "Cafe", 10_000)
        shell("wm size 1812x2176"); shell("wm density 420") // a Z Fold 5 unfolded: 690 x 829 dp
        launch(null); waitFor("row_Cafe"); waitWide(true, "unfolded"); Thread.sleep(500)
        val d = 420 / 160f
        // like a book: the fold runs top to bottom at 300 dp and is 10 dp wide
        WideLayoutTestHook.hinge = app.expenses.ui.Hinge(300f, 0f, 310f, 829f, vertical = true)
        compose.waitUntil(10_000) { kotlin.math.abs(right("wide_list_pane") - 300f * d) < 3f }
        assertTrue(left("wide_detail_pane") >= 310f * d - 3f, "the entry pane starts after the hinge, not under it")
        // a fold that leaves a side too narrow is ignored
        WideLayoutTestHook.hinge = app.expenses.ui.Hinge(150f, 0f, 150f, 829f, vertical = true)
        compose.waitUntil(10_000) { kotlin.math.abs(right("wide_list_pane") - 340f * d) < 3f }
        // table-top: the fold runs across at 345 dp; the app is drawn above it, as a phone (a window 345 dp high)
        WideLayoutTestHook.hinge = app.expenses.ui.Hinge(0f, 345f, 690f, 345f, vertical = false)
        waitWide(false, "table-top: only 345 dp are above the fold")
        assertTrue(compose.onNodeWithTag("add").fetchSemanticsNode().boundsInRoot.bottom <= 345f * d + 1f, "nothing below the fold")
        WideLayoutTestHook.hinge = null
        waitWide(true, "flat again")
    }
}
