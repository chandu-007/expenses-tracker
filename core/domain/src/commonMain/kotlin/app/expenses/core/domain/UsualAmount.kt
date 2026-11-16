package app.expenses.core.domain

/**
 * What an expense came to against what the person usually pays the same payee (UI S2): the one line on an entry's detail ("₹120 above your usual at Starbucks"). It is only said when it is worth saying:
 * at least [MIN_EARLIER] earlier expenses to the same payee in the same currency, and a difference of a quarter or more of the usual amount and of at least [MIN_UNITS] whole units of the currency.
 * Pure: the database only supplies the candidates ([SimilarCandidate]), as for [SimilarEntries].
 */
object UsualAmount {
    const val MIN_EARLIER = 3
    const val MIN_UNITS = 5L

    /** The middle amount (in minor units) of the earlier expenses that are the same payee as [label], leaving out [exceptId]; null when fewer than [MIN_EARLIER] are. */
    fun usual(label: String, exceptId: String?, currency: String, candidates: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE): Long? {
        val same = SimilarEntries.sameOnes(label, candidates.filter { it.id != exceptId && it.currency == currency && it.amountMinor > 0 }, aliases)
        if (same.size < MIN_EARLIER) return null
        val sorted = same.map { it.amountMinor }.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** [amountMinor] minus [usualMinor] when that is worth saying (positive: above the usual, negative: below it), else null. [minorDigits] is the currency's decimals. */
    fun difference(amountMinor: Long, usualMinor: Long, minorDigits: Int): Long? {
        if (usualMinor <= 0) return null
        var unit = 1L; repeat(minorDigits.coerceIn(0, 6)) { unit *= 10 }
        val diff = amountMinor - usualMinor
        val abs = if (diff < 0) -diff else diff
        return if (abs * 4 >= usualMinor && abs >= MIN_UNITS * unit) diff else null
    }
}
