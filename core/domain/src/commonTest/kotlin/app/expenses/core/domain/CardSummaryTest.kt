package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Figures are invented. */
class CardSummaryTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private val terms = CardTerms("c", statementDay = 15, dueDay = 5, minDuePercent = 5)
    private val cycle = CardBilling.cycleOn(d(2026, 3, 20), terms) // statement 15 Mar, due 5 Apr
    private fun money(m: Long) = "R${m / 100}"
    private fun summary(owedAt: Long, owedNow: Long, paid: Long, limit: Long?, today: LocalDate = d(2026, 3, 20)) =
        CardSummaries.of(CardBilling.standing(cycle, owedAt, paid, terms), owedAt, owedNow, limit, today)

    @Test
    fun chargesSinceTheStatementAreTheGrowthInWhatIsOwedPlusWhatWasPaidMeanwhile() {
        val s = summary(owedAt = 100_000, owedNow = 130_000, paid = 0, limit = null)
        assertEquals(30_000, s.spentThisCycle)
        assertEquals(d(2026, 3, 16), s.cycleStart); assertEquals(d(2026, 4, 15), s.nextStatement)
        // 400 paid in and 250 charged: the owing fell by 150, the charges are still 250
        assertEquals(25_000, summary(owedAt = 100_000, owedNow = 85_000, paid = 40_000, limit = null).spentThisCycle)
        assertEquals(0, summary(owedAt = 100_000, owedNow = 100_000, paid = 0, limit = null).spentThisCycle)
        assertEquals(0, summary(owedAt = 100_000, owedNow = 60_000, paid = 40_000, limit = null).spentThisCycle, "only a payment")
    }

    @Test
    fun aCardInCreditAtTheStatementStillCountsItsChargesFromThatCredit() {
        // 50 in credit at the cut (-5_000), 120 charged since: owes 70 now
        assertEquals(12_000, summary(owedAt = -5_000, owedNow = 7_000, paid = 0, limit = null).spentThisCycle)
    }

    @Test
    fun theLimitUseIsWhatIsOwedTodayAgainstTheLimitAndNeverNegative() {
        val s = summary(100_000, 130_000, 0, limit = 400_000)
        assertEquals(32, s.usedPercent); assertEquals(270_000, s.limitLeft); assertEquals(0.325f, s.limitFraction)
        assertNull(summary(100_000, 130_000, 0, limit = null).usedPercent)
        assertNull(summary(100_000, 130_000, 0, limit = null).limitFraction)
        assertNull(summary(100_000, 130_000, 0, limit = 0).limit, "a zero limit is no limit")
        val over = summary(100_000, 450_000, 0, limit = 400_000)
        assertEquals(112, over.usedPercent); assertEquals(0, over.limitLeft); assertEquals(1f, over.limitFraction, "the bar stops at full")
        val credit = summary(-5_000, -2_000, 0, limit = 400_000)
        assertEquals(0, credit.usedPercent); assertEquals(0, credit.owed); assertEquals(400_000, credit.limitLeft)
        assertEquals(1000, summary(0, 90_000_000, 0, limit = 100_000).usedPercent, "capped")
    }

    @Test
    fun daysToDueCountsFromTodayAndGoesNegativeOncePast() {
        assertEquals(16, summary(100_000, 100_000, 0, null, today = d(2026, 3, 20)).daysToDue)
        assertEquals(0, summary(100_000, 100_000, 0, null, today = d(2026, 4, 5)).daysToDue)
        assertEquals(-3, summary(100_000, 100_000, 0, null, today = d(2026, 4, 8)).daysToDue)
    }

    @Test
    fun theWordsSayWhatWasSpentWhatIsUsedAndWhatIsDue() {
        val s = summary(100_000, 130_000, 0, limit = 400_000)
        assertEquals("R300 spent since the 15 Mar statement", CardSummaryText.cycle(s, ::money))
        assertEquals("Nothing spent since the 15 Mar statement", CardSummaryText.cycle(summary(100_000, 100_000, 0, null), ::money))
        assertEquals("R1300 of R4000 used · 32% · R2700 left", CardSummaryText.limit(s, ::money))
        assertNull(CardSummaryText.limit(summary(100_000, 130_000, 0, null), ::money))
        assertEquals("R4500 of R4000 used · 112% · over the limit by R500", CardSummaryText.limit(summary(100_000, 450_000, 0, 400_000), ::money))
        assertEquals("R4000 of R4000 used · 100% · at the limit", CardSummaryText.limit(summary(100_000, 400_000, 0, 400_000), ::money))
        assertEquals("R1000 left to pay · minimum about R50 · due 5 Apr (in 16 days)", CardSummaryText.due(s, ::money))
    }

    @Test
    fun theDueLineHandlesPaidNothingOwedTodayTomorrowAndOverdue() {
        assertEquals("The 15 Mar statement is paid", CardSummaryText.due(summary(100_000, 20_000, 100_000, null), ::money))
        assertEquals("Nothing was owed on the 15 Mar statement", CardSummaryText.due(summary(0, 5_000, 0, null), ::money))
        assertEquals("R1000 left to pay · minimum about R50 · due 5 Apr (today)", CardSummaryText.due(summary(100_000, 100_000, 0, null, d(2026, 4, 5)), ::money))
        assertEquals("R1000 left to pay · minimum about R50 · due 5 Apr (tomorrow)", CardSummaryText.due(summary(100_000, 100_000, 0, null, d(2026, 4, 4)), ::money))
        assertEquals("R1000 left to pay · minimum about R50 · was due 5 Apr (3 days overdue)", CardSummaryText.due(summary(100_000, 100_000, 0, null, d(2026, 4, 8)), ::money))
        assertEquals("R1000 left to pay · was due 5 Apr (1 day overdue)", CardSummaryText.due(CardSummaries.of(CardBilling.standing(cycle, 100_000, 0, terms.copy(minDuePercent = 0)), 100_000, 100_000, null, d(2026, 4, 6)), ::money))
    }
}
