package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A database made by 0.10.6 (the commit before the work of Slice 104; its CREATE statements are `schema-0.10.6.sql`, taken from that commit) opens with the current code, keeps every row it had, and works
 * with the new queries and with the move of a bill made from a payment's confirmation. The schema changes since are additive: the small table `entry_replaced` (the record of entries a type change
 * replaced, Slice 109) and the small table `trip` with its one index (Travel mode, Slice 110), which `SchemaUpgrade.ensureLatest` creates; no other table, column or index changed. Rows, texts and amounts are invented.
 */
class SchemaFrom0106Test {
    private val statements = checkNotNull(javaClass.classLoader.getResourceAsStream("schema-0.10.6.sql")).readBytes().decodeToString().split("-- @@").drop(1).map { it.trim() }.filter { it.isNotEmpty() }

    private fun baseline(): File = File.createTempFile("expenses-0106", ".db").also { f ->
        f.deleteOnExit()
        DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c -> c.createStatement().use { st -> statements.forEach { st.execute(it) } } }
    }

    private fun fresh(): File = File.createTempFile("expenses-now", ".db").also { f ->
        f.deleteOnExit()
        val d = JdbcSqliteDriver("jdbc:sqlite:${f.path}")
        ExpensesDb.Schema.create(d); d.close()
    }

    /** Every table, index and trigger by name, and every column of every table with its type, whether it may be empty and whether it is the key: what "the same schema" means. */
    private fun shape(f: File): List<String> = DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
        val out = ArrayList<String>()
        fun rows(sql: String, read: (java.sql.ResultSet) -> String) = c.createStatement().use { st -> st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(read(rs)) } } }
        for (entry in rows("SELECT type || ':' || name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name") { it.getString(1) }) {
            val type = entry.substringBefore(':'); val name = entry.substringAfter(':')
            out += "$type $name"
            if (type == "table") rows("PRAGMA table_info('$name')") { "$name.${it.getString("name")} ${it.getString("type")} notnull=${it.getInt("notnull")} pk=${it.getInt("pk")}" }.forEach { out += it }
        }
        out
    }

    private fun exec(c: Connection, sql: String) = c.createStatement().use { it.execute(sql) }

    @Test
    fun the0106DatabaseDiffersFromTheCurrentSchemaOnlyByTheTablesOfReplacedEntriesAndOfTripsAndTheUpgradeStepAddsExactlyThat() {
        val old = baseline(); val now = fresh()
        assertTrue(shape(old).size > 200, "the baseline was read: ${shape(old).size} objects and columns")
        val replaced = { s: String -> s.startsWith("table entry_replaced") || s.startsWith("entry_replaced.") }
        val trip = { s: String -> s.startsWith("table trip") || s.startsWith("trip.") || s == "index trip_days" }
        assertEquals(shape(now).filterNot(replaced).filterNot(trip), shape(old), "no other table, column, index or trigger changed since 0.10.6")
        assertEquals(listOf("table trip", "trip.id TEXT notnull=1 pk=1", "trip.name TEXT notnull=1 pk=0", "trip.tag TEXT notnull=1 pk=0", "trip.start_day INTEGER notnull=1 pk=0", "trip.end_day INTEGER notnull=1 pk=0", "trip.enabled INTEGER notnull=1 pk=0", "trip.daily_budget_minor INTEGER notnull=0 pk=0"), shape(now).filter { it.startsWith("table trip") || it.startsWith("trip.") }, "Travel mode is one small table")
        assertTrue("index trip_days" in shape(now))
        assertEquals(listOf("table entry_replaced", "entry_replaced.entry_id TEXT notnull=1 pk=1", "entry_replaced.replaced_by TEXT notnull=1 pk=0", "entry_replaced.reason TEXT notnull=1 pk=0"), shape(now).filter(replaced), "the record of replaced entries is one small table")
        val d = JdbcSqliteDriver("jdbc:sqlite:${old.path}")
        SchemaUpgrade.ensureLatest(d); SchemaUpgrade.ensureLatest(d); d.close()
        assertEquals(shape(now), shape(old), "the upgrade step makes it the same schema, run once or twice")
    }

    @Test
    fun aDatabaseOf0106GetsTheTableAndTheRecordOfAReplacedEntryWorksOnItWithItsRowsIntact() {
        val f = baseline()
        DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
            exec(c, "INSERT INTO currency(code, digits) VALUES ('INR', 2)")
            exec(c, "INSERT INTO account(id, name, type, currency, deleted, opening_minor, archived) VALUES ('main', 'Main', 'BANK', 'INR', 0, 0, 0)")
            exec(c, "INSERT INTO account(id, name, type, currency, deleted, opening_minor, archived) VALUES ('cash', 'Cash', 'CASH', 'INR', 0, 0, 0)")
            exec(c, "INSERT INTO entry(id, kind, occurred_at, account_id, amount_minor, currency, merchant, source, source_ref, deleted) VALUES ('e1', 'EXPENSE', 5000, 'main', 250000, 'INR', 'ATM', 'sms', 'ref-1', 0)")
        }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${f.path}")
        SchemaUpgrade.ensureLatest(driver)
        val db = ExpensesDb(driver); val clock = app.expenses.core.domain.sync.HlcClock("n")
        val ledger = LedgerStore(db, clock) { 1L }
        assertEquals(emptyList(), db.schemaQueries.allReplaced().executeAsList(), "nothing was replaced on a database from before")
        val changes = KindChanges(db, ledger, OriginStore(db), ExpectStore(db), AttachmentStore(db, clock) { 1L })
        val c = changes.change("e1", app.expenses.core.domain.ledger.Transfer(app.expenses.core.domain.ledger.EntryId("t1"), 5000, app.expenses.core.domain.ledger.AccountId("main"), app.expenses.core.domain.ledger.AccountId("cash"), app.expenses.core.domain.Money(250000, app.expenses.core.domain.Currency.INR)), "ATM", emptyList())
        assertEquals("t1", ledger.replaced.by("e1")); assertEquals(1, db.schemaQueries.entryCount().executeAsOne().toInt())
        changes.undo(c)
        assertNull(ledger.replaced.by("e1")); assertEquals("e1", db.schemaQueries.recentEntries(10).executeAsList().single().id); assertEquals("ref-1", db.schemaQueries.entryById("e1").executeAsOne().source_ref)
        driver.close()
    }

    @Test
    fun itsRowsSurviveAndTheNewQueriesAndTheMoveOfAWrongBillWorkOnIt() {
        val f = baseline()
        val text = "Hi Asha, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811"
        DriverManager.getConnection("jdbc:sqlite:${f.path}").use { c ->
            exec(c, "INSERT INTO currency(code, digits) VALUES ('INR', 2)")
            exec(c, "INSERT INTO account(id, name, type, currency, deleted, opening_minor, archived) VALUES ('main', 'Main', 'BANK', 'INR', 0, 0, 0)")
            exec(c, "INSERT INTO entry(id, kind, occurred_at, account_id, amount_minor, currency, merchant, source, source_ref, deleted) VALUES ('e1', 'EXPENSE', 5000, 'main', 250000, 'INR', 'Shop', 'sms', 'ref-1', 0)")
            exec(c, "INSERT INTO entry_origin(entry_id, kind, sender, text, received_at) VALUES ('e1', 'SMS', 'AX-HDFCBK-S', 'Rs.2,500.00 debited', 5000)")
            exec(c, "INSERT INTO bill(id, source, sender, issuer, kind, title, amount_minor, min_due_minor, currency, due_date, account_last4, reference, auto_debit, received_at, body, status, entry_id, paid_at, dedupe_key) " +
                "VALUES ('wrong', 'SMS', 'AX-KOTAKB-S', 'Kotak Mahindra Bank', 'CREDIT_CARD', 'Kotak Mahindra Bank credit card', 1234567, NULL, 'INR', NULL, NULL, NULL, 0, 1000, '$text', 'FOUND', NULL, NULL, 'k-wrong')")
            exec(c, "INSERT INTO bill(id, source, sender, issuer, kind, title, amount_minor, min_due_minor, currency, due_date, account_last4, reference, auto_debit, received_at, body, status, entry_id, paid_at, dedupe_key) " +
                "VALUES ('real', 'SMS', 'AX-AXISBK-S', 'Axis Bank', 'CREDIT_CARD', 'Axis Bank credit card 4321', 1234567, 61728, 'INR', '2026-11-25', '4321', NULL, 0, 1000, 'statement words', 'TRACKED', NULL, NULL, 'k-real')")
        }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${f.path}")
        SchemaUpgrade.ensureLatest(driver)
        val db = ExpensesDb(driver); val q = db.schemaQueries
        val bills = BillStore(db); val inbox = InboxStore(db)
        assertEquals(1, q.entryCount().executeAsOne().toInt()); assertEquals("Shop", q.recentEntries(10).executeAsList().single().merchant)
        assertEquals(text, bills.get("wrong")!!.body); assertEquals(BillStatus.TRACKED, bills.get("real")!!.status)
        // the new queries read the old rows
        assertEquals(listOf("e1"), q.entriesNearBillPayment(250_000, "INR", 0, 10_000).executeAsList().map { it.id })
        assertEquals(emptyList(), q.transferLegsNear(250_000, "INR", 0, 10_000).executeAsList())
        // the move of the wrong bill, and Undo, on the old database
        val repairs = BillRepairs(db, inbox, bills)
        val moved = repairs.move(bills.get("wrong")!!, InboxItem("p1", "SMS", "AX-KOTAKB-S", "Kotak Mahindra Bank", 1_000, "DEBIT", 1_234_567, "INR", null, null, null, false, "GENERIC", text, InboxStatus.PENDING, null), "fp")
        assertEquals(BillStatus.DISMISSED, bills.get("wrong")!!.status); assertEquals(text, inbox.get("p1")!!.body)
        assertEquals(BillStatus.TRACKED, bills.get("real")!!.status, "no other bill was touched"); assertEquals(1, q.entryCount().executeAsOne().toInt())
        repairs.undo(moved)
        assertEquals(BillStatus.FOUND, bills.get("wrong")!!.status); assertEquals(text, bills.get("wrong")!!.body); assertNull(inbox.get("p1"), "the untouched item the move made is taken away again")
        // a bill paid by an entry and put back exactly
        assertTrue(bills.markPaid("real", "e1", 9_000)); assertTrue(bills.unpayTo("real", BillStatus.TRACKED, "statement words"))
        assertEquals("statement words", bills.get("real")!!.body); assertNull(bills.get("real")!!.entryId)
        driver.close()
    }
}
