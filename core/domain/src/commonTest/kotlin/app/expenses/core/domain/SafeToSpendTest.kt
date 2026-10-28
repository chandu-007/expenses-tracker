package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SafeToSpendTest {
    private val today = LocalDate(2026, 10, 28)
    private val end = LocalDate(2026, 11, 1) // 4 days: 28, 29, 30, 31 Oct

    private fun input(
        assets: Long = 5_000_000, cards: Long = 0, obligations: List<Obligation> = emptyList(), setAside: List<SetAside> = emptyList(), on: Boolean = false,
        horizonEnd: LocalDate = end, horizon: Horizon = Horizon.BUDGET_PERIOD,
    ) = SafeToSpendInput(today, horizonEnd, horizon, assets, 2, cards, if (cards > 0) 1 else 0, obligations, setAside, on)

    private fun ob(title: String, minor: Long, due: LocalDate, kind: ObligationKind) = Obligation(title, minor, due, kind)

    @Test
    fun theBalanceLessCardDuesBillsEmisAndRepeatingExpenses() {
        val r = SpendableMath.compute(input(
            assets = 5_000_000, cards = 1_200_000,
            obligations = listOf(
                ob("Electricity", 180_000, LocalDate(2026, 10, 30), ObligationKind.BILL),
                ob("Home loan EMI", 800_000, LocalDate(2026, 10, 31), ObligationKind.EMI),
                ob("Rent", 1_000_000, LocalDate(2026, 10, 31), ObligationKind.REPEATING),
            ),
        ))
        assertEquals(5_000_000 - 1_200_000 - 180_000 - 800_000 - 1_000_000, r.spendable)
        assertEquals(1_820_000, r.spendable)
        assertEquals(listOf(SafeToSpendPart.BALANCE, SafeToSpendPart.CARDS, SafeToSpendPart.BILLS, SafeToSpendPart.EMIS, SafeToSpendPart.REPEATING), r.lines.map { it.part })
        assertEquals(1_200_000, r.total(SafeToSpendPart.CARDS))
        assertEquals(0, r.shortfall)
        assertFalse(r.nothingSpare)
    }

    @Test
    fun emptyPartsAreLeftOutOfTheBreakdown() {
        val r = SpendableMath.compute(input())
        assertEquals(listOf(SafeToSpendPart.BALANCE), r.lines.map { it.part })
        assertEquals(5_000_000, r.spendable)
    }

    @Test
    fun whatIsDueAfterTheHorizonIsNotTakenOffAndAnOverdueBillIs() {
        val r = SpendableMath.compute(input(obligations = listOf(
            ob("Later", 100_000, LocalDate(2026, 11, 1), ObligationKind.BILL), // on the horizon's end day: exclusive, so not counted
            ob("Overdue water", 30_000, LocalDate(2026, 10, 20), ObligationKind.BILL),
        )))
        assertEquals(5_000_000 - 30_000, r.spendable)
        assertEquals(1, r.lines.first { it.part == SafeToSpendPart.BILLS }.count)
    }

    @Test
    fun aBillThatARepeatingItemAlreadyStandsForIsCountedOnce() {
        val r = SpendableMath.compute(input(obligations = listOf(
            ob("Broadband (found)", 79_900, LocalDate(2026, 10, 30), ObligationKind.BILL),
            ob("Broadband", 79_900, LocalDate(2026, 10, 29), ObligationKind.REPEATING), // same amount, a day apart
            ob("Gas (found)", 50_000, LocalDate(2026, 10, 30), ObligationKind.BILL),
            ob("Rent", 50_000, LocalDate(2026, 10, 20), ObligationKind.REPEATING), // same amount but ten days apart: two payments
        )))
        assertEquals(5_000_000 - 79_900 - 50_000 - 50_000, r.spendable)
        assertEquals(1, r.lines.first { it.part == SafeToSpendPart.REPEATING }.count - 1) // rent and broadband
        assertEquals(50_000, r.total(SafeToSpendPart.BILLS))
    }

    @Test
    fun neverBelowZeroItSaysNothingSpareAndHowFarShort() {
        val r = SpendableMath.compute(input(assets = 100_000, cards = 150_000))
        assertEquals(0, r.spendable)
        assertEquals(50_000, r.shortfall)
        assertTrue(r.nothingSpare)
        assertEquals(0, r.perDay)
        assertEquals("Nothing spare until 31 Oct", SafeToSpendText.headline(r, { "₹" + it / 100 }) { "${it.day} Oct" })
        assertEquals("Short by ₹500: what is due is more than what your accounts hold", SafeToSpendText.shortfall(r) { "₹" + it / 100 })
        assertNull(SafeToSpendText.perDay(r) { "x" })
    }

    @Test
    fun perDaySpreadsTheSpendableOverTheDaysLeftTodayCounting() {
        val r = SpendableMath.compute(input(assets = 400_000))
        assertEquals(4, r.daysLeft)
        assertEquals(100_000, r.perDay)
        assertEquals("about ₹1000 a day", SafeToSpendText.perDay(r) { "₹" + it / 100 })
        // the last day: the whole remainder
        val lastDay = SpendableMath.compute(input(assets = 400_000, horizonEnd = LocalDate(2026, 10, 29)))
        assertEquals(1, lastDay.daysLeft)
        assertEquals(400_000, lastDay.perDay)
    }

    @Test
    fun yearlyBillsAreTakenOffOnlyWhenThePersonTurnedItOn() {
        val fund = listOf(SetAside("Insurance", 200_000), SetAside("Road tax", 50_000))
        val off = SpendableMath.compute(input(setAside = fund, on = false))
        assertEquals(5_000_000, off.spendable)
        assertEquals(250_000, off.putAsideIfOn)
        assertFalse(off.putAsideCounted)
        assertNull(off.lines.firstOrNull { it.part == SafeToSpendPart.PUT_ASIDE })
        assertNotNull(SafeToSpendText.setAsideHint(off) { "₹" + it / 100 })
        val on = SpendableMath.compute(input(setAside = fund, on = true))
        assertEquals(5_000_000 - 250_000, on.spendable)
        assertEquals(2, on.lines.first { it.part == SafeToSpendPart.PUT_ASIDE }.count)
        assertNull(SafeToSpendText.setAsideHint(on) { "x" })
    }

    @Test
    fun theBreakdownRowsSayWhatEachLineIs() {
        val r = SpendableMath.compute(input(assets = 5_000_000, cards = 1_000_000, obligations = listOf(ob("Rent", 100_000, today, ObligationKind.REPEATING))))
        val rows = SafeToSpendText.rows(r, { "₹" + it / 100 }) { "${it.day} Oct" }
        assertEquals("₹50000", rows.first { it.part == SafeToSpendPart.BALANCE }.amountText)
        assertEquals("−₹10000", rows.first { it.part == SafeToSpendPart.CARDS }.amountText)
        assertEquals("1 payment waiting or coming until 31 Oct", rows.first { it.part == SafeToSpendPart.REPEATING }.detail)
    }

    @Test
    fun thingsThatCannotBePlacedAreSaidNotSilentlyDropped() {
        val r = SpendableMath.compute(input(), notCounted = 2)
        assertEquals("2 bills or repeating expenses have no due date or amount and are not counted.", SafeToSpendText.notCounted(r))
        assertNull(SafeToSpendText.notCounted(SpendableMath.compute(input())))
    }

    @Test
    fun theThirtyDayHorizonIsNamed() {
        val r = SpendableMath.compute(input(horizonEnd = LocalDate(2026, 11, 27), horizon = Horizon.THIRTY_DAYS))
        assertEquals(30, r.daysLeft)
        assertTrue(SafeToSpendText.horizonWhy(r).contains("30 days"))
        assertTrue(SafeToSpendText.EXPLAIN.startsWith("An estimate"))
    }
}

class SinkingFundsTest {
    private val today = LocalDate(2026, 10, 28)

    @Test
    fun onlyScheduleRoundsOfThreeMonthsOrMoreAreSlow() {
        assertNull(SinkingFunds.cycleMonths(Schedule(RepeatUnit.MONTH, 1, today)))
        assertNull(SinkingFunds.cycleMonths(Schedule(RepeatUnit.MONTH, 2, today)))
        assertEquals(3, SinkingFunds.cycleMonths(Schedule(RepeatUnit.MONTH, 3, today)))
        assertEquals(6, SinkingFunds.cycleMonths(Schedule(RepeatUnit.MONTH, 6, today)))
        assertEquals(12, SinkingFunds.cycleMonths(Schedule(RepeatUnit.YEAR, 1, today)))
        assertNull(SinkingFunds.cycleMonths(Schedule(RepeatUnit.WEEK, 20, today)))
        assertNull(SinkingFunds.cycleMonths(Schedule(RepeatUnit.DAY, 365, today)))
    }

    @Test
    fun monthsUntilRoundsUpAndIsAtLeastOne() {
        assertEquals(1, SinkingFunds.monthsUntil(today, today))
        assertEquals(1, SinkingFunds.monthsUntil(today, LocalDate(2026, 11, 3)))
        assertEquals(1, SinkingFunds.monthsUntil(today, LocalDate(2026, 11, 28)))
        assertEquals(2, SinkingFunds.monthsUntil(today, LocalDate(2026, 11, 29)))
        assertEquals(6, SinkingFunds.monthsUntil(today, LocalDate(2027, 4, 28)))
        assertEquals(7, SinkingFunds.monthsUntil(today, LocalDate(2027, 4, 30)))
        assertEquals(12, SinkingFunds.monthsUntil(today, LocalDate(2027, 10, 28)))
    }

    @Test
    fun theMonthlyAmountIsRoundedUpSoTheMoneyIsThereOnTheDay() {
        // ₹12,000.00 over 6 months: exactly 2,000.00
        assertEquals(200_000, SinkingFunds.perMonth(1_200_000, today, LocalDate(2027, 4, 28)))
        // ₹10,000.00 over 7 months: 1,428.57 rounds up to 1,428.58 (142,858 paise), never down
        assertEquals(142_858, SinkingFunds.perMonth(1_000_000, today, LocalDate(2027, 4, 30)))
        assertTrue(142_858L * 7 >= 1_000_000)
        // one paisa over one month
        assertEquals(1, SinkingFunds.perMonth(1, today, LocalDate(2027, 4, 30)))
    }

    @Test
    fun forItemUsesTheNextDateAndSkipsFastEndedAndAmountlessItems() {
        val yearly = Schedule(RepeatUnit.YEAR, 1, LocalDate(2026, 4, 28)) // next on 28 Apr 2027
        val got = assertNotNull(SinkingFunds.forItem(1_200_000, yearly, today))
        assertEquals(LocalDate(2027, 4, 28), got.due)
        assertEquals(200_000, got.perMonthMinor)
        assertNull(SinkingFunds.forItem(null, yearly, today), "an amount that differs each time cannot be spread")
        assertNull(SinkingFunds.forItem(100, Schedule(RepeatUnit.MONTH, 1, today), today))
        assertNull(SinkingFunds.forItem(100, Schedule(RepeatUnit.YEAR, 1, LocalDate(2020, 1, 1), ScheduleEnd.AfterCount(2)), today), "ended")
        assertEquals("set aside ₹2000 a month", SinkingFunds.text(200_000) { "₹" + it / 100 })
    }

    @Test
    fun quarterlyAndHalfYearlyAreSpreadToo() {
        val quarterly = Schedule(RepeatUnit.MONTH, 3, LocalDate(2026, 8, 15)) // 15 Aug, 15 Nov, ...
        val q = assertNotNull(SinkingFunds.forItem(300_000, quarterly, today))
        assertEquals(LocalDate(2026, 11, 15), q.due)
        assertEquals(300_000, q.perMonthMinor) // under a month away: all of it this month
        val half = Schedule(RepeatUnit.MONTH, 6, LocalDate(2026, 8, 15)) // next 15 Feb 2027
        assertEquals(LocalDate(2027, 2, 15), assertNotNull(SinkingFunds.forItem(1_200_000, half, today)).due)
        assertEquals(300_000, assertNotNull(SinkingFunds.forItem(1_200_000, half, today)).perMonthMinor) // 4 months
    }
}

class PaymentsLeftTest {
    private val today = LocalDate(2026, 10, 28)
    private fun money(m: Long) = "₹" + m / 100
    private fun day(d: LocalDate) = "${d.day}/${d.month.ordinal + 1}/${d.year}"

    @Test
    fun lastDateOfEachKindOfEnd() {
        val start = LocalDate(2026, 1, 31)
        assertNull(Schedule(RepeatUnit.MONTH, 1, start).lastDate())
        assertEquals(LocalDate(2026, 12, 31), Schedule(RepeatUnit.MONTH, 1, start, ScheduleEnd.AfterCount(12)).lastDate())
        assertEquals(LocalDate(2026, 1, 31), Schedule(RepeatUnit.MONTH, 1, start, ScheduleEnd.AfterCount(1)).lastDate())
        // on or before 15 Jun: the last occurrence is the 31st clamp of May
        assertEquals(LocalDate(2026, 5, 31), Schedule(RepeatUnit.MONTH, 1, start, ScheduleEnd.OnOrBefore(LocalDate(2026, 6, 15))).lastDate())
        assertEquals(LocalDate(2026, 1, 31), Schedule(RepeatUnit.MONTH, 1, start, ScheduleEnd.OnOrBefore(start)).lastDate())
        assertEquals(LocalDate(2026, 3, 1), Schedule(RepeatUnit.WEEK, 1, LocalDate(2026, 1, 4), ScheduleEnd.OnOrBefore(LocalDate(2026, 3, 3))).lastDate())
    }

    @Test
    fun countsWhatIsLeftAndSaysWhenTheLastIsAndTheTotal() {
        // 24 monthly payments from 5 Jan 2026; today 28 Oct 2026: 9 are behind (Jan to Sep... and Oct 5), so 14 are left from 5 Nov 2026
        val s = Schedule(RepeatUnit.MONTH, 1, LocalDate(2026, 1, 5), ScheduleEnd.AfterCount(24))
        val handled = (0..9).map { s.dateAt(it)!!.toEpochDays() }.toSet() // January to October's payment were posted
        val left = assertNotNull(PaymentsLeftMath.of(s, today.let { LocalDate(2026, 8, 29) }, handled))
        assertEquals(14, left.count)
        assertEquals(LocalDate(2027, 12, 5), left.last)
        assertEquals("14 payments left · last on 5/12/2027 · ₹350000 still to pay", PaymentsLeftMath.text(left, 2_500_000, ::money, ::day))
    }

    @Test
    fun anOverduePaymentStillWaitingCountsAndAPostedOneDoesNot() {
        val s = Schedule(RepeatUnit.MONTH, 1, LocalDate(2026, 9, 5), ScheduleEnd.AfterCount(4)) // 5 Sep, 5 Oct, 5 Nov, 5 Dec
        val from = LocalDate(2026, 8, 29)
        assertEquals(3, PaymentsLeftMath.of(s, from, setOf(s.dateAt(0)!!.toEpochDays()))!!.count) // 5 Oct is overdue and still counts
        assertEquals(2, PaymentsLeftMath.of(s, from, setOf(s.dateAt(0)!!.toEpochDays(), s.dateAt(1)!!.toEpochDays()))!!.count)
    }

    @Test
    fun oneLeftAnEndDateAndNoFixedAmount() {
        val s = Schedule(RepeatUnit.MONTH, 1, LocalDate(2026, 6, 10), ScheduleEnd.OnOrBefore(LocalDate(2026, 11, 20)))
        val left = assertNotNull(PaymentsLeftMath.of(s, LocalDate(2026, 10, 29), emptySet()))
        assertEquals(1, left.count)
        assertEquals("1 payment left · last on 10/11/2026 · ₹5000 still to pay", PaymentsLeftMath.text(left, 500_000, ::money, ::day))
        assertEquals("1 payment left · last on 10/11/2026", PaymentsLeftMath.text(left, null, ::money, ::day))
    }

    @Test
    fun finishedAndEndlessSchedules() {
        val done = Schedule(RepeatUnit.MONTH, 1, LocalDate(2025, 1, 5), ScheduleEnd.AfterCount(3))
        val left = assertNotNull(PaymentsLeftMath.of(done, today, emptySet()))
        assertEquals(0, left.count)
        assertEquals("All payments made", PaymentsLeftMath.text(left, 100, ::money, ::day))
        assertNull(PaymentsLeftMath.of(Schedule(RepeatUnit.MONTH, 1, today), today, emptySet()), "no end, no EMI view")
    }
}

class ExpectMathTest {
    @Test
    fun whatIsStillToComeNeverBelowZeroNeverMoreThanTheExpense() {
        assertEquals(50_000, ExpectMath.remaining(50_000, 50_000, 0))
        assertEquals(30_000, ExpectMath.remaining(50_000, 50_000, 20_000))
        assertEquals(0, ExpectMath.remaining(50_000, 50_000, 50_000))
        assertEquals(0, ExpectMath.remaining(50_000, 50_000, 60_000))
        assertEquals(10_000, ExpectMath.remaining(80_000, 40_000, 30_000), "expected is capped at the expense that was later edited down")
        assertEquals(20_000, ExpectMath.remaining(20_000, 50_000, 0))
    }

    @Test
    fun theTypedAmountBlankMeansAllMoreThanTheExpenseIsRefused() {
        val parse: (String) -> Long? = { it.toDoubleOrNull()?.let { v -> (v * 100).toLong() } }
        assertEquals(50_000, ExpectMath.expected("", 50_000, parse))
        assertEquals(50_000, ExpectMath.expected("  ", 50_000, parse))
        assertEquals(20_000, ExpectMath.expected("200", 50_000, parse))
        assertEquals(50_000, ExpectMath.expected("500", 50_000, parse))
        assertNull(ExpectMath.expected("500.01", 50_000, parse))
        assertNull(ExpectMath.expected("0", 50_000, parse))
        assertNull(ExpectMath.expected("abc", 50_000, parse))
    }

    @Test
    fun fromTextPrefersAPersonsNameThenFreeText() {
        assertEquals("from Asha", ExpectMath.fromText("Asha", "the office"))
        assertEquals("from the office", ExpectMath.fromText(null, " the office "))
        assertEquals("", ExpectMath.fromText(null, ""))
        assertEquals("", ExpectMath.fromText(" ", null))
    }
}
