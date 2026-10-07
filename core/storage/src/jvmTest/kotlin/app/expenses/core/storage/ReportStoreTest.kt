package app.expenses.core.storage

import app.expenses.core.domain.CalendarMath
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.CategoryRow
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
import app.expenses.core.domain.ledger.FriendPaid
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Share
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
import kotlin.test.assertTrue

class ReportStoreTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val zone = TimeZone.of("Asia/Kolkata")
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val categories = CategoryStore(db, clock) { 1L }
    private val ledger = LedgerStore(db, clock) { 1L }
    private val reports = ReportStore(db, ledger)
    private val base = LocalDate(2026, 8, 1)

    init {
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr))
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
    }

    private fun day(offset: Int, hour: Int = 12) = base.plus(offset, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() + hour * 3_600_000L
    private fun month(y: Int, mo: Int) = BudgetPeriod.Monthly(1).containing(LocalDate(y, mo, 15))
    private fun split(cat: String, v: Long) = CategorySplit(CategoryId(cat), m(v))

    private fun sum(lines: List<DrillLine>) = lines.sumOf { it.amount.minorUnits }
    private fun flatten(rows: List<CategoryRow>): List<CategoryRow> = rows.flatMap { listOf(it) + it.children }

    /** Every figure opens into entries that add up to exactly that figure. */
    private fun assertDrillsAddUp(period: DatePeriod) {
        val rows = reports.breakdown(period, zone, inr)
        val spent = reports.spent(period, zone, inr).minorUnits
        assertEquals(spent, rows.sumOf { it.total.minorUnits }, "rows add up to the total")
        assertEquals(spent, sum(reports.drill(period, zone, inr, DrillTarget.All)), "all entries add up to the total")
        for (row in rows) {
            val target = if (row.categoryId == null) DrillTarget.Uncategorised else DrillTarget.InCategory(row.categoryId!!)
            assertEquals(row.total.minorUnits, sum(reports.drill(period, zone, inr, target)), "${row.name} opens into entries adding up to ${row.total}")
            for (child in row.children) assertEquals(child.total.minorUnits, sum(reports.drill(period, zone, inr, DrillTarget.InCategory(child.categoryId!!))), "${child.name}")
        }
        assertEquals(reports.income(period, zone, inr).minorUnits, sum(reports.drill(period, zone, inr, DrillTarget.Income)), "income")
    }

    @Test
    fun aHandBuiltMonthAddsUpAndNamesWhatItShould() {
        val cafe = categories.create("Cafe", parentId = "Food")
        ledger.save(Expense(EntryId("a"), day(2), AccountId("cash"), m(60_000), listOf(split("Food", 60_000))), EntryDetails(merchant = "Biryani"))
        ledger.save(Expense(EntryId("b"), day(3), AccountId("cash"), m(25_000), listOf(split(cafe.id, 25_000))), EntryDetails(merchant = "Chai"))
        ledger.save(Expense(EntryId("c"), day(4), AccountId("bank"), m(90_000), listOf(split("Fun", 90_000))), EntryDetails(note = "movie"))
        ledger.save(Expense(EntryId("d"), day(5), AccountId("bank"), m(5_000)), EntryDetails(merchant = "Mystery"))
        ledger.save(Income(EntryId("pay"), day(1), AccountId("bank"), m(5_000_000), CategoryId("Other")), EntryDetails(merchant = "Salary"))
        val p = month(2026, 8)
        val rows = reports.breakdown(p, zone, inr)
        assertEquals(listOf("Fun", "Food", "Uncategorised"), rows.map { it.name })
        assertEquals(m(85_000), rows[1].total, "Food plus Cafe")
        assertEquals(listOf("Biryani", "Chai"), reports.drill(p, zone, inr, DrillTarget.InCategory("Food")).map { it.title }.reversed())
        assertEquals(listOf("Mystery"), reports.drill(p, zone, inr, DrillTarget.Uncategorised).map { it.title })
        assertEquals(listOf("Salary"), reports.drill(p, zone, inr, DrillTarget.Income).map { it.title })
        assertEquals(m(5_000_000), reports.income(p, zone, inr))
        assertDrillsAddUp(p)
    }

    @Test
    fun aSplitExpenseShowsOnlyThePartInsideTheChosenCategory() {
        ledger.save(Expense(EntryId("s"), day(2), AccountId("cash"), m(90_000), listOf(split("Food", 50_000), split("Fun", 40_000))), EntryDetails(merchant = "Mall"))
        val p = month(2026, 8)
        assertEquals(50_000L, reports.drill(p, zone, inr, DrillTarget.InCategory("Food")).single().amount.minorUnits)
        assertEquals(40_000L, reports.drill(p, zone, inr, DrillTarget.InCategory("Fun")).single().amount.minorUnits)
        assertEquals(90_000L, reports.drill(p, zone, inr, DrillTarget.All).single().amount.minorUnits, "one entry, shown once, with the whole")
    }

    @Test
    fun aSharedExpenseCountsOnlyYourPartAndARefundInALaterMonthReducesThatMonth() {
        ledger.save(Expense(EntryId("dinner"), day(2), AccountId("bank"), m(300_000), listOf(split("Food", 300_000)), listOf(Share(null, m(100_000)), Share(PersonId("asha"), m(200_000)))), EntryDetails(merchant = "Dinner"))
        ledger.save(Expense(EntryId("shirt"), day(20), AccountId("bank"), m(200_000), listOf(split("Shopping", 200_000))), EntryDetails(merchant = "Shirt"))
        ledger.save(Refund(EntryId("back"), day(45), AccountId("bank"), m(50_000), EntryId("shirt")), EntryDetails(merchant = "Shirt refund"))
        assertEquals(m(100_000), reports.breakdown(month(2026, 8), zone, inr).first { it.name == "Food" }.total)
        assertEquals(m(200_000), reports.breakdown(month(2026, 8), zone, inr).first { it.name == "Shopping" }.total)
        val sep = reports.breakdown(month(2026, 9), zone, inr)
        assertEquals(m(-50_000), sep.single().total, "September only has the refund")
        assertEquals(listOf("Shirt refund"), reports.drill(month(2026, 9), zone, inr, DrillTarget.InCategory("Shopping")).map { it.title })
        assertEquals(-50_000L, reports.drill(month(2026, 9), zone, inr, DrillTarget.All).single().amount.minorUnits)
        for (p in listOf(month(2026, 8), month(2026, 9))) assertDrillsAddUp(p)
    }

    @Test
    fun deletedEntriesAndOtherCurrenciesAreNotCounted() {
        ledger.save(Expense(EntryId("keep"), day(2), AccountId("cash"), m(1_000), listOf(split("Food", 1_000))))
        ledger.save(Expense(EntryId("gone"), day(3), AccountId("cash"), m(9_000), listOf(split("Food", 9_000))))
        ledger.delete(EntryId("gone"))
        ledger.saveAccount(Account(AccountId("usd"), "USD", AccountType.BANK, Currency.USD))
        ledger.save(Expense(EntryId("usd"), day(4), AccountId("usd"), Money(5_000, Currency.USD), listOf(CategorySplit(CategoryId("Food"), Money(5_000, Currency.USD)))))
        val p = month(2026, 8)
        assertEquals(m(1_000), reports.spent(p, zone, inr))
        assertEquals(1, reports.drill(p, zone, inr, DrillTarget.All).size)
        assertEquals(Money(5_000, Currency.USD), reports.spent(p, zone, Currency.USD))
    }

    @Test
    fun aDeletedIncomeIsNotCountedAndComesBackWhenRestored() {
        ledger.save(Income(EntryId("pay"), day(1), AccountId("bank"), m(5_000_000), CategoryId("Other")), EntryDetails(merchant = "Salary"))
        ledger.save(Income(EntryId("gift"), day(2), AccountId("bank"), m(100_000), CategoryId("Other")), EntryDetails(merchant = "Gift"))
        ledger.delete(EntryId("gift"))
        val p = month(2026, 8)
        assertEquals(m(5_000_000), reports.income(p, zone, inr))
        assertEquals(listOf("Salary"), reports.drill(p, zone, inr, DrillTarget.Income).map { it.title })
        ledger.restore(EntryId("gift"))
        assertEquals(m(5_100_000), reports.income(p, zone, inr))
        assertDrillsAddUp(p)
    }

    @Test
    fun theEdgesOfAPeriodAreExactInTheUsersTimeZone() {
        val p = month(2026, 9)
        ledger.save(Expense(EntryId("last"), p.start.atStartOfDayIn(zone).toEpochMilliseconds() - 60_000, AccountId("cash"), m(100), listOf(split("Food", 100))))
        ledger.save(Expense(EntryId("first"), p.start.atStartOfDayIn(zone).toEpochMilliseconds(), AccountId("cash"), m(200), listOf(split("Food", 200))))
        ledger.save(Expense(EntryId("end"), p.endExclusive.atStartOfDayIn(zone).toEpochMilliseconds(), AccountId("cash"), m(400), listOf(split("Food", 400))))
        assertEquals(m(200), reports.spent(p, zone, inr))
        assertEquals(listOf(200L), reports.drill(p, zone, inr, DrillTarget.All).map { it.amount.minorUnits })
        assertEquals(m(100), reports.spent(month(2026, 8), zone, inr))
    }

    @Test
    fun trendGivesOneTotalPerPeriodInOrder() {
        ledger.save(Expense(EntryId("a"), day(2), AccountId("cash"), m(100), listOf(split("Food", 100))))
        ledger.save(Expense(EntryId("b"), day(40), AccountId("cash"), m(300), listOf(split("Food", 300))))
        assertEquals(listOf(m(100), m(300), m(0)), reports.trend(listOf(month(2026, 8), month(2026, 9), month(2026, 10)), zone, inr))
    }

    @Test
    fun aMessyLedgerAlwaysAddsUpInEveryPeriodAndCategory() {
        val rnd = Random(2026)
        val cafe = categories.create("Cafe", parentId = "Food").id
        val cats = listOf("Food", cafe, "Fun", "Bills", "Shopping", null)
        val expenses = mutableListOf<String>()
        var refunds = 0; var deleted = 0; var shared = 0
        repeat(260) { i ->
            val total = rnd.nextLong(100, 90_000)
            val parts = rnd.nextInt(1, 4).coerceAtMost(total.toInt())
            val each = total / parts
            val splits = (0 until parts).mapNotNull { k -> cats[rnd.nextInt(cats.size)]?.let { c -> split(c, if (k == parts - 1) total - each * (parts - 1) else each) } }
            val isShared = rnd.nextInt(5) == 0 && splits.size == parts
            if (isShared) shared++
            val shares = if (isShared) listOf(Share(null, m(total / 2)), Share(PersonId("p"), m(total - total / 2))) else emptyList()
            // a split that dropped an uncategorised part does not sum to the amount, so leave those uncategorised altogether
            val consistent = if (splits.size == parts) splits else emptyList()
            ledger.save(Expense(EntryId("e$i"), day(rnd.nextInt(0, 120), rnd.nextInt(0, 24)), AccountId("cash"), m(total), consistent, shares), EntryDetails(merchant = "m$i"))
            expenses += "e$i"
            if (rnd.nextInt(6) == 0 && !isShared) runCatching { ledger.save(Refund(EntryId("r$i"), day(rnd.nextInt(0, 140), 10), AccountId("cash"), m(rnd.nextLong(1, total + 1)), EntryId("e$i"))); refunds++ }
            if (rnd.nextInt(8) == 0) ledger.save(Income(EntryId("i$i"), day(rnd.nextInt(0, 120)), AccountId("bank"), m(rnd.nextLong(1_000, 500_000)), CategoryId("Other")))
            if (rnd.nextInt(10) == 0) {
                val mine = rnd.nextLong(100, 5_000)
                ledger.save(FriendPaid(EntryId("f$i"), day(rnd.nextInt(0, 120)), PersonId("raj"), m(mine), listOf(split("Fun", mine))))
            }
        }
        // delete a few that have no refunds
        expenses.shuffled(rnd).take(25).forEach { runCatching { ledger.delete(EntryId(it)); deleted++ } }
        assertTrue(refunds > 10 && deleted > 5 && shared > 10, "the ledger really is messy: $refunds refunds, $deleted deletions, $shared shared")
        val periods = listOf(8, 9, 10, 11, 12).map { month(2026, it) } + listOf(DatePeriod(LocalDate(2026, 8, 1), LocalDate(2026, 12, 31)), BudgetPeriod.Weekly(kotlinx.datetime.DayOfWeek.MONDAY).containing(LocalDate(2026, 9, 9)))
        for (p in periods) assertDrillsAddUp(p)
        // the report agrees with the in-memory ledger
        val full = DatePeriod(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1))
        assertEquals(ledger.loadLedger().spendByCategory(inr).values.sumOf { it.minorUnits }, reports.spent(full, zone, inr).minorUnits)
        assertTrue(flatten(reports.breakdown(full, zone, inr)).size > 3)
    }
}

class CalendarPointsTest {
    private val inr = Currency.INR
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val ledger = LedgerStore(db, HlcClock("n")) { 1L }
    private val reports = ReportStore(db, ledger)
    private val ist = TimeZone.of("Asia/Kolkata")

    @Test
    fun theCalendarsDailyTotalsEqualTheMonthsTotalAndCountRefundsAndYourShareOnly() {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        fun at(d: Int, h: Int) = LocalDateTime(2026, 10, d, h, 0).toInstant(ist).toEpochMilliseconds()
        ledger.save(Expense(EntryId("a"), at(3, 10), AccountId("cash"), Money(10_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(10_000, inr)))))
        ledger.save(Expense(EntryId("b"), at(3, 20), AccountId("cash"), Money(30_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(30_000, inr))), listOf(Share(null, Money(10_000, inr)), Share(PersonId("asha"), Money(20_000, inr)))))
        ledger.save(Refund(EntryId("r"), at(5, 9), AccountId("cash"), Money(4_000, inr), EntryId("a")))
        ledger.save(Expense(EntryId("z"), at(31, 23), AccountId("cash"), Money(500, inr)))
        ledger.save(Expense(EntryId("nov"), LocalDateTime(2026, 11, 1, 0, 1).toInstant(ist).toEpochMilliseconds(), AccountId("cash"), Money(777, inr)))
        val month = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
        val cal = CalendarMath.month(2026, 10, ist, reports.spendPoints(month, ist, inr), emptyList(), LocalDate(2026, 10, 1))
        assertEquals(20_000L, cal.days[2].spentMinor, "your 100 plus your 100 share of the dinner")
        assertEquals(-4_000L, cal.days[4].spentMinor, "a refund lowers its own day")
        assertEquals(reports.spent(month, ist, inr).minorUnits, cal.totalSpentMinor, "the calendar and the report always agree")
    }
}
