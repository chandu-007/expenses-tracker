package app.expenses.core.domain

/** "When the payee or text mentions [pattern], use [categoryId]." Rules are tried in the order given; the first that matches wins. */
data class CaptureRule(val id: String, val pattern: String, val categoryId: String) {
    init {
        require(id.isNotEmpty()) { "A rule needs an id" }
        require(words(pattern).isNotEmpty()) { "A rule needs something to look for" }
        require(categoryId.isNotEmpty()) { "A rule needs a category" }
    }

    /** Every word of the pattern appears in [text] (any order, any case). */
    fun matches(text: String): Boolean {
        val t = text.lowercase()
        return words(pattern).all { t.contains(it) }
    }

    companion object {
        fun words(pattern: String): List<String> = pattern.lowercase().split(' ', ',', '\n').map { it.trim() }.filter { it.length >= 2 }
    }
}

enum class SuggestionSource { RULE, LEARNED }

data class CategorySuggestion(val categoryId: String, val source: SuggestionSource)

/** What the person chose for a payee last time, kept so the next one starts there. */
data class LearnedMerchant(val key: String, val categoryId: String)

object CategorySuggest {
    /** The same payee written differently ("Swiggy ", "SWIGGY.", "swiggy@icici") gets one key: lower case, letters and digits only, and a UPI address without its bank handle. */
    fun merchantKey(payee: String): String {
        val name = payee.trim().lowercase().substringBefore('@')
        return name.filter { it.isLetterOrDigit() }
    }

    /**
     * The category for a captured transaction: the person's own rules first (in their order), then what they chose for this payee before.
     * A category that no longer exists or is hidden (it is not in [usable]) is never suggested, so a deleted category cannot come back through a rule.
     * Null when nothing applies, which is better than a wrong guess the person might not notice.
     */
    fun suggest(payee: String?, body: String, rules: List<CaptureRule>, learned: Map<String, String>, usable: Set<String>): CategorySuggestion? {
        val text = listOfNotNull(payee, body).joinToString(" ")
        rules.firstOrNull { it.categoryId in usable && it.matches(text) }?.let { return CategorySuggestion(it.categoryId, SuggestionSource.RULE) }
        val key = payee?.let(::merchantKey)?.takeIf { it.length >= 2 } ?: return null
        return learned[key]?.takeIf { it in usable }?.let { CategorySuggestion(it, SuggestionSource.LEARNED) }
    }
}
