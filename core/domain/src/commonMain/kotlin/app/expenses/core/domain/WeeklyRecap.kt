package app.expenses.core.domain

import kotlinx.datetime.DatePeriod as KxDatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The week a recap covers (Monday to Sunday, [weekEndExclusive] being the Monday after) and the text ready to show. */
data class RecapDue(val weekStart: LocalDate, val title: String, val text: String)

object WeeklyRecap {
    /** The Monday on or before [date]. */
    fun mondayOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)

    /** The last finished Monday-to-Sunday week as of [today]: the one before the week [today] is in. Its recap is due all through the current week. */
    fun recappedWeek(today: LocalDate): DatePeriod {
        val thisMonday = mondayOf(today)
        return DatePeriod(thisMonday.minus(7, DateTimeUnit.DAY), thisMonday)
    }

    fun previousWeek(week: DatePeriod): DatePeriod = DatePeriod(week.start.minus(7, DateTimeUnit.DAY), week.start)

    /**
     * What to tell the person: how much went out in the week, against the week before, and where most of it went. Null when there is nothing worth a
     * notification (nothing spent that week and the one before). [format] writes an amount in the ledger's currency; [top] is the largest category
     * (name and whole-percent share of the week), if any.
     */
    fun compose(week: DatePeriod, spent: Money, previous: Money, top: Pair<String, Int>?, format: (Money) -> String): Pair<String, String>? {
        if (spent.minorUnits <= 0 && previous.minorUnits <= 0) return null
        val title = if (spent.minorUnits <= 0) "Last week you spent nothing" else "Last week: ${format(spent)} spent"
        val parts = mutableListOf<String>()
        val change = ReportMath.percentChange(spent, previous)
        when {
            previous.minorUnits <= 0 -> {}
            spent.minorUnits == previous.minorUnits -> parts += "The same as the week before."
            change == null -> {}
            spent.minorUnits > previous.minorUnits -> parts += "${format(spent - previous)} more than the week before (+$change%)."
            else -> parts += "${format(previous - spent)} less than the week before ($change%)."
        }
        if (top != null && spent.minorUnits > 0) parts += "Most on ${top.first} (${top.second}%)."
        if (parts.isEmpty()) parts += "Open the app for the details."
        return title to parts.joinToString(" ")
    }

    fun key(weekStart: LocalDate) = "recap:${weekStart.toEpochDays()}"
}
