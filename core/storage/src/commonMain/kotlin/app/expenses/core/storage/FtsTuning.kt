package app.expenses.core.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Keeps full-text index maintenance off the save path.
 *
 * By default SQLite's FTS5 merges index segments as part of a commit, and on a 100,000-row ledger with the trigram
 * index one save in about 60 took roughly 100 ms (always the same one, save number 11, on an Android 8 emulator with
 * SQLCipher). With merging switched off every save stayed at 1 ms or less (95th percentile 0.6 ms, slowest of 1,000
 * saves 5 ms). The price is that each commit leaves one more small segment for searches to read: with 1,000 unmerged
 * segments a common search went from 8.2 ms to 9.8 ms and a rare one from 12.2 ms to 15.3 ms. [mergeStep] merges them
 * later, in small steps (at most about 67 ms each), when the app is idle.
 *
 * [apply] only switches off the automatic merge and raises the "crisis" threshold, at which SQLite merges anyway to
 * protect search speed. The settings are stored inside the database, so they persist.
 */
object FtsTuning {
    /** Segments allowed before SQLite merges on its own regardless; a safety net that is rarely reached. */
    const val CRISIS_MERGE_SEGMENTS = 1000

    /** Idempotent: writes nothing if the settings are already in place, so opening the app causes no write. */
    fun apply(driver: SqlDriver) {
        val current = driver.executeQuery(null, "SELECT k, v FROM entry_fts_config WHERE k IN ('automerge', 'crisismerge')", { c ->
            val m = HashMap<String, Long>()
            while (c.next().value) m[c.getString(0)!!] = c.getLong(1) ?: -1
            QueryResult.Value(m)
        }, 0).value
        if (current["automerge"] != 0L) driver.execute(null, "INSERT INTO entry_fts(entry_fts, rank) VALUES ('automerge', 0)", 0)
        if (current["crisismerge"] != CRISIS_MERGE_SEGMENTS.toLong()) driver.execute(null, "INSERT INTO entry_fts(entry_fts, rank) VALUES ('crisismerge', $CRISIS_MERGE_SEGMENTS)", 0)
    }

    /**
     * Merges up to [pages] pages of index segments. Returns true if it did work, meaning calling it again may do more;
     * false once the index is fully merged. Each call is short, so callers can run steps between other writes.
     */
    fun mergeStep(driver: SqlDriver, pages: Int = 200): Boolean {
        require(pages > 0) { "pages must be positive" }
        val before = totalChanges(driver)
        driver.execute(null, "INSERT INTO entry_fts(entry_fts, rank) VALUES ('merge', $pages)", 0)
        // FTS5 documents that merging is complete once a step changes fewer than two rows.
        return totalChanges(driver) - before >= 2
    }

    private fun totalChanges(driver: SqlDriver): Long =
        driver.executeQuery(null, "SELECT total_changes()", { c -> c.next(); QueryResult.Value(c.getLong(0) ?: 0L) }, 0).value
}
