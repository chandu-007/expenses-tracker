package app.expenses

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertTrue

/**
 * Search between chosen days. Three entries are put on the 3rd, 10th and 20th of the current month (local noon) and the days are picked in the Material3 date
 * picker, which opens on the current month. Written without being run on a device: the picker's day cells are found by their text ("10") being a clickable node.
 */
@RunWith(AndroidJUnit4::class)
class SearchDatesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }

    private fun noonOfThisMonth(day: Int): Long = LocalDate.now().withDayOfMonth(day).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun spend(id: String, merchant: String, day: Int) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), noonOfThisMonth(day), AccountId("cash"), Money(10_000, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(10_000, Currency.INR)))), EntryDetails(merchant = merchant)) }
    }

    /** Material3 gives a day cell the whole date as its text ("Saturday, October 10, 2026"), not just its number. */
    private fun dayCell(day: Int) = SemanticsMatcher("day $day") { n ->
        n.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) &&
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().any { Regex(" $day, \\d{4}$").containsMatchIn(it.text) }
    }

    /** Opens the picker of [field] ("search_date_from" or "search_date_to") and chooses [day] of the month shown. */
    private fun pick(field: String, day: Int) {
        tap(field); waitFor("search_date_ok")
        compose.waitUntil(10_000) { compose.onAllNodes(dayCell(day)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(dayCell(day)).onFirst().performClick()
        tap("search_date_ok"); waitGone("search_date_ok")
    }

    private fun openSearch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add"); tap("open_search"); waitFor("search_text") }

    @Test
    fun pickingFromAndToLimitsTheResultsToThoseDaysWholeAndEitherEndMayBeLeftOpen() {
        spend("a", "OnThird", 3); spend("b", "OnTenth", 10); spend("c", "OnTwentieth", 20)
        openSearch()
        pick("search_date_from", 10)
        waitFor("result_b"); waitFor("result_c"); assertTrue(!has("result_a"), "from only: the 3rd is before it")
        waitFor("search_date_summary")
        pick("search_date_to", 10)
        waitGone("result_c"); assertTrue(has("result_b") && !has("result_a"), "the 10th alone: both ends are included, the whole day")
        tap("search_date_from_clear"); waitFor("result_a")
        assertTrue(has("result_b") && !has("result_c"), "to only: the 3rd and the 10th")
        tap("search_date_to_clear"); waitGone("search_date_summary")
    }

    @Test
    fun aFirstDayAfterTheLastSaysSoAndSearchesNothing() {
        spend("a", "OnThird", 3); spend("b", "OnTenth", 10); spend("c", "OnTwentieth", 20)
        openSearch()
        pick("search_date_from", 20); waitFor("result_c")
        pick("search_date_to", 10)
        waitFor("search_date_problem")
        assertTrue(!has("result_a") && !has("result_b") && !has("result_c"), "no search is run while the dates disagree")
        tap("search_date_to_clear"); waitGone("search_date_problem"); waitFor("result_c")
    }

    @Test
    fun aSavedSearchKeepsItsDates() {
        spend("a", "OnThird", 3); spend("b", "OnTenth", 10); spend("c", "OnTwentieth", 20)
        openSearch()
        pick("search_date_from", 10); pick("search_date_to", 10); waitFor("result_b")
        tap("save_search"); type("saved_name", "Tenth"); tap("saved_ok"); waitFor("saved_Tenth")
        tap("search_date_from_clear"); tap("search_date_to_clear"); waitGone("search_date_summary")
        tap("saved_Tenth")
        waitFor("search_date_summary"); waitFor("result_b"); assertTrue(!has("result_a") && !has("result_c"))
    }
}
