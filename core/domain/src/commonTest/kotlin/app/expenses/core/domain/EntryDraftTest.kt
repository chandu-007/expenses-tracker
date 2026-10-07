package app.expenses.core.domain

import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Ledger
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Transfer
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The Phase 1 correctness corpus, built through the same path the form uses: transfers, card bill payment, refund, ATM to cash, split. */
class EntryDraftTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val id = EntryId("x")
    private fun ok(d: EntryDraft): app.expenses.core.domain.ledger.Entry = (EntryDrafts.build(d, id, inr) as DraftResult.Ok).entry
    private fun problem(d: EntryDraft) = (EntryDrafts.build(d, id, inr) as DraftResult.Problem).message
    private fun d(kind: DraftKind, amount: String = "100", account: String = "bank", to: String = "", category: String = "", splits: List<SplitDraft> = emptyList(), refunds: String? = null, refundable: Long = 0) =
        EntryDraft(null, kind, amount, category, splits, account, to, "", 5L, refunds, refundable)
    private fun ledger() = Ledger.EMPTY
        .addAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 1_000_000))
        .addAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        .addAccount(Account(AccountId("card"), "Card", AccountType.CREDIT_CARD, inr))

    @Test
    fun anExpenseIsOneCategoryOrNone() {
        assertEquals(listOf(CategoryId("Food")), (ok(d(DraftKind.EXPENSE, category = "Food")) as Expense).splits.map { it.category })
        assertEquals(emptyList(), (ok(d(DraftKind.EXPENSE)) as Expense).splits)
        assertEquals(CategoryId("Salary"), (ok(d(DraftKind.INCOME, category = "Salary")) as Income).category)
        assertEquals(null, (ok(d(DraftKind.INCOME)) as Income).category)
    }

    @Test
    fun aSplitMustAddUpExactlyAndEachPartNeedsACategoryAnAmountAndADistinctCategory() {
        fun split(vararg p: Pair<String, String>) = d(DraftKind.EXPENSE, "1000", splits = p.map { SplitDraft(it.first, it.second) })
        val e = ok(split("Food" to "600", "Home" to "400")) as Expense
        assertEquals(listOf(m(60_000), m(40_000)), e.splits.map { it.amount }); assertEquals(m(100_000), e.amount)
        assertEquals("₹100.00 is not assigned to any category yet.", problem(split("Food" to "600", "Home" to "300")))
        assertEquals("The parts add up to ₹50.00 more than the amount.", problem(split("Food" to "600", "Home" to "450")))
        assertEquals("Choose a category for part 2.", problem(split("Food" to "600", "" to "400")))
        assertEquals("Enter an amount for part 2.", problem(split("Food" to "600", "Home" to "")))
        assertEquals("Enter an amount for part 2.", problem(split("Food" to "600", "Home" to "0")))
        assertEquals("Use each category once; add the amounts together instead.", problem(split("Food" to "600", "Food" to "400")))
        assertEquals(40_000L, EntryDrafts.leftToAssign(split("Food" to "600", "Home" to ""), inr))
        assertEquals(null, EntryDrafts.leftToAssign(split("Food" to "6x", "Home" to ""), inr))
        // one part is not a split: it is simply the category (the editor may leave a single row behind)
        assertTrue(!EntryDrafts.isSplit(d(DraftKind.EXPENSE, splits = listOf(SplitDraft("Food", "100")))))
    }

    @Test
    fun aSplitOfAnyPartsThatSumToTheAmountAlwaysBuildsAndOneCentOffNeverDoes() = runTest {
        checkAll(Arb.list(Arb.int(1, 100_000), 2..6)) { parts ->
            val total = parts.sumOf { it.toLong() }
            val draft = d(DraftKind.EXPENSE, Money(total, inr).toPlainString(), splits = parts.mapIndexed { i, p -> SplitDraft("C$i", Money(p.toLong(), inr).toPlainString()) })
            assertEquals(total, (ok(draft) as Expense).splits.sumOf { it.amount.minorUnits })
            val off = draft.copy(amountText = Money(total + 1, inr).toPlainString())
            assertIs<DraftResult.Problem>(EntryDrafts.build(off, id, inr))
        }
    }

    @Test
    fun aTransferNeedsTwoDifferentAccountsAndCoversAtmAndCardBillPayment() {
        assertEquals("Choose two different accounts.", problem(d(DraftKind.TRANSFER, account = "bank", to = "bank")))
        assertEquals("Choose the account the money goes to.", problem(d(DraftKind.TRANSFER)))
        var l = ledger()
        // ATM: bank to cash; card spending then paying the card bill from the bank
        l = l.add(ok(d(DraftKind.TRANSFER, "5000", "bank", "cash")).let { (it as Transfer).copy(id = EntryId("atm")) })
        l = l.add(Expense(EntryId("swiggy"), 1, AccountId("card"), m(120_000)))
        assertEquals(m(-120_000), l.balances().getValue(AccountId("card")), "spending on the card is owed")
        l = l.add((ok(d(DraftKind.TRANSFER, "1200", "bank", "card")) as Transfer).copy(id = EntryId("billpay")))
        val b = l.balances()
        assertEquals(m(1_000_000 - 500_000 - 120_000), b.getValue(AccountId("bank")))
        assertEquals(m(500_000), b.getValue(AccountId("cash")))
        assertEquals(m(0), b.getValue(AccountId("card")), "paying the bill clears what the card owed")
        // none of it is spending: the only expense is the one purchase
        assertEquals(m(120_000), l.totalSpend(inr))
        assertEquals(m(1_000_000 - 120_000), l.netWorth(inr) , "moving money between my own accounts never changes net worth; only the purchase did")
    }

    @Test
    fun aRefundIsBoundedByWhatIsStillRefundableAndLowersSpendWithoutBeingIncome() {
        var l = ledger().add(Expense(EntryId("buy"), 1, AccountId("card"), m(300_000), listOf(app.expenses.core.domain.ledger.CategorySplit(CategoryId("Shopping"), m(300_000)))))
        val ref = d(DraftKind.REFUND, "1000", "card", refunds = "buy", refundable = 300_000)
        l = l.add((ok(ref) as Refund).copy(id = EntryId("r1")))
        assertEquals(m(200_000), l.totalSpend(inr), "net spend dropped by the refund")
        assertEquals(m(-200_000), l.balances().getValue(AccountId("card")), "and the card owes less")
        assertEquals("At most ₹2,000.00 can still be refunded.", problem(d(DraftKind.REFUND, "2500", "card", refunds = "buy", refundable = 200_000)))
        assertEquals("At most ₹2,000.00 can still be refunded.", problem(d(DraftKind.REFUND, "2000.01", "card", refunds = "buy", refundable = 200_000)), "one paisa over is refused")
        assertEquals("This expense has been refunded in full.", problem(d(DraftKind.REFUND, "1", "card", refunds = "buy", refundable = 0)))
        assertEquals(m(0), (ok(d(DraftKind.REFUND, "2000", "card", refunds = "buy", refundable = 200_000)).let { l.add((it as Refund).copy(id = EntryId("r2"))) }).totalSpend(inr), "the rest can be refunded")
        assertEquals("A refund needs the expense it is for.", problem(d(DraftKind.REFUND, "1", "card")))
    }

    @Test
    fun badInputsAreExplainedAndNothingIsBuilt() {
        assertEquals("Enter an amount.", problem(d(DraftKind.EXPENSE, "")))
        assertEquals("Enter an amount like 250 or 99.50.", problem(d(DraftKind.EXPENSE, "12.345")))
        assertEquals("Enter an amount like 250 or 99.50.", problem(d(DraftKind.EXPENSE, "0")))
        assertEquals("Choose an account.", problem(d(DraftKind.EXPENSE, account = "")))
    }
}
