package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** How spending stands against how much of the period has gone (UI S5). Never a verdict on the person: it says where the bar is against "today". */
enum class PaceLevel { EARLY, ON_PACE, A_LITTLE_AHEAD, AHEAD, OVER }

/**
 * Pace for a limit (UI S5, backlog U-06): the share of the period that has gone ("today" on the bar), and a word for where the spending stands against it. Pure arithmetic over whole numbers,
 * so the words at their boundaries are tested on the host.
 */
object PaceMath {
    /** Days of the period that have started, today included, from 1 to the length of the period. A date before the period counts as the first day, one after it as the last. */
    fun daysIn(period: DatePeriod, today: LocalDate): Int = (period.start.daysUntil(today) + 1).coerceIn(1, period.days)

    /** The share of the period gone, today counted as a whole day: day 14 of 30 is 14/30. */
    fun elapsed(period: DatePeriod, today: LocalDate): Float = daysIn(period, today).toFloat() / period.days

    /** The first days of a period say nothing yet: one day's rent would look like a month of overspending. */
    const val EARLY_DAYS = 2

    /** Up to this multiple of the pace (spent against the share of the period gone) is "a little ahead"; beyond it, "ahead". */
    const val LITTLE_AHEAD = 1.25

    /**
     * Where [spent] stands against [limit] with [elapsed] of the period gone ([daysIn] days, of [periodDays]). [upcoming] is what repeating bills still to come will take: when spent plus upcoming is more
     * than the limit, the limit will be passed whatever the pace so far, so it is at least "a little ahead" (a rent still to be posted is not a reason to feel on pace). Amounts in minor units of one currency.
     */
    fun level(spent: Long, limit: Long, elapsed: Float, daysIn: Int, periodDays: Int, upcoming: Long = 0L): PaceLevel {
        if (limit <= 0) return PaceLevel.EARLY
        if (spent > limit) return PaceLevel.OVER
        val bills = spent.coerceAtLeast(0) + upcoming.coerceAtLeast(0) > limit
        if (daysIn <= EARLY_DAYS && periodDays > 4 && !bills) return PaceLevel.EARLY
        val ratio = (spent.coerceAtLeast(0).toDouble() / limit) / elapsed.coerceAtLeast(0.0001f)
        return when {
            bills && ratio <= LITTLE_AHEAD -> PaceLevel.A_LITTLE_AHEAD
            ratio <= 1.0 -> PaceLevel.ON_PACE
            ratio <= LITTLE_AHEAD -> PaceLevel.A_LITTLE_AHEAD
            else -> PaceLevel.AHEAD
        }
    }

    /** The word for a level; "Over by ₹1,655" needs [overBy] (the formatted amount over the limit). */
    fun word(level: PaceLevel, overBy: String = ""): String = when (level) {
        PaceLevel.EARLY -> "Just started"
        PaceLevel.ON_PACE -> "On pace"
        PaceLevel.A_LITTLE_AHEAD -> "A little ahead"
        PaceLevel.AHEAD -> "Ahead of pace"
        PaceLevel.OVER -> if (overBy.isEmpty()) "Over the limit" else "Over by $overBy"
    }

    /**
     * The status chip's four words (redesign rule 7): "On pace" and "Well under" (teal), "A little ahead" (amber), "Over by ₹X" (red). [wellUnder] is [isWellUnder]; the early days of a period
     * and "ahead of pace" fold into the nearest of the four, so every bar in the app says one of the same four things.
     */
    fun statusWord(level: PaceLevel, wellUnder: Boolean = false, overBy: String = ""): String = when (level) {
        PaceLevel.EARLY, PaceLevel.ON_PACE -> if (wellUnder) "Well under" else "On pace"
        PaceLevel.A_LITTLE_AHEAD, PaceLevel.AHEAD -> "A little ahead"
        PaceLevel.OVER -> if (overBy.isEmpty()) "Over the limit" else "Over by $overBy"
    }

    /** True when less than 60% of what an even pace would have spent by now is gone, once the period has really begun (the first tenth of it is too early to say). */
    fun isWellUnder(spentFraction: Float, elapsed: Float): Boolean = elapsed >= 0.1f && spentFraction <= elapsed * 0.6f

    /** What the end of the period is called on Home: "payday" when the person starts the month on their own day (their salary day), else what it is. */
    fun endWord(period: BudgetPeriod): String = when (period) {
        is BudgetPeriod.Monthly -> if (period.startDay != 1) "payday" else "month end"
        is BudgetPeriod.Weekly -> "week end"
        is BudgetPeriod.BiWeekly -> "fortnight end"
        BudgetPeriod.SemiMonthly -> "the next half"
        is BudgetPeriod.EveryDays -> "period end"
        is BudgetPeriod.Yearly -> "year end"
    }

    /** "₹1,565 a day · 16 days to payday" (the amount arrives formatted). The last day says "last day". */
    fun heroLine(perDay: String, daysLeft: Int, end: String): String {
        val tail = if (daysLeft <= 1) "last day before $end" else "$daysLeft days to $end"
        return "$perDay a day · $tail"
    }

    /** "You can keep spending ₹1,565 a day for 16 more days" (the sentence under the Budget ring). */
    fun sentence(perDay: String, daysLeft: Int): String =
        if (daysLeft <= 1) "You can spend $perDay today" else "You can keep spending $perDay a day for $daysLeft more days"
}
