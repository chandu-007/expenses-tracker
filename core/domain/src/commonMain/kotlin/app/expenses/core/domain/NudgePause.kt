package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus

/**
 * UI S6 (owner decision D2): "Pause nudges". One switch with three lengths that quiets everything the app volunteers, and nothing the person is bound to or asked for.
 *
 * Stored as one setting, [SETTING]: epoch milliseconds until which nudges are paused, [FOREVER] (Long.MAX_VALUE) for "until I turn it back on". No setting, or a time in the past, is not paused.
 *
 * **Paused** (suggestions and reminders that only ask the person to tidy or set something up; each is still one tap away in More):
 *  - the Home cards To review, New bills, Money to get back, No category, Set up reading bank texts and Getting started ([HOME_PAUSED]);
 *  - the backup reminder (its card in More and the dot on the More tab);
 *  - occasional milestones ([Milestones]);
 *  - the push notices [pausesNotice] says: a bill found in a message, "spending is ahead of the month", the month-end note and "card bill is paid".
 *
 * **Not paused**: Bills due (a bill due or overdue is a commitment with a date), push reminders of a tracked bill, a repeating expense or a card's due date, a card's statement and its limit,
 * budget "near" and "over" alerts, and the weekly recap (the person switched that on themselves, it is off by default). Those have their own switches in Notifications.
 */
object NudgePause {
    const val SETTING = "nudges_paused_until"
    const val FOREVER = Long.MAX_VALUE

    enum class Choice { WEEK, MONTH, FOREVER }

    /** The Home cards a pause hides. Bills due ([HomeNudge.BILLS_DUE]) is the one that never is. */
    val HOME_PAUSED: Set<HomeNudge> = HomeNudge.entries.toSet() - HomeNudge.BILLS_DUE

    fun isPaused(untilMillis: Long?, nowMillis: Long): Boolean = untilMillis != null && nowMillis < untilMillis

    /** The setting's value for a pause of [choice] begun at [nowMillis]: a week, or a calendar month, from the start of today in [zone] (so "until 25 Nov" is a whole day); or [FOREVER]. */
    fun until(choice: Choice, nowMillis: Long, zone: TimeZone): Long = when (choice) {
        Choice.FOREVER -> FOREVER
        Choice.WEEK -> startOfDay(dateOf(nowMillis, zone).plus(7, DateTimeUnit.DAY), zone)
        Choice.MONTH -> startOfDay(dateOf(nowMillis, zone).plus(1, DateTimeUnit.MONTH), zone)
    }

    /** "Paused until 25 Nov", "Paused" (until turned back on), or null when not paused. */
    fun status(untilMillis: Long?, nowMillis: Long, zone: TimeZone): String? {
        if (!isPaused(untilMillis, nowMillis)) return null
        if (untilMillis == FOREVER) return "Paused"
        val d = dateOf(untilMillis!!, zone)
        return "Paused until ${d.dayOfMonth} ${MONTH_NAMES[d.monthNumber - 1]}"
    }

    /** Whether a pause holds back a push notice of [kind] (see the class comment). */
    fun pausesNotice(kind: NoticeKind): Boolean = when (kind) {
        NoticeKind.BILL_FOUND, NoticeKind.BUDGET_PACE, NoticeKind.BUDGET_WRAPUP, NoticeKind.CARD_CLEARED -> true
        NoticeKind.BILL_DUE, NoticeKind.CARD_DUE, NoticeKind.BILL_TRACKED, NoticeKind.CARD_STATEMENT, NoticeKind.CARD_LIMIT,
        NoticeKind.BUDGET_NEAR, NoticeKind.BUDGET_OVER, NoticeKind.WEEKLY_RECAP -> false
    }

    private val MONTH_NAMES = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
