package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FtsTuningTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)

    private fun config(): Map<String, Long> {
        val rs = driver.getConnection().createStatement().executeQuery("SELECT k, v FROM entry_fts_config WHERE k IN ('automerge','crisismerge')")
        return buildMap { while (rs.next()) put(rs.getString(1), rs.getLong(2)) }
    }

    private fun add(i: Int, merchant: String) =
        db.schemaQueries.insertEntry("e$i", "EXPENSE", i.toLong(), "bank", null, 100, "INR", "c", merchant, "UPI note $i", "manual", null)

    @Test
    fun settingsAreStoredAndApplyingTwiceWritesNothingTheSecondTime() {
        assertTrue(config().isEmpty(), "SQLite defaults are in force until set")
        FtsTuning.apply(driver)
        assertEquals(mapOf("automerge" to 0L, "crisismerge" to FtsTuning.CRISIS_MERGE_SEGMENTS.toLong()), config())
        val before = driver.getConnection().createStatement().executeQuery("SELECT total_changes()").use { it.next(); it.getLong(1) }
        FtsTuning.apply(driver)
        val after = driver.getConnection().createStatement().executeQuery("SELECT total_changes()").use { it.next(); it.getLong(1) }
        assertEquals(before, after, "re-applying must not write, so opening the app is not a write")
    }

    @Test
    fun searchAnswersAreIdenticalBeforeAndAfterMergingManyUnmergedSegments() {
        FtsTuning.apply(driver)
        val rnd = Random(3)
        val words = listOf("swiggy", "zomato", "amazon", "uber", "bigbasket")
        // every insert is its own commit, so each leaves its own index segment
        for (i in 0 until 600) add(i, "${words[rnd.nextInt(words.size)]} ${rnd.nextInt(50)}")
        for (i in 0 until 600 step 7) db.schemaQueries.softDeleteEntry("e$i")
        for (i in 1 until 600 step 11) add(i, "edited ${words[rnd.nextInt(words.size)]}") // edits go through the update trigger
        val queries = listOf("swiggy", "zomato 4", "mazon", "edited", "uber 12", "nomatch", "upi note")
        val before = queries.map { q -> db.search(q, 1000).map { it.id } }
        var steps = 0
        while (FtsTuning.mergeStep(driver, pages = 5) && steps < 10_000) steps++
        assertTrue(steps > 0, "there were unmerged segments to merge")
        assertFalse(FtsTuning.mergeStep(driver), "a fully merged index has nothing left to do")
        assertEquals(before, queries.map { q -> db.search(q, 1000).map { it.id } })
        assertTrue(before.any { it.isNotEmpty() }, "the comparison must not be vacuous")
    }

    @Test
    fun mergeStepNeedsAPositivePageCount() {
        FtsTuning.apply(driver)
        kotlin.test.assertFailsWith<IllegalArgumentException> { FtsTuning.mergeStep(driver, 0) }
    }
}
