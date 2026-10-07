package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/**
 * One line of a category breakdown. [total] includes the subcategories in [children]. [share] is the whole percent of all positive
 * spending, rounded down (0 for a category whose refunds exceed its spending). A null [categoryId] is uncategorised spending.
 */
data class CategoryRow(val categoryId: String?, val name: String, val total: Money, val share: Int, val children: List<CategoryRow> = emptyList())

object ReportMath {
    const val UNCATEGORISED = "Uncategorised"

    /**
     * Groups spending by top-level category, largest first, with subcategories nested inside their parent. Spending on a category that
     * is no longer in [categories] (or whose parent is not) is shown under its own id rather than dropped, so rows always add up to
     * the full amount. Rows with a total of exactly zero are left out. Rows for different currencies must not be mixed.
     */
    fun breakdown(spent: Map<String?, Money>, categories: List<Category>, currency: Currency): List<CategoryRow> {
        val byId = categories.associateBy { it.id }
        fun topOf(id: String): String = byId[id]?.parentId?.takeIf { it in byId } ?: id
        val own = spent.filterValues { it.minorUnits != 0L }
        val ownByCategory = own.entries.filter { it.key != null }.associate { it.key!! to it.value }
        val groups = ownByCategory.keys.groupBy(::topOf)
        val positiveTotal = own.values.filter { it.minorUnits > 0 }.fold(0L) { a, m -> a + m.minorUnits }

        fun share(m: Money) = if (m.minorUnits <= 0 || positiveTotal <= 0) 0 else (m.minorUnits.toDouble() * 100.0 / positiveTotal).toInt().coerceAtMost(100)
        fun nameOf(id: String) = byId[id]?.name ?: id
        fun sum(ids: List<String>) = ids.fold(Money.zero(currency)) { a, id -> a + ownByCategory.getValue(id) }

        val rows = groups.map { (top, ids) ->
            val total = sum(ids)
            val children = ids.filter { it != top }.map { id -> CategoryRow(id, nameOf(id), ownByCategory.getValue(id), share(ownByCategory.getValue(id))) }
                .sortedByDescending { it.total.minorUnits }
            CategoryRow(top, nameOf(top), total, share(total), children)
        }.toMutableList()
        own[null]?.let { rows += CategoryRow(null, UNCATEGORISED, it, share(it)) }
        return rows.filter { it.total.minorUnits != 0L || it.children.isNotEmpty() }.sortedWith(compareByDescending<CategoryRow> { it.total.minorUnits }.thenBy { it.name.lowercase() })
    }

    /** Whole percent change from [previous] to [current], rounded to nearest, or null when there is nothing to compare with (previous is zero or negative). */
    fun percentChange(current: Money, previous: Money): Int? {
        if (previous.minorUnits <= 0) return null
        val delta = (current.minorUnits - previous.minorUnits).toDouble() * 100.0 / previous.minorUnits
        return delta.roundToInt()
    }

    /**
     * The budget period [offset] steps from the one containing [today]: 0 is the current one, -1 the one before, and so on. Positive
     * offsets are the future and are allowed (reports never show them, but the stepping is the same).
     */
    fun periodAt(period: BudgetPeriod, today: LocalDate, offset: Int): DatePeriod {
        var p = period.containing(today)
        repeat(kotlin.math.abs(offset)) { p = if (offset < 0) period.containing(p.start.minus(1, DateTimeUnit.DAY)) else period.containing(p.endExclusive) }
        return p
    }

    /** The calendar year [year] shifted by [offset] years from the one containing [today]. */
    fun yearAt(today: LocalDate, offset: Int): DatePeriod = LocalDate(today.year + offset, 1, 1).let { DatePeriod(it, LocalDate(it.year + 1, 1, 1)) }

    /** The twelve calendar months of [year], in order. */
    fun monthsOf(year: Int): List<DatePeriod> = (1..12).map { m ->
        val first = LocalDate(year, m, 1)
        DatePeriod(first, first.plus(1, DateTimeUnit.MONTH))
    }

    /**
     * The calendar months a range touches, each cut to the range (the first and last may be partial), at most the last [MAX_BARS] of them.
     * Together they cover the range exactly once.
     */
    fun monthBars(range: DatePeriod): List<DatePeriod> {
        val bars = ArrayList<DatePeriod>()
        var start = range.start
        while (start < range.endExclusive) {
            val monthEnd = LocalDate(start.year, start.monthNumber, 1).plus(1, DateTimeUnit.MONTH)
            val end = minOf(monthEnd, range.endExclusive)
            bars += DatePeriod(start, end)
            start = end
        }
        return bars.takeLast(MAX_BARS)
    }

    const val MAX_BARS = 24

    /** One row of a report grouped by account, tag or merchant. [key] identifies it for drilling in (null for "Untagged"). */
    data class GroupRow(val key: String?, val name: String, val total: Money, val share: Int)

    /**
     * Ranks grouped totals, biggest first (ties by name, ignoring case), with each row's share of the positive total as a whole percent.
     * Groups that net to zero or less (a refunded purchase) stay listed, with no share.
     */
    fun groups(items: List<Triple<String?, String, Money>>, currency: Currency): List<GroupRow> {
        val positive = items.sumOf { it.third.minorUnits.coerceAtLeast(0) }
        return items.sortedWith(compareByDescending<Triple<String?, String, Money>> { it.third.minorUnits }.thenBy { it.second.lowercase() })
            .map { (k, n, m) -> GroupRow(k, n, m, if (m.minorUnits <= 0 || positive <= 0) 0 else (m.minorUnits * 100 / positive).toInt().coerceAtMost(100)) }
            .also { rows -> require(rows.all { it.total.currency == currency }) { "Groups must all be in $currency" } }
    }
}
