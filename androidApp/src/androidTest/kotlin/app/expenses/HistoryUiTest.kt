package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import app.expenses.core.domain.RevisionKind
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EncryptedDriver
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
class HistoryUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { scenario?.close(); resetApp() }

    private fun launch() { scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitText(t: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun amount() = db { it.db.schemaQueries.entryById("e").executeAsOne().amount_minor }

    private fun seed() = db { ok ->
        ok.store.save(Expense(EntryId("e"), System.currentTimeMillis(), AccountId("cash"), Money(10_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(10_000, inr)))), EntryDetails(merchant = "Cafe"))
    }

    @Test
    fun theHistoryShowsCreationAndEachEditWithOldAndNewValuesAndAnEditCanBeUndone() {
        seed()
        // wait so the saves get different times (history groups by time)
        Thread.sleep(30)
        launch(); tap("row_Cafe"); type("amount", "150"); tap("save"); waitFor("add")
        Thread.sleep(30)
        tap("row_Cafe"); type("amount", "175"); tap("category_chip_Bills"); tap("save"); waitFor("add")
        assertEquals(17_500L, amount())
        tap("row_Cafe"); tap("history_open"); waitFor("history_list")
        waitText("Created"); waitText("Account: Cash"); waitText("Amount: ₹100.00 → ₹150.00"); waitText("Amount: ₹150.00 → ₹175.00"); waitText("Categories: Food ₹150.00 → Bills ₹175.00")
        // the newest edit is undone: back to ₹150 and Food
        tap("restore_2"); waitFor("add")
        assertEquals(15_000L, amount())
        val cat = db { it.db.schemaQueries.splitsOf("e").executeAsList().single().category_id }
        assertEquals("Food", cat)
        // the undo is itself an edit in the history, so nothing is lost
        val kinds = db { it.store.history(EntryId("e")).map { r -> r.kind } }
        assertEquals(listOf(RevisionKind.CREATED, RevisionKind.EDITED, RevisionKind.EDITED, RevisionKind.EDITED), kinds)
    }

    @Test
    fun deletingAndBringingBackAnEntryAppearsInItsHistory() {
        seed()
        db { ok -> Thread.sleep(30); ok.store.delete(EntryId("e")); Thread.sleep(30); ok.store.restore(EntryId("e")) }
        launch(); tap("row_Cafe"); tap("history_open"); waitFor("history_list")
        waitText("Deleted"); waitText("Brought back"); waitText("Created")
        assertTrue(!has("restore_0") && !has("restore_1"), "only edits can be undone")
    }

    @Test
    fun backFromTheHistoryReturnsToTheEditorNotTheList() {
        seed(); launch(); tap("row_Cafe"); tap("history_open"); waitFor("history_list")
        tap("history_back"); waitFor("amount")
    }
}
