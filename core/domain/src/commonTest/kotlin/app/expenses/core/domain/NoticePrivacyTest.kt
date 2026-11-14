package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F-01: with Hide amounts or the App lock on, no notice of any kind carries an amount; with neither, nothing is changed. */
class NoticePrivacyTest {
    private val today = LocalDate(2026, 10, 7)
    private val start = LocalDate(2026, 10, 1)

    /** One notice of EVERY kind, made by the real planner from inputs that carry amounts (the tracked bill's text too: the check leaves it out when Hide amounts is on, a snoozed copy may still have it). */
    private fun everyKind(): List<Notice> {
        val prefs = NotificationPrefs(quietEnabled = false, billHour = 0, recap = true)
        val all = NoticePlanner.plan(
            prefs, today, 12 * 60,
            bills = listOf(BillDue("rent", "Rent", "₹15,000.00", today)),
            budgets = listOf(BudgetStanding(null, "Everything", BudgetState.OVER, "₹5,850.00 over ₹10,000.00", start), BudgetStanding("food", "Food", BudgetState.NEAR, "₹1,200.00 left of ₹6,000.00", start)),
            alreadySent = emptySet(),
            cards = listOf(CardDue("amex", "Amex", today.plus(2, kotlinx.datetime.DateTimeUnit.DAY), "₹40,000.00", "₹2,000.00", 10)),
            recap = RecapDue(start, "Last week: ₹3,450.00 spent", "₹1,000.00 more than the week before (+40%). Most on Food (60%)."),
            foundBills = listOf(FoundBill("f", "HDFC Bank credit card", null, "due 25 Oct")),
            trackedBills = listOf(TrackedBill("t", "BESCOM bill", "₹1,842.50", today.plus(1, kotlinx.datetime.DateTimeUnit.DAY))),
            pace = listOf(BudgetPace("travel", "Travel", 70, 40, "₹3,000.00 left of ₹10,000.00", start)),
            wrapUp = BudgetWrapUp(start, "₹8,000.00 spent of ₹10,000.00: ₹2,000.00 kept."),
            statements = listOf(CardStatementMade("amex", "Amex", today, today.plus(20, kotlinx.datetime.DateTimeUnit.DAY), "₹40,000.00", "₹2,000.00")),
            limits = listOf(CardLimitUse("amex", "Amex", 80, 80, "₹80,000.00", "₹1,00,000.00", 2026 * 12 + 10)),
            cleared = listOf(CardCleared("amex", "Amex", today)),
        )
        return all
    }

    private val digitOrRupee = Regex("[0-9₹]")

    @Test
    fun theHarnessReallyProducesEveryKindWithAmountsInTheirText() {
        val all = everyKind()
        assertEquals(NoticeKind.entries.toSet(), all.map { it.kind }.toSet(), "a notice of every kind")
        // the kinds the check writes figures into carry them when nothing is hidden (so the next test proves the masking, not an empty harness)
        for (k in listOf(NoticeKind.BILL_DUE, NoticeKind.CARD_DUE, NoticeKind.BUDGET_NEAR, NoticeKind.BUDGET_OVER, NoticeKind.BUDGET_PACE, NoticeKind.BUDGET_WRAPUP, NoticeKind.CARD_STATEMENT, NoticeKind.CARD_LIMIT, NoticeKind.BILL_TRACKED)) {
            assertTrue(all.filter { it.kind == k }.all { "₹" in it.text }, "$k carries an amount when nothing is hidden")
        }
        assertTrue("₹" in all.single { it.kind == NoticeKind.WEEKLY_RECAP }.title, "the recap's title carries the amount spent")
    }

    @Test
    fun withHideAmountsOrAppLockNoNoticeOfAnyKindHasAnAmountInItsText() {
        for (n in everyKind()) {
            val m = NoticePrivacy.masked(n, hide = true)
            // a bill found in a message is the one kind whose text may have a digit: its due date ("due 25 Oct"); an amount is never there
            val allowed = if (n.kind == NoticeKind.BILL_FOUND) Regex("due \\d{1,2} [A-Z][a-z]{2}") else null
            val text = if (allowed != null) m.text.replace(allowed, "") else m.text
            assertTrue(!digitOrRupee.containsMatchIn(text), "${n.kind}: '${m.text}' has an amount")
            assertTrue("₹" !in m.title && !Regex("\\d,\\d|\\d\\.\\d{2}").containsMatchIn(m.title), "${n.kind}: the title '${m.title}' has an amount")
            assertEquals(n.key, m.key, "the key is kept: what was shown is recorded as without masking")
            assertEquals(n.ref, m.ref); assertEquals(n.kind, m.kind)
        }
    }

    @Test
    fun withNeitherOnNothingIsChanged() {
        for (n in everyKind()) assertEquals(n, NoticePrivacy.masked(n, hide = false))
    }

    @Test
    fun theWordsAreTheOnesTheIPhoneAlreadyUsed() {
        fun text(kind: NoticeKind) = NoticePrivacy.masked(everyKind().first { it.kind == kind }, true).text
        assertEquals("Open the app to post or skip it", text(NoticeKind.BILL_DUE))
        assertEquals("Open the app to see what to pay", text(NoticeKind.CARD_DUE))
        assertEquals("Open the app to see how much is left", text(NoticeKind.BUDGET_OVER)); assertEquals("Open the app to see how much is left", text(NoticeKind.BUDGET_NEAR))
        assertEquals("Open the app to see how your spending is going", text(NoticeKind.BUDGET_PACE))
        assertEquals("Open the app to see how last month went", text(NoticeKind.BUDGET_WRAPUP))
        assertEquals("Open the app to see your card", text(NoticeKind.CARD_STATEMENT)); assertEquals("Open the app to see your card", text(NoticeKind.CARD_LIMIT)); assertEquals("Open the app to see your card", text(NoticeKind.CARD_CLEARED))
        val recap = NoticePrivacy.masked(everyKind().first { it.kind == NoticeKind.WEEKLY_RECAP }, true)
        assertEquals("Your weekly recap", recap.title); assertEquals("Open the app to see how last week went", recap.text)
        assertEquals("Mark it paid when it is done", text(NoticeKind.BILL_TRACKED), "a tracked bill keeps only its hint")
        assertEquals("Mark it paid when it is done", NoticePrivacy.masked(Notice("k", NoticeKind.BILL_TRACKED, "t", "Mark it paid when it is done"), true).text, "and a text that never had an amount is not cut")
    }

    @Test
    fun everyKindThatCanCarryAnAmountHasALockScreenVersionInOurOwnWords() {
        for (k in NoticeKind.entries) {
            val title = NoticePrivacy.publicTitle(k)
            if (k == NoticeKind.CARD_CLEARED) { assertNull(title, "a card that is paid carries no figure"); continue }
            assertNotNull(title, "$k has a public version")
            assertTrue(!digitOrRupee.containsMatchIn(title), "$k: '$title'")
        }
        assertTrue(!digitOrRupee.containsMatchIn(NoticePrivacy.PUBLIC_TEXT))
        assertEquals("A bill was found", NoticePrivacy.publicTitle(NoticeKind.BILL_FOUND)); assertEquals("A bill needs attention", NoticePrivacy.publicTitle(NoticeKind.BILL_TRACKED))
        assertNotEquals(NoticePrivacy.publicTitle(NoticeKind.BILL_DUE), NoticePrivacy.publicTitle(NoticeKind.BUDGET_OVER))
    }
}
