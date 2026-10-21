package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardStoreTest {
    private val inr = Currency.INR
    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val ledger = LedgerStore(db, clock) { 1L }
    private val recurring = RecurringStore(db, clock, { 1L }, ledger)
    private val budgets = BudgetStore(db, clock, { 1L }, ledger, recurring)
    private val cards = CardStore(db, clock) { 2L }
    private val service = NotificationService(db, recurring, budgets, cards, ReportStore(db, ledger))
    private val card = AccountId("card")
    private val bank = AccountId("bank")
    private val terms = CardTerms("card", statementDay = 15, dueDay = 5, minDuePercent = 5, remindDaysBefore = 3)
    private var n = 0

    init {
        CategoryStore(db, clock) { 1L }
        ledger.saveAccount(Account(bank, "Bank", AccountType.BANK, inr))
        ledger.saveAccount(Account(card, "HDFC card", AccountType.CREDIT_CARD, inr))
    }

    private fun at(y: Int, m: Int, d: Int, hour: Int = 12) = LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds() + hour * 3_600_000L
    private fun spend(millis: Long, minor: Long) = ledger.save(Expense(EntryId("x${n++}"), millis, card, Money(minor, inr)))
    private fun today(d: Int) = LocalDate(2026, 3, d)

    @Test
    fun theStatementIsWhatWasOwedAtTheCutAndLaterSpendingIsNotOnIt() {
        cards.save(terms)
        spend(at(2026, 2, 20), 30_000); spend(at(2026, 3, 15, 23), 20_000) // the last one is on the statement day itself: on the statement
        spend(at(2026, 3, 16, 0), 99_000) // the next day: the next statement
        val s = cards.standing("card", today(20), zone)!!
        assertEquals(50_000, s.statementBalance); assertEquals(50_000, s.remaining); assertEquals(2_500, s.minimumDue)
        assertEquals(LocalDate(2026, 3, 15), s.cycle.statementDate); assertEquals(LocalDate(2026, 4, 5), s.cycle.dueDate)
    }

    @Test
    fun paymentsRefundsAndTransfersInAfterTheStatementReduceWhatRemainsButPaymentsBeforeItAreAlreadyInTheBalance() {
        cards.save(terms)
        spend(at(2026, 3, 1), 100_000)
        ledger.save(Transfer(EntryId("early"), at(2026, 3, 10), bank, card, Money(10_000, inr))) // before the cut: already netted in the balance
        assertEquals(90_000, cards.standing("card", today(20), zone)!!.remaining)
        ledger.save(Transfer(EntryId("pay"), at(2026, 3, 18), bank, card, Money(40_000, inr)))
        assertEquals(50_000, cards.standing("card", today(20), zone)!!.remaining)
        ledger.save(Refund(EntryId("ref"), at(2026, 3, 19), card, Money(5_000, inr), EntryId("x0")))
        ledger.save(Income(EntryId("cb"), at(2026, 3, 19), card, Money(5_000, inr)))
        assertEquals(40_000, cards.standing("card", today(20), zone)!!.remaining)
        ledger.save(Transfer(EntryId("rest"), at(2026, 3, 20), bank, card, Money(80_000, inr)))
        assertEquals(0, cards.standing("card", today(21), zone)!!.remaining, "overpaid is zero, not negative")
    }

    @Test
    fun noTermsMeansNoStandingAndOnlyACreditCardCanHaveTerms() {
        assertNull(cards.standing("card", today(20), zone))
        assertTrue(assertFailsWith<IllegalArgumentException> { cards.save(CardTerms("bank", 1, 2)) }.message!!.contains("credit card"))
        assertFailsWith<IllegalArgumentException> { cards.save(CardTerms("nobody", 1, 2)) }
    }

    @Test
    fun savingChangesAndClearingAreLoggedPerFieldAndASecondIdenticalSaveLogsNothing() {
        cards.save(terms)
        fun ops() = db.schemaQueries.opsOfRow("card_terms", "card").executeAsList()
        assertEquals(4, ops().size)
        cards.save(terms); assertEquals(4, ops().size)
        cards.save(terms.copy(dueDay = 9)); assertEquals(5, ops().size); assertEquals("9", ops().last().value_)
        assertEquals(terms.copy(dueDay = 9), cards.terms("card"))
        cards.clear("card"); assertNull(cards.terms("card")); assertEquals("deleted", ops().last().field_)
        val n = ops().size; cards.clear("card"); assertEquals(n, ops().size)
    }

    @Test
    fun theCheckRemindsOnlyForACardWithSomethingLeftAndOnlyOncePerNotice() {
        cards.save(terms)
        spend(at(2026, 3, 1), 40_000)
        val minute = 9 * 60 + 5
        assertEquals(emptyList(), service.check(LocalDate(2026, 3, 30), minute, zone).notices.filter { it.kind == NoticeKind.CARD_DUE }, "too early")
        val ahead = service.check(LocalDate(2026, 4, 2), minute, zone).notices.single { it.kind == NoticeKind.CARD_DUE }
        assertEquals("HDFC card bill due in 3 days", ahead.title); assertEquals("Pay ₹400.00, minimum about ₹20.00", ahead.text)
        service.markShown(listOf(ahead))
        assertEquals(emptyList(), service.check(LocalDate(2026, 4, 3), minute, zone).notices.filter { it.kind == NoticeKind.CARD_DUE })
        ledger.save(Transfer(EntryId("pay"), at(2026, 3, 20), bank, card, Money(40_000, inr)))
        assertEquals(emptyList(), service.check(LocalDate(2026, 4, 5), minute, zone).notices.filter { it.kind == NoticeKind.CARD_DUE }, "paid in full: no reminder on the due day")
    }

    @Test
    fun aBackupCarriesTheTermsAndRestoringTwiceChangesNothingAndNeverOverwritesTermsAlreadyHere() {
        cards.save(terms.copy(minDuePercent = 10))
        val backup = BackupStore(db, clock, { 1L }, ledger, TemplateStore(db, clock) { 1L }, recurring)
        val data = BackupFile.decode(BackupFile.encode(backup.export(), 1L)).second
        assertEquals(listOf(BackupCardTerms("card", 15, 5, 10, 3)), data.cardTerms)
        val fresh = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
        val fl = LedgerStore(fresh, clock) { 1L }
        val fb = BackupStore(fresh, clock, { 1L }, fl, TemplateStore(fresh, clock) { 1L }, RecurringStore(fresh, clock, { 1L }, fl))
        fb.restore(data); fb.restore(data)
        val fc = CardStore(fresh, clock) { 1L }
        assertEquals(terms.copy(minDuePercent = 10), fc.terms("card"))
        fc.save(terms.copy(dueDay = 20))
        fb.restore(data)
        assertEquals(20, fc.terms("card")!!.dueDay, "terms set on this phone are kept")
    }

    @Test
    fun anAccountThatStoppedBeingACardNoLongerRemindsEvenIfItsTermsRemain() {
        cards.save(terms); spend(at(2026, 3, 1), 40_000)
        assertEquals(1, cards.all().size)
        ledger.saveAccount(Account(card, "HDFC card", AccountType.BANK, inr))
        assertEquals(emptyList(), cards.all()); assertEquals(emptyList(), service.check(LocalDate(2026, 4, 5), 10 * 60, zone).notices.filter { it.kind == NoticeKind.CARD_DUE })
    }

    // ---- statement, credit limit and "paid" notices, through the service ----

    private fun check(d: LocalDate, hour: Int = 12) = service.check(d, hour * 60, zone).notices
    init { service.setPrefs(app.expenses.core.domain.NotificationPrefs(quietEnabled = false)) }

    @Test
    fun aStatementNoticeComesOnTheDayItIsMadeAndForTwoDaysAfterButOnlyOnceAndNotForAnEmptyOne() {
        cards.save(terms)
        spend(at(2026, 3, 1), 120_000)
        assertTrue(check(LocalDate(2026, 3, 14)).none { it.kind == NoticeKind.CARD_STATEMENT }, "the day before the statement")
        val n = check(LocalDate(2026, 3, 15)).single { it.kind == NoticeKind.CARD_STATEMENT }
        assertEquals("HDFC card statement is ready", n.title); assertTrue(n.text.contains("1,200.00") && n.text.contains("minimum") && n.text.contains("5 Apr"), n.text); assertEquals("card", n.ref)
        assertTrue(check(LocalDate(2026, 3, 17)).any { it.kind == NoticeKind.CARD_STATEMENT }, "two days on, not yet told: still announced")
        assertTrue(check(LocalDate(2026, 3, 18)).none { it.kind == NoticeKind.CARD_STATEMENT }, "three days on, not yet told: too late to announce")
        service.markShown(listOf(n))
        assertTrue(check(LocalDate(2026, 3, 16)).none { it.kind == NoticeKind.CARD_STATEMENT }, "once")
        // a card with nothing owed has no statement to announce
        cards.clear("card"); cards.save(terms.copy(statementDay = 20))
        ledger.save(Income(EntryId("pay"), at(2026, 3, 10), card, Money(120_000, inr), app.expenses.core.domain.ledger.CategoryId("income:salary")))
        assertTrue(check(LocalDate(2026, 3, 20)).none { it.kind == NoticeKind.CARD_STATEMENT })
    }

    @Test
    fun theCreditLimitNoticeFollowsTheBalanceAtEightyAndOneHundredPercentOncePerMonth() {
        cards.save(terms); cards.setCreditLimit("card", 100_000)
        spend(at(2026, 3, 2), 70_000)
        assertTrue(check(today(3)).none { it.kind == NoticeKind.CARD_LIMIT }, "70%")
        spend(at(2026, 3, 3), 15_000)
        val n80 = check(today(4)).single { it.kind == NoticeKind.CARD_LIMIT }
        assertEquals("HDFC card is 85% of its limit", n80.title); service.markShown(listOf(n80))
        assertTrue(check(today(5)).none { it.kind == NoticeKind.CARD_LIMIT })
        spend(at(2026, 3, 5), 20_000)
        val n100 = check(today(6)).single { it.kind == NoticeKind.CARD_LIMIT }
        assertEquals("HDFC card is at its credit limit", n100.title); service.markShown(listOf(n100))
        assertTrue(check(today(7)).none { it.kind == NoticeKind.CARD_LIMIT })
        assertTrue(check(LocalDate(2026, 4, 2)).any { it.kind == NoticeKind.CARD_LIMIT }, "a new month says it again")
        cards.setCreditLimit("card", null)
        assertTrue(check(LocalDate(2026, 5, 2)).none { it.kind == NoticeKind.CARD_LIMIT }, "no limit, no notice")
        assertNull(cards.creditLimit("card"))
    }

    @Test
    fun payingAStatementInFullThatWasToldAboutBringsOnePaidNoticeAndAStatementNeverToldAboutDoesNot() {
        cards.save(terms)
        spend(at(2026, 3, 1), 50_000)
        val told = check(LocalDate(2026, 3, 15)).single { it.kind == NoticeKind.CARD_STATEMENT }
        service.markShown(listOf(told))
        assertTrue(check(LocalDate(2026, 3, 20)).none { it.kind == NoticeKind.CARD_CLEARED }, "still owed")
        ledger.save(Transfer(EntryId("pay"), at(2026, 3, 22), bank, card, Money(50_000, inr)))
        val paid = check(LocalDate(2026, 3, 23)).single { it.kind == NoticeKind.CARD_CLEARED }
        assertEquals("HDFC card bill is paid", paid.title); service.markShown(listOf(paid))
        assertTrue(check(LocalDate(2026, 3, 24)).none { it.kind == NoticeKind.CARD_CLEARED }, "once")
    }

    @Test
    fun theWarningLevelIsTheChosenPercentAndLastMonthsKeptBudgetIsSaidInTheFirstDaysOnly() {
        budgets.setLimit(null, Money(100_000, inr))
        ledger.save(Expense(EntryId("sep"), at(2026, 9, 10), bank, Money(80_000, inr), listOf(app.expenses.core.domain.ledger.CategorySplit(app.expenses.core.domain.ledger.CategoryId("Food"), Money(80_000, inr)))))
        val early = check(LocalDate(2026, 10, 2))
        val wrap = early.single { it.kind == NoticeKind.BUDGET_WRAPUP }
        assertTrue(wrap.text.contains("800.00") && wrap.text.contains("1,000.00") && wrap.text.contains("200.00"), wrap.text)
        assertTrue(check(LocalDate(2026, 10, 8)).none { it.kind == NoticeKind.BUDGET_WRAPUP }, "after the first days it is not news")
        service.markShown(listOf(wrap)); assertTrue(check(LocalDate(2026, 10, 3)).none { it.kind == NoticeKind.BUDGET_WRAPUP })
        // a lower warning level warns sooner
        ledger.save(Expense(EntryId("oct"), at(2026, 10, 20), bank, Money(60_000, inr), listOf(app.expenses.core.domain.ledger.CategorySplit(app.expenses.core.domain.ledger.CategoryId("Food"), Money(60_000, inr)))))
        assertTrue(check(LocalDate(2026, 10, 24)).none { it.kind == NoticeKind.BUDGET_NEAR }, "60% is below the default 80")
        service.setPrefs(service.prefs().copy(budgetNearPercent = 50))
        assertTrue(check(LocalDate(2026, 10, 24)).any { it.kind == NoticeKind.BUDGET_NEAR }, "60% is past a chosen 50")
        // and last month's note is for a kept budget only
        service.setPrefs(service.prefs().copy(budgetNearPercent = 80))
        budgets.setLimit(null, Money(50_000, inr))
        assertTrue(check(LocalDate(2026, 11, 2)).none { it.kind == NoticeKind.BUDGET_WRAPUP }, "October's 600 went over its 500")
    }
}
