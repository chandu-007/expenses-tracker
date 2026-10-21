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

/** One category's spending so far this period and what it usually is by the same point (the average of the earlier periods). */
data class InsightLine(val name: String, val current: Long, val usual: Long)

/** The one thing worth saying about where spending differs from usual. */
object SpendingInsight {
    const val MIN_EXTRA_MINOR = 50_000L // at least 500 more than usual (in rupees; other currencies scale the same way)
    const val MIN_EXTRA_PERCENT = 25

    /**
     * The category that is furthest above its usual (by amount), when it is clearly above: at least [MIN_EXTRA_PERCENT] percent and [MIN_EXTRA_MINOR] more.
     * Null when nothing stands out, so the line is only shown when it has something to say.
     */
    fun best(lines: List<InsightLine>, format: (Long) -> String): String? {
        val hit = lines.filter { it.usual > 0 && it.current - it.usual >= MIN_EXTRA_MINOR && (it.current - it.usual) * 100 / it.usual >= MIN_EXTRA_PERCENT }.maxByOrNull { it.current - it.usual } ?: return null
        val percent = (hit.current - hit.usual) * 100 / hit.usual
        return "${hit.name} is $percent% above your usual (${format(hit.current)} against ${format(hit.usual)} by now)"
    }
}
