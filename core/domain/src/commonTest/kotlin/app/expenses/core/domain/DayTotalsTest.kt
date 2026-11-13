package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayTotalsTest {
    private fun e(day: Long, kind: String, minor: Long, currency: String = "INR") = DayEntry(day, kind, minor, currency)

    @Test
    fun aDayTotalIsWhatWasSpentThatDay() {
        val t = DayTotals.of(listOf(e(10, "EXPENSE", 41_100), e(10, "EXPENSE", 70_100), e(9, "EXPENSE", 43_600), e(8, "EXPENSE", 100)), "INR", endReached = true)
        assertEquals(mapOf(10L to 111_200L, 9L to 43_600L, 8L to 100L), t)
    }

    @Test
    fun incomeRefundsTransfersAndSettlementsAreNotSpending() {
        val t = DayTotals.of(listOf(e(10, "INCOME", 5_000_000), e(10, "REFUND", 1_000), e(10, "TRANSFER", 2_000), e(10, "SETTLEMENT", 3_000), e(10, "EXPENSE", 500), e(9, "INCOME", 100)), "INR", endReached = true)
        assertEquals(mapOf(10L to 500L), t)
        assertTrue(9L !in t, "a day with only income has no total")
    }

    @Test
    fun anEntryInAnotherCurrencyIsLeftOutNotAddedAcross() {
        val t = DayTotals.of(listOf(e(10, "EXPENSE", 1_000), e(10, "EXPENSE", 99_900, "USD"), e(9, "EXPENSE", 5_000, "USD")), "INR", endReached = true)
        assertEquals(mapOf(10L to 1_000L), t)
    }

    @Test
    fun theOldestLoadedDayHasNoTotalUntilTheEndOfTheListIsReached() {
        val rows = listOf(e(10, "EXPENSE", 100), e(9, "EXPENSE", 200), e(9, "EXPENSE", 300))
        assertEquals(mapOf(10L to 100L), DayTotals.of(rows, "INR", endReached = false))
        assertEquals(mapOf(10L to 100L, 9L to 500L), DayTotals.of(rows, "INR", endReached = true))
    }

    @Test
    fun anEmptyListHasNoTotals() {
        assertEquals(emptyMap(), DayTotals.of(emptyList(), "INR", endReached = true))
    }
}
