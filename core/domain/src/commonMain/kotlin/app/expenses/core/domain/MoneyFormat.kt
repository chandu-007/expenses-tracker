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

/**
 * The amount as plain digits for an edit field: no symbol and no grouping, with exactly the currency's decimal
 * places ("1234567.50", "1200" for yen). The inverse of [parseAmount].
 */
fun Money.toPlainString(): String {
    val digits = currency.minorUnitDigits
    val negative = minorUnits < 0
    val raw = minorUnits.toString().removePrefix("-").padStart(digits + 1, '0')
    val whole = raw.substring(0, raw.length - digits)
    val fraction = if (digits == 0) "" else "." + raw.substring(raw.length - digits)
    return (if (negative) "-" else "") + whole + fraction
}

/**
 * A short amount for tight places (calendar cells, chart labels): ₹950, ₹1.2K, ₹12K, ₹1.2L, ₹3.4Cr. Rupees use lakh and crore; other
 * currencies use K, M and B with their code. The figure is cut, never rounded up, so ₹99,999 reads ₹99.9K and not ₹100K, and a short
 * label never overstates. Below ₹10 the paise are kept ("₹9.50"). Integer arithmetic only.
 */
fun Money.compact(): String {
    val digits = currency.minorUnitDigits
    var scale = 1L
    repeat(digits) { scale *= 10 }
    val negative = minorUnits < 0
    // work on the negative value's digits: abs of Long.MIN_VALUE overflows
    val units = minorUnits.toString().removePrefix("-").let { s -> if (s.length <= digits) 0L else s.substring(0, s.length - digits).toLong() }
    val fraction = minorUnits.toString().removePrefix("-").padStart(digits + 1, '0').takeLast(digits)
    val isRupee = currency.code == "INR"
    val symbol = if (isRupee) "₹" else currency.code + " "
    val tiers = if (isRupee) listOf(10_000_000L to "Cr", 100_000L to "L", 1_000L to "K") else listOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
    val body = tiers.firstOrNull { units >= it.first }?.let { (size, suffix) ->
        val whole = units / size
        val tenth = (units % size) * 10 / size
        (if (tenth == 0L) "$whole" else "$whole.$tenth") + suffix
    } ?: if (units < 10 && digits > 0 && fraction.any { it != '0' }) "$units.$fraction".trimEnd('0') else "$units"
    return (if (negative) "-" else "") + symbol + body
}
