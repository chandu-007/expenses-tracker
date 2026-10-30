package app.expenses.core.storage

import app.expenses.core.domain.CandidateAccount
import app.expenses.core.domain.CandidateEntry
import app.expenses.core.domain.TransferCandidateList
import app.expenses.core.domain.TransferCandidates
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.db.ExpensesDb

/**
 * Reads the ledger for "Transfer candidates" (expenses and income that are really money moving between the person's own accounts) and keeps the person's "Not a transfer" answers.
 * Reads only: converting is [KindChanges] through the entry actions. The reading is two indexed or coarse-filtered queries and the precise judgement of
 * [TransferCandidates] over what they return, so a ledger of tens of thousands of entries costs a fraction of a second; run it off the main thread.
 * Answers are kept as one setting per entry ("transfer_no:<entry id>"), device-local like the subscription finder's "not a subscription", and are not part of a backup.
 * Not thread-safe: use through the process-wide write lock.
 */
class TransferCandidateStore(private val db: ExpensesDb, private val kindChanges: KindChanges, private val templates: TemplateStore) {
    private val q get() = db.schemaQueries

    /** Most rows each query hands to the judgement: a bound for a ledger full of matching words; newest first, so the newest are always seen. */
    private companion object {
        const val MAX_ROWS = 20_000L
        const val PAIR_WINDOW_MS = 36 * 3_600_000L
        const val PREFIX = "transfer_no:"
    }

    /**
     * The candidates among the live entries in [currency]. [namedEnd] gives the person's own account that the original words of an entry name as the other end of the payment, or null (the caller reads
     * digits and accounts; storage does not know the parsers). Entries that cannot change their type ([KindChanges.refusal]) or were answered "Not a transfer" are not in it.
     */
    fun scan(currency: String, namedEnd: (CandidateEntry) -> String? = { null }): TransferCandidateList {
        val accounts = q.selectAccounts().executeAsList().mapNotNull { a -> AccountType.entries.firstOrNull { it.name == a.type }?.let { CandidateAccount(a.id, a.name, it) } }
        val wording = q.transferWordingRows(currency, MAX_ROWS).executeAsList().map { r ->
            CandidateEntry(r.id, r.kind == "EXPENSE", r.account_id!!, r.occurred_at, r.origin_at, r.amount_minor, r.merchant, r.note, r.origin_text)
        }
        val pairs = q.transferPairRows(PAIR_WINDOW_MS, currency, MAX_ROWS).executeAsList().map { r ->
            CandidateEntry(r.out_id, true, r.out_account!!, r.out_at, r.out_arrived, r.amount_minor, r.out_merchant, r.out_note, r.out_text) to
                CandidateEntry(r.in_id, false, r.in_account!!, r.in_at, r.in_arrived, r.amount_minor, r.in_merchant, r.in_note, r.in_text)
        }
        return TransferCandidates.find(
            wording, pairs, accounts, dismissed(), { id -> kindChanges.refusal(id) != null }, namedEnd,
            otherHalfLive = { e -> otherHalfLive(e.id) }, absorbable = { e -> q.attachmentsOf(e.id).executeAsList().isEmpty() },
        )
    }

    /**
     * Whether the other half of the move [entryId] stands for is already a live entry, so turning this one into a transfer would count the money twice: an entry holding this one's bank
     * reference with "/reversal" added (a failed ATM withdrawal's money coming back), or a live entry of the opposite kind for the same amount on another account within 36 hours (the credit
     * on the destination of a self transfer, captured too late to be paired). False for an entry that is not there.
     */
    fun otherHalfLive(entryId: String): Boolean {
        val e = q.entryById(entryId).executeAsOneOrNull()?.takeIf { it.deleted == 0L && (it.kind == "EXPENSE" || it.kind == "INCOME") } ?: return false
        e.source_ref?.let { ref -> q.entryBySourceRef(e.source, "$ref/reversal").executeAsOneOrNull()?.let { if (it.deleted == 0L) return true } }
        val account = e.account_id ?: return false
        return q.transferOtherHalfNear(if (e.kind == "EXPENSE") "INCOME" else "EXPENSE", e.currency, e.amount_minor, account, e.occurred_at - PAIR_WINDOW_MS, e.occurred_at + PAIR_WINDOW_MS).executeAsOne()
    }

    /** The ids of the entries the person answered "Not a transfer" for. */
    fun dismissed(): Set<String> = templates.settingsWithPrefix(PREFIX).mapTo(HashSet()) { it.first.removePrefix(PREFIX) }

    /** Remembers "Not a transfer" for [ids]. */
    fun dismiss(ids: Collection<String>) = db.transaction { for (id in ids) templates.putSettingIfChanged(PREFIX + id, "1") }

    /** Forgets every "Not a transfer" answer; returns how many there were. */
    fun clearDismissed(): Int = db.transactionWithResult { templates.settingsWithPrefix(PREFIX).also { all -> all.forEach { templates.removeSetting(it.first) } }.size }
}
