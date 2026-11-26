package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PaceMathTest {
    private val nov = DatePeriod(LocalDate(2026, 11, 1), LocalDate(2026, 12, 1))

    @Test fun theShareOfThePeriodGoneCountsTodayAsWholeAndStaysInsideThePeriod() {
        assertEquals(1, PaceMath.daysIn(nov, LocalDate(2026, 11, 1)))
        assertEquals(14, PaceMath.daysIn(nov, LocalDate(2026, 11, 14)))
        assertEquals(30, PaceMath.daysIn(nov, LocalDate(2026, 11, 30)))
        assertEquals(30, PaceMath.daysIn(nov, LocalDate(2026, 12, 5)), "after the period: the last day")
        assertEquals(1, PaceMath.daysIn(nov, LocalDate(2026, 10, 20)), "before it: the first")
        assertEquals(14f / 30f, PaceMath.elapsed(nov, LocalDate(2026, 11, 14)))
    }

    @Test fun aSalaryDayPeriodAcrossTwoMonthsIsCountedFromItsOwnStart() {
        val p = DatePeriod(LocalDate(2026, 10, 25), LocalDate(2026, 11, 25))
        assertEquals(1, PaceMath.daysIn(p, LocalDate(2026, 10, 25)))
        assertEquals(10, PaceMath.daysIn(p, LocalDate(2026, 11, 3)))
        assertEquals(31, p.days)
    }

    @Test fun theWordsAtTheirBoundaries() {
        val e = 0.5f // day 15 of 30
        fun lvl(spent: Long, limit: Long = 1000, up: Long = 0) = PaceMath.level(spent, limit, e, 15, 30, up)
        assertEquals(PaceLevel.ON_PACE, lvl(500), "exactly the pace")
        assertEquals(PaceLevel.A_LITTLE_AHEAD, lvl(501), "one past the pace")
        assertEquals(PaceLevel.A_LITTLE_AHEAD, lvl(625), "the edge of a little: 1.25 times")
        assertEquals(PaceLevel.AHEAD, lvl(626))
        assertEquals(PaceLevel.AHEAD, lvl(1000), "the whole limit is spent, not over")
        assertEquals(PaceLevel.OVER, lvl(1001))
        assertEquals(PaceLevel.ON_PACE, lvl(0))
    }

    @Test fun refundsBelowZeroCountAsNothingSpent() {
        assertEquals(PaceLevel.ON_PACE, PaceMath.level(-500, 1000, 0.5f, 15, 30))
    }

    @Test fun theFirstDaysSayNothingYetUnlessBillsAlreadyPassTheLimit() {
        assertEquals(PaceLevel.EARLY, PaceMath.level(900, 1000, 1f / 30, 1, 30), "one day's rent is not a month of overspending")
        assertEquals(PaceLevel.EARLY, PaceMath.level(400, 1000, 2f / 30, 2, 30))
        assertEquals(PaceLevel.AHEAD, PaceMath.level(900, 1000, 1f / 30, 1, 30, upcoming = 200), "the bills still to come pass the limit: the first day is judged then, and 90% on day 1 is ahead")
        assertEquals(PaceLevel.A_LITTLE_AHEAD, PaceMath.level(30, 1000, 1f / 30, 1, 30, upcoming = 980), "a little spent, but the bills still to come pass the limit")
        assertEquals(PaceLevel.ON_PACE, PaceMath.level(100, 1000, 3f / 30, 3, 30), "day 3 is judged")
        assertEquals(PaceLevel.EARLY, PaceMath.level(300, 1000, 2f / 7, 2, 7), "a week is judged from its third day")
        assertEquals(PaceLevel.ON_PACE, PaceMath.level(300, 1000, 3f / 7, 3, 7))
    }

    @Test fun billsStillToComeNeverLetAnUnderPaceLookFine() {
        // 300 spent of 1000 with 40% gone is on pace; with 800 of bills still to come it will pass the limit
        assertEquals(PaceLevel.ON_PACE, PaceMath.level(300, 1000, 0.4f, 12, 30))
        assertEquals(PaceLevel.A_LITTLE_AHEAD, PaceMath.level(300, 1000, 0.4f, 12, 30, upcoming = 800))
        assertEquals(PaceLevel.AHEAD, PaceMath.level(900, 1000, 0.4f, 12, 30, upcoming = 800))
    }

    @Test fun noLimitIsEarlyAndNeverJudged() {
        assertEquals(PaceLevel.EARLY, PaceMath.level(10, 0, 0.5f, 15, 30))
    }

    @Test fun wordsAndSentences() {
        assertEquals("On pace", PaceMath.word(PaceLevel.ON_PACE))
        assertEquals("A little ahead", PaceMath.word(PaceLevel.A_LITTLE_AHEAD))
        assertEquals("Ahead of pace", PaceMath.word(PaceLevel.AHEAD))
        assertEquals("Over by ₹1,655.00", PaceMath.word(PaceLevel.OVER, "₹1,655.00"))
        assertEquals("Over the limit", PaceMath.word(PaceLevel.OVER))
        assertEquals("₹1,565 a day · 16 days to payday", PaceMath.heroLine("₹1,565", 16, "payday"))
        assertEquals("₹1,565 a day · last day before month end", PaceMath.heroLine("₹1,565", 1, "month end"))
        assertEquals("You can keep spending ₹1,565 a day for 16 more days", PaceMath.sentence("₹1,565", 16))
        assertEquals("You can spend ₹900 today", PaceMath.sentence("₹900", 1))
    }

    @Test fun theEndOfThePeriodIsCalledPaydayOnlyForASalaryDayStart() {
        assertEquals("month end", PaceMath.endWord(BudgetPeriod.Monthly(1)))
        assertEquals("payday", PaceMath.endWord(BudgetPeriod.Monthly(25)))
        assertEquals("week end", PaceMath.endWord(BudgetPeriod.Weekly(kotlinx.datetime.DayOfWeek.MONDAY)))
    }

    @Test
    fun theStatusChipHasFourWordsAndEveryLevelFoldsIntoOne() {
        assertEquals("On pace", PaceMath.statusWord(PaceLevel.ON_PACE)); assertEquals("On pace", PaceMath.statusWord(PaceLevel.EARLY))
        assertEquals("Well under", PaceMath.statusWord(PaceLevel.ON_PACE, wellUnder = true))
        assertEquals("A little ahead", PaceMath.statusWord(PaceLevel.A_LITTLE_AHEAD)); assertEquals("A little ahead", PaceMath.statusWord(PaceLevel.AHEAD))
        assertEquals("Over by ₹1,655", PaceMath.statusWord(PaceLevel.OVER, overBy = "₹1,655")); assertEquals("Over the limit", PaceMath.statusWord(PaceLevel.OVER))
    }

    @Test
    fun wellUnderNeedsTheFirstTenthOfThePeriodAndLessThanSixtyPercentOfAnEvenPace() {
        assertEquals(true, PaceMath.isWellUnder(0.2f, 0.5f)); assertEquals(true, PaceMath.isWellUnder(0.3f, 0.5f))
        assertEquals(false, PaceMath.isWellUnder(0.31f, 0.5f)); assertEquals(false, PaceMath.isWellUnder(0f, 0.05f), "too early in the period to say")
    }
}
