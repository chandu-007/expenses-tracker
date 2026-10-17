package app.expenses.facade

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppCoreTest {
    private var now = 1_760_000_000_000L // mid October 2025
    private fun core(): AppCore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ExpensesDb.Schema.create(driver)
        return AppCore(driver, "test", { now++ }, TimeZone.UTC)
    }

    @Test fun aFreshLedgerHasCashAndBankAndTheExpenseAndIncomeCategories() {
        val c = core()
        assertEquals(setOf("Cash", "Bank"), c.accounts().map { it.name }.toSet())
        assertTrue(c.categories(income = false).any { it.id == "Food" } && c.categories(income = false).none { it.id == "income:salary" })
        assertTrue(c.categories(income = true).any { it.id == "income:salary" } && c.categories(income = true).none { it.id == "Food" })
    }

    @Test fun addingAnExpenseShowsInTheListTheMonthTotalAndTheBalance() {
        val c = core()
        c.add(false, "1,250.50", "Food", "cash", "Lunch")
        val row = c.recent(10).single()
        assertEquals("Lunch", row.title); assertEquals("Food", row.categoryName); assertTrue(row.amountText.contains("1,250.50"))
        assertTrue(c.monthSpendText().contains("1,250.50"), c.monthSpendText())
        assertTrue(c.accounts().first { it.id == "cash" }.balanceText.contains("1,250.50"))
    }

    @Test fun anIncomeIsNotSpendingAndBadAmountsAreRefusedWithAReason() {
        val c = core()
        c.add(true, "50000", "income:salary", "bank", "Pay")
        assertTrue(c.monthSpendText().contains("0.00"), c.monthSpendText())
        assertTrue(c.recent(5).single().income)
        assertFailsWith<IllegalArgumentException> { c.add(false, "abc", "Food", "cash", "") }
        assertFailsWith<IllegalArgumentException> { c.add(false, "0", "Food", "cash", "") }
        assertFailsWith<IllegalArgumentException> { c.add(false, "1.234", "Food", "cash", "") }
    }

    @Test fun similarEntriesAreOfferedAndMovedOnlyWhenAskedAndOnlyThoseOfTheRightKind() {
        val c = core()
        c.add(false, "899", "Other", "bank", "iCloud"); c.add(false, "899", "Other", "bank", "Apple iCloud+")
        c.add(false, "300", "Other", "bank", "Netflix")
        val filed = c.add(false, "899", "Bills", "bank", "iCloud")
        val offer = assertNotNull(c.similarTo(filed))
        assertEquals(2, offer.rows.size); assertEquals("Bills", offer.categoryName)
        assertEquals("Other", c.recent(10).first { it.title == "Netflix" }.categoryName, "nothing moved yet")
        assertEquals(2, c.fileUnder(offer.rows.map { it.id }, "Bills"))
        assertEquals(listOf("Bills", "Bills", "Other", "Bills"), c.recent(10).reversed().map { it.categoryName })
        assertNull(c.similarTo(filed), "all are in Bills now")
        val salary = c.add(true, "100", "income:salary", "bank", "Pay")
        assertEquals(0, c.fileUnder(listOf(salary), "Bills"), "an income is never given an expense category")
    }
}
