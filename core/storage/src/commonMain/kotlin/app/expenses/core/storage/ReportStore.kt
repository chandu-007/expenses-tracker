package app.expenses.core.storage

import app.expenses.core.domain.Category
import app.expenses.core.domain.CategoryRow
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.Money
import app.expenses.core.domain.ReportMath
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.TimeZone

/** What a report figure was opened from. */
sealed interface DrillTarget {
    /** Everything spent in the period. */
    data object All : DrillTarget

    /** One category and its subcategories. */
    data class InCategory(val id: String) : DrillTarget

    data object Uncategorised : DrillTarget

    /** Income received in the period. */
    data object Income : DrillTarget
}

/** One entry behind a report figure. [amount] is the part of it inside the chosen figure: your share of spending, or the income received. */
data class DrillLine(val entryId: String, val occurredAt: Long, val kind: String, val title: String, val categoryName: String?, val amount: Money)

/**
 * Read-only reports over the same spend lines the budgets use, so every number agrees with the rest of the app. Spending is your net
 * share (refunds taken off, only your part of shared expenses). Every figure can be opened as the list of entries it was added up from:
 * the lines of [drill] always sum to the matching figure of [breakdown], [spent] or [income].
 */
class ReportStore(private val db: ExpensesDb, private val ledger: LedgerStore) {
    private val q get() = db.schemaQueries

    /** Every category including deleted and merged ones, so old spending still has a name. */
    private fun categories(): List<Category> = q.allCategories().executeAsList().map {
        Category(it.id, it.name, it.parent_id, it.color, it.position.toInt(), it.hidden == 1L)
    }

    fun breakdown(period: DatePeriod, zone: TimeZone, currency: Currency): List<CategoryRow> =
        ReportMath.breakdown(spentByCategory(period, zone, currency), categories(), currency)

    fun spent(period: DatePeriod, zone: TimeZone, currency: Currency): Money =
        spentByCategory(period, zone, currency).values.fold(Money.zero(currency)) { a, m -> a + m }

    fun income(period: DatePeriod, zone: TimeZone, currency: Currency): Money =
        Money(q.incomeTotal(currency.code, period.startMillis(zone), period.endMillis(zone)).executeAsOne(), currency)

    /** Spending for each period in order, for a trend. */
    fun trend(periods: List<DatePeriod>, zone: TimeZone, currency: Currency): List<Money> = periods.map { spent(it, zone, currency) }

    /** The entries behind a figure, newest first, at most [limit]. Entries touching several chosen categories appear once with the sum. */
    fun drill(period: DatePeriod, zone: TimeZone, currency: Currency, target: DrillTarget, limit: Long = 500): List<DrillLine> {
        val from = period.startMillis(zone)
        val until = period.endMillis(zone)
        val names = categories().associate { it.id to it.name }
        fun title(kind: String, merchant: String?, note: String?) = merchant ?: note ?: kind.lowercase().replaceFirstChar { it.uppercase() }
        fun money(minor: Long) = Money(minor, currency)
        if (target == DrillTarget.Income) {
            return q.incomeEntries(currency.code, from, until, limit).executeAsList().map {
                DrillLine(it.id, it.occurred_at, it.kind, title(it.kind, it.merchant, it.note), it.category_id?.let { id -> names[id] ?: id }, money(it.amount_minor))
            }
        }
        data class Row(val id: String, val kind: String, val merchant: String?, val note: String?, val at: Long, val mine: Long, val category: String?)
        val rows = when (target) {
            DrillTarget.All -> q.drillAll(currency.code, from, until, limit).executeAsList().map { Row(it.entry_id, it.kind, it.merchant, it.note, it.occurred_at, it.mine ?: 0L, it.category_id) }
            DrillTarget.Uncategorised -> q.drillUncategorised(currency.code, from, until, limit).executeAsList().map { Row(it.entry_id, it.kind, it.merchant, it.note, it.occurred_at, it.mine ?: 0L, it.category_id) }
            is DrillTarget.InCategory -> {
                val all = categories()
                val ids = listOf(target.id) + all.filter { it.parentId == target.id }.map { it.id }
                q.drillCategories(currency.code, from, until, ids, limit).executeAsList().map { Row(it.entry_id, it.kind, it.merchant, it.note, it.occurred_at, it.mine ?: 0L, it.category_id) }
            }
            DrillTarget.Income -> emptyList()
        }
        return rows.map { DrillLine(it.id, it.at, it.kind, title(it.kind, it.merchant, it.note), it.category?.let { id -> names[id] ?: id }, money(it.mine)) }
    }

    private fun spentByCategory(period: DatePeriod, zone: TimeZone, currency: Currency): Map<String?, Money> =
        ledger.spendByCategory(currency, from = period.startMillis(zone), until = period.endMillis(zone)).mapKeys { it.key?.value }
}
