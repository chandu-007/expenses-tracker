package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** A spending limit for one period. A null [categoryId] is the overall cap on everything; otherwise it limits that category and its subcategories. */
data class BudgetLimit(val categoryId: String?, val limit: Money) {
    init {
        require(limit.minorUnits > 0) { "A budget limit must be positive" }
    }
}

enum class BudgetState { OK, NEAR, OVER }

/** Where one limit stands. [remaining] goes negative once spending passes the limit. */
data class BudgetProgress(
    val categoryId: String?,
    val limit: Money,
    val spent: Money,
    val remaining: Money,
    /** Whole percent of the limit used, rounded down; it exceeds 100 when over. */
    val usedPercent: Int,
    val state: BudgetState,
)

/** What is left to spend freely until the period ends. */
data class SafeToSpend(val remaining: Money, val daysLeft: Int, val perDay: Money)

object BudgetMath {
    /** At or above this share of the limit the budget is flagged as near its limit. */
    const val NEAR_PERCENT = 80

    /**
     * Progress for every limit. [spentByCategory] is net spending (refunds already taken off, only your share of shared expenses),
     * with `null` for uncategorised. A category limit counts the category and everything in [childrenOf] it; the overall limit
     * counts all of it, including uncategorised. All amounts must be in the currency of the limits.
     */
    fun progress(
        limits: List<BudgetLimit>,
        spentByCategory: Map<String?, Money>,
        childrenOf: Map<String, List<String>> = emptyMap(),
    ): List<BudgetProgress> = limits.map { b ->
        val currency = b.limit.currency
        require(spentByCategory.values.all { it.currency == currency }) { "Spending and limits must be in the same currency" }
        val spent = if (b.categoryId == null) {
            spentByCategory.values.fold(Money.zero(currency)) { acc, m -> acc + m }
        } else {
            val ids = listOf(b.categoryId) + childrenOf[b.categoryId].orEmpty()
            ids.fold(Money.zero(currency)) { acc, id -> acc + (spentByCategory[id] ?: Money.zero(currency)) }
        }
        val percent = percentUsed(spent.minorUnits, b.limit.minorUnits)
        BudgetProgress(
            b.categoryId, b.limit, spent, b.limit - spent, percent,
            when {
                spent > b.limit -> BudgetState.OVER
                percent >= NEAR_PERCENT -> BudgetState.NEAR
                else -> BudgetState.OK
            },
        )
    }

    /** `spent * 100 / limit`, rounded down, without overflowing for very large amounts. Spending below zero (refunds) counts as 0. */
    internal fun percentUsed(spent: Long, limit: Long): Int {
        require(limit > 0) { "limit must be positive" }
        if (spent <= 0) return 0
        if (spent > Long.MAX_VALUE / 100) return Int.MAX_VALUE
        return (spent * 100 / limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * What can be spent per day for the rest of [period], from the overall limit's remaining amount. Today counts as a day left,
     * so on the last day the whole remainder is available. Nothing is left once the limit is reached.
     *
     * This does not yet subtract upcoming bills or planned savings; recurring items are not built, so it is simply "remaining
     * cap spread over the days left".
     */
    fun safeToSpend(overall: BudgetProgress, today: LocalDate, period: DatePeriod): SafeToSpend {
        require(overall.categoryId == null) { "Safe to spend comes from the overall limit" }
        val daysLeft = if (today < period.start) period.days else maxOf(1, today.daysUntil(period.endExclusive))
        val remaining = overall.remaining
        val perDay = if (remaining.minorUnits <= 0) Money.zero(remaining.currency) else Money(remaining.minorUnits / daysLeft, remaining.currency)
        return SafeToSpend(remaining, daysLeft, perDay)
    }
}
