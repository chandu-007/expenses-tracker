package app.expenses.core.domain

/** ISO 4217 currency. [minorUnitDigits] is the number of decimal places (INR = 2, JPY = 0, KWD = 3). */
data class Currency(val code: String, val minorUnitDigits: Int) {
    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Invalid currency code: $code" }
        require(minorUnitDigits in 0..4) { "Invalid minorUnitDigits: $minorUnitDigits" }
    }

    companion object {
        val INR = Currency("INR", 2)
        val USD = Currency("USD", 2)
    }
}

/**
 * An amount in a single currency, stored as an integer count of minor units (paise, cents).
 * Never uses floating point. Arithmetic across different currencies is rejected.
 * Overflow throws instead of wrapping.
 */
data class Money(val minorUnits: Long, val currency: Currency) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math2.addExact(minorUnits, other.minorUnits), currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math2.subtractExact(minorUnits, other.minorUnits), currency)
    }

    operator fun unaryMinus(): Money = Money(Math2.negateExact(minorUnits), currency)

    val isZero: Boolean get() = minorUnits == 0L
    val isNegative: Boolean get() = minorUnits < 0L

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Currency mismatch: ${currency.code} vs ${other.currency.code}" }
    }

    companion object {
        fun zero(currency: Currency) = Money(0L, currency)
    }
}

/** Overflow-checked Long arithmetic that works on every Kotlin Multiplatform target. */
internal object Math2 {
    fun addExact(a: Long, b: Long): Long {
        val r = a + b
        if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("Long overflow")
        return r
    }

    fun subtractExact(a: Long, b: Long): Long {
        val r = a - b
        if (((a xor b) and (a xor r)) < 0) throw ArithmeticException("Long overflow")
        return r
    }

    fun negateExact(a: Long): Long {
        if (a == Long.MIN_VALUE) throw ArithmeticException("Long overflow")
        return -a
    }
}
