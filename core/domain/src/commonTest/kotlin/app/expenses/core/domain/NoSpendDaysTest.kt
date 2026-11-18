package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoSpendDaysTest {
    /** A month of ten days from epoch day 100, spending as given (minor units; 0 is none). */
    private fun month(vararg spent: Long) = spent.mapIndexed { i, s -> (100L + i) to s }

    @Test
    fun aPastDayWithNothingSpentCountsAndADayWithSpendingDoesNot() {
        val days = month(0, 500, 0, 0, 120, 0, 0, 0, 0, 0)
        assertEquals(listOf(100L, 102L, 103L, 105L), NoSpendDays.of(days, todayEpochDay = 106L, firstEntryEpochDay = 100L))
    }

    @Test
    fun todayAndTheFutureAreLeftAlone() {
        val days = month(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(listOf(100L, 101L, 102L), NoSpendDays.of(days, todayEpochDay = 103L, firstEntryEpochDay = 100L), "today is not over yet")
        assertEquals(emptyList(), NoSpendDays.of(days, todayEpochDay = 100L, firstEntryEpochDay = 100L))
    }

    @Test
    fun daysBeforeTheFirstEntryDoNotCountAndAnEmptyLedgerHasNone() {
        val days = month(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertEquals(listOf(104L, 105L, 106L), NoSpendDays.of(days, todayEpochDay = 107L, firstEntryEpochDay = 104L))
        assertEquals(emptyList(), NoSpendDays.of(days, todayEpochDay = 107L, firstEntryEpochDay = null))
    }

    @Test
    fun aDayThatOnlyHadARefundIsNotCountedAsSpending() {
        assertEquals(listOf(100L), NoSpendDays.of(month(-300, 50), todayEpochDay = 102L, firstEntryEpochDay = 100L))
    }

    @Test
    fun theSummaryIsOneCalmLineWithNoSpendingAmount() {
        assertEquals("6 no-spend days this month", NoSpendDays.summary(6, null))
        assertEquals("1 no-spend day this month", NoSpendDays.summary(1, null))
        assertEquals("3 no-spend days in October 2026", NoSpendDays.summary(3, "October 2026"))
        assertNull(NoSpendDays.summary(0, null))
    }
}
