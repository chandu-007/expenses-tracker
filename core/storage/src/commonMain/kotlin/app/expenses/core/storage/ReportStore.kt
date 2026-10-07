package app.expenses.core.storage

import app.expenses.core.domain.Category
import app.expenses.core.domain.CategoryRow
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.Money
import app.expenses.core.domain.ReportMath
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.TimeZone

enum class ReportGroup { CATEGORY, ACCOUNT, TAG, MERCHANT }

/** What a report figure was opened from. */
sealed interface DrillTarget {
    /** Everything spent in the period. */
    data object All : DrillTarget

    /** One category and its subcategories. */
    data class InCategory(val id: String) : DrillTarget

    data object Uncategorised : DrillTarget

    /** Spending from one account. */
    data class InAccount(val id: String) : DrillTarget

    /** Spending on entries carrying a tag (ignoring case). */
    data class WithTag(val tag: String) : DrillTarget

    /** Spending on entries with no tag at all. */
    data object Untagged : DrillTarget

    /** Spending at one merchant: [key] is the name in lower case (empty for entries with no merchant). */
    data class AtMerchant(val key: String) : DrillTarget

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
    /** Every spend line in [period] (net of refunds, your share), for the calendar to add up by day. */
    fun spendPoints(period: DatePeriod, zone: TimeZone, currency: Currency): List<app.expenses.core.domain.SpendPoint> =
        q.spendPointsBetween(currency.code, period.startMillis(zone), period.endMillis(zone)).executeAsList().map { app.expenses.core.domain.SpendPoint(it.occurred_at, it.mine_minor) }

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
        val rows = when (target) {
            DrillTarget.All -> q.drillAll(currency.code, from, until, limit).executeAsList().map { Row(it.entry_id, it.kind, it.merchant, it.note, it.occurred_at, it.mine ?: 0L, it.category_id) }
            DrillTarget.Uncategorised -> q.drillUncategorised(currency.code, from, until, limit).executeAsList().map { Row(it.entry_id, it.kind, it.merchant, it.note, it.occurred_at, it.mine ?: 0L, it.category_id) }
            is DrillTarget.InCategory -> {
                val all = categories()
                val ids = listOf(target.id) + all.filter { it.parentId == target.id }.map { it.id }
                q.drillCategories(currency.code, from, until, ids, limit).executeAsList().map { Row(it.entry_id, it.kind, it.merchant, it.note, it.occurred_at, it.mine ?: 0L, it.category_id) }
            }
            DrillTarget.Income -> emptyList()
            is DrillTarget.InAccount -> grouped(currency, from, until, account = target.id)
            is DrillTarget.WithTag -> grouped(currency, from, until, tag = target.tag)
            DrillTarget.Untagged -> grouped(currency, from, until, untagged = true)
            is DrillTarget.AtMerchant -> grouped(currency, from, until, merchantKey = target.key)
        }
        return rows.map { DrillLine(it.id, it.at, it.kind, title(it.kind, it.merchant, it.note), it.category?.let { id -> names[id] ?: id }, money(it.mine)) }
    }

    private fun grouped(currency: Currency, from: Long, until: Long, account: String? = null, tag: String? = null, merchantKey: String? = null, untagged: Boolean = false, limit: Long = 500) =
        q.drillGrouped(currency.code, from, until, account, tag, merchantKey, if (untagged) 1L else 0L, limit).executeAsList().map { Row(it.entry_id, it.kind, it.merchant, it.note, it.occurred_at, it.mine ?: 0L, it.category_id) }

    private data class Row(val id: String, val kind: String, val merchant: String?, val note: String?, val at: Long, val mine: Long, val category: String?)

    /** Your spending in [period] grouped by account, tag or merchant (the category report has its own tree), biggest first. */
    fun groups(group: ReportGroup, period: DatePeriod, zone: TimeZone, currency: Currency): List<ReportMath.GroupRow> {
        val from = period.startMillis(zone); val until = period.endMillis(zone)
        val items: List<Triple<String?, String, Money>> = when (group) {
            ReportGroup.CATEGORY -> emptyList()
            ReportGroup.ACCOUNT -> {
                val names = q.allAccounts().executeAsList().associate { it.id to it.name }
                q.spendByAccount(currency.code, from, until).executeAsList().map { Triple(it.key, names[it.key] ?: "An account that was removed", Money(it.mine ?: 0L, currency)) }
            }
            ReportGroup.TAG -> {
                val tagged = q.spendByTag(currency.code, from, until).executeAsList().map { Triple<String?, String, Money>(it.key, it.key, Money(it.mine ?: 0L, currency)) }
                val untagged = q.spendUntagged(currency.code, from, until).executeAsOne()
                if (untagged != 0L) tagged + Triple(null, "No tag", Money(untagged, currency)) else tagged
            }
            ReportGroup.MERCHANT -> q.spendByMerchant(currency.code, from, until).executeAsList().map { Triple<String?, String, Money>(it.key, it.display?.trim()?.takeIf { d -> d.isNotEmpty() } ?: "No merchant", Money(it.mine ?: 0L, currency)) }
        }
        return ReportMath.groups(items, currency)
    }

    private fun spentByCategory(period: DatePeriod, zone: TimeZone, currency: Currency): Map<String?, Money> =
        ledger.spendByCategory(currency, from = period.startMillis(zone), until = period.endMillis(zone)).mapKeys { it.key?.value }
}
