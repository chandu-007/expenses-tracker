package app.expenses.core.domain

/** One calm moment Home may mention once: its [id] (what "once ever" is counted by) and the one line it says. The line never holds an amount. */
data class Milestone(val id: String, val text: String)

/** A milestone the person has acknowledged, and the day (epoch day) they did; the caps count from these. */
data class MilestoneSeen(val id: String, val epochDay: Long)

/** The month that just ended, as far as milestones care: [overallBudgetKept] is true only when an overall budget existed for it and was not passed; [quietDays] is its count of no-spend days ([NoSpendDays]). */
data class MilestonePeriod(val startEpochDay: Long, val monthName: String, val overallBudgetKept: Boolean, val quietDays: Int)

/**
 * What is known when a milestone is considered. [daysOfUse] is the whole days since the milestone clock started on a ledger that was new then (a handful of entries at most);
 * null on a ledger that already had history, which has no "first week". [lastPeriod] is the budget month that just ended, or null.
 */
data class MilestoneFacts(val entryCount: Long, val daysOfUse: Int?, val backedUp: Boolean, val lastPeriod: MilestonePeriod?)

/**
 * UI S6 (owner decision D2, items U-16 and N-26): occasional, calm milestones. Opt-in (off until the person turns "Occasional milestones" on in Notifications), about a few a year, shown only
 * as one small card on Home (never a push notice), with one line, a drawn tick and a "Thanks" button. No streak, no counter, no red, no confetti, and no amount in any text.
 *
 * Rules, all pure and tested by `MilestonesTest`:
 *  - each milestone at most once ever ([MilestoneSeen] ids), a monthly one once per month it is about;
 *  - at most [MAX_PER_YEAR] in any [WINDOW_DAYS] days, and at least [MIN_GAP_DAYS] days after the last;
 *  - never while "Pause nudges" is on ([NudgePause]), never while the opt-in is off.
 *
 * The candidates, most recent first (so a person who turns this on late hears of last month before an old first): [UNDER_BUDGET] (N-26, the month that ended under its overall budget; only
 * where there was one), [QUIET_DAYS] (a month with several no-spend days), [FIRST_BACKUP], [ENTRIES_100], [FIRST_WEEK] and [FIRST_ENTRY]. The last two only on a ledger that was new when the clock started.
 */
object Milestones {
    const val ON_SETTING = "milestones_on"
    const val SEEN_SETTING = "milestones_seen"
    /** When the milestone clock started: epoch milliseconds, or "old" if the ledger already had history then. Written once, on the first look with an entry. */
    const val SINCE_SETTING = "milestones_since"

    const val MAX_PER_YEAR = 6
    const val MIN_GAP_DAYS = 14
    const val WINDOW_DAYS = 365
    /** A ledger with at most this many entries when the clock starts is "new": it gets the first-entry and first-week moments. */
    const val NEW_LEDGER_ENTRIES = 10
    /** The first-entry moment is only for the first days of a new ledger. */
    const val FIRST_ENTRY_DAYS = 14
    /** A month needs this many no-spend days to be called quiet. */
    const val QUIET_DAYS_MIN = 5

    const val FIRST_ENTRY = "first_entry"
    const val FIRST_WEEK = "first_week"
    const val ENTRIES_100 = "entries_100"
    const val FIRST_BACKUP = "first_backup"
    const val UNDER_BUDGET = "under_budget"
    const val QUIET_DAYS = "quiet_days"

    /** The id of a monthly milestone: the kind and the first day of the month it is about, so each month is its own "once". */
    fun monthlyId(kind: String, periodStartEpochDay: Long) = "$kind:$periodStartEpochDay"

    /** Everything that holds right now, in the order they are offered. */
    fun candidates(f: MilestoneFacts): List<Milestone> = buildList {
        f.lastPeriod?.let { p ->
            if (p.overallBudgetKept) add(Milestone(monthlyId(UNDER_BUDGET, p.startEpochDay), "${p.monthName} ended under budget."))
            if (p.quietDays >= QUIET_DAYS_MIN) add(Milestone(monthlyId(QUIET_DAYS, p.startEpochDay), "${p.monthName} had several days with no spending."))
        }
        if (f.backedUp) add(Milestone(FIRST_BACKUP, "Your first backup is made."))
        if (f.entryCount >= 100) add(Milestone(ENTRIES_100, "More than a hundred entries are in your records."))
        val days = f.daysOfUse
        if (days != null && days in 7..35 && f.entryCount >= 3) add(Milestone(FIRST_WEEK, "A first week of keeping your records."))
        if (days != null && days < FIRST_ENTRY_DAYS && f.entryCount in 1..NEW_LEDGER_ENTRIES.toLong()) add(Milestone(FIRST_ENTRY, "Your first entry is noted."))
    }

    /**
     * Whether a milestone may be shown at all now: the opt-in is on, no pause is on, fewer than [MAX_PER_YEAR] were acknowledged in the last [WINDOW_DAYS] days and the last was at least
     * [MIN_GAP_DAYS] days ago. Cheap on purpose: asked first, so nothing is read from the ledger when the answer is no.
     */
    fun mayShow(enabled: Boolean, pausedUntilMillis: Long?, nowMillis: Long, seen: List<MilestoneSeen>, todayEpochDay: Long): Boolean {
        if (!enabled || NudgePause.isPaused(pausedUntilMillis, nowMillis)) return false
        val recent = seen.filter { todayEpochDay - it.epochDay in 0 until WINDOW_DAYS }
        if (recent.size >= MAX_PER_YEAR) return false
        val last = seen.maxOfOrNull { it.epochDay } ?: return true
        return todayEpochDay - last >= MIN_GAP_DAYS
    }

    /** The milestone to show, or null: the first of [candidates] not yet acknowledged, when [mayShow] says so. */
    fun next(enabled: Boolean, pausedUntilMillis: Long?, nowMillis: Long, seen: List<MilestoneSeen>, todayEpochDay: Long, facts: MilestoneFacts): Milestone? {
        if (!mayShow(enabled, pausedUntilMillis, nowMillis, seen, todayEpochDay)) return null
        val done = seen.map { it.id }.toSet()
        return candidates(facts).firstOrNull { it.id !in done }
    }

    /** [seen] with [id] acknowledged on [epochDay]. Acknowledging twice changes nothing. */
    fun acknowledge(seen: List<MilestoneSeen>, id: String, epochDay: Long): List<MilestoneSeen> =
        if (seen.any { it.id == id }) seen else seen + MilestoneSeen(id, epochDay)

    /** The setting's value: `id@day` joined by commas. */
    fun encode(seen: List<MilestoneSeen>): String = seen.joinToString(",") { "${it.id}@${it.epochDay}" }

    /** Reads the setting; an entry that does not parse is left out (a damaged value never blocks Home). */
    fun decode(raw: String?): List<MilestoneSeen> = raw.orEmpty().split(',').mapNotNull { part ->
        val at = part.lastIndexOf('@')
        if (at <= 0) return@mapNotNull null
        val day = part.substring(at + 1).toLongOrNull() ?: return@mapNotNull null
        MilestoneSeen(part.substring(0, at), day)
    }

    /** What the milestone clock setting says: the days since it started, or null for "old", a missing or a damaged value. */
    fun daysOfUse(since: String?, nowMillis: Long): Int? {
        val start = since?.toLongOrNull() ?: return null
        return ((nowMillis - start) / 86_400_000L).coerceAtLeast(0L).toInt()
    }
}
