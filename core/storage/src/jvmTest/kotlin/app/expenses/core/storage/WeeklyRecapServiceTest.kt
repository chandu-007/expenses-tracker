package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.NotificationPrefs
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
import kotlin.test.assertNull

class WeeklyRecapServiceTest {
    private val inr = Currency.INR
    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val categories = CategoryStore(db, clock) { 1L }
    private val ledger = LedgerStore(db, clock) { 1L }
    private val recurring = RecurringStore(db, clock, { 1L }, ledger)
    private val budgets = BudgetStore(db, clock, { 1L }, ledger, recurring)
    private val service = NotificationService(db, recurring, budgets, CardStore(db, clock) { 1L }, ReportStore(db, ledger))
    private var n = 0
    private val wednesday = LocalDate(2026, 10, 7) // the week to recap is Mon 28 Sep to Sun 4 Oct
    private val minute = 9 * 60

    init {
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr))
        service.setPrefs(NotificationPrefs(quietEnabled = false, billHour = 9, recap = true))
    }

    private fun spend(day: LocalDate, minor: Long, category: String, hour: Int = 12) =
        ledger.save(Expense(EntryId("e${n++}"), day.atStartOfDayIn(zone).toEpochMilliseconds() + hour * 3_600_000L, AccountId("bank"), Money(minor, inr), listOf(CategorySplit(CategoryId(category), Money(minor, inr)))))

    private fun recap() = service.check(wednesday, minute, zone).notices.singleOrNull { it.kind == NoticeKind.WEEKLY_RECAP }

    @Test
    fun theRecapComparesTheFinishedWeekWithTheOneBeforeAndNamesTheBiggestCategory() {
        spend(LocalDate(2026, 9, 28), 30_000, "Food"); spend(LocalDate(2026, 10, 4), 70_000, "Bills", hour = 23) // Monday and the last evening of Sunday: both in the week
        spend(LocalDate(2026, 9, 21), 125_000, "Food"); spend(LocalDate(2026, 9, 27), 25_000, "Food", hour = 23) // the week before, ending Sunday night
        spend(LocalDate(2026, 10, 5), 999_900, "Fun", hour = 0) // Monday the 5th at midnight: this week, not recapped
        val r = recap()!!
        assertEquals("Last week: ₹1,000.00 spent", r.title)
        assertEquals("₹500.00 less than the week before (-33%). Most on Bills (70%).", r.text)
    }

    @Test
    fun itIsOffUntilSwitchedOnQuietWeeksAreSilentAndEachWeekIsSentOnce() {
        service.setPrefs(NotificationPrefs(quietEnabled = false, billHour = 9, recap = false))
        spend(LocalDate(2026, 9, 30), 10_000, "Food")
        assertNull(recap(), "off")
        service.setPrefs(NotificationPrefs(quietEnabled = false, billHour = 9, recap = true))
        val first = recap()!!
        service.markShown(listOf(first))
        assertNull(recap(), "already sent for that week")
        spend(LocalDate(2026, 10, 6), 20_000, "Food")
        val next = service.check(LocalDate(2026, 10, 14), minute, zone).notices.single { it.kind == NoticeKind.WEEKLY_RECAP }
        assertEquals("Last week: ₹200.00 spent", next.title, "the next week has its own recap")
    }

    @Test
    fun twoEmptyWeeksSendNothing() {
        spend(LocalDate(2026, 8, 1), 10_000, "Food") // long ago
        assertNull(recap())
    }
}
