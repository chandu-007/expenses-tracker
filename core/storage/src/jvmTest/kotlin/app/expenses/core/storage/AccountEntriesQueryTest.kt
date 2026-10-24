package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Home narrowed to one account: the two lookups find what was paid from it and what a transfer put into it, in order, and each is an index search, not a scan of the ledger. */
class AccountEntriesQueryTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)

    private fun put(id: String, kind: String, at: Long, account: String, counter: String? = null) = driver.execute(null,
        "INSERT INTO entry(id, kind, occurred_at, account_id, counter_account_id, amount_minor, currency, source, deleted) VALUES ('$id', '$kind', $at, '$account', ${counter?.let { "'$it'" } ?: "NULL"}, 100, 'INR', 'manual', 0)", 0)

    @Test
    fun anAccountsEntriesAreWhatLeftItAndWhatAWasTransferredIntoItInTimeOrderWithKeysetPaging() {
        put("a1", "EXPENSE", 10, "cash"); put("b1", "EXPENSE", 20, "bank"); put("t1", "TRANSFER", 30, "bank", "cash"); put("a2", "EXPENSE", 40, "cash")
        val q = db.schemaQueries
        assertEquals(listOf("a2", "a1"), q.recentEntriesByAccount("cash", 10).executeAsList().map { it.id })
        assertEquals(listOf("t1"), q.recentEntriesByCounter("cash", 10).executeAsList().map { it.id }, "the transfer into cash")
        assertEquals(listOf("b1", "t1"), q.recentEntriesByAccount("bank", 10).executeAsList().map { it.id }.reversed())
        assertEquals(emptyList(), q.recentEntriesByCounter("bank", 10).executeAsList())
        val firstSeq = q.recentEntriesByAccount("cash", 10).executeAsList().first().seq
        assertEquals(listOf("a1"), q.entriesBeforeByAccount("cash", 40, firstSeq, 10).executeAsList().map { it.id })
    }

    @Test
    fun bothLookupsUseAnIndexOnTheAccountSoARarelyUsedAccountDoesNotScanTheLedger() {
        fun plan(sql: String): String {
            val out = StringBuilder()
            driver.executeQuery(null, "EXPLAIN QUERY PLAN $sql", { c -> while (c.next().value) out.append(c.getString(3)).append('\n'); app.cash.sqldelight.db.QueryResult.Unit }, 0)
            return out.toString()
        }
        val byAccount = plan("SELECT * FROM entry WHERE deleted = 0 AND account_id = 'x' ORDER BY occurred_at DESC, seq DESC LIMIT 50")
        val byCounter = plan("SELECT * FROM entry WHERE deleted = 0 AND counter_account_id = 'x' ORDER BY occurred_at DESC, seq DESC LIMIT 50")
        assertTrue("SEARCH" in byAccount && "entry_account_time_seq" in byAccount, byAccount)
        assertTrue("SEARCH" in byCounter && "entry_counter_time" in byCounter, byCounter)
    }
}
