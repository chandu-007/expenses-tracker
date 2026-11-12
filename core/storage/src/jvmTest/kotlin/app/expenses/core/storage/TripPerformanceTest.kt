package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Trip
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Travel mode on a ledger of 20,000 entries (a third of them carry the trip's tag, spread over 400 days): the hook that runs on every save is one read of the few enabled trips, the figures and the
 * offer read only the trip's days. The bounds are for a laptop's JVM and generous; the numbers are printed so a run shows them (the phone's budgets are in `PerformanceBudgetTest`, on a device).
 */
class TripPerformanceTest {
    private val ist = TimeZone.of("Asia/Kolkata")
    private val inr = Currency.INR
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val ledger = LedgerStore(db, HlcClock("n")) { System.currentTimeMillis() }.also { it.zone = { ist } }
    private val base = LocalDate(2026, 1, 1)
    private fun noon(dayOffset: Int) = LocalDate.fromEpochDays(base.toEpochDays() + dayOffset).atStartOfDayIn(ist).toEpochMilliseconds() + 12 * 3_600_000L
    private fun expense(id: String, at: Long) = Expense(EntryId(id), at, AccountId("cash"), Money(10_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(10_000, inr))))
    private val trip = Trip("year", "A long stay", "Trip: Long", base.toEpochDays().toLong(), base.toEpochDays().toLong() + 365)

    init {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        ledger.atomically { for (i in 0 until 20_000) ledger.save(expense("e$i", noon(i % 400)), EntryDetails(merchant = "Merchant $i", tags = if (i % 3 == 0) listOf("Trip: Long") else null)) }
    }

    private fun hookMillis(): Double = millis { for (i in 1..20_000) ledger.trips.tripAt(noon(i % 400)) } / 20_000

    private inline fun millis(block: () -> Unit): Double { val t = System.nanoTime(); block(); return (System.nanoTime() - t) / 1e6 }

    @Test
    fun theHookCostsAboutOneSmallReadWithNoTripsAndWithOneOnAndTheFiguresReadOnlyTheTripsDays() {
        // the hook with no trip at all (the usual phone), one trip, and five that are on
        val none = hookMillis()
        ledger.trips.save(trip)
        val one = hookMillis()
        for (k in 0 until 4) ledger.trips.save(Trip("far$k", "Far $k", "far $k", base.toEpochDays() + 5_000L + k * 10, base.toEpochDays() + 5_004L + k * 10))
        val five = hookMillis()
        println("TRIP PERF hook per call: no trips %.4f ms, one %.4f ms, five %.4f ms".format(none, one, five))
        assertTrue(none < 0.25 && one < 0.25 && five < 0.25, "one hook call stays far below a quarter of a millisecond: $none $one $five")

        val lines = ledger.trips.lines(trip, inr)
        val t1 = millis { ledger.trips.lines(trip, inr) }; val t2 = millis { ledger.trips.totals(trip, inr) }; val t3 = millis { ledger.trips.untagged(trip, listOf(trip.startDay..trip.endDay)) }
        println("TRIP PERF 20k ledger: lines %.1f ms (%d lines), totals %.1f ms, untagged offer %.1f ms".format(t1, lines.lines.size, t2, t3))
        assertTrue(lines.lines.size in 5_000..7_000 && !lines.capped, "about a third of the entries in the window: ${lines.lines.size}")
        assertTrue(t1 < 600 && t2 < 400 && t3 < 600, "lines $t1 ms, totals $t2 ms, offer $t3 ms")
        assertEquals(lines.lines.sumOf { it.mineMinor } to lines.lines.map { it.entryId }.toSet().size, ledger.trips.totals(trip, inr))
    }

    @Test
    fun savingWithTheHookAskedForCostsNoMoreThanAFewTenthsOfAMillisecondOverSavingWithout() {
        fun batch(tag: Boolean, from: Int): Double = millis { ledger.atomically { for (i in from until from + 1_500) ledger.save(expense("n$i", noon(500 + i % 30)), EntryDetails(merchant = "New $i", tripTag = tag)) } } / 1_500
        // no trip exists: asking for the hook is one empty read; entries land outside any window
        val plain = (0 until 3).minOf { batch(false, it * 10_000) }
        val asked = (0 until 3).minOf { batch(true, 100_000 + it * 10_000) }
        println("TRIP PERF save with no trips: %.4f ms plain, %.4f ms with the hook asked for (best of 3 batches of 1,500)".format(plain, asked))
        assertTrue(asked - plain < 0.2, "the hook adds ${asked - plain} ms per save with no trips")
        ledger.trips.save(Trip("t", "Then", "Trip: Then", base.toEpochDays() + 500L, base.toEpochDays() + 529L))
        val tagged = (0 until 3).minOf { batch(true, 200_000 + it * 10_000) }
        println("TRIP PERF save inside a trip (tag added): %.4f ms".format(tagged))
        assertTrue(tagged - plain < 1.5, "a tagged save adds the tag rows: ${tagged - plain} ms")
        assertEquals(4_500, db.schemaQueries.tagCountsAll().executeAsList().single { it.tag == "Trip: Then" }.uses.toInt())
    }

    @Test
    fun aTripWithMoreLinesThanTheLimitIsCutAndFlaggedAndKeepsTheNewest() {
        val short = ledger.trips.lines(trip, inr, limit = 50)
        assertEquals(50, short.lines.size); assertTrue(short.capped)
        assertEquals(short.lines.map { it.occurredAt }.sortedDescending(), short.lines.map { it.occurredAt }, "newest first")
        assertTrue(!ledger.trips.lines(trip, inr, limit = 100_000).capped)
    }
}
