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
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.Transfer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Own-account transfers read from two bank texts: the invented texts, digits and amounts below are not anyone's. */
class CaptureTransfersTest {
    private val d = Device("a").also {
        it.ledger.saveAccount(Account(AccountId("main"), "Main 1234", AccountType.BANK, it.inr))
        it.ledger.saveAccount(Account(AccountId("save"), "Savings 5678", AccountType.BANK, it.inr))
    }
    private val inbox = InboxStore(d.db)
    private val origins = OriginStore(d.db)
    private val transfers = CaptureTransfers(d.db, d.ledger, inbox, origins)
    private val q get() = d.db.schemaQueries

    private val outText = "Amt Deducted! Rs.45,250.75 from your HDFC Bank A/c XX1234 for Money Transfer via HDFC Bank Online Banking."
    private val inText = "Update! INR 45,250.75 deposited in HDFC Bank A/c XX5678 on 29-OCT-26 for XXXXXXXXXX1234-TPT-Trip-ANITA RAO.Avl bal INR 9.00"
    private val amount = 4_525_075L

    private fun item(id: String, direction: String, last4: String, at: Long, body: String) =
        InboxItem(id, "SMS", "AX-HDFCBK-S", "HDFC Bank", at, direction, amount, "INR", null, null, last4, false, "GENERIC", body, InboxStatus.PENDING, null).also { assertTrue(inbox.add(it, "fp-$id")) }

    private fun transfer(id: String = "t1", at: Long = 1_000) = Transfer(EntryId(id), at, AccountId("main"), AccountId("save"), Money(amount, Currency.INR))
    private fun live() = q.recentEntries(50).executeAsList()
    private fun balance(id: String) = d.ledger.balances().getValue(AccountId(id)).minorUnits

    /** What adding an item as an ordinary expense or income does (the same rows as the inbox's Add). */
    private fun addedPlain(itemId: String, outgoing: Boolean, account: String, at: Long) {
        val m = Money(amount, Currency.INR)
        val id = "plain-$itemId"
        if (outgoing) d.ledger.save(Expense(EntryId(id), at, AccountId(account), m, listOf(CategorySplit(CategoryId("Other"), m))), EntryDetails(merchant = "HDFC Bank", source = "sms", sourceRef = itemId))
        else d.ledger.save(Income(EntryId(id), at, AccountId(account), m, CategoryId("income:otherincome")), EntryDetails(merchant = "HDFC Bank", source = "sms", sourceRef = itemId))
        origins.put(id, OriginKind.SMS, "AX-HDFCBK-S", inbox.get(itemId)!!.body!!, at)
        inbox.confirm(itemId, id)
    }

    @Test
    fun twoWaitingTextsBecomeOneTransferAndNeitherIsAnExpenseOrIncome() {
        item("out", "DEBIT", "1234", 1_000, outText); item("in", "CREDIT", "5678", 1_020, inText)
        transfers.addPair(transfer(), "Trip", "in-ref", "out", "in")
        val e = live().single()
        assertEquals("TRANSFER", e.kind); assertEquals("main", e.account_id); assertEquals("save", e.counter_account_id); assertEquals(amount, e.amount_minor)
        assertEquals(-amount, balance("main")); assertEquals(amount, balance("save"))
        assertEquals(0, inbox.pendingCount())
        assertEquals(InboxStatus.CONFIRMED, inbox.get("out")!!.status); assertEquals("t1", inbox.get("out")!!.entryId); assertEquals("t1", inbox.get("in")!!.entryId)
        val origin = origins.get("t1")!!
        assertTrue(outText in origin.text && inText in origin.text, "both original texts are kept, untouched"); assertEquals(1_000L, origin.receivedAt)
        assertEquals("Trip", e.merchant)
    }

    @Test
    fun anItemAlreadyHandledOrATakenReferenceChangesNothing() {
        item("out", "DEBIT", "1234", 1_000, outText); item("in", "CREDIT", "5678", 1_020, inText)
        inbox.dismiss("in")
        assertFailsWith<IllegalArgumentException> { transfers.addPair(transfer(), null, "r", "out", "in") }
        assertTrue(live().isEmpty(), "no entry was left behind"); assertEquals(InboxStatus.PENDING, inbox.get("out")!!.status, "the first item was not consumed either")
        assertTrue(inbox.undismiss("in", inText))
        d.ledger.save(Expense(EntryId("x"), 1, AccountId("main"), Money(5, Currency.INR), listOf(CategorySplit(CategoryId("Other"), Money(5, Currency.INR)))), EntryDetails(source = "sms", sourceRef = "taken"))
        assertFailsWith<IllegalArgumentException> { transfers.addPair(transfer(), null, "taken", "out", "in") }
        assertEquals(1, live().size); assertEquals(2, inbox.pendingCount())
    }

    @Test
    fun theSecondTextReplacesTheEarlierExpenseAndUndoPutsEverythingBack() {
        item("out", "DEBIT", "1234", 1_000, outText)
        addedPlain("out", outgoing = true, account = "main", at = 1_000)
        item("in", "CREDIT", "5678", 60_000, inText)
        assertEquals(-amount, balance("main")); assertEquals(0, balance("save"))

        val r = transfers.replaceEarlier(transfer("t2", 1_000), "Trip", "in-ref", "in", "plain-out")
        val rows = live()
        assertEquals(listOf("TRANSFER"), rows.map { it.kind }, "the expense is gone from the ledger and there is no income: only the transfer")
        assertEquals(-amount, balance("main")); assertEquals(amount, balance("save"))
        assertEquals(1, q.entryById("plain-out").executeAsOne().deleted, "a soft delete, so it can be restored")
        assertEquals(InboxStatus.CONFIRMED, inbox.get("in")!!.status)
        val text = origins.get("t2")!!.text
        assertTrue(outText in text && inText in text, "both texts are kept")

        transfers.undoReplace(r)
        assertEquals(listOf("EXPENSE"), live().map { it.kind }); assertEquals("plain-out", live().single().id)
        assertEquals(-amount, balance("main")); assertEquals(0, balance("save"))
        assertEquals(1, q.entryById("t2").executeAsOne().deleted)
        val back = inbox.get("in")!!
        assertEquals(InboxStatus.PENDING, back.status); assertEquals(inText, back.body, "the item waits again with its text"); assertNull(back.entryId)
        assertEquals(outText, origins.get("plain-out")!!.text, "the earlier entry kept its own words")
        // after an undo the deleted transfer lets go of its reference: the text can be made a transfer again with the same reference, or added normally
        assertNull(q.entryBySourceRef("sms", "in-ref").executeAsOneOrNull(), "nothing holds the text's reference now")
        val again = transfers.replaceEarlier(transfer("t3", 1_000), null, "in-ref", "in", "plain-out")
        assertEquals("t3", again.transferId); assertEquals(listOf("TRANSFER"), live().map { it.kind })
    }

    @Test
    fun theEarlierIncomeCanBeTheFirstHalfToo() {
        item("in", "CREDIT", "5678", 1_000, inText); addedPlain("in", outgoing = false, account = "save", at = 1_000)
        item("out", "DEBIT", "1234", 30_000, outText)
        val r = transfers.replaceEarlier(transfer("t4", 1_000), null, "out-ref", "out", "plain-in")
        assertEquals(listOf("TRANSFER"), live().map { it.kind }); assertEquals(amount, balance("save")); assertEquals(-amount, balance("main"))
        transfers.undoReplace(r)
        assertEquals(listOf("INCOME"), live().map { it.kind }); assertEquals(amount, balance("save")); assertEquals(0, balance("main"))
    }

    @Test
    fun aFailureHalfwayLeavesTheEarlierEntryAndTheItemAsTheyWere() {
        item("out", "DEBIT", "1234", 1_000, outText); addedPlain("out", true, "main", 1_000)
        item("in", "CREDIT", "5678", 2_000, inText)
        // the entry is not a plain expense or income any more (it was already deleted by hand)
        d.ledger.delete(EntryId("plain-out"))
        assertFailsWith<IllegalArgumentException> { transfers.replaceEarlier(transfer("t5", 1_000), null, "r5", "in", "plain-out") }
        assertEquals(InboxStatus.PENDING, inbox.get("in")!!.status); assertTrue(live().isEmpty())
        // a refund on the earlier expense stops the delete, and so the whole step
        addedPlain2()
        assertFailsWith<IllegalArgumentException> { transfers.replaceEarlier(transfer("t6", 1_000), null, "r6", "in", "bought") }
        assertEquals(InboxStatus.PENDING, inbox.get("in")!!.status)
        assertEquals(setOf("bought", "refund"), live().map { it.id }.toSet(), "nothing was removed and no transfer was added")
    }

    private fun addedPlain2() {
        val m = Money(amount, Currency.INR)
        d.ledger.save(Expense(EntryId("bought"), 1_000, AccountId("main"), m, listOf(CategorySplit(CategoryId("Other"), m))), EntryDetails(source = "sms", sourceRef = "bought-ref"))
        d.ledger.save(Refund(EntryId("refund"), 1_500, AccountId("main"), Money(100, Currency.INR), EntryId("bought")))
    }

    @Test
    fun onlyPlainEntriesFromATextOfExactlyThisAmountAndTimeAreCandidates() {
        val m = Money(amount, Currency.INR)
        fun expense(id: String, at: Long = 1_000, amt: Money = m, source: String = "sms", shares: List<Share> = emptyList(), account: String = "main") =
            d.ledger.save(Expense(EntryId(id), at, AccountId(account), amt, listOf(CategorySplit(CategoryId("Other"), amt)), shares), EntryDetails(source = source, sourceRef = "ref-$id"))
        expense("ok"); expense("manual", source = "manual"); expense("other-amount", amt = Money(amount + 1, Currency.INR)); expense("shared", shares = listOf(Share(null, Money(amount / 2, Currency.INR)), Share(null, Money(amount - amount / 2, Currency.INR))))
        expense("late", at = 1_000 + 25 * 3_600_000L); expense("refunded"); d.ledger.save(Refund(EntryId("rf"), 2_000, AccountId("main"), Money(100, Currency.INR), EntryId("refunded")))
        d.ledger.save(Income(EntryId("inc"), 1_200, AccountId("save"), m, CategoryId("income:otherincome")), EntryDetails(source = "sms", sourceRef = "ref-inc"))
        origins.put("ok", OriginKind.SMS, "AX-HDFCBK-S", outText, 900)
        val found = transfers.earlier(amount, "INR", 1_500, 24 * 3_600_000L)
        assertEquals(setOf("ok", "inc"), found.map { it.entryId }.toSet())
        val ok = found.single { it.entryId == "ok" }
        assertTrue(ok.outgoing); assertEquals("main", ok.accountId); assertEquals(outText, ok.originText); assertEquals(900L, ok.receivedAt); assertEquals(OriginKind.SMS, ok.originKind)
        assertTrue(!found.single { it.entryId == "inc" }.outgoing)
        assertTrue(transfers.earlier(amount, "USD", 1_500, 24 * 3_600_000L).isEmpty(), "another currency")
        // the window runs on when the text arrived: an entry placed at noon of the bank's date (a delayed text) is still found by the arrival time
        expense("delayed", at = 1_000 + 40 * 3_600_000L); origins.put("delayed", OriginKind.SMS, "AX-HDFCBK-S", inText, 1_400)
        assertTrue("delayed" in transfers.earlier(amount, "INR", 1_500, 24 * 3_600_000L).map { it.entryId })
    }

    // ---- money neither created nor lost ----

    /** Everything a person could read off the books: balances, spending by category (whole ledger and a window), live entries' rows, splits, tags and origins. */
    private data class Books(
        val balances: Map<AccountId, Money>, val spendAll: Map<CategoryId?, Money>, val spendWindow: Map<CategoryId?, Money>, val incomeTotal: Long,
        val rows: List<app.expenses.core.storage.db.Entry>, val splits: Map<String, List<Pair<String, Long>>>, val tags: Map<String, List<String>>, val origins: Map<String, Origin?>,
    )

    private fun books(): Books {
        val rows = q.liveEntriesInInsertionOrder().executeAsList()
        return Books(
            d.ledger.balances(), d.ledger.spendByCategory(Currency.INR), d.ledger.spendByCategory(Currency.INR, from = 0L, until = 10_000L),
            rows.filter { it.kind == "INCOME" }.sumOf { it.amount_minor }, rows,
            rows.associate { it.id to q.splitsOf(it.id).executeAsList().map { s -> s.category_id to s.amount_minor } }, rows.associate { it.id to d.ledger.tagsOf(EntryId(it.id)).sorted() },
            rows.associate { it.id to origins.get(it.id) },
        )
    }

    private fun spendTotal(b: Books) = b.spendAll.values.sumOf { it.minorUnits }
    private fun netWorth(b: Books) = b.balances.values.sumOf { it.minorUnits }

    private fun otherBooks() {
        val m = Money(300_00, Currency.INR); val big = Money(5_000_00, Currency.INR)
        d.ledger.save(Expense(EntryId("coffee"), 500, AccountId("save"), m, listOf(CategorySplit(CategoryId("Food"), m))), EntryDetails(merchant = "Cafe", source = "manual"))
        d.ledger.save(Income(EntryId("salary"), 600, AccountId("main"), big, CategoryId("income:salary")), EntryDetails(merchant = "Acme", source = "manual"))
    }

    @Test
    fun convertingAnAcceptedExpenseToATransferMovesTheMoneyOnceAndUndoRestoresEverythingExactly() {
        otherBooks()
        item("out", "DEBIT", "1234", 1_000, outText)
        val m = Money(amount, Currency.INR)
        d.ledger.save(
            Expense(EntryId("plain-out"), 1_000, AccountId("main"), m, listOf(CategorySplit(CategoryId("Food"), m))),
            EntryDetails(merchant = "HDFC Bank", note = "for the trip", source = "sms", sourceRef = "out", tags = listOf("Trip", "80C")),
        )
        origins.put("plain-out", OriginKind.SMS, "AX-HDFCBK-S", outText, 1_000)
        inbox.confirm("out", "plain-out")
        item("in", "CREDIT", "5678", 60_000, inText)
        val before = books()
        assertEquals(amount + 300_00, spendTotal(before), "the expense and the coffee are spending")

        val r = transfers.replaceEarlier(transfer("t9", 1_000), "Trip", "in-ref", "in", "plain-out")
        val after = books()
        // the spending total drops by exactly the amount; the coffee is untouched
        assertEquals(spendTotal(before) - amount, spendTotal(after)); assertEquals(300_00L, spendTotal(after))
        assertEquals(before.spendWindow.values.sumOf { it.minorUnits } - amount, after.spendWindow.values.sumOf { it.minorUnits }, "and so does a period total")
        assertEquals(before.incomeTotal, after.incomeTotal, "no income appears")
        // balances: the source is as it was (the money left it either way), the destination gained it: the 'transfer' result
        assertEquals(before.balances.getValue(AccountId("main")), after.balances.getValue(AccountId("main")))
        assertEquals(before.balances.getValue(AccountId("save")) + m, after.balances.getValue(AccountId("save")))
        assertEquals(netWorth(before) + amount, netWorth(after), "the missing credit is now there: nothing else moved")
        assertEquals(setOf("coffee", "salary", "t9"), after.rows.map { it.id }.toSet())

        transfers.undoReplace(r)
        val undone = books()
        assertEquals(before.balances, undone.balances); assertEquals(before.spendAll, undone.spendAll); assertEquals(before.spendWindow, undone.spendWindow); assertEquals(before.incomeTotal, undone.incomeTotal)
        assertEquals(before.rows, undone.rows, "every live entry is the same row, field for field, with the same ids (merchant, note, category, source, reference)")
        assertEquals(before.splits, undone.splits, "category"); assertEquals(before.tags, undone.tags, "tags"); assertEquals(listOf("80C", "Trip"), undone.tags.getValue("plain-out"))
        assertEquals(before.origins, undone.origins, "origin"); assertEquals("for the trip", q.entryById("plain-out").executeAsOne().note)
    }

    @Test
    fun convertingAnAcceptedIncomeToATransferLeavesTheDestinationAndTakesTheMoneyFromTheSourceAndUndoIsExact() {
        otherBooks()
        item("in", "CREDIT", "5678", 1_000, inText)
        val m = Money(amount, Currency.INR)
        d.ledger.save(Income(EntryId("plain-in"), 1_000, AccountId("save"), m, CategoryId("income:interest")), EntryDetails(merchant = "HDFC Bank", note = "interest-ish", source = "sms", sourceRef = "in", tags = listOf("Q3")))
        origins.put("plain-in", OriginKind.SMS, "AX-HDFCBK-S", inText, 1_000)
        inbox.confirm("in", "plain-in")
        item("out", "DEBIT", "1234", 40_000, outText)
        val before = books()

        val r = transfers.replaceEarlier(transfer("t10", 1_000), null, "out-ref", "out", "plain-in")
        val after = books()
        assertEquals(spendTotal(before), spendTotal(after), "spending is unchanged: the debit was never recorded as an expense")
        assertEquals(before.incomeTotal - amount, after.incomeTotal, "the income that was not income goes")
        assertEquals(before.balances.getValue(AccountId("save")), after.balances.getValue(AccountId("save")))
        assertEquals(before.balances.getValue(AccountId("main")) - m, after.balances.getValue(AccountId("main")))
        assertEquals(netWorth(before) - amount, netWorth(after), "the debit now leaves the source: nothing else moved")

        transfers.undoReplace(r)
        val undone = books()
        assertEquals(before, undone, "balances, totals, rows, categories, tags and origins are exactly as they were")
    }

    @Test
    fun afterUndoTheRestoredTextCanBeAddedNormallyAndMadeATransferAgain() {
        item("out", "DEBIT", "1234", 1_000, outText); addedPlain("out", true, "main", 1_000)
        item("in", "CREDIT", "5678", 60_000, inText)
        val r = transfers.replaceEarlier(transfer("t11", 1_000), null, "UPI-REF-1", "in", "plain-out")
        transfers.undoReplace(r)
        // what the inbox's Add does for this item: it saves an income under the item's reference (this used to be refused: the deleted transfer still held it)
        assertNull(q.entryBySourceRef("sms", "UPI-REF-1").executeAsOneOrNull())
        d.ledger.save(Income(EntryId("plain-in"), 60_000, AccountId("save"), Money(amount, Currency.INR), CategoryId("income:otherincome")), EntryDetails(source = "sms", sourceRef = "UPI-REF-1"))
        assertEquals("plain-in", q.entryBySourceRef("sms", "UPI-REF-1").executeAsOne().id)
        // and the same text can be made a transfer again (after taking that income back out of the way)
        d.ledger.delete(EntryId("plain-in")); q.clearSourceRef("plain-in")
        assertEquals(listOf("EXPENSE"), live().map { it.kind })
        val again = transfers.replaceEarlier(transfer("t12", 1_000), null, "UPI-REF-1", "in", "plain-out")
        assertEquals(listOf("TRANSFER"), live().map { it.kind }); assertEquals("UPI-REF-1", q.entryById(again.transferId).executeAsOne().source_ref)
    }

    @Test
    fun bothTextsOfOneUpiOrImpsTransferCarryTheSameReferenceAndTheConversionStillWorks() {
        // the first text was added as an expense under its bank reference; the second text, for the same transfer, carries that very reference
        item("out", "DEBIT", "1234", 1_000, outText)
        val m = Money(amount, Currency.INR)
        d.ledger.save(Expense(EntryId("plain-out"), 1_000, AccountId("main"), m, listOf(CategorySplit(CategoryId("Other"), m))), EntryDetails(merchant = "HDFC Bank", source = "sms", sourceRef = "628374659283"))
        origins.put("plain-out", OriginKind.SMS, "AX-HDFCBK-S", outText, 1_000); inbox.confirm("out", "plain-out")
        item("in", "CREDIT", "5678", 60_000, inText)
        val r = transfers.replaceEarlier(transfer("t13", 1_000), null, "628374659283", "in", "plain-out")
        assertEquals(listOf("TRANSFER"), live().map { it.kind })
        assertEquals("628374659283/transfer", q.entryById("t13").executeAsOne().source_ref); assertEquals("628374659283", q.entryById("plain-out").executeAsOne().source_ref)
        // another entry holding the reference is still a refusal
        transfers.undoReplace(r)
        d.ledger.save(Expense(EntryId("other"), 5, AccountId("save"), Money(5, Currency.INR), listOf(CategorySplit(CategoryId("Other"), Money(5, Currency.INR)))), EntryDetails(source = "sms", sourceRef = "taken-ref"))
        assertFailsWith<IllegalArgumentException> { transfers.replaceEarlier(transfer("t14", 1_000), null, "taken-ref", "in", "plain-out") }
        // two waiting texts with one shared reference become one transfer under it
        item("o2", "DEBIT", "1234", 900_000, outText); item("i2", "CREDIT", "5678", 930_000, inText)
        transfers.addPair(transfer("t15", 900_000), null, "SAME-IMPS-REF", "o2", "i2")
        assertEquals("SAME-IMPS-REF", q.entryById("t15").executeAsOne().source_ref)
    }

    @Test
    fun theNoteAndTagsOfTheEarlierEntryCarryOverAndEntriesWithAttachmentsOrSeveralCategoriesAreNotOffered() {
        val m = Money(amount, Currency.INR)
        item("out", "DEBIT", "1234", 1_000, outText)
        d.ledger.save(Expense(EntryId("plain-out"), 1_000, AccountId("main"), m, listOf(CategorySplit(CategoryId("Food"), m))), EntryDetails(merchant = "HDFC Bank", note = "for the trip", source = "sms", sourceRef = "o-ref", tags = listOf("Trip", "80C")))
        inbox.confirm("out", "plain-out")
        item("in", "CREDIT", "5678", 60_000, inText)
        transfers.replaceEarlier(transfer("t16", 1_000), "Trip", "i-ref", "in", "plain-out")
        assertEquals("for the trip", q.entryById("t16").executeAsOne().note)
        assertEquals(listOf("80C", "Trip"), d.ledger.tagsOf(EntryId("t16")).sorted(), "the note and the tags are on the transfer, not hidden on the deleted entry")

        // not offered: an attachment, or two categories
        val half = Money(amount / 2, Currency.INR); val rest = Money(amount - amount / 2, Currency.INR)
        d.ledger.save(Expense(EntryId("split"), 2_000, AccountId("main"), m, listOf(CategorySplit(CategoryId("Food"), half), CategorySplit(CategoryId("Other"), rest))), EntryDetails(source = "sms", sourceRef = "s-ref"))
        d.ledger.save(Expense(EntryId("pic"), 2_100, AccountId("main"), m, listOf(CategorySplit(CategoryId("Food"), m))), EntryDetails(source = "sms", sourceRef = "p-ref"))
        q.insertAttachment("att1", "pic", "receipt.jpg", "image/jpeg", 10, "00", 1L)
        d.ledger.save(Expense(EntryId("plainer"), 2_200, AccountId("main"), m, listOf(CategorySplit(CategoryId("Food"), m))), EntryDetails(source = "sms", sourceRef = "q-ref"))
        assertEquals(setOf("plainer"), transfers.earlier(amount, "INR", 2_100, 24 * 3_600_000L).map { it.entryId }.toSet())
    }

    @Test
    fun anExpenseWithAnExpectationOpenOrClosedOrWithAnAttachmentIsNotOffered() {
        val m = Money(amount, Currency.INR)
        fun expense(id: String, at: Long) = d.ledger.save(Expense(EntryId(id), at, AccountId("main"), m, listOf(CategorySplit(CategoryId("Food"), m))), EntryDetails(source = "sms", sourceRef = "ref-$id"))
        val expects = ExpectStore(d.db)
        expense("open", 1_000); expects.set("open", "Ravi", null, amount)
        expense("closed", 1_100); expects.set("closed", "Ravi", null, amount); expects.close("closed")
        expense("pic", 1_200); q.insertAttachment("att2", "pic", "bill.jpg", "image/jpeg", 10, "00", 1L)
        expense("plain", 1_300)
        assertEquals(setOf("plain"), transfers.earlier(amount, "INR", 1_200, 24 * 3_600_000L).map { it.entryId }.toSet(), "an open or closed expectation, or an attachment, is never offered")
    }

    @Test
    fun theWaitingListHidesAnExpectationWhoseEntryIsDeletedWhateverDeletedItAndShowsItAgainOnRestore() {
        val expects = ExpectStore(d.db)
        val m = Money(amount, Currency.INR)
        d.ledger.save(Expense(EntryId("e1"), 1_000, AccountId("main"), m, listOf(CategorySplit(CategoryId("Food"), m))), EntryDetails(merchant = "Dinner", source = "manual"))
        expects.set("e1", "Ravi", null, amount)
        assertEquals(listOf("e1"), expects.waiting().map { it.entryId })
        d.ledger.delete(EntryId("e1"))   // a plain delete, a conversion, an import's replacement: the same soft delete
        assertTrue(expects.waiting().isEmpty(), "no orphan of a deleted expense")
        assertTrue(expects.all().isEmpty(), "and a backup does not carry it either")
        d.ledger.restore(EntryId("e1"))
        assertEquals(listOf("e1"), expects.waiting().map { it.entryId })
    }
}
