package app.expenses.core.storage

import app.expenses.core.domain.ExpectMath
import app.expenses.core.storage.db.ExpensesDb

/** What was expected back for one expense and not yet [closed] by the person. [from] is free text, [personId] a person on the list; both may be absent. */
data class Expectation(val entryId: String, val from: String?, val personId: String?, val expectedMinor: Long, val closed: Boolean)

/**
 * An expense with money still expected back: [remainingMinor] is what is left to come ([expectedMinor] at most the expense, less the refunds already recorded against it).
 * [title] is the expense's merchant or note; [currency] its currency code.
 */
data class WaitingBack(
    val entryId: String, val title: String, val occurredAt: Long, val accountId: String?, val currency: String, val amountMinor: Long,
    val expectedMinor: Long, val gotMinor: Long, val remainingMinor: Long, val from: String?, val personId: String?,
)

/**
 * "I expect this back": a small side table beside the entries (an expense that a friend or an employer will pay back). Money that comes back is an ordinary refund of the
 * expense, so the figure left to come is worked out, never stored twice. Closing it ("not expecting it any more") only marks the row, so it can be reopened.
 * Not thread-safe: use through the process-wide write lock.
 */
class ExpectStore(private val db: ExpensesDb) {
    private val q get() = db.schemaQueries

    /** Expects [expectedMinor] back for [entryId], replacing what was there and reopening it. A blank [from] is stored as none. */
    fun set(entryId: String, from: String?, personId: String?, expectedMinor: Long) {
        require(expectedMinor > 0) { "The amount expected back must be positive" }
        q.putExpect(entryId, from?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_FROM), personId?.takeIf { it.isNotEmpty() }, expectedMinor)
    }

    /** Forgets the expectation of [entryId] altogether (the person switched it off on the expense). */
    fun clear(entryId: String) { q.deleteExpect(entryId) }

    fun get(entryId: String): Expectation? = q.expectOf(entryId).executeAsOneOrNull()?.let { Expectation(it.entry_id, it.from_text, it.person_id, it.expected_minor, it.closed == 1L) }

    /** "Not expecting it any more": it leaves the waiting list. False when there was nothing open to close. */
    fun close(entryId: String): Boolean = db.transactionWithResult {
        val before = q.expectOf(entryId).executeAsOneOrNull()
        q.setExpectClosed(1L, entryId)
        before != null && before.closed == 0L
    }

    /** Takes back a [close]. */
    fun reopen(entryId: String) { q.setExpectClosed(0L, entryId) }

    /** Everything still waiting, newest expense first: open, of a live expense, with something left to come. */
    fun waiting(): List<WaitingBack> = q.waitingExpects().executeAsList().sortedWith(compareByDescending<app.expenses.core.storage.db.WaitingExpects> { it.occurred_at }.thenByDescending { it.seq }).mapNotNull { r ->
        val remaining = ExpectMath.remaining(r.expected_minor, r.amount_minor, r.refunded)
        if (remaining <= 0) null
        else WaitingBack(
            r.entry_id, r.merchant ?: r.note ?: "An expense", r.occurred_at, r.account_id, r.currency, r.amount_minor,
            minOf(r.expected_minor, r.amount_minor), r.refunded, remaining, r.from_text, r.person_id,
        )
    }

    /** Every expectation of a live expense, closed ones too: what a backup keeps. */
    fun all(): List<Expectation> = q.allLiveExpects().executeAsList().map { Expectation(it.entry_id, it.from_text, it.person_id, it.expected_minor, it.closed == 1L) }

    /** Puts an expectation back as a backup held it; one already here is left alone. */
    fun restore(e: Expectation) { q.insertExpectRaw(e.entryId, e.from, e.personId, e.expectedMinor, if (e.closed) 1L else 0L) }

    companion object {
        const val MAX_FROM = 80
    }
}
