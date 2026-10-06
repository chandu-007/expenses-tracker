package app.expenses.core.domain.ledger

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LedgerTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)

    private val bank = Account(AccountId("bank"), "Bank", AccountType.BANK, inr)
    private val cash = Account(AccountId("cash"), "Cash", AccountType.CASH, inr)
    private val card = Account(AccountId("card"), "Card", AccountType.CREDIT_CARD, inr)
    private val food = CategoryId("food")
    private val travel = CategoryId("travel")
    private val friend = PersonId("asha")

    private fun base() = Ledger.EMPTY.addAccount(bank).addAccount(cash).addAccount(card)
    private var n = 0
    private fun id() = EntryId("e${n++}")

    @Test
    fun transfersNeverChangeNetWorthOrSpend() = runTest {
        checkAll(Arb.list(Arb.long(1, 1_000_000), 1..20)) { amounts ->
            var l = base().add(Income(id(), 0, bank.id, m(100_000_000)))
            val before = l.netWorth(inr)
            for ((i, a) in amounts.withIndex()) {
                l = if (i % 2 == 0) l.add(Transfer(id(), 1, bank.id, cash.id, m(a)))
                else l.add(Transfer(id(), 1, bank.id, card.id, m(a)))
            }
            assertEquals(before, l.netWorth(inr))
            assertTrue(l.totalSpend(inr).isZero)
        }
    }

    @Test
    fun cardSpendThenBillPaymentCountsOnce() {
        val l = base()
            .add(Income(id(), 0, bank.id, m(50_000)))
            .add(Expense(id(), 1, card.id, m(12_000), listOf(CategorySplit(food, m(12_000)))))
            .add(Transfer(id(), 2, bank.id, card.id, m(12_000)))
        assertEquals(m(12_000), l.totalSpend(inr))
        assertEquals(m(0), l.balance(card.id))
        assertEquals(m(38_000), l.balance(bank.id))
    }

    @Test
    fun atmWithdrawalMovesMoneyToCashWallet() {
        val l = base()
            .add(Income(id(), 0, bank.id, m(10_000)))
            .add(Transfer(id(), 1, bank.id, cash.id, m(2_000)))
            .add(Expense(id(), 2, cash.id, m(500)))
        assertEquals(m(1_500), l.balance(cash.id))
        assertEquals(m(500), l.totalSpend(inr))
    }

    @Test
    fun refundNetsAgainstOriginalCategories() {
        val exp = Expense(
            EntryId("x"), 1, bank.id, m(1_000),
            listOf(CategorySplit(food, m(600)), CategorySplit(travel, m(400))),
        )
        val l = base().add(exp).add(Refund(id(), 2, bank.id, m(500), exp.id))
        assertEquals(m(500), l.totalSpend(inr))
        assertEquals(m(300), l.spendByCategory(inr)[food])
        assertEquals(m(200), l.spendByCategory(inr)[travel])
    }

    @Test
    fun refundsCannotExceedOriginalOrReferenceMissingEntries() {
        val exp = Expense(EntryId("x"), 1, bank.id, m(1_000))
        val l = base().add(exp).add(Refund(id(), 2, bank.id, m(600), exp.id))
        assertFailsWith<IllegalArgumentException> { l.add(Refund(id(), 3, bank.id, m(401), exp.id)) }
        assertFailsWith<IllegalArgumentException> { l.add(Refund(id(), 3, bank.id, m(1), EntryId("nope"))) }
    }

    @Test
    fun sharedExpenseCountsMyShareAndCreatesReceivable() {
        val l = base().add(
            Expense(
                id(), 1, bank.id, m(4_000),
                splits = listOf(CategorySplit(food, m(4_000))),
                shares = listOf(Share(null, m(1_000)), Share(friend, m(3_000))),
            ),
        )
        assertEquals(m(1_000), l.totalSpend(inr, SpendBasis.MY_SHARE))
        assertEquals(m(4_000), l.totalSpend(inr, SpendBasis.CASH_OUT))
        assertEquals(m(3_000), l.personBalance(friend, inr))
        assertEquals(m(-4_000), l.balance(bank.id))
    }

    @Test
    fun friendPaidCreatesPayableWithoutMovingAccounts() {
        val l = base().add(FriendPaid(id(), 1, friend, m(700), listOf(CategorySplit(food, m(700)))))
        assertEquals(m(700), l.totalSpend(inr, SpendBasis.MY_SHARE))
        assertTrue(l.totalSpend(inr, SpendBasis.CASH_OUT).isZero)
        assertEquals(m(-700), l.personBalance(friend, inr))
        assertTrue(l.balances().values.all { it.isZero })
    }

    @Test
    fun settlementClearsPersonBalanceWithoutAffectingSpend() {
        val l = base()
            .add(Expense(id(), 1, bank.id, m(4_000), shares = listOf(Share(null, m(1_000)), Share(friend, m(3_000)))))
            .add(Settlement(id(), 2, friend, bank.id, m(3_000), SettlementDirection.RECEIVED_FROM_PERSON))
        assertTrue(l.personBalance(friend, inr).isZero)
        assertEquals(m(1_000), l.totalSpend(inr))
        assertEquals(m(-1_000), l.balance(bank.id))
    }

    @Test
    fun refundChainsNetExactlyAndNeverGoNegativeOrExceedTheOriginal() = runTest {
        checkAll(
            Arb.long(2, 1_000_000),
            Arb.list(Arb.long(1, 1_000), 2..5),
            Arb.list(Arb.long(1, 1_000_000), 1..8),
        ) { total, weights, refundAttempts ->
            val parts = allocate(total, weights).filter { it > 0 }
            val cats = parts.indices.map { CategoryId("c$it") }
            val exp = Expense(
                EntryId("orig"), 1, bank.id, m(parts.sum()),
                parts.mapIndexed { i, p -> CategorySplit(cats[i], m(p)) },
            )
            var l = base().add(exp)
            var refunded = 0L
            for ((i, r) in refundAttempts.withIndex()) {
                val fits = refunded + r <= exp.amount.minorUnits
                val attempt = Refund(EntryId("r$i"), 2, bank.id, m(r), exp.id)
                if (fits) {
                    l = l.add(attempt)
                    refunded += r
                } else {
                    val before = l
                    assertFailsWith<IllegalArgumentException> { l.add(attempt) }
                    assertEquals(before.entries, l.entries)
                }
                val spend = l.spendByCategory(inr)
                assertEquals(exp.amount.minorUnits - refunded, spend.values.sumOf { it.minorUnits })
                assertTrue(spend.values.all { it.minorUnits >= 0 })
            }
            assertEquals(refunded, l.refundedMinorUnits(exp.id))
        }
    }


    @Test
    fun chainedSmallRefundsNeverOverRefundASingleCategory() {
        // Regression: splitting each 1-paisa refund independently by 1:2 refunded the second category 3 against 2.
        val a = CategoryId("a")
        val b = CategoryId("b")
        val exp = Expense(EntryId("o"), 1, bank.id, m(3), listOf(CategorySplit(a, m(1)), CategorySplit(b, m(2))))
        val l = base().add(exp)
            .add(Refund(EntryId("r1"), 2, bank.id, m(1), exp.id))
            .add(Refund(EntryId("r2"), 3, bank.id, m(1), exp.id))
            .add(Refund(EntryId("r3"), 4, bank.id, m(1), exp.id))
        assertTrue(l.spendByCategory(inr).isEmpty())
        assertTrue(l.totalSpend(inr).isZero)
    }
    @Test
    fun balancesDoNotDependOnEntryOrderAcrossMixedEntryTypes() = runTest {
        checkAll(Arb.list(Arb.long(1, 10_000), 3..20), Arb.long(0, Long.MAX_VALUE)) { amounts, seed ->
            val entries = amounts.mapIndexed { i, a ->
                val id = EntryId("m$i")
                when (i % 4) {
                    0 -> Income(id, i.toLong(), bank.id, m(a))
                    1 -> Expense(id, i.toLong(), card.id, m(a))
                    2 -> Transfer(id, i.toLong(), bank.id, cash.id, m(a))
                    else -> Transfer(id, i.toLong(), bank.id, card.id, m(a))
                }
            }
            val shuffled = entries.shuffled(kotlin.random.Random(seed))
            val a = base().addAll(entries)
            val b = base().addAll(shuffled)
            assertEquals(a.balances(), b.balances())
            assertEquals(a.totalSpend(inr), b.totalSpend(inr))
            assertEquals(a.netWorth(inr), b.netWorth(inr))
        }
    }

    @Test
    fun invalidEntriesAreRejectedAtomically() {
        val l = base()
        assertFailsWith<IllegalArgumentException> {
            l.add(Expense(id(), 1, bank.id, m(100), splits = listOf(CategorySplit(food, m(99)))))
        }
        assertFailsWith<IllegalArgumentException> {
            l.add(Expense(id(), 1, bank.id, m(100), shares = listOf(Share(null, m(50)))))
        }
        assertFailsWith<IllegalArgumentException> {
            l.add(Expense(id(), 1, AccountId("ghost"), m(100)))
        }
        assertFailsWith<IllegalArgumentException> {
            l.add(Expense(id(), 1, bank.id, Money(100, Currency.USD)))
        }
        assertFailsWith<IllegalArgumentException> {
            l.addAll(listOf(Income(id(), 1, bank.id, m(10)), Expense(id(), 2, AccountId("ghost"), m(1))))
        }
        assertTrue(l.entries.isEmpty())
    }

    @Test
    fun crossCurrencyTransferKeepsBothAmounts() {
        val usd = Account(AccountId("usd"), "USD", AccountType.BANK, Currency.USD)
        val l = base().addAccount(usd)
            .add(Income(id(), 0, bank.id, m(100_000)))
            .add(Transfer(id(), 1, bank.id, usd.id, m(84_000), Money(1_000, Currency.USD)))
        assertEquals(m(16_000), l.balance(bank.id))
        assertEquals(Money(1_000, Currency.USD), l.balance(usd.id))
    }

    @Test
    fun allocationNeverLosesMinorUnits() = runTest {
        checkAll(Arb.long(0, 10_000_000), Arb.list(Arb.long(1, 1_000), 1..8)) { total, weights ->
            val parts = allocate(total, weights)
            assertEquals(total, parts.sum())
            assertTrue(parts.all { it >= 0 })
        }
    }

    @Test
    fun balancesAreReproducibleFromAnyEntryOrderOfIndependentAccounts() = runTest {
        checkAll(Arb.list(Arb.long(1, 10_000), 2..15)) { amounts ->
            val entries = amounts.mapIndexed { i, a -> Income(EntryId("i$i"), i.toLong(), bank.id, m(a)) }
            val forward = base().addAll(entries).balances()
            val backward = base().addAll(entries.reversed()).balances()
            assertEquals(forward, backward)
        }
    }
}
