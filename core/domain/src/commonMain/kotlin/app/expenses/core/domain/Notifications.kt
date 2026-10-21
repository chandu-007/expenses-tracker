package app.expenses.core.domain

import kotlinx.datetime.LocalDate

enum class NoticeKind { BILL_DUE, CARD_DUE, BUDGET_NEAR, BUDGET_OVER, WEEKLY_RECAP, BILL_FOUND, BILL_TRACKED, BUDGET_PACE, BUDGET_WRAPUP, CARD_STATEMENT, CARD_LIMIT, CARD_CLEARED }

/**
 * What the user chose about notifications. Bill reminders and budget alerts are separate switches; nothing else is ever sent.
 * Quiet hours run from [quietStartMinute] to [quietEndMinute] (minutes after midnight) and may wrap past midnight (22:00 to 07:00).
 * Bill reminders are sent from [billHour] on the day a bill is due, never earlier.
 */
data class NotificationPrefs(
    val bills: Boolean = true,
    val budgets: Boolean = true,
    val quietEnabled: Boolean = true,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val billHour: Int = 9,
    /** The weekly summary of spending. Off until the person turns it on. */
    val recap: Boolean = false,
    /** A budget is called "near its limit" from this percent of it (50 to 95). */
    val budgetNearPercent: Int = 80,
    /** Say so when spending runs well ahead of the month, before the limit is near. */
    val budgetPace: Boolean = true,
    /** At the start of a month, say how last month's overall budget went when it was kept. */
    val budgetWrapUp: Boolean = true,
    /** Remind about a tracked bill three days ahead, and one day ahead (the day itself and overdue are always reminded). */
    val billAhead3: Boolean = true,
    val billAhead1: Boolean = true,
    /** Say when a card's statement has been made, with what is owed. */
    val cardStatement: Boolean = true,
    /** Say when a card's balance passes 80% and 100% of the credit limit the person entered. */
    val cardLimit: Boolean = true,
) {
    init {
        require(budgetNearPercent in 50..95) { "The near-limit warning must be 50 to 95 percent" }
        require(quietStartMinute in 0 until MINUTES_PER_DAY && quietEndMinute in 0 until MINUTES_PER_DAY) { "Quiet hours must be within a day" }
        require(billHour in 0..23) { "Reminder hour must be 0 to 23" }
    }

    /** True when notifications must be held back at [minuteOfDay]. Start and end equal means no quiet period at all. */
    fun isQuiet(minuteOfDay: Int): Boolean {
        if (!quietEnabled || quietStartMinute == quietEndMinute) return false
        return if (quietStartMinute < quietEndMinute) minuteOfDay in quietStartMinute until quietEndMinute
        else minuteOfDay >= quietStartMinute || minuteOfDay < quietEndMinute
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** One notification to show. [key] identifies the event so it is shown once: the same key is never planned again after it is recorded as sent. */
data class Notice(val key: String, val kind: NoticeKind, val title: String, val text: String, /** What the notice is about, for its buttons: a tracked bill's id, or a card's account id. */ val ref: String? = null)

/** A bill waiting to be posted, as far as notifications care. */
data class BillDue(val itemId: String, val name: String, val amountText: String?, val date: LocalDate)

/** A bill just found in a message and not yet added. [dueText] is how its date reads ("due 25 Oct"), or empty. */
data class FoundBill(val id: String, val title: String, val amountText: String?, val dueText: String)

/** A bill the person is tracking. [daysLeft] is negative once it is overdue. */
data class TrackedBill(val id: String, val title: String, val amountText: String?, val dueDate: LocalDate)

/** An overall or category budget at its current state, with display text prepared by the caller. */
data class BudgetStanding(val scope: String?, val name: String, val state: BudgetState, val remainingText: String, val periodStart: LocalDate)

/** Spending that is running ahead of the month while the budget is still below its warning level. */
data class BudgetPace(val scope: String?, val name: String, val usedPercent: Int, val monthPercent: Int, val remainingText: String, val periodStart: LocalDate)

/** Last month's overall budget was kept: [text] says by how much. [periodStart] is the start of the month it is about. */
data class BudgetWrapUp(val periodStart: LocalDate, val text: String)

/** A card whose statement was just made. */
data class CardStatementMade(val accountId: String, val name: String, val statementDate: LocalDate, val dueDate: LocalDate, val balanceText: String, val minimumText: String)

/** A card whose balance has reached [level] (80 or 100) percent of its credit limit, in the month [monthKey] (year * 12 + month). */
data class CardLimitUse(val accountId: String, val name: String, val level: Int, val usedPercent: Int, val usedText: String, val limitText: String, val monthKey: Int)

/** A card whose statement, which it was reminded about, has been paid in full. */
data class CardCleared(val accountId: String, val name: String, val dueDate: LocalDate)

object NoticePlanner {
    /**
     * Which notices to send now. Nothing is planned during quiet hours or for a switched-off type; because planning does not record
     * anything, held-back notices are planned again at the next check and go out once quiet hours end.
     *
     * Bills: one notice per waiting occurrence due today or earlier, from [NotificationPrefs.billHour] on the due day. Older overdue
     * occurrences are included, so a missed bill still reminds once.
     * Budgets: a budget crossing into "near" or "over" notifies once per period and level. Going straight to "over" sends only
     * "over", and an "over" notice makes a "near" one for the same budget and period pointless, so it is not sent afterwards.
     */
    fun plan(
        prefs: NotificationPrefs,
        today: LocalDate,
        minuteOfDay: Int,
        bills: List<BillDue>,
        budgets: List<BudgetStanding>,
        alreadySent: Set<String>,
        cards: List<CardDue> = emptyList(),
        recap: RecapDue? = null,
        foundBills: List<FoundBill> = emptyList(),
        trackedBills: List<TrackedBill> = emptyList(),
        pace: List<BudgetPace> = emptyList(),
        wrapUp: BudgetWrapUp? = null,
        statements: List<CardStatementMade> = emptyList(),
        limits: List<CardLimitUse> = emptyList(),
        cleared: List<CardCleared> = emptyList(),
    ): List<Notice> {
        if (prefs.isQuiet(minuteOfDay)) return emptyList()
        val out = mutableListOf<Notice>()
        if (prefs.bills) {
            // a bill found in a message is told about at once (not from the reminder hour), once, unless it is quiet hours: then at the next check
            for (b in foundBills) {
                val key = foundKey(b.id)
                if (key in alreadySent) continue
                out += Notice(key, NoticeKind.BILL_FOUND, "Bill found: ${b.title}", listOfNotNull(b.amountText, b.dueText.takeIf { it.isNotEmpty() }, "Tap to add it").joinToString(" · "))
            }
        }
        if (prefs.bills && minuteOfDay >= prefs.billHour * 60) {
            // a tracked bill: a heads-up two to three days ahead and one the day before (each can be switched off), one on the day, then one a day while it is
            // overdue (the first OVERDUE_NAG_DAYS days), each once
            for (b in trackedBills.sortedBy { it.dueDate }) {
                val daysLeft = (b.dueDate.toEpochDays() - today.toEpochDays()).toInt()
                val stage = when {
                    daysLeft < 0 -> if (-daysLeft <= OVERDUE_NAG_DAYS) "overdue${-daysLeft}" else continue
                    daysLeft == 0 -> "today"
                    daysLeft == 1 -> if (prefs.billAhead1) "d1" else continue
                    daysLeft <= TRACKED_AHEAD_DAYS -> if (prefs.billAhead3) "ahead" else continue
                    else -> continue
                }
                val key = trackedKey(b.id, stage)
                if (key in alreadySent) continue
                val title = when {
                    daysLeft < 0 -> "${b.title} is overdue" + if (daysLeft < -1) " by ${-daysLeft} days" else ""
                    daysLeft == 0 -> "${b.title} is due today"
                    else -> "${b.title} due ${if (daysLeft == 1) "tomorrow" else "in $daysLeft days"}"
                }
                out += Notice(key, NoticeKind.BILL_TRACKED, title, listOfNotNull(b.amountText, "Mark it paid when it is done").joinToString(" · "), ref = b.id)
            }
        }
        if (prefs.bills && minuteOfDay >= prefs.billHour * 60) {
            for (b in bills.filter { it.date <= today }.sortedBy { it.date }) {
                val key = billKey(b.itemId, b.date)
                if (key in alreadySent) continue
                val when_ = if (b.date == today) "due today" else "overdue"
                out += Notice(key, NoticeKind.BILL_DUE, "${b.name} $when_", listOfNotNull(b.amountText, "Open the app to post or skip it").joinToString(" · "))
            }
        }
        if (prefs.bills && minuteOfDay >= prefs.billHour * 60) {
            // A card gets a heads-up from [CardDue.remindDaysBefore] days ahead (once) and a notice on the due day or after it (once per due date).
            for (c in cards.sortedBy { it.dueDate }) {
                val daysLeft = (c.dueDate.toEpochDays() - today.toEpochDays()).toInt()
                val key = cardKey(c.accountId, c.dueDate, daysLeft <= 0)
                if (key in alreadySent) continue
                val message = listOf("Pay ${c.remainingText}", "minimum about ${c.minimumText}").joinToString(", ")
                when {
                    daysLeft <= 0 -> out += Notice(key, NoticeKind.CARD_DUE, "${c.name} bill ${if (daysLeft == 0) "is due today" else "is overdue"}", message, ref = c.accountId)
                    daysLeft <= c.remindDaysBefore -> out += Notice(key, NoticeKind.CARD_DUE, "${c.name} bill due ${if (daysLeft == 1) "tomorrow" else "in $daysLeft days"}", message, ref = c.accountId)
                }
            }
        }
        if (prefs.bills && minuteOfDay >= prefs.billHour * 60) {
            // a card's statement was just made: what is owed, the minimum and the due date, once per statement
            if (prefs.cardStatement) for (c in statements) {
                val key = cardStatementKey(c.accountId, c.statementDate)
                if (key in alreadySent) continue
                out += Notice(key, NoticeKind.CARD_STATEMENT, "${c.name} statement is ready", "${c.balanceText} owed, minimum ${c.minimumText}, due ${c.dueDate.dayOfMonth} ${MONTHS[c.dueDate.monthNumber - 1]}", ref = c.accountId)
            }
            // a statement that was reminded about and is now paid in full
            for (c in cleared) {
                val key = cardClearedKey(c.accountId, c.dueDate)
                if (key in alreadySent) continue
                out += Notice(key, NoticeKind.CARD_CLEARED, "${c.name} bill is paid", "Nothing is left to pay on this statement.", ref = c.accountId)
            }
        }
        if (prefs.bills && prefs.cardLimit) {
            // a card's balance against the credit limit: once per month and level, and 100% makes the 80% one pointless
            for (c in limits) {
                val key = cardLimitKey(c.accountId, c.level, c.monthKey)
                if (key in alreadySent) continue
                if (c.level == 80 && cardLimitKey(c.accountId, 100, c.monthKey) in alreadySent) continue
                out += Notice(key, NoticeKind.CARD_LIMIT, if (c.level >= 100) "${c.name} is at its credit limit" else "${c.name} is ${c.usedPercent}% of its limit", "${c.usedText} of ${c.limitText} used", ref = c.accountId)
            }
        }
        if (prefs.recap && recap != null && minuteOfDay >= prefs.billHour * 60) {
            // Once for each finished week, from the hour set for reminders on any day of the week after it.
            val key = WeeklyRecap.key(recap.weekStart)
            if (key !in alreadySent) out += Notice(key, NoticeKind.WEEKLY_RECAP, recap.title, recap.text)
        }
        if (prefs.budgets && prefs.budgetPace) {
            // spending is well ahead of the month while the budget is not yet near its limit: once per budget and period, and never once near or over has been said
            for (p in pace) {
                val key = paceKey(p.scope, p.periodStart)
                if (key in alreadySent || budgetKey(p.scope, p.periodStart, BudgetState.NEAR) in alreadySent || budgetKey(p.scope, p.periodStart, BudgetState.OVER) in alreadySent) continue
                out += Notice(key, NoticeKind.BUDGET_PACE, "${p.name}: spending is ahead of the month", "${p.usedPercent}% used with ${p.monthPercent}% of the month gone. ${p.remainingText}")
            }
        }
        if (prefs.budgets && prefs.budgetWrapUp && wrapUp != null && minuteOfDay >= prefs.billHour * 60) {
            val key = wrapUpKey(wrapUp.periodStart)
            if (key !in alreadySent) out += Notice(key, NoticeKind.BUDGET_WRAPUP, "You kept last month's budget", wrapUp.text)
        }
        if (prefs.budgets) {
            for (s in budgets) {
                val overKey = budgetKey(s.scope, s.periodStart, BudgetState.OVER)
                val nearKey = budgetKey(s.scope, s.periodStart, BudgetState.NEAR)
                when (s.state) {
                    BudgetState.OVER -> if (overKey !in alreadySent) out += Notice(overKey, NoticeKind.BUDGET_OVER, "Over budget: ${s.name}", s.remainingText)
                    BudgetState.NEAR -> if (nearKey !in alreadySent && overKey !in alreadySent) out += Notice(nearKey, NoticeKind.BUDGET_NEAR, "${s.name} is near its limit", s.remainingText)
                    BudgetState.OK -> {}
                }
            }
        }
        return out
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** How many days in a row an overdue tracked bill is reminded about, after which it is left alone. */
    const val OVERDUE_NAG_DAYS = 7

    fun paceKey(scope: String?, periodStart: LocalDate) = "budgetpace:${scope ?: "*"}:${periodStart.toEpochDays()}"

    fun wrapUpKey(periodStart: LocalDate) = "budgetend:${periodStart.toEpochDays()}"

    fun cardStatementKey(accountId: String, statementDate: LocalDate) = "cardstmt:$accountId:${statementDate.toEpochDays()}"

    fun cardClearedKey(accountId: String, due: LocalDate) = "cardclear:$accountId:${due.toEpochDays()}"

    fun cardLimitKey(accountId: String, level: Int, monthKey: Int) = "cardlimit:$accountId:$level:$monthKey"

    fun cardKey(accountId: String, due: LocalDate, onOrAfterDue: Boolean) = "card:$accountId:${due.toEpochDays()}:${if (onOrAfterDue) "due" else "ahead"}"

    /** How many days ahead a tracked bill gets its first heads-up. */
    const val TRACKED_AHEAD_DAYS = 3

    fun foundKey(id: String) = "billfound:$id"

    fun trackedKey(id: String, stage: String) = "billtrack:$id:$stage"

    fun billKey(itemId: String, date: LocalDate) = "bill:$itemId:${date.toEpochDays()}"

    fun budgetKey(scope: String?, periodStart: LocalDate, level: BudgetState) = "budget:${scope ?: "*"}:${periodStart.toEpochDays()}:${level.name}"
}
