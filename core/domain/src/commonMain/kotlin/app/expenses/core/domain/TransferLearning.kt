package app.expenses.core.domain

/**
 * What the person teaches by turning a waiting text into a TRANSFER in To review ("this kind of text moves money to my Savings account"), so the next text of that kind starts as a transfer.
 *
 * A rule is kept under a key that says which kind of text it is and holds [Rule]: the account the money went to (or came from), how many times in a row the person made such a text a transfer
 * to it, and how many times they recorded such a text as plain spending or income instead. Two kinds of key, most specific first ([keys]):
 *  - the **payee**: "p:out:ravikumar" (the same payee, the same direction). A marketplace or wallet is no payee for this (it is every kind of payment);
 *  - the **wording**: the bank, the direction and what the text says about itself, from a closed list of transfer words (transfer, self, own account, TPT, FT-, sweep) and the digits of the OTHER
 *    account when the text names them ("to A/c xxxxxxxxxx5678"). The rails (IMPS, NEFT, RTGS, UPI) carry every payment to every person, so they never make a key on their own: one self-payment
 *    over UPI must not turn every UPI payment of that bank into a transfer.
 *
 * A suggestion is only a suggestion: it starts the card as a transfer for the person to look at ([Certainty.CHECK]). It is [Certainty.SURE], and Add all and a swipe may add it, only when the
 * person made it three times with no exception ([MIN_SURE_USES]): a wrong transfer hides money from both spending and income, which a wrong category does not, so the bar is higher than for a
 * category ([SuggestionConfidence] needs two). Nothing here reads a database; [app.expenses.core.storage] keeps the rules as device-local settings (never in a backup).
 */
object TransferLearning {
    /** Every rule is a setting under this prefix. */
    const val PREFIX = "transfer_rule:"

    /** Consistent transfers (no exception) before Add all and a swipe may take a suggestion without a look. */
    const val MIN_SURE_USES = 3

    /** A rule is forgotten once the person has recorded such texts as plain spending or income as often as they made them transfers. */
    data class Rule(val accountId: String, val uses: Int, val against: Int)

    fun encode(r: Rule): String = "${r.accountId}|${r.uses}|${r.against}"

    /** The rule a stored value holds, or null for nothing or anything that is not one. */
    fun decode(value: String?): Rule? {
        val parts = value?.split('|') ?: return null
        if (parts.size != 3 || parts[0].isEmpty()) return null
        val uses = parts[1].toIntOrNull() ?: return null
        val against = parts[2].toIntOrNull() ?: return null
        return if (uses >= 1 && against >= 0) Rule(parts[0], uses, against) else null
    }

    /** Words that say money is moving between a person's own accounts, as a closed list: the signature of a text never holds anything else a person wrote or a bank sent. */
    private val vocabulary = listOf(
        "transfer" to Regex("""(?i)\b(?:fund\s+)?transfer\w*\b"""),
        "self" to Regex("""(?i)\bself\b"""),
        "own" to Regex("""(?i)\bown\s+(?:a/?c|account)\b"""),
        "tpt" to Regex("""\bTPT\b"""),
        "ft" to Regex("""(?i)\bFT\s*-"""),
        "sweep" to Regex("""(?i)\bsweep\b"""),
    )

    /** The transfer words [body] says, in a fixed order joined by "+" ("tpt+transfer"), or null when it says none. */
    fun wording(body: String): String? = vocabulary.filter { (_, re) -> re.containsMatchIn(body) }.map { it.first }.takeIf { it.isNotEmpty() }?.joinToString("+")

    /**
     * The keys a text is remembered under, most specific first. [payeeKey] is [PayeeKey.of] of the payee, null when the text has none or it is one of the [PayeeKey.isMixed] marketplaces;
     * [counterLast4] the digits of the other account the text names, if it does. A text with no payee, no transfer word and no named account has no key: nothing is learned from it.
     */
    fun keys(issuer: String, outgoing: Boolean, payeeKey: String?, body: String, counterLast4: String?): List<String> {
        val way = if (outgoing) "out" else "in"
        val out = ArrayList<String>(2)
        payeeKey?.takeIf { it.length >= 2 && '|' !in it }?.let { out += "${PREFIX}p:$way:$it" }
        val words = wording(body)
        if (words != null || counterLast4 != null) out += "${PREFIX}w:${issuer.trim().lowercase().replace('|', ' ')}|$way|${words ?: "-"}|${counterLast4 ?: "-"}"
        return out
    }

    /** What a rule says to do with the next text of its kind. [SURE]: consistent enough for Add all and a swipe. */
    fun certainty(r: Rule): Certainty {
        if (r.against > 0 || r.uses < MIN_SURE_USES) return Certainty.CHECK
        return SuggestionConfidence.of(SuggestionSource.LEARNED, mixed = true, person = false, uses = r.uses, history = HistoryProfile(r.accountId, r.uses, r.against), suggested = r.accountId)
    }

    /**
     * The rule after the person added a text it applies to. [transferTo] is the account the money went to (or came from) when they made it a transfer, null when they recorded it as spending
     * or income. A first transfer starts a rule; the same account again counts; another account starts over with it (the latest choice is what they chose last time); a plain entry counts
     * against the rule, which is forgotten (null) once the exceptions are as many as the transfers. With no rule and a plain entry there is nothing to learn.
     */
    fun after(rule: Rule?, transferTo: String?): Rule? = when {
        transferTo != null -> if (rule != null && rule.accountId == transferTo) rule.copy(uses = rule.uses + 1) else Rule(transferTo, 1, 0)
        rule == null -> null
        else -> rule.copy(against = rule.against + 1).takeIf { it.against < it.uses }
    }
}
