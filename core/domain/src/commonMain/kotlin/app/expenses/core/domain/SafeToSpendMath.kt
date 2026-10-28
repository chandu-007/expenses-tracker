package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlin.math.abs

/** What kind of payment is still to leave the accounts. */
enum class ObligationKind { BILL, EMI, REPEATING }

/** One payment still to be made: a tracked bill, an EMI or a repeating expense (one occurrence of it). [due] may be in the past (overdue, still owed). */
data class Obligation(val title: String, val amountMinor: Long, val due: LocalDate, val kind: ObligationKind) {
    init { require(amountMinor > 0) { "An obligation is a positive amount" } }
}

/** A yearly (or quarterly, half-yearly) bill that falls due after the horizon, and what putting it aside month by month comes to. */
data class SetAside(val name: String, val perMonthMinor: Long)

/** Where the look-ahead ends: the end of the budget period (when an overall limit is set), otherwise 30 days from today. */
enum class Horizon { BUDGET_PERIOD, THIRTY_DAYS }

/**
 * What the figure is worked out from. All amounts in one currency's minor units. [assetsMinor] is what cash, bank and wallet accounts hold (not credit cards, loans or
 * archived accounts), [cardOwedMinor] what the credit cards owe now (never below zero for one card). [horizonEnd] is exclusive.
 */
data class SafeToSpendInput(
    val today: LocalDate,
    val horizonEnd: LocalDate,
    val horizon: Horizon,
    val assetsMinor: Long,
    val assetAccounts: Int,
    val cardOwedMinor: Long,
    val cardsOwing: Int,
    val obligations: List<Obligation>,
    /** Yearly bills falling due after the horizon. Taken off only when [putAsideOn]; shown either way. */
    val setAside: List<SetAside> = emptyList(),
    val putAsideOn: Boolean = false,
)

enum class SafeToSpendPart { BALANCE, CARDS, BILLS, EMIS, REPEATING, PUT_ASIDE }

/** One row of the breakdown. [minor] is positive; every part except [SafeToSpendPart.BALANCE] is taken off. [count] is how many things it is made of. */
data class SafeToSpendLine(val part: SafeToSpendPart, val minor: Long, val count: Int)

/**
 * The answer. [spendable] is never negative ([nothingSpare] says so); [shortfall] is how far below zero the sum fell. [lines] are only the parts that are not empty.
 * [putAsideIfOn] is what yearly bills would need a month: not taken off while [putAsideCounted] is false.
 */
data class BalanceSafeToSpend(
    val spendable: Long,
    val shortfall: Long,
    val lines: List<SafeToSpendLine>,
    val daysLeft: Int,
    val perDay: Long,
    val horizon: Horizon,
    val horizonEnd: LocalDate,
    val putAsideIfOn: Long,
    val putAsideCounted: Boolean,
    /** Tracked bills with no due date and repeating items with no fixed amount: they cannot be placed or priced and are not counted. */
    val notCounted: Int = 0,
) {
    val nothingSpare: Boolean get() = spendable == 0L
    fun total(part: SafeToSpendPart): Long = lines.firstOrNull { it.part == part }?.minor ?: 0L
}

/**
 * "How much can I spend": the balance of the accounts that hold money, less what the cards owe, less what is still to be paid before the horizon.
 * An estimate, not a promise: it knows only what the app knows (entries, tracked bills, repeating items). Income still to come is not added.
 */
object SpendableMath {
    /** A tracked bill and a repeating item for the same payment (same amount, due within this many days of each other) are one payment, counted once. */
    const val SAME_PAYMENT_DAYS = 3
    const val DEFAULT_HORIZON_DAYS = 30

    fun compute(input: SafeToSpendInput, notCounted: Int = 0): BalanceSafeToSpend {
        val due = input.obligations.filter { it.due < input.horizonEnd }
        val repeating = due.filter { it.kind == ObligationKind.REPEATING }
        // a bill that a repeating item already stands for is not taken off twice
        val others = due.filter { o ->
            o.kind == ObligationKind.REPEATING || repeating.none { r -> r.amountMinor == o.amountMinor && abs(r.due.toEpochDays() - o.due.toEpochDays()) <= SAME_PAYMENT_DAYS }
        }
        val bills = others.filter { it.kind == ObligationKind.BILL }
        val emis = others.filter { it.kind == ObligationKind.EMI }
        val putAside = input.setAside.sumOf { it.perMonthMinor }
        val counted = if (input.putAsideOn) putAside else 0L
        val lines = buildList {
            add(SafeToSpendLine(SafeToSpendPart.BALANCE, input.assetsMinor, input.assetAccounts))
            if (input.cardOwedMinor > 0) add(SafeToSpendLine(SafeToSpendPart.CARDS, input.cardOwedMinor, input.cardsOwing))
            if (bills.isNotEmpty()) add(SafeToSpendLine(SafeToSpendPart.BILLS, bills.sumOf { it.amountMinor }, bills.size))
            if (emis.isNotEmpty()) add(SafeToSpendLine(SafeToSpendPart.EMIS, emis.sumOf { it.amountMinor }, emis.size))
            if (repeating.isNotEmpty()) add(SafeToSpendLine(SafeToSpendPart.REPEATING, repeating.sumOf { it.amountMinor }, repeating.size))
            if (input.putAsideOn && counted > 0) add(SafeToSpendLine(SafeToSpendPart.PUT_ASIDE, counted, input.setAside.size))
        }
        val raw = input.assetsMinor - input.cardOwedMinor - bills.sumOf { it.amountMinor } - emis.sumOf { it.amountMinor } - repeating.sumOf { it.amountMinor } - counted
        val daysLeft = maxOf(1, input.today.daysUntil(input.horizonEnd))
        val spendable = raw.coerceAtLeast(0L)
        return BalanceSafeToSpend(spendable, (-raw).coerceAtLeast(0L), lines, daysLeft, spendable / daysLeft, input.horizon, input.horizonEnd, putAside, input.putAsideOn, notCounted)
    }
}

/**
 * Putting a yearly bill aside month by month. A repeating item that comes round every three months or more ([MIN_CYCLE_MONTHS]: quarterly, half-yearly, yearly) is
 * spread over the months until its next date, rounding the monthly amount up so the money is there on the day.
 */
object SinkingFunds {
    const val MIN_CYCLE_MONTHS = 3

    /** How many months one round of the schedule takes, or null when it repeats more often than that (days, weeks, every month or two). */
    fun cycleMonths(schedule: Schedule): Int? = when (schedule.unit) {
        RepeatUnit.MONTH -> schedule.every
        RepeatUnit.YEAR -> schedule.every * 12
        else -> null
    }?.takeIf { it >= MIN_CYCLE_MONTHS }

    /** Whole months from [today] to [due], rounded up, at least 1: a bill due in 10 days is put aside this month. */
    fun monthsUntil(today: LocalDate, due: LocalDate): Int {
        if (due <= today) return 1
        val whole = today.monthsUntil(due)
        return maxOf(1, if (today.plus(whole, DateTimeUnit.MONTH) < due) whole + 1 else whole)
    }

    /** `amountMinor` divided over the months until [due], rounded up. */
    fun perMonth(amountMinor: Long, today: LocalDate, due: LocalDate): Long {
        require(amountMinor > 0) { "amount must be positive" }
        val months = monthsUntil(today, due).toLong()
        return (amountMinor + months - 1) / months
    }

    /** The monthly amount to put aside for a slow item, or null when it is not slow, has no fixed amount or has ended. [today] counts as before the next date. */
    fun forItem(amountMinor: Long?, schedule: Schedule, today: LocalDate): SetAsideFor? {
        if (amountMinor == null || amountMinor <= 0 || cycleMonths(schedule) == null) return null
        val next = schedule.nextOnOrAfter(today)?.date ?: return null
        return SetAsideFor(perMonth(amountMinor, today, next), next)
    }

    /** "set aside ₹2,000.00 a month" for the repeating list. */
    fun text(perMonthMinor: Long, money: (Long) -> String): String = "set aside ${money(perMonthMinor)} a month"
}

/** [perMonthMinor] to put aside until [due]. */
data class SetAsideFor(val perMonthMinor: Long, val due: LocalDate)

/** How many payments of a schedule that ends are still to come. */
data class PaymentsLeft(val count: Int, val last: LocalDate)

/**
 * The EMI view of a repeating item that has an end (after a number of payments, or on a date): how many payments are left, when the last is, and the payments
 * times the amount. There is no interest or principal split: this only counts payments, it knows nothing about how the loan was priced.
 */
object PaymentsLeftMath {
    /**
     * Payments of [schedule] on or after [from] that were not dealt with ([handled] are the days, as epoch days, that were posted or skipped), up to the last one.
     * Null when the schedule never ends. A payment still waiting and overdue counts, so [from] should reach back as far as overdue ones are still offered.
     */
    fun of(schedule: Schedule, from: LocalDate, handled: Set<Long>): PaymentsLeft? {
        val last = schedule.lastDate() ?: return null
        if (from > last) return PaymentsLeft(0, last)
        val left = schedule.occurrences(from, last.plus(1, DateTimeUnit.DAY)).count { it.date.toEpochDays() !in handled }
        return PaymentsLeft(left, last)
    }

    /** "3 payments left · last on 5 Mar 2028 · ₹30,000.00 still to pay" ("still to pay" only with a fixed amount); "All payments made" once none is left. */
    fun text(left: PaymentsLeft, amountMinor: Long?, money: (Long) -> String, day: (LocalDate) -> String): String =
        if (left.count == 0) "All payments made"
        else listOfNotNull(
            if (left.count == 1) "1 payment left" else "${left.count} payments left",
            "last on ${day(left.last)}",
            amountMinor?.takeIf { it > 0 }?.let { "${money(it * left.count)} still to pay" },
        ).joinToString(" · ")
}
