package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Flow
import app.expenses.core.domain.Money
import app.expenses.core.domain.StatementTxn
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
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
import kotlin.test.assertFailsWith

/** Card payments imported from a bank statement as transfers, and the rows of other apps' files with their own account, category, note and labels. All data is invented. */
class StatementImporterTransferTest {
    private val inr = Currency.INR
    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val ledger = LedgerStore(db, clock) { 1L }
    private val categories = CategoryStore(db, clock) { 1L }
    private val importer = StatementImporter(db, ledger)
    private val bank = AccountId("bank")
    private val card = AccountId("card")
    private val card2 = AccountId("card2")
    private fun d(day: Int) = LocalDate(2026, 10, 1).plus(day - 1, DateTimeUnit.DAY)
    private fun at(day: Int) = d(day).atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    init {
        ledger.saveAccount(Account(bank, "Bank", AccountType.BANK, inr, openingMinor = 100_000_00))
        ledger.saveAccount(Account(card, "HDFC Regalia", AccountType.CREDIT_CARD, inr))
        ledger.saveAccount(Account(card2, "ICICI Amazon", AccountType.CREDIT_CARD, inr))
        ledger.saveAccount(Account(AccountId("usd"), "Dollar card", AccountType.CREDIT_CARD, Currency.USD))
    }

    private fun spend(day: Int, minor: Long, text: String, to: AccountId? = null, source: String? = null) = StatementTxn(day, d(day), Flow.SPENDING, minor, text, source, to?.value)
    private fun credit(day: Int, minor: Long, text: String, from: AccountId? = null) = StatementTxn(day, d(day), Flow.INCOME, minor, text, null, from?.value)
    private fun rows() = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList()
    private fun count() = rows().size

    @Test
    fun aCardPaymentBecomesATransferFromTheBankToTheCardAndIsNeitherSpendingNorIncome() {
        val r = importer.import(listOf(spend(1, 5_000_00, "UPI SWIGGY"), spend(2, 30_000_00, "CREDIT CARD PAYMENT XX1234", card)), bank, inr, zone)
        assertEquals(2, r.imported); assertEquals(1, r.transfers); assertEquals(30_000_00L, r.transferMinor)
        assertEquals(5_000_00L, r.spendingMinor, "the payment is not spending"); assertEquals(0L, r.incomeMinor)
        val t = rows().single { it.kind == "TRANSFER" }
        assertEquals(bank.value, t.account_id); assertEquals(card.value, t.counter_account_id)
        assertEquals(30_000_00L, t.amount_minor); assertEquals(30_000_00L, t.to_amount_minor)
        assertEquals("CREDIT CARD PAYMENT XX1234", t.merchant); assertEquals("csv", t.source)
        assertEquals(at(2), t.occurred_at)
        val b = ledger.balances()
        assertEquals(Money(100_000_00 - 5_000_00 - 30_000_00, inr), b[bank], "money left the bank once for the payment")
        assertEquals(Money(30_000_00, inr), b[card], "and reduced what the card owes")
        assertEquals(Money(5_000_00, inr), ledger.spendByCategory(inr).values.fold(Money.zero(inr)) { a, m -> a + m }, "spending is only the purchase")
    }

    @Test
    fun theLineIsKeptAsTheOriginOfTheTransfer() {
        importer.import(listOf(spend(2, 100, "CC PAYMENT", card, source = "02/10/2026, CC PAYMENT, 1.00")), bank, inr, zone)
        val id = rows().single().id
        assertEquals(1, db.schemaQueries.originOf(id).executeAsList().size)
    }

    @Test
    fun importingTheSameFileAgainAddsNothingEvenWhenAnotherCardIsChosenTheSecondTime() {
        val file = listOf(spend(1, 100_00, "SHOP"), spend(2, 30_000_00, "CREDIT CARD PAYMENT", card))
        importer.import(file, bank, inr, zone)
        val before = rows()
        assertEquals(2, importer.import(file, bank, inr, zone).alreadyThere)
        val other = importer.import(listOf(spend(1, 100_00, "SHOP"), spend(2, 30_000_00, "CREDIT CARD PAYMENT", card2)), bank, inr, zone)
        assertEquals(0, other.imported); assertEquals(2, other.alreadyThere, "the same line is one payment, whichever card it was said to be for")
        assertEquals(before, rows())
    }

    @Test
    fun aLineAnEarlierVersionImportedAsSpendingIsNotImportedAgainAsATransfer() {
        importer.import(listOf(spend(2, 30_000_00, "CREDIT CARD PAYMENT")), bank, inr, zone)
        assertEquals("EXPENSE", rows().single().kind)
        val r = importer.import(listOf(spend(2, 30_000_00, "CREDIT CARD PAYMENT", card)), bank, inr, zone)
        assertEquals(0, r.imported); assertEquals(1, r.alreadyThere)
        assertEquals(1, count())
    }

    @Test
    fun aLineImportedAsATransferIsNotImportedAgainAsSpendingWhenThePersonLaterLeavesItAsItStands() {
        importer.import(listOf(spend(2, 30_000_00, "CREDIT CARD PAYMENT", card)), bank, inr, zone)
        val r = importer.import(listOf(spend(2, 30_000_00, "CREDIT CARD PAYMENT")), bank, inr, zone)
        assertEquals(0, r.imported); assertEquals(1, r.alreadyThere)
        assertEquals(listOf("TRANSFER"), rows().map { it.kind })
        // an ordinary line is, of course, still imported
        assertEquals(1, importer.import(listOf(spend(3, 100, "SHOP")), bank, inr, zone).imported)
    }

    @Test
    fun aTransferTheSameCardPaymentAlreadyMadeByMarkPaidIsNotAddedTwice() {
        // Mark paid records a transfer from the chosen account to the card, dated the day it was tapped: here two days after the bank took the money
        ledger.save(Transfer(EntryId("markpaid"), at(4), bank, card, Money(30_000_00, inr)), EntryDetails(note = "Card bill payment"))
        val r = importer.import(listOf(spend(2, 30_000_00, "CREDIT CARD PAYMENT", card)), bank, inr, zone)
        assertEquals(0, r.imported); assertEquals(1, r.matchedExisting)
        assertEquals(1, count())
    }

    @Test
    fun aTransferToAnotherCardAnotherAmountOrTooFarAwayIsNotTheSamePayment() {
        ledger.save(Transfer(EntryId("markpaid"), at(2), bank, card2, Money(30_000_00, inr)))
        assertEquals(1, importer.import(listOf(spend(2, 30_000_00, "CREDIT CARD PAYMENT", card)), bank, inr, zone).imported, "another card")
        assertEquals(1, importer.import(listOf(spend(2, 29_999_00, "CC PAYMENT", card2)), bank, inr, zone).imported, "another amount")
        ledger.save(Transfer(EntryId("far"), at(20), bank, card, Money(7_000_00, inr)))
        assertEquals(1, importer.import(listOf(spend(10, 7_000_00, "CC PAYMENT", card)), bank, inr, zone).imported, "ten days apart")
        assertEquals(1, importer.import(listOf(spend(23, 7_000_00, "CC PAYMENT", card)), bank, inr, zone).matchedExisting, "three days apart is the same payment")
    }

    @Test
    fun eachExistingTransferAnswersForOneRowOnly() {
        ledger.save(Transfer(EntryId("markpaid"), at(2), bank, card, Money(5_000_00, inr)))
        val r = importer.import(listOf(spend(2, 5_000_00, "CC PAYMENT", card), spend(2, 5_000_00, "CC PAYMENT 2", card)), bank, inr, zone)
        assertEquals(1, r.matchedExisting); assertEquals(1, r.imported, "two real payments of the same amount are two, one of them already there")
    }

    @Test
    fun aPaymentCapturedFromATextAsAnExpenseIsTheSameLineAsTheStatementsPayment() {
        ledger.save(Expense(EntryId("text"), at(2), bank, Money(30_000_00, inr)), EntryDetails(merchant = "credit card payment", source = "sms", sourceRef = "r1"))
        val r = importer.import(listOf(spend(2, 30_000_00, "CREDIT CARD PAYMENT", card)), bank, inr, zone)
        assertEquals(0, r.imported); assertEquals(1, r.matchedExisting)
    }

    @Test
    fun theBanksStatementAndTheCardsStatementForOnePaymentAreOneTransfer() {
        importer.import(listOf(spend(2, 30_000_00, "CREDIT CARD PAYMENT XX1234", card)), bank, inr, zone)
        // the card's own statement shows it as a credit, a day later, and the person records it as coming from the bank
        val r = importer.import(listOf(credit(3, 30_000_00, "PAYMENT RECEIVED - THANK YOU", bank)), card, inr, zone)
        assertEquals(0, r.imported); assertEquals(1, r.matchedExisting)
        assertEquals(listOf("TRANSFER"), rows().map { it.kind })
    }

    @Test
    fun aTransferIntoTheImportedCardIsRecordedFromTheOtherAccount() {
        val r = importer.import(listOf(credit(3, 12_000_00, "PAYMENT RECEIVED", bank)), card, inr, zone)
        assertEquals(1, r.transfers); assertEquals(0L, r.incomeMinor)
        val t = rows().single()
        assertEquals(bank.value, t.account_id); assertEquals(card.value, t.counter_account_id)
        // importing it again is already there; so is the same line from a bank's statement afterwards
        assertEquals(1, importer.import(listOf(credit(3, 12_000_00, "PAYMENT RECEIVED", bank)), card, inr, zone).alreadyThere)
        assertEquals(1, importer.import(listOf(spend(3, 12_000_00, "CC PAYMENT", card)), bank, inr, zone).matchedExisting)
    }

    @Test
    fun aTransferToAnAccountThatIsMissingInAnotherCurrencyOrTheSameOneSavesNothing() {
        val file = listOf(spend(1, 100, "SHOP"), spend(2, 200, "CC PAYMENT", card))
        assertFailsWith<IllegalArgumentException> { importer.import(file.dropLast(1) + spend(2, 200, "CC", AccountId("nope")), bank, inr, zone) }
        assertFailsWith<IllegalArgumentException> { importer.import(file.dropLast(1) + spend(2, 200, "CC", AccountId("usd")), bank, inr, zone) }
        assertFailsWith<IllegalArgumentException> { importer.import(file.dropLast(1) + spend(2, 200, "CC", bank), bank, inr, zone) }
        assertEquals(0, count(), "all or nothing")
    }

    @Test
    fun aRowOfAnotherAppsFileKeepsItsOwnAccountCategoryNoteAndLabels() {
        val food = categories.create("Team lunches").id
        val pay = categories.create("Consulting fees", kind = CategoryKind.INCOME).id
        val file = listOf(
            StatementTxn(1, d(1), Flow.SPENDING, 450_00, "Lunch", null, null, "Food", null, "Card", "with the team", listOf("work", "lunch"), accountId = card.value, categoryId = food),
            StatementTxn(2, d(2), Flow.INCOME, 85_000_00, "October pay", categoryId = pay),
            StatementTxn(3, d(3), Flow.SPENDING, 60_00, "Metro"),
        )
        val r = importer.import(file, bank, inr, zone)
        assertEquals(3, r.imported); assertEquals(450_00L + 60_00L, r.spendingMinor)
        val byName = rows().associateBy { it.merchant }
        assertEquals(card.value, byName["Lunch"]!!.account_id, "the row's own account")
        assertEquals(bank.value, byName["Metro"]!!.account_id, "the chosen one when the row names none")
        assertEquals("with the team", byName["Lunch"]!!.note)
        assertEquals(listOf("lunch", "work"), ledger.tagsOf(EntryId(byName["Lunch"]!!.id)).sorted())
        assertEquals(listOf(food), db.schemaQueries.splitsOf(byName["Lunch"]!!.id).executeAsList().map { it.category_id })
        assertEquals(pay, byName["October pay"]!!.category_id)
        assertEquals(null, db.schemaQueries.splitsOf(byName["Metro"]!!.id).executeAsList().firstOrNull()?.category_id, "no category stays none")
        assertEquals(3, importer.import(file, bank, inr, zone).alreadyThere, "the same file again adds nothing")
        assertEquals(3, count())
    }

    @Test
    fun theSameLineOnTwoAccountsIsTwoEntriesAndTheAccountIsPartOfWhatMakesItTheSame() {
        val a = StatementTxn(1, d(1), Flow.SPENDING, 100, "Tea", accountId = bank.value)
        val b = StatementTxn(1, d(1), Flow.SPENDING, 100, "Tea", accountId = card.value)
        assertEquals(2, importer.import(listOf(a, b), AccountId("card2"), inr, zone).imported)
        assertEquals(2, importer.import(listOf(a, b), AccountId("card2"), inr, zone).alreadyThere)
        // a row that names the import's own account is the very row that names none
        assertEquals(1, importer.import(listOf(a.copy(accountId = null)), bank, inr, zone).alreadyThere)
    }

    @Test
    fun anAccountOrACategoryThatIsNotThereOrIsInAnotherCurrencyRefusesTheWholeImport() {
        val ok = StatementTxn(1, d(1), Flow.SPENDING, 100, "Tea")
        assertFailsWith<IllegalArgumentException> { importer.import(listOf(ok, ok.copy(description = "x", accountId = "ghost")), bank, inr, zone) }
        assertFailsWith<IllegalArgumentException> { importer.import(listOf(ok, ok.copy(description = "x", accountId = "usd")), bank, inr, zone) }
        assertFailsWith<IllegalArgumentException> { importer.import(listOf(ok, ok.copy(description = "x", categoryId = "ghost")), bank, inr, zone) }
        assertEquals(0, count())
    }
}
