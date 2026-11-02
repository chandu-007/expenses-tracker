package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The count and the list behind Home's "N expenses have no category" card: what they count, and what they cost on a 20,000-entry ledger (read only from the partial index, never on the save path). */
class UncategorisedCountTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private var now = 0L
    private val ledger = LedgerStore(db, HlcClock("n"), { ++now })
    private val q get() = db.schemaQueries
    private fun count(cap: Long = 500) = q.uncategorisedExpenseCount("INR", cap).executeAsOne()
    private fun inr(minor: Long) = Money(minor, Currency.INR)

    init { ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR)) }

    @Test
    fun itCountsOnlyLiveExpensesThatCanBeFiledFromTheReviewAndTheListAgrees() {
        fun spend(id: String, at: Long, split: Boolean = false, shares: List<Share> = emptyList()) = ledger.save(
            Expense(EntryId(id), at, AccountId("bank"), inr(1_000), if (split) listOf(CategorySplit(CategoryId("Food"), inr(1_000))) else emptyList(), shares), EntryDetails(merchant = "Shop $id", source = "sms"),
        )
        spend("a", 10); spend("b", 20); spend("c", 30)
        spend("d", 40, split = true)
        ledger.save(Income(EntryId("i"), 50, AccountId("bank"), inr(500)), EntryDetails(merchant = "Pay"))
        spend("gone", 60); ledger.delete(EntryId("gone"))
        spend("shared", 70, shares = listOf(Share(null, inr(500)), Share(PersonId("p"), inr(500))))
        spend("refunded", 80); ledger.save(Refund(EntryId("r"), 90, AccountId("bank"), inr(100), EntryId("refunded")), EntryDetails(merchant = "back"))
        assertEquals(3, count(), "a, b and c: not the split one, the income, the deleted one, the shared one, or the one that has a refund")
        assertEquals(listOf("c", "b", "a"), q.uncategorisedExpenseList("INR", 10).executeAsList().map { it.id }, "newest first, the same ones")
        assertEquals(2, count(2), "the count stops at its cap")
        assertEquals(0, q.uncategorisedExpenseCount("USD", 500).executeAsOne(), "another currency's ledger has none here")
        ledger.save(Expense(EntryId("a"), 10, AccountId("bank"), inr(1_000), listOf(CategorySplit(CategoryId("Food"), inr(1_000)))), EntryDetails(merchant = "Shop a"))
        assertEquals(2, count(), "filing one takes it out of the count")
        ledger.delete(EntryId("b")); assertEquals(1, count(), "deleting one too")
    }

    @Test
    fun theCountReadsOnlyThePartialIndexAndCostsLittleOnTwentyThousandEntries() {
        repeat(20_000) { i ->
            val categorised = i % 5 != 0 // 4,000 without a category, like a statement import that was never sorted
            ledger.save(Expense(EntryId("e$i"), 1_000L + i, AccountId("bank"), inr(100L + i), if (categorised) listOf(CategorySplit(CategoryId("Food"), inr(100L + i))) else emptyList()), EntryDetails(merchant = "Shop ${i % 300}"))
        }
        // the plan of the count, read from SQLite itself: its first step must be the partial index, never a walk over spend_line or entry
        val plan = driver.executeQuery(null, """EXPLAIN QUERY PLAN SELECT COUNT(*) FROM (
            SELECT s.entry_id FROM spend_line s JOIN entry e ON e.id = s.entry_id
            WHERE s.currency = 'INR' AND s.category_id IS NULL AND s.mine_minor > 0 AND e.deleted = 0 AND e.kind = 'EXPENSE' AND e.original_amount_minor IS NULL
              AND NOT EXISTS (SELECT 1 FROM entry_share h WHERE h.entry_id = e.id) AND NOT EXISTS (SELECT 1 FROM entry r WHERE r.refunds_entry = e.id AND r.kind = 'REFUND' AND r.deleted = 0)
            LIMIT 500)""", { c ->
            val rows = ArrayList<String>(); while (c.next().value) rows += c.getString(3).orEmpty()
            app.cash.sqldelight.db.QueryResult.Value(rows)
        }, 0).value
        assertTrue(plan.any { "spend_line_uncategorised" in it }, "the plan uses the partial index: $plan")
        val warm = count(); assertEquals(500, warm, "4,000 without a category: capped at 500")
        var capped = 0L; val cappedNs = measureNanoTime { repeat(20) { capped += count() } }
        var full = 0L; val fullNs = measureNanoTime { full = count(100_000) }
        val listNs = measureNanoTime { assertEquals(300, q.uncategorisedExpenseList("INR", 300).executeAsList().size) }
        println("UNCATEGORISED-COUNT 20,000 entries, 4,000 without a category: capped count ${cappedNs / 20 / 1_000} us, uncapped ($full) ${fullNs / 1_000} us, list of 300 ${listNs / 1_000} us")
        assertEquals(4_000, full)
        assertTrue(cappedNs / 20 < 20_000_000, "the capped count took ${cappedNs / 20 / 1_000} us")
        assertTrue(listNs < 200_000_000, "the list took ${listNs / 1_000} us")
    }
}
