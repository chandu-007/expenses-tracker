package app.expenses

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.bulkLoad
import app.expenses.core.storage.db.ExpensesDb
import app.expenses.core.storage.search
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

        /** Pronounceable-looking unique names with no shared substrings, so frequency is controlled by [merchantFor]. */
        private fun merchantName(index: Int): String =
            java.lang.Long.toString((index + 1L) * 2_654_435_761L % 2_176_782_336L, 36).padStart(6, 'a')

        @BeforeClass @JvmStatic fun load() {
            EncryptedDriver.reset(context, NAME)
            driver = EncryptedDriver.create(context, NAME)
            db = ExpensesDb(driver)
            val rnd = Random(7)
            db.bulkLoad(driver) { // importers insert in ascending date order
                for (i in 0 until ROWS) {
                    // log-uniform pick: merchant 0 gets ln(2)/ln(500), about 11% of rows
                    val m = (MERCHANTS.toDouble().pow(rnd.nextDouble())).toInt() - 1
                    matchCount[m]++
                    val at = START + i * (TEN_YEARS_MS / ROWS)
                    db.schemaQueries.insertEntry("e$i", "EXPENSE", at, "bank", null, 100L + i % 900, "INR", "cat${i % 40}", merchantName(m), null, "manual", null)
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

    @Test fun datasetIsAsDescribed() {
        assertEquals(ROWS.toLong(), db.schemaQueries.entryCount().executeAsOne())
        val share = matchCount[0] / ROWS.toDouble()
        assertTrue(share in 0.09..0.13, "most common merchant share was $share")
    }

    @Test fun searchForTheMostCommonMerchantStaysUnder100msPhoneBudget() {
        assertEquals(50, db.search(merchantName(0)).size)
        check("search, most common merchant (~11% of rows)", 100.0, median { db.search(merchantName(0)) })
    }

    @Test fun searchForRareAndMissingTermsStaysUnder100msPhoneBudget() {
        val rare = (0 until MERCHANTS).filter { matchCount[it] > 0 }.minByOrNull { matchCount[it] }!!
        assertEquals(matchCount[rare], db.search(merchantName(rare), 1000).size)
        check("search, rare merchant (${matchCount[rare]} rows)", 100.0, median { db.search(merchantName(rare)) })
        assertEquals(0, db.search("zzzzzz").size)
        check("search, no match", 100.0, median { db.search("zzzzzz") })
    }

    @Test fun categoryTotalsForOneYearStayUnder300msPhoneBudget() {
        val until = START + 365 * DAY_MS
        val inWindow = db.schemaQueries.spendByCategory("INR", START, until).executeAsList()
        assertEquals(40, inWindow.size)
        val rowsInWindow = (until - START) / (TEN_YEARS_MS / ROWS)
        assertTrue(rowsInWindow in 9_900..10_100, "window holds $rowsInWindow rows")
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

    @Test fun savingOneTransactionStaysUnder50msPhoneBudgetAtP95() {
        val times = DoubleArray(60) { i ->
            val s = System.nanoTime()
            db.schemaQueries.insertEntry("save$i", "EXPENSE", 1_900_000_000_000L + i, "bank", null, 500, "INR", "cat1", "fresh$i", null, "manual", null)
            (System.nanoTime() - s) / 1e6
        }.sorted()
        val p95 = times[(times.size * 0.95).toInt()]
        check("save one transaction (p95 of 60 autocommit inserts)", 50.0, p95)
        times.indices.forEach { db.schemaQueries.softDeleteEntry("save$it") }
    }
}
