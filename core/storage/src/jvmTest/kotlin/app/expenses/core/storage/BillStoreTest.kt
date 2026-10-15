package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillStoreTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val store = BillStore(db)

    private fun bill(id: String, key: String = "k$id", due: String? = "2026-10-25", amount: Long = 1_234_500, body: String? = "text $id") =
        Bill(id, "SMS", "AX-HDFCBK-S", "HDFC Bank", "CREDIT_CARD", "HDFC Bank credit card", amount, 61_700, "INR", due, "0862", null, false, 1_000, body, BillStatus.FOUND, null, null, key)

    @Test
    fun aBillIsAddedFoundAndTheSameBillAgainIsNotAddedInAnyState() {
        assertTrue(store.add(bill("a", key = "same")))
        assertFalse(store.add(bill("b", key = "same")), "a reminder of the same bill")
        store.dismiss("a")
        assertFalse(store.add(bill("c", key = "same")), "a skipped bill does not come back when it is reminded")
        assertTrue(store.add(bill("d", key = "other")))
        assertEquals(listOf("d"), store.list(BillStatus.FOUND).map { it.id })
    }

    @Test
    fun trackingKeepsTheFiguresAndForgetsTheTextAndOnlyAFoundBillCanBeTracked() {
        store.add(bill("a"))
        assertEquals("text a", store.get("a")!!.body)
        assertTrue(store.track("a"))
        val t = store.get("a")!!
        assertEquals(BillStatus.TRACKED, t.status); assertNull(t.body, "the message is gone"); assertEquals(1_234_500L, t.amountMinor); assertEquals(61_700L, t.minDueMinor); assertEquals("2026-10-25", t.dueDate)
        assertFalse(store.track("a"), "already tracked"); assertFalse(store.track("missing"))
    }

    @Test
    fun payingRecordsTheEntryAndTimeAndUnpayingPutsItBackWithoutTheEntry() {
        store.add(bill("a")); store.track("a")
        assertTrue(store.markPaid("a", "entry1", 9_000))
        val p = store.get("a")!!
        assertEquals(BillStatus.PAID, p.status); assertEquals("entry1", p.entryId); assertEquals(9_000L, p.paidAt)
        assertFalse(store.markPaid("a", "entry2", 9_500), "a second payment is refused")
        assertEquals("entry1", store.get("a")!!.entryId)
        store.unpay("a")
        val u = store.get("a")!!
        assertEquals(BillStatus.TRACKED, u.status); assertNull(u.entryId); assertNull(u.paidAt)
        store.add(bill("b")); assertTrue(store.markPaid("b", null, 10_000), "a bill can be marked paid without an entry, even straight from FOUND")
        assertNull(store.get("b")!!.entryId)
    }

    @Test
    fun aSkippedBillCannotBePaidOrTrackedAndAPaidOneCannotBeSkipped() {
        store.add(bill("a")); assertTrue(store.dismiss("a"))
        assertFalse(store.track("a")); assertFalse(store.markPaid("a", null, 1))
        store.add(bill("b")); store.track("b"); store.markPaid("b", null, 1)
        assertFalse(store.dismiss("b")); assertEquals(BillStatus.PAID, store.get("b")!!.status)
        store.add(bill("c")); store.track("c"); assertTrue(store.dismiss("c"), "a tracked bill can be removed")
    }

    @Test
    fun theListIsByDueDateWithUndatedLastAndCountsFollowTheStatus() {
        store.add(bill("late", due = "2026-11-30")); store.add(bill("none", due = null)); store.add(bill("soon", due = "2026-10-12"))
        assertEquals(listOf("soon", "late", "none"), store.list(BillStatus.FOUND).map { it.id })
        assertEquals(3L, store.count(BillStatus.FOUND)); assertEquals(0L, store.count(BillStatus.TRACKED))
        store.track("soon"); assertEquals(2L, store.count(BillStatus.FOUND)); assertEquals(1L, store.count(BillStatus.TRACKED))
    }

    @Test
    fun aDueDateCanBeSetWhileFoundOrTrackedButNotOnceDone() {
        store.add(bill("a", due = null)); store.setDueDate("a", "2026-12-01")
        assertEquals("2026-12-01", store.get("a")!!.dueDate)
        store.track("a"); store.setDueDate("a", "2026-12-05"); assertEquals("2026-12-05", store.get("a")!!.dueDate)
        store.markPaid("a", null, 1); store.setDueDate("a", "2027-01-01"); assertEquals("2026-12-05", store.get("a")!!.dueDate)
    }

    @Test
    fun trackedAndPaidBillsSurviveABackupAndAFoundOneDoesNotAndRestoringTwiceChangesNothing() {
        val d = Device("a"); val bills = BillStore(d.db)
        bills.add(bill("t")); bills.track("t"); bills.add(bill("p")); bills.track("p"); bills.markPaid("p", "entry", 7_000); bills.add(bill("f")); bills.add(bill("x")); bills.dismiss("x")
        val (_, data) = BackupFile.decode(BackupFile.encode(d.backup.export(), 1L))
        val other = Device("b"); val restored = BillStore(other.db)
        other.backup.restore(data); other.backup.restore(data)
        assertEquals(setOf("t", "p", "x"), restored.all().map { it.id }.toSet(), "the bill still waiting is not restored")
        assertEquals(BillStatus.PAID, restored.get("p")!!.status); assertEquals(7_000L, restored.get("p")!!.paidAt); assertNull(restored.get("p")!!.entryId, "the entry is not part of the file")
        assertNull(restored.get("t")!!.body)
        assertFalse(restored.add(bill("t2", key = "kt")), "a restored bill still stops its own reminder from coming back")
    }

    @Test
    fun aBillPaidByAnEntryThatIsDeletedIsTrackedAgainAndPaidAgainWhenTheEntryComesBackUnlessThePersonUnpaidIt() {
        val inr = app.expenses.core.domain.Currency.INR
        val ledger = app.expenses.core.storage.LedgerStore(db, app.expenses.core.domain.sync.HlcClock("n1")) { 1L }
        ledger.saveAccount(app.expenses.core.domain.ledger.Account(app.expenses.core.domain.ledger.AccountId("bank"), "Bank", app.expenses.core.domain.ledger.AccountType.BANK, inr))
        for (id in listOf("live", "gone")) ledger.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId(id), 1_000, app.expenses.core.domain.ledger.AccountId("bank"), app.expenses.core.domain.Money(100, inr)))
        listOf("a", "b", "c").forEach { store.add(bill(it)); store.track(it) }
        store.markPaid("a", "live", 5); store.markPaid("b", "gone", 5); store.markPaid("c", null, 5)
        assertFalse(store.reconcileWithEntries(), "nothing deleted yet")
        ledger.delete(app.expenses.core.domain.ledger.EntryId("gone"))
        assertTrue(store.reconcileWithEntries())
        assertEquals(BillStatus.TRACKED, store.get("b")!!.status); assertEquals("gone", store.get("b")!!.entryId, "the link is kept so that undo can re-pay")
        assertEquals(BillStatus.PAID, store.get("a")!!.status); assertEquals(BillStatus.PAID, store.get("c")!!.status, "marked paid with no entry stays paid")
        ledger.restore(app.expenses.core.domain.ledger.EntryId("gone"))
        assertTrue(store.reconcileWithEntries()); assertEquals(BillStatus.PAID, store.get("b")!!.status, "the entry is back (undo, or a restore from sync)")
        store.unpay("b")
        assertFalse(store.reconcileWithEntries(), "not paid after all cuts the link, so a live entry does not pay it again")
        assertEquals(BillStatus.TRACKED, store.get("b")!!.status); assertNull(store.get("b")!!.entryId)
    }

    private fun ledgerWithEntry(id: String): app.expenses.core.storage.LedgerStore {
        val inr = app.expenses.core.domain.Currency.INR
        val ledger = app.expenses.core.storage.LedgerStore(db, app.expenses.core.domain.sync.HlcClock("n1")) { 1L }
        ledger.saveAccount(app.expenses.core.domain.ledger.Account(app.expenses.core.domain.ledger.AccountId("bank"), "Bank", app.expenses.core.domain.ledger.AccountType.BANK, inr))
        ledger.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId(id), 1_000, app.expenses.core.domain.ledger.AccountId("bank"), app.expenses.core.domain.Money(100, inr)))
        return ledger
    }

    @Test
    fun aLoneBillWhosePaymentWasDeletedIsReleasedAndAloneBillWhosePaymentIsBackIsPaid() {
        val ledger = ledgerWithEntry("e")
        store.add(bill("a")); store.track("a"); store.markPaid("a", "e", 5)
        assertFalse(store.reconcileWithEntries(), "in step: nothing to do")
        ledger.delete(app.expenses.core.domain.ledger.EntryId("e"))
        assertTrue(store.reconcileWithEntries()); assertEquals(BillStatus.TRACKED, store.get("a")!!.status)
        assertFalse(store.reconcileWithEntries(), "already released: nothing more to do")
        ledger.restore(app.expenses.core.domain.ledger.EntryId("e"))
        assertTrue(store.reconcileWithEntries()); assertEquals(BillStatus.PAID, store.get("a")!!.status)
    }
}
