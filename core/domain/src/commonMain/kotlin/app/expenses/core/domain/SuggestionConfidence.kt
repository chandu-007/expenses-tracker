package app.expenses.core.domain

/** How far a suggested category can be trusted without the person looking: [SURE] may be added in bulk, [CHECK] is shown and left for them. */
enum class Certainty { SURE, CHECK }

/** What the person's own earlier entries for a payee say: how many are in the leading category and how many are somewhere else. */
data class HistoryProfile(val top: String?, val agree: Int, val other: Int, /** The other categories those entries are in, the most used first. */ val runnersUp: List<String> = emptyList())

/**
 * Whether a suggestion is one the app may put into the books on a bulk "Add all" or a swipe, or one the person should look at first. A wrong category in a total is worse than a missing one
 * (a missing one is shown as "Uncategorised"), so only a suggestion that rests on the person's own consistent choices, or on a merchant the starter list names with no doubt, is [Certainty.SURE]:
 * - their own rule, the words of a bank charge, a starter merchant that sells one kind of thing: sure;
 * - a marketplace or wallet (Amazon, Paytm, Google Play), a payment to a person, and a guess from the words of a name ("tea stall"): always check;
 * - what they chose before: sure when the payee is a plain shop and nothing in their history disagrees, otherwise check; a person or marketplace needs two agreeing choices and no disagreement.
 */
object SuggestionConfidence {
    fun of(source: SuggestionSource, mixed: Boolean, person: Boolean, uses: Int, history: HistoryProfile?, suggested: String): Certainty {
        val disagrees = history != null && history.other > 0 && (history.top != suggested || history.other >= history.agree)
        val needsTwo = mixed || person
        return when (source) {
            SuggestionSource.RULE, SuggestionSource.WORDS -> Certainty.SURE
            SuggestionSource.STARTER -> if (mixed) Certainty.CHECK else Certainty.SURE
            SuggestionSource.KEYWORD, SuggestionSource.PERSON, SuggestionSource.CREDIT_WORDS -> Certainty.CHECK
            SuggestionSource.LEARNED -> when {
                disagrees -> Certainty.CHECK
                needsTwo && uses < 2 && (history?.agree ?: 0) < 2 -> Certainty.CHECK
                else -> Certainty.SURE
            }
            SuggestionSource.HISTORY -> {
                val agree = history?.agree ?: 0
                if (history == null || history.other > 0 || agree < (if (needsTwo) 3 else 2)) Certainty.CHECK else Certainty.SURE
            }
        }
    }
}
