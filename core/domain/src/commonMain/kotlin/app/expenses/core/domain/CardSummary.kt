package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * A credit card at a glance: what was charged since the latest statement, how much of the credit limit is used, and what is left to pay on that statement
 * with its minimum and due date. All amounts in minor units. [limit], [usedPercent] and [limitLeft] are null when the person never entered a limit.
 */
data class CardSummary(
    val standing: CardStanding,
    /** The first day after the latest statement was cut: the current cycle starts here. */
    val cycleStart: LocalDate,
    /** The statement after this one. */
    val nextStatement: LocalDate,
    /** New charges since the statement, before anything paid in: never negative. */
    val spentThisCycle: Long,
    /** What the card owes today, never negative. */
    val owed: Long,
    val limit: Long?,
    /** [owed] against [limit], whole percent, capped at 1000; over 100 means over the limit. */
    val usedPercent: Int?,
    /** What is left of the limit, never negative. */
    val limitLeft: Long?,
    /** Days from today to the due date of the latest statement: negative once it is past. */
    val daysToDue: Int,
) {
    /** How full the limit bar is, 0 to 1. */
    val limitFraction: Float? get() = limit?.let { (owed.toDouble() / it).coerceIn(0.0, 1.0).toFloat() }
}

object CardSummaries {
    /**
     * Works the summary out of figures the store reads: [owedAtStatement] and [owedNow] are what the card owed when the latest statement was cut and today (negative
     * when it is in credit), [standing] is the latest statement's position (it knows what was paid in since the cut), [limit] the credit limit, if any.
     *
     * Charges since the statement are what the owing grew by, plus what was paid in meanwhile: owedNow = owedAtStatement + charges - paid. So they include any
     * money moved out of the card (a cash advance), and a refund does not reduce them (it is counted as paid in).
     */
    fun of(standing: CardStanding, owedAtStatement: Long, owedNow: Long, limit: Long?, today: LocalDate): CardSummary {
        val charged = (owedNow - owedAtStatement + standing.paidSince).coerceAtLeast(0)
        val owed = owedNow.coerceAtLeast(0)
        val cap = limit?.takeIf { it > 0 }
        return CardSummary(
            standing = standing,
            cycleStart = standing.cycle.statementDate.plus(1, DateTimeUnit.DAY),
            nextStatement = standing.cycle.statementDate.plus(1, DateTimeUnit.MONTH),
            spentThisCycle = charged,
            owed = owed,
            limit = cap,
            usedPercent = cap?.let { (owed * 100 / it).coerceAtMost(1000L).toInt() },
            limitLeft = cap?.let { (it - owed).coerceAtLeast(0) },
            daysToDue = (standing.cycle.dueDate.toEpochDays() - today.toEpochDays()).toInt(),
        )
    }
}

/** The words for the card summary (the account dialog and the full tile). [money] formats minor units. */
object CardSummaryText {
    fun days(daysToDue: Int): String = when {
        daysToDue == 0 -> "today"
        daysToDue == 1 -> "tomorrow"
        daysToDue > 1 -> "in $daysToDue days"
        daysToDue == -1 -> "1 day overdue"
        else -> "${-daysToDue} days overdue"
    }

    /** "₹12,000.00 spent since the 5 Oct statement", or that nothing was. */
    fun cycle(s: CardSummary, money: (Long) -> String): String {
        val since = "since the ${CardText.day(s.standing.cycle.statementDate)} statement"
        return if (s.spentThisCycle == 0L) "Nothing spent $since" else "${money(s.spentThisCycle)} spent $since"
    }

    /** "₹45,000.00 of ₹1,00,000.00 used · 45% · ₹55,000.00 left", or null when no limit was entered. */
    fun limit(s: CardSummary, money: (Long) -> String): String? {
        val cap = s.limit ?: return null
        val percent = s.usedPercent ?: return null
        val tail = when {
            s.owed > cap -> "over the limit by ${money(s.owed - cap)}"
            s.owed == cap -> "at the limit"
            else -> "${money(s.limitLeft ?: 0)} left"
        }
        return "${money(s.owed)} of ${money(cap)} used · $percent% · $tail"
    }

    /** What is left to pay on the latest statement, its minimum and the due date with the days left; or that it is paid or nothing was owed. */
    fun due(s: CardSummary, money: (Long) -> String): String {
        val st = s.standing
        val on = CardText.day(st.cycle.statementDate)
        return when {
            st.statementBalance == 0L -> "Nothing was owed on the $on statement"
            st.remaining == 0L -> "The $on statement is paid"
            else -> buildList {
                add("${money(st.remaining)} left to pay")
                if (st.minimumDue > 0) add("minimum about ${money(st.minimumDue)}")
                add((if (s.daysToDue < 0) "was due " else "due ") + CardText.day(st.cycle.dueDate) + " (" + days(s.daysToDue) + ")")
            }.joinToString(" · ")
        }
    }
}
