package app.expenses.core.storage

import app.expenses.core.storage.db.Entry
import app.expenses.core.storage.db.ExpensesDb

private const val SORT_FIRST_MAX_MATCHES = 2000L
private const val MIN_INDEXED_LENGTH = 3

/** Wraps user text as a single FTS5 phrase so operators such as quotes, `-` or `OR` are searched literally. */
fun ftsPhrase(text: String): String = "\"" + text.replace("\"", "\"\"") + "\""

/**
 * Builds the FTS5 expression for what the user typed: every word of 3 or more characters must appear
 * somewhere in the merchant or note, in any order. Each word is quoted so operators are searched
 * literally. Words shorter than 3 characters cannot use the trigram index and are ignored when other words
 * exist, so those searches can return a superset of what was typed. Returns null when no word is long
 * enough.
 */
fun ftsQuery(text: String): String? {
    val words = text.split(Regex("\\s+")).filter { it.length >= MIN_INDEXED_LENGTH }
    return if (words.isEmpty()) null else words.joinToString(" ") { ftsPhrase(it) }
}

/**
 * Searches merchant and note text, newest first by transaction date, returning at most [limit] entries.
 * A row matches when it contains every word of 3 or more characters in [text], in any order, across both
 * fields. Text with no such word (for example "ol") falls back to a substring scan.
 * Results are exact for the words used: the plan changes with the number of matches, never the answer.
 */
fun ExpensesDb.search(text: String, limit: Long = 50): List<Entry> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    val q = schemaQueries
    val fts = ftsQuery(trimmed) ?: return q.searchShort(trimmed, limit).executeAsList()
    val matches = q.countMatches(fts, SORT_FIRST_MAX_MATCHES + 1).executeAsOne()
    return if (matches <= SORT_FIRST_MAX_MATCHES) {
        q.searchEntries(fts, limit).executeAsList()
    } else {
        q.searchEntriesWalk(fts, limit).executeAsList()
    }
}
