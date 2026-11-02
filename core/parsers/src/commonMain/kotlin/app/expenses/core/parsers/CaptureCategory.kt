package app.expenses.core.parsers

import app.expenses.core.domain.CaptureRule
import app.expenses.core.domain.Certainty
import app.expenses.core.domain.CategorySuggest
import app.expenses.core.domain.HistoryProfile
import app.expenses.core.domain.PayeeKey
import app.expenses.core.domain.SuggestionConfidence
import app.expenses.core.domain.SuggestionSource

/** The category To review starts a captured debit with, why, how far it can be trusted, and the other categories this payee has been in. */
data class CategoryAdvice(val categoryId: String, val source: SuggestionSource, val certainty: Certainty, val note: String, val alternatives: List<String>)

/**
 * Everything To review knows when it picks a category for a captured debit, in one place so the screen and the tests ask the same code: the person's rules, what they chose for this payee
 * (found under today's key or the old one), the entries they already filed for it, the starter list, the words of the name, "a payment to a person" ([CategorySuggest.suggest]); then how far
 * that can be trusted ([SuggestionConfidence]) and how to say where it came from. Pure: the caller reads the rules, the learned choices and the history (a lookup by payee) and passes them in.
 */
object CaptureCategory {
    /** What the person has taught the app: the category per payee key, and how many times in a row they agreed. */
    class Taught(val category: Map<String, String> = emptyMap(), val uses: Map<String, Int> = emptyMap())

    fun advise(
        payee: String?, text: String, rules: List<CaptureRule>, taught: Taught, usable: Set<String>, byName: Map<String, String>, history: HistoryProfile?,
        /** The payee's own address says it is a person (a mobile number). */
        toAPerson: Boolean = false,
        /** The name reads like a person's (see [PayeeKinds.looksLikePerson]): only decides how far a learned category is trusted. */
        personLike: Boolean = toAPerson,
    ): CategoryAdvice? {
        val s = CategorySuggest.suggest(payee, text, rules, taught.category, usable, byName, toAPerson, history = history?.top) ?: return null
        val key = PayeeKey.of(payee)
        val uses = (key?.let { taught.uses[it] } ?: PayeeKey.legacy(payee)?.let { taught.uses[it] }) ?: 0
        val certainty = SuggestionConfidence.of(s.source, PayeeKey.isMixed(payee), personLike, uses, history, s.categoryId)
        val note = when (s.source) {
            SuggestionSource.RULE -> "your rule"
            SuggestionSource.LEARNED -> if (uses >= 2) "you chose it $uses times for this payee" else "what you chose last time"
            SuggestionSource.HISTORY -> if ((history?.agree ?: 0) >= 2) "${history?.agree} of your earlier entries" else "your earlier entries"
            SuggestionSource.STARTER -> "a common merchant"
            SuggestionSource.PERSON -> "a payment to a person"
            SuggestionSource.WORDS -> "the words of the text"
            SuggestionSource.KEYWORD -> "a guess from the name"
        }
        val alternatives = ((history?.top?.let { listOf(it) }.orEmpty()) + history?.runnersUp.orEmpty()).filter { it != s.categoryId && it in usable }.distinct().take(2)
        return CategoryAdvice(s.categoryId, s.source, certainty, note, alternatives)
    }
}
