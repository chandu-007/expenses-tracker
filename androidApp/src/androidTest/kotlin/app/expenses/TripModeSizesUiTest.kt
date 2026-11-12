package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Trip
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertTrue

/**
 * Travel mode at 200% text size and on a fold-sized window: every screen of it is whole (nothing wider than the window, the card's text wraps instead of being cut), and every button is at least 48 dp high.
 * The display is changed through `settings` and `wm` and put back after each test. Invented data.
 */
@RunWith(AndroidJUnit4::class)
class TripModeSizesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR
    private val ua = instrumentation.uiAutomation

    private fun shell(cmd: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }
        if (cmd.startsWith("wm ")) Thread.sleep(1_500) else Thread.sleep(2_000)
    }

    @Before fun fresh() { shell("settings put system font_scale 1.0"); runBlocking { container.startFresh() } }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        shell("settings put system font_scale 1.0"); shell("wm size reset"); shell("wm density reset"); SystemDialogs.dismissAppErrorDialog()
        runBlocking { container.startFresh() }
    }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(20_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun epochDay(offset: Long) = LocalDate.now().plusDays(offset).toEpochDay()
    private fun at(offset: Long) = LocalDate.now().plusDays(offset).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun density() = context.resources.displayMetrics.density

    private fun seed() {
        db { ok ->
            ok.trips.save(Trip("goa", "Goa and Gokarna with the whole family", "Trip: Goa and Gokarna", epochDay(-1), epochDay(2), true, 250_000))
            val food = listOf(CategorySplit(CategoryId("Food"), Money(150_000, inr)))
            ok.store.save(Expense(EntryId("a"), at(0), AccountId("cash"), Money(150_000, inr), food), EntryDetails(merchant = "Beach shack", tripTag = true))
            ok.store.save(Expense(EntryId("b"), at(-1), AccountId("cash"), Money(150_000, inr), food), EntryDetails(merchant = "Seafood dinner", tripTag = true))
        }
    }

    /** The node is inside the window sideways, and (for a control) at least 48 dp high. */
    private fun fits(tag: String, control: Boolean = false) {
        waitFor(tag)
        val n = compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.fetchSemanticsNode()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val b = n.boundsInRoot
        assertTrue(b.left >= root.left - 1f && b.right <= root.right + 1f, "$tag is wider than the window: $b in $root")
        if (control) assertTrue(b.height >= 47f * density(), "$tag is ${b.height / density()} dp high")
    }

    @Test
    fun atTwiceTheTextSizeEveryTravelScreenIsWholeAndItsButtonsAreAtLeast48dp() {
        seed()
        shell("settings put system font_scale 2.0")
        launch(); waitFor("home_trip_card")
        fits("home_trip_card", control = true); fits("home_trip_line")
        compose.openMore(); fits("open_trips", control = true); tap("open_trips")
        waitFor("trips_list"); fits("trip_new", control = true); fits("trip_open_Goa and Gokarna with the whole family", control = true)
        fits("trip_edit_Goa and Gokarna with the whole family", control = true); fits("trip_delete_Goa and Gokarna with the whole family", control = true)
        tap("trip_new"); waitFor("trip_form")
        for (t in listOf("trip_name", "trip_tag", "trip_budget")) fits(t)
        for (t in listOf("trip_start", "trip_end", "trip_enabled_row", "trip_save", "trip_cancel")) fits(t, control = true)
        tap("trip_cancel"); waitFor("trips_list")
        tap("trip_open_Goa and Gokarna with the whole family"); waitFor("trip_summary")
        for (t in listOf("trip_total", "trip_days", "trip_average", "trip_budget_line", "trip_summary_note")) fits(t)
        fits("trip_see_all", control = true); fits("trip_bars"); fits("trip_bar_0", control = true)
    }

    @Test
    fun onAFoldSizedWindowHomeKeepsTheCardInItsListPaneAndTheTravelScreensFit() {
        seed()
        shell("wm size 1812x2176"); shell("wm density 420")
        launch(); waitFor("home_trip_card"); waitFor("wide_list_pane")
        val pane = compose.onNodeWithTag("wide_list_pane", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("home_trip_card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(card.left >= pane.left - 1f && card.right <= pane.right + 1f, "the card is inside the list pane: $card in $pane")
        tap("home_trip_card"); waitFor("trip_summary"); fits("trip_total"); fits("trip_see_all", control = true)
        val summary = compose.onNodeWithTag("trip_summary", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(summary.width <= 960f * density() + 2f, "the page keeps its reading width on a wide window: ${summary.width / density()} dp")
        tap("trip_summary_back"); compose.openMore(); tap("open_trips"); waitFor("trips_list"); fits("trip_new", control = true)
        tap("trip_new"); waitFor("trip_form"); fits("trip_save", control = true); fits("trip_name")
    }
}
