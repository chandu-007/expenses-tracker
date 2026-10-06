package app.expenses.core.storage

import app.expenses.core.domain.BudgetLimit
import app.expenses.core.domain.BudgetMath
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.BudgetProgress
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.Money
import app.expenses.core.domain.SafeToSpend
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** The current budget period with where every limit stands. [safeToSpend] is null when there is no overall limit. */
data class BudgetStatus(val period: DatePeriod, val progress: List<BudgetProgress>, val safeToSpend: SafeToSpend?)

/** The scope of the overall limit in the `budget` table. */
private const val OVERALL = ""

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
            else -> BudgetPeriod.Monthly(row.start_value.toInt())
        }
    }

    fun setPeriod(period: BudgetPeriod) {
        val (kind, param) = when (period) {
            is BudgetPeriod.Monthly -> "monthly" to period.startDay.toLong()
            is BudgetPeriod.Weekly -> "weekly" to (period.startsOn.ordinal + 1).toLong()
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
        val limits = limits().filter { it.limit.currency == currency }
        val spent = ledger.spendByCategory(currency, from = period.startMillis(zone), until = period.endMillis(zone))
            .mapKeys { it.key?.value }
        val children = q.liveCategories().executeAsList().filter { it.parent_id != null }.groupBy({ it.parent_id!! }, { it.id })
        val progress = BudgetMath.progress(limits, spent, children)
        val overall = progress.firstOrNull { it.categoryId == null }
        // Repeating expenses still waiting in this period (past-due ones are included: they will post dated inside it).
        val upcoming = recurring.unhandled(period.start, period.endExclusive).mapNotNull { it.item.amount }.filter { it.currency == currency }
            .fold(Money.zero(currency)) { a, m -> a + m }
        return BudgetStatus(period, progress, overall?.let { BudgetMath.safeToSpend(it, today, period, upcoming) })
    }

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
