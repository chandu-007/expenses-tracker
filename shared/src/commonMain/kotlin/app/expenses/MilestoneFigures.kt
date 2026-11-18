package app.expenses

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.Milestone
import app.expenses.core.domain.MilestoneFacts
import app.expenses.core.domain.MilestonePeriod
import app.expenses.core.domain.Milestones
import app.expenses.core.domain.NoSpendDays
import app.expenses.core.domain.NudgePause
import app.expenses.core.domain.dateOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * UI S6: the ledger side of the occasional milestones (`Milestones`, docs/HOME_NUDGES.md). Called only from the background figures and never from `refresh()` or the save path.
 * The settings are read first (a few small values) and nothing else is read from the ledger unless a milestone may be shown at all; the month facts are worked out once a day and kept.
 */
internal class MilestoneFigures {
    private var monthDay = Long.MIN_VALUE
    private var monthFacts: MilestonePeriod? = null

    /** The milestone to show now, or null (the opt-in, the Pause nudges setting and the caps are checked first, from settings alone). */
    fun next(ok: AppContainer.Opened.Ok, nowMillis: Long, zone: TimeZone): Milestone? {
        val t = ok.templates
        val seen = Milestones.decode(t.setting(Milestones.SEEN_SETTING))
        val today = dateOf(nowMillis, zone).toEpochDays()
        val pausedUntil = t.setting(NudgePause.SETTING)?.toLongOrNull()
        val on = t.setting(Milestones.ON_SETTING) == "1"
        if (!Milestones.mayShow(on, pausedUntil, nowMillis, seen, today)) return null
        val q = ok.db.schemaQueries
        val count = q.entryCount().executeAsOne()
        if (count <= 0L) return null // the clock starts with the first entry
        var since = t.setting(Milestones.SINCE_SETTING)
        if (since == null) { since = if (count <= Milestones.NEW_LEDGER_ENTRIES) nowMillis.toString() else "old"; t.putSetting(Milestones.SINCE_SETTING, since) }
        val facts = MilestoneFacts(
            entryCount = count,
            daysOfUse = Milestones.daysOfUse(since, nowMillis),
            backedUp = t.setting(DataTransfer.SETTING_BACKUP_AT)?.toLongOrNull() != null,
            lastPeriod = lastPeriod(ok, today, zone, q.firstEntryAt().executeAsOne().MIN),
        )
        return Milestones.next(on, pausedUntil, nowMillis, seen, today, facts)
    }

    /** The budget month that just ended: whether its overall limit was kept, and how many of its days had no spending. Only a monthly budget period is a "month". Worked out once per day. */
    private fun lastPeriod(ok: AppContainer.Opened.Ok, today: Long, zone: TimeZone, firstEntryAt: Long?): MilestonePeriod? {
        if (monthDay == today) return monthFacts
        monthFacts = null
        monthDay = today
        if (ok.budgets.period() !is BudgetPeriod.Monthly) return null
        val inr = BaseCurrency.current
        val todayDate = kotlinx.datetime.LocalDate.fromEpochDays(today)
        val current = ok.budgets.period().containing(todayDate)
        val before = ok.budgets.status(current.start.minus(1, DateTimeUnit.DAY), zone, inr)
        val period = before.period
        val kept = before.progress.any { it.categoryId == null && it.spent.minorUnits > 0 && it.spent <= it.limit }
        val first = firstEntryAt?.let { dateOf(it, zone).toEpochDays() }
        val perDay = LongArray(period.days)
        for (p in ok.reports.spendPoints(period, zone, inr)) {
            val i = (dateOf(p.atMillis, zone).toEpochDays() - period.start.toEpochDays()).toInt()
            if (i in perDay.indices) perDay[i] += p.minor
        }
        val quiet = NoSpendDays.of(perDay.indices.map { (period.start.toEpochDays() + it) to perDay[it] }, today, first).size
        val name = DatePattern("MMMM").format(period.start.plus(14, DateTimeUnit.DAY))
        return MilestonePeriod(period.start.toEpochDays(), name, kept, quiet).also { monthFacts = it }
    }

    companion object {
        /** The person said "Thanks": the milestone is kept as seen, on [today]. */
        fun acknowledge(ok: AppContainer.Opened.Ok, id: String, today: Long) {
            val seen = Milestones.acknowledge(Milestones.decode(ok.templates.setting(Milestones.SEEN_SETTING)), id, today)
            ok.templates.putSetting(Milestones.SEEN_SETTING, Milestones.encode(seen))
        }
    }
}
