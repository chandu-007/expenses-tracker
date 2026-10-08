package app.expenses.core.storage

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
import kotlin.test.assertTrue

class AttachmentStoreTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val ledger = LedgerStore(db, clock) { 1L }
    private val store = AttachmentStore(db, clock) { 2L }

    init {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR))
        ledger.save(Expense(EntryId("e"), 1, AccountId("cash"), Money(100, Currency.INR)))
    }

    @Test
    fun anAttachmentIsRecordedListedInOrderAndRemovedAndBothChangesAreLogged() {
        store.add("a1", "e", "../bill.pdf", "application/pdf", 1_000, "h1"); store.add("a2", "e", "photo.jpg", "", 2_000, "h2")
        assertEquals(listOf("bill.pdf", "photo.jpg"), store.list("e").map { it.name }, "the name is made safe")
        assertEquals("application/octet-stream", store.get("a2")!!.mime)
        store.delete("a1"); store.delete("a1")
        assertEquals(listOf("a2"), store.list("e").map { it.id }); assertEquals(setOf("a2"), store.allIds())
        val ops = db.schemaQueries.opsSince("", 1000).executeAsList().filter { it.entity == "attachment" }.map { it.row_id to it.field_ }
        assertTrue(("a1" to "entry_id") in ops && ("a1" to "deleted") in ops)
    }

    @Test
    fun theLimitsAndAMissingOrDeletedEntryAreRefusedWithAReason() {
        repeat(10) { store.add("a$it", "e", "f$it", "x/y", 10, "h") }
        assertEquals("An entry can have up to 10 attachments.", assertFailsWith<IllegalArgumentException> { store.add("z", "e", "f", "x/y", 10, "h") }.message)
        assertEquals("That entry no longer exists.", assertFailsWith<IllegalArgumentException> { store.add("z", "nope", "f", "x/y", 10, "h") }.message)
        ledger.save(Expense(EntryId("e2"), 1, AccountId("cash"), Money(100, Currency.INR))); ledger.delete(EntryId("e2"))
        assertEquals("That entry no longer exists.", assertFailsWith<IllegalArgumentException> { store.add("z", "e2", "f", "x/y", 10, "h") }.message)
        ledger.save(Expense(EntryId("e3"), 1, AccountId("cash"), Money(100, Currency.INR)))
        assertTrue(assertFailsWith<IllegalArgumentException> { store.add("big", "e3", "f", "x/y", 16L * 1024 * 1024, "h") }.message!!.contains("15 MB"))
        assertEquals(emptyList(), store.list("e3"))
    }

    @Test
    fun aDeletedEntryKeepsItsAttachmentsSoRestoringItBringsThemBack() {
        store.add("a1", "e", "bill.pdf", "application/pdf", 10, "h")
        ledger.delete(EntryId("e")); assertEquals(1, store.list("e").size)
        ledger.restore(EntryId("e")); assertEquals(listOf("a1"), store.list("e").map { it.id })
    }
}
