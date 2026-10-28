package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** One row of the breakdown as it is shown: [amountText] carries its sign (a minus for what is taken off). */
data class SafeToSpendRow(val part: SafeToSpendPart, val label: String, val amountText: String, val detail: String)

/** The words for the balance-based safe to spend. [money] formats minor units, [day] a date as "30 Nov". */
object SafeToSpendText {
    const val EXPLAIN = "An estimate, not a promise. It starts from what your cash, bank and wallet accounts hold, takes off what your credit cards owe and the bills, EMIs and " +
        "repeating expenses due before the end of the period, and knows nothing else: income that is still to come is not added, and anything not entered here is not counted."

    private fun lastDay(r: BalanceSafeToSpend): LocalDate = r.horizonEnd.minus(1, DateTimeUnit.DAY)

    /** "until 30 Nov": the horizon, the last day the figure looks at. */
    fun until(r: BalanceSafeToSpend, day: (LocalDate) -> String): String = "until ${day(lastDay(r))}"

    /** What the horizon is, in words: the end of the budget period, or thirty days. */
    fun horizonWhy(r: BalanceSafeToSpend): String = when (r.horizon) {
        Horizon.BUDGET_PERIOD -> "the end of your budget period"
        Horizon.THIRTY_DAYS -> "the next ${SpendableMath.DEFAULT_HORIZON_DAYS} days (no overall budget is set)"
    }

    /** "₹12,000.00 safe to spend until 30 Nov", or "Nothing spare until 30 Nov". */
    fun headline(r: BalanceSafeToSpend, money: (Long) -> String, day: (LocalDate) -> String): String =
        if (r.nothingSpare) "Nothing spare ${until(r, day)}" else "${money(r.spendable)} safe to spend ${until(r, day)}"

    /** "about ₹400.00 a day", or null when nothing is spare. */
    fun perDay(r: BalanceSafeToSpend, money: (Long) -> String): String? = if (r.nothingSpare || r.perDay <= 0) null else "about ${money(r.perDay)} a day"

    /** "Short by ₹1,200.00: what is due is more than what the accounts hold", or null. */
    fun shortfall(r: BalanceSafeToSpend, money: (Long) -> String): String? =
        if (r.shortfall > 0) "Short by ${money(r.shortfall)}: what is due is more than what your accounts hold" else null

    fun rows(r: BalanceSafeToSpend, money: (Long) -> String, day: (LocalDate) -> String): List<SafeToSpendRow> = r.lines.map { l ->
        when (l.part) {
            SafeToSpendPart.BALANCE -> SafeToSpendRow(l.part, "In your accounts", money(l.minor), if (l.count == 1) "1 cash, bank or wallet account" else "${l.count} cash, bank and wallet accounts")
            SafeToSpendPart.CARDS -> SafeToSpendRow(l.part, "Credit card bills", "−" + money(l.minor), (if (l.count == 1) "1 card" else "${l.count} cards") + ": the statement still to pay and what was spent since")
            SafeToSpendPart.BILLS -> SafeToSpendRow(l.part, "Bills due", "−" + money(l.minor), (if (l.count == 1) "1 tracked bill" else "${l.count} tracked bills") + " due ${until(r, day)}")
            SafeToSpendPart.EMIS -> SafeToSpendRow(l.part, "EMIs due", "−" + money(l.minor), (if (l.count == 1) "1 EMI" else "${l.count} EMIs") + " due ${until(r, day)}")
            SafeToSpendPart.REPEATING -> SafeToSpendRow(l.part, "Repeating expenses due", "−" + money(l.minor), (if (l.count == 1) "1 payment" else "${l.count} payments") + " waiting or coming ${until(r, day)}")
            SafeToSpendPart.PUT_ASIDE -> SafeToSpendRow(l.part, "Put aside for yearly bills", "−" + money(l.minor), if (l.count == 1) "1 yearly bill, spread over the months to its date" else "${l.count} yearly bills, spread over the months to their dates")
        }
    }

    /** Said when yearly bills exist but putting money aside is off: what it would come to, and that it is not taken off. */
    fun setAsideHint(r: BalanceSafeToSpend, money: (Long) -> String): String? =
        if (r.putAsideCounted || r.putAsideIfOn <= 0) null else "Yearly bills would need ${money(r.putAsideIfOn)} a month. Not taken off here; the switch is on the Repeating expenses page."

    /** Bills with no date, and repeating items with no fixed amount, cannot be placed: say so rather than silently leaving them out. */
    fun notCounted(r: BalanceSafeToSpend): String? =
        if (r.notCounted <= 0) null else (if (r.notCounted == 1) "1 bill or repeating expense has no due date or amount and is not counted." else "${r.notCounted} bills or repeating expenses have no due date or amount and are not counted.")
}
