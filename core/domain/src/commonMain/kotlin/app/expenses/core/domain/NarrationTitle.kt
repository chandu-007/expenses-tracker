package app.expenses.core.domain

/**
 * A clean title for the narration a bank prints in a transaction text ("XXXXXXXXXX1234-TPT-Holiday Fund Remembe-SAMPLE TRADERS PRIVATE LIMITED", "UPI-RAVI KUMAR-ravi@okaxis-123456789012",
 * "IMPS-123456789012-ANITA RAO", "NEFT Cr-HDFC0000001-State-Bank Remitter-Customer Name-N123456789", "ACH D- TATA CAPITAL-123456", "ATM-CASH"): the person or note the
 * line is about, not the whole string. It reuses [StatementDescriptions.tidy], the statement import's reading of the same shapes, and adds the two text-only cases (a hyphenated
 * NEFT remitter, ATM cash). The text the title came from is never changed: callers keep it as the entry's origin.
 *
 * Which part is the "name" follows [StatementDescriptions.tidy]: the first part after the mode that is neither a bank code nor a reference nor a handle. In HDFC's own-account form
 * "TPT-note-name" that is the note the sender typed (the more useful title for a transfer); with no note it is the name.
 */
object NarrationTitle {
    private const val MAX = 40
    private val NEFT_CREDIT = Regex("^(?:NEFT|RTGS|IMPS)\\s+CR\\s*-", RegexOption.IGNORE_CASE)
    private val FUND_TRANSFER = Regex("^FT\\s*-", RegexOption.IGNORE_CASE)
    private val ATM = Regex("^ATM\\s*[-/ ]\\s*(?:CASH|WDL|WITHDRAWAL)\\b.*$", RegexOption.IGNORE_CASE)
    private val IFSC = Regex("^[A-Za-z]{4}0[A-Za-z0-9]{6}$|^[A-Za-z]{4}\\d?[A-Za-z0-9]{6,12}$")

    /** The title, or null when [narration] holds no name to read (the caller then has nothing better than the bank's own name). */
    fun of(narration: String): String? {
        val cleaned = narration.replace(Regex("\\s+"), " ").trim().trimEnd('.', '-', ' ')
        if (cleaned.isEmpty()) return null
        if (ATM.matches(cleaned)) return "ATM cash"
        remitterOfCredit(cleaned)?.let { return shorten(it) }
        nameAfterAccount(cleaned)?.let { return shorten(it) }
        val t = StatementDescriptions.tidy(cleaned).trim().trimEnd('.', '-', ' ')
        // tidy hands the whole line back when it found no name in it
        if (t.isEmpty() || t == cleaned) return null
        return shorten(t)
    }

    /**
     * "NEFT Cr-<IFSC>-<remitter>-<account holder>-<reference>": the remitter is everything between the code and the last two parts, so a remitter whose own name has a hyphen
     * ("State-Bank Remitter") stays whole. Only when the line has that exact shape (a mode with Cr, a code, at least a remitter, a holder and a reference).
     */
    private fun remitterOfCredit(s: String): String? {
        if (!NEFT_CREDIT.containsMatchIn(s)) return null
        val parts = s.split('-').map { it.trim() }
        if (parts.size < 5 || !IFSC.matches(parts[1])) return null
        return parts.subList(2, parts.size - 2).joinToString("-").takeIf { it.count { c -> c.isLetter() } >= 2 }
    }

    /**
     * HDFC's fund-transfer credit "FT- <account holder>-<masked account number>-<remitter>" (as in its public sample text): the first name is the person's own, the one after the
     * masked number is who sent the money. Only for a line that starts with FT and has a masked number.
     */
    private fun nameAfterAccount(s: String): String? {
        if (!FUND_TRANSFER.containsMatchIn(s)) return null
        val parts = s.split('-').map { it.trim() }
        val at = parts.indexOfFirst { StatementDescriptions.MASKED_ACCOUNT.matches(it) }
        if (at < 0) return null
        return parts.drop(at + 1).joinToString("-").trim().takeIf { it.count { c -> c.isLetter() } >= 2 }
    }

    private fun shorten(s: String): String = if (s.length <= MAX) s else s.take(MAX).trimEnd()
}
