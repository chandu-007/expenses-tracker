package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsTest {
    private val today = LocalDate(2026, 10, 7)
    private val periodStart = LocalDate(2026, 10, 1)
    private fun at(h: Int, m: Int = 0) = h * 60 + m
    private fun bill(id: String, date: LocalDate = today) = BillDue(id, id, "₹100.00", date)
    private fun plan(
        prefs: NotificationPrefs = NotificationPrefs(quietEnabled = false),
        minute: Int = at(12),
        bills: List<BillDue> = emptyList(),
        budgets: List<BudgetStanding> = emptyList(),
        sent: Set<String> = emptySet(),
    ) = NoticePlanner.plan(prefs, today, minute, bills, budgets, sent)
    private fun standing(state: BudgetState, scope: String? = null) = BudgetStanding(scope, scope ?: "Everything", state, "₹100.00 left", periodStart)

    // ---- quiet hours ----

    @Test
    fun quietHoursThatWrapPastMidnightCoverBothSidesAndTheEndIsExclusive() {
        val p = NotificationPrefs() // 22:00 to 07:00
        assertTrue(p.isQuiet(at(22)))
        assertTrue(p.isQuiet(at(23, 59)))
        assertTrue(p.isQuiet(at(0)))
        assertTrue(p.isQuiet(at(6, 59)))
        assertFalse(p.isQuiet(at(7)), "07:00 is the first minute notifications are allowed")
        assertFalse(p.isQuiet(at(21, 59)))
        assertFalse(p.isQuiet(at(12)))
    }

    @Test
    fun quietHoursWithinOneDayAndTheOffCases() {
        val day = NotificationPrefs(quietStartMinute = at(13), quietEndMinute = at(14))
        assertTrue(day.isQuiet(at(13, 30)))
        assertFalse(day.isQuiet(at(14)))
        assertFalse(NotificationPrefs(quietEnabled = false).isQuiet(at(23)), "switched off")
        assertFalse(NotificationPrefs(quietStartMinute = at(8), quietEndMinute = at(8)).isQuiet(at(8)), "equal start and end is no quiet period")
    }

    @Test
    fun invalidPreferencesAreRejected() {
        assertFailsWith<IllegalArgumentException> { NotificationPrefs(quietStartMinute = 1440) }
        assertFailsWith<IllegalArgumentException> { NotificationPrefs(quietEndMinute = -1) }
        assertFailsWith<IllegalArgumentException> { NotificationPrefs(billHour = 24) }
    }

    // ---- bills ----

    @Test
    fun aBillDueTodayRemindsOnceFromTheReminderHourAndNotBefore() {
        val b = listOf(bill("rent"))
        assertTrue(plan(minute = at(8, 59), bills = b).isEmpty(), "before 9:00")
        val n = plan(minute = at(9), bills = b).single()
        assertEquals(NoticeKind.BILL_DUE, n.kind)
        assertEquals("rent due today", n.title)
        assertTrue(plan(minute = at(9), bills = b, sent = setOf(n.key)).isEmpty(), "never twice")
    }

    @Test
    fun overdueBillsRemindOnceEachAndFutureBillsNot() {
        val n = plan(bills = listOf(bill("a", today), bill("b", LocalDate(2026, 10, 3)), bill("c", LocalDate(2026, 10, 8))))
        assertEquals(listOf("b overdue", "a due today"), n.map { it.title }, "oldest first, tomorrow's bill is not mentioned")
    }

    @Test
    fun theSameBillOnTwoDatesAreTwoReminders() {
        val keys = plan(bills = listOf(bill("rent", LocalDate(2026, 9, 7)), bill("rent", today))).map { it.key }
        assertEquals(2, keys.toSet().size)
    }

    // ---- budgets ----

    @Test
    fun crossingIntoNearAndThenOverEachNotifiesOnce() {
        val near = plan(budgets = listOf(standing(BudgetState.NEAR))).single()
        assertEquals(NoticeKind.BUDGET_NEAR, near.kind)
        assertTrue(plan(budgets = listOf(standing(BudgetState.NEAR)), sent = setOf(near.key)).isEmpty())
        val over = plan(budgets = listOf(standing(BudgetState.OVER)), sent = setOf(near.key)).single()
        assertEquals(NoticeKind.BUDGET_OVER, over.kind)
        assertTrue(plan(budgets = listOf(standing(BudgetState.OVER)), sent = setOf(near.key, over.key)).isEmpty())
    }

    @Test
    fun jumpingStraightToOverSendsOnlyOverAndNearIsNotSentAfterwards() {
        val over = plan(budgets = listOf(standing(BudgetState.OVER))).single()
        assertEquals(NoticeKind.BUDGET_OVER, over.kind)
        // spending then drops back into the near zone (a refund): no "near" notice after "over" in the same period
        assertTrue(plan(budgets = listOf(standing(BudgetState.NEAR)), sent = setOf(over.key)).isEmpty())
    }

    @Test
    fun aNewPeriodNotifiesAgainAndEachBudgetIsIndependent() {
        val old = plan(budgets = listOf(standing(BudgetState.OVER))).single().key
        val next = BudgetStanding(null, "Everything", BudgetState.OVER, "x", LocalDate(2026, 11, 1))
        assertEquals(1, NoticePlanner.plan(NotificationPrefs(quietEnabled = false), LocalDate(2026, 11, 7), at(12), emptyList(), listOf(next), setOf(old)).size)
        assertEquals(2, plan(budgets = listOf(standing(BudgetState.NEAR, "Food"), standing(BudgetState.NEAR, "Fun"))).size)
        assertTrue(plan(budgets = listOf(standing(BudgetState.OK))).isEmpty())
    }

    // ---- switches and quiet hours ----

    @Test
    fun switchedOffTypesAndQuietHoursSendNothing() {
        val bills = listOf(bill("rent")); val budgets = listOf(standing(BudgetState.OVER))
        assertEquals(1, plan(prefs = NotificationPrefs(bills = false, quietEnabled = false), bills = bills, budgets = budgets).size)
        assertEquals(1, plan(prefs = NotificationPrefs(budgets = false, quietEnabled = false), bills = bills, budgets = budgets).size)
        assertTrue(plan(prefs = NotificationPrefs(bills = false, budgets = false, quietEnabled = false), bills = bills, budgets = budgets).isEmpty())
        assertTrue(plan(prefs = NotificationPrefs(), minute = at(23), bills = bills, budgets = budgets).isEmpty(), "quiet hours")
        assertEquals(1, plan(prefs = NotificationPrefs(), minute = at(7, 0), bills = bills, budgets = budgets).size, "at 07:00 quiet ends and the budget alert goes out; the bill waits for 09:00")
        assertEquals(2, plan(prefs = NotificationPrefs(), minute = at(9, 0), bills = bills, budgets = budgets).size)
    }

    // ---- bills found in messages, and bills being tracked ----

    private fun found(id: String) = FoundBill(id, "HDFC Bank credit card", "₹12,345.00", "due 25 Oct")
    private fun tracked(id: String, date: LocalDate) = TrackedBill(id, "BESCOM electricity bill", "₹1,842.50", date)
    private fun planBills(found: List<FoundBill> = emptyList(), trackedBills: List<TrackedBill> = emptyList(), minute: Int = at(12), sent: Set<String> = emptySet(), prefs: NotificationPrefs = NotificationPrefs(quietEnabled = false)) =
        NoticePlanner.plan(prefs, today, minute, emptyList(), emptyList(), sent, foundBills = found, trackedBills = trackedBills)

    @Test
    fun aBillFoundInAMessageIsToldAboutOnceAtOnceEvenBeforeTheReminderHour() {
        val n = planBills(listOf(found("b1")), minute = at(7, 30)).single()
        assertEquals(NoticeKind.BILL_FOUND, n.kind); assertEquals("Bill found: HDFC Bank credit card", n.title); assertEquals("₹12,345.00 · due 25 Oct · Tap to add it", n.text)
        assertEquals(emptyList(), planBills(listOf(found("b1")), sent = setOf(NoticePlanner.foundKey("b1"))), "already shown")
        assertEquals(2, planBills(listOf(found("b1"), found("b2"))).size)
    }

    @Test
    fun aFoundBillIsHeldBackInQuietHoursAndWhenBillNoticesAreOffAndComesLaterOtherwise() {
        val quiet = NotificationPrefs(quietEnabled = true, quietStartMinute = at(22), quietEndMinute = at(7))
        assertEquals(emptyList(), planBills(listOf(found("b1")), minute = at(23), prefs = quiet))
        assertEquals(1, planBills(listOf(found("b1")), minute = at(8), prefs = quiet).size, "nothing was recorded as sent, so it goes out once quiet hours end")
        assertEquals(emptyList(), planBills(listOf(found("b1")), prefs = NotificationPrefs(bills = false, quietEnabled = false)))
    }

    @Test
    fun aTrackedBillGetsAHeadsUpThreeDaysAheadOneOnTheDayAndOneWhenOverdueEachOnceFromTheReminderHour() {
        fun titles(date: LocalDate, minute: Int = at(12), sent: Set<String> = emptySet()) = planBills(trackedBills = listOf(tracked("t", date)), minute = minute, sent = sent).map { it.title }
        assertEquals(emptyList(), titles(LocalDate(2026, 10, 11)), "four days ahead: too early")
        assertEquals(listOf("BESCOM electricity bill due in 3 days"), titles(LocalDate(2026, 10, 10)))
        assertEquals(listOf("BESCOM electricity bill due tomorrow"), titles(LocalDate(2026, 10, 8)))
        assertEquals(listOf("BESCOM electricity bill is due today"), titles(LocalDate(2026, 10, 7)))
        assertEquals(listOf("BESCOM electricity bill is overdue"), titles(LocalDate(2026, 10, 1)))
        assertEquals(emptyList(), titles(LocalDate(2026, 10, 7), minute = at(8)), "before the reminder hour (9:00)")
        assertEquals(emptyList(), titles(LocalDate(2026, 10, 7), sent = setOf(NoticePlanner.trackedKey("t", "today"))))
        assertEquals(listOf("BESCOM electricity bill is due today"), titles(LocalDate(2026, 10, 7), sent = setOf(NoticePlanner.trackedKey("t", "ahead"))), "having been told ahead does not stop the notice on the day")
    }
}
