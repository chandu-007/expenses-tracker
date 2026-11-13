package app.expenses.core.domain

/** One entry of the list as the day totals need it: the local day (epoch-day number), its kind, the amount in minor units and the currency the amount is in. */
data class DayEntry(val day: Long, val kind: String, val minor: Long, val currency: String)

/**
 * The money spent on each day of Home's list, for the day headings ("Today  -₹1,112.00").
 *
 * - **Spent only**: the sum of the EXPENSE entries of the day. Income, refunds, transfers between one's own accounts and settlements with people are not spending and are not in it
 *   (a day with nothing but income has no total).
 * - **One currency**: only entries in [currency] (the ledger's) are added; an entry in another currency is left out rather than added across currencies.
 * - **Whole days only**: the list is read a page at a time, newest first, so the oldest day on the screen may continue on the next page. Its total is left out until the rest of the
 *   list has been read ([endReached]); an incomplete figure would be a wrong one.
 *
 * Pure and works on the rows already on the screen: no query.
 */
object DayTotals {
    const val EXPENSE = "EXPENSE"

    /** day -> total spent in minor units, for each complete day with at least one expense. [entries] are in list order (newest day first, days not interleaved). */
    fun of(entries: List<DayEntry>, currency: String, endReached: Boolean): Map<Long, Long> {
        if (entries.isEmpty()) return emptyMap()
        val lastDay = entries.last().day
        val totals = LinkedHashMap<Long, Long>()
        for (e in entries) {
            if (e.kind != EXPENSE || e.currency != currency) continue
            if (!endReached && e.day == lastDay) continue
            totals[e.day] = (totals[e.day] ?: 0L) + e.minor
        }
        return totals
    }
}
