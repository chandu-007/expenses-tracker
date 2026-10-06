package app.expenses.core.domain

/**
 * Formats an amount for display. Rupees use Indian grouping (last three digits, then pairs): ₹12,34,567.89.
 * Other currencies use thousands grouping with the currency code: USD 1,234.56. Negative amounts put the sign
 * first. Uses integer arithmetic only, so no rounding error and no locale dependency.
 */
fun Money.formatted(): String {
    val digits = currency.minorUnitDigits
    val negative = minorUnits < 0
    // abs of Long.MIN_VALUE overflows, so work with the digit string of the negative value instead.
    val raw = minorUnits.toString().removePrefix("-").padStart(digits + 1, '0')
    val whole = raw.substring(0, raw.length - digits)
    val fraction = if (digits == 0) "" else "." + raw.substring(raw.length - digits)
    val isRupee = currency.code == "INR"
    val grouped = if (isRupee) groupIndian(whole) else groupThousands(whole)
    val sign = if (negative) "-" else ""
    val symbol = if (isRupee) "₹" else currency.code + " "
    return "$sign$symbol$grouped$fraction"
}

private fun groupThousands(whole: String): String = whole.reversed().chunked(3).joinToString(",").reversed()

private fun groupIndian(whole: String): String {
    if (whole.length <= 3) return whole
    val lastThree = whole.takeLast(3)
    val rest = whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return "$rest,$lastThree"
}

/**
 * Parses what a user types into an amount of [currency]: digits with an optional decimal point and at most the
 * currency's decimal places. Commas and spaces are ignored. Returns null for anything else (empty, negative,
 * letters, too many decimals, or too large to store).
 */
fun parseAmount(text: String, currency: Currency): Money? {
    val clean = text.filter { it != ',' && !it.isWhitespace() }
    if (clean.isEmpty() || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' }) return null
    val whole = clean.substringBefore('.')
    val fraction = clean.substringAfter('.', "")
    if (whole.isEmpty() && fraction.isEmpty()) return null
    if (fraction.length > currency.minorUnitDigits) return null
    if (whole.length > 15) return null // far beyond any real amount; keeps the Long arithmetic safe
    val minor = (whole.ifEmpty { "0" } + fraction.padEnd(currency.minorUnitDigits, '0')).toLongOrNull() ?: return null
    return Money(minor, currency)
}
