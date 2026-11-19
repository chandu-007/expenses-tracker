package app.expenses

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
 * UI S7: the 35 screen-level Back handlers were replaced by one that also reports the gesture's progress. Back must do exactly what it did: the system Back key (a gesture that is all at once, so no preview) closes the
 * innermost thing first, loses nothing silently and saves nothing silently, on a stateful screen, a panel, a selection, a two-pane window, the tour and the welcome. Two ways of pressing it: the activity's dispatcher (as the older
 * tests do) and the real key event through the system (`input keyevent`), which is what a person's Back button is.
 */
@RunWith(AndroidJUnit4::class)
class UiS7BackTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private var t = 1_700_000_000_000L

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("tips_seen", "reports,budget,search,categories,calendar,goals,home_layout") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) { try { compose.waitUntil(10_000) { !has(tag) } } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        val tags = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.TestTag), useUnmergedTree = true).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        throw AssertionError("$tag is still there; the screen has: ${tags.take(40)}", e)
    } }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun hold(tag: String) { waitFor(tag); node(tag).performTouchInput { longClick() } }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun shell(cmd: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes() } }
    /** Back as the Back key: through the system, which is what the button and the three-button bar send. */
    /** The system keyboard takes the first Back (it closes itself), as it always did: so a test that typed into a field presses Back until the keyboard is gone, and only then tests the screen's own Back. */
    private fun imeVisible(): Boolean {
        var v = false
        scenario!!.onActivity { a -> v = if (android.os.Build.VERSION.SDK_INT >= 30) androidx.core.view.ViewCompat.getRootWindowInsets(a.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            else (a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).isAcceptingText }
        return v
    }
    private fun hideKeyboard() {
        // Android 11 and later say whether the keyboard is showing; before that the only sign is that a field has focus, which stays true after the keyboard closes: one Back, no more
        if (android.os.Build.VERSION.SDK_INT < 30) { if (imeVisible()) backKey(); return }
        repeat(3) { if (imeVisible()) backKey() }
        assertTrue(!imeVisible(), "the keyboard is closed")
    }
    private fun backKey() { shell("input keyevent KEYCODE_BACK"); SystemClock.sleep(700) }
    /** Back as the activity's dispatcher gets it. */
    private fun backDispatch() { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }; SystemClock.sleep(300) }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.recentEntries(50).executeAsList() } }!!
    private fun expense(id: String, merchant: String, minor: Long = 10_000) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), t++, AccountId("cash"), Money(minor, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(minor, Currency.INR)))), EntryDetails(merchant = merchant)) }
    }

    @Test
    fun backFromTheAddFormWithTypedInputClosesItAndSavesNothing_byKeyAndByDispatcher() {
        for (press in listOf(::backKey, ::backDispatch)) {
            launch(); tap("add"); waitFor("amount"); if (has("number_pad")) { tap("pad_7"); tap("pad_7"); tap("pad_7") } else { node("amount").performTextInput("777"); hideKeyboard() }
            press(); gone("amount"); waitFor("add")
            assertEquals(0, entries().size, "Back from the add form saved nothing")
        }
    }

    @Test
    fun entryDetailThenEditThenCancelReturnsToTheDetailAndBackLeavesTheDetail() {
        expense("a", "Alpha")
        launch(); tap("row_Alpha"); waitFor("detail_edit")
        tap("detail_edit"); waitFor("save")
        tap("cancel"); waitFor("detail_edit")
        backKey(); gone("detail_edit"); waitFor("row_Alpha")
        assertEquals(1, entries().size)
        // and Back from the edit form itself (not Cancel) goes to the detail, not past it
        tap("row_Alpha"); waitFor("detail_edit"); tap("detail_edit"); waitFor("save")
        backKey(); waitFor("detail_edit")
        assertEquals(10_000L, entries().single().amount_minor, "an unsaved edit changed nothing")
    }

    @Test
    fun backClearsASelectionOnHomeFirstAndThenTheNextBackLeavesNothingOpen() {
        expense("a", "Alpha"); expense("b", "Bravo")
        launch(); hold("row_Alpha"); waitFor("bulk_bar")
        backKey(); gone("bulk_bar"); assertTrue(has("row_Alpha"), "Home is still there: Back cleared the selection and nothing more")
    }

    @Test
    fun backInSearchClearsASelectionFirstThenClosesAnOpenFiltersPanelThenLeavesSearch() {
        expense("a", "Alpha"); expense("b", "Bravo")
        launch(); tap("open_search"); waitFor("search_text"); tap("search_kind_expense"); hideKeyboard() // a chip, not typing; the search field has focus though, and its keyboard takes the first Back as it always did
        waitFor("search_results")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a result") { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("result_") == true }, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val first = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a result") { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("result_") == true }, useUnmergedTree = true)[0]
        first.performTouchInput { longClick() }
        waitFor("search_selection_clear"); waitFor("bulk_bar"); compose.waitForIdle(); SystemClock.sleep(500)
        backKey(); gone("search_selection_clear"); assertTrue(has("search_results"), "Back cleared the selection and stayed in Search")
        if (android.os.Build.VERSION.SDK_INT < 30) return // the keyboard of the focused search field cannot be told apart before Android 11, so the panel and the last Back are checked from Android 11 (the selection's Back above ran on both)
        tap("search_filters_button"); waitFor("filters_apply")
        backKey(); gone("filters_apply"); assertTrue(has("search_results"), "Back closed the panel and stayed in Search")
        backKey(); gone("search_results"); waitFor("add")
    }

    @Test
    fun backClosesTheMorePageThenTheScreenOpenedFromItAndEachBackIsOneStep() {
        launch(); compose.openMore(); tap("open_appearance"); waitFor("haptics_row")
        backKey(); gone("haptics_row"); assertTrue(has("more_page"), "Back from Appearance lands on More")
        backKey(); gone("more_page"); waitFor("add")
    }

    @Test
    fun theTourBackClosesTheTourWithoutRecordingItAsSeenAndTheWelcomeBackLeavesTheApp() {
        runBlocking { container.write { it.templates.putSetting("welcome_dismissed", "0"); it.templates.putSetting("guide_seen", "") } }
        compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("welcome"); tap("welcome_tour"); waitFor("guide")
        backKey(); gone("guide"); waitFor("welcome")
        // Back on the welcome (Home) is the app's own: the activity finishes, as it did before
        backKey()
        compose.waitUntil(10_000) { scenario!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
    }

    @Test
    fun theSameBackKeyWorksWithAndWithoutAPreviewFromAnyScreenOpenedFromHome() {
        launch()
        for ((open, marker) in listOf("open_reports" to "report_list")) {
            tap(open); waitFor(marker); backKey(); gone(marker); waitFor("add")
        }
        tap("add"); waitFor("amount"); backKey(); gone("amount"); waitFor("add")
    }
}
