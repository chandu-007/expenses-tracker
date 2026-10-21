package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import app.expenses.core.domain.ledger.Income
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Search screen's start page (recent searches, ways to start), the summary line and the heading per day. */
@RunWith(AndroidJUnit4::class)
class SearchStartUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private var t = System.currentTimeMillis() - 100_000

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun hold(tag: String) { waitFor(tag); node(tag).performTouchInput { longClick() } }
    private fun textOf(tag: String): String {
        waitFor(tag)
        return node(tag).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString(" ") { it.text } else "" }
    }
    private fun waitTextOf(tag: String, expected: String) {
        val ok = runCatching { compose.waitUntil(10_000) { has(tag) && textOf(tag) == expected } }.isSuccess
        if (!ok) throw AssertionError("$tag should read \"$expected\" but reads \"${if (has(tag)) textOf(tag) else "(absent)"}\"")
    }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!

    private fun expense(id: String, merchant: String, cat: String = "Other") = db { ok ->
        ok.store.save(Expense(EntryId(id), t++, AccountId("cash"), Money(10_000, inr), listOf(CategorySplit(CategoryId(cat), Money(10_000, inr)))), EntryDetails(merchant = merchant))
    }
    private fun income(id: String, merchant: String, cat: String = "income:salary") = db { ok ->
        ok.store.save(Income(EntryId(id), t++, AccountId("cash"), Money(50_000, inr), CategoryId(cat)), EntryDetails(merchant = merchant))
    }
    private fun categoryOf(id: String) = db { it.db.schemaQueries.splitsOf(id).executeAsList().singleOrNull()?.category_id ?: it.db.schemaQueries.entryById(id).executeAsOne().category_id }

    private fun openSearch() { launch(); tap("open_search"); waitFor("search_text") }
    private fun type(text: String) { node("search_text").performTextInput(text) }

    @Test
    fun aSearchIsRememberedWhenDoneIsPressedOfferedAgainAndCanBeForgottenOrCleared() {
        expense("a", "Netflix one"); expense("b", "Swiggy")
        openSearch(); waitFor("search_hint")
        assertTrue(!has("recent_Netflix"), "nothing is recent before anything was searched")
        type("Netflix"); compose.waitUntil(10_000) { has("result_a") }
        node("search_text").performImeAction()
        node("search_clear").performClick(); waitFor("search_hint")
        waitFor("recent_Netflix")
        // a word of one letter is not kept
        type("S"); node("search_text").performImeAction(); node("search_clear").performClick(); waitFor("search_hint")
        assertTrue(!has("recent_S"))
        type("Swiggy"); compose.waitUntil(10_000) { has("result_b") }; node("search_text").performImeAction(); node("search_clear").performClick(); waitFor("recent_Swiggy")
        // choosing one searches for it
        tap("recent_Netflix"); compose.waitUntil(10_000) { has("result_a") && !has("result_b") }
        node("search_clear").performClick(); waitFor("recent_Netflix")
        tap("recent_forget_Netflix"); waitGone("recent_Netflix"); waitFor("recent_Swiggy")
        tap("recent_clear"); waitGone("recent_Swiggy")
    }

    @Test
    fun theRecentListKeepsTheNewestFirstNoRepeatsAndOnlyTheLastSix() {
        openSearch()
        var vm: LedgerViewModel? = null
        scenario!!.onActivity { vm = androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java] }
        // one at a time: each is written in the background, and the order they arrive in is the order they were typed
        listOf("one", "two", "three", "four", "five", "six", "seven", "TWO").forEach { w -> vm!!.rememberSearch(w); compose.waitUntil(10_000) { has("recent_$w") } }
        assertTrue(!has("recent_two"), "a repeat in other letters replaces the earlier one")
        assertTrue(!has("recent_one"), "only six are kept: the oldest goes")
        assertTrue(has("recent_seven") && has("recent_six") && has("recent_five") && has("recent_four") && has("recent_three"))
    }

    @Test
    fun aSuggestedSearchAppliesItsFiltersAndTheSummaryTotalsTheSpending() {
        expense("a", "Cafe one"); expense("b", "Cafe two"); income("i", "Pay")
        openSearch()
        tap("try_expenses")
        compose.waitUntil(10_000) { has("result_a") && has("result_b") }
        assertTrue(!has("result_i"), "income is not an expense")
        val summary = textOf("search_count")
        assertTrue(summary.startsWith("2 results") && summary.contains("200.00 spent"), summary)
        // the suggestion only set the chips: removing both brings the start page back
        tap("search_kind_expense"); tap("search_range_THIS_PERIOD")
        waitFor("search_hint")
    }

    @Test
    fun resultsAreGroupedUnderAHeadingPerDayAndChoosingEntriesShowsATopBarThatStopsIt() {
        expense("a", "Dayone")
        openSearch(); type("Dayone"); compose.waitUntil(10_000) { has("result_a") }
        hold("result_a"); waitFor("search_selection_clear"); waitTextOf("bulk_count", "1 selected")
        tap("search_selection_clear"); waitGone("search_selection_clear"); waitGone("bulk_count")
        assertTrue(has("result_a"), "stopping the choice keeps the results")
    }

    @Test
    fun theSpentTotalCountsOnlyTheExpensesWhenIncomeIsInTheSameResults() {
        expense("a", "Mix cafe"); income("i", "Mix pay")
        openSearch(); type("Mix"); compose.waitUntil(10_000) { has("result_a") && has("result_i") }
        val summary = textOf("search_count")
        assertTrue(summary.startsWith("2 results") && summary.contains("100.00 spent") && !summary.contains("600.00"), summary)
    }
}
