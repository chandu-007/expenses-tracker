package app.expenses.core.domain

/**
 * Paying in another currency while travelling: the amount you typed in that currency and the rate you chose (how many units of the ledger's currency one
 * unit of it is worth), turned into the amount that left your account. The app has no live rates and never looks any up; the rate is yours, so the
 * entry says exactly what you worked out, and the foreign amount is kept beside it.
 */
object ForeignAmount {

    /** The rate as a number, or null when it is not a positive number with at most six decimals. */
    fun parseRate(text: String): Double? = parseRateScaled(text)?.let { it / 1_000_000.0 }

    /** The rate as a whole number of millionths ("83.25" is 83_250_000), exact; null when it is not a positive number with at most six decimals, or above a million. */
    fun parseRateScaled(text: String): Long? {
        val clean = text.filter { it != ',' && !it.isWhitespace() }
        if (clean.isEmpty() || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' } || clean.substringAfter('.', "").length > 6) return null
        val whole = clean.substringBefore('.').ifEmpty { "0" }
        if (whole.length > 7) return null
        val v = (whole + clean.substringAfter('.', "").padEnd(6, '0')).toLongOrNull() ?: return null
        return v.takeIf { it > 0 && it <= 1_000_000L * 1_000_000L }
    }

    /**
     * What [foreignText] in [foreign] comes to in [base] at [rateText], rounded to the nearest minor unit (halves up). Null when either text is not valid or the
     * result would not fit. Worked out in floating point, which is exact enough for the amounts a person pays (far under a trillion).
     */
    fun convert(foreignText: String, rateText: String, foreign: Currency, base: Currency): Pair<Money, Money>? {
        val paid = parseAmount(foreignText, foreign)?.takeIf { it.minorUnits > 0 } ?: return null
        val rate = parseRateScaled(rateText) ?: return null
        // whole-number arithmetic, as everywhere money is added up: minor_foreign x rate(millionths) x 10^base digits / (10^6 x 10^foreign digits), halves up
        var up = 1L; repeat(base.minorUnitDigits) { up *= 10 }
        var down = 1_000_000L; repeat(foreign.minorUnitDigits) { down *= 10 }
        val factor = rate * up
        if (factor <= 0 || paid.minorUnits > Long.MAX_VALUE / factor) return null // too large to work out exactly
        val minor = (paid.minorUnits * factor + down / 2) / down
        if (minor < 1 || minor > 1_000_000_000_000_000L) return null
        return Money(minor, base) to paid
    }
}
