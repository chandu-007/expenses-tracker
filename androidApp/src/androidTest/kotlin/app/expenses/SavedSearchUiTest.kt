package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
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

@RunWith(AndroidJUnit4::class)
class SavedSearchUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private var t = System.currentTimeMillis() - 100_000

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun waitText(s: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun hold(tag: String) { waitFor(tag); compose.onNodeWithTag(tag).performTouchInput { longClick() } }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun expense(id: String, merchant: String, minor: Long, cat: String, tags: List<String>? = null) = db { ok ->
        ok.store.save(Expense(EntryId(id), t++, AccountId("cash"), Money(minor, Currency.INR), listOf(CategorySplit(CategoryId(cat), Money(minor, Currency.INR)))), EntryDetails(merchant = merchant, tags = tags))
    }
    private fun openSearch() { launch(); tap("open_search"); waitFor("search_text") }

    @Test
    fun aSearchSavedWithItsFiltersComesBackWithAllOfThemAfterARestart() {
        expense("a", "Uber ride", 50_000, "Transport"); expense("b", "Uber eats", 80_000, "Food", listOf("Work")); expense("c", "Zomato", 90_000, "Food"); expense("d", "Uber lunch", 70_000, "Food")
        openSearch()
        type("search_text", "uber"); tap("search_kind_expense"); tap("search_category"); tap("search_cat_Food"); tap("search_tag"); tap("search_tag_Work")
        waitFor("result_b"); assertTrue(!has("result_a") && !has("result_c") && !has("result_d"))
        tap("save_search"); type("saved_name", "Uber food"); tap("saved_ok"); waitFor("saved_Uber food")
        launch(); tap("open_search"); waitFor("saved_Uber food")
        tap("saved_Uber food")
        waitFor("result_b"); assertTrue(!has("result_a") && !has("result_c") && !has("result_d"), "the same conditions (text, kind, category and tag) apply again")
        compose.onNodeWithTag("search_text").assertTextContains("uber")
    }

    @Test
    fun aSavedSearchRunsAgainstTodaysEntriesNotTheOnesThatExistedWhenItWasSaved() {
        openSearch(); type("search_text", "latte"); tap("save_search"); type("saved_name", "Coffee"); tap("saved_ok"); waitFor("saved_Coffee")
        expense("n", "Latte new", 30_000, "Food")
        tap("search_back"); waitFor("add"); tap("open_search"); tap("saved_Coffee")
        waitFor("result_n")
    }

    @Test
    fun aDuplicateNameIsRefusedWithAReasonAndTheDialogStaysOpenAndLongPressRemovesOne() {
        openSearch(); type("search_text", "x"); tap("save_search"); type("saved_name", "Cabs"); tap("saved_ok"); waitFor("saved_Cabs")
        tap("save_search"); type("saved_name", "cabs"); tap("saved_ok"); waitFor("saved_problem"); waitText("already have a saved search")
        type("saved_name", ""); tap("saved_ok"); waitText("Give the search a name")
        type("saved_name", "Taxis"); tap("saved_ok"); waitFor("saved_Taxis"); waitGone("saved_name")
        hold("saved_Taxis"); tap("saved_delete_ok"); waitGone("saved_Taxis")
        hold("saved_Cabs"); tap("saved_delete_ok"); waitGone("saved_Cabs")
        assertEquals(emptyList(), db { it.savedSearches.list().map { s -> s.name } }, "both were removed, and only the searches: no entries")
    }

    @Test
    fun theSaveButtonIsOnlyOfferedOnceThereIsSomethingToSave() {
        openSearch(); assertTrue(!has("save_search"))
        type("search_text", "ab"); waitFor("save_search")
    }

    @Test
    fun longPressingAQuickEntryOnHomeOffersToRemoveIt() {
        db { ok -> ok.templates.save(app.expenses.core.domain.Template("t", "Chai", Money(2_000, Currency.INR), CategoryId("Food"), AccountId("cash"), null)) }
        launch(); hold("template_Chai"); waitText("Remove \"Chai\"?")
    }
}
