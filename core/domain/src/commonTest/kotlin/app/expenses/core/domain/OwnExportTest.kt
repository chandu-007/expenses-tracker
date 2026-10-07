package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnExportTest {
    @Test
    fun onlyTheExactHeaderOfTheAppsExportMatchesEvenWithSpacesAroundCells() {
        val row = listOf(OwnExport.HEADER, listOf("2026-10-07", "10:00", "Expense", "1.00"))
        assertTrue(OwnExport.matches(row))
        assertTrue(OwnExport.matches(listOf(OwnExport.HEADER.map { " $it " })))
        assertFalse(OwnExport.matches(emptyList()))
        assertFalse(OwnExport.matches(listOf(OwnExport.HEADER.drop(1))), "a bank file with some of the same words")
        assertFalse(OwnExport.matches(listOf(listOf("Date", "Narration", "Debit", "Credit"))))
        assertFalse(OwnExport.matches(listOf(listOf("Account Statement"), OwnExport.HEADER)), "the header must be the first row")
    }
}
