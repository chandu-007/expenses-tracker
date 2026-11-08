package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.TransferLearning
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.Transfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adding a waiting text from To review as more than an entry: a text that joins the entry holding its other leg, and an add (a transfer the person chose, Mark paid on a bill payment) that Undo takes back
 * exactly. The texts, digits and amounts are invented.
 */
class CaptureAddsTest {
    private val d = Device("a").also {
        it.ledger.saveAccount(Account(AccountId("main"), "Main 1234", AccountType.BANK, it.inr))
        it.ledger.saveAccount(Account(AccountId("save"), "Savings 5678", AccountType.BANK, it.inr))
        it.ledger.saveAccount(Account(AccountId("card"), "Axis card", AccountType.CREDIT_CARD, it.inr))
    }
    private val inbox = InboxStore(d.db)
    private val origins = OriginStore(d.db)
    private val bills = BillStore(d.db)
    private val adds = CaptureAdds(d.db, d.ledger, inbox, origins, bills)
    private val q get() = d.db.schemaQueries
    private val amount = 1_234_567L

    private val debitText = "Rs.12,345.67 debited from A/c XX1234 on 05-11-26 and transferred to A/c XX5678. Ref 612345678901"
    private val creditText = "Rs.12,345.67 credited to A/c XX5678 on 05-11-26 by A/c XX1234. Ref 612345678901"

    private fun item(id: String, direction: String, last4: String, at: Long, body: String, issuer: String = "HDFC Bank") =
        InboxItem(id, "SMS", "AX-HDFCBK-S", issuer, at, direction, amount, "INR", null, null, last4, false, "GENERIC", body, InboxStatus.PENDING, null).also { assertTrue(inbox.add(it, "fp-$id")) }

    private fun bill(id: String, status: BillStatus = BillStatus.FOUND) =
        Bill(id, "SMS", "AX-AXISBK-S", "Axis Bank", "CREDIT_CARD", "Axis Bank credit card 4321", amount, 61_728, "INR", "2026-11-15", "4321", null, false, 1_000, "the bill's words", status, null, null, "key-$id")

    /** What Add does for a transfer the person chose: the entry, its origin and the item confirmed. */
    private fun addedAsTransfer(itemId: String, entryId: String, ref: String, at: Long) {
        d.ledger.atomically {
            d.ledger.save(Transfer(EntryId(entryId), at, AccountId("main"), AccountId("save"), Money(amount, Currency.INR)), EntryDetails(merchant = "Transfer to Savings", source = "sms", sourceRef = ref))
            origins.put(entryId, OriginKind.SMS, "AX-HDFCBK-S", inbox.get(itemId)!!.body!!, at)
            inbox.confirm(itemId, entryId)
        }
    }

    private fun live() = q.recentEntries(50).executeAsList()
    private fun balance(id: String) = d.ledger.balances().getValue(AccountId(id)).minorUnits

    @Test
    fun theOtherLegJoinsTheTransferAndNothingNewIsRecorded() {
        item("out", "DEBIT", "1234", 1_000, debitText)
        addedAsTransfer("out", "t1", "612345678901", 1_000)
        item("in", "CREDIT", "5678", 1_100, creditText)
        val held = adds.join("in", "t1")
        assertEquals(1, live().size, "still one entry")
        assertEquals(InboxStatus.CONFIRMED, inbox.get("in")!!.status); assertEquals("t1", inbox.get("in")!!.entryId); assertNull(inbox.get("in")!!.body, "an added item forgets its text")
        val origin = origins.get("t1")!!
        assertTrue(debitText in origin.text && creditText in origin.text, "both texts are where the entry came from"); assertEquals(1_000L, origin.receivedAt, "the first text's time stays")
        assertEquals(-amount, balance("main")); assertEquals(amount, balance("save"))
        assertEquals(false, held.created)
    }

    @Test
    fun undoingAJoinPutsTheEntrysWordsBackAndTheTextWaitsAgain() {
        item("out", "DEBIT", "1234", 1_000, debitText)
        addedAsTransfer("out", "t1", "612345678901", 1_000)
        item("in", "CREDIT", "5678", 1_100, creditText)
        val held = adds.join("in", "t1")
        adds.undo(held)
        assertEquals(debitText, origins.get("t1")!!.text, "the entry's words are as they were")
        assertEquals(InboxStatus.PENDING, inbox.get("in")!!.status); assertEquals(creditText, inbox.get("in")!!.body); assertNull(inbox.get("in")!!.entryId)
        assertEquals(listOf("TRANSFER"), live().map { it.kind }, "the transfer itself is untouched")
    }

    @Test
    fun anEntryWithNoWordsGetsTheJoinedTextAndLosesItAgainOnUndo() {
        d.ledger.save(Transfer(EntryId("typed"), 1_000, AccountId("main"), AccountId("save"), Money(amount, Currency.INR)))
        item("in", "CREDIT", "5678", 1_100, creditText)
        val held = adds.join("in", "typed")
        assertEquals(creditText, origins.get("typed")!!.text)
        adds.undo(held)
        assertNull(origins.get("typed"), "an entry typed by hand has no words again")
    }

    @Test
    fun aJoinRefusesAHandledItemOrAGoneEntryAndChangesNothing() {
        item("in", "CREDIT", "5678", 1_100, creditText)
        assertFailsWith<IllegalArgumentException> { adds.join("in", "nowhere") }
        assertEquals(InboxStatus.PENDING, inbox.get("in")!!.status)
        d.ledger.save(Transfer(EntryId("t1"), 1_000, AccountId("main"), AccountId("save"), Money(amount, Currency.INR)))
        inbox.dismiss("in")
        assertFailsWith<IllegalArgumentException> { adds.join("in", "t1") }
        d.ledger.delete(EntryId("t1"))
        assertTrue(inbox.undismiss("in", creditText))
        assertFailsWith<IllegalArgumentException> { adds.join("in", "t1") }
        assertEquals(InboxStatus.PENDING, inbox.get("in")!!.status)
    }

    @Test
    fun undoingAnAddRemovesTheEntryFreesItsReferenceAndPutsTheBillAndTheTextBack() {
        item("out", "DEBIT", "1234", 1_000, "Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful!", issuer = "Kotak Mahindra Bank")
        val b = bill("bill1"); assertTrue(bills.add(b)); assertTrue(bills.track("bill1")); val tracked = bills.get("bill1")!!
        d.ledger.atomically {
            d.ledger.save(Transfer(EntryId("pay"), 1_000, AccountId("main"), AccountId("card"), Money(amount, Currency.INR)), EntryDetails(merchant = "Axis Bank credit card 4321", source = "sms", sourceRef = "ref-out"))
            origins.put("pay", OriginKind.SMS, "AX-KOTAKB-S", inbox.get("out")!!.body!!, 1_000)
            inbox.confirm("out", "pay")
            bills.markPaid("bill1", "pay", 1_000)
        }
        assertEquals(BillStatus.PAID, bills.get("bill1")!!.status); assertNull(bills.get("bill1")!!.body)
        assertEquals(amount, balance("card"))
        adds.undo(CaptureAdds.Held("out", "Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful!", "pay", created = true, originBefore = null, bill = tracked))
        assertTrue(live().isEmpty(), "the transfer is deleted"); assertEquals(0L, balance("card")); assertEquals(0L, balance("main"))
        assertNull(origins.get("pay")); assertNull(q.entryBySourceRef("sms", "ref-out").executeAsOneOrNull(), "its reference is free, so the text can be added again")
        val back = bills.get("bill1")!!
        assertEquals(BillStatus.TRACKED, back.status, "as it was, tracked"); assertEquals("the bill's words", back.body); assertNull(back.entryId); assertNull(back.paidAt)
        assertEquals(InboxStatus.PENDING, inbox.get("out")!!.status); assertEquals("Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful!", inbox.get("out")!!.body)
    }

    @Test
    fun aFoundBillGoesBackToFoundNotTracked() {
        assertTrue(bills.add(bill("b")))
        assertTrue(bills.markPaid("b", null, 5))
        assertTrue(bills.unpayTo("b", BillStatus.FOUND, "the bill's words"))
        assertEquals(BillStatus.FOUND, bills.get("b")!!.status); assertEquals("the bill's words", bills.get("b")!!.body)
        assertTrue(!bills.unpayTo("b", BillStatus.FOUND, "x"), "not paid: nothing to take back")
        assertFailsWith<IllegalArgumentException> { bills.unpayTo("b", BillStatus.PAID, null) }
    }

    @Test
    fun theQueriesForTheOtherLegAndTheSamePaymentFindTransfersAndExpensesOfTheExactAmount() {
        d.ledger.save(Transfer(EntryId("t1"), 10_000, AccountId("main"), AccountId("save"), Money(amount, Currency.INR)), EntryDetails(merchant = "Move"))
        d.ledger.save(Expense(EntryId("e1"), 10_500, AccountId("main"), Money(amount, Currency.INR), listOf(CategorySplit(CategoryId("Other"), Money(amount, Currency.INR)))), EntryDetails(merchant = "Bills"))
        d.ledger.save(Expense(EntryId("e2"), 10_500, AccountId("main"), Money(amount + 1, Currency.INR), listOf(CategorySplit(CategoryId("Other"), Money(amount + 1, Currency.INR)))))
        val legs = q.transferLegsNear(amount, "INR", 0, 20_000).executeAsList()
        assertEquals(listOf("t1"), legs.map { it.id }); assertEquals("main", legs.single().account_id); assertEquals("save", legs.single().counter_account_id)
        val near = q.entriesNearBillPayment(amount, "INR", 0, 20_000).executeAsList()
        assertEquals(setOf("t1", "e1"), near.map { it.id }.toSet(), "an expense or a transfer of exactly this amount, not another amount")
        assertEquals(emptyList(), q.entriesNearBillPayment(amount, "INR", 20_000, 30_000).executeAsList(), "outside the window")
        assertEquals(emptyList(), q.entriesNearBillPayment(amount, "USD", 0, 20_000).executeAsList())
    }

    @Test
    fun whatThePersonTaughtIsKeptUnderItsKeyAndForgottenWhenAskedAndHoldsNoText() {
        val key = "transfer_rule:p:out:ravikumar"
        assertNull(d.templates.transferRule(key))
        d.templates.putTransferRule(key, TransferLearning.Rule("save", 2, 0))
        assertEquals(TransferLearning.Rule("save", 2, 0), d.templates.transferRule(key))
        assertEquals(key to TransferLearning.Rule("save", 2, 0), d.templates.firstTransferRule(listOf("transfer_rule:none", key)))
        assertEquals("save|2|0", d.templates.setting(key), "an account id and two counts: no word of a text")
        d.templates.putTransferRule(key, null)
        assertNull(d.templates.transferRule(key))
    }
}
