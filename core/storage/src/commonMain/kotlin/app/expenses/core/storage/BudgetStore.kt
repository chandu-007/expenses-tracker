package app.expenses.core.storage

import app.expenses.core.domain.BudgetLimit
import app.expenses.core.domain.BudgetMath
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.BudgetProgress
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringKind
import app.expenses.core.domain.SafeToSpend
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** The current budget period with where every limit stands. [safeToSpend] is null when there is no overall limit. */
/** [carried] is what each limit gained from the period before when rollover is on (by category; null is the overall limit); empty otherwise. */
data class BudgetStatus(val period: DatePeriod, val progress: List<BudgetProgress>, val safeToSpend: SafeToSpend?, val carried: Map<String?, Money> = emptyMap())

/** The scope of the overall limit in the `budget` table. */
private const val OVERALL = ""
private const val ROLLOVER = "budget_rollover"

/**
 * Spending limits: one overall cap and any number of per-category caps, all for one period type (monthly from a chosen day, or weekly).
 * A category's limit counts spending in that category and its subcategories. Changes are written to the sync log like the rest.
 *
 * Not thread-safe: call through the process-wide write lock.
 */
class BudgetStore(
    private val db: ExpensesDb,
    private val clock: HlcClock,
    private val nowMillis: () -> Long,
    private val ledger: LedgerStore,
    private val recurring: RecurringStore,
) {
    private val q get() = db.schemaQueries

    /** Every limit; the overall one first, then categories in their display order. Limits in other currencies are included. */
    fun limits(): List<BudgetLimit> {
        val order = q.liveCategories().executeAsList().withIndex().associate { (i, c) -> c.id to i }
        return q.allBudgets().executeAsList()
            .sortedWith(compareBy({ it.scope != OVERALL }, { order[it.scope] ?: Int.MAX_VALUE }, { it.scope }))
            .map { BudgetLimit(it.scope.ifEmpty { null }, Money(it.limit_minor, currency(it.currency))) }
    }

    /** Sets or replaces a limit. A null [categoryId] is the overall cap. A merged category's id is redirected to the category it became. */
    fun setLimit(categoryId: String?, limit: Money) {
        require(limit.minorUnits > 0) { "A budget limit must be positive" }
        db.transaction {
            val scope = categoryId?.let(::liveScope) ?: OVERALL
            q.insertCurrency(limit.currency.code, limit.currency.minorUnitDigits.toLong())
            writeBudget(db, clock, nowMillis, scope, limit)
        }
    }

    fun clearLimit(categoryId: String?) {
        db.transaction { writeBudget(db, clock, nowMillis, categoryId?.let { db.resolveCategory(it) } ?: OVERALL, null) }
    }

    fun period(): BudgetPeriod {
        val row = q.budgetPeriod().executeAsOneOrNull() ?: return BudgetPeriod.Monthly(1)
        return when (row.kind) {
            "weekly" -> BudgetPeriod.Weekly(DayOfWeek.entries.first { it.ordinal + 1 == row.start_value.toInt() })
            "biweekly" -> BudgetPeriod.BiWeekly(kotlinx.datetime.LocalDate.fromEpochDays(row.start_value))
            "semimonthly" -> BudgetPeriod.SemiMonthly
            "yearly" -> BudgetPeriod.Yearly(row.start_value.toInt())
            // "days10": a custom length of 10 days, counted from the day in start_value
            else -> if (row.kind.startsWith("days")) BudgetPeriod.EveryDays(row.kind.removePrefix("days").toInt(), kotlinx.datetime.LocalDate.fromEpochDays(row.start_value)) else BudgetPeriod.Monthly(row.start_value.toInt())
        }
    }

    fun setPeriod(period: BudgetPeriod) {
        val (kind, param) = when (period) {
            is BudgetPeriod.Monthly -> "monthly" to period.startDay.toLong()
            is BudgetPeriod.Weekly -> "weekly" to (period.startsOn.ordinal + 1).toLong()
            is BudgetPeriod.BiWeekly -> "biweekly" to period.anchor.toEpochDays()
            BudgetPeriod.SemiMonthly -> "semimonthly" to 0L
            is BudgetPeriod.Yearly -> "yearly" to period.startMonth.toLong()
            is BudgetPeriod.EveryDays -> "days${period.days}" to period.anchor.toEpochDays()
        }
        db.transaction {
            val before = q.budgetPeriod().executeAsOneOrNull()
            q.putBudgetPeriod(kind, param)
            if (before?.kind != kind) q.insertOp(clock.now(nowMillis()).encode(), "budget_period", "main", "kind", kind)
            if (before?.start_value != param) q.insertOp(clock.now(nowMillis()).encode(), "budget_period", "main", "param", param.toString())
        }
    }

    /**
     * Where every limit in [currency] stands for the period containing [today] in [zone]. Spending is your net share (refunds taken
     * off). Safe to spend comes from the overall limit, less the repeating expenses still waiting in the period (those without a fixed amount cannot be counted).
     */
    fun status(today: LocalDate, zone: TimeZone, currency: Currency): BudgetStatus {
        val period = period().containing(today)
        val base = limits().filter { it.limit.currency == currency }
        val children = q.liveCategories().executeAsList().filter { it.parent_id != null }.groupBy({ it.parent_id!! }, { it.id })
        // with rollover on, what was left unspent under each limit in the period before is added to it (only that one period: it does not pile up)
        val carried = if (rollover()) {
            val before = period().containing(period.start.minus(1, kotlinx.datetime.DateTimeUnit.DAY))
            val spentBefore = ledger.spendByCategory(currency, from = before.startMillis(zone), until = before.endMillis(zone)).mapKeys { it.key?.value }
            BudgetMath.progress(base, spentBefore, children).filter { it.remaining.minorUnits > 0 }.associate { it.categoryId to it.remaining }
        } else emptyMap()
        val limits = base.map { b -> carried[b.categoryId]?.let { BudgetLimit(b.categoryId, b.limit + it) } ?: b }
        val spent = ledger.spendByCategory(currency, from = period.startMillis(zone), until = period.endMillis(zone))
            .mapKeys { it.key?.value }
        val progress = BudgetMath.progress(limits, spent, children)
        val overall = progress.firstOrNull { it.categoryId == null }
        // Repeating expenses still waiting in this period (past-due ones are included: they will post dated inside it).
        // Only expenses are spending still to come; a repeating income or a transfer between own accounts is not set aside.
        val upcoming = recurring.unhandled(period.start, period.endExclusive).filter { it.item.kind == RecurringKind.EXPENSE }.mapNotNull { it.item.amount }.filter { it.currency == currency }
            .fold(Money.zero(currency)) { a, m -> a + m }
        return BudgetStatus(period, progress, overall?.let { BudgetMath.safeToSpend(it, today, period, upcoming) }, carried)
    }

    /** Spending in [period] that repeating items posted (planned: rent, EMIs, subscriptions), by category (null: uncategorised). */
    fun plannedSpend(period: DatePeriod, zone: TimeZone, currency: Currency): Map<String?, Long> =
        q.spendByCategoryFromRepeating(currency.code, period.startMillis(zone), period.endMillis(zone)).executeAsList().associate { it.category_id to (it.mine ?: 0L) }

    /** Whether what is left unspent from the last period is added to this period's limits. Off unless the person turns it on. */
    fun rollover(): Boolean = q.settingValue(ROLLOVER).executeAsOneOrNull() == "1"

    fun setRollover(on: Boolean) { q.putSetting(ROLLOVER, if (on) "1" else "0") }

    private fun liveScope(categoryId: String): String {
        val id = db.resolveCategory(categoryId)
        val row = q.categoryById(id).executeAsOneOrNull()
        require(row != null && row.deleted == 0L) { "That category does not exist" }
        return id
    }

    private fun currency(code: String): Currency =
        q.selectCurrencies().executeAsList().first { it.code == code }.let { Currency(it.code, it.digits.toInt()) }
}

/**
 * Stores or clears (null [limit]) one budget row and logs it. Shared with [CategoryStore], which moves or drops a category's limit
 * when the category is merged or deleted. Must run inside a transaction.
 */
internal fun writeBudget(db: ExpensesDb, clock: HlcClock, nowMillis: () -> Long, scope: String, limit: Money?) {
    val q = db.schemaQueries
    val before = q.budgetByScope(scope).executeAsOneOrNull()
    if (limit == null) {
        if (before == null) return
        q.deleteBudget(scope)
        q.insertOp(clock.now(nowMillis()).encode(), "budget", scope, "limit_minor", null)
        return
    }
    q.upsertBudget(scope, limit.minorUnits, limit.currency.code)
    if (before?.limit_minor != limit.minorUnits) q.insertOp(clock.now(nowMillis()).encode(), "budget", scope, "limit_minor", limit.minorUnits.toString())
    if (before?.currency != limit.currency.code) q.insertOp(clock.now(nowMillis()).encode(), "budget", scope, "currency", limit.currency.code)
}
