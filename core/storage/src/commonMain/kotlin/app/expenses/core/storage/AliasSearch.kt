package app.expenses.core.storage

import app.expenses.core.domain.PayeeAliases
import app.expenses.core.domain.PayeeKey
import app.expenses.core.storage.db.Entry
import app.expenses.core.storage.db.ExpensesDb

/**
 * Search that also finds the other names of a shop the person joined to another ("same shop as"): a search for "Swiggy" finds the entries called "Instamart" too, and the reverse. Only the
 * person's own aliases do this (a search for a word never grows by itself), only when what they typed is one or two words naming such a shop, and every entry is found by the same text rules and
 * filters as the plain search; nothing is rewritten. With no alias it is exactly [searchFiltered].
 */
fun ExpensesDb.searchFiltered(query: SearchQuery, aliases: PayeeAliases, limit: Long = 50): List<Entry> {
    val own = searchFiltered(query, limit)
    val others = AliasSearch.otherNames(query.text, aliases)
    if (others.isEmpty()) return own
    val found = LinkedHashMap<String, Entry>()
    own.forEach { found[it.id] = it }
    for (name in others) searchFiltered(query.copy(text = name), limit).forEach { found.putIfAbsent(it.id, it) }
    return found.values.sortedWith(compareByDescending<Entry> { it.occurred_at }.thenByDescending { it.seq }).take(limit.toInt())
}

object AliasSearch {
    /** Most words a search may have for its text to count as naming a shop. */
    const val MAX_WORDS = 2

    /** The other written names of the shop that [text] names through the person's [aliases], or empty (no alias, a longer text, a name that is in no alias). */
    fun otherNames(text: String, aliases: PayeeAliases): List<String> {
        if (aliases.isEmpty) return emptyList()
        val clean = text.trim()
        if (clean.isEmpty() || clean.split(Regex("\\s+")).size > MAX_WORDS) return emptyList()
        val key = PayeeKey.of(clean) ?: return emptyList()
        if (!aliases.inGroup(key)) return emptyList()
        val typed = clean.lowercase()
        return aliases.labelsOf(key).filter { it.trim().lowercase() != typed }
    }
}
