package app.expenses.core.domain

import kotlinx.datetime.LocalDate

enum class NoticeKind { BILL_DUE, CARD_DUE, BUDGET_NEAR, BUDGET_OVER, WEEKLY_RECAP }

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
) {
    init {
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
data class Notice(val key: String, val kind: NoticeKind, val title: String, val text: String)

/** A bill waiting to be posted, as far as notifications care. */
data class BillDue(val itemId: String, val name: String, val amountText: String?, val date: LocalDate)

/** An overall or category budget at its current state, with display text prepared by the caller. */
data class BudgetStanding(val scope: String?, val name: String, val state: BudgetState, val remainingText: String, val periodStart: LocalDate)

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
    ): List<Notice> {
        if (prefs.isQuiet(minuteOfDay)) return emptyList()
        val out = mutableListOf<Notice>()
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
                    daysLeft <= 0 -> out += Notice(key, NoticeKind.CARD_DUE, "${c.name} bill ${if (daysLeft == 0) "is due today" else "is overdue"}", message)
                    daysLeft <= c.remindDaysBefore -> out += Notice(key, NoticeKind.CARD_DUE, "${c.name} bill due ${if (daysLeft == 1) "tomorrow" else "in $daysLeft days"}", message)
                }
            }
        }
        if (prefs.recap && recap != null && minuteOfDay >= prefs.billHour * 60) {
            // Once for each finished week, from the hour set for reminders on any day of the week after it.
            val key = WeeklyRecap.key(recap.weekStart)
            if (key !in alreadySent) out += Notice(key, NoticeKind.WEEKLY_RECAP, recap.title, recap.text)
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

    fun cardKey(accountId: String, due: LocalDate, onOrAfterDue: Boolean) = "card:$accountId:${due.toEpochDays()}:${if (onOrAfterDue) "due" else "ahead"}"

    fun billKey(itemId: String, date: LocalDate) = "bill:$itemId:${date.toEpochDays()}"

    fun budgetKey(scope: String?, periodStart: LocalDate, level: BudgetState) = "budget:${scope ?: "*"}:${periodStart.toEpochDays()}:${level.name}"
}
