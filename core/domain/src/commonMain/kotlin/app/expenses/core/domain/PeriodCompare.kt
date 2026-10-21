package app.expenses.core.domain

/**
 * The line on the home screen that sets this period's spending against the last one at the same point in it ("so far": the first 10 days of this month
 * against the first 10 days of last month), so a half-spent month is not compared with a whole one.
 */
object PeriodCompare {
    /**
     * [current] and [previous] are spending in minor units over the same number of days. Null when there is nothing to compare with (nothing was spent
     * in the earlier period). [last] names the earlier period ("last month"); [format] writes an amount.
     */
    fun describe(current: Long, previous: Long, last: String, format: (Long) -> String): String? {
        if (previous <= 0) return null
        val diff = current - previous
        if (diff == 0L) return "Same as $last so far"
        val percent = (kotlin.math.abs(diff) * 100 / previous).coerceAtMost(999)
        return if (diff > 0) "▲ ${format(diff)} more than $last so far (+$percent%)" else "▼ ${format(-diff)} less than $last so far (−$percent%)"
    }

    /** How the earlier period is named for each kind of budget period; null for a yearly period, which is too long to compare "so far" usefully. */
    fun lastName(period: BudgetPeriod): String? = when (period) {
        is BudgetPeriod.Weekly -> "last week"
        is BudgetPeriod.Monthly -> if (period.startDay == 1) "last month" else "the last cycle"
        is BudgetPeriod.BiWeekly -> "the last fortnight"
        BudgetPeriod.SemiMonthly -> "the last half-month"
        is BudgetPeriod.EveryDays -> "the last period"
        is BudgetPeriod.Yearly -> null
    }
}
