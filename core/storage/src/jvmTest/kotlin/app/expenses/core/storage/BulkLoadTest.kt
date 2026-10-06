package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BulkLoadTest {
    private lateinit var driver: JdbcSqliteDriver
    private fun newDb(): ExpensesDb {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ExpensesDb.Schema.create(driver)
        return ExpensesDb(driver)
    }

    private fun ExpensesDb.add(id: String, merchant: String) =
        schemaQueries.insertEntry(id, "EXPENSE", 1, "bank", null, 100, "INR", "food", merchant, null, "manual", null)

    private fun ExpensesDb.hits(q: String) = schemaQueries.searchEntries(q, 1000).executeAsList().map { it.id }.sorted()

    @Test
    fun bulkLoadedRowsAreSearchableAndLaterInsertsStayIndexed() {
        val db = newDb()
        db.add("old", "Swiggy early")
        db.bulkLoad(driver) { for (i in 1..500) db.add("b$i", if (i % 2 == 0) "Swiggy $i" else "Zomato $i") }
        assertEquals(251, db.hits("swiggy").size) // 250 bulk + the row that existed before
        assertTrue("old" in db.hits("swiggy"))
        db.add("late", "Swiggy late")
        assertTrue("late" in db.hits("swiggy"))
        assertEquals(500 + 2, db.schemaQueries.entryCount().executeAsOne().toInt())
    }

    @Test
    fun failedBulkLoadRollsBackRowsAndLeavesIndexingOn() {
        val db = newDb()
        assertFailsWith<IllegalStateException> {
            db.bulkLoad(driver) {
                db.add("x", "Swiggy")
                error("importer crashed")
            }
        }
        assertEquals(0L, db.schemaQueries.entryCount().executeAsOne())
        db.add("y", "Swiggy again")
        assertEquals(listOf("y"), db.hits("swiggy")) // triggers are active again after the rollback
    }

    @Test
    fun editsAndDeletesInsideTheIndexSurviveARebuild() {
        val db = newDb()
        db.add("a", "Swiggy")
        db.bulkLoad(driver) { db.add("a", "Zomato") } // edit applied during a bulk load
        assertTrue(db.hits("swiggy").isEmpty())
        assertEquals(listOf("a"), db.hits("zomato"))
    }
}
