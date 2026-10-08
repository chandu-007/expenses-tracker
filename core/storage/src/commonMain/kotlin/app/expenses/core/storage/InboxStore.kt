package app.expenses.core.storage

import app.expenses.core.storage.db.ExpensesDb

enum class InboxStatus { PENDING, CONFIRMED, DISMISSED }

/** One captured transaction waiting for review. [body] is present only while pending. Amounts in minor units of [currency]. */
data class InboxItem(
    val id: String, val source: String, val sender: String, val issuer: String, val receivedAt: Long, val direction: String,
    val amountMinor: Long, val currency: String, val txnDate: String?, val reference: String?, val accountLast4: String?, val viaUpi: Boolean,
    val confidence: String, val body: String?, val status: InboxStatus, val entryId: String?,
)

/** What capture decided about one text, for [InboxStore.recordEvent]. The text itself is never recorded. */
enum class CaptureEventOutcome { CAPTURED, DUPLICATE, PERSONAL_SENDER, NOT_ALLOWLISTED, OTP, NOT_A_TRANSACTION, DECLINED }

/**
 * Transactions found in texts and notifications, held until confirmed or dismissed. Nothing here is in the ledger. Capture is idempotent
 * through a fingerprint of the source, sender and normalised text, so a text delivered twice is one item. Not thread-safe: use through the
 * process-wide write lock.
 */
class InboxStore(private val db: ExpensesDb) {
    private val q get() = db.schemaQueries

    /** Adds [item] as pending unless the same text is already here (in any state). Returns false for the repeat. */
    fun add(item: InboxItem, fingerprint: String): Boolean = db.transactionWithResult {
        q.insertInbox(item.id, item.source, item.sender, item.issuer, item.receivedAt, item.direction, item.amountMinor, item.currency, item.txnDate, item.reference,
            item.accountLast4, if (item.viaUpi) 1L else 0L, item.confidence, item.body, fingerprint)
        q.inboxById(item.id).executeAsOneOrNull() != null
    }

    fun list(status: InboxStatus = InboxStatus.PENDING, limit: Long = 500): List<InboxItem> = q.inboxByStatus(status.name, limit).executeAsList().map(::of)

    fun get(id: String): InboxItem? = q.inboxById(id).executeAsOneOrNull()?.let(::of)

    fun pendingCount(): Long = q.inboxCount(InboxStatus.PENDING.name).executeAsOne()

    /** Marks a pending item confirmed (with the entry it became) and forgets its text. False when it was not pending. */
    fun confirm(id: String, entryId: String): Boolean = resolve(id, InboxStatus.CONFIRMED, entryId)

    fun dismiss(id: String): Boolean = resolve(id, InboxStatus.DISMISSED, null)

    /** Puts a resolved item back to pending (its text is gone, so only the parsed fields remain). */
    fun reopen(id: String) { q.reopenInbox(id) }

    private fun resolve(id: String, status: InboxStatus, entryId: String?): Boolean = db.transactionWithResult {
        val before = q.inboxById(id).executeAsOneOrNull()
        q.resolveInbox(status.name, entryId, id)
        before != null && before.status == InboxStatus.PENDING.name
    }

    fun recordEvent(at: Long, senderRoot: String?, outcome: CaptureEventOutcome) {
        db.transaction {
            q.insertCaptureEvent(at, senderRoot ?: "", outcome.name)
            q.pruneCaptureEvents(at - EVENT_KEEP_MS)
        }
    }

    private fun of(r: app.expenses.core.storage.db.Inbox) = InboxItem(
        r.id, r.source, r.sender, r.issuer, r.received_at, r.direction, r.amount_minor, r.currency, r.txn_date, r.reference, r.account_last4,
        r.via_upi == 1L, r.confidence, r.body, InboxStatus.valueOf(r.status), r.entry_id,
    )

    companion object { const val EVENT_KEEP_MS = 90L * 86_400_000 }
}
