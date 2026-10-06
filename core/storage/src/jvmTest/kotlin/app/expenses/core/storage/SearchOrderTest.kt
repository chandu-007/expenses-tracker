package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random
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
        assertEquals(listOf("newest", "mid", "oldest"), db.search("swiggy", 10).map { it.id })
        assertEquals(listOf("newest", "mid"), db.search("swiggy", 2).map { it.id })
    }

    @Test
    fun resultsStayExactWhenMatchesExceedTheSortFirstThreshold() {
        db.transaction {
            // Added first but newest by date: an approximate "latest added" cap would lose it.
            add("added-first-newest-date", 1_000_000, "Swiggy")
            for (i in 1..2500) add("m$i", i.toLong(), "Swiggy $i")
        }
        val ids = db.search("swiggy", 3).map { it.id }
        assertEquals(listOf("added-first-newest-date", "m2500", "m2499"), ids)
    }

    @Test
    fun bothPlansReturnTheSameAnswerOnRandomData() {
        val rnd = Random(42)
        val words = listOf("swiggy", "zomato", "amazon", "uber")
        db.transaction {
            for (i in 1..3000) {
                add("e$i", rnd.nextLong(0, 1_000_000), "${words[rnd.nextInt(words.size)]} ${rnd.nextInt(100)}")
                if (i % 7 == 0) db.schemaQueries.softDeleteEntry("e$i")
            }
        }
        for (term in listOf("swiggy", "zomato 5", "mazon", "uber 99", "nomatch")) {
            val q = ftsPhrase(term)
            val small = db.schemaQueries.searchEntries(q, 40).executeAsList().map { it.id }
            val walk = db.schemaQueries.searchEntriesWalk(q, 40).executeAsList().map { it.id }
            assertEquals(small, walk, "plans disagree for '$term'")
        }
    }

    @Test
    fun deletedEntriesNeverAppearInEitherPlan() {
        db.transaction { for (i in 1..2100) add("m$i", i.toLong(), "Swiggy $i") }
        db.schemaQueries.softDeleteEntry("m2100")
        assertTrue("m2100" !in db.search("swiggy", 5).map { it.id }) // 2100 matches: walk plan
        db.transaction { for (i in 1..2100) db.schemaQueries.softDeleteEntry("m$i") }
        assertTrue(db.search("swiggy", 5).isEmpty())
    }

    @Test
    fun userTextWithQuotesAndOperatorsIsSearchedLiterally() {
        add("a", 1, "Dominos \"Pizza\" - offer")
        add("b", 2, "Pizza Hut")
        assertEquals(listOf("a"), db.search("\"Pizza\" - offer").map { it.id })
        assertEquals(listOf("b", "a"), db.search("pizza").map { it.id })
        assertTrue(db.search("pizza OR zzzzz AND").isEmpty()) // would be a syntax error if passed raw
    }

    @Test
    fun shortAndBlankQueriesDoNotUseTheTrigramIndex() {
        add("a", 1, "Ola cabs")
        assertEquals(listOf("a"), db.search("ol").map { it.id })
        assertTrue(db.search("   ").isEmpty())
    }

    @Test
    fun categoryTotalsAreAnsweredFromTheCoveringIndexWithoutTouchingRows() {
        db.schemaQueries.insertSpendLine("a", 0, 1, "INR", "food", 100, 100)
        val rs = driver.getConnection().createStatement().executeQuery(
            "EXPLAIN QUERY PLAN SELECT category_id, SUM(mine_minor), SUM(cash_out_minor) FROM spend_line " +
                "WHERE currency = 'INR' AND occurred_at >= 0 AND occurred_at < 99 GROUP BY category_id",
        )
        val plan = buildString { while (rs.next()) append(rs.getString("detail")).append('\n') }
        assertTrue("COVERING INDEX spend_line_report" in plan, "plan was: $plan")
    }
}
