package app.expenses.core.storage

import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.storage.db.ExpensesDb

/**
 * Runs [block] (typically thousands of `insertEntry` calls) as one atomic transaction with full-text
 * indexing suspended, then rebuilds the index once (about 0.12 s for 100k rows on a desktop JVM).
 * If [block] throws, the rows, the suspension flag and the index all roll back together.
 *
 * Measured on a desktop JVM with the JDBC driver: 100k inserts took about 7.5 s with per-row index
 * triggers and about 5.5 s through this helper. Most of the remaining cost is the mere presence of the
 * triggers (1.5 s when they are dropped entirely), probably because that driver recompiles them per
 * statement. Re-measure on Android with SQLCipher before deciding whether importers need to drop the
 * triggers instead of only suspending them.
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
