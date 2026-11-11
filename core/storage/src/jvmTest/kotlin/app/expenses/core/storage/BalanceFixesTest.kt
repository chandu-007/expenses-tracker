package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.BalanceFix
import app.expenses.core.domain.BalanceFixEntry
import app.expenses.core.domain.BalanceFixLog
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Fix this balance" against a real database: the opening balance moves by the difference, no entry is made, every total that counts spending stays, the statement of a card follows,
 * and Undo puts everything back exactly. All names and amounts are invented.
 */
class BalanceFixesTest {
    private val inr = Currency.INR
    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val ledger = LedgerStore(db, clock) { 1L }
    private val recurring = RecurringStore(db, clock, { 1L }, ledger)
    private val budgets = BudgetStore(db, clock, { 1L }, ledger, recurring)
    private val cards = CardStore(db, clock) { 2L }
    private val reports = ReportStore(db, ledger)
    private val service = NotificationService(db, recurring, budgets, cards, reports)
    private val fixes = BalanceFixes(db, ledger)
    private val card = AccountId("card")
    private val bank = AccountId("bank")
    private val cash = AccountId("cash")
    private var n = 0

    init {
        CategoryStore(db, clock) { 1L }
        ledger.saveAccount(Account(bank, "Savings", AccountType.BANK, inr, openingMinor = 25_000_00))
        ledger.saveAccount(Account(cash, "Wallet cash", AccountType.CASH, inr))
        ledger.saveAccount(Account(card, "Axis Card", AccountType.CREDIT_CARD, inr))
    }

    private fun at(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L
    private fun spend(account: AccountId, millis: Long, minor: Long, category: String = "Food") =
        ledger.save(Expense(EntryId("e${n++}"), millis, account, Money(minor, inr), listOf(CategorySplit(CategoryId(category), Money(minor, inr)))))
    private fun balance(a: AccountId) = ledger.balances().getValue(a).minorUnits
    private fun opening(a: AccountId) = db.schemaQueries.accountById(a.value).executeAsOne().opening_minor
    private fun fixed(r: BalanceFixes.Result) = r as BalanceFixes.Result.Fixed
    private fun refused(r: BalanceFixes.Result) = (r as BalanceFixes.Result.Refused).message

    // ---- credit card sign cases ----

    @Test
    fun aCardThatOwesMoreThanTheAppThinksTakesTheDifferenceAsOpeningAndNothingElseChanges() {
        spend(card, at(2026, 10, 20), 3_000_00)
        val beforeSpend = ledger.spendByCategory(inr); val beforeCount = db.schemaQueries.entryCount().executeAsOne(); val beforeOps = db.schemaQueries.opsSince("", 100_000).executeAsList().size
        val r = fixed(fixes.fix("card", BalanceFix.statedBalance(AccountType.CREDIT_CARD, 5_500_00, false), inr, 1_700_000_000_000))
        assertEquals(-3_000_00L, r.beforeMinor); assertEquals(-5_500_00L, r.afterMinor); assertEquals(-2_500_00L, r.deltaMinor)
        assertEquals(-2_500_00L, opening(card), "the opening balance moved by the difference")
        assertEquals(-5_500_00L, balance(card), "the SQL balance")
        assertEquals(-5_500_00L, ledger.loadLedger().balance(card).minorUnits, "the in-memory ledger agrees with the SQL balance")
        assertEquals(beforeSpend, ledger.spendByCategory(inr), "spending by category did not change")
        assertEquals(beforeCount, db.schemaQueries.entryCount().executeAsOne(), "no entry was made")
        val op = db.schemaQueries.opsSince("", 100_000).executeAsList().drop(beforeOps)
        assertEquals(listOf("opening_minor" to "-250000"), op.filter { it.entity == "account" && it.row_id == "card" }.map { it.field_ to it.value_ }, "logged like any other edit of the account: one field, the new value")
        assertEquals(listOf(BalanceFixEntry(1_700_000_000_000, -3_000_00, -5_500_00)), fixes.history("card"))
        assertTrue("card" in fixes.dismissedHints(), "answering the hint turns it off")
    }

    @Test
    fun theOwnersCaseARealBillPaidThatWasBiggerThanTheAppKnewLeavesExtraCreditAndFixingMakesItZeroOwed() {
        // the app was installed after some purchases: it knows 10,000 of a bill that really was 12,000, and the real bill was paid from the bank
        cards.save(CardTerms("card", statementDay = 5, dueDay = 25))
        spend(card, at(2026, 10, 20), 10_000_00)
        ledger.save(Transfer(EntryId("bill"), at(2026, 11, 10), bank, card, Money(12_000_00, inr)))
        val today = LocalDate(2026, 11, 11)
        assertEquals(2_000_00L, balance(card), "extra balance: the card shows 2,000 in credit")
        assertEquals(0L, cards.summary("card", today, zone)!!.owed)
        assertNull(cards.summary("card", today, zone)!!.usedPercent, "no limit was entered")
        val r = fixed(fixes.fix("card", BalanceFix.statedBalance(AccountType.CREDIT_CARD, 0, false), inr, 5L))
        assertEquals(-2_000_00L, r.deltaMinor)
        assertEquals(0L, balance(card), "nothing owed now")
        val s = cards.summary("card", today, zone)!!
        assertEquals(12_000_00L, s.standing.statementBalance, "the statement is the bill the bank sent")
        assertEquals(0L, s.standing.remaining, "and it is paid")
        assertEquals(0L, s.owed)
        assertEquals(13_000_00L, balance(bank), "the bank balance is untouched: 25,000 less the 12,000 paid")
    }

    @Test
    fun aCardThatHasNotBeenPaidYetShowsTheRealBillAsWhatIsLeftToPayAfterTheFix() {
        cards.save(CardTerms("card", statementDay = 5, dueDay = 25, minDuePercent = 5))
        cards.setCreditLimit("card", 1_00_000_00)
        spend(card, at(2026, 10, 20), 10_000_00)
        spend(card, at(2026, 11, 8), 1_500_00) // after the statement
        val today = LocalDate(2026, 11, 11)
        assertEquals(10_000_00L, cards.standing("card", today, zone)!!.remaining)
        fixed(fixes.fix("card", BalanceFix.statedBalance(AccountType.CREDIT_CARD, 13_500_00, false), inr, 5L)) // 2,000 of older spending is missing
        val s = cards.summary("card", today, zone)!!
        assertEquals(12_000_00L, s.standing.remaining, "what is left to pay on the statement")
        assertEquals(1_500_00L, s.spentThisCycle, "this cycle's spending is only what was entered")
        assertEquals(13_500_00L, s.owed)
        assertEquals(13, s.usedPercent, "utilisation follows the corrected balance at once (13,500 of 1,00,000)")
    }

    @Test
    fun aCardInCreditCanBeFixedToAnotherCreditAndOweingBecomesCredit() {
        spend(card, at(2026, 10, 1), 1_000_00)
        // the statement says 400 owed, the app says 1,000 owed: the app is missing payments or refunds
        assertEquals(600_00L, fixed(fixes.fix("card", BalanceFix.statedBalance(AccountType.CREDIT_CARD, 400_00, false), inr, 1L)).deltaMinor)
        assertEquals(-400_00L, balance(card))
        // the bank says the card is 250 in credit
        assertEquals(650_00L, fixed(fixes.fix("card", BalanceFix.statedBalance(AccountType.CREDIT_CARD, 250_00, true), inr, 2L)).deltaMinor)
        assertEquals(250_00L, balance(card))
        assertEquals(listOf(-1_000_00L to -400_00L, -400_00L to 250_00L), fixes.history("card").map { it.beforeMinor to it.afterMinor })
    }

    @Test
    fun theDueReminderFollowsTheCorrectedAmountOwed() {
        cards.save(CardTerms("card", statementDay = 15, dueDay = 5, minDuePercent = 5, remindDaysBefore = 3))
        spend(card, at(2026, 3, 1), 40_000)
        fixed(fixes.fix("card", -90_000, inr, 5L)) // the statement says 900 is owed
        val notice = service.check(LocalDate(2026, 4, 2), 9 * 60 + 5, zone).notices.single { it.kind == NoticeKind.CARD_DUE }
        assertEquals("Pay ₹900.00, minimum about ₹45.00", notice.text)
    }

    // ---- bank, cash, wallet ----

    @Test
    fun aBankWithLessInItThanTheAppThinksLosesTheDifferenceFromItsOpeningBalance() {
        spend(bank, at(2026, 11, 1), 1_000_00)
        assertEquals(24_000_00L, balance(bank))
        val r = fixed(fixes.fix("bank", BalanceFix.statedBalance(AccountType.BANK, 23_100_50, false), inr, 7L))
        assertEquals(-899_50L, r.deltaMinor)
        assertEquals(23_100_50L, balance(bank))
        assertEquals(25_000_00L - 899_50L, opening(bank))
        assertEquals(1, db.schemaQueries.entryCount().executeAsOne())
    }

    @Test
    fun aBankCanBeFixedUpOrToOverdrawnAndTheOpeningBalanceMayGoBelowZero() {
        spend(bank, at(2026, 11, 1), 30_000_00) // app: -5,000 (overdrawn by the entries)
        assertEquals(-5_000_00L, balance(bank))
        fixed(fixes.fix("bank", BalanceFix.statedBalance(AccountType.BANK, 1_000_00, true), inr, 1L)) // the bank says overdrawn by 1,000
        assertEquals(-1_000_00L, balance(bank))
        assertEquals(-1_000_00L - (-30_000_00L), opening(bank), "the opening balance is 29,000: the entries are 30,000 out")
        // salary the app never heard of: the bank says 50,000
        spend(bank, at(2026, 11, 2), 1, "Food")
        fixed(fixes.fix("bank", BalanceFix.statedBalance(AccountType.BANK, 50_000_00, false), inr, 2L))
        assertEquals(50_000_00L, balance(bank))
    }

    @Test
    fun cashAtZeroWithNoEntriesCanBeFixedToWhatIsInTheWallet() {
        val r = fixed(fixes.fix("cash", BalanceFix.statedBalance(AccountType.CASH, 850_00, false), inr, 1L))
        assertEquals(850_00L, r.deltaMinor)
        assertEquals(850_00L, balance(cash)); assertEquals(850_00L, opening(cash))
        assertEquals(0, db.schemaQueries.entryCount().executeAsOne())
    }

    // ---- refusals ----

    @Test
    fun aBalanceThatAlreadyMatchesChangesNothingAtAll() {
        spend(card, at(2026, 10, 20), 3_000_00)
        val ops = db.schemaQueries.opsSince("", 100_000).executeAsList().size
        assertEquals("Already matches: the app shows the same amount.", refused(fixes.fix("card", -3_000_00, inr, 9L)))
        assertEquals(0L, opening(card)); assertTrue(fixes.history("card").isEmpty())
        assertEquals(ops, db.schemaQueries.opsSince("", 100_000).executeAsList().size, "nothing logged")
        assertFalse("card" in fixes.dismissedHints())
        // zero against zero, either way round
        assertEquals("Already matches: the app shows the same amount.", refused(fixes.fix("cash", BalanceFix.statedBalance(AccountType.CASH, 0, true), inr, 9L)))
    }

    @Test
    fun anotherCurrencyAnUnknownOrDeletedAccountAndAnAmountTooLargeAreRefusedAndNothingChanges() {
        val msg = refused(fixes.fix("bank", 1_000_00, Currency.USD, 1L))
        assertTrue("ledger's currency" in msg && "INR" in msg && "USD" in msg, msg)
        assertEquals(25_000_00L, opening(bank))
        assertEquals("That account no longer exists.", refused(fixes.fix("nobody", 5, inr, 1L)))
        ledger.saveAccount(Account(AccountId("gone"), "Gone", AccountType.WALLET, inr))
        ledger.saveAccount(Account(AccountId("keep"), "Keep", AccountType.WALLET, inr))
        ledger.deleteAccount(AccountId("gone"))
        assertEquals("That account no longer exists.", refused(fixes.fix("gone", 5, inr, 1L)))
        assertEquals("That amount is too large for the app to hold.", refused(fixes.fix("bank", BalanceFix.LIMIT + 1, inr, 1L)))
        assertEquals("That amount is too large for the app to hold.", refused(fixes.fix("bank", Long.MAX_VALUE, inr, 1L)))
        assertEquals(25_000_00L, opening(bank)); assertTrue(fixes.history("bank").isEmpty())
    }

    @Test
    fun hugeAmountsAreFixedExactlyAndAnOpeningBalanceThatWouldPassTheLimitIsRefused() {
        val big = BalanceFix.LIMIT
        assertEquals(big, fixed(fixes.fix("cash", big, inr, 1L)).deltaMinor)
        assertEquals(big, balance(cash)); assertEquals(big, opening(cash))
        // from the largest balance to the largest overdraft: both inside the limit, and so is the opening balance it needs
        assertEquals(-2 * big, fixed(fixes.fix("cash", -big, inr, 2L)).deltaMinor)
        assertEquals(-big, balance(cash)); assertEquals(-big, opening(cash))
        // the opening balance at the limit and entries that take the balance a little below it: fixing it back up would need an opening balance past the limit
        fixed(fixes.fix("cash", big, inr, 3L))
        assertEquals(big, opening(cash))
        spend(cash, at(2026, 11, 1), 1_000_00)
        assertEquals("That amount is too large for the app to hold.", refused(fixes.fix("cash", big, inr, 4L)))
        assertEquals(big, opening(cash)); assertEquals(listOf(1L, 2L, 3L), fixes.history("cash").map { it.atMillis }, "nothing changed, nothing recorded")
        // a balance that is itself past the limit (an income as large as the limit on top of it) has no plan at all
        ledger.save(Income(EntryId("huge"), 5L, cash, Money(big, inr), CategoryId("Other")))
        assertEquals("That amount is too large for the app to hold.", refused(fixes.fix("cash", 5, inr, 5L)))
        assertEquals(big, opening(cash))
    }

    // ---- Undo ----

    @Test
    fun undoPutsTheOpeningBalanceBackExactlyAndTakesTheLineAway() {
        spend(card, at(2026, 10, 20), 3_000_00)
        val ops = db.schemaQueries.opsSince("", 100_000).executeAsList().size
        val snapshot = db.schemaQueries.accountById("card").executeAsOne()
        val r = fixed(fixes.fix("card", -5_500_00, inr, 1_700_000_000_000))
        assertTrue(fixes.undo("card", r.deltaMinor, r.atMillis, inr))
        assertEquals(snapshot, db.schemaQueries.accountById("card").executeAsOne(), "the account row is exactly as it was")
        assertEquals(-3_000_00L, balance(card))
        assertTrue(fixes.history("card").isEmpty(), "the line is gone")
        assertNull(db.schemaQueries.settingValue(BalanceFixLog.key("card")).executeAsOneOrNull(), "the setting is gone, not empty")
        assertEquals(ops + 2, db.schemaQueries.opsSince("", 100_000).executeAsList().size, "the fix and its undo are two logged changes of one field")
    }

    @Test
    fun undoIsADifferenceSoAnEditMadeInBetweenIsNotLostAndOnlyThatFixsLineLeaves() {
        val first = fixed(fixes.fix("bank", 24_000_00, inr, 100L))
        val second = fixed(fixes.fix("bank", 23_000_00, inr, 200L))
        ledger.saveAccount(Account(bank, "Savings (main)", AccountType.BANK, inr, opening(bank) + 50_00, false)) // the person edited the account meanwhile
        assertTrue(fixes.undo("bank", second.deltaMinor, second.atMillis, inr))
        assertEquals(24_000_00L + 50_00L, balance(bank))
        assertEquals("Savings (main)", db.schemaQueries.accountById("bank").executeAsOne().name, "the rename stays")
        assertEquals(listOf(100L), fixes.history("bank").map { it.atMillis })
        assertEquals(-1_000_00L, first.deltaMinor)
    }

    @Test
    fun undoOfAnAccountThatIsGoneOrOfAnotherCurrencyDoesNothingAndSaysSo() {
        val r = fixed(fixes.fix("cash", 100_00, inr, 1L))
        assertFalse(fixes.undo("nobody", 5, 1L, inr))
        assertFalse(fixes.undo("cash", r.deltaMinor, r.atMillis, Currency.USD))
        assertEquals(100_00L, balance(cash))
    }

    // ---- parity and totals ----

    @Test
    fun theSqlBalancesAndTheInMemoryLedgerAgreeAfterFixesOnEveryKindOfAccountAndEveryTotalOfSpendingIsUntouched() {
        spend(bank, at(2026, 11, 1), 1_234_56)
        spend(card, at(2026, 11, 2), 700_00, "Fun")
        ledger.save(Transfer(EntryId("t1"), at(2026, 11, 3), bank, cash, Money(500_00, inr)))
        ledger.save(Income(EntryId("pay"), at(2026, 11, 4), bank, Money(60_000_00, inr), CategoryId("Other")))
        ledger.save(Refund(EntryId("rf"), at(2026, 11, 5), card, Money(100_00, inr), EntryId("e1")))
        val spendBefore = ledger.spendByCategory(inr)
        val period = DatePeriod(LocalDate(2026, 11, 1), LocalDate(2026, 12, 1))
        val reportBefore = reports.spent(period, zone, inr)
        fixed(fixes.fix("bank", 70_000_00, inr, 1L)); fixed(fixes.fix("card", -1_000_00, inr, 2L)); fixed(fixes.fix("cash", 0, inr, 3L))
        assertEquals(ledger.loadLedger().balances(), ledger.balances(), "SQL and in-memory balances agree")
        assertEquals(70_000_00L, balance(bank)); assertEquals(-1_000_00L, balance(card)); assertEquals(0L, balance(cash))
        assertEquals(spendBefore, ledger.spendByCategory(inr), "category totals")
        assertEquals(spendBefore, ledger.loadLedger().spendByCategory(inr), "category totals, in memory")
        assertEquals(reportBefore, reports.spent(period, zone, inr), "the period's spending in reports")
        assertEquals(5, db.schemaQueries.entryCount().executeAsOne(), "the same five entries")
    }

    @Test
    fun balancesAtEarlierDatesMoveByTheSameAmountSoTheHistoryOfTheBalanceStaysRight() {
        spend(card, at(2026, 10, 20), 3_000_00)
        val atStart = db.schemaQueries.accountBalanceBefore(at(2026, 10, 1), "card").executeAsOne().let { (it as Any?) as Number }.toLong()
        val atEnd = db.schemaQueries.accountBalanceBefore(at(2026, 12, 1), "card").executeAsOne().let { (it as Any?) as Number }.toLong()
        assertEquals(0L, atStart); assertEquals(-3_000_00L, atEnd)
        fixed(fixes.fix("card", -5_500_00, inr, 1L))
        val s2 = db.schemaQueries.accountBalanceBefore(at(2026, 10, 1), "card").executeAsOne().let { (it as Any?) as Number }.toLong()
        val e2 = db.schemaQueries.accountBalanceBefore(at(2026, 12, 1), "card").executeAsOne().let { (it as Any?) as Number }.toLong()
        assertEquals(-2_500_00L, s2, "the missing spending counts as owed before the first entry the app knows")
        assertEquals(-5_500_00L, e2)
        assertEquals(atEnd - atStart, e2 - s2, "what the entries do between the two dates is unchanged (the 3,000 spent)")
    }

    // ---- what must not move ----

    @Test
    fun transferCandidatesTheSubscriptionSamplesAndTheCardPaymentTransfersAreUnaffectedByAFix() {
        val origins = OriginStore(db); val expects = ExpectStore(db); val attachments = AttachmentStore(db, clock) { 3L }
        val templates = TemplateStore(db, clock) { 3L }
        val candidates = TransferCandidateStore(db, KindChanges(db, ledger, origins, expects, attachments), templates)
        // a cash withdrawal the finder knows, a monthly charge the subscription finder samples, and a card bill paid from the bank (a transfer)
        ledger.save(Expense(EntryId("atm"), at(2026, 10, 3), bank, Money(5_000_00, inr), listOf(CategorySplit(CategoryId("Other"), Money(5_000_00, inr)))), EntryDetails(merchant = "ATM cash", source = "sms", sourceRef = "R1"))
        origins.put("atm", OriginKind.SMS, "AX-BANK", "Rs.5,000.00 withdrawn at ATM 12345 on 03-10-26", at(2026, 10, 3))
        for (m in 8..10) ledger.save(Expense(EntryId("nf$m"), at(2026, m, 5), card, Money(649_00, inr), listOf(CategorySplit(CategoryId("Fun"), Money(649_00, inr)))), EntryDetails(merchant = "Netflix"))
        ledger.save(Transfer(EntryId("billpay"), at(2026, 10, 25), bank, card, Money(1_947_00, inr)))
        val candidatesBefore = candidates.scan("INR")
        val samplesBefore = db.schemaQueries.expenseSamples("INR", 0L).executeAsList()
        val transferBefore = db.schemaQueries.entryById("billpay").executeAsOne()
        assertEquals(listOf("atm"), candidatesBefore.cashWithdrawals.map { it.entry.id }, "the withdrawal is found")
        fixed(fixes.fix("bank", 1_234_56, inr, 1L)); fixed(fixes.fix("card", -9_999_00, inr, 2L))
        assertEquals(candidatesBefore, candidates.scan("INR"), "Transfer candidates read entries only")
        assertEquals(samplesBefore, db.schemaQueries.expenseSamples("INR", 0L).executeAsList(), "so does the subscription finder")
        assertEquals(transferBefore, db.schemaQueries.entryById("billpay").executeAsOne(), "the card payment is the same transfer, untouched")
    }

    // ---- the history ----

    @Test
    fun theHistoryKeepsTheNewestTwentyForEachAccountAndNothingForOthers() {
        for (i in 1..23) fixed(fixes.fix("cash", i * 100L, inr, i.toLong()))
        assertEquals(20, fixes.history("cash").size)
        assertEquals(4L, fixes.history("cash").first().atMillis); assertEquals(23L, fixes.history("cash").last().atMillis)
        assertTrue(fixes.history("bank").isEmpty())
        fixed(fixes.fix("bank", 24_000_00, inr, 99L))
        assertEquals(setOf("cash", "bank"), fixes.allHistory().keys)
    }

    @Test
    fun theHintStaysDismissedAndOneAccountsAnswerIsNotAnothers() {
        assertTrue(fixes.dismissedHints().isEmpty())
        fixes.dismissHint("card")
        assertEquals(setOf("card"), fixes.dismissedHints())
        fixes.dismissHint("card")
        assertEquals(setOf("card"), fixes.dismissedHints(), "twice is once")
        fixed(fixes.fix("cash", 5_00, inr, 1L))
        assertEquals(setOf("card", "cash"), fixes.dismissedHints())
    }
}
