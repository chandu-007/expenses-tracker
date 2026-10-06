package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchOrderTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)

    private fun add(id: String, at: Long, merchant: String, cat: String = "food") =
        db.schemaQueries.insertEntry(id, "EXPENSE", at, "bank", null, 100, "INR", cat, merchant, null, "manual", null)

    @Test
    fun resultsAreInDateOrderEvenWhenAddedOutOfOrder() {
        add("mid", 500, "Swiggy")
        add("newest", 900, "Swiggy")
        add("oldest", 100, "Swiggy")
        assertEquals(listOf("newest", "mid", "oldest"), db.schemaQueries.searchEntries("swiggy", 10).executeAsList().map { it.id })
        assertEquals(listOf("newest", "mid"), db.schemaQueries.searchEntries("swiggy", 2).executeAsList().map { it.id })
    }

    @Test
    fun veryCommonTermsConsiderOnlyTheMostRecentlyAdded2000Matches() {
        db.transaction {
            // The first-added entry has the newest date, but it is older than 2000 other matches by insertion.
            add("first-added-newest-date", 1_000_000, "Swiggy")
            for (i in 1..2000) add("m$i", i.toLong(), "Swiggy $i")
        }
        val ids = db.schemaQueries.searchEntries("swiggy", 5000).executeAsList().map { it.id }
        assertEquals(2000, ids.size)
        assertTrue("first-added-newest-date" !in ids)
        assertEquals("m2000", ids.first())
    }

    @Test
    fun categoryTotalsAreAnsweredFromTheCoveringIndexWithoutTouchingRows() {
        add("a", 1, "x")
        val rs = driver.getConnection().createStatement().executeQuery(
            "EXPLAIN QUERY PLAN SELECT category_id, SUM(amount_minor) FROM entry WHERE deleted = 0 AND kind = 'EXPENSE' " +
                "AND currency = 'INR' AND occurred_at >= 0 AND occurred_at < 99 GROUP BY category_id",
        )
        val plan = buildString { while (rs.next()) append(rs.getString("detail")).append('\n') }
        assertTrue("COVERING INDEX entry_spend" in plan, "plan was: $plan")
    }
}
