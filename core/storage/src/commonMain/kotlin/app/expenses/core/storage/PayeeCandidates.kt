package app.expenses.core.storage

import app.expenses.core.domain.SimilarCandidate
import app.expenses.core.domain.SimilarEntries
import app.expenses.core.storage.db.ExpensesDb

/** What one lookup of a payee's earlier entries returns at most (the `LIMIT` of the `similarCandidates` query). */
const val PAYEE_CANDIDATE_CAP = 300

/**
 * The earlier entries of kind [kind] ("EXPENSE" or "INCOME") whose merchant or note shares a word with [label], newest first, as [SimilarCandidate]s: an indexed lookup of the payee's own words, never a
 * scan of the ledger, at most [PAYEE_CANDIDATE_CAP]. Callers keep only the ones that really are the payee ([SimilarEntries.matches], [app.expenses.core.domain.AmountBands]).
 *
 * When the lookup is full and the label has more than one word, a word the payee shares with many others ("shop", "store", "pay") may have crowded its own entries out of the newest 300, so entries
 * holding ALL its words are asked for as well (a second indexed lookup, only for such payees) and come first.
 */
fun ExpensesDb.payeeCandidates(label: String, kind: String): List<SimilarCandidate> {
    val words = SimilarEntries.searchWords(label)
    if (words.isEmpty()) return emptyList()
    fun ask(joiner: String) = schemaQueries.similarCandidates(kind, words.joinToString(joiner) { ftsPhrase(it) }).executeAsList()
        .map { SimilarCandidate(it.id, it.label, it.category_id.orEmpty(), it.amount_minor, it.occurred_at, it.currency) }
    val any = ask(" OR ")
    if (any.size < PAYEE_CANDIDATE_CAP || words.size < 2) return any
    return (ask(" AND ") + any).distinctBy { it.id }
}
