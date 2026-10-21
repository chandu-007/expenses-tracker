package app.expenses.core.storage

import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.CategoryRow
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.Money
import app.expenses.core.domain.toPlainString
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * A summary of one financial year (April to March) as a spreadsheet: what came in, what was spent, and the spending by category and by month. Plain numbers
 * with two decimals so sums and sorting work. It is a summary for your own records and for an accountant; it is not tax advice or a tax computation.
 */
class FinancialYearExport(private val reports: ReportStore) {
    /** The financial year [yearsBack] years before the one containing [today] (0 is the current one). */
    fun yearOf(today: LocalDate, yearsBack: Int): DatePeriod = BudgetPeriod.Yearly().containing(today.minus(yearsBack, DateTimeUnit.YEAR))

    fun label(period: DatePeriod) = "FY ${period.start.year}-${((period.start.year + 1) % 100).toString().padStart(2, '0')}"

    fun rows(period: DatePeriod, zone: TimeZone, currency: Currency): List<List<String>> {
        fun plain(m: Money) = m.toPlainString()
        val spent = reports.spent(period, zone, currency); val income = reports.income(period, zone, currency)
        val last = period.endExclusive.minus(1, DateTimeUnit.DAY)
        val out = mutableListOf<List<String>>()
        out += listOf(label(period) + ": " + period.start + " to " + last, "", "", "")
        out += listOf("Income", plain(income), "", "")
        out += listOf("Spent", plain(spent), "", "")
        out += listOf("Income minus spending", plain(income - spent), "", "")
        out += listOf("", "", "", "")
        out += listOf("Spending by category", "Amount", "Share %", "")
        fun add(row: CategoryRow, indent: String) {
            out += listOf(indent + row.name, plain(row.total), row.share.toString(), "")
            row.children.forEach { add(it, "$indent    ") }
        }
        reports.breakdown(period, zone, currency).forEach { add(it, "") }
        out += listOf("", "", "", "")
        out += listOf("Month", "Spent", "Income", "")
        var month = period.start
        while (month < period.endExclusive) {
            val next = month.plus(1, DateTimeUnit.MONTH)
            val p = DatePeriod(month, minOf(next, period.endExclusive))
            out += listOf(month.year.toString() + "-" + month.monthNumber.toString().padStart(2, '0'), plain(reports.spent(p, zone, currency)), plain(reports.income(p, zone, currency)), "")
            month = next
        }
        return out
    }

    fun xlsx(period: DatePeriod, zone: TimeZone, currency: Currency): ByteArray = XlsxExport.build(rows(period, zone, currency), numericColumns = setOf(1, 2), sheetName = label(period))
}
