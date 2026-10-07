package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RevisionKind
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals

class EntryHistoryStoreTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private var now = 1_000L
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val ledger = LedgerStore(db, HlcClock("n")) { now }
    private fun expense(amount: Long, cat: String, account: String = "cash") = Expense(EntryId("e"), 50, AccountId(account), m(amount), listOf(CategorySplit(CategoryId(cat), m(amount))))

    init {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr))
    }

    @Test
    fun theLifeOfAnEntryReadsBackAsCreatedEditedDeletedRestoredEachSaveAsOneRevision() {
        now = 1_000; ledger.save(expense(10_000, "Food"), EntryDetails(merchant = "Cafe"))
        now = 2_000; ledger.save(expense(15_000, "Bills", "bank"), EntryDetails(merchant = "Cafe"))
        now = 3_000; ledger.delete(EntryId("e"))
        now = 4_000; ledger.restore(EntryId("e"))
        val h = ledger.history(EntryId("e"))
        assertEquals(listOf(RevisionKind.CREATED, RevisionKind.EDITED, RevisionKind.DELETED, RevisionKind.RESTORED), h.map { it.kind })
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L), h.map { it.atMillis })
        val changed = h[1].changes.associate { it.field to (it.before to it.after) }
        assertEquals("10000" to "15000", changed["amount_minor"]); assertEquals("cash" to "bank", changed["account_id"])
        assertEquals(setOf("amount_minor", "account_id", "splits"), changed.keys, "only what changed is listed (the unchanged merchant is not)")
    }

    @Test
    fun twoSavesInTheSameMillisecondStillShowAsOneChangeEachWhenTheClockMoves() {
        now = 5_000; ledger.save(expense(100, "Food"))
        now = 5_001; ledger.save(expense(200, "Food"))
        assertEquals(2, ledger.history(EntryId("e")).size)
    }

    @Test
    fun anEntryThatWasNeverEditedHasOnlyItsCreationAndAnUnknownIdHasNothing() {
        ledger.save(expense(100, "Food"))
        assertEquals(listOf(RevisionKind.CREATED), ledger.history(EntryId("e")).map { it.kind })
        assertEquals(emptyList(), ledger.history(EntryId("nope")))
    }
}
