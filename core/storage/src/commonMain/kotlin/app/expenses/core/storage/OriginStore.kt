package app.expenses.core.storage

import app.expenses.core.storage.db.ExpensesDb

/** Where an entry was read from. [sender] is the name it came from when there is one (a bank's text header, an app, a mail app); [receivedAt] is epoch millis or null. */
data class Origin(val entryId: String, val kind: OriginKind, val sender: String?, val text: String, val receivedAt: Long?)

enum class OriginKind { SMS, NOTIFICATION, EMAIL, STATEMENT }

/**
 * The original words behind an entry made from a text, notification, email notification or statement line. An entry typed by hand has none.
 * The words stay with the entry for as long as it exists (a deleted entry that is brought back gets them back); a backup carries them for live entries.
 * Not thread-safe: use through the process-wide write lock.
 */
class OriginStore(private val db: ExpensesDb) {
    private val q get() = db.schemaQueries

    /** Remembers (or replaces) the origin of [entryId]. Blank words are not an origin and are ignored. */
    fun put(entryId: String, kind: OriginKind, sender: String?, text: String, receivedAt: Long?) {
        if (text.isBlank()) return
        q.putOrigin(entryId, kind.name, sender?.takeIf { it.isNotBlank() }, text.take(MAX_CHARS), receivedAt)
    }

    fun get(entryId: String): Origin? = q.originOf(entryId).executeAsOneOrNull()?.let(::of)

    fun remove(entryId: String) { q.deleteOrigin(entryId) }

    private fun of(r: app.expenses.core.storage.db.Entry_origin) =
        Origin(r.entry_id, OriginKind.entries.firstOrNull { it.name == r.kind } ?: OriginKind.NOTIFICATION, r.sender, r.text, r.received_at)

    companion object {
        const val MAX_CHARS = 4000

        /**
         * The kind for an inbox item: texts are SMS; notifications are NOTIFICATION, or EMAIL when the sender is a mail app's name (the names the capture side
         * gives to Gmail, Outlook, Yahoo Mail and the like).
         */
        fun kindOf(source: String, sender: String?): OriginKind = when {
            source.equals("SMS", true) -> OriginKind.SMS
            sender != null && sender.trim().uppercase() in EMAIL_SENDERS -> OriginKind.EMAIL
            else -> OriginKind.NOTIFICATION
        }

        /** Sender names the capture side gives to mail apps (storage does not depend on the parsers, so the names are listed here; keep in step with the app-name map in AppSources). */
        val EMAIL_SENDERS = setOf("GMAIL", "OUTLOOK", "YAHOO", "YAHOO MAIL", "PROTON MAIL", "PROTONMAIL", "EMAIL", "MAIL")
    }
}
