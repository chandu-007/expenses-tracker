package app.expenses.core.storage

import app.expenses.core.domain.AttachmentRules
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb

data class Attachment(val id: String, val entryId: String, val name: String, val mime: String, val sizeBytes: Long, val sha256: String, val createdAt: Long)

/** What is attached to which entry. The files themselves are kept by the app; this is only the record of them. */
class AttachmentStore(private val db: ExpensesDb, private val clock: HlcClock, private val nowMillis: () -> Long) {
    private val q get() = db.schemaQueries

    fun list(entryId: String): List<Attachment> = q.attachmentsOf(entryId).executeAsList().map(::of)
    fun get(id: String): Attachment? = q.attachmentById(id).executeAsOneOrNull()?.let(::of)

    /** Records an attachment of a live entry. Throws [IllegalArgumentException] with a reason for the user when it cannot be added. */
    fun add(id: String, entryId: String, name: String, mime: String, sizeBytes: Long, sha256: String) {
        db.transaction {
            val entry = q.entryById(entryId).executeAsOneOrNull()
            require(entry != null && entry.deleted == 0L) { "That entry no longer exists." }
            AttachmentRules.checkAdd(q.attachmentsOf(entryId).executeAsList().size, sizeBytes)?.let { throw IllegalArgumentException(it) }
            q.insertAttachment(id, entryId, AttachmentRules.safeName(name), mime.ifBlank { "application/octet-stream" }, sizeBytes, sha256, nowMillis())
            q.insertOp(clock.now(nowMillis()).encode(), "attachment", id, "entry_id", entryId)
        }
    }

    fun delete(id: String) {
        db.transaction {
            if (q.attachmentById(id).executeAsOneOrNull() != null) {
                q.deleteAttachment(id)
                q.insertOp(clock.now(nowMillis()).encode(), "attachment", id, "deleted", "1")
            }
        }
    }

    /** Every attachment id on record, so files left behind with no record can be found and removed. */
    fun allIds(): Set<String> = q.allAttachmentIds().executeAsList().toSet()

    private fun of(r: app.expenses.core.storage.db.Attachment) = Attachment(r.id, r.entry_id, r.name, r.mime, r.size_bytes, r.sha256, r.created_at)
}
