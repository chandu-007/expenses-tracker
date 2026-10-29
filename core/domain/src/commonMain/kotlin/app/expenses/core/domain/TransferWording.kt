package app.expenses.core.domain

/**
 * Wording that says money is moving between a person's own accounts, in one place for everything that reads it (the pairing of two bank texts, the statement import).
 *
 * [says] is the strict reading: "transfer", "own account", "self", TPT (HDFC's third-party transfer code), "FT-", "sweep". It is what lets two texts of the same amount be paired by amount and time alone,
 * so it must not be satisfied by the payment rails: IMPS, NEFT and RTGS carry salaries, rent and every payment to another person, and 21% of the sample corpus mentions one.
 * [saysOrRail] adds the rails, for a statement line that is judged next to a transfer the app already has (the same amount on the same account within a day).
 */
object TransferWording {
    private val strict = Regex(
        """(?i)\btransfer\b|\bfunds?\s+transfer\b|\b(?:own|my|self)\s+(?:a/?c|account)\b|\bself\b|\bTPT\b|\bFT\s*-|\bsweep\b""",
    )
    private val rails = Regex("""(?i)\b(?:imps|neft|rtgs|trf)\b""")

    fun says(text: String): Boolean = strict.containsMatchIn(text)

    fun saysOrRail(text: String): Boolean = says(text) || rails.containsMatchIn(text)
}
