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
}
