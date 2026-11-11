package app.expenses.core.storage

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
import app.expenses.core.domain.ledger.Transfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What restoring backups made before and after an entry's type was changed does. Merging is by entry id and by bank reference, and since the backup carries a record of every entry a type change
 * replaced (`replaced`, see docs/BACKUP_FORMAT.md) a restore, in either order, never brings the replaced original back next to its replacement. (Before that record existed the backups of a
 * ledger with a type change could double the amount when restored in the wrong order: these tests used to pin that; they now pin the fix.)
 */
class KindChangeBackupTest {
    private val inr = Currency.INR
    private val amount = 2_000_00L

    private class Phone(val d: Device) {
        val changes = KindChanges(d.db, d.ledger, OriginStore(d.db), ExpectStore(d.db), AttachmentStore(d.db, d.clock) { 5_000L })
        val live get() = d.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList()
    }

    private fun phone(): Phone = Phone(Device("p" + System.nanoTime())).also {
        it.d.ledger.saveAccount(Account(AccountId("main"), "Main", AccountType.BANK, inr))
        it.d.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
    }

    private fun withExpense(ref: String?): Triple<Phone, BackupData, BackupData> {
        val p = phone()
        p.d.ledger.save(Expense(EntryId("A"), 1_000, AccountId("main"), Money(amount, inr), listOf(CategorySplit(CategoryId("Cash"), Money(amount, inr)))), EntryDetails(merchant = "ATM", source = "sms", sourceRef = ref))
        val old = p.d.backup.export()
        p.changes.change("A", Transfer(EntryId("B"), 1_000, AccountId("main"), AccountId("cash"), Money(amount, inr)), "ATM", emptyList())
        return Triple(p, old, p.d.backup.export())
    }

    @Test
    fun theBackupCarriesARecordOfTheReplacedEntryAndAnOlderOneDoesNot() {
        val (_, old, new) = withExpense("UPI-0")
        assertEquals(emptyList(), old.replaced)
        assertEquals(listOf(BackupReplaced("A", "B", "CHANGED")), new.replaced)
        assertEquals(listOf("B"), new.entries.map { it.id }, "the replaced entry itself is still not in a backup: only the record of it")
    }

    @Test
    fun withAReferenceRestoringTheOlderBackupAfterTheNewerOneOrTheOtherWayRoundNeverFailsAndNeverDoublesTheEntry() {
        val (_, old, new) = withExpense("UPI-1")
        // newer first, then older: the transfer stays, the old expense is the entry the type change replaced and is left out
        val a = phone(); a.d.backup.restore(new)
        assertEquals(1, a.d.backup.preview(old).replacedNotRestored, "the preview says so before anything is restored")
        val report = a.d.backup.restore(old)
        assertEquals(listOf("B"), a.live.map { it.id }); assertEquals(0, report.added.entries); assertEquals(1, report.replacedNotRestored); assertEquals(0, report.alreadyHere.entries)
        // older first, then newer: the type change is carried over: the old expense is taken away (deleted, not lost: it is a soft delete) and the transfer takes its place and its reference
        val b = phone(); b.d.backup.restore(old)
        val preview = b.d.backup.preview(new)
        assertEquals(1, preview.replacedRemoved); assertEquals(1, preview.added.entries)
        val second = b.d.backup.restore(new)
        assertEquals(listOf("B"), b.live.map { it.id }); assertEquals(1, second.added.entries); assertEquals(1, second.replacedRemoved)
        assertEquals("UPI-1", b.live.single().source_ref, "the transfer holds the bank reference now")
        assertEquals(1L, b.d.db.schemaQueries.entryById("A").executeAsOne().deleted, "the old expense is a deleted row here, as on the phone that made the change")
        // and again, in any order: nothing more happens
        for (data in listOf(old, new, old, new)) b.d.backup.restore(data)
        assertEquals(listOf("B"), b.live.map { it.id })
    }

    @Test
    fun onThePhoneThatMadeTheChangeEitherBackupAddsNothing() {
        val (p, old, new) = withExpense("UPI-2")
        val report = p.d.backup.restore(old)
        assertEquals(listOf("B"), p.live.map { it.id }, "the original is deleted here, and an entry that is here (deleted too) is left as it is"); assertEquals(0, report.added.entries); assertEquals(0, report.replacedNotRestored)
        assertEquals(0, p.d.backup.restore(new).added.entries)
        assertEquals(listOf("B"), p.live.map { it.id })
    }

    @Test
    fun withoutAReferenceTheOlderAndNewerBackupsNoLongerDoubleTheEntryInEitherOrder() {
        val (_, old, new) = withExpense(null)
        val a = phone(); a.d.backup.restore(new); a.d.backup.restore(old)
        assertEquals(listOf("B"), a.live.map { it.id }, "newer then older: the replaced expense is not brought back")
        val b = phone(); b.d.backup.restore(old); b.d.backup.restore(new)
        assertEquals(listOf("B"), b.live.map { it.id }, "older then newer: the type change is carried over and the old expense is taken away")
    }

    @Test
    fun aPairMadeOneTransferNoLongerLetsTheOlderBackupBringBackTheAbsorbedIncome() {
        // "Transfer candidates" turns an expense and the income that was its other half into ONE transfer: the expense is replaced by the transfer (it passes its reference on) and the income is
        // absorbed: deleted, and recorded as replaced by the transfer. The income held its own reference ("<ref>/reversal") only as a deleted row, and a backup carries live entries only.
        val p = phone()
        p.d.ledger.save(Expense(EntryId("X"), 1_000, AccountId("main"), Money(amount, inr), listOf(CategorySplit(CategoryId("Cash"), Money(amount, inr)))), EntryDetails(merchant = "Transfer", source = "sms", sourceRef = "UPI-9"))
        p.d.ledger.save(Income(EntryId("I"), 1_100, AccountId("cash"), Money(amount, inr), CategoryId("income:salary")), EntryDetails(merchant = "Transfer received", source = "sms", sourceRef = "UPI-9/reversal"))
        val old = p.d.backup.export()
        val c = p.changes.change("X", Transfer(EntryId("T"), 1_000, AccountId("main"), AccountId("cash"), Money(amount, inr)), "Transfer", emptyList())
        p.changes.absorb("I", c.newId)
        val new = p.d.backup.export()
        assertEquals(setOf(BackupReplaced("X", "T", "CHANGED"), BackupReplaced("I", "T", "PAIRED")), new.replaced.toSet())
        val a = phone(); a.d.backup.restore(new); a.d.backup.restore(old)
        assertEquals(listOf("T"), a.live.map { it.id }, "neither the income nor the expense is back next to the transfer")
        val b = phone(); b.d.backup.restore(old); b.d.backup.restore(new)
        assertEquals(listOf("T"), b.live.map { it.id }, "older then newer: both halves of the old pair are taken away for the one transfer")
        assertEquals("UPI-9", b.live.single().source_ref)
        // restoring only the newest backup, or onto an empty ledger, is exact, as before
        val c2 = phone(); c2.d.backup.restore(new)
        assertEquals(listOf("T"), c2.live.map { it.id })
        // Undo of the pair on the phone that made it: both halves are back and no record is left, so a backup made now carries both again
        p.changes.undo(c); p.d.ledger.restore(EntryId("I"))
        assertNull(p.d.ledger.replaced.by("X")); assertNull(p.d.ledger.replaced.by("I"))
        assertEquals(emptyList(), p.d.backup.export().replaced)
        assertEquals(setOf("X", "I"), p.live.map { it.id }.toSet())
    }
}
