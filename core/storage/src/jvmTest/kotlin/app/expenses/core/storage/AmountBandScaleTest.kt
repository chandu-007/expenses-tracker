package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.AmountBands
import app.expenses.core.domain.PayeeAlias
import app.expenses.core.domain.PayeeAliases
import app.expenses.core.domain.PayeeKey
import app.expenses.core.domain.SimilarCandidate
import app.expenses.core.domain.SimilarEntries
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the amount-aware suggestion costs on a 20,000-entry ledger. The evidence for a payee is the indexed lookup of its own words (the same one the history suggestion always used, the 300 newest
 * entries), so a lookup reads only that payee's rows, not the ledger; the band is arithmetic over those. Figures are printed; the budgets are generous (shared machines). All names invented.
 */
class AmountBandScaleTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)

    private fun candidates(label: String): List<SimilarCandidate> = db.payeeCandidates(label, "EXPENSE")

    @Test
    fun aLookupForOnePayeeReadsOnlyThatPayeesRowsAndIsFastOnTwentyThousandEntries() {
        val rnd = Random(21)
        // 20,000 entries in date order: 1,200 Amazon in three kinds of amount, 40 Rare Shop in two, the rest 400 other shops
        var rare = 0; var amazon = 0
        db.bulkLoad(driver) {
            for (i in 0 until 20_000) {
                val (merchant, amount, cat) = when {
                    i % 16 == 0 && amazon < 1_200 -> { amazon++; when (rnd.nextInt(3)) { 0 -> Triple("Amazon", 19_900L + rnd.nextInt(2_000) - 1_000, "Subscriptions"); 1 -> Triple("Amazon", 249_900L + rnd.nextInt(60_000) - 30_000, "Shopping"); else -> Triple("AMAZON PAY INDIA", 59_900L + rnd.nextInt(6_000), "Bills") } }
                    i % 500 == 3 && rare < 40 -> { rare++; if (rare % 2 == 0) Triple("Rare Shop", 4_000L + rnd.nextInt(500), "Food") else Triple("Rare Shop", 90_000L + rnd.nextInt(5_000), "Groceries") }
                    else -> Triple("Shop number ${rnd.nextInt(400)}", 100L + rnd.nextInt(90_000), "Misc")
                }
                db.schemaQueries.insertEntry("e$i", "EXPENSE", 1_000L + i, "bank", null, amount, "INR", null, merchant, null, "manual", null)
                db.schemaQueries.insertSplit("e$i", 0, cat, amount)
            }
        }
        assertEquals(20_000L, db.schemaQueries.entryCount().executeAsOne())

        // only the payee's own rows come back, never the ledger
        val rareRows = candidates("Rare Shop"); val amazonRows = candidates("Amazon")
        assertEquals(rare, rareRows.count { it.label == "Rare Shop" }, "Rare Shop: all of its own $rare entries are found out of 20,000, although its word 'shop' is in thousands of others (the all-words lookup)")
        assertTrue(rareRows.size <= 2 * PAYEE_CANDIDATE_CAP, "never more than the two capped lookups, not the ledger")
        assertEquals(PAYEE_CANDIDATE_CAP, amazonRows.size, "a heavy payee is capped at the 300 newest")
        assertTrue(amazonRows.all { SimilarEntries.matches("Amazon", it.label) })

        // lookups
        val aliases = PayeeAliases(listOf(PayeeAlias(PayeeKey.of("Shop number 7")!!, PayeeKey.of("Shop number 8")!!, "Shop number 7", "Shop number 8")))
        val times = ArrayList<Long>()
        var spoke = 0
        repeat(300) { i ->
            val label = if (i % 3 == 0) "Amazon" else if (i % 3 == 1) "Rare Shop" else "Shop number ${i % 400}"
            val amount = if (i % 3 == 1) 4_100L else 19_900L
            times += measureNanoTime {
                val rows = candidates(label)
                SimilarEntries.profile(label, rows, aliases)
                if (AmountBands.of(label, amount, "INR", rows, aliases) != null) spoke++
            }
        }
        times.sort()
        val median = times[times.size / 2] / 1_000_000.0; val p95 = times[times.size * 95 / 100] / 1_000_000.0
        println("AMOUNT-BAND-SCALE 20,000 entries: a payee lookup (indexed rows + history profile + amount band) median ${"%.2f".format(median)} ms, p95 ${"%.2f".format(p95)} ms over ${times.size}; Rare Shop read ${rareRows.size} rows, Amazon ${amazonRows.size} (cap 300); the band spoke for $spoke")
        assertTrue(spoke > 0)
        assertTrue(p95 < 150.0, "p95 of a payee lookup was $p95 ms")
        assertNotNull(AmountBands.of("Amazon", 19_900, "INR", amazonRows)); assertNotNull(AmountBands.of("Rare Shop", 4_100, "INR", rareRows))
        assertEquals("Subscriptions", AmountBands.of("Amazon", 19_900, "INR", amazonRows)!!.top)
        assertEquals("Shopping", AmountBands.of("Amazon", 249_900, "INR", amazonRows)!!.top)
        assertEquals("Food", AmountBands.of("Rare Shop", 4_100, "INR", rareRows)!!.top)
        assertEquals("Groceries", AmountBands.of("Rare Shop", 91_000, "INR", rareRows)!!.top)
    }
}
