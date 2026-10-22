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

/** From search results: Select all (or touch and hold) and set one category for every selected entry, with the category's kind respected. */
@RunWith(AndroidJUnit4::class)
class SearchBulkCategoryUiTest {
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
    private fun hold(tag: String) { waitFor(tag); compose.waitForIdle(); node(tag).performTouchInput { longClick() } } // idle first: a long press while the list is still settling (keyboard going, rows animating) is not always taken
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

    private fun search(text: String, vararg shown: String) {
        launch(); tap("open_search"); waitFor("search_text")
        node("search_text").performTextInput(text)
        compose.waitUntil(10_000) { has("search_count") }
        // the way a person looks at a long list of results: the keyboard's Back key closes it without leaving the screen
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close(); Thread.sleep(700)
        shown.forEach { waitFor("result_$it") }
    }

    private fun fourNetflixAndTwoSalaries() {
        (1..4).forEach { expense("n$it", "Netflix") }
        income("s1", "Salary Acme"); income("s2", "Salary Beta")
    }

    @Test
    fun selectAllSelectsEveryResultAndOneCategoryChangesAllFourAndUndoRestores() {
        fourNetflixAndTwoSalaries()
        search("Netflix", "n1", "n2", "n3", "n4")
        waitFor("search_select_all")
        compose.onNodeWithTag("search_select_all").assertTextEquals("Select all 4")
        tap("search_select_all")
        waitTextOf("bulk_count", "4 selected")
        compose.onNodeWithTag("search_select_all").assertTextEquals("Clear selection")
        tap("bulk_category"); tap("bulk_category_option_Bills")
        waitFor("search_notice"); compose.waitUntil(10_000) { textOf("search_notice").contains("Changed the category of 4 entries") }
        waitGone("bulk_bar")
        assertEquals(List(4) { "Bills" }, (1..4).map { categoryOf("n$it") })
        assertEquals("income:salary", categoryOf("s1"), "the incomes were never in the results")
        // the results refreshed: each row's second line names its category now
        val shown = { compose.onAllNodes(hasText(" · ", substring = true) and !hasTestTag("search_count"), useUnmergedTree = true).fetchSemanticsNodes().map { n -> n.config[SemanticsProperties.Text].joinToString(" ") { it.text } } }
        val refreshed = runCatching { compose.waitUntil(10_000) { shown().let { rows -> rows.isNotEmpty() && rows.all { it.endsWith("Bills") } } } }.isSuccess
        if (!refreshed) throw AssertionError("every row on screen should name Bills now: " + shown()) // only the rows that fit are composed
        tap("search_undo")
        compose.waitUntil(10_000) { (1..4).all { categoryOf("n$it") == "Other" } }
        waitGone("search_notice")
    }

    @Test
    fun clearSelectionLeavesNothingSelectedAndChangingTheSearchDropsASelection() {
        fourNetflixAndTwoSalaries()
        search("Netflix", "n1", "n2", "n3", "n4")
        hold("result_n1"); waitTextOf("bulk_count", "1 selected")
        tap("result_n2"); waitTextOf("bulk_count", "2 selected")
        compose.onNodeWithTag("search_select_all").assertTextEquals("Select all 4") // a partial selection offers Select all
        tap("search_select_all"); waitTextOf("bulk_count", "4 selected")
        tap("search_select_all"); waitGone("bulk_bar"); waitFor("search_select_all")
        tap("search_select_all"); waitTextOf("bulk_count", "4 selected")
        node("search_text").performTextInput(" ")
        waitGone("bulk_bar")
    }

    @Test
    fun theIncomeSectionIsDisabledForAnAllExpenseSelectionAndNothingChangesWhenTheDialogIsClosed() {
        fourNetflixAndTwoSalaries()
        search("Netflix", "n1", "n2", "n3", "n4")
        tap("search_select_all"); waitTextOf("bulk_count", "4 selected")
        tap("bulk_category"); waitFor("bulk_category_heading_expense"); waitFor("bulk_category_heading_income")
        node("bulk_category_option_Bills").performScrollTo().assertIsEnabled()
        node("bulk_category_option_income:salary").performScrollTo().assertIsNotEnabled()
        waitFor("bulk_category_note_income")
        waitFor("bulk_category_search") // more than twelve categories
        tap("bulk_category_close"); waitGone("bulk_category_list")
        assertEquals(List(4) { "Other" }, (1..4).map { categoryOf("n$it") })
        waitFor("bulk_bar")
    }

    @Test
    fun aMixedSelectionWithAnExpenseCategoryChangesTheExpensesSkipsTheIncomeAndSaysSo() {
        (1..3).forEach { expense("n$it", "Netflix") }
        income("r1", "Netflix refund", cat = "income:refundsandcashback")
        search("Netflix", "n1", "n2", "n3", "r1")
        tap("search_select_all"); waitTextOf("bulk_count", "4 selected")
        tap("bulk_category"); waitFor("bulk_category_heading_income")
        node("bulk_category_option_income:salary").performScrollTo().assertIsEnabled() // mixed: both sections are open
        node("bulk_category_option_Bills").performScrollTo().assertIsEnabled()
        tap("bulk_category_option_Bills")
        waitFor("search_notice")
        compose.waitUntil(10_000) { textOf("search_notice").contains("Changed the category of 3 entries") }
        val notice = textOf("search_notice")
        assertTrue(notice.contains("1 skipped") && notice.contains("income can't use an expense category"), notice)
        assertEquals(List(3) { "Bills" }, (1..3).map { categoryOf("n$it") })
        assertEquals("income:refundsandcashback", categoryOf("r1"), "the income keeps its category")
        tap("search_undo")
        compose.waitUntil(10_000) { (1..3).all { categoryOf("n$it") == "Other" } }
        assertEquals("income:refundsandcashback", categoryOf("r1"))
    }

    @Test
    fun anAllIncomeSelectionDimsTheExpenseSectionAndTheSearchBoxFindsAnIncomeCategory() {
        income("p1", "Payout One"); income("p2", "Payout Two")
        expense("e1", "Payout Shop")
        search("Payout", "p1", "p2", "e1")
        hold("result_p1"); waitTextOf("bulk_count", "1 selected") // selection mode must be on before the second tap, or that tap is not a selection
        tap("result_p2"); waitTextOf("bulk_count", "2 selected")
        tap("bulk_category"); waitFor("bulk_category_search")
        node("bulk_category_search").performTextInput("other")
        waitFor("bulk_category_option_income:otherincome")
        node("bulk_category_option_Other").performScrollTo().assertIsNotEnabled()
        waitFor("bulk_category_note_expense")
        tap("bulk_category_option_income:otherincome")
        compose.waitUntil(10_000) { categoryOf("p1") == "income:otherincome" && categoryOf("p2") == "income:otherincome" }
        compose.waitUntil(10_000) { has("search_notice") && textOf("search_notice").contains("Changed the category of 2 entries") }
        assertEquals("Other", categoryOf("e1"), "the expense that was not selected is untouched")
    }

    @Test
    fun doneOnTheKeyboardPutsItAwayAndLeavesTheResultsShown() {
        expense("a", "Netflix one"); expense("b", "Netflix two")
        launch(); tap("open_search"); waitFor("search_text")
        node("search_text").performTextInput("Netflix")
        compose.waitUntil(10_000) { has("search_count") && has("result_a") }
        fun keyboardShown() = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys input_method").use { java.io.BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(it.fileDescriptor))).readText() }.contains("mInputShown=true")
        compose.waitUntil(10_000) { keyboardShown() }
        node("search_text").performImeAction()
        compose.waitUntil(10_000) { !keyboardShown() }
        assertTrue(has("result_a") && has("result_b"), "the results are still there")
    }
}
