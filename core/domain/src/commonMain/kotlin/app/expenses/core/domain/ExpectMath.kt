package app.expenses.core.domain

/** The sums behind "I expect this back". */
object ExpectMath {
    /**
     * What is still to come back: the amount expected (never more than the expense itself) less what has been refunded against the expense, never below zero. The
     * expense keeps counting as spending until a refund reduces it, exactly as any refund does.
     */
    fun remaining(expectedMinor: Long, amountMinor: Long, refundedMinor: Long): Long = (minOf(expectedMinor, amountMinor) - refundedMinor).coerceAtLeast(0L)

    /** "from Asha", "from your employer", or "" when nobody was named. [personName] is the name of a person on the list, [from] free text. */
    fun fromText(personName: String?, from: String?): String {
        val who = personName?.trim()?.takeIf { it.isNotEmpty() } ?: from?.trim()?.takeIf { it.isNotEmpty() }
        return if (who == null) "" else "from $who"
    }

    /**
     * The amount typed for "expected back": blank means the whole expense. Null when it is not a positive amount, or is more than the expense. [parse] reads a typed amount
     * into minor units.
     */
    fun expected(text: String, amountMinor: Long, parse: (String) -> Long?): Long? {
        if (text.isBlank()) return amountMinor
        val v = parse(text) ?: return null
        return v.takeIf { it in 1..amountMinor }
    }
}
