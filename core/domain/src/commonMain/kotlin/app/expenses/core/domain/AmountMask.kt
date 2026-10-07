package app.expenses.core.domain

/**
 * Hides money in text for use in public. An amount is a currency symbol or code followed by digits, optionally signed, with grouping,
 * decimals and a compact suffix: "₹1,23,456.78", "-₹950", "+₹2.5K", "USD 1,234.56", "₹3.4Cr". Everything else is left alone, so dates,
 * counts, percentages and labels such as "Amount (₹)" read as before.
 */
object AmountMask {
    const val HIDDEN = "••••"
    private val amount = Regex("""([-+]?)(₹|\b[A-Z]{3} )\d[\d,]*(\.\d+)?(Cr|[KLMB])?""")

    fun mask(text: String): String = amount.replace(text) { m -> m.groupValues[1] + (if (m.groupValues[2] == "₹") "₹" else m.groupValues[2]) + HIDDEN }
}
