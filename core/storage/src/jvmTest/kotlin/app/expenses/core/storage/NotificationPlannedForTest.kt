package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.NoticePlanner
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [NotificationService.plannedFor]: what the iPhone hands the system ahead of time. It must plan like [NotificationService.check] would on that day, and change nothing. */
class NotificationPlannedForTest {
    private val inr = Currency.INR
    private val zone = TimeZone.of("Asia/Kolkata")
    private val today = LocalDate(2026, 10, 7)
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n1")
    private val categories = CategoryStore(db, clock) { 1L }
    private val ledger = LedgerStore(db, clock, { 1L })
    private val recurring = RecurringStore(db, clock, { 1L }, ledger)
    private val budgets = BudgetStore(db, clock, { 1L }, ledger, recurring)
    private val service = NotificationService(db, recurring, budgets, CardStore(db, clock) { 1L }, ReportStore(db, ledger))
    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, inr)
    private val nineAm = 9 * 60

    init {
        ledger.saveAccount(bank)
        service.setPrefs(NotificationPrefs(quietEnabled = false))
    }

    private fun bill(id: String, start: LocalDate, auto: Boolean = false) =
        recurring.save(RecurringItem(id, id, Money(10_000, inr), CategoryId("Bills"), bank.id, null, Schedule(RepeatUnit.MONTH, 1, start), auto))

    @Test
    fun aBillIsPlannedOnItsDueDayFromTheReminderHourAndNotBefore() {
        val due = today.plus(2, DateTimeUnit.DAY)
        bill("rent", due)
        assertEquals(emptyList(), service.plannedFor(today, nineAm, zone))
        assertEquals(emptyList(), service.plannedFor(due, nineAm - 1, zone), "not before the reminder hour")
        val planned = service.plannedFor(due, nineAm, zone)
        assertEquals(listOf(NoticePlanner.billKey("rent", due)), planned.map { it.key })
        assertEquals(NoticeKind.BILL_DUE, planned.single().kind)
    }

    @Test
    fun anItemThatPostsByItselfNeedsNoReminder() {
        bill("auto", today, auto = true)
        assertEquals(emptyList(), service.plannedFor(today, nineAm, zone))
    }

    @Test
    fun whatWasAlreadyShownIsNotPlannedAgain() {
        bill("rent", today)
        val first = service.plannedFor(today, nineAm, zone)
        assertEquals(1, first.size)
        service.markShown(first)
        assertEquals(emptyList(), service.plannedFor(today, nineAm, zone))
    }

    @Test
    fun quietHoursAndTheSwitchHoldEverythingBack() {
        bill("rent", today)
        service.setPrefs(NotificationPrefs(quietEnabled = true, quietStartMinute = 8 * 60, quietEndMinute = 10 * 60))
        assertEquals(emptyList(), service.plannedFor(today, nineAm, zone), "9:00 is inside quiet hours")
        assertEquals(1, service.plannedFor(today, 10 * 60, zone).size, "and the end of them is not")
        service.setPrefs(NotificationPrefs(bills = false, quietEnabled = false))
        assertEquals(emptyList(), service.plannedFor(today, nineAm, zone))
    }

    @Test
    fun planningChangesNothing() {
        bill("auto", today, auto = true)
        bill("rent", today)
        service.plannedFor(today, nineAm, zone)
        // the automatic item has not been posted, nothing was recorded as shown: the real check still posts it and announces the bill
        assertTrue(recurring.due(today).any { it.item.id == "auto" })
        val result = service.check(today, nineAm, zone)
        assertEquals(listOf("auto"), result.autoPosted.map { it.item.id })
        assertEquals(listOf(NoticePlanner.billKey("rent", today)), result.notices.map { it.key })
    }
}
