package app.expenses.core.parsers

import kotlinx.datetime.LocalDate

/**
 * When a message comes without a sender (a pasted text, or a Shortcuts automation that did not pass one), the words have to say it is a bank's. The rule is
 * deliberately strict, because the allowlist of senders cannot protect here:
 *  1. **A known bank, card issuer or biller is named in the message** (HDFC Bank, SBI, ICICI Bank, BESCOM, ...; or its header such as HDFCBK): then the message is judged as if it came from that header.
 *  2. Otherwise only if it has **all of**: a rupee amount (Rs, INR, ₹), a word that says money moved (debited, credited, withdrawn, deposited, spent) and an account or card
 *     mask (a/c XX1234, card ending 1234). A friend's "I sent you Rs 500" has none of the second and third.
 * Anything else is counted as "not a bank transaction" and is not parsed or kept. What passes is still only put in To review: nothing reaches the ledger until the person adds it.
 */
object PasteRules {
    sealed interface Evidence {
        data class Bank(val root: String) : Evidence
        data object AlertWords : Evidence
        data object None : Evidence
    }

    private val amount = Regex("""(?i)(?:rs\.?|inr|₹)\s*[0-9]|\b(?:debited|credited|spent|withdrawn)\s+(?:by|for|of|with)\s+[0-9][0-9,]*\.[0-9]{2}\b""")
    private val moved = Regex("""(?i)\b(?:debited|credited|withdrawn|deposited|spent)\b""")
    private val mask = Regex("""(?i)(?:\b(?:a/?c|acct|account|card)\b\s*(?:no\.?)?\s*(?:ending(?:\s+in|\s+with)?)?\s*[:*]?\s*[x*]*[0-9]{3,6}\b|\bending\s+(?:in\s+|with\s+)?[0-9]{4}\b|\b[x*]{2,}[0-9]{3,6}\b)""")

    /** Names that are also ordinary words are matched in capitals only ("slice" the issuer is "SLICE"). */
    private val plainWords = setOf("slice")

    private val banks: List<Pair<String, Regex>> by lazy {
        // the root itself (HDFCBK) and its display name (HDFC Bank); the first built-in root that matches wins. A bank that signs its texts in other words ("Kotak811") says so in [BankRule.textAliases], tried after every name.
        SenderRules.BUILT_IN.flatMap { (root, name) ->
            listOf(root to Regex("""\b${Regex.escape(root)}\b""", RegexOption.IGNORE_CASE), root to wordsOf(name, name.lowercase() in plainWords))
        } + BankRules.ALL.flatMap { r -> r.textAliases.map { r.root to wordsOf(it, false) } }
    }

    private fun wordsOf(name: String, capitalsOnly: Boolean): Regex =
        Regex("""\b${Regex.escape(if (capitalsOnly) name.uppercase() else name)}\b""", if (capitalsOnly) emptySet() else setOf(RegexOption.IGNORE_CASE))

    fun evidence(body: String): Evidence {
        val named = banks.filter { (_, re) -> re.containsMatchIn(body) }.map { it.first }.distinct()
        if (named.isNotEmpty()) {
            // A bill payment names the CARD's bank and is signed by the bank that paid it ("Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811"): the paying bank
            // wrote the text, so it is the other one. With only the card's bank named, that bank stands in for the sender, as for any pasted text.
            val card = if (named.size > 1) BillPayments.read(body)?.cardBank else null
            val writer = card?.let { c -> named.firstOrNull { root -> SenderRules.BUILT_IN[root] != c } }
            return Evidence.Bank(writer ?: named.first())
        }
        return if (amount.containsMatchIn(body) && moved.containsMatchIn(body) && mask.containsMatchIn(body)) Evidence.AlertWords else Evidence.None
    }

    /** The root recorded in capture health for a text that came without a sender. */
    const val NO_SENDER_ROOT = "PASTED"

    /** The issuer shown for a transaction whose bank could not be told from the words. */
    const val UNKNOWN_ISSUER = "Bank message"

    /**
     * The verdict for a text with [sender] (blank when the message came without one): judged by its header when there is one, otherwise by the evidence in its words ([evidence]).
     * Null when a text with no sender has none: it is counted, never read any further. Pure: this is what the dry run "Check a message" and the real intake both call, so they cannot disagree.
     */
    fun classify(sender: String, text: String, extra: Set<String>, arrived: LocalDate): CaptureOutcome? {
        if (sender.isNotEmpty()) return CaptureClassifier.classify(sender, text, extra, arrived)
        return when (val evidence = evidence(text)) {
            is Evidence.Bank -> CaptureClassifier.classify(evidence.root, text, extra, arrived)
            Evidence.AlertWords -> when (val r = CaptureClassifier.classify(NO_SENDER_ROOT, text, extra + NO_SENDER_ROOT, arrived)) {
                is CaptureOutcome.Transaction -> r.copy(issuer = UNKNOWN_ISSUER)
                is CaptureOutcome.Bill -> r.copy(issuer = UNKNOWN_ISSUER)
                is CaptureOutcome.Ignored -> r
            }
            Evidence.None -> null
        }
    }
}
