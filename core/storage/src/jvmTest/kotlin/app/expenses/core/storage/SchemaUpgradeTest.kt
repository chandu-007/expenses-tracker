package app.expenses.core.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaUpgradeTest {
    private fun oldShape(): SqlDriver {
        val d = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ExpensesDb.Schema.create(d)
        // what an install from before accounts had opening balances and entries had tags looked like
        d.execute(null, "ALTER TABLE account DROP COLUMN opening_minor", 0)
        d.execute(null, "ALTER TABLE account DROP COLUMN archived", 0)
        d.execute(null, "DROP TABLE entry_tag", 0)
        return d
    }

    @Test
    fun anOlderDatabaseFailsUntilUpgradedAndThenWorksWithItsDataIntact() {
        val d = oldShape()
        d.execute(null, "INSERT INTO account(id, name, type, currency, deleted) VALUES ('cash', 'Cash', 'CASH', 'INR', 0)", 0)
        d.execute(null, "INSERT INTO currency(code, digits) VALUES ('INR', 2)", 0)
        val db = ExpensesDb(d)
        assertFailsWith<Exception> { db.schemaQueries.selectAccounts().executeAsList() }
        SchemaUpgrade.ensureLatest(d)
        val account = db.schemaQueries.selectAccounts().executeAsList().single()
        assertEquals("Cash", account.name); assertEquals(0L, account.opening_minor); assertEquals(0L, account.archived)
        val ledger = LedgerStore(db, HlcClock("n")) { 1L }
        ledger.save(Expense(EntryId("e"), 1, AccountId("cash"), Money(100, Currency.INR)), EntryDetails(tags = listOf("trip")))
        assertEquals(listOf("trip"), ledger.tagsOf(EntryId("e")))
    }

    @Test
    fun runningItOnACurrentDatabaseOrTwiceChangesNothing() {
        val d = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
        val db = ExpensesDb(d)
        LedgerStore(db, HlcClock("n")) { 1L }.saveAccount(Account(AccountId("a"), "A", AccountType.BANK, Currency.INR, 500, true))
        SchemaUpgrade.ensureLatest(d); SchemaUpgrade.ensureLatest(d)
        val a = db.schemaQueries.selectAccounts().executeAsList().single()
        assertEquals(500L, a.opening_minor); assertEquals(1L, a.archived)
    }
}
