package app.expenses.core.storage

import app.expenses.core.storage.db.ExpensesDb

enum class BillStatus { FOUND, TRACKED, PAID, DISMISSED }

/**
 * A bill found in a text or notification. [kind] is the name of the parser's bill kind (kept as text so this module does not depend on the parsers),
 * [dueDate] is `yyyy-MM-dd` or null when the message gave none, [title] is how it is shown ("HDFC Bank credit card"). [body] is the message, kept while the bill is FOUND or TRACKED (so an entry recorded from it keeps its words), cleared when it is paid or dismissed;
  * [dedupeKey] identifies the bill across reminders.
 */
data class Bill(
    val id: String, val source: String, val sender: String, val issuer: String, val kind: String, val title: String,
    val amountMinor: Long, val minDueMinor: Long?, val currency: String, val dueDate: String?, val accountLast4: String?, val reference: String?, val autoDebit: Boolean,
    val receivedAt: Long, val body: String?, val status: BillStatus, val entryId: String?, val paidAt: Long?, val dedupeKey: String,
)

/**
 * Bills the person is told about and keeps track of. Nothing here is in the ledger: a bill becomes an entry only when the person records its payment, and even
 * then the entry is made by the caller. Not thread-safe: use through the process-wide write lock.
 */
class BillStore(private val db: ExpensesDb) {
    private val q get() = db.schemaQueries

    /** Adds [bill] as FOUND unless the same bill (same [Bill.dedupeKey]) is already here in any state. Returns false for the repeat. */
    fun add(bill: Bill): Boolean = db.transactionWithResult {
        q.insertBill(
            bill.id, bill.source, bill.sender, bill.issuer, bill.kind, bill.title, bill.amountMinor, bill.minDueMinor, bill.currency, bill.dueDate, bill.accountLast4, bill.reference,
            if (bill.autoDebit) 1L else 0L, bill.receivedAt, bill.body, bill.status.name, bill.entryId, bill.paidAt, bill.dedupeKey,
        )
        q.billById(bill.id).executeAsOneOrNull() != null
    }

    fun get(id: String): Bill? = q.billById(id).executeAsOneOrNull()?.let(::of)

    /** In order of due date, the ones with no date last. */
    fun list(status: BillStatus, limit: Long = 200): List<Bill> = q.billsByStatus(status.name, limit).executeAsList().map(::of)

    fun count(status: BillStatus): Long = q.billCount(status.name).executeAsOne()

    fun all(): List<Bill> = q.allBills().executeAsList().map(::of)

    /** FOUND to TRACKED: the person added it. Its text is forgotten. False when it was not FOUND. */
    fun track(id: String): Boolean = move(id, BillStatus.FOUND, BillStatus.TRACKED)

    /** Skipped. From FOUND or TRACKED; a PAID bill is left as it is. */
    fun dismiss(id: String): Boolean = move(id, BillStatus.FOUND, BillStatus.DISMISSED) || move(id, BillStatus.TRACKED, BillStatus.DISMISSED)

    /** Takes back a [dismiss]: the bill goes back to [status] (FOUND or TRACKED, what it was) holding [body]. False when it was not skipped. */
    fun undismiss(id: String, status: BillStatus, body: String?): Boolean = db.transactionWithResult {
        require(status == BillStatus.FOUND || status == BillStatus.TRACKED) { "A skipped bill goes back to found or tracked" }
        val before = q.billById(id).executeAsOneOrNull()
        q.reviveDismissedBill(status.name, body, id)
        before != null && before.status == BillStatus.DISMISSED.name
    }

    /** Marks a FOUND or TRACKED bill paid, with the entry that recorded the payment when there is one. False when it was already PAID or skipped. */
    fun markPaid(id: String, entryId: String?, at: Long): Boolean = db.transactionWithResult {
        val before = q.billById(id).executeAsOneOrNull()
        q.payBill(entryId, at, id)
        before != null && before.status in setOf(BillStatus.FOUND.name, BillStatus.TRACKED.name)
    }

    /** PAID back to TRACKED. The entry that was made stays; deleting it is the person's call. */
    fun unpay(id: String) { q.unpayBill(id) }

    /**
     * Keeps paid bills in step with the entries that paid them: a bill whose entry was deleted goes back to tracked, and one whose entry is live again
     * (undone, restored, or arrived by sync) is paid again. The link is kept either way. True when any bill changed.
     */
    fun reconcileWithEntries(): Boolean {
        if (!q.billsOutOfStep().executeAsOne()) return false // the usual case: one read, no write
        return db.transactionWithResult {
        val before = q.billCount(BillStatus.PAID.name).executeAsOne()
        q.releaseBillsOfRemovedEntries()
        q.repayBillsOfRestoredEntries()
        q.billCount(BillStatus.PAID.name).executeAsOne() != before
        }
    }

    fun setDueDate(id: String, isoDate: String?) { q.setBillDue(isoDate, id) }

    /** Puts a bill back exactly as a backup held it; one already here is left alone. */
    fun restore(bill: Bill) { add(bill) }

    private fun move(id: String, from: BillStatus, to: BillStatus): Boolean = db.transactionWithResult {
        val before = q.billById(id).executeAsOneOrNull()
        q.moveBill(to.name, id, from.name)
        before != null && before.status == from.name
    }

    private fun of(r: app.expenses.core.storage.db.Bill) = Bill(
        r.id, r.source, r.sender, r.issuer, r.kind, r.title, r.amount_minor, r.min_due_minor, r.currency, r.due_date, r.account_last4, r.reference, r.auto_debit == 1L,
        r.received_at, r.body, BillStatus.valueOf(r.status), r.entry_id, r.paid_at, r.dedupe_key,
    )
}
