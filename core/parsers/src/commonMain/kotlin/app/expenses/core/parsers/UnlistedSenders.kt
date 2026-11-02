package app.expenses.core.parsers

/**
 * What one text from an alphanumeric header that is NOT on the list looks like, judged in memory and never kept: is it shaped like a bank's payment text, and if so a masked outline
 * of its wording ([Hint.outline]) that the person can recognise ("ICICI Bank Acct XX### debited for Rs ###.## on ...") without the message, a name or a number being kept.
 *
 * A text is **payment-shaped** only when ALL of these hold (STRICT on purpose: the person is asked to trust a sender on the strength of this, and a wrong yes is worse than a miss):
 *  1. [GenericAlertParser] reads it as a money-out or money-in alert (not declined, not a one-time password) with an amount above zero;
 *  2. it says money moved in the past tense (debited, credited, spent, withdrawn, deducted ... [MoneyMoved.verbStated]): "will be debited" and "kindly ignore if already paid" do not count;
 *  3. it carries at least one bank-text marker: an account or card mask with digits (`A/c XX123`, `Card ending 3025`, `XX1234`), a UPI / IMPS / NEFT / RTGS reference number, or a
 *     balance or limit line (`Avl bal`, `Available balance`, `Avl limit`). An amount with "credited" or "paid" and none of these (cashback, a recharge, a delivery) is not a bank's text.
 *
 * The caller must only ask for a sender that [SenderRules.headerRoot] reads as an alphanumeric header (never a phone number, never an email, never a text with no sender).
 * Nothing here is stored; see [outline] for the only thing that is.
 */
object UnlistedSenders {
    /** Whether the text is money out or money in, as shown in words. */
    enum class Way { OUT, IN }

    /** [outline] is built only from [outline]'s whitelist: never a digit, never a name. */
    data class Hint(val way: Way, val outline: String)

    /** The longest outline kept, in characters. */
    const val OUTLINE_CAP = 60

    /** Bank texts are a few hundred characters; a long text is a newsletter or an email body. */
    private const val MAX_BODY = 1200

    private const val ELLIPSIS = "\u2026"

    private val mask = Regex("""(?i)(?:\b(?:a/c|a\.c\.?|acct?\.?|account|card)\b\W{0,3}(?:(?:no|number)\b\W{0,3})?(?:ending\W{0,3}(?:with\W{0,3}|in\W{0,3})?)?[x*]*\d{3,6}\b)|(?<![A-Za-z0-9])[x*]{2,}\d{3,6}\b|\bending\s+(?:with\s+|in\s+)?\d{4}\b""")
    private val reference = Regex("""(?i)\b(?:upi|imps|neft|rtgs)\b(?:\s*(?:ref(?:erence)?|txn|utr|id)\b)?\.?(?:\s*(?:no\b\.?|number\b|id\b))?\s*[:\-/]?\s*\d{6,}|\bupi/[a-z0-9]+/\d{9,}""")
    private val balance = Regex("""(?i)\b(?:avl|avail(?:able)?)\.?\s*(?:credit\s+)?(?:bal\w*|lim\w*|lmt)\b""")

    /** The words an outline may show, lower case, and how each is written. Generic payment vocabulary and the names of the banks the app already lists. */
    private val fixed: Map<String, String> = listOf(
        "debited", "credited", "spent", "withdrawn", "received", "paid", "sent", "on", "for", "to", "from", "with", "by", "Rs", "INR", "A/c", "Acct", "Account", "Card", "ending", "UPI", "IMPS", "NEFT", "RTGS",
    ).associateBy { it.lowercase() }
    private val bankWords: Map<String, String> = BankRules.ALL.flatMap { it.issuer.split(' ') }.filter { it.isNotEmpty() && it.all { c -> c.isLetter() } }.associateBy { it.lowercase() }
    private val allowed: Map<String, String> = bankWords + fixed

    /** Words that may follow "UPI" / "Card" / "Rs" before the number they introduce without breaking the link ("UPI Ref No 123"). They are not shown. */
    private val labels = setOf("ref", "reference", "no", "number", "id", "utr", "txn")

    /** Words after which a number is shown as '#' (an amount, a mask, a reference). Any other number is a date, a phone number, a code: shown as the ellipsis. */
    private val numberAfter = setOf("rs", "inr", "ending", "upi", "imps", "neft", "rtgs", "a/c", "acct", "account", "card")

    private val token = Regex("""\d[\d,]*(?:\.\d+)?|[A-Za-z*/][A-Za-z0-9*/]*""")

    /** Null when [body] is not payment-shaped (see the class comment); otherwise the direction and the masked outline. Pure; never throws on odd input. */
    fun hint(body: String, root: String): Hint? {
        if (body.isBlank() || body.length > MAX_BODY) return null
        val parsed = GenericAlertParser.parse(body, root) as? ParseResult.Parsed ?: return null
        val alert = parsed.alert
        if (alert.direction != Direction.DEBIT && alert.direction != Direction.CREDIT) return null
        if (alert.amountMinor <= 0) return null
        if (!MoneyMoved.verbStated(body)) return null
        if (!(mask.containsMatchIn(body) || reference.containsMatchIn(body) || balance.containsMatchIn(body))) return null
        return Hint(if (alert.direction == Direction.DEBIT) Way.OUT else Way.IN, outline(body))
    }

    /**
     * The masked outline of [body]: the whitelisted payment words as they are, each number that follows one of them (an amount, a mask, a reference) with every digit replaced by '#', a mask as
     * `XX` and its digits as '#', and every other word or number a single ellipsis (runs of them become one). Punctuation is dropped. At most [OUTLINE_CAP] characters, cut at a word.
     * It never holds a digit, and never a word that is not on the whitelist: a person's name, a shop, a bank's own sender name are all the ellipsis.
     */
    fun outline(body: String): String {
        val out = ArrayList<String>()
        fun put(s: String) { if (s == ELLIPSIS && out.lastOrNull() == ELLIPSIS) return; out += s }
        var ctx = false
        for (m in token.findAll(body.replace("\u20B9", " Rs "))) {
            val w = m.value
            if (w[0].isDigit()) {
                if (ctx) { put(w.map { if (it.isDigit()) '#' else it }.joinToString("")); ctx = false } else put(ELLIPSIS)
                continue
            }
            val lower = w.lowercase()
            val firstDigit = w.indexOfFirst { it.isDigit() }
            when {
                firstDigit >= 0 -> {
                    val prefix = w.substring(0, firstDigit).lowercase()
                    val rest = w.substring(firstDigit)
                    val digitsOnly = rest.all { it.isDigit() }
                    when {
                        digitsOnly && prefix.isNotEmpty() && prefix.all { it == 'x' || it == '*' } -> put("XX" + "#".repeat(rest.length))
                        digitsOnly && (prefix == "rs" || prefix == "inr") -> { put(if (prefix == "rs") "Rs" else "INR"); put("#".repeat(rest.length)) }
                        else -> put(ELLIPSIS)
                    }
                    ctx = false
                }
                w.all { it == 'x' || it == 'X' || it == '*' } && w.length >= 2 -> { put("XX"); ctx = true }
                lower in labels && ctx -> Unit
                else -> {
                    val shown = allowed[lower]
                    if (shown != null) { put(shown); ctx = lower in numberAfter } else { put(ELLIPSIS); ctx = false }
                }
            }
        }
        return cap(out)
    }

    private fun cap(tokens: List<String>): String {
        val full = tokens.joinToString(" ")
        if (full.length <= OUTLINE_CAP) return full
        val kept = ArrayList<String>()
        var len = 0
        for (t in tokens) {
            val next = len + (if (kept.isEmpty()) 0 else 1) + t.length
            if (next > OUTLINE_CAP - 2) break
            kept += t; len = next
        }
        if (kept.lastOrNull() != ELLIPSIS) kept += ELLIPSIS
        return kept.joinToString(" ")
    }

    /** Every word an outline may show, lower case, for the tests: the whitelist is data, not behaviour hidden in a regex. */
    val outlineWords: Set<String> get() = allowed.keys + "xx"
}
