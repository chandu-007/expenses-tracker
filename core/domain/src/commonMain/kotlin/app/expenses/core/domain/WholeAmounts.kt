package app.expenses.core.domain

/**
 * Whole-unit amounts for Home's summary figures (the month total, the budget and safe-to-spend tiles, the account tiles): "₹31,822.40" is drawn "₹31,822". Only the DISPLAYED text is rounded; the exact
 * amount stays in the data, in the list rows, the entry, the reports, every edit field, and in what a screen reader says (callers keep the exact text for that).
 *
 * Rules, so a rounded figure never lies:
 * - round half up on the size of the amount (₹0.50 reads ₹1, -₹0.50 reads -₹1; the digit after the point decides, so ₹10.499 reads ₹10);
 * - an amount that would read zero but is not zero keeps its paise (₹0.40 stays ₹0.40; Home never says "₹0 left" for 40 paise);
 * - only amounts with a decimal part change; compact amounts (₹1.2K, ₹3.4Cr), counts, dates and percentages are left alone;
 * - works on the text, with exact decimal digits and no floating point, in every currency the app formats (rupees keep Indian grouping).
 */
object WholeAmounts {
    private val amount = Regex("""([-+]?)(₹|\b[A-Z]{3} )(\d[\d,]*)\.(\d+)(?![\dKLMB]|Cr)""")

    /** [text] with each amount that has a decimal part rounded to whole units. */
    fun round(text: String): String = amount.replace(text) { m ->
        val sign = m.groupValues[1]; val symbol = m.groupValues[2]; val digits = m.groupValues[3].replace(",", ""); val fraction = m.groupValues[4]
        val whole = digits.toLongOrNull() ?: return@replace m.value // far beyond any real amount: leave it exact
        val up = fraction[0] >= '5'
        val rounded = if (up) whole + 1 else whole
        val nonZero = whole != 0L || fraction.any { it != '0' }
        if (rounded == 0L && nonZero) m.value
        else sign + symbol + (if (symbol == "₹") groupIndian(rounded.toString()) else groupThousands(rounded.toString()))
    }

    private fun groupThousands(whole: String): String = whole.reversed().chunked(3).joinToString(",").reversed()

    private fun groupIndian(whole: String): String {
        if (whole.length <= 3) return whole
        return whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + whole.takeLast(3)
    }
}

/** [this] with every decimal amount in it rounded to whole units for display (see [WholeAmounts]). */
fun String.wholeAmounts(): String = WholeAmounts.round(this)
