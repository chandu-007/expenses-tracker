package app.expenses.core.domain

/** One earlier entry that might be the same thing as another: what it was called, what it is filed under, and enough to recognise it in a list. */
data class SimilarCandidate(val id: String, val label: String, val categoryId: String, val amountMinor: Long, val occurredAt: Long)

/**
 * Finds earlier entries that look like the same payment as one the person has just filed, so the same category can be offered for all of them ("every
 * month I pay iCloud"). It only suggests: the person decides, one by one or all at once. Pure and tested; the database only supplies candidates.
 */
object SimilarEntries {
    /** Two names match when they are the same payee written differently, or one holds the other (at least four characters, so "ola" never matches "cola stall"). */
    fun matches(a: String, b: String): Boolean {
        val x = CategorySuggest.merchantKey(a)
        val y = CategorySuggest.merchantKey(b)
        if (x.length < 3 || y.length < 3) return false
        if (x == y) return true
        val shorter = if (x.length <= y.length) x else y
        val longer = if (x.length <= y.length) y else x
        return shorter.length >= 4 && longer.contains(shorter)
    }

    /** The words worth looking up (three letters or more), at most four, so a long note does not turn into a slow search. */
    fun searchWords(text: String): List<String> =
        text.lowercase().substringBefore('@').split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.distinct().take(4)

    /**
     * The earlier entries called something like [label] that are not yet in [categoryId], newest first. The entry just filed ([exceptId]) is left out, and
     * an entry with no single category (a split, say) is never offered because a move would lose its split.
     */
    fun similar(label: String, categoryId: String, exceptId: String?, candidates: List<SimilarCandidate>): List<SimilarCandidate> =
        candidates.filter { it.id != exceptId && it.categoryId.isNotEmpty() && it.categoryId != categoryId && matches(label, it.label) }
            .sortedByDescending { it.occurredAt }

    /**
     * The category most of the entries that look like [label] are in, or null when there are none. A tie goes to the newer entry's category.
     * What a captured text should start with when nothing more personal applies.
     */
    fun topCategory(label: String, candidates: List<SimilarCandidate>): String? {
        val same = candidates.filter { it.categoryId.isNotEmpty() && matches(label, it.label) }
        if (same.isEmpty()) return null
        val counts = same.groupingBy { it.categoryId }.eachCount()
        val best = counts.values.max()
        return same.sortedByDescending { it.occurredAt }.first { counts[it.categoryId] == best }.categoryId
    }
}
