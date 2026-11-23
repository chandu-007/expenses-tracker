package app.expenses.core.domain

import kotlinx.datetime.LocalDate

/**
 * N-18, "already spoken for": how much of what is left under the budget the bills and repeating expenses still due before the period ends will take. The safe-to-spend
 * figures already know these bills; this puts them in one line beside "left" ("₹2,100 of what's left is already spoken for by 3 bills due") and, on a tap, lists them.
 * Pure: the caller hands in the bills (tracked bills with a due date, unposted repeating expenses) and what is left.
 */
object SpokenFor {
    /** One bill or repeating expense. [paid] ones are never counted (a tracked bill marked paid, an occurrence already posted or skipped). */
    data class Due(val name: String, val amountMinor: Long, val due: LocalDate, val paid: Boolean = false)

    /**
     * What is spoken for: [items] still due inside the period, in date order, adding up to [totalMinor]; [leftMinor] is what is left under the limit (negative when over).
     * [coversAll] is true when the bills take all that is left or more.
     */
    data class Result(val items: List<Due>, val totalMinor: Long, val leftMinor: Long) {
        val count: Int get() = items.size
        val overBudget: Boolean get() = leftMinor < 0
        val coversAll: Boolean get() = !overBudget && totalMinor >= leftMinor
    }

    /**
     * The bills of [dues] that fall in [periodStart] (inclusive; one that was due earlier in the period and is still unpaid counts, it will still be paid) to
     * [periodEndExclusive], unpaid and with an amount, against [leftMinor]; null when there are none, so no line is drawn.
     */
    fun compute(leftMinor: Long, dues: List<Due>, periodStart: LocalDate, periodEndExclusive: LocalDate): Result? {
        val items = dues.filter { !it.paid && it.amountMinor > 0 && it.due >= periodStart && it.due < periodEndExclusive }.sortedWith(compareBy({ it.due }, { it.name.lowercase() }))
        if (items.isEmpty()) return null
        var total = 0L
        for (d in items) total = Math2.addExact(total, d.amountMinor)
        return Result(items, total, leftMinor)
    }

    /** "3 bills" / "1 bill". */
    fun bills(count: Int): String = if (count == 1) "1 bill" else "$count bills"

    /**
     * The line, with amounts made by [money]: "₹2,100.00 of what's left is already spoken for by 3 bills due"; when the bills take it all, "All of what's left is
     * spoken for: 3 bills due come to ₹2,100.00"; over the limit, "3 bills still due (₹2,100.00) will add to what is over".
     */
    fun line(r: Result, money: (Long) -> String): String = when {
        r.overBudget -> "${bills(r.count).replaceFirstChar { it.uppercase() }} still due (${money(r.totalMinor)}) will add to what is over"
        r.coversAll -> "All of what's left is spoken for: ${bills(r.count)} due come to ${money(r.totalMinor)}"
        else -> "${money(r.totalMinor)} of what's left is already spoken for by ${bills(r.count)} due"
    }
}
