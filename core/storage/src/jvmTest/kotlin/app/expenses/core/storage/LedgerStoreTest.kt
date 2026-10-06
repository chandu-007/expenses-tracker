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
import app.expenses.core.domain.ledger.FriendPaid
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Ledger
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.SettlementDirection
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.SpendBasis
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.Hlc
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import app.expenses.core.domain.ledger.Entry as DomainEntry

class LedgerStoreTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private var clockMillis = 1_000L
    private val store = LedgerStore(db, HlcClock("n1"), { clockMillis })

    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, inr)
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, inr)
    private val card = Account(AccountId("card"), "Card", AccountType.CREDIT_CARD, inr)
    private val accounts = listOf(bank, cash, card)
    private val cats = (0 until 5).map { CategoryId("c$it") }
    private val people = listOf(PersonId("p0"), PersonId("p1"))

    init { accounts.forEach(store::saveAccount) }

    private fun reference(entries: List<DomainEntry>): Ledger =
        accounts.fold(Ledger.EMPTY) { l, a -> l.addAccount(a) }.addAll(entries)

    private fun assertAgrees(entries: List<DomainEntry>, windows: List<Pair<Long, Long>>) {
        val ref = reference(entries)
        val loaded = store.loadLedger()
        assertEquals(ref.balances(), loaded.balances(), "balances after reload")
        for (p in people) assertEquals(ref.personBalance(p, inr), loaded.personBalance(p, inr), "person balance $p")
        for ((from, until) in windows) for (basis in SpendBasis.entries) {
            val expected = ref.spendByCategory(inr, basis, from, until)
            assertEquals(expected, loaded.spendByCategory(inr, basis, from, until), "reloaded ledger spend $basis [$from,$until)")
            assertEquals(expected, store.spendByCategory(inr, basis, from, until), "SQL spend $basis [$from,$until)")
        }
    }

    @Test
    fun everyEntryKindRoundTripsAndSqlReportsMatchTheLedger() {
        val food = cats[0]
        val travel = cats[1]
        val friend = people[0]
        val entries = listOf<DomainEntry>(
            Income(EntryId("i1"), 1, bank.id, m(100_000), CategoryId("salary")),
            Transfer(EntryId("t1"), 2, bank.id, cash.id, m(5_000)),
            Expense(EntryId("x1"), 3, card.id, m(1_001), listOf(CategorySplit(food, m(334)), CategorySplit(travel, m(667)))),
            Expense(EntryId("x2"), 4, bank.id, m(4_000), listOf(CategorySplit(food, m(4_000))), listOf(Share(null, m(1_000)), Share(friend, m(3_000)))),
            FriendPaid(EntryId("f1"), 5, friend, m(700), listOf(CategorySplit(travel, m(700)))),
            Refund(EntryId("r1"), 6, card.id, m(300), EntryId("x1")),
            Refund(EntryId("r2"), 7, card.id, m(100), EntryId("x1")),
            Transfer(EntryId("t2"), 8, bank.id, card.id, m(901)),
            Settlement(EntryId("s1"), 9, friend, bank.id, m(3_000), SettlementDirection.RECEIVED_FROM_PERSON),
        )
        entries.forEach { store.save(it) }
        assertAgrees(entries, listOf(Long.MIN_VALUE to Long.MAX_VALUE, 0L to 5L, 5L to 8L))
    }

    @Test
    fun foreignCurrencyAndZeroDecimalCurrenciesKeepTheirDecimalPlaces() {
        val jpy = Currency("JPY", 0)
        val kwd = Currency("KWD", 3)
        val yen = Account(AccountId("yen"), "Yen", AccountType.BANK, jpy)
        store.saveAccount(yen)
        store.save(Income(EntryId("i"), 1, yen.id, Money(50_000, jpy)))
        store.save(Expense(EntryId("e"), 2, yen.id, Money(1_200, jpy), original = Money(7_500, kwd)))
        val ledger = store.loadLedger()
        assertEquals(Money(48_800, jpy), ledger.balance(yen.id))
        assertEquals(Money(7_500, kwd), (ledger.entry(EntryId("e")) as Expense).original)
        assertFailsWith<IllegalArgumentException> { store.saveAccount(Account(AccountId("bad"), "Bad", AccountType.BANK, Currency("JPY", 2))) }
    }

    @Test
    fun randomHistoriesWithBackdatedRefundsEditsAndDeletesAgreeEverywhere() {
        repeat(40) { seed ->
            val rnd = Random(seed)
            val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
            val s = LedgerStore(ExpensesDb(fresh), HlcClock("n$seed"), { 5_000L })
            accounts.forEach(s::saveAccount)
            val live = LinkedHashMap<String, DomainEntry>()
            val refundedSoFar = HashMap<String, Long>()
            var next = 0
            fun id() = EntryId("e${next++}")
            fun acc() = accounts[rnd.nextInt(accounts.size)].id

            repeat(60) {
                when (rnd.nextInt(10)) {
                    0, 1, 2 -> { // expense, maybe split, maybe shared
                        val amount = rnd.nextLong(2, 50_000)
                        val parts = splitInto(amount, rnd.nextInt(1, 4), rnd)
                        val splits = if (rnd.nextBoolean()) parts.mapIndexed { i, a -> CategorySplit(cats[(i + rnd.nextInt(5)) % 5].let { c -> if (parts.indices.count { j -> j < i } >= 0) CategoryId("${c.value}-$i") else c }, m(a)) } else emptyList()
                        val shares = if (rnd.nextInt(4) == 0) {
                            val theirs = rnd.nextLong(1, amount)
                            listOf(Share(null, m(amount - theirs)), Share(people[rnd.nextInt(2)], m(theirs)))
                        } else emptyList()
                        val e = Expense(id(), rnd.nextLong(0, 1_000), acc(), m(amount), splits, shares)
                        s.save(e); live[e.id.value] = e
                    }
                    3 -> { val e = Income(id(), rnd.nextLong(0, 1_000), acc(), m(rnd.nextLong(1, 90_000))); s.save(e); live[e.id.value] = e }
                    4 -> {
                        val from = acc(); var to = acc(); while (to == from) to = acc()
                        val e = Transfer(id(), rnd.nextLong(0, 1_000), from, to, m(rnd.nextLong(1, 9_000))); s.save(e); live[e.id.value] = e
                    }
                    5 -> {
                        val amount = rnd.nextLong(1, 5_000)
                        val splits = if (rnd.nextBoolean()) splitInto(amount, 2, rnd).mapIndexed { i, a -> CategorySplit(CategoryId("f$i"), m(a)) } else emptyList()
                        val e = FriendPaid(id(), rnd.nextLong(0, 1_000), people[rnd.nextInt(2)], m(amount), splits); s.save(e); live[e.id.value] = e
                    }
                    6, 7 -> { // back-dated refund of a random unshared expense
                        val candidates = live.values.filterIsInstance<Expense>().filter { it.shares.isEmpty() }
                        if (candidates.isNotEmpty()) {
                            val x = candidates[rnd.nextInt(candidates.size)]
                            val room = x.amount.minorUnits - (refundedSoFar[x.id.value] ?: 0L)
                            if (room > 0) {
                                val amt = rnd.nextLong(1, room + 1)
                                val r = Refund(id(), rnd.nextLong(0, 1_000), x.account, m(amt), x.id)
                                s.save(r); live[r.id.value] = r; refundedSoFar[x.id.value] = (refundedSoFar[x.id.value] ?: 0L) + amt
                            }
                        }
                    }
                    8 -> { // delete a refund, which re-allocates the remaining ones
                        val refunds = live.values.filterIsInstance<Refund>()
                        val r = if (refunds.isEmpty()) null else refunds[rnd.nextInt(refunds.size)]
                        if (r != null) {
                            s.delete(r.id); live.remove(r.id.value)
                            refundedSoFar[r.refundsEntry.value] = refundedSoFar.getValue(r.refundsEntry.value) - r.amount.minorUnits
                        }
                    }
                    else -> { val e = Settlement(id(), rnd.nextLong(0, 1_000), people[rnd.nextInt(2)], acc(), m(rnd.nextLong(1, 4_000)), SettlementDirection.entries[rnd.nextInt(2)]); s.save(e); live[e.id.value] = e }
                }
            }
            val ref = reference(live.values.toList())
            val loaded = s.loadLedger()
            assertEquals(ref.balances(), loaded.balances(), "seed $seed balances")
            for ((from, until) in listOf(Long.MIN_VALUE to Long.MAX_VALUE, 0L to 300L, 300L to 700L, 700L to 1_001L))
                for (basis in SpendBasis.entries) {
                    val expected = ref.spendByCategory(inr, basis, from, until)
                    assertEquals(expected, s.spendByCategory(inr, basis, from, until), "seed $seed SQL spend $basis [$from,$until)")
                    assertEquals(expected, loaded.spendByCategory(inr, basis, from, until), "seed $seed reloaded spend $basis")
                }
        }
    }

    private fun splitInto(total: Long, parts: Int, rnd: Random): List<Long> {
        if (parts <= 1 || total < parts) return listOf(total)
        val cuts = (1 until parts).map { rnd.nextLong(1, total) }.toSortedSet().toList()
        val points = listOf(0L) + cuts + listOf(total)
        return points.zipWithNext { a, b -> b - a }.filter { it > 0 }
    }

    // ---- rules ----

    @Test
    fun invalidEntriesAreRejectedAndLeaveNothingBehind() {
        store.save(Expense(EntryId("x"), 1, bank.id, m(1_000), listOf(CategorySplit(cats[0], m(1_000)))))
        store.save(Refund(EntryId("r"), 2, bank.id, m(600), EntryId("x")))
        fun rows() = listOf(
            db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().size,
            db.schemaQueries.allSplits().executeAsList().size,
            driver.getConnection().createStatement().executeQuery("select count(*) from spend_line").use { it.next(); it.getInt(1) },
            db.schemaQueries.opsSince("", 10_000).executeAsList().size,
        )
        val before = rows()
        assertFailsWith<IllegalArgumentException> { store.save(Refund(EntryId("r2"), 3, bank.id, m(401), EntryId("x"))) }
        assertFailsWith<IllegalArgumentException> { store.save(Refund(EntryId("r3"), 3, bank.id, m(1), EntryId("nope"))) }
        assertFailsWith<IllegalArgumentException> { store.save(Expense(EntryId("y"), 3, AccountId("ghost"), m(10))) }
        assertFailsWith<IllegalArgumentException> { store.save(Expense(EntryId("y"), 3, bank.id, m(100), listOf(CategorySplit(cats[0], m(99))))) }
        assertFailsWith<IllegalArgumentException> { store.save(Expense(EntryId("x"), 1, bank.id, m(500))) } // below what is refunded
        assertFailsWith<IllegalArgumentException> { store.save(Income(EntryId("x"), 1, bank.id, m(5))) } // kind change
        assertFailsWith<IllegalArgumentException> { store.delete(EntryId("x")) } // still has refunds
        assertEquals(before, rows())
    }

    @Test
    fun editingAnExpenseReallocatesItsRefundsAndDeletingARefundRestoresTheRest() {
        val a = CategoryId("a"); val b = CategoryId("b")
        val x = Expense(EntryId("x"), 1, bank.id, m(3), listOf(CategorySplit(a, m(1)), CategorySplit(b, m(2))))
        val r1 = Refund(EntryId("r1"), 2, bank.id, m(1), x.id)
        val r2 = Refund(EntryId("r2"), 3, bank.id, m(1), x.id)
        listOf(x, r1, r2).forEach { store.save(it) }
        assertAgrees(listOf(x, r1, r2), listOf(Long.MIN_VALUE to Long.MAX_VALUE))
        val x2 = Expense(x.id, 1, bank.id, m(3), listOf(CategorySplit(a, m(2)), CategorySplit(b, m(1))))
        store.save(x2)
        assertAgrees(listOf(x2, r1, r2), listOf(Long.MIN_VALUE to Long.MAX_VALUE))
        store.delete(r1.id)
        assertAgrees(listOf(x2, r2), listOf(Long.MIN_VALUE to Long.MAX_VALUE))
        store.restore(r1.id)
        assertAgrees(listOf(x2, r1, r2), listOf(Long.MIN_VALUE to Long.MAX_VALUE))
    }

    @Test
    fun deletingAnEarlierRefundMovesALaterRefundToADifferentCategory() {
        // x = a:1 + b:2. Refund r1 (1) lands on b (largest remainder); then r2 (1) lands on a (tie, first).
        // Without r1, r2 alone lands on b. So deleting r1 must rewrite r2's stored lines.
        val a = CategoryId("a"); val b = CategoryId("b")
        val x = Expense(EntryId("x"), 1, bank.id, m(3), listOf(CategorySplit(a, m(1)), CategorySplit(b, m(2))))
        val r1 = Refund(EntryId("r1"), 2, bank.id, m(1), x.id)
        val r2 = Refund(EntryId("r2"), 3, bank.id, m(1), x.id)
        listOf(x, r1, r2).forEach { store.save(it) }
        assertEquals(mapOf<CategoryId?, Money>(b to m(1)), store.spendByCategory(inr))
        store.delete(r1.id)
        assertEquals(mapOf<CategoryId?, Money>(a to m(1), b to m(1)), store.spendByCategory(inr))
        assertAgrees(listOf(x, r2), listOf(Long.MIN_VALUE to Long.MAX_VALUE))
    }

    @Test
    fun restoringARefundThatNoLongerFitsIsRejected() {
        val x = Expense(EntryId("x"), 1, bank.id, m(1_000))
        store.save(x)
        store.save(Refund(EntryId("r1"), 2, bank.id, m(800), x.id))
        store.delete(EntryId("r1"))
        store.save(Refund(EntryId("r2"), 3, bank.id, m(700), x.id))
        assertFailsWith<IllegalArgumentException> { store.restore(EntryId("r1")) }
        assertEquals(m(700), store.spendByCategory(inr, SpendBasis.MY_SHARE).getValue(null).let { m(1_000) - it })
    }

    @Test
    fun deletedEntriesVanishFromReloadsReportsAndSearchButEditsAreBlocked() {
        store.save(Expense(EntryId("x"), 1, bank.id, m(1_000), listOf(CategorySplit(cats[0], m(1_000)))), EntryDetails(merchant = "Swiggy", note = "dinner"))
        assertEquals(listOf("x"), db.search("swiggy").map { it.id })
        store.delete(EntryId("x"))
        assertTrue(store.loadLedger().entries.isEmpty())
        assertTrue(store.spendByCategory(inr).isEmpty())
        assertTrue(db.search("swiggy").isEmpty())
        assertFailsWith<IllegalArgumentException> { store.save(Expense(EntryId("x"), 1, bank.id, m(5))) }
        store.restore(EntryId("x"))
        assertEquals(m(1_000), store.spendByCategory(inr).getValue(cats[0]))
        assertEquals(listOf("x"), db.search("swiggy").map { it.id })
    }

    // ---- sync log ----

    @Test
    fun theSyncLogRecordsOnlyChangedFieldsInStrictlyIncreasingClockOrder() {
        val base = db.schemaQueries.opsSince("", 10_000).executeAsList().size // account operations
        store.save(Expense(EntryId("x"), 5, bank.id, m(1_000)), EntryDetails(merchant = "Cafe"))
        val created = db.schemaQueries.opsSince("", 10_000).executeAsList().drop(base)
        assertEquals(setOf("kind", "occurred_at", "account_id", "amount_minor", "currency", "merchant", "source", "splits", "shares"), created.map { it.field_ }.toSet())
        clockMillis += 10
        store.save(Expense(EntryId("x"), 5, bank.id, m(1_250)), EntryDetails(merchant = "Cafe"))
        val edit = db.schemaQueries.opsSince("", 10_000).executeAsList().drop(base + created.size)
        assertEquals(listOf("amount_minor" to "1250"), edit.map { it.field_ to it.value_ })
        store.save(Expense(EntryId("x"), 5, bank.id, m(1_250)), EntryDetails(merchant = "Cafe"))
        assertEquals(base + created.size + 1, db.schemaQueries.opsSince("", 10_000).executeAsList().size) // no change, no ops
        store.delete(EntryId("x"))
        assertEquals("1", db.schemaQueries.latestOp("entry", "x", "deleted").executeAsOne().value_)
        val stamps = db.schemaQueries.opsSince("", 10_000).executeAsList().map { Hlc.decode(it.hlc) }
        assertTrue(stamps.zipWithNext().all { (a, b) -> a < b })
    }
}
