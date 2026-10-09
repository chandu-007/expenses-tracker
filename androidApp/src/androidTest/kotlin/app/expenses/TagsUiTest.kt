package app.expenses

import android.content.Intent
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

@RunWith(AndroidJUnit4::class)
class TagsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private var t = System.currentTimeMillis() - 100_000

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun waitText(s: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun hold(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { longClick() } }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun tagsOf(id: String) = db { it.store.tagsOf(EntryId(id)) }

    private fun expense(id: String, merchant: String, tags: List<String>? = null) = db { ok ->
        ok.store.save(Expense(EntryId(id), t++, AccountId("cash"), Money(10_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(10_000, inr)))), EntryDetails(merchant = merchant, tags = tags))
    }

    @Test
    fun tagsAreAddedInTheFormByTypingAndByTappingASuggestionAndAreShownAgainWhenEditing() {
        expense("old", "Old", listOf("Trip"))
        launch(); tap("add"); type("amount", "250")
        type("tag_input", "#Goa"); tap("tag_add"); waitFor("tag_chip_Goa")
        tap("tag_suggest_Trip"); waitFor("tag_chip_Trip")
        tap("save"); waitFor("add")
        val id = db { it.db.schemaQueries.recentEntries(5).executeAsList().first { r -> r.amount_minor == 25_000L }.id }
        assertEquals(listOf("Goa", "Trip"), tagsOf(id))
    }

    @Test
    fun aDuplicateTagIsRefusedWithAReasonAndATagChipTakesItOff() {
        expense("a", "Alpha", listOf("Trip"))
        launch(); tap("row_Alpha"); waitFor("tag_chip_Trip")
        type("tag_input", "trip"); tap("tag_add"); waitFor("tag_problem"); waitText("already on this entry")
        tap("tag_chip_Trip"); waitGone("tag_chip_Trip")
        tap("save"); waitFor("add")
        assertEquals(emptyList(), tagsOf("a"))
    }

    @Test
    fun searchingByTagFindsOnlyTaggedEntriesAndCombinesWithText() {
        expense("a", "Alpha Cafe", listOf("Trip")); expense("b", "Beta Cafe"); expense("c", "Gamma", listOf("Trip"))
        launch(); tap("open_search"); waitFor("search_text")
        tap("search_tag"); tap("search_tag_Trip")
        waitFor("result_a"); waitFor("result_c"); assertEquals(false, has("result_b"))
        compose.onNodeWithTag("search_text").performTextInput("Cafe")
        waitFor("result_a"); waitGone("result_c") // results settle on the one tagged entry that also matches the text
    }

    @Test
    fun bulkTaggingAddsANewTagToAllSelectedAndRemovingTakesItOffAndUndoRestores() {
        expense("a", "Alpha", listOf("Trip")); expense("b", "Bravo")
        launch(); hold("row_Alpha"); tap("row_Bravo")
        tap("bulk_tag"); tap("bulk_tag_new"); type("bulk_tag_input", "Tax"); tap("bulk_tag_ok")
        waitText("Tagged 2 entries")
        assertEquals(listOf("Tax", "Trip"), tagsOf("a")); assertEquals(listOf("Tax"), tagsOf("b"))
        compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick()
        compose.waitUntil(10_000) { tagsOf("b").isEmpty() }
        assertEquals(listOf("Trip"), tagsOf("a"))
        hold("row_Alpha"); tap("bulk_tag"); tap("bulk_tag_remove_Trip")
        compose.waitUntil(10_000) { tagsOf("a").isEmpty() }
    }

    @Test
    fun aTagChangeShowsInTheEntrysHistory() {
        expense("a", "Alpha", listOf("Trip"))
        Thread.sleep(30)
        launch(); tap("row_Alpha"); type("tag_input", "Tax"); tap("tag_add"); tap("save"); waitFor("add")
        tap("row_Alpha"); tap("history_open"); waitFor("history_list")
        waitText("Tags: Trip → Tax, Trip")
    }
}
