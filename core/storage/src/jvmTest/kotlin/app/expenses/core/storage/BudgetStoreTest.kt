package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.BudgetLimit
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.BudgetState
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetStoreTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val zone = TimeZone.of("Asia/Kolkata")
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private val clock = HlcClock("n1")
    private val categories = CategoryStore(db, clock, { 1_000L })
    private val ledger = LedgerStore(db, clock, { 1_000L })
    private val recurring = RecurringStore(db, clock, { 1_000L }, ledger)
    private val budgets = BudgetStore(db, clock, { 1_000L }, ledger, recurring)
    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, inr)

    init { ledger.saveAccount(bank) }

    /** Millis for a date in Kolkata, [minute] minutes after midnight. */
    private fun at(y: Int, mo: Int, d: Int, minute: Int = 0) = LocalDate(y, mo, d).atStartOfDayIn(zone).toEpochMilliseconds() + minute * 60_000L
    private fun spend(id: String, amount: Long, category: String?, whenMillis: Long) {
        ledger.save(Expense(EntryId(id), whenMillis, bank.id, m(amount), category?.let { listOf(CategorySplit(CategoryId(it), m(amount))) } ?: emptyList()))
    }
    private fun status(y: Int, mo: Int, d: Int) = budgets.status(LocalDate(y, mo, d), zone, inr)
    private fun budgetOps() = db.schemaQueries.opsSince("", 100_000).executeAsList().filter { it.entity == "budget" }

    @Test
    fun withNoLimitsThereIsNothingToShowAndNoSafeToSpend() {
        val s = status(2026, 10, 6)
        assertTrue(s.progress.isEmpty())
        assertNull(s.safeToSpend)
        assertEquals(LocalDate(2026, 10, 1), s.period.start)
    }

    @Test
    fun onlySpendingInsideThePeriodCountsAndTheEdgesAreExact() {
        budgets.setLimit(null, m(100_000))
        spend("before", 1_000, "Food", at(2026, 9, 30, 24 * 60 - 1)) // 23:59 on 30 Sep: last minute of the previous month
        spend("first", 2_000, "Food", at(2026, 10, 1)) // midnight at the start: in
        spend("last", 4_000, "Food", at(2026, 10, 31, 24 * 60 - 1)) // 23:59 on 31 Oct: in
        spend("after", 8_000, "Food", at(2026, 11, 1)) // midnight 1 Nov: next period
        assertEquals(m(6_000), status(2026, 10, 15).progress.single().spent)
        assertEquals(m(1_000), status(2026, 9, 15).progress.single().spent)
        assertEquals(m(8_000), status(2026, 11, 15).progress.single().spent)
    }

    @Test
    fun categoryLimitsIncludeSubcategoriesAndTheOverallLimitIncludesUncategorised() {
        val cafe = categories.create("Cafe", parentId = "Food")
        budgets.setLimit(null, m(50_000))
        budgets.setLimit("Food", m(10_000))
        budgets.setLimit("Fun", m(1_000))
        spend("a", 6_000, "Food", at(2026, 10, 3))
        spend("b", 2_500, cafe.id, at(2026, 10, 4))
        spend("c", 1_200, "Fun", at(2026, 10, 5))
        spend("d", 500, null, at(2026, 10, 5))
        val s = status(2026, 10, 6)
        val byScope = s.progress.associateBy { it.categoryId }
        assertEquals(m(10_200), byScope.getValue(null).spent)
        assertEquals(m(8_500), byScope.getValue("Food").spent, "Food plus Cafe")
        assertEquals(BudgetState.NEAR, byScope.getValue("Food").state)
        assertEquals(BudgetState.OVER, byScope.getValue("Fun").state)
        assertEquals(listOf<String?>(null, "Food", "Fun"), s.progress.map { it.categoryId }, "overall first, then the category order")
    }

    @Test
    fun yourShareOfASharedExpenseAndRefundsAreWhatCounts() {
        budgets.setLimit(null, m(10_000))
        ledger.save(Expense(EntryId("dinner"), at(2026, 10, 2), bank.id, m(3_000), listOf(CategorySplit(CategoryId("Food"), m(3_000))), listOf(Share(null, m(1_000)), Share(PersonId("asha"), m(2_000)))))
        assertEquals(m(1_000), status(2026, 10, 6).progress.single().spent, "only your own share")
        spend("shirt", 2_000, "Shopping", at(2026, 10, 3))
        ledger.save(Refund(EntryId("back"), at(2026, 10, 4), bank.id, m(500), EntryId("shirt")))
        assertEquals(m(2_500), status(2026, 10, 6).progress.single().spent)
    }

    @Test
    fun aRefundInALaterPeriodReducesThatPeriodNotTheOriginal() {
        budgets.setLimit(null, m(10_000))
        spend("shirt", 2_000, "Shopping", at(2026, 10, 30))
        ledger.save(Refund(EntryId("back"), at(2026, 11, 2), bank.id, m(500), EntryId("shirt")))
        assertEquals(m(2_000), status(2026, 10, 31).progress.single().spent)
        assertEquals(m(-500), status(2026, 11, 3).progress.single().spent)
        assertEquals(0, status(2026, 11, 3).progress.single().usedPercent)
    }

    @Test
    fun safeToSpendComesFromTheOverallLimit() {
        budgets.setLimit(null, m(31_000))
        spend("a", 10_000, "Food", at(2026, 10, 2))
        val s = status(2026, 10, 11).safeToSpend!!
        assertEquals(21, s.daysLeft)
        assertEquals(m(1_000), s.perDay)
    }

    @Test
    fun aSalaryCyclePeriodMovesTheBoundary() {
        budgets.setPeriod(BudgetPeriod.Monthly(25))
        budgets.setLimit(null, m(100_000))
        spend("old", 1_000, "Food", at(2026, 10, 24))
        spend("new", 3_000, "Food", at(2026, 10, 25))
        assertEquals(m(3_000), status(2026, 10, 30).progress.single().spent)
        assertEquals(m(1_000), status(2026, 10, 24).progress.single().spent)
        assertEquals(LocalDate(2026, 10, 25), status(2026, 10, 30).period.start)
    }

    @Test
    fun theWeeklyPeriodAndThe25thSurviveAReopen() {
        assertEquals(BudgetPeriod.Monthly(1), budgets.period(), "calendar month by default")
        for (p in listOf(BudgetPeriod.Monthly(25), BudgetPeriod.Weekly(DayOfWeek.SUNDAY), BudgetPeriod.Weekly(DayOfWeek.MONDAY), BudgetPeriod.BiWeekly(LocalDate(2026, 1, 5)), BudgetPeriod.BiWeekly(LocalDate(1999, 12, 31)), BudgetPeriod.SemiMonthly, BudgetPeriod.Yearly(), BudgetPeriod.Yearly(1), BudgetPeriod.EveryDays(10, LocalDate(2026, 10, 1)), BudgetPeriod.EveryDays(366, LocalDate(2020, 2, 29)))) {
            budgets.setPeriod(p)
            assertEquals(p, BudgetStore(db, clock, { 2_000L }, ledger, recurring).period())
        }
    }

    @Test
    fun changingALimitAndClearingItAreLoggedAndASecondSetOfTheSameValueIsNot() {
        budgets.setLimit("Food", m(5_000))
        val n = budgetOps().size
        budgets.setLimit("Food", m(5_000))
        assertEquals(n, budgetOps().size, "no change, no op")
        budgets.setLimit("Food", m(6_000))
        assertEquals(n + 1, budgetOps().size)
        budgets.clearLimit("Food")
        assertTrue(budgetOps().last().let { it.row_id == "Food" && it.field_ == "limit_minor" && it.value_ == null })
        assertTrue(budgets.limits().isEmpty())
        budgets.clearLimit("Food") // clearing what is not there is harmless
    }

    @Test
    fun invalidLimitsAreRefused() {
        assertFailsWith<IllegalArgumentException> { budgets.setLimit("Food", m(0)) }
        assertFailsWith<IllegalArgumentException> { budgets.setLimit("NoSuchCategory", m(100)) }
        val gone = categories.create("Gone"); categories.delete(gone.id)
        assertFailsWith<IllegalArgumentException> { budgets.setLimit(gone.id, m(100)) }
        assertTrue(budgets.limits().isEmpty())
    }

    // ---- interaction with categories ----

    @Test
    fun mergingMovesTheLimitUnlessTheTargetHasItsOwn() {
        budgets.setLimit("Groceries", m(4_000))
        categories.merge("Groceries", "Food")
        assertEquals(listOf(BudgetLimit("Food", m(4_000))), budgets.limits(), "no limit on Food, so Groceries' limit moves")

        budgets.setLimit("Bills", m(7_000))
        budgets.setLimit("Fun", m(2_000))
        categories.merge("Bills", "Fun")
        assertEquals(m(2_000), budgets.limits().first { it.categoryId == "Fun" }.limit, "the target's own limit wins")
        assertTrue(budgets.limits().none { it.categoryId == "Bills" }, "nothing is left on the removed category")
    }

    @Test
    fun aLimitSetWithAMergedIdLandsOnTheTarget() {
        categories.merge("Groceries", "Food")
        budgets.setLimit("Groceries", m(3_000))
        assertEquals(listOf(BudgetLimit("Food", m(3_000))), budgets.limits())
        budgets.clearLimit("Groceries")
        assertTrue(budgets.limits().isEmpty())
    }

    @Test
    fun deletingACategoryRemovesItsLimit() {
        val pets = categories.create("Pets")
        budgets.setLimit(pets.id, m(900))
        categories.delete(pets.id)
        assertTrue(budgets.limits().isEmpty())
    }

    @Test
    fun limitsInAnotherCurrencyAreKeptButNotShownInThePrimaryOne() {
        budgets.setLimit(null, Money(5_000, Currency.USD))
        assertEquals(1, budgets.limits().size)
        assertTrue(status(2026, 10, 6).progress.isEmpty())
        assertNotNull(budgets.status(LocalDate(2026, 10, 6), zone, Currency.USD).safeToSpend)
    }

    @Test
    fun billsStillWaitingInThePeriodAreSetAsideFromSafeToSpend() {
        fun bill(id: String, amount: Long?, day: LocalDate, end: app.expenses.core.domain.ScheduleEnd = app.expenses.core.domain.ScheduleEnd.AfterCount(1)) =
            recurring.save(app.expenses.core.domain.RecurringItem(id, id, amount?.let { m(it) }, CategoryId("Bills"), bank.id, null, app.expenses.core.domain.Schedule(app.expenses.core.domain.RepeatUnit.MONTH, 1, day, end)))
        budgets.setLimit(null, m(31_000))
        bill("rent", 6_000, LocalDate(2026, 10, 28)) // later this period: counts
        bill("wifi", 500, LocalDate(2026, 10, 3)) // past due and not posted: will post inside this period, counts
        bill("power", null, LocalDate(2026, 10, 20)) // no fixed amount: cannot be counted
        bill("tuition", 9_000, LocalDate(2026, 11, 2)) // next period: does not count
        bill("old", 7_000, LocalDate(2026, 9, 28)) // last period: does not count
        recurring.post("wifi", LocalDate(2026, 10, 3), zone).also { assertNotNull(it) } // posted: spent now, no longer upcoming
        bill("gym", 1_000, LocalDate(2026, 10, 15))
        bill("water", 300, LocalDate(2026, 10, 5)) // overdue and unposted: counts, the user still has to deal with it
        val s = status(2026, 10, 11).safeToSpend!!
        assertEquals(m(7_300), s.upcoming, "rent 6,000 + gym 1,000 + overdue water 300")
        assertEquals(m(500), status(2026, 10, 11).progress.single().spent)
        assertEquals(m(30_500 - 7_300), s.available)
        assertEquals(m((30_500 - 7_300) / 21), s.perDay)
        recurring.skip("gym", LocalDate(2026, 10, 15))
        assertEquals(m(6_300), status(2026, 10, 11).safeToSpend!!.upcoming, "a skipped bill is no longer coming")
    }

    @Test
    fun aRepeatingIncomeOrTransferStillWaitingIsNotABillSoSafeToSpendIsUnchanged() {
        val sch = app.expenses.core.domain.Schedule(app.expenses.core.domain.RepeatUnit.MONTH, 1, LocalDate(2026, 10, 28), app.expenses.core.domain.ScheduleEnd.AfterCount(1))
        budgets.setLimit(null, m(31_000))
        val before = status(2026, 10, 11).safeToSpend!!
        ledger.saveAccount(Account(AccountId("sav"), "Savings", AccountType.BANK, inr))
        recurring.save(app.expenses.core.domain.RecurringItem("salary", "Salary", m(50_000), CategoryId("Other"), bank.id, null, sch, kind = app.expenses.core.domain.RecurringKind.INCOME))
        recurring.save(app.expenses.core.domain.RecurringItem("sip", "SIP", m(8_000), null, bank.id, null, sch, kind = app.expenses.core.domain.RecurringKind.TRANSFER, toAccount = AccountId("sav")))
        val after = status(2026, 10, 11).safeToSpend!!
        assertEquals(m(0), after.upcoming, "neither money coming in nor a move to your own savings is spending still to come")
        assertEquals(before.available, after.available); assertEquals(before.perDay, after.perDay)
        recurring.save(app.expenses.core.domain.RecurringItem("rent", "Rent", m(6_000), CategoryId("Bills"), bank.id, null, sch))
        assertEquals(m(6_000), status(2026, 10, 11).safeToSpend!!.upcoming, "a real bill alongside them still counts, and only it")
    }
}
