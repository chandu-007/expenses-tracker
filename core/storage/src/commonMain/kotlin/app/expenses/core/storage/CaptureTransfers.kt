package app.expenses.core.storage

import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.storage.db.ExpensesDb
import app.expenses.core.domain.ledger.EntryId

/** An entry already added from an earlier text that could be one half of a transfer: the answer of [CaptureTransfers.earlier]. [outgoing] is true for an expense, false for income. */
data class EarlierEntry(
    val entryId: String, val outgoing: Boolean, val accountId: String, val occurredAt: Long, val merchant: String?,
    val originText: String?, val originSender: String?, val originKind: OriginKind?, val receivedAt: Long?,
)

/**
 * What "this is a transfer between my own accounts" does to the books, each as one all-or-nothing step on the database:
 *  - [addPair]: two texts waiting in To review become ONE transfer entry (neither is also recorded as an expense or income);
 *  - [replaceEarlier]: a text waiting in To review is the other half of something the person already added as an expense or income, so the earlier entry is deleted (the
 *    ordinary soft delete: it can be restored) and one transfer is added in its place; [undoReplace] puts that back exactly.
 * Nothing here decides *whether* two things are one transfer (that is [app.expenses.core.domain.TransferPairs]) and nothing runs without the person asking.
 * Not thread-safe: use through the process-wide write lock.
 */
class CaptureTransfers(private val db: ExpensesDb, private val ledger: LedgerStore, private val inbox: InboxStore, private val origins: OriginStore) {
    private val q get() = db.schemaQueries

    private companion object { const val LATE_TEXT_MS = 48 * 3_600_000L }

    /**
     * Entries read from a text, of exactly [amountMinor] in [currency], that happened within [windowMs] of [nearMillis] and that nothing else is tied to (no shared part, refund, bill, repeat
     * or "expecting this back"). Both an expense (money out) and income (money in) are returned; the caller keeps the half it needs.
     */
    fun earlier(amountMinor: Long, currency: String, nearMillis: Long, windowMs: Long): List<EarlierEntry> =
        // the database range is on when the entry happened (an index range scan, so a long ledger costs nothing); a text that arrived late is placed on the bank's date, up to two days from its arrival
        q.transferCandidates(amountMinor, currency, nearMillis - windowMs - LATE_TEXT_MS, nearMillis + windowMs + LATE_TEXT_MS).executeAsList()
            .filter { r -> (r.origin_at ?: r.occurred_at).let { it >= nearMillis - windowMs && it < nearMillis + windowMs } }
            .map { r -> EarlierEntry(r.id, r.kind == "EXPENSE", r.account_id!!, r.occurred_at, r.merchant, r.origin_text, r.origin_sender, r.origin_kind?.let { k -> OriginKind.entries.firstOrNull { it.name == k } }, r.origin_at) }

    /** What [replaceEarlier] changed, so [undoReplace] can put it back: the transfer, the entry it replaced, and the item (with the text it had) that was added. */
    data class Replaced(val transferId: String, val earlierId: String, val itemId: String, val itemBody: String?)

    /**
     * Records [transfer] for the pending items [outId] (money out) and [intoId] (money in) and marks both added. The two texts are kept as the entry's origin, one after the
     * other. Throws [IllegalArgumentException], changing nothing, when either item is no longer waiting, or the reference is already in the ledger.
     * The two texts of an own-account UPI or IMPS transfer usually carry the same bank reference; the transfer is the one entry for both, so it keeps that reference once.
     */
    fun addPair(transfer: Transfer, merchant: String?, sourceRef: String, outId: String, intoId: String) {
        ledger.atomically {
            val out = waiting(outId); val into = waiting(intoId)
            val ref = freeRef(sourceRef, replacing = null)
            ledger.save(transfer, EntryDetails(merchant = merchant, note = null, source = "sms", sourceRef = ref))
            origins.put(transfer.id.value, OriginStore.kindOf(out.source, out.sender), out.sender, joined(out.body, into.body), out.receivedAt)
            require(inbox.confirm(outId, transfer.id.value) && inbox.confirm(intoId, transfer.id.value)) { "That item was already handled." }
        }
    }

    /**
     * Replaces the entry [earlierId] (an expense or income added from an earlier text) and the pending item [itemId] with one [transfer]: the earlier entry is soft-deleted, the
     * transfer saved, the item marked added, and both texts kept as the transfer's origin, all or nothing. The earlier entry's note and tags carry over to the transfer (its category and
     * title stay with the deleted entry, and come back with Undo). Returns what [undoReplace] needs.
     * Throws [IllegalArgumentException], changing nothing, when the item is no longer waiting, the earlier entry is gone or not a plain expense or income, or the reference is taken by
     * some other entry. When the two texts carry the same bank reference (the usual UPI or IMPS case) the earlier entry holds it, and the transfer is saved under "<reference>/transfer".
     */
    fun replaceEarlier(transfer: Transfer, merchant: String?, sourceRef: String, itemId: String, earlierId: String): Replaced = ledger.atomically {
        val item = waiting(itemId)
        val row = q.entryById(earlierId).executeAsOneOrNull()
        require(row != null && row.deleted == 0L && (row.kind == "EXPENSE" || row.kind == "INCOME")) { "The earlier entry is no longer there." }
        val ref = freeRef(sourceRef, replacing = earlierId)
        val before = origins.get(earlierId)
        val tags = ledger.tagsOf(EntryId(earlierId))
        ledger.delete(EntryId(earlierId))
        ledger.save(transfer, EntryDetails(merchant = merchant, note = row.note, source = "sms", sourceRef = ref, tags = tags))
        origins.put(transfer.id.value, OriginStore.kindOf(item.source, item.sender), item.sender, joined(before?.text, item.body), item.receivedAt)
        require(inbox.confirm(itemId, transfer.id.value)) { "That item was already handled." }
        Replaced(transfer.id.value, earlierId, itemId, item.body)
    }

    /**
     * Puts everything [replaceEarlier] did back: the transfer goes (to the bin, like any deleted entry), the earlier entry is restored, and the item waits again with its text. The deleted
     * transfer lets go of its bank reference (a deleted entry otherwise keeps it for ever), so the waiting text can be added normally, or made a transfer again.
     */
    fun undoReplace(r: Replaced) {
        ledger.atomically {
            ledger.delete(EntryId(r.transferId))
            q.clearSourceRef(r.transferId)
            origins.remove(r.transferId)
            ledger.restore(EntryId(r.earlierId))
            inbox.unconfirm(r.itemId, r.itemBody)
        }
    }

    private fun waiting(id: String): InboxItem {
        val item = inbox.get(id) ?: throw IllegalArgumentException("That item is no longer in the inbox.")
        require(item.status == InboxStatus.PENDING) { "That item was already handled." }
        return item
    }

    /**
     * The reference to save the transfer under: [sourceRef] when nothing holds it; "<sourceRef>/transfer" when the entry being replaced ([replacing]) holds it (both texts of the move carry the
     * same bank reference); otherwise the refusal of any other entry that holds it, with the reason.
     */
    private fun freeRef(sourceRef: String, replacing: String?): String {
        val existing = q.entryBySourceRef("sms", sourceRef).executeAsOneOrNull() ?: return sourceRef
        if (replacing != null && existing.id == replacing) return "$sourceRef/transfer".also { refuseIfTaken(it) }
        refuseIfTaken(sourceRef)
        return sourceRef
    }

    private fun refuseIfTaken(sourceRef: String) {
        val existing = q.entryBySourceRef("sms", sourceRef).executeAsOneOrNull() ?: return
        throw IllegalArgumentException(
            if (existing.deleted == 1L) "You deleted an entry from this message earlier, so it is not added again. Skip this one."
            else "An entry from this message is already in your ledger (reference $sourceRef). Skip this one.",
        )
    }

    private fun joined(first: String?, second: String?): String = listOfNotNull(first?.takeIf { it.isNotBlank() }, second?.takeIf { it.isNotBlank() }).joinToString("\n\n")
}
