package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Search page's Filters button, the one row of filters under the box, the Filters panel (Slice 106) and the summary line. All names and amounts are invented.
 */
@RunWith(AndroidJUnit4::class)
class SearchFiltersUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private var t = System.currentTimeMillis() - 100_000

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        shell("wm size reset"); shell("wm density reset"); shell("settings put system font_scale 1.0")
        runBlocking { container.startFresh() }
    }

    private fun shell(cmd: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand(cmd)).use { it.readBytes() }
        if (cmd.startsWith("wm ") || cmd.startsWith("settings ")) Thread.sleep(1_500) // a display or font change takes a moment to reach the windows
    }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return node(tag).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString(" ") { it.text } else "" } }
    private fun waitTextOf(tag: String, expected: String) {
        val ok = runCatching { compose.waitUntil(10_000) { has(tag) && textOf(tag) == expected } }.isSuccess
        if (!ok) throw AssertionError("$tag should read \"$expected\" but reads \"${if (has(tag)) textOf(tag) else "(absent)"}\"")
    }
    private fun describe(tag: String): String { waitFor(tag); return node(tag).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.ContentDescription)) c[SemanticsProperties.ContentDescription].joinToString(" ") else "" } }
    private fun type(tag: String, text: String) { waitFor(tag); node(tag).performTextClearance(); node(tag).performTextInput(text) }
    private fun bounds(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!

    private fun expense(id: String, merchant: String, rupees: Long, cat: String, account: String = "cash", tags: List<String>? = null) = db { ok ->
        ok.store.save(Expense(EntryId(id), t++, AccountId(account), Money(rupees * 100, Currency.INR), listOf(CategorySplit(CategoryId(cat), Money(rupees * 100, Currency.INR)))), EntryDetails(merchant = merchant, tags = tags))
    }
    private fun seed() {
        expense("a", "Swiggy", 250, "Food"); expense("b", "Zomato", 400, "Food", "bank", listOf("Work")); expense("c", "Uber", 90, "Transport"); expense("d", "Uber lunch", 70, "Food", "bank")
    }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun openSearch() { launch(); tap("open_search"); waitFor("search_text") }
    private fun openPanel() { tap("search_filters_button"); waitFor("filters_apply") }
    private fun closePanel() { tap("filters_apply"); waitGone("filters_apply") }
    private fun imeUp(): Boolean { var v = false; scenario!!.onActivity { v = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }; return v }

    @Test
    fun theFiltersButtonCountsTheFiltersThatAreOnAndSaysSoToAScreenReader() {
        seed(); openSearch()
        assertTrue(!has("search_filters_badge"), "no filter is on: no badge")
        assertEquals("Filters", describe("search_filters_button"))
        tap("search_kind_expense"); waitFor("search_filters_badge"); compose.waitUntil(10_000) { describe("search_filters_button") == "Filters, 1 active" }
        tap("search_range_THIS_PERIOD"); compose.waitUntil(10_000) { describe("search_filters_button") == "Filters, 2 active" }
        openPanel(); tap("search_category"); tap("search_cat_Food"); closePanel()
        compose.waitUntil(10_000) { describe("search_filters_button") == "Filters, 3 active" }
        // the words typed are not a filter
        type("search_text", "zo"); compose.waitUntil(10_000) { has("result_b") }
        assertEquals("Filters, 3 active", describe("search_filters_button"))
    }

    @Test
    fun anActiveFilterIsAChipUnderTheBoxThatRemovesItselfAndTheCountFollows() {
        seed(); openSearch()
        openPanel(); tap("search_category"); tap("search_cat_Food"); tap("search_account_Bank"); closePanel()
        waitFor("search_active_chip_category"); waitFor("search_active_chip_account")
        assertEquals("Remove filter: category Food", describe("search_active_chip_category")); assertEquals("Remove filter: account Bank", describe("search_active_chip_account")) // what a screen reader says
        waitFor("result_b"); waitFor("result_d"); assertTrue(!has("result_a") && !has("result_c"), "Food in the Bank account")
        tap("search_active_chip_account"); waitGone("search_active_chip_account"); waitFor("result_a") // the cash Food entry is back
        assertTrue(has("search_active_chip_category") && !has("result_c"), "only the account chip went")
        compose.waitUntil(10_000) { describe("search_filters_button") == "Filters, 1 active" }
        tap("search_active_chip_category"); waitGone("search_active_chip_category"); waitFor("search_hint") // nothing is left to search for
        assertTrue(!has("search_filters_badge"))
    }

    @Test
    fun thePanelChangesTheResultsAtOnceItsButtonSaysHowManyAndResetAndClearAllWork() {
        seed(); openSearch(); openPanel()
        waitTextOf("filters_count", "Show results") // nothing asked yet
        tap("filters_kind_expense"); waitTextOf("filters_count", "Show 4 results")
        tap("search_category"); tap("search_cat_Food"); waitTextOf("filters_count", "Show 3 results")
        tap("search_account_Bank"); waitTextOf("filters_count", "Show 2 results")
        // Reset puts the filters back as they were when the panel opened (none), Clear all takes every filter off
        tap("filters_reset"); compose.waitUntil(10_000) { describe("search_filters_button") == "Filters" }; waitFor("search_hint")
        tap("filters_kind_income"); waitTextOf("filters_count", "Show 0 results")
        tap("filters_kind_expense"); tap("search_account_Cash"); waitTextOf("filters_count", "Show 2 results")
        tap("filters_clear"); compose.waitUntil(10_000) { !has("search_filters_badge") }; waitFor("search_hint")
        tap("filters_kind_expense"); closePanel() // Apply keeps what is chosen
        waitFor("result_a"); waitFor("result_b")
        assertTrue(describe("search_filters_button") == "Filters, 1 active")
    }

    @Test
    fun aSearchIsSavedFromThePanelAndUsedFromThePanelToo() {
        seed(); openSearch()
        type("search_text", "uber"); waitFor("result_c")
        openPanel(); tap("save_search"); type("saved_name", "Rides"); tap("saved_ok"); waitFor("filters_saved_Rides")
        type("search_text", "") // the page behind it goes back to its start
        waitFor("search_hint")
        tap("filters_saved_Rides"); waitGone("filters_apply") // using one closes the panel
        waitFor("result_c"); assertTrue(!has("result_a"))
        node("search_text").assertTextContains("uber")
    }

    @Test
    fun openedFromAnAccountTheKeyboardStaysDownTheAccountIsAChipAndItsListIsThere() {
        expense("n", "CashNow", 100, "Food"); expense("b", "BankNow", 100, "Food", "bank")
        launch(); tap("balance_chip_Cash"); tap("account_search")
        waitFor("search_text"); waitFor("result_n"); waitFor("search_active_chip_account")
        Thread.sleep(2_000)
        assertTrue(!imeUp(), "the box did not take the keyboard")
        assertEquals("Remove filter: account Cash", describe("search_active_chip_account"))
        assertTrue(!has("result_b"))
        assertTrue(bounds("search_summary").top >= bounds("search_active_chips").bottom - 1f, "the summary is under the filters")
    }

    @Test
    fun theControlsAreInReadingOrderAndTheSummaryStaysWhileTheResultsScroll() {
        db { ok -> repeat(40) { i -> ok.store.save(Expense(EntryId("e$i"), t++, AccountId("cash"), Money(10_000, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(10_000, Currency.INR)))), EntryDetails(merchant = "Chai $i")) } }
        openSearch(); type("search_text", "chai"); waitFor("search_summary"); waitFor("search_count"); waitFor("result_e39")
        val field = bounds("search_text"); val button = bounds("search_filters_button"); val chips = bounds("search_active_chips"); val summary = bounds("search_summary")
        assertTrue(button.left >= field.right - 1f && kotlin.math.abs(button.center.y - field.center.y) < 8f, "the Filters button is beside the box")
        assertTrue(chips.top >= field.bottom - 1f && summary.top >= chips.bottom - 1f, "box, filters, summary from top to bottom")
        assertTrue(bounds("search_results").top >= summary.bottom - 1f, "the results are under the summary")
        node("search_results").performScrollToNode(hasTestTag("result_e5"))
        assertEquals(summary.top, bounds("search_summary").top, "the summary did not move with the list")
        assertTrue(textOf("search_count").startsWith("40 results"), textOf("search_count"))
    }

    @Test
    fun atTwoHundredPercentTextEverythingIsStillReachableAndNothingIsCutOff() {
        seed(); shell("settings put system font_scale 2.0")
        openSearch(); tap("search_kind_expense"); waitFor("result_a")
        listOf("search_text", "search_filters_button", "search_active_chips", "search_summary", "result_a").forEach { node(it).assertIsDisplayed() }
        val dm = context.resources.displayMetrics
        assertTrue(bounds("search_filters_button").height >= 48f * dm.density - 1f && bounds("search_filters_button").width >= 48f * dm.density - 1f, "the button is at least 48 dp")
        openPanel()
        listOf("filters_panel", "filters_apply", "filters_close", "filters_clear").forEach { node(it).assertIsDisplayed() }
        assertTrue(bounds("filters_apply").bottom <= bounds("filters_panel").bottom + 1f, "the Apply button is inside the panel")
        tap("search_category"); tap("search_cat_Food"); waitTextOf("filters_count", "Show 3 results")
        closePanel(); waitFor("search_active_chip_category")
    }

    @Test
    fun onATwoPaneWindowTheFiltersAreASheetOverTheListPaneOnlyNoWiderThanThePane() {
        seed(); shell("wm size 1812x2176"); shell("wm density 420") // a Z Fold 5 unfolded: 690 x 829 dp
        openSearch(); tap("search_kind_expense"); waitFor("result_a")
        waitFor("wide_list_pane"); tap("result_a"); waitFor("amount") // an entry open beside the results
        openPanel()
        val d = 420 / 160f
        val panel = bounds("filters_panel")
        assertTrue(panel.width <= 480f * d + 2f, "at most 480 dp wide, was ${panel.width / d} dp")
        assertTrue(panel.width >= 300f * d, "and not a sliver, was ${panel.width / d} dp")
        assertTrue(panel.right <= bounds("wide_list_pane").right + 1f, "it stays inside the list pane")
        assertTrue(has("amount") && bounds("amount").left >= panel.right - 1f, "the open entry is beside it, not under it")
        tap("search_category"); tap("search_cat_Food"); waitTextOf("filters_count", "Show 3 results")
        closePanel(); waitGone("filters_panel"); waitFor("search_active_chip_category"); assertTrue(has("amount"))
    }
}
