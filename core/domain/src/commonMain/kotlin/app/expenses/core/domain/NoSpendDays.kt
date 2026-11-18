package app.expenses.core.domain

/**
 * UI S6 (U-16): the days of a month the Calendar marks with a small ring, "no spending". Pure: the caller hands over what each day of the month spent.
 *
 * A day counts when it is in the past (today is not over yet, so it is left alone), nothing was spent on it, and it is on or after the day of the ledger's first entry
 * (before that the person was not using the app, so a quiet day there says nothing). A ledger with no entry has no such day.
 */
object NoSpendDays {
    /** Epoch days of the days that were without spending. [days] are the month's days in order as (epoch day, minor units spent that day). */
    fun of(days: List<Pair<Long, Long>>, todayEpochDay: Long, firstEntryEpochDay: Long?): List<Long> {
        if (firstEntryEpochDay == null) return emptyList()
        return days.filter { (day, spent) -> spent <= 0L && day < todayEpochDay && day >= firstEntryEpochDay }.map { it.first }
    }

    /** "6 no-spend days this month" (or "1 no-spend day this month"); with a [monthLabel] (a month that is not this one) "6 no-spend days in October 2026"; null when there are none. */
    fun summary(count: Int, monthLabel: String?): String? {
        if (count <= 0) return null
        val unit = if (count == 1) "no-spend day" else "no-spend days"
        return if (monthLabel == null) "$count $unit this month" else "$count $unit in $monthLabel"
    }
}
