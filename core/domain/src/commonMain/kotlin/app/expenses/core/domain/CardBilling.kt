package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * How a credit card bills: the day of the month the statement is cut ([statementDay]) and the day the payment is due ([dueDay]),
 * both 1 to 28 so every month has them. [minDuePercent] is the share of the statement balance the bank asks as a minimum (most Indian
 * cards: 5), only used to estimate the minimum; the statement itself has the exact figure. [remindDaysBefore] is how many days ahead of
 * the due date the first reminder comes (0 = only on the day).
 */
data class CardTerms(val accountId: String, val statementDay: Int, val dueDay: Int, val minDuePercent: Int = 5, val remindDaysBefore: Int = 3) {
    init {
        require(statementDay in 1..28) { "The statement day must be 1 to 28." }
        require(dueDay in 1..28) { "The due day must be 1 to 28." }
        require(minDuePercent in 0..100) { "The minimum due percentage must be 0 to 100." }
        require(remindDaysBefore in 0..10) { "Reminders start 0 to 10 days before the due date." }
    }
}

/** One billing cycle: the statement was cut on [statementDate] and is due on [dueDate]. */
data class BillingCycle(val statementDate: LocalDate, val dueDate: LocalDate)

/** What is still to pay on a statement. All amounts in minor units, never negative. */
data class CardStanding(val cycle: BillingCycle, val statementBalance: Long, val paidSince: Long, val remaining: Long, val minimumDue: Long)

object CardBilling {
    /** The most recent statement date on or before [today]. */
    fun statementOnOrBefore(today: LocalDate, statementDay: Int): LocalDate {
        val thisMonth = LocalDate(today.year, today.month, statementDay)
        return if (thisMonth <= today) thisMonth else thisMonth.plus(-1, DateTimeUnit.MONTH)
    }

    /** The first date after the statement date that falls on [dueDay]: same month when the due day is later in the month, else the next. */
    fun dueDateFor(statement: LocalDate, dueDay: Int): LocalDate {
        val sameMonth = LocalDate(statement.year, statement.month, dueDay)
        return if (sameMonth > statement) sameMonth else sameMonth.plus(1, DateTimeUnit.MONTH)
    }

    fun cycleOn(today: LocalDate, terms: CardTerms): BillingCycle {
        val statement = statementOnOrBefore(today, terms.statementDay)
        return BillingCycle(statement, dueDateFor(statement, terms.dueDay))
    }

    /**
     * What remains on the statement of [cycle]: the balance owed when it was cut, less what has been paid in since (payments, refunds and
     * transfers into the card), never below zero. The minimum due is estimated from the percentage and never exceeds what remains.
     */
    fun standing(cycle: BillingCycle, owedAtStatement: Long, paidSince: Long, terms: CardTerms): CardStanding {
        val statement = owedAtStatement.coerceAtLeast(0)
        val remaining = (statement - paidSince.coerceAtLeast(0)).coerceAtLeast(0)
        val estimate = (statement * terms.minDuePercent + 99) / 100
        return CardStanding(cycle, statement, paidSince.coerceAtLeast(0), remaining, minOf(estimate, remaining))
    }
}

/** The words for a card on the accounts screen. */
object CardText {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun day(d: LocalDate) = "${d.dayOfMonth} ${MONTHS[d.monthNumber - 1]}"

    fun ordinal(n: Int) = "$n" + if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

    fun terms(t: CardTerms) = "Statement on the ${ordinal(t.statementDay)} · due on the ${ordinal(t.dueDay)}"

    /** Where the latest statement stands: what is left, by when, and the estimated minimum, or that nothing is owed or it is paid. */
    fun status(s: CardStanding, today: LocalDate, money: (Long) -> String): String = when {
        s.statementBalance == 0L -> "Nothing was owed on the ${day(s.cycle.statementDate)} statement"
        s.remaining == 0L -> "The ${day(s.cycle.statementDate)} statement is paid"
        else -> "${if (today > s.cycle.dueDate) "Overdue since" else "Due"} ${day(s.cycle.dueDate)} · ${money(s.remaining)} left · minimum about ${money(s.minimumDue)}"
    }
}

/** A card with something left to pay, ready for the planner (display text prepared by the caller). */
data class CardDue(val accountId: String, val name: String, val dueDate: LocalDate, val remainingText: String, val minimumText: String, val remindDaysBefore: Int)
