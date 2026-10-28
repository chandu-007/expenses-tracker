package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus

enum class RepeatUnit { DAY, WEEK, MONTH, YEAR }

/** When a repeating item stops. */
sealed interface ScheduleEnd {
    data object Never : ScheduleEnd

    /** The last occurrence is on or before [date]. */
    data class OnOrBefore(val date: LocalDate) : ScheduleEnd

    /** Exactly [count] occurrences in total, counting the first. */
    data class AfterCount(val count: Int) : ScheduleEnd {
        init { require(count >= 1) { "A schedule needs at least one occurrence" } }
    }
}

/** One occurrence of a schedule: its position [index] (0 is the first) and its [date]. */
data class Occurrence(val index: Int, val date: LocalDate)

/**
 * A repeating schedule: every [every] days, weeks, months or years from [start], until [end].
 *
 * Each date is computed from the start and its position, never from the previous date, so a clamped date does not drift: a monthly
 * item that starts on the 31st falls on 31 Jan, 28 Feb (29 in a leap year), 31 Mar, 30 Apr and back to 31 May. Weekly items keep
 * the weekday of [start]; yearly items keep its month and day (29 Feb falls on 28 Feb in other years).
 */
data class Schedule(val unit: RepeatUnit, val every: Int, val start: LocalDate, val end: ScheduleEnd = ScheduleEnd.Never) {
    init {
        require(every in 1..MAX_EVERY) { "Repeat interval must be 1 to $MAX_EVERY" }
        if (end is ScheduleEnd.OnOrBefore) require(end.date >= start) { "A schedule cannot end before it starts" }
    }

    /** The date of occurrence [index], or null when the schedule has ended before it. */
    fun dateAt(index: Int): LocalDate? {
        require(index >= 0) { "index must not be negative" }
        if (end is ScheduleEnd.AfterCount && index >= end.count) return null
        val date = when (unit) {
            RepeatUnit.DAY -> start.plus(index.toLong() * every, DateTimeUnit.DAY)
            RepeatUnit.WEEK -> start.plus(index.toLong() * every * 7, DateTimeUnit.DAY)
            RepeatUnit.MONTH -> monthsLater(index.toLong() * every)
            RepeatUnit.YEAR -> monthsLater(index.toLong() * every * 12)
        }
        return if (end is ScheduleEnd.OnOrBefore && date > end.date) null else date
    }

    /** Occurrences with `from <= date < until`, in order. */
    fun occurrences(from: LocalDate, until: LocalDate): List<Occurrence> {
        val out = mutableListOf<Occurrence>()
        var i = firstIndexNear(from)
        while (true) {
            val d = dateAt(i) ?: break
            if (d >= until) break
            if (d >= from) out += Occurrence(i, d)
            i++
        }
        return out
    }

    /** The first occurrence on or after [from], or null if the schedule has ended. */
    fun nextOnOrAfter(from: LocalDate): Occurrence? {
        var i = firstIndexNear(from)
        while (true) {
            val d = dateAt(i) ?: return null
            if (d >= from) return Occurrence(i, d)
            i++
        }
    }

    /** The date of the last occurrence, or null when the schedule never ends. */
    fun lastDate(): LocalDate? = when (end) {
        ScheduleEnd.Never -> null
        is ScheduleEnd.AfterCount -> dateAt(end.count - 1)
        is ScheduleEnd.OnOrBefore -> {
            // from near the end, step back to an occurrence that is inside it, then forward to the last one
            var i = firstIndexNear(end.date)
            while (i > 0 && dateAt(i) == null) i--
            while (dateAt(i + 1) != null) i++
            dateAt(i)
        }
    }

    private fun monthsLater(months: Long): LocalDate {
        val total = start.year.toLong() * 12 + (start.month.ordinal) + months
        val year = (total / 12).toInt()
        val month = (total % 12).toInt() + 1
        val first = LocalDate(year, month, 1)
        val length = first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
        return LocalDate(year, month, minOf(start.day, length))
    }

    /** A little before the first occurrence that could be on or after [from], so long histories are not walked from the start. */
    private fun firstIndexNear(from: LocalDate): Int {
        if (from <= start) return 0
        val estimate = when (unit) {
            RepeatUnit.DAY -> start.daysUntil(from) / every
            RepeatUnit.WEEK -> start.daysUntil(from) / (7 * every)
            RepeatUnit.MONTH -> start.monthsUntil(from) / every
            RepeatUnit.YEAR -> start.monthsUntil(from) / (12 * every)
        }
        return maxOf(0, estimate - 1)
    }

    companion object {
        const val MAX_EVERY = 365
    }
}
