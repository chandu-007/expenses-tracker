package app.expenses.core.parsers

/**
 * The courtesy line at the end of a bill reminder that says what to do **if the bill is already paid**: "Kindly ignore, if already paid.", "Please ignore this
 * message if you have already made the payment", "If payment is already made, disregard this". It is not a payment, but it carries the word "paid" (or
 * "payment ... made"), which the transaction rules read as money going out and the receipt rules read as a payment that went through. Both parsers read the
 * words with this clause taken out, so a reminder is judged by what it asks for, not by what it says to do when the request is already met.
 */
internal object PaidDisclaimer {
    private val ignoreClause = Regex("""(?i)\b(?:ignore|disregard)\b[^.!?\n]{0,80}\b(?:paid|payment|settled|made|cleared|done)\b[^.!?\n]*""")
    private val ifPaidClause = Regex("""(?i)\b(?:if|in\s+case|once)\s+(?:you\s+(?:have\s+|'ve\s+)?)?(?:the\s+|your\s+)?(?:(?:bill|payment|amount|dues?)\s+(?:is\s+|has\s+been\s+|was\s+)?)?(?:already\s+)?(?:paid|settled|cleared|made|done)\b[^.!?\n]*""")
    private val ifPaymentClause = Regex("""(?i)\b(?:if|in\s+case)\s+(?:you\s+have\s+)?(?:already\s+)?(?:made|done)\s+(?:the\s+|this\s+)?payment\b[^.!?\n]*""")

    /** [text] with the "ignore if already paid" clauses removed (replaced by a space). */
    fun strip(text: String): String = ifPaymentClause.replace(ifPaidClause.replace(ignoreClause.replace(text, " "), " "), " ")
}
