package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class StatementImporterTest {
    private val inr = Currency.INR
    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val categories = CategoryStore(db, clock) { 1L }
    private val ledger = LedgerStore(db, clock) { 1L }
    private val importer = StatementImporter(db, ledger)
    private val bank = AccountId("bank")
    private fun d(day: Int) = LocalDate(2026, 10, 1).plus(day - 1, DateTimeUnit.DAY)

    init {
        ledger.saveAccount(Account(bank, "Bank", AccountType.BANK, inr))
        ledger.saveAccount(Account(AccountId("usd"), "USD", AccountType.BANK, Currency.USD))
    }

    private fun spend(day: Int, minor: Long, text: String, row: Int = day) = StatementTxn(row, d(day), Flow.SPENDING, minor, text)
    private fun income(day: Int, minor: Long, text: String, row: Int = day) = StatementTxn(row, d(day), Flow.INCOME, minor, text)
    private fun count() = db.schemaQueries.entryCount().executeAsOne()
    private fun ids() = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.id }

    @Test
    fun spendingBecomesExpensesIncomeBecomesIncomeAndAmountsAreExact() {
        val r = importer.import(listOf(spend(1, 25_050, "UPI SWIGGY"), income(2, 5_000_000, "SALARY")), bank, inr, zone)
        assertEquals(ImportResult(2, 0, 25_050, 5_000_000), r)
        val rows = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList()
        assertEquals(listOf("EXPENSE", "INCOME"), rows.map { it.kind })
        assertEquals(listOf(25_050L, 5_000_000L), rows.map { it.amount_minor })
        assertEquals(listOf("UPI SWIGGY", "SALARY"), rows.map { it.merchant })
        assertEquals(setOf("csv"), rows.map { it.source }.toSet())
        assertEquals(d(1).atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L, rows[0].occurred_at, "noon of the day in the user's zone")
        assertEquals(mapOf<CategoryId?, Money>(null to Money(25_050, inr)), ledger.spendByCategory(inr), "left uncategorised")
        assertEquals(1, db.search("swiggy").size, "the description can be searched")
    }

    @Test
    fun importingTheSameFileAgainAddsNothingAndChangesNothing() {
        val file = listOf(spend(1, 100, "A"), spend(2, 200, "B"), income(3, 300, "C"))
        importer.import(file, bank, inr, zone)
        val before = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList()
        val again = importer.import(file, bank, inr, zone)
        assertEquals(ImportResult(0, 3, 0, 0), again)
        assertEquals(before, db.schemaQueries.liveEntriesInInsertionOrder().executeAsList())
    }

    @Test
    fun twoIdenticalRowsOnOneDayAreTwoEntriesAndStayTwoOnReimport() {
        val file = listOf(spend(1, 4_000, "CHAI STALL"), spend(1, 4_000, "CHAI STALL"), spend(1, 4_000, "chai   stall"))
        assertEquals(3, importer.import(file, bank, inr, zone).imported, "same text apart from case and spacing counts as identical, and each is kept")
        assertEquals(3L, count())
        assertEquals(3, importer.import(file, bank, inr, zone).alreadyThere)
        assertEquals(3L, count())
        assertEquals(3, ids().toSet().size)
    }

    @Test
    fun anOverlappingExportAddsOnlyWhatIsNew() {
        val october = (1..20).map { spend(it, 100L + it, "TXN $it") }
        val laterExport = (10..30).map { spend(it, 100L + it, "TXN $it") } // overlaps days 10 to 20
        importer.import(october, bank, inr, zone)
        val r = importer.import(laterExport, bank, inr, zone)
        assertEquals(11, r.alreadyThere)
        assertEquals(10, r.imported)
        assertEquals(30L, count())
    }

    @Test
    fun theSameRowsInADifferentOrderOrASeparateImportGiveTheSameEntries() {
        val rows = (1..30).map { spend(it % 7 + 1, 100L * (it % 5 + 1), "SHOP ${it % 4}") } + (1..5).map { income(it, 1_000, "PAY") }
        importer.import(rows, bank, inr, zone)
        val first = ids().sorted()
        val other = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
        val otherLedger = LedgerStore(other, HlcClock("m")) { 1L }
        otherLedger.saveAccount(Account(bank, "Bank", AccountType.BANK, inr))
        StatementImporter(other, otherLedger).import(rows.shuffled(Random(3)), bank, inr, zone)
        val second = other.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.id }.sorted()
        // shuffling changes which identical row is "first", but the set of ids is the same because identical rows are interchangeable
        assertEquals(first, second)
    }

    @Test
    fun aDeletedImportIsNotBroughtBackAndAnEditedOneIsNotOverwritten() {
        val file = listOf(spend(1, 100, "A"), spend(2, 200, "B"))
        importer.import(file, bank, inr, zone)
        val (a, b) = ids()
        ledger.delete(EntryId(a))
        ledger.save(Expense(EntryId(b), d(2).atStartOfDayIn(zone).toEpochMilliseconds(), bank, Money(999, inr)), EntryDetails(merchant = "edited"))
        val r = importer.import(file, bank, inr, zone)
        assertEquals(ImportResult(0, 2, 0, 0), r)
        assertEquals(listOf(b), ids(), "the deleted one stays deleted")
        assertEquals(999L, db.schemaQueries.entryById(b).executeAsOne().amount_minor, "the edit is kept")
    }

    @Test
    fun rowsAreSavedOldestFirstWhateverTheFileOrder() {
        importer.import(listOf(spend(5, 1, "E"), spend(1, 1, "A"), spend(3, 1, "C"), spend(1, 2, "A2")), bank, inr, zone)
        assertEquals(listOf("A", "A2", "C", "E"), db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.merchant })
    }

    @Test
    fun differentAccountsAmountsDaysAndDirectionsAreNeverMistakenForDuplicates() {
        ledger.saveAccount(Account(AccountId("card"), "Card", AccountType.BANK, inr))
        importer.import(listOf(spend(1, 100, "X")), bank, inr, zone)
        assertEquals(1, importer.import(listOf(spend(1, 100, "X")), AccountId("card"), inr, zone).imported, "another account")
        assertEquals(1, importer.import(listOf(spend(1, 101, "X")), bank, inr, zone).imported, "another amount")
        assertEquals(1, importer.import(listOf(spend(2, 100, "X")), bank, inr, zone).imported, "another day")
        assertEquals(1, importer.import(listOf(income(1, 100, "X")), bank, inr, zone).imported, "the other direction")
        assertEquals(1, importer.import(listOf(spend(1, 100, "Y")), bank, inr, zone).imported, "another description")
        assertEquals(6L, count(), "the first one plus five that each differ in exactly one way")
    }

    @Test
    fun aReExportWithDifferentCaseOrSpacingInADescriptionIsStillTheSameTransaction() {
        importer.import(listOf(spend(1, 100, "UPI  SWIGGY LTD")), bank, inr, zone)
        val r = importer.import(listOf(spend(1, 100, "upi swiggy   ltd "), income(2, 5, "x")), bank, inr, zone)
        assertEquals(1, r.alreadyThere)
        assertEquals(2L, count())
    }

    @Test
    fun aBadAccountOrCurrencyIsRefusedAndNothingIsSaved() {
        assertFailsWith<IllegalArgumentException> { importer.import(listOf(spend(1, 1, "A")), AccountId("nope"), inr, zone) }
        assertEquals("That account is in USD, not INR", assertFailsWith<IllegalArgumentException> { importer.import(listOf(spend(1, 1, "A")), AccountId("usd"), inr, zone) }.message, "refused by the importer itself")
        assertEquals(0L, count())
    }

    @Test
    fun aFailureInTheMiddleSavesNothing() {
        // a zero amount breaks the ledger's rules part-way through
        assertFailsWith<IllegalArgumentException> { importer.import(listOf(spend(1, 100, "OK"), spend(2, 100, "OK2"), spend(3, 0, "ZERO")), bank, inr, zone) }
        assertEquals(0L, count())
    }

    @Test
    fun aBigStatementImportsInReasonableTimeAndEveryRowIsCountedOnce() {
        val rnd = Random(5)
        val rows = (1..5_000).map { i -> spend(1 + rnd.nextInt(28), 100L + rnd.nextInt(90_000), "MERCHANT ${rnd.nextInt(300)}", row = i) }
        val started = System.nanoTime()
        val r = importer.import(rows, bank, inr, zone)
        val seconds = (System.nanoTime() - started) / 1e9
        assertEquals(5_000, r.imported)
        assertEquals(5_000L, count())
        assertEquals(rows.sumOf { it.minor }, ledger.spendByCategory(inr).getValue(null).minorUnits)
        assertEquals(ledger.spendByCategory(inr), ledger.loadLedger().spendByCategory(inr), "SQL totals and the in-memory ledger agree")
        assertEquals(0, importer.import(rows, bank, inr, zone).imported)
        assertTrue(seconds < 30, "5,000 rows took $seconds s")
        println("IMPORT 5000 rows: ${"%.2f".format(seconds)} s")
    }

    @Test
    fun aRowMatchingAnEntryFromAnotherSourceIsNotAddedAgainAndIsCountedAndEachEntryAnswersForOneRow() {
        fun at(day: Int, hour: Int = 15) = d(day).atStartOfDayIn(zone).toEpochMilliseconds() + hour * 3_600_000L
        ledger.save(Expense(EntryId("sms1"), at(3), bank, Money(25_000, inr)), EntryDetails(merchant = "from a text", source = "sms", sourceRef = "R1"))
        ledger.save(Expense(EntryId("hand"), at(5), bank, Money(40_000, inr)), EntryDetails(merchant = "typed"))
        ledger.save(app.expenses.core.domain.ledger.Income(EntryId("sal"), at(6), bank, Money(900_000, inr)), EntryDetails(merchant = "salary text", source = "sms", sourceRef = "R2"))
        ledger.save(Expense(EntryId("two"), at(12), bank, Money(77_000, inr)), EntryDetails(merchant = "two days before the row", source = "sms", sourceRef = "R3"))
        ledger.saveAccount(Account(AccountId("other"), "Other", AccountType.BANK, inr))
        ledger.save(Expense(EntryId("elsewhere"), at(20), AccountId("other"), Money(55_000, inr)), EntryDetails(merchant = "other account", source = "sms", sourceRef = "R4"))
        val rows = listOf(
            spend(14, 77_000, "TWO DAYS ON", row = 7),   // two days from the nearest entry: a different payment
            spend(20, 55_000, "OTHER ACCOUNT ROW", row = 8), // an entry of the same amount exists, in another account
            spend(3, 25_000, "SHOP A", row = 1),   // the text's entry, same day
            spend(4, 40_000, "SHOP B", row = 2),   // the typed one, a day off
            income(6, 900_000, "SALARY", row = 3),
            spend(3, 25_000, "SHOP A AGAIN", row = 4), // a second same-amount purchase: the one entry cannot answer for both
            spend(9, 25_000, "OTHER", row = 5),   // too far from any entry
            income(3, 25_000, "REFUND-ISH", row = 6), // same amount, other direction
        )
        val r = importer.import(rows, bank, inr, zone)
        assertEquals(3, r.matchedExisting); assertEquals(5, r.imported)
        assertEquals(setOf("SHOP A AGAIN", "OTHER", "REFUND-ISH", "TWO DAYS ON", "OTHER ACCOUNT ROW"), db.schemaQueries.recentEntries(50).executeAsList().filter { it.source == "csv" }.map { it.merchant }.toSet())
        assertEquals(5, importer.import(rows, bank, inr, zone).let { it.alreadyThere }, "the rows that were added are recognised on a second run, and the matched ones match again")
    }

    @Test
    fun theSameLineImportedAgainUnderADifferentDescriptionIsNotAddedTwice() {
        val raw = "01/10/26 UPI-ZERODHA COINS-ZERODHA.NSEINVEST.BRK@VALIDHDFC-HDFC0MERUPI-104184779254 5,000.00"
        val before = StatementTxn(1, d(1), Flow.SPENDING, 500_000, "UPI-ZERODHA COINS-ZERODHA.NSEINVEST.BRK@VALIDHDFC-HDFC0MERUPI-104184779254", sourceText = raw)
        assertEquals(1, importer.import(listOf(before), bank, inr, zone).imported)
        // a later version tidies the description: the file is the same
        val tidied = before.copy(description = "ZERODHA COINS")
        val again = importer.import(listOf(tidied), bank, inr, zone)
        assertEquals(0, again.imported, "nothing is added twice")
        assertEquals(1, again.alreadyThere)
        // two real lines alike on one day still both come in the first time, and each answers for one entry the second time
        val twoLines = listOf(tidied.copy(row = 2, description = "A", sourceText = "x 100.00", minor = 10_000), tidied.copy(row = 3, description = "A", sourceText = "x 100.00", minor = 10_000))
        assertEquals(2, importer.import(twoLines, bank, inr, zone).imported)
        assertEquals(0, importer.import(twoLines.map { it.copy(description = "B") }, bank, inr, zone).imported)
    }
}
