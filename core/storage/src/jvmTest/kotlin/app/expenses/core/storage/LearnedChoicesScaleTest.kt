package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.CaptureRule
import app.expenses.core.domain.CategorySuggest
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.PayeeKey
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What learning costs on a big ledger: a lookup is a map read (no scan), learning is a handful of statements, and the one pass over uncategorised expenses is read only when the person opens
 * the list of what was learned. Budgets are generous (shared machines); the figures are printed for the record.
 */
class LearnedChoicesScaleTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private var now = 0L
    private val ledger = LedgerStore(db, HlcClock("n"), { ++now })
    private val store = CaptureRuleStore(db)

    private fun word(i: Int): String = buildString { var n = i; repeat(4) { append('a' + n % 26); n /= 26 } }

    @Test
    fun lookupsAreMapReadsAndLearningAndTheUncategorisedPassAreCheapOnTwentyThousandEntries() {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR))
        val saving = measureTimeMillis {
            repeat(20_000) { i -> ledger.save(Expense(EntryId("e$i"), 1_000L + i, AccountId("cash"), Money(100L + i, Currency.INR)), EntryDetails(merchant = "Shop number ${i % 400}")) }
        }
        val learning = measureTimeMillis { db.transaction { repeat(3_000) { i -> store.learn("Payee ${word(i)}", "Food") } } }
        assertEquals(3_000, store.learned().size)
        val rules = listOf(CaptureRule("r", "netflix", "Fun"))
        val taught = store.learned()
        val usable = setOf("Food", "Fun")
        var found = 0
        val lookups = measureTimeMillis {
            repeat(5_000) { i -> if (CategorySuggest.suggest("PAYEE ${word(i % 3_000).uppercase()} BANGALORE KA IN", "x", rules, taught, usable)?.categoryId == "Food") found++ }
        }
        val rowsMs = measureTimeMillis { store.learnedRows() }
        val passMs = measureTimeMillis { assertEquals(20_000, db.schemaQueries.uncategorisedExpenses(CaptureRuleStore.HISTORY_SCAN).executeAsList().size) }
        println("LEARNED-SCALE 20,000 entries saved in $saving ms; 3,000 choices learned in $learning ms; 5,000 lookups in $lookups ms ($found found); learnedRows $rowsMs ms; uncategorised pass over 20,000 entries $passMs ms")
        assertEquals(5_000, found, "every spelling with city noise finds the learned choice")
        assertTrue(lookups < 2_000, "5,000 lookups in $lookups ms")
        assertTrue(learning < 5_000, "3,000 choices learned in $learning ms")
        assertTrue(passMs < 3_000, "the uncategorised pass took $passMs ms")
        assertTrue(PayeeKey.of("PAYEE ${word(17).uppercase()} BANGALORE KA IN") == PayeeKey.of("Payee ${word(17)}"))
    }
}
