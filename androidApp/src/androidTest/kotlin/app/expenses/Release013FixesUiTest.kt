package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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
import app.expenses.ui.WideLayoutTestHook
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the 0.13.0 review found, one test each (invented data): a tab or Add tapped in a Search that a trip's "See all entries" opened leaves the trip's page; on a wide window the Add button and
 * a bulk change do not leave an old entry's form on the screen; Back closes the Filters panel before the entry beside it; coming back to the results from an entry does not blank them; changing a trip's
 * tag offers to tag the entries again; the row buttons of a trip name it.
 */
@RunWith(AndroidJUnit4::class)
class Release013FixesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val shot = FailureShot()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR
    private val zone = ZoneId.systemDefault()

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); WideLayoutTestHook.widthDp = null; WideLayoutTestHook.heightDp = null; runBlocking { container.startFresh() } }

    private fun launch(widthDp: Int? = null) {
        WideLayoutTestHook.widthDp = widthDp
        compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
    }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = FailureShot.around("Fixes_waitFor_$tag") { compose.waitUntil(15_000) { has(tag) } }
    private fun waitGone(tag: String) = FailureShot.around("Fixes_waitGone_$tag") { compose.waitUntil(15_000) { !has(tag) } }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun back() { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
    private fun editable(tag: String): String = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c ->
        c.getOrNull(SemanticsProperties.EditableText)?.text ?: c.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
    }
    private fun textOf(tag: String) = editable(tag)
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun epochDay(offset: Long) = LocalDate.now().plusDays(offset).toEpochDay()
    private fun at(offset: Long) = LocalDate.now().plusDays(offset).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun trip(name: String, from: Long, to: Long) = db { ok -> ok.trips.save(Trip(name.lowercase(), name, "Trip: $name", epochDay(from), epochDay(to), true, null)) }
    private fun spend(id: String, merchant: String, at: Long, rupees: Long = 100, tripTag: Boolean = false) = db { ok ->
        ok.store.save(Expense(EntryId(id), at, AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId("Food"), Money(rupees * 100, inr)))), EntryDetails(merchant = merchant, tripTag = tripTag))
    }
    private fun seedCafes() { spend("e1", "Cafe", System.currentTimeMillis(), 100); spend("e2", "Cafe Noir", System.currentTimeMillis(), 250); spend("e3", "Dosa", System.currentTimeMillis(), 40); spend("e4", "Cafe Roma", System.currentTimeMillis(), 120) }
    private fun openSearchAndType(q: String) {
        tap("open_search"); waitFor("search_text")
        compose.onNodeWithTag("search_text").performTextInput(q); waitFor("result_e1")
    }
    private fun categoryOf(id: String) = db { it.db.schemaQueries.splitsOf(id).executeAsList().singleOrNull()?.category_id ?: it.db.schemaQueries.entryById(id).executeAsOne().category_id }

    // ---- the trip's page and Search ----

    @Test
    fun aTabOrAddTappedInASearchOpenedFromATripLeavesTheTripPage() {
        trip("Goa", -2, 2); spend("in1", "Tagged lunch", at(0), 100, tripTag = true)
        launch(); compose.openMore(); tap("open_trips"); waitFor("trips_list"); tap("trip_open_Goa"); waitFor("trip_see_all"); tap("trip_see_all"); waitFor("search_text")
        tap("nav_home"); waitFor("add"); assertFalse(has("trip_summary"), "Home is Home, not the trip's summary again")

        compose.openMore(); tap("open_trips"); waitFor("trips_list"); tap("trip_open_Goa"); waitFor("trip_see_all"); tap("trip_see_all"); waitFor("search_text")
        tap("bar_add"); waitFor("amount"); assertEquals("", editable("amount"), "the add form, empty")
        tap("cancel"); waitFor("add"); assertFalse(has("trip_summary") || has("trips_list"), "back on Home after the add form")
    }

    @Test
    fun theRowButtonsOfATripNameIt() {
        trip("Goa", -2, 2)
        launch(); compose.openMore(); tap("open_trips"); waitFor("trip_edit_Goa")
        fun said(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertEquals(listOf("Edit Goa"), said("trip_edit_Goa")); assertEquals(listOf("Delete Goa"), said("trip_delete_Goa"))
    }

    @Test
    fun changingOnlyATripsTagOffersToTagTheEntriesUnderTheNewTag() {
        trip("Goa", -2, 2); spend("in1", "Tagged lunch", at(0), 100, tripTag = true); spend("in2", "Tagged boat", at(-1), 200, tripTag = true)
        launch(); compose.openMore(); tap("open_trips"); waitFor("trip_edit_Goa"); tap("trip_edit_Goa"); waitFor("trip_form")
        compose.onNodeWithTag("trip_tag", useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag("trip_tag", useUnmergedTree = true).performTextInput("Goa 2026")
        tap("trip_save"); waitFor("trip_offer_title")
        assertTrue(textOf("trip_offer_title").startsWith("Tag 2 earlier entries"), textOf("trip_offer_title"))
        tap("trip_offer_yes")
        compose.waitUntil(15_000) { db { ok -> ok.store.tagsOf(EntryId("in1")) }.any { it.equals("Goa 2026", true) } && db { ok -> ok.store.tagsOf(EntryId("in2")) }.any { it.equals("Goa 2026", true) } }
    }

    // ---- two panes ----

    @Test
    fun onAWideWindowAddFromTheBarOrHomeOpensTheAddFormNotTheOldEntry() {
        seedCafes()
        launch(1000); openSearchAndType("caf")
        tap("result_e1"); waitFor("amount"); compose.waitUntil(10_000) { editable("amount") == "100.00" }
        compose.onNodeWithTag("search_text").performImeAction() // the bar goes while the keyboard is up
        tap("bar_add"); compose.waitUntil(10_000) { has("amount") && editable("amount").isEmpty() }
        tap("cancel"); waitFor("add")
        // Home: the entry beside the list, then Add
        tap("row_Cafe"); compose.waitUntil(10_000) { has("amount") && editable("amount") == "100.00" }
        tap("add"); compose.waitUntil(10_000) { has("amount") && editable("amount").isEmpty() }
    }

    @Test
    fun onAWideWindowABulkChangeToTheOpenEntryClosesItsFormSoItsSaveCannotUndoTheChange() {
        seedCafes()
        launch(1000); openSearchAndType("caf")
        tap("result_e1"); waitFor("amount"); compose.waitUntil(10_000) { editable("amount") == "100.00" }
        compose.onNodeWithTag("result_e2", useUnmergedTree = true).performTouchInput { longClick() }; waitFor("search_selection_clear")
        tap("result_e1")
        tap("bulk_category"); tap("bulk_category_option_Bills")
        waitGone("amount"); waitFor("wide_placeholder")
        compose.waitUntil(10_000) { categoryOf("e1") == "Bills" }
    }

    @Test
    fun onAWideWindowBackClosesTheFiltersPanelBeforeTheEntryBesideIt() {
        seedCafes()
        launch(1000); openSearchAndType("caf")
        tap("result_e1"); waitFor("amount"); compose.waitUntil(10_000) { editable("amount") == "100.00" }
        tap("search_filters_button"); waitFor("filters_apply")
        back(); waitGone("filters_apply")
        assertTrue(has("amount") && editable("amount") == "100.00", "the first Back closed only the Filters panel; the entry is still open")
        back(); waitGone("amount"); waitFor("wide_placeholder")
    }

    // ---- one pane ----

    @Test
    fun comingBackToTheResultsFromAnEntryKeepsTheWordsAndWhereTheListWasScrolledTo() {
        for (i in 1..30) spend("m$i", "Cab %02d".format(i), System.currentTimeMillis() - (31 - i) * 60_000L, 10)
        launch(); tap("open_search"); waitFor("search_text")
        compose.onNodeWithTag("search_text").performTextInput("cab"); waitFor("result_m30")
        compose.onNodeWithTag("search_results").performScrollToNode(hasTestTag("result_m5")); waitFor("result_m5")
        tap("result_m5"); waitFor("amount"); waitGone("result_m5")
        tap("cancel"); waitFor("search_text")
        // the page was taken down and put back: the words are in the box, and the list is where it was (the newest rows are not on the screen) instead of blanked by a second search and put at the top
        compose.waitUntil(10_000) { has("result_m5") }
        assertTrue(has("result_m5") && !has("result_m30"), "the list is still scrolled down to m5")
        assertEquals("cab", editable("search_text"))
    }
}
