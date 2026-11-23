package app.expenses.core.domain

import kotlinx.datetime.LocalDate

/**
 * N-18, "already spoken for": how much of what is left under the budget the bills and repeating expenses still due before the period ends will take. The safe-to-spend
 * figures already know these bills; this puts them in one line beside "left" ("₹2,100 of what's left is already spoken for by 3 bills due") and, on a tap, lists them.
 * Pure: the caller hands in the bills (tracked bills with a due date, unposted repeating expenses) and what is left.
 */
object SpokenFor {
    /**
     * One bill or repeating expense. [amountMinor] is null for one whose amount is not known yet (a variable repeating item, a bill read from a text with no figure): it is counted and
     * listed but adds nothing to the total. [paid] ones are never counted (a tracked bill marked paid, an occurrence already posted or skipped).
     */
    data class Due(val name: String, val amountMinor: Long?, val due: LocalDate, val paid: Boolean = false)

    /**
     * What is spoken for: [items] still due before the period ends (and overdue ones from before it, which will still be paid from what is left), in date order, adding up to
     * [totalMinor]; [noAmount] are due too but have no amount yet (not in the total); [leftMinor] is what is left under the limit (negative when over). [coversAll] is true when
     * the bills with an amount take all that is left or more.
     */
    data class Result(val items: List<Due>, val noAmount: List<Due>, val totalMinor: Long, val leftMinor: Long) {
        val count: Int get() = items.size
        val overBudget: Boolean get() = leftMinor < 0
        val coversAll: Boolean get() = items.isNotEmpty() && !overBudget && totalMinor >= leftMinor
        /** Every bill the line speaks of, with and without an amount, in date order (what a tap lists). */
        val listed: List<Due> get() = (items + noAmount).sortedWith(compareBy({ it.due }, { it.name.lowercase() }))
    }

    /**
     * The unpaid bills of [dues] due before [periodEndExclusive], against [leftMinor]; null when there are none, so no line is drawn. There is no lower bound: a bill that fell due
     * before the period began and is still unpaid will be paid from what is left, so it counts (the caller decides how far back the repeating items it hands in reach).
     */
    fun compute(leftMinor: Long, dues: List<Due>, periodEndExclusive: LocalDate): Result? {
        val open = dues.filter { !it.paid && it.due < periodEndExclusive && (it.amountMinor == null || it.amountMinor > 0) }
        val order = compareBy<Due>({ it.due }, { it.name.lowercase() })
        val items = open.filter { it.amountMinor != null }.sortedWith(order)
        val noAmount = open.filter { it.amountMinor == null }.sortedWith(order)
        if (items.isEmpty() && noAmount.isEmpty()) return null
        var total = 0L
        for (d in items) total = Math2.addExact(total, d.amountMinor!!)
        return Result(items, noAmount, total, leftMinor)
    }

    /** "3 bills" / "1 bill". */
    fun bills(count: Int): String = if (count == 1) "1 bill" else "$count bills"

    /**
     * The line, with amounts made by [money]: "₹2,100.00 of what's left is already spoken for by 3 bills due"; when the bills take it all, "All of what's left is
     * spoken for: 3 bills due come to ₹2,100.00"; over the limit, "3 bills still due (₹2,100.00) will add to what is over". Bills with no amount yet are said after it
     * (", plus 1 with no amount yet"); with only such bills, "1 bill due has no amount yet". [cardBillsNote] adds " (card bills not included)" when the Home banner beside the line
     * counts a card's bill (this line leaves card bills out on purpose: paying a card is a transfer, not spending).
     */
    fun line(r: Result, money: (Long) -> String, cardBillsNote: Boolean = false): String {
        val unknown = r.noAmount.size
        val body = when {
            r.items.isEmpty() -> "${bills(unknown)} due ${if (unknown == 1) "has" else "have"} no amount yet"
            r.overBudget -> "${bills(r.count).replaceFirstChar { it.uppercase() }} still due (${money(r.totalMinor)}) will add to what is over"
            r.coversAll -> "All of what's left is spoken for: ${bills(r.count)} due ${if (r.count == 1) "comes" else "come"} to ${money(r.totalMinor)}"
            else -> "${money(r.totalMinor)} of what's left is already spoken for by ${bills(r.count)} due"
        }
        val plus = if (r.items.isNotEmpty() && unknown > 0) ", plus $unknown with no amount yet" else ""
        return body + plus + if (cardBillsNote) " (card bills not included)" else ""
    }
}
