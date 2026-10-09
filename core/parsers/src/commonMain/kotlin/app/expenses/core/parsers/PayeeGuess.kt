package app.expenses.core.parsers

/**
 * A best-effort name for who was paid, read from the words of an alert, to give the inbox row something readable. It is a **guess for a human
 * to check**, never an input to the books: a wrong guess shows as a wrong name the person can correct, a missing one shows the bank.
 */
object PayeeGuess {
    private val vpa = Regex("""(?i)\b(?:to|from|by)\s+(?:vpa\s+)?([a-z0-9][\w.\-]{1,}@[a-z][\w.\-]*)""")
    private val upiPath = Regex("""(?i)upi/(?:p2[am]|[a-z]+)/\d{6,}/([A-Za-z][A-Za-z0-9 &.\-]{1,30})""")
    private val atName = Regex("""(?i)\b(?:at|to|from|towards)\s+([A-Za-z][A-Za-z0-9&'.\-]*(?:\s+[A-Za-z0-9&'.\-]+){0,3}?)(?=\s+(?:on|via|ref|upi|using|through|txn|trf|avl|bal|available|\(|\.|,|;|$))""")
    private val notNames = setOf("your", "you", "a/c", "ac", "account", "vpa", "card", "bank", "self", "the", "us", "me", "info", "credit", "debit", "upi", "rs", "inr")

    /** A payment app's notification starts with the person's name as its title, which the capture joins to the text as "Name: text". */
    private val titleName = Regex("""^([A-Za-z][A-Za-z .'\-]{1,30}?):\s""")

    /** "Chandu. sent ₹10 to you", "Chandu paid you ₹10": the name leads the sentence. */
    private val leadingName = Regex("""(?:^|:\s)([A-Za-z][A-Za-z .'\-]{1,30}?)\.?\s+(?:sent|paid)\s+(?:you\s+)?(?:₹|rs\.?|inr)""", RegexOption.IGNORE_CASE)

    /** Words that make a title or lead-in a heading rather than a person's name ("Payment successful", "Dear Customer", "Money received"). */
    private val headingWords = setOf(
        "payment", "payments", "successful", "success", "received", "money", "paid", "sent", "transaction", "credited", "debited", "alert", "dear", "customer",
        "bank", "upi", "transfer", "failed", "pending", "reminder", "offer", "cashback", "reward", "rewards", "request", "requested", "bill", "recharge", "refund",
        "you", "your", "account", "a/c", "info", "notice", "update",
    )

    private fun looksLikeName(s: String): Boolean {
        val t = s.trim().trimEnd('.', '-')
        return t.length in 2..30 && t.none { it.isDigit() } && t.split(' ', '.').filter { it.isNotEmpty() }.none { it.lowercase() in headingWords || it.lowercase() in notNames }
    }

    fun of(body: String): String? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        // a payment app puts the person's name first; try the leading sentence, then the title, before the "to / from / at" wording
        leadingName.find(text)?.groupValues?.get(1)?.takeIf(::looksLikeName)?.let { return it.trim().trimEnd('.', '-') }
        titleName.find(text)?.groupValues?.get(1)?.takeIf(::looksLikeName)?.let { return it.trim() }
        vpa.find(text)?.groupValues?.get(1)?.let { return it.lowercase() }
        upiPath.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.lowercase() !in notNames }?.let { return it }
        for (m in atName.findAll(text)) {
            val name = m.groupValues[1].trim().trimEnd('.', '-')
            if (name.length >= 3 && name.lowercase() !in notNames && name.split(' ').first().lowercase() !in notNames && !name.any { it.isDigit() } ) return name
        }
        return null
    }
}
