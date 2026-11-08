package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one move that rewrites bills already on a phone ("a bill made from the confirmation of a payment goes to To review"): it is the person's tap, one all-or-nothing step, it deletes nothing,
 * the pending item keeps the original words, it cannot run twice, and Undo puts both back. Texts, banks and amounts are invented.
 */
class BillRepairsTest {
    private val d = Device("a").also { it.ledger.saveAccount(Account(AccountId("main"), "Main", AccountType.BANK, it.inr)) }
    private val inbox = InboxStore(d.db)
    private val bills = BillStore(d.db)
    private val repairs = BillRepairs(d.db, inbox, bills)
    private val q get() = d.db.schemaQueries
    private val text = "Hi Asha, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811"

    private fun bill(id: String, status: BillStatus = BillStatus.FOUND, body: String? = text) =
        Bill(id, "SMS", "AX-KOTAKB-S", "Kotak Mahindra Bank", "CREDIT_CARD", "Kotak Mahindra Bank credit card", 1_234_567, null, "INR", null, null, null, false, 1_000, body, status, null, null, "key-$id")

    private fun item(id: String) = InboxItem(id, "SMS", "AX-KOTAKB-S", "Kotak Mahindra Bank", 1_000, "DEBIT", 1_234_567, "INR", null, null, null, false, "GENERIC", text, InboxStatus.PENDING, null)

    @Test
    fun theTextWaitsInToReviewWithItsOriginalWordsAndTheBillIsSkippedNotDeleted() {
        assertTrue(bills.add(bill("b")))
        val moved = repairs.move(bills.get("b")!!, item("i"), "fp-1")
        val pending = inbox.get("i")!!
        assertEquals(InboxStatus.PENDING, pending.status); assertEquals(text, pending.body, "nothing is lost: the original words wait in To review"); assertEquals(1_234_567L, pending.amountMinor)
        val gone = bills.get("b")!!
        assertEquals(BillStatus.DISMISSED, gone.status); assertEquals(1_234_567L, gone.amountMinor, "the row is still there"); assertEquals("i", moved.itemId)
        assertEquals(emptyList(), bills.list(BillStatus.FOUND))
        assertEquals(1, q.billCount("DISMISSED").executeAsOne().toInt())
        assertTrue(!bills.add(bill("again").copy(dedupeKey = "key-b")), "the same text cannot become a bill again")
    }

    @Test
    fun aBillThePersonHasActedOnSinceItWasLookedAtChangesNothingAndLeavesNoItemBehind() {
        assertTrue(bills.add(bill("t"))); assertTrue(bills.track("t"))
        assertFailsWith<IllegalArgumentException> { repairs.move(bills.get("t")!!, item("i1"), "fp-t") }
        assertNull(inbox.get("i1"), "all or nothing: no pending item was made"); assertEquals(BillStatus.TRACKED, bills.get("t")!!.status)
        assertTrue(bills.add(bill("p"))); bills.markPaid("p", null, 5)
        assertFailsWith<IllegalArgumentException> { repairs.move(bills.get("p")!!, item("i2"), "fp-p") }
        assertNull(inbox.get("i2")); assertEquals(BillStatus.PAID, bills.get("p")!!.status)
        assertFailsWith<IllegalArgumentException> { repairs.move(bill("ghost"), item("i3"), "fp-g") }
        assertNull(inbox.get("i3"))
    }

    @Test
    fun itCannotRunTwiceAndTheSameTextAlreadyInToReviewIsNotMadeTwice() {
        assertTrue(bills.add(bill("b")))
        val b = bills.get("b")!!
        repairs.move(b, item("i"), "fp-1")
        assertFailsWith<IllegalArgumentException> { repairs.move(b, item("j"), "fp-2") }
        assertNull(inbox.get("j")); assertEquals(1, inbox.list().size)
        // a second wrong bill made from the very same text (another day's copy) when its item is already waiting: the bill goes, no second item
        assertTrue(bills.add(bill("c")))
        val moved = repairs.move(bills.get("c")!!, item("k"), "fp-1")
        assertNull(moved.itemId); assertNull(inbox.get("k")); assertEquals(1, inbox.list().size); assertEquals(BillStatus.DISMISSED, bills.get("c")!!.status)
    }

    @Test
    fun undoPutsTheBillBackWithItsWordsTakesAwayTheUntouchedItemItMadeAndTheMoveCanBeDoneAgain() {
        assertTrue(bills.add(bill("b")))
        val moved = repairs.move(bills.get("b")!!, item("i"), "fp-1")
        repairs.undo(moved)
        val back = bills.get("b")!!
        assertEquals(BillStatus.FOUND, back.status); assertEquals(text, back.body); assertEquals(1_234_567L, back.amountMinor)
        assertNull(inbox.get("i"), "the item the move made, untouched, is taken away: its words are on the bill")
        assertEquals(0, inbox.pendingCount().toInt())
        val again = repairs.move(bills.get("b")!!, item("i2"), "fp-1")
        assertEquals("i2", again.itemId); assertEquals(text, inbox.get("i2")!!.body, "moved again: the text waits in To review again")
    }

    @Test
    fun anItemThePersonHasHandledSinceIsLeftExactlyAsTheyLeftItWhenTheMoveIsUndone() {
        assertTrue(bills.add(bill("b")))
        val moved = repairs.move(bills.get("b")!!, item("i"), "fp-1")
        assertTrue(inbox.confirm("i", "an-entry"))
        repairs.undo(moved)
        assertEquals(InboxStatus.CONFIRMED, inbox.get("i")!!.status); assertEquals("an-entry", inbox.get("i")!!.entryId)
        assertEquals(BillStatus.FOUND, bills.get("b")!!.status)
    }

    @Test
    fun itDoesNotTouchTheLedgerAndCostsNothingOnATwentyThousandEntryLedger() {
        var tick = 0L
        val big = LedgerStore(d.db, app.expenses.core.domain.sync.HlcClock("big")) { ++tick }
        big.atomically {
            repeat(20_000) { n -> big.save(Expense(EntryId("e$n"), 1_000L + n, AccountId("main"), Money(100L + n, Currency.INR), listOf(CategorySplit(CategoryId("Other"), Money(100L + n, Currency.INR))))) }
        }
        assertTrue(bills.add(bill("b")))
        val before = q.entryCount().executeAsOne()
        val t0 = System.nanoTime()
        val found = bills.list(BillStatus.FOUND, 500)
        repairs.move(found.single(), item("i"), "fp-1")
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(before, q.entryCount().executeAsOne(), "no entry is read or changed")
        assertTrue(ms < 250, "the move took $ms ms on 20,000 entries")
        println("PERF bill repair on 20,000 entries: $ms ms")
    }
}
