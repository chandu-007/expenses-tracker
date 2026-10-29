package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The week line / bills banner counts a card by its billing cycle's due date, which rolls into the next month (or is already past) when the due day is early in the month. The device test
 * `AlertsUiTest.theWeekLineAlsoCountsRepeatingItems...` builds the card's tracked bill with the rule below instead of with today's month; this proves, for every day of 2026 to 2028
 * (all month ends, the 29th, 30th and 31st, and a leap year), that the rule is the product's own: the app's [CardBilling.cycleOn] gives exactly that date, and the card is always inside the banner's window.
 */
class CardDueRolloverTest {
    /** The test's rule, written without CardBilling: the first date after the 1st of today's month that falls on the due day. */
    private fun ruleOfTheTest(today: LocalDate, dueDay: Int): LocalDate =
        generateSequence(LocalDate(today.year, today.month, 1).plus(1, DateTimeUnit.DAY)) { it.plus(1, DateTimeUnit.DAY) }.first { it.day == dueDay }

    @Test
    fun theTestsDueDateIsTheProductsOnEveryDayOfThreeYears() {
        var d = LocalDate(2026, 1, 1)
        var checked = 0
        while (d <= LocalDate(2028, 12, 31)) {
            val dueDay = d.plus(3, DateTimeUnit.DAY).day.coerceAtMost(28) // as the test chooses it
            val product = CardBilling.cycleOn(d, CardTerms("amex", statementDay = 1, dueDay = dueDay)).dueDate
            assertEquals(ruleOfTheTest(d, dueDay), product, "today $d, due day $dueDay")
            // the banner counts a card due before the end of the 8 days that start today (a past due date counts as overdue)
            assertTrue(product < d.plus(8, DateTimeUnit.DAY), "today $d: due $product is outside the banner's window")
            checked++
            d = d.plus(1, DateTimeUnit.DAY)
        }
        assertEquals(1096, checked)
    }

    @Test
    fun thePreviousTestExpectationWasWrongAtMonthEnd() {
        // 29 Oct 2026: the due day is the 1st, which falls due on 1 Nov, not on the 1st of October (the date the test used to build)
        val today = LocalDate(2026, 10, 29)
        assertEquals(LocalDate(2026, 11, 1), CardBilling.cycleOn(today, CardTerms("amex", 1, 1)).dueDate)
        // 30 Oct: due day 2 is 2 Oct, already past: overdue, which the banner also counts
        assertEquals(LocalDate(2026, 10, 2), CardBilling.cycleOn(LocalDate(2026, 10, 30), CardTerms("amex", 1, 2)).dueDate)
    }
}
