package app.expenses.core.domain

/**
 * Paying in another currency while travelling: the amount you typed in that currency and the rate you chose (how many units of the ledger's currency one
 * unit of it is worth), turned into the amount that left your account. The app has no live rates and never looks any up; the rate is yours, so the
 * entry says exactly what you worked out, and the foreign amount is kept beside it.
 */
object ForeignAmount {
    private const val MAX_RATE = 1_000_000.0

    /** The rate as a number, or null when it is not a positive number with at most six decimals. */
    fun parseRate(text: String): Double? {
        val clean = text.filter { it != ',' && !it.isWhitespace() }
        if (clean.isEmpty() || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' } || clean.substringAfter('.', "").length > 6) return null
        val v = clean.toDoubleOrNull() ?: return null
        return v.takeIf { it > 0 && it <= MAX_RATE }
    }

    /**
     * What [foreignText] in [foreign] comes to in [base] at [rateText], rounded to the nearest minor unit (halves up). Null when either text is not valid or the
     * result would not fit. Worked out in floating point, which is exact enough for the amounts a person pays (far under a trillion).
     */
    fun convert(foreignText: String, rateText: String, foreign: Currency, base: Currency): Pair<Money, Money>? {
        val paid = parseAmount(foreignText, foreign)?.takeIf { it.minorUnits > 0 } ?: return null
        val rate = parseRate(rateText) ?: return null
        var scale = 1.0; repeat(foreign.minorUnitDigits) { scale *= 10 }
        var baseScale = 1.0; repeat(base.minorUnitDigits) { baseScale *= 10 }
        val minor = kotlin.math.floor(paid.minorUnits / scale * rate * baseScale + 0.5)
        if (minor < 1 || minor > 1e15) return null
        return Money(minor.toLong(), base) to paid
    }
}
