package app.expenses

import android.content.Intent
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.bulkLoad
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * The save budget as the user feels it, in the app's own database with 100,000 entries and 508 categories: from the write through the view
 * model until the NEW ROW AND THE PERIOD TOTAL are on screen (budget: 50 ms on a phone). Everything else on the home screen (budget bars, bills
 * due, balances, tags, categories) is rebuilt in a second pass right behind it; that pass is timed separately and held to 200 ms on a phone.
 * Part of the timing gate (run on its own, like PerformanceBudgetTest).
 */
@RunWith(AndroidJUnit4::class)
class RefreshBudgetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @After fun cleanUp() { scenario?.close(); runBlocking { container.close() }; EncryptedDriver.reset(context) }

    @Test
    fun savingOneExpenseShowsTheNewRowAndTotalWithin50msPhoneBudgetAndTheRestSettlesWithin200ms() {
        runBlocking {
            container.write { ok ->
                repeat(500) { i -> ok.categories.create("Category $i") }
                ok.db.bulkLoad(ok.driver) {
                    for (i in 0 until 100_000) {
                        val at = 1_300_000_000_000L + i * 3_000_000L
                        ok.db.schemaQueries.insertEntry("e$i", "EXPENSE", at, "bank", null, 100L + i % 900, "INR", "Category ${i % 500}", "Shop ${i % 300}", "UPI Ref ${100_000_000L + i * 7919L} txn", "manual", null)
                        ok.db.schemaQueries.insertSpendLine("e$i", 0, at, "INR", "Category ${i % 500}", 100L + i % 900, 100L + i % 900)
                    }
                }
            }
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        var vm: LedgerViewModel? = null
        scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }
        val model = vm!!
        Thread.sleep(4_000) // let the first load and the background figures settle, as they have before a person adds anything
        val settle = ArrayList<Double>()
        fun one(i: Int): Double {
            model.lastSaveMillis = 0.0; model.lastSettleMillis = 0.0
            model.saveEntry(EntryDraft(null, DraftKind.EXPENSE, "5${i % 90}", "Category 1", emptyList(), "bank", "", "gate $i", System.currentTimeMillis()), false)
            val deadline = System.currentTimeMillis() + 10_000
            while (model.lastSaveMillis == 0.0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
            while (model.lastSettleMillis == 0.0 && System.currentTimeMillis() < deadline) Thread.sleep(5) // the follow-up rebuild
            settle += model.lastSettleMillis
            return model.lastSaveMillis
        }
        repeat(10) { one(it) } // warm-up: the second expense of a session, not the first
        settle.clear()
        val times = DoubleArray(60) { one(100 + it) }.sorted()
        val p95 = times[(times.size * 0.95).toInt()]
        Log.w("PERF", "save to new row and total visible at 100k: p50 ${"%.1f".format(times[times.size / 2])} ms, p95 ${"%.1f".format(p95)} ms, max ${"%.1f".format(times.last())} ms")
        assertTrue(times.first() > 0.0, "every save finished")
        val settled = settle.sorted(); val settleP95 = settled[(settled.size * 0.95).toInt()]
        Log.w("PERF", "rebuild after the row is shown: p50 ${"%.1f".format(settled[settled.size / 2])} ms, p95 ${"%.1f".format(settleP95)} ms")
        assertTrue(settleP95 <= 200.0 / 4.0, "the rest of the screen took p95 ${"%.1f".format(settleP95)} ms to settle after the new row, over 50 ms (200 ms phone)")
        assertTrue(p95 <= 50.0 / 4.0, "the new row and total took p95 ${"%.1f".format(p95)} ms to appear, over 12.5 ms (50 ms phone budget at the assumed 4x slowdown)")
    }
}
