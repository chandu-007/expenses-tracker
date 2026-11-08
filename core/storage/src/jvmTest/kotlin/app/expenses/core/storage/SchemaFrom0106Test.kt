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
 * A database made by 0.10.6 (the commit before the work of Slice 104; its CREATE statements are `schema-0.10.6.sql`, taken from that commit) opens with the current code, needs no migration (the work added
 * queries only: no table, column or index changed), keeps every row it had, and works with the new queries and with the move of a bill made from a payment's confirmation. Rows, texts and amounts are invented.
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
    fun the0106DatabaseHasExactlyTheSchemaTheCurrentCodeCreatesSoNothingIsMigrated() {
        val old = baseline(); val now = fresh()
        assertTrue(shape(old).size > 200, "the baseline was read: ${shape(old).size} objects and columns")
        assertEquals(shape(now), shape(old), "no table, column, index or trigger changed since 0.10.6")
        val d = JdbcSqliteDriver("jdbc:sqlite:${old.path}")
        SchemaUpgrade.ensureLatest(d); SchemaUpgrade.ensureLatest(d); d.close()
        assertEquals(shape(now), shape(old), "the upgrade step changes nothing on it, run once or twice")
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
