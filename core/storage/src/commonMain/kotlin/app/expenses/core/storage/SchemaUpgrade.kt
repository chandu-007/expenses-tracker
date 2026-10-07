package app.expenses.core.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/**
 * Brings a database created by an earlier build of the app up to the current shape, without losing anything in it. The schema version has
 * not been raised yet because the app has not been released, so a database made before a column or table was added would otherwise fail
 * on its first query. Every step checks first, so it is safe to run on every open and on a database that is already current.
 *
 * From the first store release on, schema changes are real SQLDelight migrations (`.sqm` files) and this class stops growing.
 */
object SchemaUpgrade {
    fun ensureLatest(driver: SqlDriver) {
        if ("opening_minor" !in columns(driver, "account")) run(driver, "ALTER TABLE account ADD COLUMN opening_minor INTEGER NOT NULL DEFAULT 0")
        if ("archived" !in columns(driver, "account")) run(driver, "ALTER TABLE account ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        run(driver, "CREATE TABLE IF NOT EXISTS entry_tag (entry_id TEXT NOT NULL, tag TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY (entry_id, tag)) WITHOUT ROWID")
        run(driver, "CREATE INDEX IF NOT EXISTS entry_tag_tag ON entry_tag(tag, entry_id)")
    }

    private fun run(driver: SqlDriver, sql: String) { driver.execute(null, sql, 0) }

    private fun columns(driver: SqlDriver, table: String): Set<String> =
        driver.executeQuery(null, "PRAGMA table_info($table)", { c ->
            val names = HashSet<String>()
            while (c.next().value) c.getString(1)?.let(names::add)
            QueryResult.Value(names)
        }, 0).value
}
