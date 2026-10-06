package app.expenses.core.storage

import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.storage.db.ExpensesDb

/**
 * Runs [block] (typically thousands of `insertEntry` calls) as one atomic transaction with full-text
 * indexing suspended, then rebuilds the index once. If [block] throws, the rows, the suspension flag
 * and the index all roll back together.
 *
 * **Importers must insert in ascending `occurred_at` order.** Measured for 100k rows on an Android
 * emulator with SQLCipher and every index in place: about 2.2 s in date order, about 20 s in random
 * date order, because each insert then lands in a random place in several B-trees.
 *
 * The same run took about 3.6 s with the per-row index triggers active and about 1.6 s if the triggers
 * were dropped and recreated inside the transaction, so suspending (no DDL) is the better trade. Desktop
 * JDBC numbers were different (7.5 s, 5.5 s and 1.5 s) and should not be used for decisions.
 *
 * [driver] must be the driver [ExpensesDb] was created with. The FTS5 'rebuild' command is issued as raw
 * SQL because SQLDelight's compiler cannot type-check inserts into the hidden FTS5 command column.
 */
fun <T> ExpensesDb.bulkLoad(driver: SqlDriver, block: () -> T): T = transactionWithResult {
    schemaQueries.suspendFts()
    val result = block()
    schemaQueries.resumeFts()
    driver.execute(null, "INSERT INTO entry_fts(entry_fts) VALUES ('rebuild')", 0)
    result
}
