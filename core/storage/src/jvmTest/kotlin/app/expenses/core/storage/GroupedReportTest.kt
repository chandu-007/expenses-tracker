package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DatePeriod
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupedReportTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val zone = TimeZone.UTC
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val ledger = LedgerStore(db, HlcClock("n")) { 1L }
    private val reports = ReportStore(db, ledger)
    private val month = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
    private fun at(day: Int, h: Int = 12) = LocalDateTime(2026, 10, day, h, 0).toInstant(zone).toEpochMilliseconds()

    init {
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr)); ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        ledger.saveAccount(Account(AccountId("card"), "Card", AccountType.CREDIT_CARD, inr))
    }

    private fun populate(n: Int, rnd: Random) {
        val merchants = listOf("Swiggy", "swiggy ", "Uber", null, "Big Basket", "")
        val tags = listOf("Trip", "Tax", "trip", "Work")
        val cats = listOf("Food", "Bills", "Fun")
        repeat(n) { i ->
            val amount = rnd.nextLong(1, 500) * 100
            val shared = rnd.nextInt(5) == 0 && amount >= 200
            val tagList = (0 until rnd.nextInt(3)).map { tags[rnd.nextInt(tags.size)] }
            ledger.save(
                Expense(EntryId("e$i"), at(1 + rnd.nextInt(28), rnd.nextInt(24)), AccountId(listOf("bank", "cash", "card")[rnd.nextInt(3)]), m(amount), listOf(CategorySplit(CategoryId(cats[rnd.nextInt(3)]), m(amount))),
                    if (shared) listOf(Share(null, m(amount / 2)), Share(PersonId("asha"), m(amount - amount / 2))) else emptyList()),
                EntryDetails(merchant = merchants[rnd.nextInt(merchants.size)], tags = tagList),
            )
            if (rnd.nextInt(8) == 0 && !shared) ledger.save(Refund(EntryId("r$i"), at(28), AccountId("bank"), m(amount / 4 + 1), EntryId("e$i")))
        }
    }

    @Test
    fun accountsAndMerchantsEachPartitionTheSpendingAndDrillingIntoAnyRowReturnsExactlyItsTotal() {
        populate(300, Random(11))
        val total = reports.spent(month, zone, inr)
        for (g in listOf(ReportGroup.ACCOUNT, ReportGroup.MERCHANT)) {
            val rows = reports.groups(g, month, zone, inr)
            assertEquals(total, rows.fold(m(0)) { a, r -> a + r.total }, "$g rows add up to the month's spending")
            for (r in rows) {
                val target = if (g == ReportGroup.ACCOUNT) DrillTarget.InAccount(r.key!!) else DrillTarget.AtMerchant(r.key!!)
                val lines = reports.drill(month, zone, inr, target, 10_000)
                assertEquals(r.total, lines.fold(m(0)) { a, l -> a + l.amount }, "$g row '${r.name}': its entries add up to its total")
            }
        }
    }

    @Test
    fun tagsListAnEntryUnderEachOfItsTagsIgnoringCaseAndUntaggedSpendingStaysSeparateAndDrillsExactly() {
        populate(300, Random(12))
        val rows = reports.groups(ReportGroup.TAG, month, zone, inr)
        assertTrue(rows.map { it.name.lowercase() }.let { it.size == it.toSet().size }, "Trip and trip are one row: ${rows.map { it.name }}")
        val untagged = rows.firstOrNull { it.key == null }
        for (r in rows) {
            val target = if (r.key == null) DrillTarget.Untagged else DrillTarget.WithTag(r.key!!.lowercase()) // a different case still finds it
            assertEquals(r.total, reports.drill(month, zone, inr, target, 10_000).fold(m(0)) { a, l -> a + l.amount }, "tag row '${r.name}'")
        }
        // spending with no tag plus spending on entries with exactly one tag never exceeds the total
        assertTrue((untagged?.total?.minorUnits ?: 0) <= reports.spent(month, zone, inr).minorUnits)
        assertTrue(rows.sumOf { it.total.minorUnits } >= reports.spent(month, zone, inr).minorUnits, "multi-tagged entries count more than once, never less")
    }

    @Test
    fun rowsAreRankedAndMerchantsDifferingOnlyInCaseOrSpacesAreOne() {
        ledger.save(Expense(EntryId("a"), at(2), AccountId("cash"), m(1_000)), EntryDetails(merchant = "Swiggy"))
        ledger.save(Expense(EntryId("b"), at(3), AccountId("cash"), m(3_000)), EntryDetails(merchant = " swiggy "))
        ledger.save(Expense(EntryId("c"), at(4), AccountId("cash"), m(2_000)), EntryDetails(merchant = "Uber"))
        ledger.save(Expense(EntryId("d"), at(5), AccountId("cash"), m(500)))
        val rows = reports.groups(ReportGroup.MERCHANT, month, zone, inr)
        assertEquals(listOf("swiggy", "uber", ""), rows.map { it.key })
        assertEquals(listOf(m(4_000), m(2_000), m(500)), rows.map { it.total })
        assertEquals(listOf(61, 30, 7), rows.map { it.share }, "shares of the total, as whole percents")
        assertEquals("No merchant", rows.last().name)
    }

    @Test
    fun aRangeThatIsNotAMonthWorksAndOtherRangesAreExcluded() {
        ledger.save(Expense(EntryId("a"), at(10), AccountId("cash"), m(100))); ledger.save(Expense(EntryId("b"), at(20), AccountId("cash"), m(200))); ledger.save(Expense(EntryId("c"), at(25), AccountId("bank"), m(400)))
        val range = DatePeriod(LocalDate(2026, 10, 15), LocalDate(2026, 10, 26)) // 15th to the 25th inclusive
        assertEquals(m(600), reports.groups(ReportGroup.ACCOUNT, range, zone, inr).fold(m(0)) { a, r -> a + r.total })
        assertEquals(listOf(m(400), m(200)), reports.groups(ReportGroup.ACCOUNT, range, zone, inr).map { it.total })
    }

    @Test
    fun aMerchantRefundedMoreThanSpentThisMonthIsListedNegativeWithNoShare() {
        val sept = LocalDateTime(2026, 9, 10, 12, 0).toInstant(zone).toEpochMilliseconds()
        ledger.save(Expense(EntryId("old"), sept, AccountId("cash"), m(10_000), listOf(CategorySplit(CategoryId("Fun"), m(10_000)))), EntryDetails(merchant = "Shoes"))
        ledger.save(Refund(EntryId("back"), at(5), AccountId("cash"), m(4_000), EntryId("old")))
        ledger.save(Expense(EntryId("x"), at(6), AccountId("cash"), m(1_000)), EntryDetails(merchant = "Cafe"))
        val rows = reports.groups(ReportGroup.MERCHANT, month, zone, inr)
        assertEquals(listOf("cafe", "shoes"), rows.map { it.key })
        assertEquals(m(-4_000), rows.last().total); assertEquals(0, rows.last().share); assertEquals(100, rows.first().share)
    }

    @Test
    fun aRefundFollowsItsPurchaseForTagsAndMerchantButItsOwnAccountForAccounts() {
        ledger.save(Expense(EntryId("buy"), at(2), AccountId("card"), m(10_000)), EntryDetails(merchant = "Shoes", tags = listOf("Gift")))
        ledger.save(Refund(EntryId("back"), at(9), AccountId("bank"), m(4_000), EntryId("buy")))
        val tag = reports.groups(ReportGroup.TAG, month, zone, inr)
        assertEquals(listOf(m(6_000)), tag.map { it.total }, "Gift nets the refund; nothing is left under 'No tag'")
        assertEquals(m(6_000), reports.groups(ReportGroup.MERCHANT, month, zone, inr).single { it.key == "shoes" }.total)
        val acc = reports.groups(ReportGroup.ACCOUNT, month, zone, inr).associate { it.key to it.total }
        assertEquals(m(10_000), acc["card"]); assertEquals(m(-4_000), acc["bank"], "the money came back to the bank account")
        assertEquals(m(-4_000), reports.drill(month, zone, inr, DrillTarget.InAccount("bank"), 10).single().amount)
        assertEquals(setOf(m(10_000), m(-4_000)), reports.drill(month, zone, inr, DrillTarget.WithTag("gift"), 10).map { it.amount }.toSet())
    }
}
