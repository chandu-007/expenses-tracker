package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UI S2, the entry detail: a tap on an entry shows it read-only (Edit opens the form, Cancel in the form comes back), with Duplicate, Delete and Undo, and amounts that Hide amounts masks. */
@RunWith(AndroidJUnit4::class)
class UiS2DetailTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun spend(id: String, rupees: Long, category: String, note: String) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis(), AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))), EntryDetails(merchant = note, tags = listOf("Trip"))) }
    }
    private fun rows() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!

    @Test
    fun tappingAnEntryShowsItsDetailAndEditOpensTheFormAndCancelComesBack() {
        spend("e1", 120, "Food", "Cafe")
        launch(); compose.openEntryDetail("row_Cafe")
        node("detail_title").assertTextContains("Cafe"); node("detail_amount").assertTextContains("₹120.00", substring = true)
        node("detail_sub").assertTextContains("Food", substring = true)
        node("detail_category").assertTextContains("Food"); node("detail_account").assertTextContains("Cash"); node("detail_tags").assertTextContains("Trip")
        assertFalse(has("amount") || has("save"), "the detail is read-only: no form")
        tap("detail_edit"); waitFor("amount"); waitGone("detail_edit")
        node("amount").assertTextContains("120.00", substring = true)
        tap("cancel"); waitFor("detail_edit"); waitGone("amount")
        tap("detail_back"); waitFor("add"); waitGone("detail_edit")
        assertEquals(12_000L, rows().single().amount_minor)
    }

    @Test
    fun theCategoryChangeLinkOpensTheForm() {
        spend("e1", 120, "Food", "Cafe")
        launch(); compose.openEntryDetail("row_Cafe"); tap("detail_category_change"); waitFor("amount")
    }

    @Test
    fun deletingFromTheDetailOffersUndoAndUndoBringsItBack() {
        spend("e1", 120, "Food", "Cafe")
        launch(); compose.openEntryDetail("row_Cafe"); tap("detail_delete")
        waitFor("add"); waitGone("detail_edit")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rows().isEmpty(), "the entry is deleted")
        compose.onNodeWithTag("month_spend").assertTextContains("₹0", substring = true)
        compose.onAllNodes(hasText("Undo"))[0].performClick()
        compose.waitUntil(10_000) { has("row_Cafe") }
        assertEquals(1, rows().size)
    }

    @Test
    fun duplicateStartsANewEntryWithTheSameAmountCategoryAccountAndNote() {
        spend("e1", 120, "Food", "Cafe")
        launch(); compose.openEntryDetail("row_Cafe"); tap("detail_duplicate")
        waitFor("amount"); waitFor("save")
        node("amount").assertTextContains("120", substring = true)
        node("note").assertTextContains("Cafe"); node("category_chip_Food").assertIsSelected()
        tap("save"); waitGone("save")
        compose.waitUntil(10_000) { rows().size == 2 }
        assertEquals(listOf(12_000L, 12_000L), rows().map { it.amount_minor })
    }

    @Test
    fun anIncomeAndATransferShowTheirOwnFacts() {
        runBlocking { container.write { ok -> ok.store.save(Income(EntryId("i1"), System.currentTimeMillis(), AccountId("cash"), Money(500_000, inr), CategoryId("income:salary")), EntryDetails(merchant = "Acme")) } }
        launch(); compose.openEntryDetail("row_Acme")
        node("detail_amount").assertTextContains("+₹5,000.00", substring = true); node("detail_account").assertTextContains("Cash")
        waitFor("detail_duplicate") // an income can be duplicated too
    }

    @Test
    fun hideAmountsMasksTheDetailAndAReadOnlyEntryKeepsItsReason() {
        spend("e1", 120, "Food", "Cafe")
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        launch(); compose.openEntryDetail("row_Cafe")
        val shown = node("detail_amount").fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertFalse(shown.any { it.isDigit() }, "no digit of a hidden amount is on screen: '$shown'")
    }

    @Test
    fun anEntryFoundInSearchOpensTheSameDetail() {
        spend("e1", 120, "Food", "Cafe")
        launch(); tap("open_search"); waitFor("search_text"); node("search_text").performTextInput("Cafe")
        waitFor("result_e1"); tap("result_e1"); waitFor("detail_edit")
        node("detail_amount").assertTextContains("₹120.00", substring = true)
    }

    private fun openFromSearch(id: String, word: String) { tap("open_search"); waitFor("search_text"); node("search_text").performTextInput(word); waitFor("result_$id"); tap("result_$id"); waitFor("detail_edit") }

    @Test
    fun anAmountFarFromWhatThePersonUsuallyPaysThePayeeIsSaidInOneLineAndAnUsualOneSaysNothing() {
        spend("a", 300, "Food", "Starbucks"); spend("b", 320, "Food", "Starbucks"); spend("c", 340, "Food", "Starbucks"); spend("d", 480, "Food", "Starbucks"); spend("e", 330, "Food", "Cafe")
        launch(); openFromSearch("d", "Starbucks")
        waitFor("detail_insight")
        node("detail_insight").assertTextContains("₹160.00 above your usual at Starbucks", substring = true)
        tap("detail_back"); waitFor("search_text"); waitFor("result_b"); tap("result_b"); waitFor("detail_edit")
        compose.waitForIdle(); Thread.sleep(600)
        assertFalse(has("detail_insight"), "₹320 is the usual amount: nothing to say")
    }

    @Test
    fun theInsightLineIsMaskedByHideAmountsAndAPayeeWithFewEarlierEntriesHasNone() {
        spend("a", 300, "Food", "Starbucks"); spend("b", 320, "Food", "Starbucks"); spend("d", 900, "Food", "Starbucks")
        launch(); openFromSearch("d", "Starbucks"); compose.waitForIdle(); Thread.sleep(600)
        assertFalse(has("detail_insight"), "two earlier entries are not a usual amount")
        tap("detail_back")
        spend("c", 340, "Food", "Starbucks")
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        launch(); openFromSearch("d", "Starbucks"); waitFor("detail_insight")
        val shown = node("detail_insight").fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertFalse(shown.any { it.isDigit() }, "no digit of a hidden amount is on screen: '$shown'")
    }
}
