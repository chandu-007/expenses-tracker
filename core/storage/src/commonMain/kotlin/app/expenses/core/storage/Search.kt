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

/**
 * What to look for. Every part is optional and they all have to hold at once. [minMinor] and [maxMinor] are amounts in minor units
 * of [currency] (required when either is given, since amounts in different currencies cannot be compared). [categoryIds] matches an
 * expense with a split in one of them, or an income in one of them; a caller wanting "Food and its subcategories" lists all of them.
 * [kind] is an entry kind such as "EXPENSE" or "INCOME". The period is half-open: [fromMillis] up to but not including [untilMillis].
 */
data class SearchQuery(
    val text: String = "",
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
    val currency: String? = null,
    val categoryIds: List<String>? = null,
    val kind: String? = null,
    val fromMillis: Long = Long.MIN_VALUE,
    val untilMillis: Long = Long.MAX_VALUE,
) {
    init {
        require((minMinor == null && maxMinor == null) || currency != null) { "An amount range needs a currency" }
        require(categoryIds == null || categoryIds.isNotEmpty()) { "An empty category list matches nothing; leave it null for any category" }
    }

    val hasAnyCondition: Boolean get() = text.isNotBlank() || minMinor != null || maxMinor != null || categoryIds != null || kind != null || fromMillis != Long.MIN_VALUE || untilMillis != Long.MAX_VALUE
}

/**
 * Searches with filters, newest first, at most [limit] entries. The text part behaves exactly as in [search] (words of 3+ characters
 * all have to appear; shorter words fall back to a substring scan), and the filters are applied in the same query, so the answer never
 * depends on the plan chosen. With no text the date index is walked newest first and stops at [limit].
 */
fun ExpensesDb.searchFiltered(query: SearchQuery, limit: Long = 50): List<Entry> {
    val q = schemaQueries
    val text = query.text.trim()
    val ids = query.categoryIds ?: listOf("")
    val filterCats = if (query.categoryIds != null) 1L else 0L
    if (text.isEmpty()) {
        return q.searchFilteredNoText(query.currency, query.minMinor, query.maxMinor, query.kind, query.fromMillis, query.untilMillis, filterCats, ids, limit).executeAsList()
    }
    val fts = ftsQuery(text) ?: return q.searchFilteredShort(likeEscape(text), query.currency, query.minMinor, query.maxMinor, query.kind, query.fromMillis, query.untilMillis, filterCats, ids, limit).executeAsList()
    // Words under 3 characters cannot use the index, so they are checked as substrings on the rows the index finds (the first two; more are rare).
    val shorts = shortWords(text).map(::likeEscape)
    val short1 = shorts.getOrNull(0)
    val short2 = shorts.getOrNull(1)
    val matches = q.countMatches(fts, SORT_FIRST_MAX_MATCHES + 1).executeAsOne()
    return if (matches <= SORT_FIRST_MAX_MATCHES) {
        q.searchFilteredText(fts, short1, short2, query.currency, query.minMinor, query.maxMinor, query.kind, query.fromMillis, query.untilMillis, filterCats, ids, limit).executeAsList()
    } else {
        q.searchFilteredTextWalk(fts, short1, short2, query.currency, query.minMinor, query.maxMinor, query.kind, query.fromMillis, query.untilMillis, filterCats, ids, limit).executeAsList()
    }
}

/** The words of [text] too short for the trigram index (1 or 2 characters). */
internal fun shortWords(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() && it.length < MIN_INDEXED_LENGTH }

/** Makes user text safe inside a LIKE pattern: `%` and `_` are searched literally (the queries declare `\` as the escape character). */
internal fun likeEscape(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
