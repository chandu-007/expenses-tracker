package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainRecordsTest {
    /** A phone whose clock moves on at every reading (a fixed one overflows the logical clock's counter after tens of thousands of changes in one millisecond). */
    private class Phone {
        val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
        private val clock = HlcClock("chain")
        private var t = 1_000L
        private val now: () -> Long = { ++t }
        val ledger = LedgerStore(db, clock, now)
        val backup = BackupStore(db, clock, now, ledger, TemplateStore(db, clock, now), RecurringStore(db, clock, now, ledger))
        val live get() = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.id }
        init { ledger.saveAccount(Account(AccountId("main"), "Main", AccountType.BANK, Currency.INR)) }
    }

    private fun entries(n: Int) = (0 until n).map { i -> BackupEntry("c$i", "EXPENSE", 1_000L + i, "main", amountMinor = 100L, currency = "INR", merchant = "Shop", splits = listOf(BackupSplit("Food", 100L))) }

    /**
     * A damaged or hand-made file whose records chain thousands of entries that are ALL live here: the plan walked each chain to its end (quadratic), a stall of many seconds on a phone.
     * A chain longer than the app ever makes is left alone, like a loop, and the walk stays short: only the originals within 64 links of its end can be taken away (the damage a damaged file can do is capped).
     */
    @Test
    fun aLongChainOfLiveEntriesInTheRecordsDoesNotStallThePlanAndTakesAwayAtMostTheLast64() {
        val n = 20_000
        val p = Phone()
        val base = BackupData(currencies = listOf(BackupCurrency("INR", 2)), accounts = listOf(BackupAccount("main", "Main", "BANK", "INR")), entries = entries(n))
        p.backup.restore(base)
        val data = BackupData(currencies = listOf(BackupCurrency("INR", 2)), replaced = (0 until n - 1).map { BackupReplaced("c$it", "c${it + 1}", "CHANGED") })
        val t0 = System.nanoTime()
        val report = p.backup.preview(data)
        p.backup.restore(data)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < 5_000, "preview and restore of a $n-link chain took $ms ms")
        assertTrue(report.replacedRemoved <= 64, "a chain this long is not one the app makes: at most the 64 nearest its end go, not ${report.replacedRemoved}")
        assertEquals(n - report.replacedRemoved, p.live.size)
    }

    /** A chain of a few links (a type changed more than once) still works: every original in it goes, the last stays. */
    @Test
    fun aShortChainStillRemovesTheEarlierEntries() {
        val p = Phone()
        p.backup.restore(BackupData(currencies = listOf(BackupCurrency("INR", 2)), accounts = listOf(BackupAccount("main", "Main", "BANK", "INR")), entries = entries(4)))
        val data = BackupData(currencies = listOf(BackupCurrency("INR", 2)), replaced = (0 until 3).map { BackupReplaced("c$it", "c${it + 1}", "CHANGED") })
        p.backup.restore(data)
        assertEquals(listOf("c3"), p.live)
    }
}
