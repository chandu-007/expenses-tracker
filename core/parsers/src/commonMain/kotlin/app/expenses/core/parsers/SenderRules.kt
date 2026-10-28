package app.expenses.core.parsers

/**
 * Who sent a text, as far as capture cares. Indian transactional SMS come from alphanumeric headers registered with TRAI: an operator and
 * circle prefix of two letters, a dash, the header root, and often a dash and a one-letter category suffix: `AX-HDFCBK-S`, `VM-SBIINB`,
 * `JD-ICICIB-T`. The prefix and suffix change from one text to the next, the **root** does not, so the allowlist is matched on the root.
 * A personal number (digits, with or without +91) has no header and is never captured.
 */
object SenderRules {
    private val header = Regex("""^([A-Za-z0-9]{2})-([A-Za-z0-9]{3,12}?)(?:-([A-Za-z]))?$""")

    /** The upper-case header root of an alphanumeric sender, or null for a phone number or anything that is not a registered header. */
    fun headerRoot(sender: String): String? {
        val s = sender.trim()
        if (s.isEmpty() || s.removePrefix("+").all { it.isDigit() || it == ' ' || it == '-' }) return null
        val m = header.matchEntire(s)
        val root = if (m != null) m.groupValues[2] else s.takeIf { it.length in 3..12 && it.all { c -> c.isLetterOrDigit() } && it.any { c -> c.isLetter() } }
        return root?.uppercase()
    }

    /** Header root to the name shown to the user, for every entry of [BankRules.ALL]. The data lives there; this is the view the classifier looks roots up in. */
    val BUILT_IN: Map<String, String> = BankRules.ALL.associate { it.root to it.issuer }

    /** Every phrase an email's sender name may contain, lower case, longest first so "State Bank of India" and "Union Bank of India" win over "Bank of India". */
    private val emailPhrases: List<Pair<String, String>> = (
        BankRules.ALL.filter { it.emailNamed }.groupBy { it.issuer }.map { (name, rules) -> name.lowercase() to rules.first().root } +
            BankRules.ALL.flatMap { r -> r.emailAliases.map { it to r.root } }
        ).distinctBy { it.first }.sortedByDescending { it.first.length }

    private fun isWordChar(c: Char) = c.isLetterOrDigit()

    /** Whether [phrase] stands in [text] as whole words ("hsbc" in "HSBC Alerts", not in "xhsbcx"). */
    private fun hasWords(text: String, phrase: String): Boolean {
        var from = 0
        while (true) {
            val at = text.indexOf(phrase, from)
            if (at < 0) return false
            val end = at + phrase.length
            if ((at == 0 || !isWordChar(text[at - 1])) && (end == text.length || !isWordChar(text[end]))) return true
            from = at + 1
        }
    }

    /**
     * The header root of the bank an email says it is from, judged by the sender's display name ("HDFC Bank", "ICICI Bank Alerts") or an address written in its
     * words ("alerts@hdfcbank.net"), or null when it names no bank on the built-in list or the person's [extraRoots]. An email app's notification has no SMS header, so
     * this is what stands in for one; nothing else about the email is looked at. A name of seven letters or more also matches with its spaces closed up, which is how
     * an address writes it. **The names are written from public knowledge of the banks, not checked against real emails.**
     */
    fun emailIssuer(senderName: String, extraRoots: Set<String> = emptySet()): String? {
        val lower = senderName.trim().lowercase()
        if (lower.isEmpty()) return null
        val closed = lower.filter { isWordChar(it) }
        for ((phrase, root) in emailPhrases) {
            if (hasWords(lower, phrase)) return root
            val tight = phrase.filter { isWordChar(it) }
            if (tight.length >= 7 && closed.contains(tight)) return root
        }
        return extraRoots.map { it.trim().uppercase() }.filter { it.length >= 4 }.firstOrNull { closed.contains(it.lowercase()) }
    }
}
