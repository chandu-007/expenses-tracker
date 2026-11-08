package app.expenses.core.storage

import app.expenses.core.storage.db.ExpensesDb

/**
 * Moves a bill an earlier version made from the confirmation of a payment out of Bills and into To review, where the text is a bill payment ([app.expenses.core.parsers.BillPaymentRepair] says which
 * bills; only the person's tap starts it, never the app on its own). One all-or-nothing step: the text waits in To review as a pending item holding the original words (nothing is lost), and the bill is
 * skipped (the ordinary skip, so its dedupe key still keeps the same text from becoming a bill again; the bill row is not deleted). [undo] puts both back. Not thread-safe: use through the process-wide
 * write lock.
 */
class BillRepairs(private val db: ExpensesDb, private val inbox: InboxStore, private val bills: BillStore) {
    /** What one move changed, so [undo] can put it back: the bill and the words it held, and the pending item made for them (null when the same text was already in To review or was added before). */
    class Moved(val billId: String, val billBody: String?, val itemId: String?)

    /**
     * Moves [bill] (which must still be FOUND) to To review as [item], fingerprinted [fingerprint] like any captured text. Throws [IllegalArgumentException], changing nothing, when the bill is no longer
     * FOUND (the person acted on it since it was looked at).
     */
    fun move(bill: Bill, item: InboxItem, fingerprint: String): Moved = db.transactionWithResult {
        val now = bills.get(bill.id)
        require(now != null && now.status == BillStatus.FOUND) { "That bill is no longer waiting in Bills." }
        val added = inbox.add(item, fingerprint)
        require(bills.dismiss(bill.id)) { "That bill is no longer waiting in Bills." }
        Moved(bill.id, now.body, item.id.takeIf { added })
    }

    /**
     * Puts a [move] back: the bill is FOUND again with its words, and the pending item the move made is taken away again while nobody has acted on it (so the same text can be moved again; the words are on
     * the bill). An item the person has handled meanwhile is left exactly as they left it.
     */
    fun undo(moved: Moved) {
        db.transaction {
            moved.itemId?.let { inbox.removeIfPending(it) }
            bills.undismiss(moved.billId, BillStatus.FOUND, moved.billBody)
        }
    }
}
