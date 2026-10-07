package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Transfer
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
class BulkEditTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private var t = System.currentTimeMillis() - 100_000

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { scenario?.close(); resetApp() }

    private fun launch() { scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun waitText(s: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun hold(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { longClick() } }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!

    private fun expense(id: String, merchant: String, minor: Long = 10_000, cat: String = "Food", account: String = "cash") = db { ok ->
        ok.store.save(Expense(EntryId(id), t++, AccountId(account), Money(minor, inr), listOf(CategorySplit(CategoryId(cat), Money(minor, inr)))), EntryDetails(merchant = merchant))
    }
    private fun categoryOf(id: String) = db { it.db.schemaQueries.splitsOf(id).executeAsList().singleOrNull()?.category_id ?: it.db.schemaQueries.entryById(id).executeAsOne().category_id }
    private fun accountOf(id: String) = db { it.db.schemaQueries.entryById(id).executeAsOne().account_id }
    private fun live(id: String) = db { it.db.schemaQueries.entryById(id).executeAsOneOrNull()?.deleted == 0L }

    @Test
    fun longPressingRowsSelectsThemAndOneCategoryChangeAppliesToAllAndUndoPutsThemBack() {
        expense("a", "Alpha"); expense("b", "Bravo"); expense("c", "Charlie")
        launch()
        hold("row_Alpha"); waitText("1 selected")
        tap("row_Bravo"); waitText("2 selected") // once selecting, a tap toggles instead of opening
        tap("bulk_category"); tap("bulk_cat_Bills")
        waitGone("bulk_bar")
        assertEquals(listOf("Bills", "Bills", "Food"), listOf(categoryOf("a"), categoryOf("b"), categoryOf("c")))
        waitText("Changed the category of 2 entries")
        compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick() // the snackbar's Undo
        compose.waitUntil(10_000) { categoryOf("a") == "Food" && categoryOf("b") == "Food" }
    }

    @Test
    fun cancellingSelectionChangesNothingAndBackAlsoLeavesSelection() {
        expense("a", "Alpha")
        launch(); hold("row_Alpha"); waitFor("bulk_bar")
        tap("bulk_cancel"); waitGone("bulk_bar")
        hold("row_Alpha"); waitFor("bulk_bar")
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitGone("bulk_bar"); assertEquals("Food", categoryOf("a"))
    }

    @Test
    fun aBulkCategorySkipsSplitsTransfersAndRefundsAndSaysSoAndLeavesThemUntouched() {
        expense("a", "Alpha")
        db { ok ->
            ok.store.save(Expense(EntryId("s"), t++, AccountId("cash"), Money(100_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(60_000, inr)), CategorySplit(CategoryId("Fun"), Money(40_000, inr)))), EntryDetails(merchant = "Split"))
            ok.store.save(Transfer(EntryId("t"), t++, AccountId("cash"), AccountId("bank"), Money(5_000, inr)), EntryDetails(merchant = "Move"))
            ok.store.save(Income(EntryId("i"), t++, AccountId("cash"), Money(7_000, inr), CategoryId("Other")), EntryDetails(merchant = "Pay"))
        }
        val r = db { ok -> EntryActions(ok).bulkSetCategory(listOf("a", "s", "t", "i", "missing"), "Bills") }
        assertEquals(2, r.changed, "the plain expense and the income")
        assertEquals(3, r.skipped, "the split, the transfer and the unknown id")
        assertTrue(r.why!!.contains("split"))
        assertEquals("Bills", categoryOf("a")); assertEquals("Bills", categoryOf("i"))
        assertEquals(listOf("Food", "Fun"), db { it.db.schemaQueries.splitsOf("s").executeAsList().map { s -> s.category_id } }, "the split is untouched")
        // an entry already in the category is neither changed nor skipped
        assertEquals(0, db { ok -> EntryActions(ok).bulkSetCategory(listOf("a"), "Bills") }.let { it.changed + it.skipped })
    }

    @Test
    fun aBulkAccountMoveSkipsATransferThatWouldGoFromAnAccountToItself() {
        expense("a", "Alpha"); expense("b", "Bravo", account = "bank")
        db { ok -> ok.store.save(Transfer(EntryId("t"), t++, AccountId("cash"), AccountId("bank"), Money(5_000, inr)), EntryDetails(merchant = "Move")) }
        val r = db { ok -> EntryActions(ok).bulkSetAccount(listOf("a", "b", "t"), "bank") }
        assertEquals(1, r.changed, "only Alpha actually moves: Bravo is there already")
        assertEquals(1, r.skipped, "the transfer from cash to bank cannot start in bank")
        assertEquals("bank", accountOf("a")); assertEquals("cash", accountOf("t"))
    }

    @Test
    fun deletingAnExpenseWithAnUnselectedRefundIsSkippedAndSelectingBothDeletesBothAndUndoBringsBothBack() {
        expense("e", "Shoes", 50_000)
        db { ok -> ok.store.save(Refund(EntryId("r"), t++, AccountId("cash"), Money(10_000, inr), EntryId("e")), EntryDetails(merchant = "Return")) }
        val only = db { ok -> EntryActions(ok).bulkDelete(listOf("e")) }
        assertEquals(0, only.changed); assertEquals(1, only.skipped); assertTrue(live("e"))
        val both = db { ok -> EntryActions(ok).bulkDelete(listOf("e", "r")) }
        assertEquals(2, both.changed); assertTrue(!live("e") && !live("r"))
        db { ok -> ok.store.atomically { both.undoRestore.forEach { EntryActions(ok).restore(it) } } }
        assertTrue(live("e") && live("r"), "restored in an order the ledger accepts (the expense before its refund)")
    }

    @Test
    fun theUndoOnTheSnackbarTakesTheWholeBulkDeleteBack() {
        expense("a", "Alpha"); expense("b", "Bravo")
        launch(); hold("row_Alpha"); tap("row_Bravo"); tap("bulk_delete")
        waitGone("bulk_bar"); waitText("Deleted 2 entries")
        assertTrue(!live("a") && !live("b"))
        compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick()
        compose.waitUntil(10_000) { live("a") && live("b") }
    }

    @Test
    fun fromSearchResultsAnEditAppliesAndTheNoticeOffersUndo() {
        expense("a", "Alpha Cafe"); expense("b", "Beta Cafe"); expense("c", "Other")
        launch(); tap("open_search"); waitFor("search_text")
        compose.onNodeWithTag("search_text").performTextInput("Cafe")
        waitFor("result_a"); waitFor("result_b")
        hold("result_a"); tap("result_b"); waitText("2 selected")
        tap("bulk_category"); tap("bulk_cat_Fun")
        waitFor("search_notice"); waitText("Changed the category of 2 entries")
        assertEquals(listOf("Fun", "Fun", "Food"), listOf(categoryOf("a"), categoryOf("b"), categoryOf("c")))
        tap("search_undo")
        compose.waitUntil(10_000) { categoryOf("a") == "Food" && categoryOf("b") == "Food" }
        waitGone("search_notice")
    }
}
