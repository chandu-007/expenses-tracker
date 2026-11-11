package app.expenses.core.domain

/**
 * What the person's earlier entries for one payee say about an AMOUNT like this one: the category most of the entries of nearby amounts are in, how many agree and how many are somewhere else.
 * [amountMinor] is the amount asked about, [runnersUp] the other categories of those nearby entries, the most used first.
 */
data class AmountBand(val top: String, val agree: Int, val other: Int, val runnersUp: List<String>, val amountMinor: Long, val currency: String = "") {
    /** The same facts in the shape [SuggestionConfidence] reads. */
    fun profile() = HistoryProfile(top, agree, other, runnersUp)

    /** The amount asked about as the person reads it, whole when it is whole: "₹199", "₹1,249.50". */
    fun like(): String {
        val c = CurrencyChoices.byCode(currency.ifEmpty { "INR" }).takeIf { it.code == currency.ifEmpty { "INR" } } ?: runCatching { Currency(currency, 2) }.getOrDefault(Currency.INR)
        val text = Money(amountMinor, c).formatted()
        return if (c.minorUnitDigits > 0 && text.endsWith("." + "0".repeat(c.minorUnitDigits))) text.dropLast(c.minorUnitDigits + 1) else text
    }
}

/**
 * Amount-aware category suggestions for a payee that is paid for different things: "Amazon" at Rs 199 is a subscription, at Rs 2,499 a purchase; a corner shop's UPI of Rs 40 is tea, of Rs 4,000 a
 * bill. Nothing is stored for it: the evidence is the entries the person already filed for the payee (their amounts and categories are already in the ledger, found through the same indexed lookup
 * of the payee's own words as the history suggestion: the 300 newest), so there is no second table to keep in step, and a category the person
 * changes on an entry changes what is suggested.
 *
 * Nearest neighbours, not buckets: an earlier amount is "like" this one when the larger is at most [WINDOW_PERCENT] percent of the smaller (so Rs 199 is near 160 to 248, and a subscription that
 * went from 179 to 199 to 219 is one band), with no edge to fall on the wrong side of. It speaks only when
 * - the payee really is paid for different things (its earlier entries are in at least two categories: a payee with one category is answered by the plain history, as before),
 * - at least [MIN_AGREE] nearby entries share a category and it has MORE nearby entries than any other (a tie says nothing),
 * - the amounts and the payee are in the same currency (an entry's own amount, never a converted one).
 * It is a CHECK unless [SuggestionConfidence] says it is strong: [SuggestionConfidence.AGREE_MIXED] or more nearby entries agree and none of the nearby entries is in another category. A payee's
 * own name is matched by [PayeeKey] (the same shop written differently, the built-in and the person's aliases), never by one name holding the other's letters.
 */
object AmountBands {
    /** The larger of two amounts is at most this percent of the smaller for them to be "like" each other: 125 is within a quarter. */
    const val WINDOW_PERCENT = 125

    /** The fewest nearby entries in one category for the band to speak. */
    const val MIN_AGREE = 2

    fun near(a: Long, b: Long): Boolean {
        if (a <= 0 || b <= 0) return false
        val lo = minOf(a, b); val hi = maxOf(a, b)
        return hi * 100 <= lo * WINDOW_PERCENT
    }

    /**
     * The band for [amountMinor] (in [currency], empty when not known) of the payee written [label], from [candidates] (earlier entries of kind EXPENSE, or of INCOME for a payer, as
     * [SimilarCandidate]s: only the ones of this payee are read, by key). Null when it has nothing decisive to say; the caller then uses what it used before.
     */
    fun of(label: String, amountMinor: Long, currency: String, candidates: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE): AmountBand? {
        if (amountMinor <= 0 || candidates.size < MIN_AGREE) return null
        val shop = PayeeKey.of(label, aliases) ?: return null
        val keys = HashMap<String, String?>()
        val own = candidates.filter { c ->
            c.categoryId.isNotEmpty() && c.amountMinor > 0 && (c.currency.isEmpty() || currency.isEmpty() || c.currency == currency) &&
                keys.getOrPut(c.label) { PayeeKey.of(c.label, aliases) } == shop
        }
        if (own.size < MIN_AGREE || own.mapTo(HashSet()) { it.categoryId }.size < 2) return null
        val near = own.filter { near(it.amountMinor, amountMinor) }
        if (near.size < MIN_AGREE) return null
        val counts = near.groupingBy { it.categoryId }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val best = counts.first()
        if (best.value < MIN_AGREE || (counts.size > 1 && counts[1].value == best.value)) return null
        return AmountBand(best.key, best.value, near.size - best.value, counts.drop(1).map { it.key }, amountMinor, currency)
    }
}
