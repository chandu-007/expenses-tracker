package app.expenses

import app.expenses.core.domain.AmountBand
import app.expenses.core.domain.AmountBands
import app.expenses.core.domain.HistoryProfile
import app.expenses.core.domain.PayeeAliases
import app.expenses.core.domain.PayeeKey
import app.expenses.core.domain.SimilarEntries
import app.expenses.core.storage.payeeCandidates

/**
 * What the person's earlier entries say about one payee, read once for To review: the entries of the payee (an indexed lookup of its own words, the 300 newest, never a scan of the ledger; see
 * [payeeCandidates]), the plain history of them ([HistoryProfile]) and what they say about the amount on the card ([AmountBand]). A payee the person joined to other names ("same shop as") is read
 * through those names too.
 */
internal class CategoryEvidence(val history: HistoryProfile?, val band: AmountBand?) {
    companion object {

        /**
         * The evidence for a payee written [clean] (and [raw] as the text had it) with [amountMinor] in [currency]: its entries under both spellings and under the other names of its shop, when the
         * person joined it to others. Only the names the person's aliases know are looked up beyond the two spellings: at most [MAX_NAMES] lookups, none for a payee with no alias.
         */
        fun of(ok: AppContainer.Opened.Ok, clean: String, raw: String, kind: String, amountMinor: Long, currency: String, aliases: PayeeAliases): CategoryEvidence {
            val names = LinkedHashSet<String>()
            names += clean
            if (raw != clean) names += raw
            if (!aliases.isEmpty) PayeeKey.of(clean)?.let { key -> names += aliases.labelsOf(key) }
            val found = names.take(MAX_NAMES).flatMap { ok.db.payeeCandidates(it, kind) }.distinctBy { it.id }
            return CategoryEvidence(SimilarEntries.profile(clean, found, aliases), AmountBands.of(clean, amountMinor, currency, found, aliases))
        }

        const val MAX_NAMES = 6

        /**
         * The reason a suggestion rests on the amount, with its counts: "You chose Subscriptions for Amazon amounts like ₹199 (4 times)"; for money in "You chose Salary for money from Acme Ltd of
         * amounts like ₹52,000 (3 times)". When some nearby entries are in another category that is said too, so a "check" is not a surprise.
         */
        fun reason(category: String, payee: String, band: AmountBand, income: Boolean): String {
            val times = { n: Int -> if (n == 1) "once" else "$n times" }
            val others = if (band.other > 0) "; ${times(band.other)} something else" else ""
            val who = if (payee.isBlank()) (if (income) "money in" else "this payee") else if (income) "money from $payee" else payee
            return "You chose $category for $who ${if (income) "of " else ""}amounts like ${band.like()} (${times(band.agree)}$others)"
        }
    }
}
