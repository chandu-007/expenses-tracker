package app.expenses.core.storage

import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.storage.db.ExpensesDb

/**
 * What an add from To review does besides saving the entry, and how to take it back exactly:
 *  - [join]: the waiting text is the OTHER LEG of an entry that is already in the ledger (the credit text of a transfer whose debit text was added, the card's "payment received" text of a bill
 *    payment already recorded): nothing new is recorded, the text is added to the entry's words ("Where this came from" then shows both) and the waiting item is marked added;
 *  - [undo]: puts back what [join] or an add that created an entry ([Held.created]) changed: the new entry goes (to the bin, its bank reference let go, its words removed) or the entry's words
 *    are as they were, a bill the add marked paid is FOUND or TRACKED again with its words, and the waiting item waits again holding its text.
 * Each is one all-or-nothing step. Not thread-safe: use through the process-wide write lock.
 */
class CaptureAdds(private val db: ExpensesDb, private val ledger: LedgerStore, private val inbox: InboxStore, private val origins: OriginStore, private val bills: BillStore) {
    private val q get() = db.schemaQueries

    /**
     * What an add changed. [created] is true when [entryId] was made by the add (Undo deletes it), false for a join (Undo restores [originBefore], the entry's words before, or removes them when
     * it had none). [bill] is the bill the add marked paid, as it was before.
     */
    class Held(val itemId: String, val itemBody: String?, val entryId: String, val created: Boolean, val originBefore: Origin?, val bill: Bill?)

    /**
     * The waiting item [itemId] is added to the entry [entryId]'s words and marked added. Throws [IllegalArgumentException], changing nothing, when the item is no longer waiting or the entry is gone.
     */
    fun join(itemId: String, entryId: String): Held = ledger.atomically {
        val item = inbox.get(itemId)?.takeIf { it.status == InboxStatus.PENDING } ?: throw IllegalArgumentException("That item was already handled.")
        val row = q.entryById(entryId).executeAsOneOrNull()
        require(row != null && row.deleted == 0L) { "That entry is no longer there." }
        val before = origins.get(entryId)
        val words = listOfNotNull(before?.text?.takeIf { it.isNotBlank() }, item.body?.takeIf { it.isNotBlank() }).joinToString("\n\n")
        origins.put(entryId, before?.kind ?: OriginStore.kindOf(item.source, item.sender), before?.sender ?: item.sender, words, before?.receivedAt ?: item.receivedAt)
        require(inbox.confirm(itemId, entryId)) { "That item was already handled." }
        Held(itemId, item.body, entryId, created = false, originBefore = before, bill = null)
    }

    /** Puts back what [held] changed (see the class). Never throws for a thing that is already as it was. */
    fun undo(held: Held) {
        ledger.atomically {
            if (held.created) {
                ledger.delete(EntryId(held.entryId))
                q.clearSourceRef(held.entryId)
                origins.remove(held.entryId)
            } else {
                val b = held.originBefore
                if (b != null) origins.put(held.entryId, b.kind, b.sender, b.text, b.receivedAt) else origins.remove(held.entryId)
            }
            held.bill?.let { bills.unpayTo(it.id, it.status, it.body) }
            inbox.unconfirm(held.itemId, held.itemBody)
        }
    }
}
