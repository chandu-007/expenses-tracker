package app.expenses

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * A cheap fingerprint of "has anything written to the database since I last looked": two small reads, no table scan.
 *
 * [changes] is SQLite's `total_changes()` of this connection: it moves on every row written through it, by anyone in this process (the screen, a home-screen widget's quick add or Undo, a captured
 * text, a notification button). [version] is `PRAGMA data_version`, which moves when ANOTHER connection (another process, such as an iPhone share extension) commits. Together they say whether the
 * screen's picture of the ledger can be out of date, without rebuilding it to find out.
 */
data class DataSignature(val changes: Long, val version: Long) {
    companion object {
        fun read(driver: SqlDriver): DataSignature = DataSignature(
            changes = driver.executeQuery(null, "SELECT total_changes()", { c -> c.next(); QueryResult.Value(c.getLong(0) ?: 0L) }, 0).value,
            version = driver.executeQuery(null, "PRAGMA data_version", { c -> c.next(); QueryResult.Value(c.getLong(0) ?: 0L) }, 0).value,
        )
    }
}
