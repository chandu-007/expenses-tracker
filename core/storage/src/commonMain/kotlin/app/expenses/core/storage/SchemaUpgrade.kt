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
        if ("kind" !in columns(driver, "category")) {
            run(driver, "ALTER TABLE category ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE'")
            // a category an income already uses stays available for income as well, so no existing entry loses its category
            run(driver, "UPDATE category SET kind = 'BOTH' WHERE id IN (SELECT DISTINCT category_id FROM entry WHERE kind = 'INCOME' AND category_id IS NOT NULL)")
        }
        if ("kind" !in columns(driver, "recurring")) run(driver, "ALTER TABLE recurring ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXPENSE'")
        if ("to_account_id" !in columns(driver, "recurring")) run(driver, "ALTER TABLE recurring ADD COLUMN to_account_id TEXT")
        run(driver, "CREATE INDEX IF NOT EXISTS entry_account_time_seq ON entry(account_id, occurred_at DESC, seq DESC) WHERE deleted = 0")
        run(driver, "CREATE INDEX IF NOT EXISTS entry_counter_time ON entry(counter_account_id, occurred_at DESC, seq DESC) WHERE counter_account_id IS NOT NULL AND deleted = 0")
        run(driver, "CREATE INDEX IF NOT EXISTS entry_account_sums ON entry(account_id, kind, direction, amount_minor) WHERE deleted = 0")
        run(driver, "CREATE INDEX IF NOT EXISTS entry_transfer_in ON entry(counter_account_id, to_amount_minor) WHERE kind = 'TRANSFER' AND deleted = 0")
        run(driver, "CREATE TABLE IF NOT EXISTS attachment (id TEXT NOT NULL PRIMARY KEY, entry_id TEXT NOT NULL, name TEXT NOT NULL, mime TEXT NOT NULL, size_bytes INTEGER NOT NULL, sha256 TEXT NOT NULL, created_at INTEGER NOT NULL)")
        run(driver, "CREATE INDEX IF NOT EXISTS attachment_entry ON attachment(entry_id)")
        run(driver, "CREATE TABLE IF NOT EXISTS saved_search (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, text TEXT NOT NULL DEFAULT '', min_rupees TEXT NOT NULL DEFAULT '', max_rupees TEXT NOT NULL DEFAULT '', category_id TEXT, kind TEXT NOT NULL DEFAULT 'ANY', range_kind TEXT NOT NULL DEFAULT 'ANY', tag TEXT, deleted INTEGER NOT NULL DEFAULT 0)")
        if ("from_day" !in columns(driver, "saved_search")) run(driver, "ALTER TABLE saved_search ADD COLUMN from_day INTEGER")
        if ("to_day" !in columns(driver, "saved_search")) run(driver, "ALTER TABLE saved_search ADD COLUMN to_day INTEGER")
        if ("account_id" !in columns(driver, "saved_search")) run(driver, "ALTER TABLE saved_search ADD COLUMN account_id TEXT")
        run(driver, "CREATE TABLE IF NOT EXISTS inbox (id TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, sender TEXT NOT NULL, issuer TEXT NOT NULL, received_at INTEGER NOT NULL, direction TEXT NOT NULL, amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, txn_date TEXT, reference TEXT, account_last4 TEXT, via_upi INTEGER NOT NULL DEFAULT 0, confidence TEXT NOT NULL, body TEXT, status TEXT NOT NULL DEFAULT 'PENDING', entry_id TEXT, fingerprint TEXT NOT NULL UNIQUE)")
        run(driver, "CREATE INDEX IF NOT EXISTS inbox_status ON inbox(status, received_at DESC)")
        run(driver, "CREATE TABLE IF NOT EXISTS capture_rule (id TEXT NOT NULL PRIMARY KEY, position INTEGER NOT NULL, pattern TEXT NOT NULL, category_id TEXT NOT NULL)")
        run(driver, "CREATE TABLE IF NOT EXISTS merchant_map (key TEXT NOT NULL PRIMARY KEY, category_id TEXT NOT NULL, uses INTEGER NOT NULL DEFAULT 1)")
        run(driver, "CREATE TABLE IF NOT EXISTS capture_event (at INTEGER NOT NULL, sender_root TEXT NOT NULL, outcome TEXT NOT NULL)")
        run(driver, "CREATE INDEX IF NOT EXISTS capture_event_at ON capture_event(at)")
        run(driver, "CREATE TABLE IF NOT EXISTS card_terms (account_id TEXT NOT NULL PRIMARY KEY, statement_day INTEGER NOT NULL, due_day INTEGER NOT NULL, min_due_percent INTEGER NOT NULL DEFAULT 5, remind_days INTEGER NOT NULL DEFAULT 3)")
        run(driver, "CREATE TABLE IF NOT EXISTS account_look (account_id TEXT NOT NULL PRIMARY KEY, network TEXT, tier TEXT, last4 TEXT)")
        run(driver, "CREATE INDEX IF NOT EXISTS account_look_last4 ON account_look(last4) WHERE last4 IS NOT NULL")
        run(driver, "CREATE TABLE IF NOT EXISTS entry_tag (entry_id TEXT NOT NULL, tag TEXT NOT NULL COLLATE NOCASE, PRIMARY KEY (entry_id, tag)) WITHOUT ROWID")
        run(driver, "CREATE INDEX IF NOT EXISTS entry_deleted_ids ON entry(id) WHERE deleted = 1")
        run(driver, "CREATE INDEX IF NOT EXISTS entry_tag_tag ON entry_tag(tag, entry_id)")
        run(driver, "CREATE INDEX IF NOT EXISTS entry_loans ON entry(person_id, currency, direction, amount_minor, occurred_at) WHERE kind = 'SETTLEMENT' AND deleted = 0")
        run(driver, "CREATE TABLE IF NOT EXISTS bill (id TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, sender TEXT NOT NULL, issuer TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, amount_minor INTEGER NOT NULL, min_due_minor INTEGER, currency TEXT NOT NULL, due_date TEXT, account_last4 TEXT, reference TEXT, auto_debit INTEGER NOT NULL DEFAULT 0, received_at INTEGER NOT NULL, body TEXT, status TEXT NOT NULL DEFAULT 'FOUND', entry_id TEXT, paid_at INTEGER, dedupe_key TEXT NOT NULL UNIQUE)")
        run(driver, "CREATE INDEX IF NOT EXISTS bill_status ON bill(status, due_date)")
        run(driver, "CREATE TABLE IF NOT EXISTS person (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, deleted INTEGER NOT NULL DEFAULT 0)")
        run(driver, "CREATE TABLE IF NOT EXISTS entry_origin (entry_id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, sender TEXT, text TEXT NOT NULL, received_at INTEGER)")
    }

    private fun run(driver: SqlDriver, sql: String) { driver.execute(null, sql, 0) }

    private fun columns(driver: SqlDriver, table: String): Set<String> =
        driver.executeQuery(null, "PRAGMA table_info($table)", { c ->
            val names = HashSet<String>()
            while (c.next().value) c.getString(1)?.let(names::add)
            QueryResult.Value(names)
        }, 0).value
}
