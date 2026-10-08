package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeeklyRecapTest {
    private val inr = Currency.INR
    private fun m(minor: Long) = Money(minor, inr)
    private val fmt = { x: Money -> "₹" + (x.minorUnits / 100) }
    private fun d(y: Int, mo: Int, day: Int) = LocalDate(y, mo, day)
    private val week = DatePeriod(d(2026, 9, 28), d(2026, 10, 5))

    @Test
    fun theRecappedWeekIsTheFinishedMondayToSundayBeforeTheCurrentOneOnEveryDayOfThisWeek() {
        // 5 Oct 2026 is a Monday
        for (day in 5..11) assertEquals(week, WeeklyRecap.recappedWeek(d(2026, 10, day)), "Oct $day")
        assertEquals(DatePeriod(d(2026, 10, 5), d(2026, 10, 12)), WeeklyRecap.recappedWeek(d(2026, 10, 12)), "the next Monday moves on")
        assertEquals(DatePeriod(d(2026, 10, 26), d(2026, 11, 2)), WeeklyRecap.recappedWeek(d(2026, 11, 3)), "across a month end")
        assertEquals(DatePeriod(d(2026, 12, 28), d(2027, 1, 4)), WeeklyRecap.recappedWeek(d(2027, 1, 4)), "across a year end")
        assertEquals(d(2026, 9, 21), WeeklyRecap.previousWeek(week).start); assertEquals(d(2026, 9, 28), WeeklyRecap.previousWeek(week).endExclusive)
    }

    @Test
    fun theTextSaysHowMuchAgainstTheWeekBeforeAndWhereMostWent() {
        assertEquals("Last week: ₹1200 spent" to "₹300 less than the week before (-20%). Most on Food (42%).", WeeklyRecap.compose(week, m(120_000), m(150_000), "Food" to 42, fmt))
        assertEquals("Last week: ₹1500 spent" to "₹300 more than the week before (+25%). Most on Rent (80%).", WeeklyRecap.compose(week, m(150_000), m(120_000), "Rent" to 80, fmt))
        assertEquals("Last week: ₹1200 spent" to "The same as the week before. Most on Food (100%).", WeeklyRecap.compose(week, m(120_000), m(120_000), "Food" to 100, fmt))
        assertEquals("Last week: ₹1200 spent" to "Most on Food (100%).", WeeklyRecap.compose(week, m(120_000), m(0), "Food" to 100, fmt), "nothing before: no comparison")
        assertEquals("Last week: ₹1200 spent" to "Open the app for the details.", WeeklyRecap.compose(week, m(120_000), m(0), null, fmt))
    }

    @Test
    fun aQuietWeekAfterABusyOneIsWorthSayingAndTwoEmptyWeeksAreNot() {
        assertEquals("Last week you spent nothing" to "₹1500 less than the week before (-100%).", WeeklyRecap.compose(week, m(0), m(150_000), "Food" to 100, fmt))
        assertNull(WeeklyRecap.compose(week, m(0), m(0), null, fmt))
    }

    private val prefs = NotificationPrefs(quietEnabled = false, billHour = 9, recap = true)
    private val due = RecapDue(week.start, "Last week: ₹1200 spent", "text")
    private fun plan(p: NotificationPrefs = prefs, minute: Int = 9 * 60, sent: Set<String> = emptySet(), recap: RecapDue? = due) =
        NoticePlanner.plan(p, d(2026, 10, 7), minute, emptyList(), emptyList(), sent, emptyList(), recap)

    @Test
    fun theRecapGoesOutOnceOnAfterTheReminderHourOnlyWhenSwitchedOnAndNotAgainOnceSent() {
        val n = plan().single()
        assertEquals(NoticeKind.WEEKLY_RECAP, n.kind); assertEquals(WeeklyRecap.key(week.start), n.key); assertEquals("Last week: ₹1200 spent", n.title)
        assertEquals(emptyList(), plan(minute = 9 * 60 - 1), "not before the hour")
        assertEquals(emptyList(), plan(p = prefs.copy(recap = false)), "off by default and when off")
        assertEquals(emptyList(), plan(sent = setOf(n.key)), "once")
        assertEquals(emptyList(), plan(recap = null))
        assertEquals(false, NotificationPrefs().recap, "the default is off")
        assertEquals(emptyList(), plan(p = prefs.copy(quietEnabled = true, quietStartMinute = 8 * 60, quietEndMinute = 10 * 60)), "held back in quiet hours")
    }

    @Test
    fun eachWeekHasItsOwnKey() {
        assertEquals(true, WeeklyRecap.key(d(2026, 9, 28)) != WeeklyRecap.key(d(2026, 10, 5)))
    }
}
