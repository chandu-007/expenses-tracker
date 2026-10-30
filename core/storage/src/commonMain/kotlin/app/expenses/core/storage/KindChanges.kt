package app.expenses.core.storage

import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.storage.db.ExpensesDb
import app.expenses.core.domain.ledger.Entry as DomainEntry

/**
 * "Change the type" of an entry: an expense that is really a cash withdrawal, an income that was a transfer. A ledger entry's kind never changes in place (its history is
 * one story about one kind of thing), so it is done as one all-or-nothing step on the database, the same swap as [CaptureTransfers.replaceEarlier]:
 * the old entry is soft-deleted (the ordinary delete, so it can be restored) and a new entry of the new kind takes its place, carrying its date, account, title, note, tags,
 * source, bank reference, original text (origin) and attachments. [undo] puts the old entry back exactly (same id, same fields, history intact) and removes the new one.
 * Nothing is learned from a change (no rule, no "last used" memory). Not thread-safe: use through the process-wide write lock.
 */
class KindChanges(private val db: ExpensesDb, private val ledger: LedgerStore, private val origins: OriginStore, private val expects: ExpectStore, private val attachments: AttachmentStore) {
    private val q get() = db.schemaQueries

    /** What [change] did, so [undo] can put it back: the entry that replaced the old one, the old one, and the bank reference the old one held (it passes to the new one meanwhile). */
    data class Changed(val newId: String, val oldId: String, val oldSourceRef: String?)

    /**
     * Replaces the live expense, income or transfer [oldId] with [entry] (of another kind, with its own new id). [merchant] is the title and [tags] the tags the person left on
     * the form; the old note (or, when it has none, [fallbackNote]: the note of the other half of a move that is absorbed), source and bank reference carry over, so "Where this came from" still works and a text delivered again is still seen as already added.
     * Throws [IllegalArgumentException], changing nothing, when the entry is gone, is not a plain expense, income or transfer, is shared, in another currency, tied to a bill or a repeating
     * item, has "I expect this back" on, or [entry] breaks a ledger rule (two different accounts for a transfer, and so on).
     */
    fun change(oldId: String, entry: DomainEntry, merchant: String?, tags: List<String>, fallbackNote: String? = null): Changed = ledger.atomically {
        val row = checked(oldId)
        val columns = EntryMapping.columns(entry)
        require(columns.kind != row.kind && (columns.kind == "EXPENSE" || columns.kind == "INCOME" || columns.kind == "TRANSFER")) { "Choose another type." }
        require(entry.id.value != oldId) { "The changed entry needs its own id." }
        require(columns.currency == row.currency) { "This entry is shared or in another currency, so its type can't be changed here." }

        val reference = row.source_ref
        // the unique index on (source, reference) covers deleted entries too: the new entry takes the reference over, and Undo hands it back
        if (reference != null) ledger.setSourceRef(EntryId(oldId), null)
        val origin = origins.get(oldId)
        ledger.delete(EntryId(oldId))
        ledger.save(entry, EntryDetails(merchant = merchant?.takeIf { it.isNotBlank() }, note = if (row.note.isNullOrBlank()) fallbackNote?.takeIf { it.isNotBlank() } ?: row.note else row.note, source = row.source, sourceRef = reference, tags = tags))
        if (origin != null) origins.put(entry.id.value, origin.kind, origin.sender, origin.text, origin.receivedAt)
        attachments.move(oldId, entry.id.value)
        Changed(entry.id.value, oldId, reference)
    }

    /** Why [oldId] cannot have its type changed (the message for the person), or null when it can: what [change] checks of the old entry, without writing anything. */
    fun refusal(oldId: String): String? = try { checked(oldId); null } catch (e: IllegalArgumentException) { e.message }

    private fun checked(oldId: String): app.expenses.core.storage.db.Entry {
        val row = q.entryById(oldId).executeAsOneOrNull()
        require(row != null && row.deleted == 0L) { "This entry no longer exists." }
        require(row.kind == "EXPENSE" || row.kind == "INCOME" || row.kind == "TRANSFER") { "This kind of entry can't change its type." }
        require(expects.get(oldId) == null) { EXPECTED_BACK }
        require(!q.entryPaysBill(oldId).executeAsOne()) { "This entry is marked as the payment of a bill, so its type can't be changed here." }
        require(!q.entryFromRepeating(oldId).executeAsOne()) { "A repeating item added this entry, so its type can't be changed here." }
        require(!q.hasLiveRefunds(oldId).executeAsOne()) { "This expense has refunds, so its type can't be changed here." }
        require(
            row.original_amount_minor == null && (row.to_currency == null || row.to_currency == row.currency && row.to_amount_minor == row.amount_minor) && q.sharesOf(oldId).executeAsList().isEmpty(),
        ) { "This entry is shared or in another currency, so its type can't be changed here." }
        return row
    }

    /**
     * Puts everything [change] did back: the new entry goes (to the bin, like any deleted entry) and lets go of the bank reference and the original text, the photos return to the old
     * entry, and the old entry is restored with its reference. All or nothing.
     */
    fun undo(c: Changed) {
        ledger.atomically {
            // a later change of the new entry (a transfer made an expense, say) passed the reference on again: this one can no longer be undone, and nothing is touched
            if (c.oldSourceRef != null) {
                val old = q.entryById(c.oldId).executeAsOneOrNull() ?: throw IllegalArgumentException(CANT_UNDO)
                val holder = q.entryBySourceRef(old.source, c.oldSourceRef).executeAsOneOrNull()
                require(holder == null || holder.id == c.newId || holder.id == c.oldId) { CANT_UNDO }
            }
            ledger.delete(EntryId(c.newId))
            ledger.setSourceRef(EntryId(c.newId), null)
            origins.remove(c.newId)
            attachments.move(c.newId, c.oldId)
            if (c.oldSourceRef != null) ledger.setSourceRef(EntryId(c.oldId), c.oldSourceRef)
            ledger.restore(EntryId(c.oldId))
        }
    }

    companion object {
        /** Why an expense with "I expect this back" (open, or closed) is not changed: the expectation belongs to the expense. */
        /** Why an Undo is refused: a later change of the same entry has taken over what this one handed on. */
        const val CANT_UNDO = "This change can't be undone any more."

        const val EXPECTED_BACK = "Turn off \"I expect this back\" first: save the expense with it off, then change its type."
    }
}
