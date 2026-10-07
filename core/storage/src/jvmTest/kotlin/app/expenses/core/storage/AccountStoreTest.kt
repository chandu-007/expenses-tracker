package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.Template
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
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.SettlementDirection
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountStoreTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val categories = CategoryStore(db, clock) { 1L }
    private val ledger = LedgerStore(db, clock) { 1L }
    private val templates = TemplateStore(db, clock) { 1L }
    private val recurring = RecurringStore(db, clock, { 1L }, ledger)

    private fun acct(id: String, type: AccountType = AccountType.BANK, opening: Long = 0, archived: Boolean = false) = Account(AccountId(id), id.replaceFirstChar { it.uppercase() }, type, inr, opening, archived)

    @Test
    fun theOpeningBalanceAndArchivedFlagAreStoredLoggedAndComeBack() {
        ledger.saveAccount(acct("hdfc", opening = 5_000_000))
        assertEquals(5_000_000L, db.schemaQueries.accountById("hdfc").executeAsOne().opening_minor)
        ledger.saveAccount(acct("hdfc", opening = 4_000_000, archived = true))
        val row = db.schemaQueries.accountById("hdfc").executeAsOne()
        assertEquals(4_000_000L, row.opening_minor); assertEquals(1L, row.archived)
        val ops = db.schemaQueries.opsSince("", 1000).executeAsList().filter { it.entity == "account" && it.row_id == "hdfc" }.map { it.field_ to it.value_ }
        assertTrue("opening_minor" to "4000000" in ops && "archived" to "1" in ops, "$ops")
        assertEquals(acct("hdfc", opening = 4_000_000, archived = true), ledger.loadLedger().accounts.getValue(AccountId("hdfc")), "an archived account still loads, so its entries stay valid")
    }

    @Test
    fun anArchivedAccountKeepsItsHistoryAndItsBalance() {
        ledger.saveAccount(acct("old", opening = 10_000))
        ledger.save(Expense(EntryId("e"), 1, AccountId("old"), m(3_000), listOf(CategorySplit(CategoryId("Food"), m(3_000)))))
        ledger.saveAccount(acct("old", opening = 10_000, archived = true))
        assertEquals(m(7_000), ledger.balances().getValue(AccountId("old")))
        assertEquals(m(3_000), ledger.spendByCategory(inr).getValue(CategoryId("Food")))
    }

    @Test
    fun usageCountsEntriesDeletedOnesTemplatesAndRepeatingExpenses() {
        ledger.saveAccount(acct("a")); ledger.saveAccount(acct("b"))
        assertEquals(0L, ledger.accountUsage(AccountId("a")))
        ledger.save(Expense(EntryId("e"), 1, AccountId("a"), m(1)))
        ledger.save(Transfer(EntryId("t"), 2, AccountId("b"), AccountId("a"), m(1)))
        assertEquals(2L, ledger.accountUsage(AccountId("a")), "an entry and a transfer into it")
        ledger.delete(EntryId("e"))
        assertEquals(2L, ledger.accountUsage(AccountId("a")), "a deleted entry could come back, so it still counts")
        templates.save(Template("tp", "T", m(1), CategoryId("Food"), AccountId("a"), null))
        recurring.save(RecurringItem("r", "R", m(1), CategoryId("Bills"), AccountId("a"), null, Schedule(RepeatUnit.MONTH, 1, LocalDate(2026, 1, 1))))
        assertEquals(4L, ledger.accountUsage(AccountId("a")))
    }

    @Test
    fun anAccountIsDeletedOnlyWhenNothingRefersToItAndNeverTheLastActiveOne() {
        ledger.saveAccount(acct("a")); ledger.saveAccount(acct("b")); ledger.saveAccount(acct("c", archived = true))
        ledger.save(Expense(EntryId("e"), 1, AccountId("a"), m(1)))
        assertTrue(assertFailsWith<IllegalArgumentException> { ledger.deleteAccount(AccountId("a")) }.message!!.contains("entries"))
        ledger.delete(EntryId("e"))
        assertFailsWith<IllegalArgumentException> { ledger.deleteAccount(AccountId("a")) } // a deleted entry could come back
        ledger.deleteAccount(AccountId("c")) // archived and unused: fine, and it was not 'active' so the rule about the last one is not involved
        ledger.deleteAccount(AccountId("b"))
        assertTrue(assertFailsWith<IllegalArgumentException> { ledger.deleteAccount(AccountId("b")) }.message!!.contains("no longer exists"))
        ledger.saveAccount(acct("d")); ledger.deleteAccount(AccountId("d")) // 'a' is still active, so 'd' may go
        assertEquals(listOf("a"), db.schemaQueries.selectAccounts().executeAsList().map { it.id })
        assertTrue(db.schemaQueries.opsSince("", 1000).executeAsList().any { it.entity == "account" && it.row_id == "b" && it.field_ == "deleted" && it.value_ == "1" }, "the deletion is logged for sync")
    }

    @Test
    fun theOnlyActiveAccountCannotBeDeletedEvenWhenUnusedAndArchivedOnesDoNotCountAsActive() {
        ledger.saveAccount(acct("only")); ledger.saveAccount(acct("gone", archived = true))
        assertTrue(assertFailsWith<IllegalArgumentException> { ledger.deleteAccount(AccountId("only")) }.message!!.contains("at least one"))
        assertEquals(listOf("gone", "only"), db.schemaQueries.selectAccounts().executeAsList().map { it.id }.sorted())
    }

    @Test
    fun theCurrencyOfAnAccountStillCannotChange() {
        ledger.saveAccount(acct("a"))
        assertFailsWith<IllegalArgumentException> { ledger.saveAccount(Account(AccountId("a"), "A", AccountType.BANK, Currency.USD)) }
    }

    @Test
    fun sqlBalancesAlwaysEqualTheInMemoryLedgerOnAMessyLedgerWithEveryKindOfEntry() {
        val rnd = Random(99)
        val types = listOf(AccountType.BANK, AccountType.CASH, AccountType.WALLET, AccountType.CREDIT_CARD, AccountType.LOAN)
        val ids = (1..6).map { "acc$it" }
        ids.forEachIndexed { i, id -> ledger.saveAccount(acct(id, types[i % types.size], opening = rnd.nextLong(-500_000, 900_000), archived = i == 5)) }
        fun any() = AccountId(ids[rnd.nextInt(ids.size)])
        val expenses = mutableListOf<Pair<String, Long>>()
        var kinds = mutableSetOf<String>()
        repeat(500) { i ->
            val at = 1_000L + i
            val amount = rnd.nextLong(1, 90_000)
            when (rnd.nextInt(8)) {
                0, 1, 2 -> { ledger.save(Expense(EntryId("e$i"), at, any(), m(amount), listOf(CategorySplit(CategoryId("Food"), m(amount))))); expenses += "e$i" to amount; kinds += "expense" }
                3 -> { ledger.save(Income(EntryId("i$i"), at, any(), m(amount), CategoryId("Other"))); kinds += "income" }
                4 -> { val from = any(); var to = any(); while (to == from) to = any(); ledger.save(Transfer(EntryId("t$i"), at, from, to, m(amount))); kinds += "transfer" }
                5 -> { val (id, total) = expenses.randomOrNull(rnd) ?: return@repeat; val acc = db.schemaQueries.entryById(id).executeAsOne().account_id!!
                    runCatching { ledger.save(Refund(EntryId("r$i"), at, AccountId(acc), m(rnd.nextLong(1, total + 1)), EntryId(id))); kinds += "refund" } }
                6 -> { ledger.save(Settlement(EntryId("s$i"), at, PersonId("p${rnd.nextInt(3)}"), any(), m(amount), SettlementDirection.entries[rnd.nextInt(2)])); kinds += "settlement" }
                else -> { ledger.save(FriendPaid(EntryId("f$i"), at, PersonId("p1"), m(amount), listOf(CategorySplit(CategoryId("Fun"), m(amount))))); kinds += "friend" }
            }
            if (rnd.nextInt(12) == 0) runCatching { ledger.delete(EntryId(expenses.randomOrNull(rnd)?.first ?: "none")) }
        }
        assertTrue(kinds.size == 6, "the ledger contains every kind of entry: $kinds")
        assertEquals(ledger.loadLedger().balances(), ledger.balances(), "SQL balances equal the in-memory ledger's")
        assertEquals(ledger.loadLedger().netWorth(inr), ledger.balances().values.fold(Money.zero(inr)) { a, b -> a + b })
    }

    @Test
    fun backupCarriesTheOpeningBalanceAndArchiveFlagAndAnOlderFileWithoutThemStillReads() {
        ledger.saveAccount(acct("card", AccountType.CREDIT_CARD, opening = -25_000, archived = true))
        val backup = BackupStore(db, clock, { 1L }, ledger, templates, recurring)
        val fresh = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
        val freshLedger = LedgerStore(fresh, clock) { 1L }
        BackupStore(fresh, clock, { 1L }, freshLedger, TemplateStore(fresh, clock) { 1L }, RecurringStore(fresh, clock, { 1L }, freshLedger)).restore(BackupFile.decode(BackupFile.encode(backup.export(), 1L)).second)
        assertEquals(acct("card", AccountType.CREDIT_CARD, opening = -25_000, archived = true), freshLedger.loadLedger().accounts.getValue(AccountId("card")))
        val old = """{"accounts":[{"id":"x","name":"X","type":"BANK","currency":"INR"}]}""".encodeToByteArray()
        val header = ("""{"format":"expenses-backup","version":1,"createdAt":1,"payloadBytes":${old.size},"sha256":"${Sha256.hex(old)}"}""" + "\n").encodeToByteArray()
        val a = BackupFile.decode(header + old).second.accounts.single()
        assertEquals(0L, a.openingMinor); assertEquals(false, a.archived)
    }
}
