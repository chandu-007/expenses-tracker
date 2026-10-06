package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageTest {
    private fun newDb(): Pair<JdbcSqliteDriver, ExpensesDb> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ExpensesDb.Schema.create(driver)
        return driver to ExpensesDb(driver)
    }

    private fun ExpensesDb.entry(id: String, at: Long, amount: Long, cat: String? = "food", merchant: String? = null, ref: String? = null, source: String = "manual") =
        schemaQueries.insertEntry(id, "EXPENSE", at, "bank", null, amount, "INR", cat, merchant, null, source, ref)

    @Test
    fun spendByCategorySumsOnlyMatchingNonDeletedExpensesInRange() {
        val (_, db) = newDb()
        db.entry("a", 100, 500, "food")
        db.entry("b", 200, 700, "food")
        db.entry("c", 300, 900, "travel")
        db.entry("d", 999, 111, "food") // outside range
        val rows = db.schemaQueries.spendByCategory("INR", 0, 500).executeAsList().associate { it.category_id to it.total }
        assertEquals(mapOf<String?, Long?>("food" to 1200L, "travel" to 900L), rows)
    }

    @Test
    fun sameSourceReferenceFromAnotherIdFailsAndKeepsTheOriginalRow() {
        val (_, db) = newDb()
        db.entry("a", 1, 100, ref = "UPI123", source = "sms")
        assertFailsWith<Exception> { db.entry("b", 2, 999, ref = "UPI123", source = "sms") }
        assertEquals(1L, db.schemaQueries.entryCount().executeAsOne())
        val row = db.schemaQueries.recentEntries(10).executeAsOne()
        assertEquals("a", row.id)
        assertEquals(100L, row.amount_minor)
        db.entry("c", 3, 100, ref = "UPI123", source = "email") // another source is allowed; the dedup engine decides
        assertEquals(2L, db.schemaQueries.entryCount().executeAsOne())
    }

    @Test
    fun reinsertingTheSameIdUpdatesInPlaceInsteadOfDuplicating() {
        val (_, db) = newDb()
        db.entry("a", 1, 100, cat = "food")
        db.entry("a", 1, 150, cat = "travel")
        val row = db.schemaQueries.recentEntries(10).executeAsOne()
        assertEquals(1L, db.schemaQueries.entryCount().executeAsOne())
        assertEquals(150L, row.amount_minor)
        assertEquals("travel", row.category_id)
    }

    @Test
    fun replayingAnEditDoesNotResurrectADeletedEntryAndKeepsOtherColumns() {
        val (_, db) = newDb()
        db.entry("a", 1, 100, cat = "food", merchant = "Cafe")
        db.schemaQueries.softDeleteEntry("a")
        db.entry("a", 1, 250, cat = "food", merchant = "Cafe") // an edit replayed from the op log
        assertEquals(0L, db.schemaQueries.entryCount().executeAsOne())
        db.schemaQueries.restoreEntry("a")
        val row = db.schemaQueries.recentEntries(10).executeAsOne()
        assertEquals(250L, row.amount_minor)
        assertEquals("Cafe", row.merchant)
    }

    @Test
    fun searchFindsMerchantAndRecentIsNewestFirst() {
        val (_, db) = newDb()
        for (i in 1..25) db.entry("e$i", i.toLong(), 100, merchant = if (i % 5 == 0) "Swiggy $i" else "Shop $i")
        assertEquals(5, db.schemaQueries.searchEntries("swiggy", 50).executeAsList().size)
        assertEquals(List(3) { "e${25 - it}" }, db.schemaQueries.recentEntries(3).executeAsList().map { it.id })
    }

    @Test
    fun keysetPagingVisitsEveryRowExactlyOnceEvenWhenTimestampsTie() {
        val (_, db) = newDb()
        // 40 rows over only 5 distinct timestamps, so most rows tie
        for (i in 1..40) db.entry("e$i", (i % 5).toLong(), 100)
        val seen = mutableListOf<String>()
        var page = db.schemaQueries.recentEntries(7).executeAsList()
        while (page.isNotEmpty()) {
            seen += page.map { it.id }
            val last = page.last()
            page = db.schemaQueries.entriesBefore(last.occurred_at, last.seq, 7).executeAsList()
        }
        assertEquals(40, seen.size)
        assertEquals(40, seen.toSet().size)
        assertEquals(seen, db.schemaQueries.recentEntries(100).executeAsList().map { it.id })
    }

    @Test
    fun opLogReturnsOperationsInClockOrderAndLatestWins() {
        val (_, db) = newDb()
        val q = db.schemaQueries
        q.insertOp("000000000000002-0000-b", "entry", "x", "note", "second")
        q.insertOp("000000000000001-0000-a", "entry", "x", "note", "first")
        q.insertOp("000000000000003-0000-a", "entry", "x", "note", "third")
        assertEquals(listOf("first", "second", "third"), q.opsSince("", 10).executeAsList().map { it.value_ })
        assertEquals(listOf("third"), q.opsSince("000000000000002-0000-b", 10).executeAsList().map { it.value_ })
        assertEquals("third", q.latestOp("entry", "x", "note").executeAsOne().value_)
        assertFailsWith<Exception> { q.insertOp("000000000000001-0000-a", "entry", "y", "note", "dup clock") }
    }

    @Test
    fun hotQueriesUseIndexesInsteadOfFullScans() {
        val (driver, db) = newDb()
        for (i in 1..200) db.entry("e$i", i.toLong(), 100)
        fun plan(sql: String): String {
            val rs = (driver as JdbcSqliteDriver).getConnection().createStatement().executeQuery("EXPLAIN QUERY PLAN $sql")
            val sb = StringBuilder()
            while (rs.next()) sb.append(rs.getString("detail")).append('\n')
            return sb.toString()
        }
        val recent = plan("SELECT * FROM entry WHERE deleted = 0 ORDER BY occurred_at DESC LIMIT 20")
        assertTrue("entry_time" in recent, "recent list should use entry_time, plan was: $recent")
        val acct = plan("SELECT * FROM entry WHERE deleted = 0 AND account_id = 'bank' ORDER BY occurred_at DESC LIMIT 20")
        assertTrue("entry_account_time" in acct, "account list should use entry_account_time, plan was: $acct")
    }

    @Test
    fun fullTextSearchMatchesSubstringsCaseInsensitivelyAndIgnoresDeletedRows() {
        val (_, db) = newDb()
        db.entry("a", 1, 100, merchant = "Swiggy Instamart")
        db.entry("b", 2, 100, merchant = "Zomato")
        assertEquals(listOf("a"), db.schemaQueries.searchEntries("wigg", 10).executeAsList().map { it.id })
        assertEquals(listOf("a"), db.schemaQueries.searchEntries("SWIGGY", 10).executeAsList().map { it.id })
        db.schemaQueries.softDeleteEntry("a")
        assertTrue(db.schemaQueries.searchEntries("swiggy", 10).executeAsList().isEmpty())
        db.schemaQueries.restoreEntry("a")
        assertEquals(1, db.schemaQueries.searchEntries("swiggy", 10).executeAsList().size)
    }

    @Test
    fun fullTextIndexFollowsEditsViaUpsert() {
        val (_, db) = newDb()
        db.entry("a", 1, 100, merchant = "Swiggy")
        db.entry("a", 1, 100, merchant = "Zomato") // edit replayed through the upsert
        assertTrue(db.schemaQueries.searchEntries("swiggy", 10).executeAsList().isEmpty())
        assertEquals(listOf("a"), db.schemaQueries.searchEntries("zomato", 10).executeAsList().map { it.id })
    }

    @Test
    fun shortQueriesFallBackToLikeBecauseTrigramNeedsThreeCharacters() {
        val (_, db) = newDb()
        db.entry("a", 1, 100, merchant = "Ola cabs")
        assertTrue(db.schemaQueries.searchEntries("ol", 10).executeAsList().isEmpty())
        assertEquals(listOf("a"), db.schemaQueries.searchShort("ol", 10).executeAsList().map { it.id })
    }
}
