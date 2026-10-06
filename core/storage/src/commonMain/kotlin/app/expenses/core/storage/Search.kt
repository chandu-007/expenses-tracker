package app.expenses.core.storage

import app.expenses.core.storage.db.Entry
import app.expenses.core.storage.db.ExpensesDb

private const val SORT_FIRST_MAX_MATCHES = 2000L

/** Wraps user text as a single FTS5 phrase so operators such as quotes, `-` or `OR` are searched literally. */
fun ftsPhrase(text: String): String = "\"" + text.replace("\"", "\"\"") + "\""

/**
 * Searches merchant and note text, newest first by transaction date, returning at most [limit] entries.
 * Text shorter than 3 characters cannot use the trigram index and falls back to a scan.
 * Results are exact: the plan changes with the number of matches, never the answer.
 */
fun ExpensesDb.search(text: String, limit: Long = 50): List<Entry> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    val q = schemaQueries
    if (trimmed.length < 3) return q.searchShort(trimmed, limit).executeAsList()
    val phrase = ftsPhrase(trimmed)
    val matches = q.countMatches(phrase, SORT_FIRST_MAX_MATCHES + 1).executeAsOne()
    return if (matches <= SORT_FIRST_MAX_MATCHES) {
        q.searchEntries(phrase, limit).executeAsList()
    } else {
        q.searchEntriesWalk(phrase, limit).executeAsList()
    }
}
