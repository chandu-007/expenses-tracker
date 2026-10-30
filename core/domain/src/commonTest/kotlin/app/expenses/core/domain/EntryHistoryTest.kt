package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class EntryHistoryTest {
    private fun op(at: Long, field: String, value: String?) = LoggedChange(at, field, value)
    private val fmt = HistoryFormat(Currency.INR, { "acct:$it" }, { "cat:$it" }, { "d$it" })

    @Test
    fun aCreationThenAnEditThenADeleteAndARestoreAreFourRevisionsEachWithItsOwnChanges() {
        val ops = listOf(
            op(100, "kind", "EXPENSE"), op(100, "amount_minor", "10000"), op(100, "merchant", "Cafe"),
            op(200, "amount_minor", "15000"), op(200, "merchant", "Cafe 2"),
            op(300, "deleted", "1"), op(400, "deleted", "0"),
        )
        val r = EntryHistory.revisions(ops)
        assertEquals(listOf(RevisionKind.CREATED, RevisionKind.EDITED, RevisionKind.DELETED, RevisionKind.RESTORED), r.map { it.kind })
        assertEquals(listOf(100L, 200L, 300L, 400L), r.map { it.atMillis })
        assertEquals(listOf(FieldChange("amount_minor", "10000", "15000"), FieldChange("merchant", "Cafe", "Cafe 2")), r[1].changes)
        assertEquals(emptyMap(), r[0].before)
        assertEquals("10000", r[1].before["amount_minor"]); assertEquals("15000", r[1].after["amount_minor"])
        assertEquals("15000", r[3].after["amount_minor"], "a delete and restore leave the values alone")
    }

    @Test
    fun theBankReferenceMovingWithATypeChangeIsNotARevisionAndDoesNotTurnADeleteOrARestoreIntoAnEdit() {
        val ops = listOf(
            op(100, "kind", "EXPENSE"), op(100, "amount_minor", "10000"), op(100, "source_ref", "UPI-1"),
            op(300, "source_ref", null), op(300, "deleted", "1"), // the reference passes to the new entry in the same step as the delete
            op(500, "source_ref", "UPI-1"), op(500, "deleted", "0"), // and comes back with the Undo
            op(600, "source_ref", null), // alone (a clock tick between the two writes): nothing to show
        )
        val r = EntryHistory.revisions(ops)
        assertEquals(listOf(RevisionKind.CREATED, RevisionKind.DELETED, RevisionKind.RESTORED), r.map { it.kind })
        assertEquals(emptyList(), fmt.lines(r[1].changes), "no line about the reference")
        assertEquals(emptyList(), fmt.lines(r[2].changes))
    }

    @Test
    fun anOrderOfOpsThatArrivesOutOfOrderIsReplayedByTime() {
        val r = EntryHistory.revisions(listOf(op(200, "amount_minor", "2"), op(100, "amount_minor", "1"), op(100, "kind", "EXPENSE")))
        assertEquals(listOf(100L, 200L), r.map { it.atMillis })
        assertEquals(FieldChange("amount_minor", "1", "2"), r[1].changes.single())
    }

    @Test
    fun aSaveThatChangedNothingIsNotARevision() {
        val r = EntryHistory.revisions(listOf(op(100, "kind", "EXPENSE"), op(100, "amount_minor", "5"), op(200, "amount_minor", "5")))
        assertEquals(1, r.size)
    }

    @Test
    fun linesAreWordedForAPersonAndHideTechnicalFields() {
        val lines = fmt.lines(listOf(
            FieldChange("amount_minor", "10000", "15050"), FieldChange("occurred_at", "5", "6"), FieldChange("account_id", "bank", "cash"),
            FieldChange("category_id", null, "Food"), FieldChange("merchant", "Cafe", null), FieldChange("source", "manual", "csv"),
            FieldChange("kind", null, "EXPENSE"), FieldChange("source_ref", null, "x"), FieldChange("currency", null, "INR"),
            FieldChange("splits", "Food:60000", "Food:50000,Home%3A2:10000"), FieldChange("tags", null, "goa,tax"),
        ))
        assertEquals(listOf(
            HistoryLine("Amount", "₹100.00", "₹150.50"), HistoryLine("Date", "d5", "d6"), HistoryLine("Account", "acct:bank", "acct:cash"),
            HistoryLine("Category", null, "cat:Food"), HistoryLine("Note or merchant", "Cafe", null),
            HistoryLine("Categories", "cat:Food ₹600.00", "cat:Food ₹500.00, cat:Home:2 ₹100.00"), HistoryLine("Tags", null, "goa, tax"),
        ), lines)
    }
}

class HistoryRestoreTest {
    private val inr = Currency.INR
    private val now = EntryDraft("e", DraftKind.EXPENSE, "150.00", "Bills", emptyList(), "bank", "", "Cafe 2", 9L)

    @Test
    fun anOldVersionsTagsComeBackAndAVersionWithoutTagsClearsThem() {
        assertEquals(listOf("goa", "tax"), HistoryRestore.apply(now, mapOf("amount_minor" to "1", "tags" to "goa,tax"), inr).tags)
        assertEquals(emptyList(), HistoryRestore.apply(now.copy(tags = listOf("x")), mapOf("amount_minor" to "1", "tags" to ""), inr).tags)
        assertEquals(emptyList(), HistoryRestore.apply(now.copy(tags = listOf("x")), mapOf("amount_minor" to "1"), inr).tags, "an older version from before tags existed had none")
    }

    @Test
    fun anOlderExpenseVersionComesBackWithItsAmountDateAccountCategoryAndNote() {
        val old = mapOf("amount_minor" to "10000", "occurred_at" to "5", "account_id" to "cash", "merchant" to "Cafe", "splits" to "Food:10000")
        assertEquals(EntryDraft("e", DraftKind.EXPENSE, "100.00", "Food", emptyList(), "cash", "", "Cafe", 5L), HistoryRestore.apply(now, old, inr))
    }

    @Test
    fun aSplitVersionComesBackAsTheSameSplitWithEscapedNamesUnescapedAndAnUncategorisedOneAsNone() {
        val split = HistoryRestore.apply(now, mapOf("amount_minor" to "1000", "splits" to "Food:600,Home%3A2%2C:400"), inr)
        assertEquals(listOf(SplitDraft("Food", "6.00"), SplitDraft("Home:2,", "4.00")), split.splits)
        assertEquals("", HistoryRestore.apply(now, mapOf("amount_minor" to "1000", "splits" to ""), inr).category)
    }

    @Test
    fun anOldVersionWithNoNoteClearsTheNoteAndATransferKeepsItsDestinationFromTheVersion() {
        assertEquals("", HistoryRestore.apply(now, mapOf("amount_minor" to "1"), inr).note)
        val t = HistoryRestore.apply(now.copy(kind = DraftKind.TRANSFER, toAccountId = "x"), mapOf("counter_account_id" to "cash", "account_id" to "bank", "amount_minor" to "500"), inr)
        assertEquals("cash", t.toAccountId); assertEquals("5.00", t.amountText)
        val inc = HistoryRestore.apply(now.copy(kind = DraftKind.INCOME), mapOf("category_id" to "Salary", "amount_minor" to "500"), inr)
        assertEquals("Salary", inc.category)
    }
}
