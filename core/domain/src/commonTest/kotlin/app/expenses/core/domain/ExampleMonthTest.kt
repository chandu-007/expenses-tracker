package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExampleMonthTest {
    @Test
    fun theMonthCardTotalIsTheSpendingRowsAddedUpAndTheLineAgreesWithIt() {
        val spending = ExampleMonth.rows.filter { !it.income }
        assertEquals(spending.sumOf { it.minor }, ExampleMonth.spentMinor)
        assertEquals(ExampleMonth.rows.filter { it.income }.sumOf { it.minor }, ExampleMonth.incomeMinor)
        assertEquals(ExampleMonth.LINE_DAYS, ExampleMonth.dailyMinor.size)
        assertEquals(ExampleMonth.spentMinor, ExampleMonth.dailyMinor.sum(), "the 14-day line shows exactly the spending of the rows")
        assertTrue(ExampleMonth.rows.all { it.daysAgo in 0 until ExampleMonth.LINE_DAYS }, "every row falls inside the days the line covers")
    }

    @Test
    fun theRowsMakeABelievableMonthNewestFirst() {
        val rows = ExampleMonth.rows
        assertTrue(rows.size in 5..8)
        assertEquals(rows.sortedBy { it.daysAgo }, rows, "newest first, like Home")
        assertTrue(rows.all { it.merchant.isNotBlank() && it.categoryName.isNotBlank() && it.minor > 0 })
        assertEquals(rows.size, ExampleMonth.merchants.toSet().size, "no merchant twice")
        assertEquals(1, rows.count { it.income }, "one salary")
        assertTrue(ExampleMonth.incomeMinor > ExampleMonth.spentMinor, "a month that adds up: the salary is more than the spending")
        assertTrue(ExampleMonth.spentMinor in 500_000L..5_000_000L, "a plausible figure in rupees, not paise or lakhs")
        assertTrue(rows.first { it.merchant == "Chai" }.minor < rows.first { it.merchant == "Groceries" }.minor)
    }

    @Test
    fun anExampleRowIsPlainDisplayDataBuiltFromTextAndNumbersOnly() {
        // no id, account, kind or currency: nothing here can be handed to a store as an entry, and the file imports nothing from the ledger package
        val row = ExampleMonth.rows.first()
        assertEquals(ExampleRow(row.merchant, row.categoryName, row.minor, row.daysAgo, row.income), row)
        assertEquals(row, row.copy())
    }
}
