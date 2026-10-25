package app.expenses.core.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.SearchDates
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Searching inside one account and between chosen days, and Home's account narrowed to a period. */
class SearchAccountDatesTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private val ledger = LedgerStore(db, HlcClock("n")) { 1L }
    private val ist = TimeZone.of("Asia/Kolkata")
    private fun m(v: Long) = Money(v, Currency.INR)
    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDate(y, mo, d).atStartOfDayIn(ist).toEpochMilliseconds() + (h * 60 + min) * 60_000L
    private fun day(y: Int, mo: Int, d: Int) = LocalDate(y, mo, d).toEpochDays().toLong()

    init {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, Currency.INR))
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR))
    }

    private fun spend(id: String, at: Long, account: String, merchant: String, tags: List<String>? = null) =
        ledger.save(Expense(EntryId(id), at, AccountId(account), m(100)), EntryDetails(merchant = merchant, tags = tags))
    private fun ids(q: SearchQuery) = db.searchFiltered(q).map { it.id }.sorted()

    @Test
    fun aRangeOfDaysIncludesTheFirstAndLastDayWholeAndOpenEndsHaveNoLimit() {
        spend("before", at(2026, 9, 30, 23, 59), "cash", "Edge"); spend("first-am", at(2026, 10, 1), "cash", "Edge")
        spend("mid", at(2026, 10, 8, 12), "cash", "Edge"); spend("last-pm", at(2026, 10, 15, 23, 59), "cash", "Edge"); spend("after", at(2026, 10, 16), "cash", "Edge")
        fun range(from: Long?, to: Long?, text: String = "") = SearchQuery(text = text, fromMillis = SearchDates.fromMillis(from, ist), untilMillis = SearchDates.untilMillis(to, ist))
        val a = day(2026, 10, 1); val b = day(2026, 10, 15)
        assertEquals(listOf("first-am", "last-pm", "mid"), ids(range(a, b)))
        assertEquals(listOf("first-am", "last-pm", "mid"), ids(range(a, b, "edge")), "with text")
        assertEquals(listOf("after", "first-am", "last-pm", "mid"), ids(range(a, null)), "from only")
        assertEquals(listOf("before", "first-am", "last-pm", "mid"), ids(range(null, b)), "to only")
        assertEquals(listOf("mid"), ids(range(day(2026, 10, 8), day(2026, 10, 8))), "one day")
    }

    @Test
    fun anAccountSearchFindsWhatLeftItAndWhatWasTransferredIntoItInEveryPlan() {
        spend("c1", 1_000, "cash", "Edge", listOf("Trip")); spend("b1", 2_000, "bank", "Edge", listOf("Trip"))
        ledger.save(Transfer(EntryId("t1"), 3_000, AccountId("bank"), AccountId("cash"), m(500)), EntryDetails(merchant = "Edge", tags = listOf("Trip")))
        assertEquals(listOf("c1", "t1"), ids(SearchQuery(accountId = "cash")), "no text")
        assertEquals(listOf("b1", "t1"), ids(SearchQuery(accountId = "bank")))
        assertEquals(listOf("c1", "t1"), ids(SearchQuery(text = "edge", accountId = "cash")), "indexed text")
        assertEquals(listOf("c1", "t1"), ids(SearchQuery(text = "ed", accountId = "cash")), "short text")
        assertEquals(listOf("c1", "t1"), ids(SearchQuery(tag = "trip", accountId = "cash")), "tag")
        assertEquals(listOf("b1", "c1", "t1"), ids(SearchQuery(text = "edge")), "no account: everything")
        assertEquals(emptyList(), ids(SearchQuery(accountId = "nowhere")))
        assertTrue(SearchQuery(accountId = "cash").hasAnyCondition)
        // the date-walk plan (a word on more than 2,000 rows)
        repeat(2_100) { i -> ledger.save(Expense(EntryId("bulk$i"), 10_000L + i, AccountId(if (i % 2 == 0) "cash" else "bank"), m(100)), EntryDetails(note = "everyday")) }
        assertEquals(1_050, db.searchFiltered(SearchQuery(text = "everyday", accountId = "cash"), 5_000).size)
    }

    @Test
    fun homesAccountInAPeriodIsTheSameTwoLookupsLimitedToTheDatesAndKeysetPagesInside() {
        spend("old", 10, "cash", "x"); spend("in1", 100, "cash", "x"); spend("in2", 200, "cash", "x"); spend("late", 900, "cash", "x"); spend("other", 150, "bank", "x")
        ledger.save(Transfer(EntryId("tin"), 300, AccountId("bank"), AccountId("cash"), m(500)), EntryDetails())
        ledger.save(Transfer(EntryId("tout"), 400, AccountId("cash"), AccountId("bank"), m(500)), EntryDetails())
        val q = db.schemaQueries
        assertEquals(listOf("tout", "in2", "in1"), q.recentEntriesByAccountInRange("cash", 100, 900, 10).executeAsList().map { it.id }, "from is inclusive, until is not")
        assertEquals(listOf("tin"), q.recentEntriesByCounterInRange("cash", 100, 900, 10).executeAsList().map { it.id }, "into cash")
        assertEquals(emptyList(), q.recentEntriesByAccountInRange("cash", 1_000, 2_000, 10).executeAsList())
        val first = q.recentEntriesByAccountInRange("cash", 100, 900, 1).executeAsList().single()
        assertEquals(listOf("in2", "in1"), q.entriesBeforeByAccountInRange("cash", 100, 900, first.occurred_at, first.seq, 10).executeAsList().map { it.id })
        val tin = q.recentEntriesByCounterInRange("cash", 100, 900, 10).executeAsList().single()
        assertEquals(emptyList(), q.entriesBeforeByCounterInRange("cash", 100, 900, tin.occurred_at, tin.seq, 10).executeAsList())
        assertEquals(listOf("tin"), q.entriesBeforeByCounterInRange("cash", 100, 900, 500, 1_000_000, 10).executeAsList().map { it.id })
    }

    @Test
    fun theInRangeLookupsAreStillIndexSearchesOnTheAccountAndTime() {
        fun plan(sql: String): String {
            val out = StringBuilder()
            driver.executeQuery(null, "EXPLAIN QUERY PLAN $sql", { c -> while (c.next().value) out.append(c.getString(3)).append('\n'); QueryResult.Unit }, 0)
            return out.toString()
        }
        val range = "AND occurred_at >= 100 AND occurred_at < 900"
        val byAccount = plan("SELECT * FROM entry WHERE deleted = 0 AND account_id = 'x' $range ORDER BY occurred_at DESC, seq DESC LIMIT 50")
        val byCounter = plan("SELECT * FROM entry WHERE deleted = 0 AND counter_account_id = 'x' $range ORDER BY occurred_at DESC, seq DESC LIMIT 50")
        val pageAccount = plan("SELECT * FROM entry WHERE deleted = 0 AND account_id = 'x' $range AND (occurred_at, seq) < (500, 5) ORDER BY occurred_at DESC, seq DESC LIMIT 50")
        val pageCounter = plan("SELECT * FROM entry WHERE deleted = 0 AND counter_account_id = 'x' $range AND (occurred_at, seq) < (500, 5) ORDER BY occurred_at DESC, seq DESC LIMIT 50")
        for (p in listOf(byAccount, pageAccount)) assertTrue("SEARCH" in p && "entry_account_time_seq" in p && "SCAN" !in p, p)
        for (p in listOf(byCounter, pageCounter)) assertTrue("SEARCH" in p && "entry_counter_time" in p && "SCAN" !in p, p)
        assertTrue("TEMP B-TREE" !in byAccount && "TEMP B-TREE" !in byCounter, "no sort step: $byAccount / $byCounter")
    }
}
