package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringStoreTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val zone = TimeZone.of("Asia/Kolkata")
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private val clock = HlcClock("n1")
    private val categories = CategoryStore(db, clock, { 1_000L })
    private val ledger = LedgerStore(db, clock, { 1_000L })
    private val recurring = RecurringStore(db, clock, { 1_000L }, ledger)
    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, inr)
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    init { ledger.saveAccount(bank) }

    private fun rent(start: LocalDate = d(2026, 8, 1), end: ScheduleEnd = ScheduleEnd.Never, auto: Boolean = false, amount: Money? = m(1_500_000), id: String = "rent") =
        RecurringItem(id, "Rent", amount, CategoryId("Bills"), bank.id, "Landlord", Schedule(RepeatUnit.MONTH, 1, start, end), auto)

    private fun entryCount() = db.schemaQueries.entryCount().executeAsOne()

    @Test
    fun dueListsUnhandledOccurrencesUpToAndIncludingTodayNotTheFuture() {
        recurring.save(rent())
        assertEquals(listOf(d(2026, 9, 1), d(2026, 10, 1)), recurring.due(d(2026, 10, 1)).map { it.date }, "1 Aug is more than 60 days back")
        assertEquals(listOf(d(2026, 8, 1), d(2026, 9, 1)), recurring.due(d(2026, 9, 30)).map { it.date }, "1 Oct is not due on 30 Sep")
    }

    @Test
    fun postingCreatesOneExpenseOnTheDueDayAndCannotBeDoneTwice() {
        recurring.save(rent())
        val id = recurring.post("rent", d(2026, 9, 1), zone)
        assertEquals("rec:rent:${d(2026, 9, 1).toEpochDays()}", id)
        assertNull(recurring.post("rent", d(2026, 9, 1), zone), "the same occurrence is never posted twice")
        assertEquals(1L, entryCount())
        val row = db.schemaQueries.entryById(id!!).executeAsOne()
        assertEquals(d(2026, 9, 1).atStartOfDayIn(zone).toEpochMilliseconds(), row.occurred_at)
        assertEquals(1_500_000L, row.amount_minor)
        assertEquals("Landlord", row.merchant)
        assertEquals("recurring", row.source)
        assertEquals(mapOf<CategoryId?, Money>(CategoryId("Bills") to m(1_500_000)), ledger.spendByCategory(inr))
        assertEquals(listOf(d(2026, 10, 1)), recurring.due(d(2026, 10, 1)).map { it.date })
    }

    @Test
    fun skippingRemovesTheOccurrenceWithoutAnEntryAndUndoBringsItBack() {
        recurring.save(rent())
        recurring.skip("rent", d(2026, 8, 1))
        assertEquals(0L, entryCount())
        assertEquals(listOf(d(2026, 9, 1)), recurring.due(d(2026, 9, 1)).map { it.date })
        recurring.undo("rent", d(2026, 8, 1))
        assertEquals(listOf(d(2026, 8, 1), d(2026, 9, 1)), recurring.due(d(2026, 9, 1)).map { it.date })
    }

    @Test
    fun undoingAPostDeletesTheEntryAndMakesTheOccurrenceDueAgain() {
        recurring.save(rent())
        recurring.post("rent", d(2026, 8, 1), zone)
        recurring.undo("rent", d(2026, 8, 1))
        assertTrue(ledger.spendByCategory(inr).isEmpty(), "the expense no longer counts")
        assertEquals(d(2026, 8, 1), recurring.due(d(2026, 8, 1)).single().date)
        assertNotNull(recurring.post("rent", d(2026, 8, 1), zone), "it can be posted again, and then counts once")
        assertEquals(m(1_500_000), ledger.spendByCategory(inr).getValue(CategoryId("Bills")))
    }

    @Test
    fun anItemWithoutAFixedAmountNeedsOneWhenPostedAndCannotPostByItself() {
        recurring.save(rent(amount = null, id = "power"))
        assertFailsWith<IllegalArgumentException> { recurring.post("power", d(2026, 8, 1), zone) }
        assertEquals(0L, entryCount())
        recurring.post("power", d(2026, 8, 1), zone, amount = m(234_500))
        assertEquals(m(234_500), ledger.spendByCategory(inr).getValue(CategoryId("Bills")))
        assertFailsWith<IllegalArgumentException> { rent(amount = null, auto = true) }
        assertFailsWith<IllegalArgumentException> { recurring.post("power", d(2026, 9, 1), zone, amount = m(0)) }
    }

    @Test
    fun autoPostPostsOnlyItemsSetToAutoAndOnlyOnce() {
        recurring.save(rent(id = "a", auto = true))
        recurring.save(rent(id = "b", auto = false))
        val first = recurring.autoPost(d(2026, 9, 15), zone)
        assertEquals(setOf("a"), first.map { it.first.item.id }.toSet())
        assertEquals(2, first.size, "August and September")
        assertEquals(2L, entryCount())
        assertTrue(recurring.autoPost(d(2026, 9, 15), zone).isEmpty())
        assertEquals(2, recurring.due(d(2026, 9, 15)).count { it.item.id == "b" }, "the confirm-first item still waits")
    }

    @Test
    fun editingTheScheduleOrAmountNeverReplaysOrForgetsWhatWasDone() {
        recurring.save(rent())
        recurring.post("rent", d(2026, 9, 1), zone)
        recurring.skip("rent", d(2026, 8, 1))
        recurring.save(rent(amount = m(1_600_000)))
        assertEquals(listOf(d(2026, 10, 1)), recurring.due(d(2026, 10, 1)).map { it.date }, "only the new occurrence is due")
        assertEquals(1_500_000L, db.schemaQueries.entryById("rec:rent:${d(2026, 9, 1).toEpochDays()}").executeAsOne().amount_minor, "the posted one is unchanged")
        recurring.post("rent", d(2026, 10, 1), zone)
        assertEquals(1_600_000L, db.schemaQueries.entryById("rec:rent:${d(2026, 10, 1).toEpochDays()}").executeAsOne().amount_minor, "future ones use the new amount")
        // moving the start while keeping the same dates still treats those dates as handled
        recurring.save(rent(start = d(2026, 9, 1), amount = m(1_600_000)))
        assertTrue(recurring.due(d(2026, 10, 1)).isEmpty())
    }

    @Test
    fun onlyTheLast60DaysAreOfferedAndEndedSchedulesStop() {
        val today = d(2026, 10, 1)
        recurring.save(rent(start = today.minus(61, DateTimeUnit.DAY).let { d(it.year, it.month.ordinal + 1, it.day) }, end = ScheduleEnd.AfterCount(2)))
        // 61 days ago: outside the window; the second occurrence is a month later
        assertEquals(1, recurring.due(today).size)
        recurring.save(rent(id = "edge", start = today.minus(60, DateTimeUnit.DAY)))
        assertTrue(recurring.due(today).any { it.item.id == "edge" && it.date == d(2026, 8, 2) }, "exactly 60 days ago is still offered")
        recurring.save(rent(id = "short", start = d(2026, 6, 1), end = ScheduleEnd.AfterCount(2)))
        assertTrue(recurring.due(d(2026, 12, 1)).none { it.item.id == "short" }, "ended long ago")
    }

    @Test
    fun aDeletedItemIsNotDueAndCannotBePosted() {
        recurring.save(rent())
        recurring.post("rent", d(2026, 8, 1), zone)
        recurring.delete("rent")
        assertTrue(recurring.due(d(2026, 10, 1)).isEmpty())
        assertTrue(recurring.list().isEmpty())
        assertFailsWith<IllegalArgumentException> { recurring.post("rent", d(2026, 9, 1), zone) }
        assertEquals(1L, entryCount(), "what was posted stays")
        assertFailsWith<IllegalArgumentException> { recurring.save(rent()) }
    }

    @Test
    fun aDateThatIsNotAnOccurrenceIsRefused() {
        recurring.save(rent())
        assertFailsWith<IllegalArgumentException> { recurring.post("rent", d(2026, 9, 2), zone) }
        assertFailsWith<IllegalArgumentException> { recurring.skip("rent", d(2026, 9, 2)) }
        assertEquals(0L, entryCount())
    }

    @Test
    fun categoriesAreProtectedAndFollowMerges() {
        recurring.save(rent())
        assertEquals(1L, categories.usage("Bills"))
        assertFailsWith<IllegalArgumentException> { categories.delete("Bills") }
        categories.merge("Bills", "Other")
        assertEquals(CategoryId("Other"), recurring.list().single().category)
        recurring.save(rent().copy(category = CategoryId("Bills")))
        assertEquals(CategoryId("Other"), recurring.list().single().category, "a late edit with the merged id is redirected")
    }

    @Test
    fun itemsRoundTripAndChangesAreLogged() {
        val item = rent(end = ScheduleEnd.OnOrBefore(d(2027, 3, 1)), auto = true)
        recurring.save(item)
        assertEquals(item, recurring.list().single())
        fun ops() = db.schemaQueries.opsSince("", 100_000).executeAsList().filter { it.entity == "recurring" }
        val n = ops().size
        recurring.save(item)
        assertEquals(n, ops().size, "saving the same thing logs nothing")
        recurring.save(item.copy(amount = m(1_700_000)))
        assertEquals(listOf("amount_minor" to "1700000"), ops().drop(n).map { it.field_ to it.value_ })
        val ongoing = rent(id = "gym", end = ScheduleEnd.AfterCount(6), amount = null)
        recurring.save(ongoing)
        assertEquals(ongoing, recurring.list().first { it.id == "gym" })
    }
}
