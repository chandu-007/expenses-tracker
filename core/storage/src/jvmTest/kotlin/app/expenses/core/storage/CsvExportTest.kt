package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CsvExportTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val categories = CategoryStore(db, clock) { 1L }
    private val ledger = LedgerStore(db, clock) { 1L }
    private val csv = CsvExport(db)
    private val ist = TimeZone.of("Asia/Kolkata")
    private fun at(y: Int, mo: Int, d: Int, h: Int, min: Int, zone: TimeZone = ist) = LocalDateTime(y, mo, d, h, min).toInstant(zone).toEpochMilliseconds()

    init {
        ledger.saveAccount(Account(AccountId("bank"), "HDFC, Savings", AccountType.BANK, inr))
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
    }

    private fun lines() = csv.build(ist).split("\r\n").also { assertEquals("", it.last(), "every row ends with CRLF") }.dropLast(1)

    @Test
    fun headerRowsAndPlainNumbersWithTheLocalDate() {
        ledger.save(Expense(EntryId("a"), at(2026, 10, 7, 23, 30), AccountId("cash"), m(123_456), listOf(CategorySplit(CategoryId("Food"), m(123_456)))), EntryDetails(merchant = "Cafe"))
        val l = lines()
        assertEquals("Date,Time,Type,Amount,Currency,Your share,Category,Account,To account,Person,Merchant,Note,Tags,Source", l[0])
        assertEquals("2026-10-07,23:30,Expense,1234.56,INR,1234.56,Food,Cash,,,Cafe,,,manual", l[1])
    }

    @Test
    fun tagsAreListedInTheirOwnColumnAndQuotedBecauseTheyContainCommas() {
        ledger.save(Expense(EntryId("a"), at(2026, 10, 7, 9, 0), AccountId("cash"), m(100)), EntryDetails(merchant = "X", tags = listOf("Trip", "80C")))
        assertEquals("2026-10-07,09:00,Expense,1.00,INR,1.00,,Cash,,,X,,\"80C, Trip\",manual", csv.build(ist).split("\r\n")[1])
    }

    @Test
    fun theDateFollowsTheTimeZoneAcrossMidnight() {
        ledger.save(Expense(EntryId("a"), at(2026, 10, 7, 20, 0, TimeZone.UTC), AccountId("cash"), m(100)))
        assertEquals("2026-10-07,20:00", csv.build(TimeZone.UTC).split("\r\n")[1].substringBefore(",Expense"))
        assertEquals("2026-10-08,01:30", csv.build(ist).split("\r\n")[1].substringBefore(",Expense"), "20:00 UTC is 01:30 the next day in India")
    }

    @Test
    fun commasQuotesAndLineBreaksAreQuotedPerRfc4180() {
        ledger.save(Expense(EntryId("a"), at(2026, 10, 7, 9, 0), AccountId("bank"), m(100), listOf(CategorySplit(CategoryId("Food"), m(100)))), EntryDetails(merchant = "Cafe, \"The\" Best", note = "line1\nline2"))
        assertEquals("""2026-10-07,09:00,Expense,1.00,INR,1.00,Food,"HDFC, Savings",,,"Cafe, ""The"" Best","line1
line2",,manual""", csv.build(ist).substringAfter("\r\n").removeSuffix("\r\n"))
    }

    @Test
    fun textThatWouldRunAsAFormulaIsDefusedButNumbersAreNot() {
        ledger.save(Expense(EntryId("a"), at(2026, 10, 7, 9, 0), AccountId("cash"), m(100)), EntryDetails(merchant = "=HYPERLINK(\"http://x\")", note = "-1+2"))
        val row = lines()[1]
        assertEquals(true, row.contains("'=HYPERLINK") && row.contains(",'-1+2,"))
        assertEquals("'=x", CsvExport.text("=x")); assertEquals("'+1", CsvExport.text("+1")); assertEquals("'@a", CsvExport.text("@a")); assertEquals("'\tx", CsvExport.text("\tx"))
        assertEquals("plain", CsvExport.text("plain")); assertEquals("", CsvExport.text("")); assertEquals("a=b", CsvExport.text("a=b"))
    }

    @Test
    fun splitSharedAndIncomeRowsSayWhatTheyAre() {
        ledger.save(Expense(EntryId("s"), at(2026, 10, 7, 9, 0), AccountId("cash"), m(90_000), listOf(CategorySplit(CategoryId("Food"), m(50_000)), CategorySplit(CategoryId("Fun"), m(40_000)))))
        ledger.save(Expense(EntryId("d"), at(2026, 10, 7, 10, 0), AccountId("cash"), m(300_000), listOf(CategorySplit(CategoryId("Food"), m(300_000))), listOf(Share(null, m(100_000)), Share(PersonId("asha"), m(200_000)))))
        ledger.save(Income(EntryId("i"), at(2026, 10, 7, 11, 0), AccountId("bank"), m(5_000_000), CategoryId("Other")))
        val l = lines()
        assertEquals("2026-10-07,09:00,Expense,900.00,INR,900.00,Food + Fun,Cash,,,,,,manual", l[1])
        assertEquals("2026-10-07,10:00,Expense,3000.00,INR,1000.00,Food,Cash,,,,,,manual", l[2], "Your share excludes what Asha owes")
        assertEquals("2026-10-07,11:00,Income,50000.00,INR,,Other,\"HDFC, Savings\",,,,,,manual", l[3])
    }

    @Test
    fun deletedEntriesAreLeftOutAndRowsAreOldestFirst() {
        ledger.save(Expense(EntryId("late"), at(2026, 10, 9, 9, 0), AccountId("cash"), m(100)), EntryDetails(merchant = "late"))
        ledger.save(Expense(EntryId("early"), at(2026, 10, 1, 9, 0), AccountId("cash"), m(100)), EntryDetails(merchant = "early"))
        ledger.save(Expense(EntryId("gone"), at(2026, 10, 5, 9, 0), AccountId("cash"), m(100)), EntryDetails(merchant = "gone"))
        ledger.delete(EntryId("gone"))
        assertEquals(listOf("early", "late"), lines().drop(1).map { it.split(",")[10] })
    }

    @Test
    fun theFileStartsWithAByteOrderMarkSoExcelReadsUnicode() {
        ledger.save(Expense(EntryId("a"), at(2026, 10, 7, 9, 0), AccountId("cash"), m(100)), EntryDetails(merchant = "चाय ₹"))
        val bytes = csv.bytes(ist)
        assertContentEquals(CsvExport.BOM, bytes.copyOfRange(0, 3))
        assertEquals(true, bytes.copyOfRange(3, bytes.size).decodeToString().contains("चाय ₹"))
    }

    @Test
    fun anEmptyLedgerStillGivesTheHeader() {
        assertEquals(listOf(CsvExport.HEADER.joinToString(",")), lines())
    }
}
