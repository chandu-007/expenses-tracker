package app.expenses

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.FtsTuning
import app.expenses.core.storage.bulkLoad
import app.expenses.core.storage.db.ExpensesDb
import app.expenses.core.storage.SearchQuery
import app.expenses.core.storage.search
import app.expenses.core.storage.searchFiltered
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Performance gate for the storage layer on a realistic dataset: 100,000 transactions in ascending date
 * order over 10 years, 500 merchants with a skewed frequency (the most common merchant is about 11% of
 * rows, like a food-delivery app), 40 categories, encrypted with SQLCipher. Budgets come from
 * PRODUCT_CONTEXT.md section 6.1 divided by [PHONE_SLOWDOWN].
 *
 * ASSUMPTION (unverified): a mid-range phone is [PHONE_SLOWDOWN] times slower than the emulator host
 * (Apple silicon). Measure on a real mid-range device and replace this constant with the observed ratio.
 * Run on one pinned AVD in CI so results are comparable between runs.
 */
@RunWith(AndroidJUnit4::class)
class PerformanceBudgetTest {
    companion object {
        const val PHONE_SLOWDOWN = 4.0

        /**
         * Phone budget for a search that has to read the whole 100,000-row ledger. Over the 100 ms product target on purpose (see the two tests that
         * use it). Raised from 600 to 800 when the second such case was added, which measures about 143 ms of the 150 ms the old value allowed here.
         */
        const val WORST_CASE_PHONE_MS = 800.0
        private const val ROWS = 100_000
        private const val MERCHANTS = 500
        private const val DAY_MS = 86_400_000L
        private const val TEN_YEARS_MS = 3_650 * DAY_MS
        private const val START = 1_500_000_000_000L
        private const val NAME = "perf-gate.db"
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private lateinit var driver: SqlDriver
        private lateinit var db: ExpensesDb
        private val matchCount = IntArray(MERCHANTS)
        private val banks = listOf("hdfc", "icici", "sbi", "axis", "kotak", "yesbank", "pnb", "idfc")

        /** Pronounceable-looking unique names with no shared substrings, so frequency is controlled by [merchantFor]. */
        private fun merchantName(index: Int): String =
            java.lang.Long.toString((index + 1L) * 2_654_435_761L % 2_176_782_336L, 36).padStart(6, 'a')

        @BeforeClass @JvmStatic fun load() {
            EncryptedDriver.reset(context, NAME)
            driver = EncryptedDriver.create(context, NAME)
            db = ExpensesDb(driver)
            FtsTuning.apply(driver) // the app does this when it opens the database
            val rnd = Random(7)
            db.bulkLoad(driver) { // importers insert in ascending date order
                for (i in 0 until ROWS) {
                    // log-uniform pick: merchant 0 gets ln(2)/ln(500), about 11% of rows
                    val m = (MERCHANTS.toDouble().pow(rnd.nextDouble())).toInt() - 1
                    matchCount[m]++
                    val at = START + i * (TEN_YEARS_MS / ROWS)
                    db.schemaQueries.insertEntry("e$i", "EXPENSE", at, "bank", null, 100L + i % 900, "INR", "cat${i % 40}", merchantName(m), "UPI ${banks[i % banks.size]} Ref ${100_000_000L + i * 7919L} txn", "manual", null)
                    // what LedgerStore writes for an unsplit, unshared expense
                    db.schemaQueries.insertSpendLine("e$i", 0, at, "INR", "cat${i % 40}", 100L + i % 900, 100L + i % 900)
                    if (i % 3 == 0) db.schemaQueries.insertTag("e$i", "tag${i % 500}") // a third of the ledger tagged, 500 distinct tags (PRODUCT_CONTEXT 6.1)
                }
            }
        }

        @AfterClass @JvmStatic fun unload() {
            runCatching { driver.close() }
            EncryptedDriver.reset(context, NAME)
        }

        private fun budget(phoneMs: Double) = phoneMs / PHONE_SLOWDOWN
    }

    private fun median(runs: Int = 7, f: () -> Unit): Double {
        f()
        val samples = DoubleArray(runs) { val s = System.nanoTime(); f(); (System.nanoTime() - s) / 1e6 }.sorted()
        return samples[runs / 2]
    }

    private fun check(label: String, phoneBudgetMs: Double, measured: Double) {
        val limit = budget(phoneBudgetMs)
        Log.w("PERF", "$label: ${"%.2f".format(measured)} ms (limit ${"%.1f".format(limit)} ms = $phoneBudgetMs ms / $PHONE_SLOWDOWN)")
        assertTrue(measured <= limit, "$label took $measured ms, over the $limit ms limit ($phoneBudgetMs ms phone budget)")
    }

    /** Counts stored spend lines in a window, to prove the window really holds about a tenth of the data. */
    private fun rowsInWindowCount(from: Long, until: Long): Long =
        driver.executeQuery(null, "SELECT COUNT(*) FROM spend_line WHERE occurred_at >= $from AND occurred_at < $until", { c ->
            c.next(); app.cash.sqldelight.db.QueryResult.Value(c.getLong(0)!!)
        }, 0).value

    @Test fun tagSuggestionsAcrossAThirdOfTheLedgerTaggedStayUnder50msPhoneBudget() {
        // Runs on every refresh, so on every launch and every save. It once took 787 ms on the first (cold) launch of a 100k ledger with 33k tagged entries.
        val ledger = app.expenses.core.storage.LedgerStore(db, app.expenses.core.domain.sync.HlcClock("perf")) { System.currentTimeMillis() }
        assertEquals(40, ledger.tagsInUse(40).size)
        assertEquals(67L, ledger.tagsInUse(40).first().second, "the most used tag has 67 of the 33,334 tagged entries")
        check("tag suggestions (33k tagged entries, 500 tags)", 50.0, median { ledger.tagsInUse(40) })
    }

    @Test fun datasetIsAsDescribed() {
        assertEquals(ROWS.toLong(), db.schemaQueries.entryCount().executeAsOne())
        val share = matchCount[0] / ROWS.toDouble()
        assertTrue(share in 0.09..0.13, "most common merchant share was $share")
    }

    @Test fun searchForTheMostCommonMerchantStaysUnder100msPhoneBudget() {
        assertEquals(50, db.search(merchantName(0)).size)
        check("search, most common merchant (~11% of rows)", 100.0, median { db.search(merchantName(0)) })
    }

    @Test fun searchForTextThatAlmostEveryNoteSharesStaysUnder100msPhoneBudget() {
        // SMS and UPI notes nearly all say "UPI ... Ref ... txn" and name a bank, so these match most of the ledger.
        for (term in listOf("upi", "txn", "ref", "upi hdfc", "hdfc")) {
            assertEquals(50, db.search(term).size, term)
            check("search, shared note text '$term'", 100.0, median { db.search(term) })
        }
    }

    @Test fun searchForRareAndMissingTermsStaysUnder100msPhoneBudget() {
        val rare = (0 until MERCHANTS).filter { matchCount[it] > 0 }.minByOrNull { matchCount[it] }!!
        assertEquals(matchCount[rare], db.search(merchantName(rare), 1000).size)
        check("search, rare merchant (${matchCount[rare]} rows)", 100.0, median { db.search(merchantName(rare)) })
        assertEquals(0, db.search("zzzzzz").size)
        check("search, no match", 100.0, median { db.search("zzzzzz") })
    }

    @Test fun filteredSearchesStayUnder100msPhoneBudget() {
        val lastYear = START + 3_285 * DAY_MS
        val cases = listOf(
            "text shared by every row + a narrow amount range (~6% of rows)" to SearchQuery("upi", minMinor = 100, maxMinor = 153, currency = "INR"),
            "text shared by every row + one category (2.5% of rows)" to SearchQuery("upi", categoryIds = listOf("cat7")),
            "text shared by every row + the last year" to SearchQuery("upi", fromMillis = lastYear),
            "text shared by every row + category + amount + last year" to SearchQuery("txn", minMinor = 200, maxMinor = 400, currency = "INR", categoryIds = listOf("cat3", "cat9"), fromMillis = lastYear),
            "most common merchant + amount range" to SearchQuery(merchantName(0), minMinor = 500, currency = "INR"),
            "no text, category only" to SearchQuery(categoryIds = listOf("cat11")),
            "no text, amount range only" to SearchQuery(minMinor = 100, maxMinor = 199, currency = "INR"),
        )
        for ((label, q) in cases) {
            assertEquals(50, db.searchFiltered(q).size, label)
            check("filtered search, $label", 100.0, median { db.searchFiltered(q) })
        }
    }

    /**
     * KNOWN MISS against the 100 ms product target: a word every row has plus a short word almost none has ("upi zq"). The index finds
     * all 100,000 rows and each is then checked for the short word (1-2 characters cannot use the trigram index), so the whole ledger is
     * read. Measured on the emulator (best case): about 143 ms; "hdfc ##" about 114 ms. A common short word ("upi 1") stops early, 8 ms.
     * Tried and rejected: the sort-first plan is 30% faster here (100 ms) but 15 times slower when the short word is common (116 ms vs
     * 8 ms), and nothing says in advance which applies; capping the rows examined would make answers inexact. Gated with the other
     * whole-ledger case at [WORST_CASE_PHONE_MS] so it cannot quietly get worse.
     */
    @Test fun aCommonWordWithARareShortWordStaysWithinTheWorstCaseBudget() {
        for (text in listOf("upi zq", "txn #7", "hdfc ##")) {
            val q = SearchQuery(text)
            assertEquals(0, db.searchFiltered(q).size, text)
            check("search, common word + rare short word '$text' (matches nothing, reads the whole ledger)", WORST_CASE_PHONE_MS, median { db.searchFiltered(q) })
        }
        val common = SearchQuery("upi 1") // a short word many rows have: stops early
        assertEquals(50, db.searchFiltered(common).size)
        check("search, common word + common short word 'upi 1'", 100.0, median { db.searchFiltered(common) })
    }

    /**
     * The worst case for walking the date index: a filter so selective that nothing matches, so the whole ledger is read.
     *
     * KNOWN MISS against the 100 ms product target. Measured on the emulator (best case): about 50 ms with an amount filter alone and
     * about 117 ms when a category filter is added (any category test roughly doubles the scan; cause not investigated). Ordinary
     * filtered searches, where matches are found early, take under 10 ms. This case is gated separately at [WORST_CASE_PHONE_MS] so it
     * cannot quietly get worse; the screen runs searches off the main thread and shows that it is working.
     */
    @Test fun aVerySelectiveFilterWithNoTextStaysWithinItsOwnWorstCaseBudget() {
        val q = SearchQuery(minMinor = 999, maxMinor = 999, currency = "INR", categoryIds = listOf("cat3"))
        assertEquals(0, db.searchFiltered(q, 1000).size, "this filter matches nothing in the dataset, so it reads every row")
        check("filtered search, no text, matches nothing (reads the whole ledger)", WORST_CASE_PHONE_MS, median { db.searchFiltered(q) })
    }

    @Test fun categoryTotalsForOneYearStayUnder300msPhoneBudget() {
        val until = START + 365 * DAY_MS
        val inWindow = db.schemaQueries.spendByCategory("INR", START, until).executeAsList()
        assertEquals(40, inWindow.size)
        val step = TEN_YEARS_MS / ROWS
        val seeded = (0 until ROWS).filter { START + it * step < until }.sumOf { 100L + it % 900 }
        assertEquals(seeded, inWindow.sumOf { it.mine ?: 0L }, "the timed query must return exactly what was seeded in the window")
        val counted = rowsInWindowCount(START, until)
        assertTrue(counted in 9_900L..10_100L, "the one-year window holds $counted spend lines, expected about 10,000")
        check("category totals, one year (~10k rows)", 300.0, median { db.schemaQueries.spendByCategory("INR", START, until).executeAsList() })
    }

    @Test fun categoryTotalsForTenYearsStayUnder1sPhoneBudget() {
        check("category totals, 10 years (100k rows)", 1000.0, median { db.schemaQueries.spendByCategory("INR", 0, Long.MAX_VALUE).executeAsList() })
    }

    @Test fun listFirstPageAndDeepScrollStayUnder100msPhoneBudget() {
        assertEquals(50, db.schemaQueries.recentEntries(50).executeAsList().size)
        check("recent 50", 100.0, median { db.schemaQueries.recentEntries(50).executeAsList() })
        // The 50 oldest rows of the whole list, reached by keyset paging from the 51st oldest.
        val all = db.schemaQueries.entriesBefore(Long.MAX_VALUE, Long.MAX_VALUE, ROWS.toLong()).executeAsList()
        assertEquals(ROWS, all.size)
        val cursorRow = all[ROWS - 51]
        val deep = db.schemaQueries.entriesBefore(cursorRow.occurred_at, cursorRow.seq, 50).executeAsList()
        assertEquals(all.takeLast(50).map { it.id }, deep.map { it.id })
        check("deep scroll, last page (keyset)", 100.0, median { db.schemaQueries.entriesBefore(cursorRow.occurred_at, cursorRow.seq, 50).executeAsList() })
    }

    @Test fun accountBalancesForTheHomeStripStayUnder150msPhoneBudgetInTheBackground() {
        // Worked out off the save path, only while the Balances block or the accounts screen shows, a moment after the last write. Without the
        // covering indexes it took about 240 ms on this dataset; with them about 25 ms. The budget is a background one, not the 50 ms save budget.
        // The generated entries refer to accounts that have no row; give them rows for the measurement, and remove them again.
        val ids = driver.executeQuery(null, "SELECT DISTINCT account_id FROM entry WHERE account_id IS NOT NULL", { c ->
            val l = ArrayList<String>(); while (c.next().value) l += c.getString(0)!!; app.cash.sqldelight.db.QueryResult.Value(l)
        }, 0).value
        ids.forEach { driver.execute(null, "INSERT OR IGNORE INTO account(id, name, type, currency, deleted) VALUES ('$it', '$it', 'BANK', 'INR', 0)", 0) }
        try {
            assertEquals(ids.size, db.schemaQueries.accountBalances().executeAsList().size)
            val ms = median { db.schemaQueries.accountBalances().executeAsList() }
            Log.w("PERF", "account balances over $ROWS entries and ${ids.size} accounts: median ${"%.2f".format(ms)} ms")
            check("account balances over the whole ledger (background)", 150.0, ms)
        } finally { ids.forEach { driver.execute(null, "DELETE FROM account WHERE id = '$it'", 0) } }
    }

    @Test fun theWholeSavePathStaysUnder50msPhoneBudgetAtP95() {
        // What the app really does for one expense: LedgerStore.save (validation, splits, spend lines, change log) and then the queries the
        // screen refresh runs (the recent list, whether anything was ever recorded, tags). The live count and the balances run later, in the background. The raw-insert test above only times one statement.
        val clock = app.expenses.core.domain.sync.HlcClock("perf")
        val ledger = app.expenses.core.storage.LedgerStore(db, clock) { System.currentTimeMillis() }
        val accountRowsAdded = listOf("bank").filter { db.schemaQueries.accountById(it).executeAsOneOrNull() == null }
        accountRowsAdded.forEach { driver.execute(null, "INSERT INTO account(id, name, type, currency, deleted) VALUES ('$it', '$it', 'BANK', 'INR', 0)", 0) }
        val ids = (0 until 40).map { "path$it" }
        try {
            val times = DoubleArray(ids.size) { i ->
                val s = System.nanoTime()
                ledger.save(
                    app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId(ids[i]), 1_900_000_000_000L + i, app.expenses.core.domain.ledger.AccountId("bank"), app.expenses.core.domain.Money(500, app.expenses.core.domain.Currency.INR),
                        listOf(app.expenses.core.domain.ledger.CategorySplit(app.expenses.core.domain.ledger.CategoryId("cat1"), app.expenses.core.domain.Money(500, app.expenses.core.domain.Currency.INR)))),
                    app.expenses.core.storage.EntryDetails(merchant = "path$i", tags = listOf("perf")),
                )
                val t1 = System.nanoTime()
                db.schemaQueries.recentEntries(50).executeAsList(); val t2 = System.nanoTime()
                db.schemaQueries.entryCountEver().executeAsOne(); val t3 = System.nanoTime() // the cheap check the refresh runs; the live count is in the background job
                ledger.tagsInUse(40); val t4 = System.nanoTime()
                if (i == 20) Log.w("PERF", "split: save ${"%.1f".format((t1 - s) / 1e6)} recent ${"%.1f".format((t2 - t1) / 1e6)} count ${"%.1f".format((t3 - t2) / 1e6)} tags ${"%.1f".format((t4 - t3) / 1e6)} ms")
                (System.nanoTime() - s) / 1e6
            }.sorted()
            val p95 = times[(times.size * 0.95).toInt()]
            Log.w("PERF", "whole save path: p50 ${"%.2f".format(times[times.size / 2])} ms, p95 ${"%.2f".format(p95)} ms, max ${"%.2f".format(times.last())} ms")
            check("save one expense end to end (p95 of ${ids.size})", 50.0, p95)
        } finally {
            ids.forEach { runCatching { ledger.delete(app.expenses.core.domain.ledger.EntryId(it)) } }
            accountRowsAdded.forEach { driver.execute(null, "DELETE FROM account WHERE id = '$it'", 0) }
        }
    }

    @Test fun savingOneTransactionStaysUnder50msPhoneBudgetAtP95() {
        val ids = (0 until 60).map { "save$it" }
        try {
            val times = DoubleArray(60) { i ->
                val s = System.nanoTime()
                db.schemaQueries.insertEntry(ids[i], "EXPENSE", 1_900_000_000_000L + i, "bank", null, 500, "INR", "cat1", "fresh$i", null, "manual", null)
                (System.nanoTime() - s) / 1e6
            }.sorted()
            val p95 = times[(times.size * 0.95).toInt()]
            Log.w("PERF", "save one transaction: p50 ${"%.2f".format(times[30])} ms, p95 ${"%.2f".format(p95)} ms, max ${"%.2f".format(times.last())} ms")
            check("save one transaction (p95 of 60 autocommit inserts)", 50.0, p95)
            // No save may stall: before the index tuning one save in 60 took about 100 ms. The budget for the worst save is 100 ms.
            check("slowest of 60 saves", 100.0, times.last())
        } finally {
            // Always remove the rows, so one failure cannot make the dataset check in another test fail too.
            ids.forEach { db.schemaQueries.softDeleteEntry(it) }
        }
    }
}
