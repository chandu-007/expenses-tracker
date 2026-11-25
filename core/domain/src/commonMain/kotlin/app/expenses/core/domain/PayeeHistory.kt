package app.expenses.core.domain

/**
 * What the ledger already holds for one payee, said on two screens of the redesign: an expense's detail ("At Barbeque Nation this year · 4 visits · ₹4,860") and a card in To review ("Matched 14 earlier
 * Zomato entries"). Pure: the database supplies the candidates (the newest entries whose words match the name, one indexed lookup), as for [UsualAmount]; "the same payee" is [SimilarEntries.matcher]
 * (the name written differently, and the same-shop groups, built in and the person's own).
 */
object PayeeHistory {
    /** The detail says the year's visits only from two (one visit is the entry itself, which is nothing to say). */
    const val MIN_VISITS = 2

    /** How many times the payee was paid in a period and how much in all, in minor units of one currency. */
    data class Visits(val count: Int, val totalMinor: Long)

    /**
     * The entries to the same payee as [label] in [currency] dated from [fromMillis] (inclusive) to [untilMillis] (exclusive), the entry being looked at included: their number and their sum.
     * Null below [MIN_VISITS] or for a blank name.
     */
    fun visits(label: String, currency: String, fromMillis: Long, untilMillis: Long, candidates: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE): Visits? {
        val name = label.trim()
        if (name.isEmpty()) return null
        val same = SimilarEntries.matcher(name, aliases)
        val hits = candidates.filter { it.currency == currency && it.amountMinor > 0 && it.occurredAt >= fromMillis && it.occurredAt < untilMillis && same(it.label) }.distinctBy { it.id }
        return if (hits.size >= MIN_VISITS) Visits(hits.size, hits.sumOf { it.amountMinor }) else null
    }

    /** How many entries already in the ledger are the same payee as [label] (any currency, any date, with or without a category); 0 for a blank name. */
    fun earlier(label: String, candidates: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE): Int {
        val name = label.trim()
        if (name.isEmpty()) return 0
        val same = SimilarEntries.matcher(name, aliases)
        return candidates.filter { same(it.label) }.distinctBy { it.id }.size
    }
}
