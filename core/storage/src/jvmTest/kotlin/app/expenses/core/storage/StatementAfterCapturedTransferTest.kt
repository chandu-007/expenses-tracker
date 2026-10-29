package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Flow
import app.expenses.core.domain.Money
import app.expenses.core.domain.StatementTxn
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A move of money between two of the person's own accounts that was captured from the bank's two texts is ONE transfer entry. The two statements that later show it (a debit on the
 * source account's, a credit on the destination's) must not add an expense and an income on top: the import answers each line with the transfer it already has, once per side. All data is invented.
 */
class StatementAfterCapturedTransferTest {
    private val inr = Currency.INR
    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val ledger = LedgerStore(db, HlcClock("n")) { 1L }
    private val importer = StatementImporter(db, ledger)
    private val main = AccountId("main"); private val savings = AccountId("savings")
    private fun d(day: Int) = LocalDate(2026, 10, 1).plus(day - 1, DateTimeUnit.DAY)
    private fun noon(day: Int) = d(day).atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L
    private fun rows() = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList()

    init {
        ledger.saveAccount(Account(main, "Main", AccountType.BANK, inr, openingMinor = 200_000_00))
        ledger.saveAccount(Account(savings, "Savings", AccountType.BANK, inr))
        // captured from two texts on the 29th: Main to Savings
        ledger.save(Transfer(EntryId("t"), noon(29), main, savings, Money(45_250_75, inr)), EntryDetails(merchant = "Trip", source = "sms", sourceRef = "ref"))
    }

    private fun debit(day: Int, minor: Long, text: String = "TPT-Trip-SAVINGS") = StatementTxn(day, d(day), Flow.SPENDING, minor, text)
    private fun credit(day: Int, minor: Long, text: String = "TPT-Trip-MAIN") = StatementTxn(day, d(day), Flow.INCOME, minor, text)

    @Test
    fun theSourceStatementsDebitIsTheCapturedTransferNotANewExpense() {
        val r = importer.import(listOf(debit(29, 45_250_75)), main, inr, zone)
        assertEquals(0, r.imported); assertEquals(1, r.matchedExisting, "the person is told it was matched")
        assertEquals(listOf("TRANSFER"), rows().map { it.kind })
    }

    @Test
    fun theDestinationStatementsCreditIsTheSameTransferNotNewIncome() {
        val r = importer.import(listOf(credit(30, 45_250_75)), savings, inr, zone) // a bank may date the credit the next day
        assertEquals(0, r.imported); assertEquals(1, r.matchedExisting)
        assertEquals(listOf("TRANSFER"), rows().map { it.kind })
    }

    @Test
    fun bothStatementsOfOneFileAnswerFromTheOneTransferAndNothingIsDoubleCounted() {
        val a = importer.import(listOf(debit(29, 45_250_75)), main, inr, zone)
        val b = importer.import(listOf(credit(29, 45_250_75)), savings, inr, zone)
        assertEquals(0, a.imported + b.imported)
        assertEquals(1, rows().size)
        assertEquals(Money(200_000_00 - 45_250_75, inr), ledger.balances()[main]); assertEquals(Money(45_250_75, inr), ledger.balances()[savings])
    }

    @Test
    fun aDifferentAmountAnotherAccountOrADayAwayIsStillImported() {
        val r = importer.import(listOf(debit(29, 45_250_76), debit(33, 45_250_75)), main, inr, zone)
        assertEquals(2, r.imported, "another amount, and the right amount four days later, are real lines")
        val other = importer.import(listOf(credit(29, 45_250_75)), main, inr, zone)
        assertEquals(1, other.imported, "a credit on the SOURCE account is not the transfer's credit")
    }

    @Test
    fun twoSameAmountLinesOnOneDayAreNotBothSwallowedByTheOneTransfer() {
        val r = importer.import(listOf(debit(29, 45_250_75, "TPT-Trip-SAVINGS"), debit(29, 45_250_75, "NEFT-Another-PERSON")), main, inr, zone)
        assertEquals(1, r.imported); assertEquals(1, r.matchedExisting, "each existing entry answers for one line")
    }

    @Test
    fun aRealPaymentOfTheSameAmountOnTheSameDayIsImportedWhenTheFileHasNoRowForTheTransfer() {
        // a captured 45,250.75 transfer, and a file with only a Zomato order of that very amount on the day
        val r = importer.import(listOf(debit(29, 45_250_75, "UPI-ZOMATO-zomato@icici-123456789012")), main, inr, zone)
        assertEquals(1, r.imported); assertEquals(0, r.matchedExisting)
        assertEquals(setOf("TRANSFER", "EXPENSE"), rows().map { it.kind }.toSet())
        // the same for a credit that is a real payment in
        val c = importer.import(listOf(credit(29, 45_250_75, "UPI-RAVI KUMAR-ravi@okaxis-123456789012")), savings, inr, zone)
        assertEquals(1, c.imported)
    }

    @Test
    fun aHandTypedSweepOnTheDayBeforeDoesNotSwallowARealRentRowOfTheSameAmount() {
        ledger.save(Transfer(EntryId("sweep"), noon(27), main, savings, Money(25_000_00, inr)), EntryDetails(merchant = "Sweep"))
        val r = importer.import(listOf(debit(28, 25_000_00, "NEFT-RENT-LANDLORD R RAO")), main, inr, zone)
        // a rail word is wording a transfer might carry: it is matched; plain words are not
        assertEquals(1, r.matchedExisting)
        val real = importer.import(listOf(debit(28, 25_000_00, "RENT FOR OCTOBER")), main, inr, zone)
        assertEquals(1, real.imported, "no transfer wording: a real rent payment")
    }

    @Test
    fun aRowThatNamesTheOtherAccountsDigitsOrWhoseFileHoldsTheOtherSideIsTheTransfer() {
        lookStore().save(app.expenses.core.domain.AccountLook("savings", last4 = "5678"))
        val digits = importer.import(listOf(debit(29, 45_250_75, "TO A/C 5678 ONLINE")), main, inr, zone)
        assertEquals(0, digits.imported); assertEquals(1, digits.matchedExisting)
        // no words at all, but one file holds both statements (rows say their own account): both sides match the one transfer
        val both = importer.import(listOf(StatementTxn(1, d(29), Flow.SPENDING, 45_250_75, "ONLINE", accountId = "main"), StatementTxn(2, d(29), Flow.INCOME, 45_250_75, "ONLINE", accountId = "savings")), main, inr, zone)
        assertEquals(0, both.imported); assertEquals(2, both.matchedExisting)
        assertEquals(1, rows().size)
    }

    private fun lookStore() = AccountLookStore(db, HlcClock("look")) { 7L }
}
