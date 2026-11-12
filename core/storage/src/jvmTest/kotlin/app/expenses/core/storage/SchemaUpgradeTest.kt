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
        d.execute(null, "DROP TABLE saved_search", 0)
        d.execute(null, "DROP TABLE entry_replaced", 0)
        d.execute(null, "DROP TABLE trip", 0)
        d.execute(null, "DROP TABLE attachment", 0)
        d.execute(null, "ALTER TABLE recurring DROP COLUMN kind", 0)
        d.execute(null, "ALTER TABLE recurring DROP COLUMN to_account_id", 0)
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
        val clock = HlcClock("n") // one clock for every store, as in the app
        val ledger = LedgerStore(db, clock) { 1L }
        ledger.save(Expense(EntryId("e"), 1, AccountId("cash"), Money(100, Currency.INR)), EntryDetails(tags = listOf("trip")))
        assertEquals(listOf("trip"), ledger.tagsOf(EntryId("e")))
        AttachmentStore(db, clock) { 2L }.let { it.add("a", "e", "r.jpg", "image/jpeg", 5, "h"); assertEquals(listOf("r.jpg"), it.list("e").map { x -> x.name }) }
        SavedSearchStore(db, clock) { 2L }.let { it.save(SavedSearch("s", "Cabs", "uber")); assertEquals(listOf("Cabs"), it.list().map { s -> s.name }) }
        assertEquals(emptyList(), app.expenses.core.storage.RecurringStore(db, clock, { 1L }, ledger).list(), "the repeating-item table has its new columns")
        ledger.replaced.mark("old", "e", "CHANGED"); assertEquals("e", ledger.replaced.by("old")); assertEquals(listOf("old"), db.schemaQueries.allReplaced().executeAsList().map { it.entry_id }, "the table of replaced entries exists after the upgrade")
        ledger.trips.save(app.expenses.core.domain.Trip("goa", "Goa", "Trip: Goa", 20_400, 20_406)); assertEquals(listOf("goa"), ledger.trips.all().map { it.id }, "the table of trips exists after the upgrade")
        SchemaUpgrade.ensureLatest(d); assertEquals(listOf("goa"), ledger.trips.all().map { it.id }, "a second run keeps the trips")
    }

    @Test
    fun runningItOnACurrentDatabaseOrTwiceChangesNothing() {
        val d = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
        val db = ExpensesDb(d)
        LedgerStore(db, HlcClock("n")) { 1L }.also { it.saveAccount(Account(AccountId("a"), "A", AccountType.BANK, Currency.INR, 500, true)); it.trips.save(app.expenses.core.domain.Trip("goa", "Goa", "Trip: Goa", 20_400, 20_406, true, 5_000)) }
        SchemaUpgrade.ensureLatest(d); SchemaUpgrade.ensureLatest(d)
        assertEquals(listOf(app.expenses.core.domain.Trip("goa", "Goa", "Trip: Goa", 20_400, 20_406, true, 5_000)), LedgerStore(db, HlcClock("n")) { 1L }.trips.all(), "running it again leaves the trips as they were")
        val a = db.schemaQueries.selectAccounts().executeAsList().single()
        assertEquals(500L, a.opening_minor); assertEquals(1L, a.archived)
    }
}
