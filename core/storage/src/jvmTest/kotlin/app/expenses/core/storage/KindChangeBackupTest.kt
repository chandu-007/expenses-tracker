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

/** What restoring backups made before and after an entry's type was changed does. Merging is by entry id, and by bank reference. */
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
    fun withAReferenceRestoringTheOlderBackupAfterTheNewerOneOrTheOtherWayRoundNeverFailsAndNeverDoublesTheEntry() {
        val (_, old, new) = withExpense("UPI-1")
        // newer first, then older: the transfer stays, the old expense is the same transaction and is left out
        val a = phone(); a.d.backup.restore(new)
        val report = a.d.backup.restore(old)
        assertEquals(listOf("B"), a.live.map { it.id }); assertEquals(1, report.alreadyHere.entries); assertEquals(0, report.added.entries)
        assertEquals(1, a.d.backup.preview(old).alreadyHere.entries)
        // older first, then newer: no error either; the entry that is there stays (the older one)
        val b = phone(); b.d.backup.restore(old)
        val second = b.d.backup.restore(new)
        assertEquals(listOf("A"), b.live.map { it.id }); assertEquals(1, second.alreadyHere.entries)
    }

    @Test
    fun onThePhoneThatMadeTheChangeTheOlderBackupAddsNothing() {
        val (p, old, _) = withExpense("UPI-2")
        val report = p.d.backup.restore(old)
        assertEquals(listOf("B"), p.live.map { it.id }, "the original is deleted here, and an entry that is here (deleted too) is left as it is"); assertEquals(0, report.added.entries)
    }

    @Test
    fun withoutAReferenceTheOlderAndNewerBackupsBothBringTheirEntryWhichIsADoubleCountThatNeedsAFormatChangeToAvoid() {
        val (_, old, new) = withExpense(null)
        val a = phone(); a.d.backup.restore(new); a.d.backup.restore(old)
        assertEquals(setOf("A", "B"), a.live.map { it.id }.toSet(), "documented in BACKUP_FORMAT.md: restore the newest backup only, or onto an empty ledger")
    }

    @Test
    fun aPairMadeOneTransferLetsTheOlderBackupBringBackTheAbsorbedIncomeWhichIsADoubleCountDocumentedInBackupFormat() {
        // "Transfer candidates" turns an expense and the income that was its other half into ONE transfer: the expense is replaced by the transfer (it passes its reference on) and the income is deleted.
        // The deleted income holds its own reference ("<ref>/reversal") only as a deleted row, and a backup carries live entries only, so a newer backup does not mention it.
        // Restoring the older backup after the newer one then re-adds the income: nothing live holds its reference. Documented in BACKUP_FORMAT.md ("Changing an entry's type"):
        // pair conversions are outside the "nothing is doubled" guarantee; restore only the newest backup, or restore onto an empty ledger. No format change was made for it.
        val p = phone()
        p.d.ledger.save(Expense(EntryId("X"), 1_000, AccountId("main"), Money(amount, inr), listOf(CategorySplit(CategoryId("Cash"), Money(amount, inr)))), EntryDetails(merchant = "Transfer", source = "sms", sourceRef = "UPI-9"))
        p.d.ledger.save(Income(EntryId("I"), 1_100, AccountId("cash"), Money(amount, inr), CategoryId("income:salary")), EntryDetails(merchant = "Transfer received", source = "sms", sourceRef = "UPI-9/reversal"))
        val old = p.d.backup.export()
        p.changes.change("X", Transfer(EntryId("T"), 1_000, AccountId("main"), AccountId("cash"), Money(amount, inr)), "Transfer", emptyList())
        p.d.ledger.delete(EntryId("I"))
        val new = p.d.backup.export()
        val a = phone(); a.d.backup.restore(new); a.d.backup.restore(old)
        assertEquals(setOf("T", "I"), a.live.map { it.id }.toSet(), "the income is back next to the transfer: money in counted twice")
        // restoring only the newest backup, or onto an empty ledger, is exact
        val b = phone(); b.d.backup.restore(new)
        assertEquals(listOf("T"), b.live.map { it.id })
    }
}
