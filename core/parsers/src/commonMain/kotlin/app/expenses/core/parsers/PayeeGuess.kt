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

    fun of(body: String): String? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        vpa.find(text)?.groupValues?.get(1)?.let { return it.lowercase() }
        upiPath.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.lowercase() !in notNames }?.let { return it }
        for (m in atName.findAll(text)) {
            val name = m.groupValues[1].trim().trimEnd('.', '-')
            if (name.length >= 3 && name.lowercase() !in notNames && name.split(' ').first().lowercase() !in notNames && !name.any { it.isDigit() } ) return name
        }
        return null
    }
}
