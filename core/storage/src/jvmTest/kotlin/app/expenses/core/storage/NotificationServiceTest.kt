package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationServiceTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val zone = TimeZone.of("Asia/Kolkata")
    private val today = LocalDate(2026, 10, 7)
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n1")
    private val categories = CategoryStore(db, clock) { 1L } // seeds the default categories
    private val ledger = LedgerStore(db, clock, { 1L })
    private val recurring = RecurringStore(db, clock, { 1L }, ledger)
    private val budgets = BudgetStore(db, clock, { 1L }, ledger, recurring)
    private val service = NotificationService(db, recurring, budgets, CardStore(db, clock) { 1L }, ReportStore(db, ledger))
    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, inr)
    private val noon = 12 * 60

    init {
        ledger.saveAccount(bank)
        service.setPrefs(NotificationPrefs(quietEnabled = false))
    }

    private fun spend(id: String, rupees: Long, category: String) =
        ledger.save(Expense(EntryId(id), today.atStartOfDayIn(zone).toEpochMilliseconds() + 3_600_000, bank.id, m(rupees * 100), listOf(CategorySplit(CategoryId(category), m(rupees * 100)))))

    private fun bill(id: String, auto: Boolean = false) =
        recurring.save(RecurringItem(id, id, m(10_000), CategoryId("Bills"), bank.id, null, Schedule(RepeatUnit.MONTH, 1, today), auto))

    @Test
    fun preferencesRoundTripAndDamagedValuesFallBackToDefaults() {
        assertEquals(NotificationPrefs(quietEnabled = false), service.prefs())
        val p = NotificationPrefs(bills = false, budgets = true, quietEnabled = true, quietStartMinute = 23 * 60 + 30, quietEndMinute = 6 * 60 + 15, billHour = 18)
        service.setPrefs(p)
        assertEquals(p, service.prefs())
        db.schemaQueries.putSetting("notif_quiet_start", "99999")
        assertEquals(NotificationPrefs(), service.prefs(), "an out-of-range value is not trusted")
    }

    @Test
    fun aBillDueTodayIsNotifiedOnceOnceRecordedAsShown() {
        bill("rent")
        val first = service.check(today, noon, zone).notices.single()
        assertEquals(NoticeKind.BILL_DUE, first.kind)
        assertEquals(1, service.check(today, noon, zone).notices.size, "not shown yet, so it is offered again")
        service.markShown(listOf(first))
        assertTrue(service.check(today, noon, zone).notices.isEmpty())
    }

    @Test
    fun aRepeatingIncomeOrTransferRemindsToConfirmItInNeutralWordsNotAsABill() {
        ledger.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("sav"), "Savings", app.expenses.core.domain.ledger.AccountType.BANK, inr))
        recurring.save(RecurringItem("pay", "Salary", m(5_000_000), CategoryId("Other"), bank.id, null, Schedule(RepeatUnit.MONTH, 1, today), kind = app.expenses.core.domain.RecurringKind.INCOME))
        recurring.save(RecurringItem("sip", "SIP", m(1_000_000), null, bank.id, null, Schedule(RepeatUnit.MONTH, 1, today), kind = app.expenses.core.domain.RecurringKind.TRANSFER, toAccount = AccountId("sav")))
        val notices = service.check(today, noon, zone).notices.sortedBy { it.title }
        assertEquals(setOf("Salary due today", "SIP due today"), notices.map { it.title }.toSet())
        assertTrue(notices.all { "bill" !in it.title.lowercase() && "bill" !in it.text.lowercase() && "post or skip" in it.text }, "$notices")
    }

    @Test
    fun budgetAlertsFollowSpendingAndOnlyFireOncePerLevel() {
        budgets.setLimit(null, m(100_000))
        spend("a", 700, "Food")
        assertTrue(service.check(today, noon, zone).notices.isEmpty(), "70% is fine")
        spend("b", 150, "Food")
        val near = service.check(today, noon, zone).notices.single()
        assertEquals(NoticeKind.BUDGET_NEAR, near.kind)
        assertTrue(near.text.contains("left of"))
        service.markShown(listOf(near))
        spend("c", 200, "Food")
        val over = service.check(today, noon, zone).notices.single()
        assertEquals(NoticeKind.BUDGET_OVER, over.kind)
        assertTrue(over.text.contains("over"))
        service.markShown(listOf(over))
        assertTrue(service.check(today, noon, zone).notices.isEmpty())
    }

    @Test
    fun aCategoryBudgetIsNamedAfterItsCategory() {
        budgets.setLimit("Food", m(100_000))
        spend("a", 900, "Food")
        assertEquals("Food is near its limit", service.check(today, noon, zone).notices.single().title)
    }

    @Test
    fun checkingPostsAutoItemsInTheBackgroundAndDoesNotAlsoRemindAboutThem() {
        bill("netflix", auto = true)
        bill("rent", auto = false)
        val r = service.check(today, noon, zone)
        assertEquals(listOf("netflix"), r.autoPosted.map { it.item.id })
        assertEquals(listOf("rent due today"), r.notices.map { it.title })
        assertEquals(m(10_000), ledger.spendByCategory(inr).getValue(CategoryId("Bills")))
        assertTrue(service.check(today, noon, zone).autoPosted.isEmpty(), "a second check posts nothing more")
    }

    @Test
    fun quietHoursHoldNoticesBackWithoutLosingThem() {
        service.setPrefs(NotificationPrefs())
        bill("rent")
        assertTrue(service.check(today, 23 * 60, zone).notices.isEmpty())
        assertEquals(1, service.check(today, 9 * 60, zone).notices.size, "still there in the morning")
    }

    @Test
    fun onlyTheMostRecentEventsAreRemembered() {
        val many = (1..NotificationService.MAX_REMEMBERED + 50).map { app.expenses.core.domain.Notice("k$it", NoticeKind.BILL_DUE, "t", "x") }
        service.markShown(many)
        val stored = db.schemaQueries.settingValue("notif_sent").executeAsOne().split('\n')
        assertEquals(NotificationService.MAX_REMEMBERED, stored.size)
        assertEquals("k${NotificationService.MAX_REMEMBERED + 50}", stored.last())
    }

    // ---- bills found in messages ----

    private val billStore = BillStore(db)
    private val withBills = NotificationService(db, recurring, budgets, CardStore(db, clock) { 1L }, ReportStore(db, ledger), billStore)
    private fun found(id: String, due: String? = "2026-10-25") = Bill(id, "SMS", "AX-HDFCBK-S", "HDFC Bank", "CREDIT_CARD", "HDFC Bank credit card", 1_234_500, 61_700, "INR", due, "0862", null, false, 1_000, "text", BillStatus.FOUND, null, null, "k$id")

    @Test
    fun aFoundBillIsToldAboutWithItsAmountAndDateAndNotAgainOnceShown() {
        billStore.add(found("b1"))
        val first = withBills.check(today, noon, zone).notices.single { it.kind == app.expenses.core.domain.NoticeKind.BILL_FOUND }
        assertEquals("Bill found: HDFC Bank credit card", first.title); assertEquals("₹12,345.00 · due 25 Oct · Tap to add it", first.text)
        withBills.markShown(listOf(first))
        assertTrue(withBills.check(today, noon, zone).notices.none { it.kind == app.expenses.core.domain.NoticeKind.BILL_FOUND })
    }

    @Test
    fun hideAmountsKeepsTheFigureOutOfTheNotification() {
        billStore.add(found("b1"))
        db.schemaQueries.putSetting("hide_amounts", "1")
        val n = withBills.check(today, noon, zone).notices.single { it.kind == app.expenses.core.domain.NoticeKind.BILL_FOUND }
        assertEquals("due 25 Oct · Tap to add it", n.text)
    }

    @Test
    fun aTrackedBillRemindsFromThreeDaysBeforeItsDateAndAFoundOneDoesNot() {
        billStore.add(found("t", due = "2026-10-09")); billStore.track("t")
        billStore.add(found("f", due = "2026-10-09"))
        val kinds = withBills.check(today, noon, zone).notices.map { it.kind }
        assertEquals(1, kinds.count { it == app.expenses.core.domain.NoticeKind.BILL_TRACKED }, "only the tracked one reminds")
        assertEquals(1, kinds.count { it == app.expenses.core.domain.NoticeKind.BILL_FOUND })
        billStore.add(found("undated", due = null)); billStore.track("undated")
        assertEquals(1, withBills.check(today, noon, zone).notices.count { it.kind == app.expenses.core.domain.NoticeKind.BILL_TRACKED }, "a tracked bill with no date has nothing to remind about")
    }

    @Test
    fun noMoreThanFiveFoundBillsAreToldAboutAtOnce() {
        repeat(8) { billStore.add(found("b$it").copy(dedupeKey = "k$it", dueDate = "2026-10-2${it}")) }
        assertEquals(5, withBills.check(today, noon, zone).notices.count { it.kind == app.expenses.core.domain.NoticeKind.BILL_FOUND })
    }

    private fun cardWithStatementDue(): LocalDate {
        ledger.saveAccount(Account(AccountId("card"), "Amex", AccountType.CREDIT_CARD, inr))
        CardStore(db, clock) { 1L }.save(app.expenses.core.domain.CardTerms("card", 25, 12))
        ledger.save(Expense(EntryId("big"), LocalDate(2026, 9, 1).atStartOfDayIn(zone).toEpochMilliseconds(), AccountId("card"), m(500_000)))
        return LocalDate(2026, 10, 10) // statement cut 25 Sep, due 12 Oct, reminders from 9 Oct
    }

    @Test
    fun aCardWhoseStatementIsATrackedBillIsRemindedOnceByTheBillNotAlsoByItsBillingDates() {
        val day = cardWithStatementDue()
        assertTrue(withBills.check(day, noon, zone).notices.any { it.kind == app.expenses.core.domain.NoticeKind.CARD_DUE }, "with no bill, the card reminds by itself")
        billStore.add(found("b1", due = "2026-10-12")); billStore.track("b1")
        val kinds = withBills.check(day, noon, zone).notices.map { it.kind }
        assertTrue(app.expenses.core.domain.NoticeKind.BILL_TRACKED in kinds); assertTrue(app.expenses.core.domain.NoticeKind.CARD_DUE !in kinds, "$kinds")
    }

    @Test
    fun aTrackedBillForAnotherDueDateDoesNotSilenceTheCard() {
        val day = cardWithStatementDue()
        billStore.add(found("b1", due = "2026-10-25")); billStore.track("b1")
        assertTrue(withBills.check(day, noon, zone).notices.any { it.kind == app.expenses.core.domain.NoticeKind.CARD_DUE })
    }
}
