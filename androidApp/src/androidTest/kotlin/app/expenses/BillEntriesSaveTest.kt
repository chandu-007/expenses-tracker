package app.expenses

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.BillEntries
import app.expenses.core.domain.BillItem
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The items of a bill saved as entries of their own (the view model behind "Each item as its own entry"): all of them, in one step, taken back by one Undo. */
@RunWith(AndroidJUnit4::class)
class BillEntriesSaveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as ExpensesApp
    private val store = ViewModelStore()

    @Before fun fresh() { runBlocking { app.container.startFresh() } }
    @After fun cleanUp() { store.clear(); runBlocking { app.container.startFresh() } }

    private fun viewModel(): LedgerViewModel {
        val vm = ViewModelProvider(store, app.viewModelFactory)[LedgerViewModel::class.java]
        val deadline = System.currentTimeMillis() + 10_000
        while (vm.state.value !is UiState.Ready && System.currentTimeMillis() < deadline) Thread.sleep(20)
        return vm
    }

    private fun rows() = runBlocking { app.container.write { ok -> ok.db.schemaQueries.recentEntries(50).executeAsList().filter { it.deleted == 0L } } }!!
    private fun waitFor(what: String, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!check() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue(check(), what)
    }

    @Test
    fun eachItemBecomesAnExpenseThatAddsUpToTheBillAndOneUndoTakesThemAllBack() {
        val vm = viewModel()
        val base = EntryDraft(null, DraftKind.EXPENSE, category = "Food", accountId = "bank", occurredAt = System.currentTimeMillis(), tags = listOf("Dinner"))
        val items = listOf(BillItem("Veg Biryani", 18_000), BillItem("Masala Dosa", 12_000, quantity = 2), BillItem("Filter Coffee", 4_000))
        vm.saveEntries(BillEntries.drafts(base, items, 1_700, Currency.INR), photoUri = null)
        waitFor("four entries") { rows().size == 4 }
        val saved = rows()
        assertEquals(35_700L, saved.sumOf { it.amount_minor }, "the entries add up to the bill")
        assertEquals(setOf("Veg Biryani", "Masala Dosa ×2", "Filter Coffee", BillEntries.REST_NAME), saved.mapNotNull { it.merchant }.toSet())
        assertTrue(saved.all { it.kind == "EXPENSE" && it.account_id == "bank" })

        // one Undo takes back all four
        waitFor("an undo offered") { (vm.state.value as? UiState.Ready)?.undo != null }
        val undo = assertNotNull((vm.state.value as UiState.Ready).undo)
        assertEquals(4, undo.bulkCreated.size)
        vm.undo(undo)
        waitFor("none left") { rows().isEmpty() }
    }

    @Test
    fun aDraftTheLedgerRefusesSavesNothingAtAll() {
        val vm = viewModel()
        val base = EntryDraft(null, DraftKind.EXPENSE, category = "Food", accountId = "bank", occurredAt = System.currentTimeMillis())
        val good = BillEntries.drafts(base, listOf(BillItem("Tea", 2_000)), 0, Currency.INR)
        val bad = good.map { it.copy(amountText = "not an amount", note = "Broken") }
        vm.saveEntries(good + bad, photoUri = null)
        Thread.sleep(1_500)
        assertEquals(0, rows().size, "all or none: a refused line keeps the good one out too")
    }
}
