package app.expenses.core.domain.ledger

import app.expenses.core.domain.Math2

private fun mulExact(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0L
    val r = a * b
    if (r / b != a || (a == -1L && b == Long.MIN_VALUE) || (b == -1L && a == Long.MIN_VALUE)) {
        throw ArithmeticException("Long overflow")
    }
    return r
}

/**
 * Splits [total] minor units across [weights] proportionally with no rounding loss
 * (largest-remainder method; ties go to the earlier index). The result always sums to [total].
 */
internal fun allocate(total: Long, weights: List<Long>): List<Long> {
    require(total >= 0) { "total must not be negative" }
    require(weights.isNotEmpty() && weights.all { it > 0 }) { "weights must be positive" }
    val sum = weights.fold(0L) { acc, w -> Math2.addExact(acc, w) }
    val floors = LongArray(weights.size)
    val remainders = LongArray(weights.size)
    var assigned = 0L
    for (i in weights.indices) {
        val product = mulExact(total, weights[i])
        floors[i] = product / sum
        remainders[i] = product % sum
        assigned += floors[i]
    }
    var leftover = (total - assigned).toInt()
    val order = weights.indices.sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
    for (i in order) {
        if (leftover == 0) break
        floors[i] += 1
        leftover--
    }
    return floors.toList()
}
