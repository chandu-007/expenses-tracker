package app.expenses.core.parsers

import app.expenses.core.domain.TransferWording

/**
 * What a bank text says about the *other end* of a payment when that end may be another account of the same person. Banks word the two halves of one
 * own-account transfer as two unrelated messages (one for each account, often seconds apart), so the only things that can tie them together are the amount, the
 * time, the digits of the other account when a text names them, and wording that says "transfer".
 *
 * The wordings were read from HDFC Bank's public sample texts (the corpus in this module's tests: "XXXXXXXXXX1234-TPT-note-name" in a credit's narration, "To A/c xxxxxxxxxx5678"
 * in an IMPS debit, "a/c *1234 ... to a/c **5678" in a UPI transfer, "Money Transfer via ... Online Banking" in a debit). Other banks' own-account wordings are not checked
 * against real messages; a bank that words them differently simply does not get the combined card, and its two texts stay two items.
 */
object TransferClues {
    /** A masked account number: six or more mask characters then the last four digits ("XXXXXXXXXX1234", "xxxxxxxxxx5678"). A shorter mask is the text's own "A/c XX1234". */
    private val maskedNumber = Regex("""(?i)[x*]{6,}(\d{4})\b""")

    /** "to a/c **5678", "from A/c XX1234": an account named after a direction word. */
    private val accountMention = Regex("""(?i)\b(?:a/?c|acct|account)\s*(?:no\.?)?\s*[:*]?\s*[x*]*(\d{3,6})(?:\b|-)""")

    /**
     * The last four digits of the account a text is ABOUT when it names two accounts and the first named is not that one: "Rs 5000 sent to A/c 9876 via IMPS from A/c XX1234" is about 1234 (the
     * account money left, for a debit), and "credited to A/c XX5678 ... from A/c XX1234" about 5678 (the account money reached, for a credit). Null when the text names fewer than two accounts, or
     * the first one named is already the right one, so the caller keeps its ordinary reading.
     */
    fun ownLast4(text: String, debit: Boolean): String? {
        val mentions = accountMention.findAll(text).toList()
        if (mentions.size < 2) return null
        fun after(m: MatchResult): Pair<Int, Int> { // last position of "from" and of "to"/"in" in the 30 characters before the mention
            val w = text.substring(maxOf(0, m.range.first - 30), m.range.first).lowercase()
            return w.lastIndexOf("from") to maxOf(w.lastIndexOf(" to "), w.lastIndexOf(" in "), w.lastIndexOf("into"), w.lastIndexOf("credited"), w.lastIndexOf("deposited"))
        }
        val chosen = mentions.firstOrNull { m -> after(m).let { (from, to) -> if (debit) from > to else to > from } } ?: return null
        val digits = chosen.groupValues[1].takeLast(4)
        return digits.takeIf { it.length == 4 && it != mentions.first().groupValues[1].takeLast(4) }
    }

    /**
     * The last four digits of the other account a text names, or null. [own] is the digits of the account the text is about (so the account's own digits are never
     * returned as the other end). A masked full number wins over an "A/c" mention because a bank prints the other party's number masked that way; otherwise the first
     * "A/c" mention that is not [own] is taken.
     */
    fun counterLast4(text: String, own: String?): String? {
        maskedNumber.findAll(text).map { it.groupValues[1] }.firstOrNull { it != own }?.let { return it }
        return accountMention.findAll(text).map { it.groupValues[1].takeLast(4) }.firstOrNull { it != own && it.length == 4 }
    }

    /**
     * Wording that says money is moving between accounts rather than being spent: "transfer" (Money Transfer, Fund Transfer), "own account", "self", TPT (HDFC's third-party transfer code), "FT-",
     * "sweep" ([TransferWording.says]). The bank-to-bank rails (IMPS, NEFT, RTGS) are NOT wording of this kind: they carry salaries and every payment to another person. On its own this proves
     * nothing; it only counts together with the same amount going the other way on another account of the person within minutes.
     */
    fun hasTransferWords(text: String): Boolean = TransferWording.says(text)
}
