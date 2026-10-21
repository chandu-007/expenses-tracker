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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.atStartOfDayIn
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
    private val bills: BillStore? = null,
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
                budgetNearPercent = int(NEAR_PERCENT, d.budgetNearPercent), budgetPace = flag(PACE, d.budgetPace), budgetWrapUp = flag(WRAP_UP, d.budgetWrapUp),
                billAhead3 = flag(AHEAD3, d.billAhead3), billAhead1 = flag(AHEAD1, d.billAhead1), cardStatement = flag(CARD_STATEMENT, d.cardStatement), cardLimit = flag(CARD_LIMIT, d.cardLimit),
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
            q.putSetting(NEAR_PERCENT, p.budgetNearPercent.toString())
            q.putSetting(PACE, if (p.budgetPace) "1" else "0")
            q.putSetting(WRAP_UP, if (p.budgetWrapUp) "1" else "0")
            q.putSetting(AHEAD3, if (p.billAhead3) "1" else "0")
            q.putSetting(AHEAD1, if (p.billAhead1) "1" else "0")
            q.putSetting(CARD_STATEMENT, if (p.cardStatement) "1" else "0")
            q.putSetting(CARD_LIMIT, if (p.cardLimit) "1" else "0")
        }
    }

    /** Posts what is set to post by itself, then works out which notices are due. Records nothing as shown. */
    fun check(today: LocalDate, minuteOfDay: Int, zone: TimeZone, currency: Currency = app.expenses.core.domain.BaseCurrency.current): CheckResult {
        bills?.reconcileWithEntries()
        val posted = recurring.autoPost(today, zone).map { it.first }
        val bills = recurring.due(today).map { BillDue(it.item.id, it.item.name, it.item.amount?.formatted(), it.date) }
        val status = budgets.status(today, zone, currency)
        val names = q.liveCategories().executeAsList().associate { it.id to it.name }
        val prefs0 = prefs()
        // the warning level is the person's own: over is over, near is from the chosen percent
        fun levelOf(p: app.expenses.core.domain.BudgetProgress) = when { p.state == BudgetState.OVER -> BudgetState.OVER; p.usedPercent >= prefs0.budgetNearPercent -> BudgetState.NEAR; else -> BudgetState.OK }
        fun leftText(it: app.expenses.core.domain.BudgetProgress) =
            if (it.remaining.minorUnits >= 0) "${it.remaining.formatted()} left of ${it.limit.formatted()}" else "${app.expenses.core.domain.Money(-it.remaining.minorUnits, currency).formatted()} over ${it.limit.formatted()}"
        val standings = status.progress.filter { levelOf(it) != BudgetState.OK }.map {
            BudgetStanding(it.categoryId, it.categoryId?.let { id -> names[id] } ?: "Everything", levelOf(it), leftText(it), status.period.start)
        }
        // spending well ahead of the month while still below the warning level (a budget needs some days behind it and some ahead to say anything)
        val daysIn = status.period.start.daysUntil(today) + 1
        val monthPercent = (daysIn * 100 / status.period.days).coerceIn(0, 100)
        // what repeating items posted is planned (rent, EMIs): it does not count towards "ahead of the month"
        val planned = budgets.plannedSpend(status.period, zone, currency)
        val childrenOf = q.liveCategories().executeAsList().filter { it.parent_id != null }.groupBy({ it.parent_id!! }, { it.id })
        fun plannedFor(scope: String?) = if (scope == null) planned.values.sum() else (listOf(scope) + childrenOf[scope].orEmpty()).sumOf { planned[it] ?: 0L }
        fun unplannedPercent(p: app.expenses.core.domain.BudgetProgress) = ((p.spent.minorUnits - plannedFor(p.categoryId)).coerceAtLeast(0L) * 100 / p.limit.minorUnits).coerceAtMost(1000L).toInt()
        val pace = if (monthPercent in PACE_FROM_MONTH_PERCENT..PACE_UNTIL_MONTH_PERCENT) status.progress.filter { levelOf(it) == BudgetState.OK && unplannedPercent(it) >= PACE_MIN_USED_PERCENT && unplannedPercent(it) >= monthPercent + PACE_LEAD_PERCENT }
            .map { app.expenses.core.domain.BudgetPace(it.categoryId, it.categoryId?.let { id -> names[id] } ?: "Everything", unplannedPercent(it), monthPercent, leftText(it), status.period.start) } else emptyList()
        // in the first days of a month: last month's overall budget, if there was one and it was kept
        val wrapUp = if (prefs0.budgetWrapUp && status.period.start.daysUntil(today) < WRAP_UP_DAYS) {
            val before = budgets.status(status.period.start.minus(1, kotlinx.datetime.DateTimeUnit.DAY), zone, currency)
            before.progress.firstOrNull { it.categoryId == null && it.spent.minorUnits > 0 && it.spent <= it.limit }?.let {
                app.expenses.core.domain.BudgetWrapUp(before.period.start, "${it.spent.formatted()} spent of ${it.limit.formatted()}: ${it.remaining.formatted()} kept.")
            }
        } else null
        val prefs = prefs0
        val hide = q.settingValue("hide_amounts").executeAsOneOrNull() == "1"
        return CheckResult(posted, NoticePlanner.plan(prefs, today, minuteOfDay, bills, standings, sent(), cardsDue(today, zone), if (prefs.recap) recapDue(today, zone, currency) else null, foundBills(hide), trackedBills(hide),
            pace = pace, wrapUp = wrapUp, statements = cardStatements(today, zone), limits = cardLimits(today, zone), cleared = cardsCleared(today, zone)))
    }

    /**
     * What [check] would plan on [day] at [minuteOfDay], without posting or recording anything: for a platform that has to hand the system its
     * reminders ahead of time (iPhone). Only bills and cards: repeating items that wait for the person (one set to post by itself needs no
     * reminder), tracked bills and credit-card due dates. Budgets and the recap depend on spending that has not happened yet, and a bill found
     * in a message is announced at once, so they are left to [check].
     */
    fun plannedFor(day: LocalDate, minuteOfDay: Int, zone: TimeZone): List<Notice> {
        val prefs = prefs()
        val hide = q.settingValue("hide_amounts").executeAsOneOrNull() == "1"
        val due = recurring.due(day).filter { !it.item.auto }.map { BillDue(it.item.id, it.item.name, it.item.amount?.formatted(), it.date) }
        return NoticePlanner.plan(prefs.copy(budgets = false, recap = false), day, minuteOfDay, due, emptyList(), sent(), cardsDue(day, zone), null, emptyList(), trackedBills(hide))
    }

    private fun money(minor: Long, code: String, hide: Boolean): String? {
        if (hide) return null // with Hide amounts on, a notification does not carry the figure either
        val digits = q.selectCurrencies().executeAsList().firstOrNull { it.code == code }?.digits?.toInt() ?: 2
        return app.expenses.core.domain.Money(minor, app.expenses.core.domain.Currency(code, digits)).formatted()
    }

    private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    /** Bills waiting to be added, newest first (at most [MAX_BILL_NOTICES], so a flood of reminders cannot bury the phone). */
    private fun foundBills(hide: Boolean): List<app.expenses.core.domain.FoundBill> = bills?.list(BillStatus.FOUND, MAX_BILL_NOTICES.toLong())?.map { b ->
        val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { "due ${it.dayOfMonth} ${monthNames[it.monthNumber - 1]}" }.orEmpty()
        app.expenses.core.domain.FoundBill(b.id, b.title, money(b.amountMinor, b.currency, hide), due)
    }.orEmpty()

    /** Bills being tracked that have a due date. */
    private fun trackedBills(hide: Boolean): List<app.expenses.core.domain.TrackedBill> = bills?.list(BillStatus.TRACKED)?.mapNotNull { b ->
        val date = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
        app.expenses.core.domain.TrackedBill(b.id, b.title, money(b.amountMinor, b.currency, hide), date)
    }.orEmpty()

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
    private fun cardsDue(today: LocalDate, zone: TimeZone): List<CardDue> = withoutTracked(cardsDueAll(today, zone))

    /**
     * A card whose statement is already a tracked bill (due dates within two days) is reminded by that bill, not twice. The bill must be known to be for that
     * card: the account remembered for its issuer and last four digits, or, with none remembered, the only card due in that window. With two or more
     * possible cards and nothing to tell them apart, both reminders are sent: a repeated reminder costs less than a missing one.
     */
    private fun withoutTracked(due: List<CardDue>): List<CardDue> {
        val tracked = bills?.list(BillStatus.TRACKED)?.filter { it.kind == "CREDIT_CARD" } ?: return due
        val silenced = mutableSetOf<String>()
        for (b in tracked) {
            val day = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            val near = due.filter { it.accountId !in silenced && kotlin.math.abs(it.dueDate.toEpochDays() - day.toEpochDays()) <= 2 }
            // the account that carries the bill's last four digits, else the account remembered for its issuer and digits
            val byDigits = b.accountLast4?.let { d -> app.expenses.core.domain.AccountLooks.pick(q.accountsWithLast4(d).executeAsList().map { it.id to app.expenses.core.domain.ledger.AccountType.valueOf(it.type) }, preferCard = true) }
            val mapped = byDigits ?: q.settingValue(CaptureAccounts.key(b.issuer, b.accountLast4)).executeAsOneOrNull()
            val hit = if (mapped != null) near.firstOrNull { it.accountId == mapped } else near.singleOrNull()
            if (hit != null) silenced += hit.accountId
        }
        return due.filter { it.accountId !in silenced }
    }

    private fun cardsDueAll(today: LocalDate, zone: TimeZone): List<CardDue> = cards.all().mapNotNull { terms ->
        val account = q.accountById(terms.accountId).executeAsOneOrNull() ?: return@mapNotNull null
        val standing = cards.standing(terms.accountId, today, zone)?.takeIf { it.remaining > 0 } ?: return@mapNotNull null
        val currency = app.expenses.core.domain.Currency(account.currency, q.selectCurrencies().executeAsList().first { it.code == account.currency }.digits.toInt())
        CardDue(terms.accountId, account.name, standing.cycle.dueDate, app.expenses.core.domain.Money(standing.remaining, currency).formatted(), app.expenses.core.domain.Money(standing.minimumDue, currency).formatted(), terms.remindDaysBefore)
    }

    private fun currencyOf(code: String) = app.expenses.core.domain.Currency(code, q.selectCurrencies().executeAsList().firstOrNull { it.code == code }?.digits?.toInt() ?: 2)

    /** Cards whose statement was made today or in the last two days and has something on it: told about once, from the reminder hour. */
    private fun cardStatements(today: LocalDate, zone: TimeZone): List<app.expenses.core.domain.CardStatementMade> = cards.all().mapNotNull { terms ->
        val account = q.accountById(terms.accountId).executeAsOneOrNull() ?: return@mapNotNull null
        val standing = cards.standing(terms.accountId, today, zone) ?: return@mapNotNull null
        val age = standing.cycle.statementDate.daysUntil(today)
        if (age !in 0..2 || standing.statementBalance <= 0) return@mapNotNull null
        val cur = currencyOf(account.currency)
        app.expenses.core.domain.CardStatementMade(terms.accountId, account.name, standing.cycle.statementDate, standing.cycle.dueDate, app.expenses.core.domain.Money(standing.statementBalance, cur).formatted(), app.expenses.core.domain.Money(standing.minimumDue, cur).formatted())
    }

    /** Cards that were reminded about (a statement or a due notice was sent for this cycle) and whose statement is now paid in full. */
    private fun cardsCleared(today: LocalDate, zone: TimeZone): List<app.expenses.core.domain.CardCleared> {
        val sent = sent()
        return cards.all().mapNotNull { terms ->
            val account = q.accountById(terms.accountId).executeAsOneOrNull() ?: return@mapNotNull null
            val standing = cards.standing(terms.accountId, today, zone) ?: return@mapNotNull null
            if (standing.statementBalance <= 0 || standing.remaining > 0) return@mapNotNull null
            val told = NoticePlanner.cardStatementKey(terms.accountId, standing.cycle.statementDate) in sent || NoticePlanner.cardKey(terms.accountId, standing.cycle.dueDate, false) in sent || NoticePlanner.cardKey(terms.accountId, standing.cycle.dueDate, true) in sent
            if (!told) null else app.expenses.core.domain.CardCleared(terms.accountId, account.name, standing.cycle.dueDate)
        }
    }

    /** Cards with a credit limit whose balance owed has reached 80% or 100% of it (the highest level reached). */
    private fun cardLimits(today: LocalDate, zone: TimeZone): List<app.expenses.core.domain.CardLimitUse> = cards.all().mapNotNull { terms ->
        val limit = cards.creditLimit(terms.accountId) ?: return@mapNotNull null
        val account = q.accountById(terms.accountId).executeAsOneOrNull() ?: return@mapNotNull null
        val untilMillis = today.plus(1, kotlinx.datetime.DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
        val owed = -(q.accountBalanceBefore(untilMillis, terms.accountId).executeAsOneOrNull()?.let { (it as Any?) as? Number }?.toLong() ?: 0L)
        if (owed <= 0) return@mapNotNull null
        val percent = (owed * 100 / limit).coerceAtMost(1000L).toInt()
        val level = when { percent >= 100 -> 100; percent >= 80 -> 80; else -> return@mapNotNull null }
        val cur = currencyOf(account.currency)
        app.expenses.core.domain.CardLimitUse(terms.accountId, account.name, level, percent, app.expenses.core.domain.Money(owed, cur).formatted(), app.expenses.core.domain.Money(limit, cur).formatted(), today.year * 12 + today.monthNumber)
    }

    fun markShown(notices: List<Notice>) {
        if (notices.isEmpty()) return
        val keys = (sent().toList() + notices.map { it.key }).distinct().takeLast(MAX_REMEMBERED)
        q.putSetting(SENT, keys.joinToString("\n"))
    }

    private fun sent(): Set<String> = q.settingValue(SENT).executeAsOneOrNull()?.split('\n')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    companion object {
        private const val MAX_BILL_NOTICES = 5
        private const val PACE_FROM_MONTH_PERCENT = 30 // rent and EMIs land in the first days: no verdict before a third of the period
        private const val PACE_UNTIL_MONTH_PERCENT = 90
        private const val PACE_MIN_USED_PERCENT = 30
        private const val PACE_LEAD_PERCENT = 20
        private const val WRAP_UP_DAYS = 5
        private const val RECAP = "notif_recap"
        private const val BILLS = "notif_bills"
        private const val BUDGETS = "notif_budgets"
        private const val QUIET_ON = "notif_quiet_on"
        private const val QUIET_START = "notif_quiet_start"
        private const val QUIET_END = "notif_quiet_end"
        private const val BILL_HOUR = "notif_bill_hour"
        private const val SENT = "notif_sent"
        private const val NEAR_PERCENT = "notif_near_percent"
        private const val PACE = "notif_pace"
        private const val WRAP_UP = "notif_wrapup"
        private const val AHEAD3 = "notif_ahead3"
        private const val AHEAD1 = "notif_ahead1"
        private const val CARD_STATEMENT = "notif_card_statement"
        private const val CARD_LIMIT = "notif_card_limit"

        /** Only the most recent events are remembered; a notice older than this could repeat, which is harmless. */
        const val MAX_REMEMBERED = 400
    }
}
