package app.expenses.core.domain

/** One earlier entry that might be the same thing as another: what it was called, what it is filed under, and enough to recognise it in a list. [currency] is the currency of [amountMinor] (empty when not known). */
data class SimilarCandidate(val id: String, val label: String, val categoryId: String, val amountMinor: Long, val occurredAt: Long, val currency: String = "")

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

    /**
     * A test for "is this earlier entry's name the same payee as [label]": [matches], and, when [label] is in the built-in same-merchant table ([MerchantAliases]) or in a group the person made
     * ([aliases]), every other name of that shop. Costs nothing extra for a name in neither.
     */
    fun matcher(label: String, aliases: PayeeAliases = PayeeAliases.NONE): (String) -> Boolean {
        val key = PayeeKey.of(label) ?: return { other -> matches(label, other) }
        val shop = aliases.resolve(key)
        if (!(MerchantAliases.inGroup(key) || aliases.inGroup(key) || aliases.inGroup(shop))) return { other -> matches(label, other) }
        val seen = HashMap<String, Boolean>()
        return { other -> matches(label, other) || seen.getOrPut(other) { PayeeKey.of(other, aliases) == shop } }
    }

    /** The earlier entries that are the same payee as [label] (see [matcher]) and have a category. */
    fun sameOnes(label: String, candidates: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE): List<SimilarCandidate> {
        val same = matcher(label, aliases)
        return candidates.filter { it.categoryId.isNotEmpty() && same(it.label) }
    }

    /** The words worth looking up (three letters or more), at most four, so a long note does not turn into a slow search. */
    fun searchWords(text: String): List<String> =
        text.lowercase().substringBefore('@').split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.distinct().take(4)

    /**
     * The earlier entries called something like [label] that are not yet in [categoryId], newest first. The entry just filed ([exceptId]) is left out, and
     * an entry with no single category (a split, say) is never offered because a move would lose its split.
     */
    fun similar(label: String, categoryId: String, exceptId: String?, candidates: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE): List<SimilarCandidate> {
        val same = matcher(label, aliases)
        return candidates.filter { it.id != exceptId && it.categoryId.isNotEmpty() && it.categoryId != categoryId && same(it.label) }.sortedByDescending { it.occurredAt }
    }

    /**
     * The category most of the entries that look like [label] are in, or null when there are none. A tie goes to the newer entry's category.
     * What a captured text should start with when nothing more personal applies.
     */
    fun topCategory(label: String, candidates: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE): String? {
        val same = sameOnes(label, candidates, aliases)
        if (same.isEmpty()) return null
        val counts = same.groupingBy { it.categoryId }.eachCount()
        val best = counts.values.max()
        return same.sortedByDescending { it.occurredAt }.first { counts[it.categoryId] == best }.categoryId
    }

    /** What the earlier entries that look like [label] say: the leading category, how many are in it and how many are in others. Null when none read alike. */
    fun profile(label: String, candidates: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE): HistoryProfile? {
        val same = sameOnes(label, candidates, aliases)
        if (same.isEmpty()) return null
        val top = topCategory(label, same, aliases) ?: return null
        val agree = same.count { it.categoryId == top }
        val runnersUp = same.filter { it.categoryId != top }.groupingBy { it.categoryId }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        return HistoryProfile(top, agree, same.size - agree, runnersUp)
    }
}
