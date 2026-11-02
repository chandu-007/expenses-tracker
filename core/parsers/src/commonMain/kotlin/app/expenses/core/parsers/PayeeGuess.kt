package app.expenses.core.parsers

import app.expenses.core.domain.NarrationTitle

/**
 * A best-effort name for who was paid, read from the words of an alert, to give the inbox row something readable. It is a **guess for a human
 * to check**, never an input to the books: a wrong guess shows as a wrong name the person can correct, a missing one shows the bank.
 */
object PayeeGuess {
    private val vpa = Regex("""(?i)\b(?:to|from|by|at)\s+(?:vpa\s+)?([a-z0-9][\w.\-]{1,}@[a-z][\w.\-]*)""")
    private val upiPath = Regex("""(?i)upi/(?:p2[am]|[a-z]+)/\d{6,}/([A-Za-z][A-Za-z0-9 &.\-]{1,30})""")
    /** What follows a name in a bank's line and is not part of it: Axis prints "UPI/P2M/ref/NAME Not you? SMS BLOCK ..." with no stop between. */
    private val afterName = Regex("""(?i)\s+(?:not\s+(?:you|u)\b|sms\b|call\b|if\s+not\b|ref\b|upi\b|avl\b|bal\b|axis\b).*$""")
    /** ICICI and IDFC name the payee of a UPI debit before the word "credited", after a semicolon: "debited for Rs 14.00 on 09-May-26; Pune Metro credited. UPI:...". */
    private val semicolonCredited = Regex("""(?i);\s*([A-Za-z0-9][A-Za-z0-9&'./*\-]*(?:\s+[A-Za-z0-9&'./*\-]+){0,5}?)\s+credited\b""")
    /** A payment gateway's prefix on a card statement name ("RAZ*SWIGGY", "PAYU*BIGBASKET", "BILLDESK*AIRTEL"): the shop is what follows it. */
    private val gatewayPrefix = Regex("""(?i)^(?:raz|rzp|razorpay|pyu|payu|payuindia|ccav|ccavenue|billdesk|bdsk|cashfree|cf|paytm|ptm|phonepe|ppay|pg|ib|ecom|pos|upi|instamojo|ebs)\s*\*\s*""")
    private val trailing = Regex("""(?i)(?:\.(?:rrn|upi|avl|ref|bal)\b.*|\s+no\.+.*)$""")
    private val connectors = setOf("to", "at", "from", "via", "using", "with", "for", "on", "by")
    private val maskedToken = Regex("""[x*]+\d*""")
    private val notInName = setOf("card", "a/c", "ac", "acct", "account")
    /** The payment apps whose own name is the title of their notifications: never the name of a person. */
    private val appNames = setOf("google pay", "gpay", "g pay", "phonepe", "phone pe", "paytm", "bhim", "bhim upi", "amazon pay", "cred", "mobikwik", "navi", "super money", "freecharge", "whatsapp pay", "samsung pay")
    private val atName = Regex("""(?i)\b(?:at|to|from|towards)\s+([A-Za-z][A-Za-z0-9&'.*\-]*(?:\s+[A-Za-z0-9&'.*\-]+){0,5}?)(?=\s+(?:on|via|ref|upi|using|through|txn|trf|avl|bal|available|\(|\.|,|;|$)|$)""")
    /**
     * ICICI's card alert names the shop after the date, with "on" and no "at": "spent using ICICI Bank Card XX1234 on 05-Nov-26 on ACME STORES. Avl Limit: ...". The name runs to the
     * full stop that ends the sentence (a dot inside a name, "AMAZON.IN", is kept) or to the limit that follows.
     */
    private val onNameAfterDate = Regex(
        """(?i)\b\d{1,2}[-/ ](?:\d{1,2}|[a-z]{3})[-/ ]\d{2,4}\s+on\s+([A-Za-z][A-Za-z0-9&'.\-]*(?:\s+[A-Za-z0-9&'.\-]+){0,3}?)(?=\.(?:\s|$)|\s+(?:avl|avail\w*|limit|bal|ref|upi|txn)\b|,|;|$)""",
    )
    private val notNames = setOf("your", "you", "a/c", "ac", "account", "vpa", "card", "bank", "self", "the", "us", "me", "info", "credit", "debit", "upi", "rs", "inr")

    /** A payment app's notification starts with the person's name as its title, which the capture joins to the text as "Name: text". */
    private val titleName = Regex("""^([A-Za-z][A-Za-z .'\-]{1,30}?):\s""")

    /** "Chandu. sent ₹10 to you", "Chandu paid you ₹10": the name leads the sentence. */
    private val leadingName = Regex("""(?:^|:\s)([A-Za-z][A-Za-z .'\-]{1,30}?)\.?\s+(?:sent|paid)\s+(?:you\s+)?(?:₹|rs\.?|inr)""", RegexOption.IGNORE_CASE)

    /** "Sent by RAVI KUMAR via UPI": a credit text names who paid in. */
    private val sentBy = Regex("""\b(?:sent|paid|transferred)\s+by\s+([A-Za-z][A-Za-z .'\-]{1,30}?)(?=\s+(?:via|on|ref|upi|using|through|from|to)\b|[.,;]|$)""", RegexOption.IGNORE_CASE)

    /** Words that make a title or lead-in a heading rather than a person's name ("Payment successful", "Dear Customer", "Money received"). */
    private val headingWords = setOf(
        "payment", "payments", "successful", "success", "received", "money", "paid", "sent", "transaction", "credited", "debited", "alert", "dear", "customer",
        "bank", "upi", "transfer", "failed", "pending", "reminder", "offer", "cashback", "reward", "rewards", "request", "requested", "bill", "recharge", "refund",
        "you", "your", "account", "a/c", "info", "notice", "update",
    )

    /**
     * A shop name as a card or bank prints it, tidied: a gateway prefix dropped, a "*" read as a space, terminal and branch numbers ("KIRANA MART 2231", "EAZYDINE0000000") cut off, and "M/S" dropped.
     * Null when nothing name-like is left (a name that is only digits, "ATM 12345", "POS").
     */
    internal fun tidyName(raw: String): String? {
        var t = raw.trim().trimEnd('.', '-', ' ')
        t = gatewayPrefix.replace(t, "")
        // what a bank prints after the name without a stop: "RAHUL SHARMA.RRN 123", "FD no..Avl Bal", "SampleCo with your BOBCARD", "SampleMerchant Store for INR"
        t = trailing.replace(t, "")
        t = t.replace('*', ' ').replace(Regex("""(?i)^m/s\s+"""), "")
        val words = t.split(' ').filter { it.isNotEmpty() }.mapNotNull { w ->
            val digits = w.count { it.isDigit() }
            when {
                digits == 0 -> w
                digits == w.length -> if (digits <= 2) w else null // "2231" is a terminal; "7" in "Sector 7" is kept
                else -> w.trimEnd { it.isDigit() }.takeIf { it.isNotEmpty() && w.length - it.length >= 3 } ?: w.takeIf { digits <= 2 }
            }
        }
        val cut = words.indexOfFirst { it.lowercase() in connectors }.let { if (it < 0) words.size else it }
        val name = words.take(cut).joinToString(" ").trimEnd('.', '-', ' ')
        val lower = name.lowercase().split(' ')
        // an account or card mask ("XX0000", "x") or the words of the bank's own sentence ("HDFC Bank Card x"): not a payee, but what follows a "to" may be ("from XX0000 to SampleRide")
        if (name.isEmpty() || maskedToken.matches(lower.first()) || lower.any { it in notInName }) return words.drop(cut + 1).takeIf { it.isNotEmpty() }?.let { tidyName(it.joinToString(" ")) }
        if (name.length < 3 || name.none { it.isLetter() }) return null
        if (name.lowercase() in notNames || lower.first() in notNames || lower.first() in setOf("atm", "pos", "ecom")) return null
        return name
    }

    private fun looksLikeName(s: String): Boolean {
        val t = s.trim().trimEnd('.', '-')
        return t.length in 2..30 && t.none { it.isDigit() } && t.lowercase() !in appNames && t.split(' ', '.').filter { it.isNotEmpty() }.none { it.lowercase() in headingWords || it.lowercase() in notNames }
    }

    /**
     * The narration a bank prints for a payment ("for XXXXXXXXXX1234-TPT-Holiday Fund Remembe-SAMPLE TRADERS PRIVATE LIMITED", "Info: UPI-RAVI KUMAR-ravi@okaxis-123456789012"): a mode (UPI, NEFT,
     * IMPS, RTGS, TPT, FT, ACH, NACH, ECS, SI, POS, ATM) joined to its parts with hyphens or slashes, after "for", "Info:" or "Remarks:". It must carry a hyphen or slash: "for NEFT txn via
     * Online Banking" is wording, not a narration. Null when there is none. The words are returned as printed.
     */
    fun narration(body: String): String? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        return narrationLine.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.', '-', ' ')?.takeIf { it.isNotEmpty() }
    }

    private val narrationLine = Regex(
        """(?i)(?:\bfor\s+|\binfo\s*[:\-]\s*|\bremarks?\s*[:\-]\s*|\bnarration\s*[:\-]\s*)((?:[x*]{4,}\d{3,4}\s*-\s*)?(?:UPI|NEFT|IMPS|RTGS|TPT|FT|ACH|NACH|ECS|SI|POS|ATM)(?:\s+(?:CR|DR|D|C))?\s*[-/].{2,160}?)(?=\.\s*(?:Avl|Bal|New\s+bal|Available|Closing|Cheque|Not\s+you|Call|If\b|Ref\b|Team|Thank)|\s+(?:Avl|Bal|New\s+bal|Available)\b|\s*$)""",
    )

    fun of(body: String): String? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        sentBy.find(text)?.groupValues?.get(1)?.takeIf(::looksLikeName)?.let { return it.trim().trimEnd('.', '-') }
        // a payment app puts the person's name first; try the leading sentence, then the title, before the "to / from / at" wording
        leadingName.find(text)?.groupValues?.get(1)?.takeIf(::looksLikeName)?.let { return it.trim().trimEnd('.', '-') }
        titleName.find(text)?.groupValues?.get(1)?.takeIf(::looksLikeName)?.let { return it.trim() }
        vpa.find(text)?.groupValues?.get(1)?.let { return it.lowercase() }
        upiPath.find(text)?.groupValues?.get(1)?.let { afterName.replace(it, "") }?.let(::tidyName)?.let { return it }
        semicolonCredited.find(text)?.groupValues?.get(1)?.let(::tidyName)?.let { return it }
        for (m in atName.findAll(text)) {
            val name = tidyName(m.groupValues[1]) ?: continue
            // a name that runs to the very end of the text ("Paid ₹250 to Swiggy") must be written as a name: "subject to clearing" is wording
            if (m.groups[1]!!.range.last + 1 >= text.length && !m.groupValues[1].first().isUpperCase()) continue
            return name
        }
        onNameAfterDate.find(text)?.groupValues?.get(1)?.let(::tidyName)?.takeIf { it.lowercase().split(' ').none { w -> w in headingWords } }?.let { return it }
        // a bank that prints a narration instead of "to <name>": the person or note in it, read as the statement import reads the same line
        return narration(text)?.let(NarrationTitle::of)
    }
}
