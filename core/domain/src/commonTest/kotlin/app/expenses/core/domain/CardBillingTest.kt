package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardBillingTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private val terms = CardTerms("c", statementDay = 15, dueDay = 5) // statement on the 15th, due on the 5th of the next month

    @Test
    fun theStatementIsTheLatestOneOnOrBeforeTodayAcrossMonthAndYearEnds() {
        assertEquals(d(2026, 3, 15), CardBilling.statementOnOrBefore(d(2026, 3, 15), 15), "on the day itself")
        assertEquals(d(2026, 3, 15), CardBilling.statementOnOrBefore(d(2026, 3, 31), 15))
        assertEquals(d(2026, 2, 15), CardBilling.statementOnOrBefore(d(2026, 3, 14), 15))
        assertEquals(d(2025, 12, 28), CardBilling.statementOnOrBefore(d(2026, 1, 27), 28), "back across the new year")
        assertEquals(d(2026, 2, 28), CardBilling.statementOnOrBefore(d(2026, 2, 28), 28), "February has a 28th")
    }

    @Test
    fun theDueDateIsTheFirstDueDayAfterTheStatementInTheSameOrTheNextMonth() {
        assertEquals(d(2026, 4, 5), CardBilling.dueDateFor(d(2026, 3, 15), 5), "due day earlier in the month: next month")
        assertEquals(d(2026, 3, 25), CardBilling.dueDateFor(d(2026, 3, 15), 25), "due day later in the month: same month")
        assertEquals(d(2026, 4, 15), CardBilling.dueDateFor(d(2026, 3, 15), 15), "same day number: a month later, never the same day")
        assertEquals(d(2027, 1, 5), CardBilling.dueDateFor(d(2026, 12, 15), 5), "across the year end")
        assertEquals(CardBilling.cycleOn(d(2026, 3, 20), terms), BillingCycle(d(2026, 3, 15), d(2026, 4, 5)))
    }

    @Test
    fun whatRemainsIsTheStatementLessPaymentsSinceNeverNegativeAndTheMinimumNeverExceedsIt() {
        val c = BillingCycle(d(2026, 3, 15), d(2026, 4, 5))
        assertEquals(CardStanding(c, 100_000, 0, 100_000, 5_000), CardBilling.standing(c, 100_000, 0, terms))
        assertEquals(CardStanding(c, 100_000, 60_000, 40_000, 5_000), CardBilling.standing(c, 100_000, 60_000, terms))
        assertEquals(CardStanding(c, 100_000, 98_000, 2_000, 2_000), CardBilling.standing(c, 100_000, 98_000, terms), "minimum capped at what remains")
        assertEquals(0, CardBilling.standing(c, 100_000, 150_000, terms).remaining, "overpaid")
        assertEquals(CardStanding(c, 0, 0, 0, 0), CardBilling.standing(c, -5_000, 0, terms), "a card in credit owes nothing")
        assertEquals(5_001, CardBilling.standing(c, 100_001, 0, terms).minimumDue, "the estimate rounds up so it never understates")
        assertEquals(0, CardBilling.standing(c, 100_000, 0, terms.copy(minDuePercent = 0)).minimumDue)
    }

    @Test
    fun termsRejectDaysThatSomeMonthsLackAndOutOfRangeValues() {
        for (bad in listOf(0, 29, 31)) { assertFailsWith<IllegalArgumentException> { CardTerms("c", bad, 5) }; assertFailsWith<IllegalArgumentException> { CardTerms("c", 5, bad) } }
        assertFailsWith<IllegalArgumentException> { CardTerms("c", 5, 5, minDuePercent = 101) }
        assertFailsWith<IllegalArgumentException> { CardTerms("c", 5, 5, remindDaysBefore = 11) }
        CardTerms("c", 1, 28, 0, 0)
    }

    private val prefs = NotificationPrefs(quietEnabled = false, billHour = 9)
    private fun due(day: LocalDate, daysAhead: Int = 3) = CardDue("c", "HDFC card", day, "₹40,000.00", "₹2,000.00", daysAhead)
    private fun plan(today: LocalDate, sent: Set<String> = emptySet(), minute: Int = 9 * 60, card: CardDue = due(d(2026, 4, 5))) =
        NoticePlanner.plan(prefs, today, minute, emptyList(), emptyList(), sent, listOf(card))

    @Test
    fun aCardRemindsAheadOnceThenOnTheDueDayThenWhenOverdueEachOnlyOnce() {
        assertEquals(emptyList(), plan(d(2026, 4, 1)), "4 days ahead with a 3 day lead: too early")
        val ahead = plan(d(2026, 4, 2)).single()
        assertEquals("HDFC card bill due in 3 days", ahead.title); assertEquals("Pay ₹40,000.00, minimum about ₹2,000.00", ahead.text)
        assertEquals("HDFC card bill due tomorrow", plan(d(2026, 4, 4)).single().title)
        assertEquals(emptyList(), plan(d(2026, 4, 3), sent = setOf(ahead.key)), "the heads-up is sent once")
        assertEquals(emptyList(), plan(d(2026, 4, 4), sent = setOf(ahead.key)), "still once on the next day")
        val today = plan(d(2026, 4, 5), sent = setOf(ahead.key)).single()
        assertEquals("HDFC card bill is due today", today.title); assertEquals(NoticeKind.CARD_DUE, today.kind)
        assertEquals(emptyList(), plan(d(2026, 4, 5), sent = setOf(ahead.key, today.key)))
        assertEquals("HDFC card bill is overdue", plan(d(2026, 4, 9), sent = setOf(ahead.key)).single().title)
        assertEquals(emptyList(), plan(d(2026, 4, 9), sent = setOf(ahead.key, today.key)), "the due-day notice also covers the overdue days")
    }

    @Test
    fun aZeroDayLeadMeansOnlyTheDueDayAndNothingIsSentBeforeTheHourOrDuringQuietHoursOrWhenBillsAreOff() {
        assertEquals(emptyList(), plan(d(2026, 4, 4), card = due(d(2026, 4, 5), 0)))
        assertEquals(1, plan(d(2026, 4, 5), card = due(d(2026, 4, 5), 0)).size)
        assertEquals(emptyList(), plan(d(2026, 4, 5), minute = 8 * 60 + 59))
        assertEquals(emptyList(), NoticePlanner.plan(prefs.copy(quietEnabled = true, quietStartMinute = 8 * 60, quietEndMinute = 10 * 60), d(2026, 4, 5), 9 * 60, emptyList(), emptyList(), emptySet(), listOf(due(d(2026, 4, 5)))))
        assertEquals(emptyList(), NoticePlanner.plan(prefs.copy(bills = false), d(2026, 4, 5), 9 * 60, emptyList(), emptyList(), emptySet(), listOf(due(d(2026, 4, 5)))))
    }

    @Test
    fun theNextBillingCycleGetsItsOwnNoticesBecauseTheKeyCarriesTheDueDate() {
        val first = plan(d(2026, 4, 5)).single().key
        val second = plan(d(2026, 5, 5), sent = setOf(first), card = due(d(2026, 5, 5))).single()
        assertTrue(second.key != first)
    }

    @Test
    fun theWordsForTheAccountsScreen() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "23rd", "28th"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 28).map { CardText.ordinal(it) })
        assertEquals("Statement on the 15th · due on the 5th", CardText.terms(terms))
        assertEquals("5 Apr", CardText.day(d(2026, 4, 5)))
        val c = BillingCycle(d(2026, 3, 15), d(2026, 4, 5))
        val money = { m: Long -> "₹$m" }
        assertEquals("Due 5 Apr · ₹400 left · minimum about ₹20", CardText.status(CardStanding(c, 1000, 600, 400, 20), d(2026, 4, 5), money), "on the due day it is still due")
        assertEquals("Overdue since 5 Apr · ₹400 left · minimum about ₹20", CardText.status(CardStanding(c, 1000, 600, 400, 20), d(2026, 4, 6), money))
        assertEquals("The 15 Mar statement is paid", CardText.status(CardStanding(c, 1000, 1000, 0, 0), d(2026, 3, 20), money))
        assertEquals("Nothing was owed on the 15 Mar statement", CardText.status(CardStanding(c, 0, 0, 0, 0), d(2026, 3, 20), money))
    }
}
