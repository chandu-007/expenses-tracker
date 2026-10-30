package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RevisionKind
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.Entry
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Change the type" of an entry: invented entries and amounts only. */
class KindChangesTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) }
    private val db = ExpensesDb(driver)
    private val clock = HlcClock("a")
    private var now = 5_000L
    private val ledger = LedgerStore(db, clock) { now }
    private val origins = OriginStore(db)
    private val expects = ExpectStore(db)
    private val attachments = AttachmentStore(db, clock) { now }
    private val changes = KindChanges(db, ledger, origins, expects, attachments)
    private val q get() = db.schemaQueries
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val amount = 2_000_00L

    init {
        ledger.saveAccount(Account(AccountId("main"), "Main", AccountType.BANK, inr))
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        ledger.saveAccount(Account(AccountId("save"), "Savings", AccountType.BANK, inr))
    }

    private fun expense(id: String, at: Long = 1_000, account: String = "main", amt: Long = amount, category: String = "Other") =
        Expense(EntryId(id), at, AccountId(account), m(amt), listOf(CategorySplit(CategoryId(category), m(amt))))

    private fun income(id: String, at: Long = 1_000, account: String = "main", amt: Long = amount) = Income(EntryId(id), at, AccountId(account), m(amt), CategoryId("income:salary"))
    private fun transfer(id: String, from: String, to: String, at: Long = 1_000, amt: Long = amount) = Transfer(EntryId(id), at, AccountId(from), AccountId(to), m(amt))

    /** An expense read from a text, as the inbox adds it, with everything a change has to carry. */
    private fun capturedExpense(id: String = "atm", ref: String? = "UPI-1") {
        ledger.save(expense(id, category = "Cash"), EntryDetails(merchant = "ATM Koramangala", note = "for the trip", source = "sms", sourceRef = ref, tags = listOf("Trip", "80C")))
        origins.put(id, OriginKind.SMS, "AX-BANK", "Rs.2,000.00 debited at ATM", 900)
    }

    private fun balance(id: String) = ledger.balances().getValue(AccountId(id)).minorUnits
    private fun live() = q.liveEntriesInInsertionOrder().executeAsList()

    private data class Books(
        val balances: Map<AccountId, Money>, val spendAll: Map<CategoryId?, Money>, val spendWindow: Map<CategoryId?, Money>, val income: Long,
        val rows: List<app.expenses.core.storage.db.Entry>, val splits: Map<String, List<Pair<String, Long>>>, val tags: Map<String, List<String>>,
        val origins: Map<String, Origin?>, val attachments: Map<String, List<String>>,
    )

    private fun books(): Books {
        val rows = live()
        return Books(
            ledger.balances(), ledger.spendByCategory(inr), ledger.spendByCategory(inr, from = 0L, until = 10_000L), rows.filter { it.kind == "INCOME" }.sumOf { it.amount_minor }, rows,
            rows.associate { it.id to q.splitsOf(it.id).executeAsList().map { s -> s.category_id to s.amount_minor } }, rows.associate { it.id to ledger.tagsOf(EntryId(it.id)).sorted() },
            rows.associate { it.id to origins.get(it.id) }, rows.associate { it.id to attachments.list(it.id).map { a -> a.id } },
        )
    }

    private fun spend(b: Books) = b.spendAll.values.sumOf { it.minorUnits }

    private fun otherBooks() {
        ledger.save(expense("coffee", at = 500, account = "save", amt = 300_00, category = "Food"), EntryDetails(merchant = "Cafe"))
        ledger.save(income("salary", at = 600, amt = 5_000_00), EntryDetails(merchant = "Acme"))
    }

    // ---- the swap ----

    @Test
    fun anExpenseBecomesATransferCarryingEverythingAndUndoPutsItAllBackExactly() {
        otherBooks(); capturedExpense()
        attachments.add("photo", "atm", "slip.jpg", "image/jpeg", 100, "h")
        val before = books()

        val c = changes.change("atm", transfer("t1", "main", "cash"), "ATM Koramangala", listOf("Trip", "80C"))
        val after = books()
        assertEquals(KindChanges.Changed("t1", "atm", "UPI-1"), c)
        assertEquals(setOf("coffee", "salary", "t1"), after.rows.map { it.id }.toSet(), "one transfer, the expense is gone")
        assertEquals(spend(before) - amount, spend(after), "spending drops by exactly the amount"); assertEquals(300_00L, spend(after))
        assertEquals(before.spendWindow.values.sumOf { it.minorUnits } - amount, after.spendWindow.values.sumOf { it.minorUnits }, "and so does a period total")
        assertEquals(before.income, after.income, "no income appears")
        assertEquals(before.balances.getValue(AccountId("main")), after.balances.getValue(AccountId("main")), "the money still left the bank account")
        assertEquals(before.balances.getValue(AccountId("cash")) + m(amount), after.balances.getValue(AccountId("cash")), "and it is in the cash account now")
        val t = q.entryById("t1").executeAsOne()
        assertEquals("TRANSFER", t.kind); assertEquals("main", t.account_id); assertEquals("cash", t.counter_account_id); assertEquals(amount, t.amount_minor); assertEquals(1_000L, t.occurred_at)
        assertEquals("ATM Koramangala", t.merchant); assertEquals("for the trip", t.note); assertEquals("sms", t.source); assertEquals("UPI-1", t.source_ref)
        assertEquals(listOf("80C", "Trip"), after.tags.getValue("t1")); assertEquals(origins.get("atm")!!.copy(entryId = "t1"), after.origins.getValue("t1"), "Where this came from still works")
        assertEquals(listOf("photo"), after.attachments.getValue("t1"), "the photo moved with it"); assertTrue(attachments.list("atm").isEmpty())
        val old = q.entryById("atm").executeAsOne()
        assertEquals(1L, old.deleted); assertNull(old.source_ref, "the reference passed to the new entry"); assertEquals("t1", q.entryBySourceRef("sms", "UPI-1").executeAsOne().id)

        changes.undo(c)
        val undone = books()
        assertEquals(before, undone, "balances, totals, rows (every field), splits, tags, origins and attachments are exactly as they were")
        assertEquals("UPI-1", q.entryById("atm").executeAsOne().source_ref)
        val gone = q.entryById("t1").executeAsOne()
        assertEquals(1L, gone.deleted); assertNull(gone.source_ref); assertNull(origins.get("t1")); assertTrue(attachments.list("t1").isEmpty())
    }

    @Test
    fun everyPairOfKindsMovesTheMoneyRightAndUndoIsExact() {
        otherBooks()
        ledger.save(expense("e", at = 1_000), EntryDetails(merchant = "Shop", note = "n"))
        ledger.save(income("i", at = 1_100), EntryDetails(merchant = "Pay"))
        ledger.save(transfer("t", "main", "save", at = 1_200), EntryDetails(merchant = "Move"))
        val x = amount
        // (old, new entry, balance change of main, cash, save; spending change; income change)
        class Case(val old: String, val entry: Entry, val main: Long, val cash: Long, val save: Long, val spend: Long, val income: Long)
        val cases = listOf(
            Case("e", income("n1", at = 1_000), main = 2 * x, cash = 0, save = 0, spend = -x, income = x),
            Case("e", transfer("n2", "main", "cash"), main = 0, cash = x, save = 0, spend = -x, income = 0),
            Case("i", expense("n3", at = 1_100), main = -2 * x, cash = 0, save = 0, spend = x, income = -x),
            Case("i", transfer("n4", "save", "main", at = 1_100), main = 0, cash = 0, save = -x, spend = 0, income = -x),
            Case("t", expense("n5", at = 1_200, account = "main"), main = 0, cash = 0, save = -x, spend = x, income = 0),
            Case("t", income("n6", at = 1_200, account = "save"), main = x, cash = 0, save = 0, spend = 0, income = x),
        )
        val start = books()
        for (c in cases) {
            val before = books()
            val done = changes.change(c.old, c.entry, "Moved", emptyList())
            val after = books()
            assertEquals(c.main, after.balances.getValue(AccountId("main")).minorUnits - before.balances.getValue(AccountId("main")).minorUnits, "main for ${c.old} -> ${c.entry.id.value}")
            assertEquals(c.cash, after.balances.getValue(AccountId("cash")).minorUnits - before.balances.getValue(AccountId("cash")).minorUnits, "cash for ${c.old} -> ${c.entry.id.value}")
            assertEquals(c.save, after.balances.getValue(AccountId("save")).minorUnits - before.balances.getValue(AccountId("save")).minorUnits, "save for ${c.old} -> ${c.entry.id.value}")
            assertEquals(c.spend, spend(after) - spend(before), "spending for ${c.old} -> ${c.entry.id.value}"); assertEquals(c.income, after.income - before.income, "income for ${c.old} -> ${c.entry.id.value}")
            assertEquals(before.rows.size, after.rows.size, "one entry replaced one entry")
            changes.undo(done)
            assertEquals(before, books(), "undo of ${c.old} -> ${c.entry.id.value}")
        }
        assertEquals(start, books())
        // and a change that is kept can itself be changed again (a transfer made an expense made an income)
        val a = changes.change("e", transfer("k1", "main", "cash"), "x", emptyList())
        val b = changes.change("k1", income("k2"), "x", emptyList())
        assertEquals(setOf("coffee", "salary", "i", "t", "k2"), live().map { it.id }.toSet())
        changes.undo(b); assertEquals(setOf("coffee", "salary", "i", "t", "k1"), live().map { it.id }.toSet())
        changes.undo(a); assertEquals(start, books())
    }

    @Test
    fun theOriginalHistoryIsIntactAndTheUndoneEntryIsDeletedNotEdited() {
        capturedExpense(); now += 1_000
        ledger.save(expense("atm", amt = amount + 5), EntryDetails(merchant = "ATM Koramangala", note = "for the trip", source = "sms", sourceRef = "UPI-1", tags = listOf("Trip", "80C"))) // an earlier edit
        now += 1_000
        val c = changes.change("atm", transfer("t1", "main", "cash", amt = amount + 5), "x", emptyList())
        assertEquals(listOf(RevisionKind.CREATED, RevisionKind.EDITED, RevisionKind.DELETED), ledger.history(EntryId("atm")).map { it.kind })
        assertEquals(listOf(RevisionKind.CREATED), ledger.history(EntryId("t1")).map { it.kind }, "the new entry's history starts at creation")
        now += 1_000; changes.undo(c)
        assertEquals(listOf(RevisionKind.CREATED, RevisionKind.EDITED, RevisionKind.DELETED, RevisionKind.RESTORED), ledger.history(EntryId("atm")).map { it.kind })
        assertEquals(listOf(RevisionKind.CREATED, RevisionKind.DELETED), ledger.history(EntryId("t1")).map { it.kind })
    }

    // ---- bank references ----

    @Test
    fun aTextsReferenceFollowsTheEntryAndNothingBlocksChangingAgainAfterUndoOrEditingNormally() {
        capturedExpense(ref = "UPI-9")
        val one = changes.change("atm", transfer("t1", "main", "cash"), "x", emptyList())
        assertEquals("t1", q.entryBySourceRef("sms", "UPI-9").executeAsOne().id, "a text with this reference is seen as already added, now as the transfer")
        changes.undo(one)
        assertEquals("atm", q.entryBySourceRef("sms", "UPI-9").executeAsOne().id)
        // edited normally afterwards (the ordinary edit keeps the reference)
        ledger.save(expense("atm", amt = amount + 100, category = "Cash"), EntryDetails(merchant = "ATM", note = "for the trip", source = "sms", sourceRef = "UPI-9"))
        val two = changes.change("atm", transfer("t2", "main", "cash", amt = amount + 100), "ATM", emptyList())
        assertEquals("t2", q.entryBySourceRef("sms", "UPI-9").executeAsOne().id)
        changes.undo(two)
        val three = changes.change("atm", income("t3", amt = amount + 100), "ATM", emptyList())
        assertEquals("t3", q.entryBySourceRef("sms", "UPI-9").executeAsOne().id)
        // a change that is kept, changed again, then both undone: the reference ends where it began
        val four = changes.change("t3", transfer("t4", "main", "cash", amt = amount + 100), "ATM", emptyList())
        assertEquals("t4", q.entryBySourceRef("sms", "UPI-9").executeAsOne().id)
        changes.undo(four); assertEquals("t3", q.entryBySourceRef("sms", "UPI-9").executeAsOne().id)
        changes.undo(three); assertEquals("atm", q.entryBySourceRef("sms", "UPI-9").executeAsOne().id)
        assertEquals(listOf("atm"), live().map { it.id })
    }

    @Test
    fun anEntryWithNoReferenceOrOriginChangesAndUndoesToo() {
        ledger.save(expense("hand"), EntryDetails(merchant = "Auto"))
        val c = changes.change("hand", transfer("t1", "main", "cash"), "Auto", emptyList())
        assertNull(q.entryById("t1").executeAsOne().source_ref); assertEquals("manual", q.entryById("t1").executeAsOne().source); assertNull(origins.get("t1"))
        changes.undo(c)
        assertEquals(listOf("hand"), live().map { it.id }); assertNull(q.entryById("hand").executeAsOne().source_ref)
    }

    // ---- attachments ----

    @Test
    fun attachmentsMoveToTheNewEntryAndBackAndTheMoveIsLogged() {
        ledger.save(expense("e"), EntryDetails(merchant = "Shop"))
        attachments.add("a1", "e", "one.jpg", "image/jpeg", 10, "h1"); attachments.add("a2", "e", "two.pdf", "application/pdf", 20, "h2")
        val c = changes.change("e", transfer("t1", "main", "cash"), "Shop", emptyList())
        assertEquals(setOf("a1", "a2"), attachments.list("t1").map { it.id }.toSet()); assertTrue(attachments.list("e").isEmpty())
        assertEquals("t1", q.opsOfRow("attachment", "a1").executeAsList().last { it.field_ == "entry_id" }.value_)
        // a file added to the new entry before Undo goes back with the others: it follows the entry the person has
        attachments.add("a3", "t1", "three.jpg", "image/jpeg", 30, "h3")
        changes.undo(c)
        assertEquals(setOf("a1", "a2", "a3"), attachments.list("e").map { it.id }.toSet()); assertTrue(attachments.list("t1").isEmpty())
        assertEquals("e", q.opsOfRow("attachment", "a1").executeAsList().last { it.field_ == "entry_id" }.value_)
        assertEquals(3, attachments.allIds().size, "no file lost")
    }

    @Test
    fun attachmentsOfOtherEntriesAreLeftAlone() {
        ledger.save(expense("e"), EntryDetails(merchant = "Shop")); ledger.save(expense("other", at = 2_000), EntryDetails(merchant = "Other"))
        attachments.add("mine", "e", "a.jpg", "image/jpeg", 10, "h1"); attachments.add("theirs", "other", "b.jpg", "image/jpeg", 10, "h2")
        changes.undo(changes.change("e", income("n1"), "Shop", emptyList()))
        assertEquals(listOf("theirs"), attachments.list("other").map { it.id }); assertEquals(listOf("mine"), attachments.list("e").map { it.id })
    }

    // ---- refusals and rules: nothing changes ----

    private fun assertRefused(old: String, entry: Entry, message: String? = null) {
        val before = books(); val refs = live().associate { it.id to it.source_ref }
        val e = assertFailsWith<IllegalArgumentException> { changes.change(old, entry, "x", emptyList()) }
        if (message != null) assertEquals(message, e.message)
        assertEquals(before, books(), "nothing changed"); assertEquals(refs, live().associate { it.id to it.source_ref }); assertNull(q.entryById(entry.id.value).executeAsOneOrNull(), "no new entry was left behind")
    }

    @Test
    fun anExpenseExpectedBackOpenOrClosedIsRefused() {
        capturedExpense()
        expects.set("atm", "Raj", null, amount)
        assertRefused("atm", transfer("t1", "main", "cash"), KindChanges.EXPECTED_BACK)
        assertEquals("Turn off \"I expect this back\" first: save the expense with it off, then change its type.", KindChanges.EXPECTED_BACK)
        expects.close("atm")
        assertRefused("atm", income("t2"), KindChanges.EXPECTED_BACK)
        assertEquals(Expectation("atm", "Raj", null, amount, true), expects.get("atm"), "the expectation is untouched")
        expects.clear("atm") // what saving with it off does
        changes.change("atm", transfer("t3", "main", "cash"), "x", emptyList())
    }

    @Test
    fun anEntryTiedToABillARepeatingItemARefundOrAShareIsRefused() {
        ledger.save(expense("paid"), EntryDetails(merchant = "Power")); ledger.save(expense("posted", at = 2_000), EntryDetails(merchant = "Rent")); ledger.save(expense("refunded", at = 3_000), EntryDetails(merchant = "Shoes"))
        ledger.save(expense("shared", at = 4_000), EntryDetails(merchant = "Dinner")); ledger.save(expense("foreign", at = 5_000), EntryDetails(merchant = "Hotel"))
        driver.execute(null, "INSERT INTO bill(id, source, sender, issuer, kind, title, amount_minor, currency, received_at, status, entry_id, dedupe_key) VALUES ('b1', 'SMS', 'S', 'I', 'ELECTRICITY', 'Power', 1, 'INR', 1, 'PAID', 'paid', 'k1')", 0)
        driver.execute(null, "INSERT INTO recurring_done(recurring_id, day, outcome, entry_id) VALUES ('r1', 1, 'POSTED', 'posted')", 0)
        ledger.save(Refund(EntryId("rf"), 3_500, AccountId("main"), m(100), EntryId("refunded")))
        ledger.save(Expense(EntryId("shared"), 4_000, AccountId("main"), m(amount), listOf(CategorySplit(CategoryId("Other"), m(amount))), listOf(Share(null, m(amount / 2)), Share(PersonId("p"), m(amount / 2)))), EntryDetails(merchant = "Dinner"))
        ledger.save(Expense(EntryId("foreign"), 5_000, AccountId("main"), m(amount), listOf(CategorySplit(CategoryId("Other"), m(amount))), original = Money(2_000, Currency.USD)), EntryDetails(merchant = "Hotel"))
        assertRefused("paid", transfer("n1", "main", "cash"), "This entry is marked as the payment of a bill, so its type can't be changed here.")
        assertRefused("posted", transfer("n2", "main", "cash"), "A repeating item added this entry, so its type can't be changed here.")
        assertRefused("refunded", income("n3"), "This expense has refunds, so its type can't be changed here.")
        assertRefused("shared", income("n4"), "This entry is shared or in another currency, so its type can't be changed here.")
        assertRefused("foreign", income("n5"), "This entry is shared or in another currency, so its type can't be changed here.")
    }

    @Test
    fun theRefusalIsAskedForWithoutWritingAnything() {
        capturedExpense(); ledger.save(income("i"), EntryDetails(merchant = "Pay"))
        val before = books()
        assertNull(changes.refusal("atm")); assertNull(changes.refusal("i"))
        expects.set("atm", null, null, amount)
        assertEquals(KindChanges.EXPECTED_BACK, changes.refusal("atm")); assertEquals("This entry no longer exists.", changes.refusal("nope"))
        expects.clear("atm")
        assertEquals(before, books())
    }

    @Test
    fun aRefundOrAMissingOrDeletedEntryOrTheSameKindIsRefused() {
        ledger.save(expense("e"), EntryDetails(merchant = "Shop")); ledger.save(Refund(EntryId("rf"), 2_000, AccountId("main"), m(100), EntryId("e")))
        assertRefused("rf", income("n1")) // a refund is not one of the three
        ledger.delete(EntryId("rf"))
        assertRefused("e", expense("n2"), "Choose another type.")
        assertRefused("nope", income("n3"), "This entry no longer exists.")
        ledger.delete(EntryId("e")); assertRefused("e", income("n4"), "This entry no longer exists.")
    }

    @Test
    fun anEntryThatBreaksALedgerRuleChangesNothingAtAll() {
        otherBooks(); capturedExpense()
        attachments.add("photo", "atm", "slip.jpg", "image/jpeg", 100, "h")
        val before = books()
        // the destination account does not exist: the new entry is refused after the old one was already deleted inside the step
        assertFailsWith<IllegalArgumentException> { changes.change("atm", transfer("t1", "main", "ghost"), "x", emptyList()) }
        assertEquals(before, books()); assertEquals("UPI-1", q.entryById("atm").executeAsOne().source_ref); assertEquals(0L, q.entryById("atm").executeAsOne().deleted)
        assertNull(q.entryById("t1").executeAsOneOrNull()); assertEquals(listOf("photo"), attachments.list("atm").map { it.id }); assertNull(origins.get("t1"))
        assertEquals(listOf(RevisionKind.CREATED), ledger.history(EntryId("atm")).map { it.kind }, "not even a delete was logged")
        // and the step works afterwards
        changes.change("atm", transfer("t2", "main", "cash"), "x", emptyList())
    }

    // ---- split categories ----

    @Test
    fun aSplitExpenseBecomingATransferLosesItsCategoriesAndUndoBringsThemBack() {
        ledger.save(
            Expense(EntryId("sp"), 1_000, AccountId("main"), m(amount), listOf(CategorySplit(CategoryId("Food"), m(amount - 500_00)), CategorySplit(CategoryId("Fun"), m(500_00)))),
            EntryDetails(merchant = "Mall"),
        )
        val before = books()
        assertEquals(mapOf<CategoryId?, Money>(CategoryId("Food") to m(amount - 500_00), CategoryId("Fun") to m(500_00)), before.spendAll)
        val c = changes.change("sp", transfer("t1", "main", "cash"), "Mall", emptyList())
        assertTrue(books().spendAll.isEmpty(), "no category spending is left"); assertTrue(q.splitsOf("t1").executeAsList().isEmpty())
        changes.undo(c)
        assertEquals(before, books()); assertEquals(listOf("Food" to amount - 500_00, "Fun" to 500_00L), q.splitsOf("sp").executeAsList().map { it.category_id to it.amount_minor })
    }

    @Test
    fun anIncomeBecomingAnExpenseGetsItsCategorySplitAndTheNewEntryIsASimpleOne() {
        ledger.save(income("i"), EntryDetails(merchant = "Pay"))
        val c = changes.change("i", expense("n1", category = "Food"), "Pay", emptyList())
        assertEquals(listOf("Food" to amount), q.splitsOf("n1").executeAsList().map { it.category_id to it.amount_minor }); assertEquals(mapOf<CategoryId?, Money>(CategoryId("Food") to m(amount)), ledger.spendByCategory(inr))
        changes.undo(c); assertTrue(ledger.spendByCategory(inr).isEmpty()); assertEquals("income:salary", q.entryById("i").executeAsOne().category_id)
    }

    // ---- the tags and the title are the form's ----

    @Test
    fun theTagsAndTitleOnTheFormAreWhatTheNewEntryGetsAndTheNoteStays() {
        capturedExpense()
        val c = changes.change("atm", transfer("t1", "main", "cash"), "  ", listOf("Cash"))
        val t = q.entryById("t1").executeAsOne()
        assertNull(t.merchant, "a blank title is no title"); assertEquals("for the trip", t.note); assertEquals(listOf("Cash"), ledger.tagsOf(EntryId("t1")))
        changes.undo(c)
        assertEquals(listOf("80C", "Trip"), ledger.tagsOf(EntryId("atm")).sorted(), "the original's tags are as they were"); assertEquals("ATM Koramangala", q.entryById("atm").executeAsOne().merchant)
    }
}
