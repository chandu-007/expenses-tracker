package app.expenses.core.domain

/**
 * Categories that come from the words of a bank text rather than from who was paid, for the newer starter categories ([StarterCategories]). A bank's own charges ("Annual fee of Rs.500
 * debited", "SMS charges", "ATM charges") name no merchant, so the merchant table cannot see them. Used only after everything the person chose and every merchant, and only when the person
 * has the category; it never creates one. Cash taken out at an ATM is not here: a withdrawal is recognised by [app.expenses.core.parsers.KindRules] (money moving to the person's cash).
 */
object CategoryHints {
    private val charges = Regex(
        """(?i)\b(?:annual|joining|renewal|late\s+payment|late|processing|sms|amc|service|maintenance|non[- ]?maintenance|minimum\s+balance|atm|card|debit\s+card|folio|demat)\s+(?:fee|fees|charge|charges)\b|\b(?:fee|fees|charges?)\s+(?:debited|deducted|levied|applied)\b|\bgst\s+on\s+(?:fee|fees|charge|charges)\b""",
    )

    /** The newer starter category the words of [body] point at ("Fees and charges"), or null. */
    fun categoryNameFor(body: String): String? = if (charges.containsMatchIn(body)) "Fees and charges" else null
}
