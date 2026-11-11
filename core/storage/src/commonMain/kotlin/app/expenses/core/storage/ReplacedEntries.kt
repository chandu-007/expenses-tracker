package app.expenses.core.storage

import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb

/**
 * Which entries another entry took the place of: an expense made a transfer, the income that was the other half of a move, an earlier entry a text made a transfer. A backup carries live
 * entries only, so without this record a restore of an older backup brings the replaced original back next to its replacement and the amount is counted twice. The record stays when the
 * replacement is later changed again (a chain) or deleted: the original must not come back from an old backup either way. It goes when the original is live again ([LedgerStore.restore]:
 * Undo of the change), and each change is logged like any other field (`entry_replaced`, `replaced_by`; empty when the record goes) so history and sync see it.
 * Not thread-safe: use through the process-wide write lock.
 */
class ReplacedEntries(private val db: ExpensesDb, private val clock: HlcClock, private val nowMillis: () -> Long) {
    private val q get() = db.schemaQueries

    /** Records that [replacedBy] took the place of [entryId] (a record already there is replaced). */
    fun mark(entryId: String, replacedBy: String, reason: String) {
        require(entryId.isNotEmpty() && replacedBy.isNotEmpty() && entryId != replacedBy) { "An entry cannot replace itself." }
        db.transaction {
            q.putReplaced(entryId, replacedBy, reason)
            q.insertOp(clock.now(nowMillis()).encode(), ENTITY, entryId, FIELD, replacedBy)
        }
    }

    /** Records one that came from a backup: kept as it is when this phone already has a record for [entryId]. Returns whether it was added. */
    fun adopt(entryId: String, replacedBy: String, reason: String): Boolean = db.transactionWithResult {
        if (q.replacedOf(entryId).executeAsOneOrNull() != null) return@transactionWithResult false
        q.adoptReplaced(entryId, replacedBy, reason)
        q.insertOp(clock.now(nowMillis()).encode(), ENTITY, entryId, FIELD, replacedBy)
        true
    }

    /** Forgets the record of [entryId] (it is live again). No-op when there is none. */
    fun clear(entryId: String) {
        db.transaction {
            if (q.replacedOf(entryId).executeAsOneOrNull() == null) return@transaction
            q.deleteReplaced(entryId)
            q.insertOp(clock.now(nowMillis()).encode(), ENTITY, entryId, FIELD, null)
        }
    }

    /** The entry that took the place of [entryId], or null when it was not replaced. */
    fun by(entryId: String): String? = q.replacedOf(entryId).executeAsOneOrNull()?.replaced_by

    companion object {
        const val ENTITY = "entry_replaced"
        const val FIELD = "replaced_by"

        /** The type of the entry was changed (the editor, "Make transfer" on a selection, Transfer candidates): the old entry was replaced by a new one of the other type. */
        const val CHANGED = "CHANGED"

        /** The income that was the other half of a move between two own accounts, absorbed into the one transfer that replaced the expense (Transfer candidates). */
        const val PAIRED = "PAIRED"

        /** An earlier expense or income that a text in To review turned into a transfer. */
        const val MATCHED = "MATCHED"
    }
}
