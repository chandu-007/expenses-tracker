package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinancialYearExportTest {
    private val inr = Currency.INR
    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val ledger = LedgerStore(db, clock) { 1L }
    private val export = FinancialYearExport(ReportStore(db, ledger))
    private fun at(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds() + 3_600_000

    init { CategoryStore(db, clock) { 1L }; ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr)) }

    private fun spend(id: String, y: Int, m: Int, d: Int, rupees: Long, cat: String) =
        ledger.save(Expense(EntryId(id), at(y, m, d), AccountId("bank"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(cat), Money(rupees * 100, inr)))))

    @Test
    fun theYearRunsAprilToMarchAndTheLastYearIsTheOneBefore() {
        val p = export.yearOf(LocalDate(2026, 2, 10), 0)
        assertEquals(LocalDate(2025, 4, 1), p.start); assertEquals(LocalDate(2026, 4, 1), p.endExclusive); assertEquals("FY 2025-26", export.label(p))
        assertEquals(LocalDate(2024, 4, 1), export.yearOf(LocalDate(2026, 2, 10), 1).start)
        assertEquals(LocalDate(2026, 4, 1), export.yearOf(LocalDate(2026, 4, 1), 0).start, "the first day of the new year is in it")
    }

    @Test
    fun theSummaryHasTotalsCategoriesAndTwelveMonthsAndLeavesOutOtherYears() {
        spend("a", 2025, 4, 5, 1_000, "Food"); spend("b", 2025, 9, 20, 2_000, "Food"); spend("c", 2026, 3, 31, 500, "Transport")
        spend("out1", 2025, 3, 31, 9_999, "Food"); spend("out2", 2026, 4, 1, 9_999, "Food") // the day before and the day after the year
        ledger.save(Income(EntryId("pay"), at(2025, 5, 1), AccountId("bank"), Money(5_000_000, inr), CategoryId("income:salary")))
        val rows = export.rows(export.yearOf(LocalDate(2026, 1, 15), 0), zone, inr)
        fun value(label: String) = rows.first { it[0] == label }
        assertEquals("FY 2025-26: 2025-04-01 to 2026-03-31", rows[0][0])
        assertEquals("50000.00", value("Income")[1]); assertEquals("3500.00", value("Spent")[1]); assertEquals("46500.00", value("Income minus spending")[1])
        assertEquals("3000.00", value("Food")[1]); assertEquals("500.00", value("Transport")[1])
        val months = rows.dropWhile { it[0] != "Month" }.drop(1)
        assertEquals(12, months.size); assertEquals("2025-04", months.first()[0]); assertEquals("2026-03", months.last()[0])
        assertEquals("1000.00", months.first { it[0] == "2025-04" }[1]); assertEquals("2000.00", months.first { it[0] == "2025-09" }[1]); assertEquals("500.00", months.last()[1])
        assertEquals("50000.00", months.first { it[0] == "2025-05" }[2])
        assertEquals(12, months.count { true }, "every month is listed, spending or not")
    }

    @Test
    fun theWorkbookIsAZipWithTheAmountsAsNumbersAndAYearNamedSheet() {
        spend("a", 2025, 6, 1, 100, "Food")
        val bytes = export.xlsx(export.yearOf(LocalDate(2025, 12, 1), 0), zone, inr)
        assertTrue(bytes.size > 100 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())
        val text = bytes.decodeToString()
        assertTrue(text.contains("name=\"FY 2025-26\"") && text.contains("<v>100.00</v>"), "numbers are numbers")
    }
}
