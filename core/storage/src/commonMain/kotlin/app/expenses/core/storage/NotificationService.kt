package app.expenses.core.storage

import app.expenses.core.domain.BillDue
import app.expenses.core.domain.CardDue
import app.expenses.core.domain.RecapDue
import app.expenses.core.domain.WeeklyRecap
import app.expenses.core.domain.BudgetState
import app.expenses.core.domain.BudgetStanding
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DueItem
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticePlanner
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.formatted
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** What one check found: the repeating expenses it posted by itself, and the notices that should be shown now. */
data class CheckResult(val autoPosted: List<DueItem>, val notices: List<Notice>)

/**
 * Notification preferences and the record of what was already shown, kept in the local settings table (never synced: each device
 * decides for itself what it shows), plus the check that decides what to show. Showing is the caller's job; [markShown] records it,
 * so a notice that could not be shown (permission off, app killed) is offered again at the next check.
 *
 * Not thread-safe: call through the process-wide write lock.
 */
class NotificationService(
    private val db: ExpensesDb,
    private val recurring: RecurringStore,
    private val budgets: BudgetStore,
    private val cards: CardStore,
    private val reports: ReportStore,
) {
    private val q get() = db.schemaQueries

    fun prefs(): NotificationPrefs {
        val d = NotificationPrefs()
        fun int(key: String, default: Int) = q.settingValue(key).executeAsOneOrNull()?.toIntOrNull() ?: default
        fun flag(key: String, default: Boolean) = q.settingValue(key).executeAsOneOrNull()?.let { it == "1" } ?: default
        return runCatching {
            NotificationPrefs(
                bills = flag(BILLS, d.bills), budgets = flag(BUDGETS, d.budgets), quietEnabled = flag(QUIET_ON, d.quietEnabled),
                quietStartMinute = int(QUIET_START, d.quietStartMinute), quietEndMinute = int(QUIET_END, d.quietEndMinute), billHour = int(BILL_HOUR, d.billHour), recap = flag(RECAP, d.recap),
            )
        }.getOrDefault(d) // a damaged value falls back to the defaults rather than blocking the app
    }

    fun setPrefs(p: NotificationPrefs) {
        db.transaction {
            q.putSetting(BILLS, if (p.bills) "1" else "0")
            q.putSetting(BUDGETS, if (p.budgets) "1" else "0")
            q.putSetting(QUIET_ON, if (p.quietEnabled) "1" else "0")
            q.putSetting(QUIET_START, p.quietStartMinute.toString())
            q.putSetting(QUIET_END, p.quietEndMinute.toString())
            q.putSetting(BILL_HOUR, p.billHour.toString())
            q.putSetting(RECAP, if (p.recap) "1" else "0")
        }
    }

    /** Posts what is set to post by itself, then works out which notices are due. Records nothing as shown. */
    fun check(today: LocalDate, minuteOfDay: Int, zone: TimeZone, currency: Currency = app.expenses.core.domain.BaseCurrency.current): CheckResult {
        val posted = recurring.autoPost(today, zone).map { it.first }
        val bills = recurring.due(today).map { BillDue(it.item.id, it.item.name, it.item.amount?.formatted(), it.date) }
        val status = budgets.status(today, zone, currency)
        val names = q.liveCategories().executeAsList().associate { it.id to it.name }
        val standings = status.progress.filter { it.state != BudgetState.OK }.map {
            val left = if (it.remaining.minorUnits >= 0) "${it.remaining.formatted()} left of ${it.limit.formatted()}" else "${app.expenses.core.domain.Money(-it.remaining.minorUnits, currency).formatted()} over ${it.limit.formatted()}"
            BudgetStanding(it.categoryId, it.categoryId?.let { id -> names[id] } ?: "Everything", it.state, left, status.period.start)
        }
        val prefs = prefs()
        return CheckResult(posted, NoticePlanner.plan(prefs, today, minuteOfDay, bills, standings, sent(), cardsDue(today, zone), if (prefs.recap) recapDue(today, zone, currency) else null))
    }

    /** The recap of the last finished week, or null when there is nothing to say. Computed only when the recap is switched on. */
    private fun recapDue(today: LocalDate, zone: TimeZone, currency: Currency): RecapDue? {
        val week = WeeklyRecap.recappedWeek(today)
        val spent = reports.spent(week, zone, currency)
        val previous = reports.spent(WeeklyRecap.previousWeek(week), zone, currency)
        val top = reports.breakdown(week, zone, currency).maxByOrNull { it.total.minorUnits }?.takeIf { it.total.minorUnits > 0 }?.let { it.name to it.share }
        val (title, text) = WeeklyRecap.compose(week, spent, previous, top) { it.formatted() } ?: return null
        return RecapDue(week.start, title, text)
    }

    /** Credit cards with billing dates that still have something to pay on their latest statement. */
    private fun cardsDue(today: LocalDate, zone: TimeZone): List<CardDue> = cards.all().mapNotNull { terms ->
        val account = q.accountById(terms.accountId).executeAsOneOrNull() ?: return@mapNotNull null
        val standing = cards.standing(terms.accountId, today, zone)?.takeIf { it.remaining > 0 } ?: return@mapNotNull null
        val currency = app.expenses.core.domain.Currency(account.currency, q.selectCurrencies().executeAsList().first { it.code == account.currency }.digits.toInt())
        CardDue(terms.accountId, account.name, standing.cycle.dueDate, app.expenses.core.domain.Money(standing.remaining, currency).formatted(), app.expenses.core.domain.Money(standing.minimumDue, currency).formatted(), terms.remindDaysBefore)
    }

    fun markShown(notices: List<Notice>) {
        if (notices.isEmpty()) return
        val keys = (sent().toList() + notices.map { it.key }).distinct().takeLast(MAX_REMEMBERED)
        q.putSetting(SENT, keys.joinToString("\n"))
    }

    private fun sent(): Set<String> = q.settingValue(SENT).executeAsOneOrNull()?.split('\n')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    companion object {
        private const val RECAP = "notif_recap"
        private const val BILLS = "notif_bills"
        private const val BUDGETS = "notif_budgets"
        private const val QUIET_ON = "notif_quiet_on"
        private const val QUIET_START = "notif_quiet_start"
        private const val QUIET_END = "notif_quiet_end"
        private const val BILL_HOUR = "notif_bill_hour"
        private const val SENT = "notif_sent"

        /** Only the most recent events are remembered; a notice older than this could repeat, which is harmless. */
        const val MAX_REMEMBERED = 400
    }
}
