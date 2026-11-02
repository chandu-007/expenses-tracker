package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CaptureRuleStoreTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val categories = CategoryStore(db, clock) { 1L }
    private val ledger = LedgerStore(db, clock) { 1L }
    private val store = CaptureRuleStore(db)

    init { ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR)) }

    @Test
    fun rulesKeepTheOrderTheyWereAddedInAndMoveOneStepAtATimeNeverPastTheEnds() {
        store.add("a", "uber", "Transport"); store.add("b", "swiggy", "Food"); store.add("c", "netflix", "Fun")
        assertEquals(listOf("a", "b", "c"), store.rules().map { it.id })
        store.move("c", up = true); assertEquals(listOf("a", "c", "b"), store.rules().map { it.id })
        store.move("a", up = true); store.move("b", up = false); assertEquals(listOf("a", "c", "b"), store.rules().map { it.id }, "the ends stay")
        store.move("b", up = true); store.move("b", up = true); assertEquals(listOf("b", "a", "c"), store.rules().map { it.id })
        store.remove("a"); assertEquals(listOf("b", "c"), store.rules().map { it.id })
        store.add("d", "zomato", "Food"); assertEquals(listOf("b", "c", "d"), store.rules().map { it.id }, "new rules go last")
    }

    @Test
    fun aRuleWithNothingToLookForADuplicatePatternOrAMissingCategoryIsRefused() {
        store.add("a", "Uber  Eats", "Food")
        assertEquals("Uber Eats", store.rules().single().pattern, "spacing tidied")
        assertTrue(assertFailsWith<IllegalArgumentException> { store.add("b", "uber eats", "Food") }.message!!.contains("already have a rule"))
        assertFailsWith<IllegalArgumentException> { store.add("c", "   ", "Food") }
        assertTrue(assertFailsWith<IllegalArgumentException> { store.add("d", "metro", "Nope") }.message!!.contains("no longer exists"))
        store.add("z", "metro", "Transport")
        store.add("a", "uber eats india", "Transport") // the same rule edited keeps its place
        assertEquals(listOf("uber eats india" to "Transport", "metro" to "Transport"), store.rules().map { it.pattern to it.categoryId })
    }

    @Test
    fun theSameShopWrittenDifferentlyIsOnePayeeToLearnAndToForget() {
        store.learn("SWIGGY BANGALORE KA IN", "Food"); assertEquals(mapOf("swiggy" to "Food"), store.learned())
        store.learn("swiggy@icici", "Food"); store.learn("Swiggy", "Food")
        assertEquals(3, store.learnedUses().getValue("swiggy"), "three spellings, one payee, three times in a row")
        store.learn("FRESHPIK 2231", "Groceries"); store.learn("freshpik@ybl", "Groceries"); assertEquals(2, store.learnedUses().getValue("freshpik"))
        assertEquals("Swiggy", store.learnedRows().first { it.key == "swiggy" }.label, "the list shows how the payee was first written, not the address")
        assertEquals("FRESHPIK 2231", store.learnedRows().first { it.key == "freshpik" }.label)
        store.forget("FRESHPIK BANGALORE IN"); assertEquals(setOf("swiggy"), store.learned().keys, "forgetting by any spelling")
        assertEquals(null, db.schemaQueries.settingValue("learned_label:freshpik").executeAsOneOrNull(), "the label goes with it")
    }

    @Test
    fun aChoiceStoredUnderTheOldKeyIsCarriedOverWhenTheSamePayeeIsLearnedAgain() {
        db.schemaQueries.learnMerchant("swiggybangalorekain", "Food"); db.schemaQueries.learnMerchant("swiggybangalorekain", "Food") // two choices recorded before the key changed
        store.learn("SWIGGY BANGALORE KA IN", "Food")
        assertEquals(mapOf("swiggy" to "Food"), store.learned(), "the old row moves to the new key")
        assertEquals(3, store.learnedUses().getValue("swiggy"), "and keeps its count")
        db.schemaQueries.learnMerchant("freshpikbangalorein", "Groceries")
        store.learn("FRESHPIK BANGALORE IN", "Food")
        assertEquals(mapOf("swiggy" to "Food", "freshpik" to "Food"), store.learned(), "a different choice replaces the old one")
        assertEquals(1, store.learnedUses().getValue("freshpik"))
    }

    @Test
    fun thePersonCanChangeAndForgetWhatWasLearnedByKey() {
        store.learn("Swiggy", "Food")
        store.setLearned("swiggy", "Groceries"); assertEquals(mapOf("swiggy" to "Groceries"), store.learned())
        assertFailsWith<IllegalArgumentException> { store.setLearned("swiggy", "Nope") }
        assertEquals("Swiggy", store.learnedRows().single().label)
        store.forgetKey("swiggy"); assertEquals(emptyMap(), store.learned()); assertEquals(emptyList(), store.learnedRows())
    }

    @Test
    fun aChoiceIsLearnedPerPayeeKeyAndALaterDifferentChoiceReplacesIt() {
        store.learn("Swiggy@icici", "Food"); assertEquals(mapOf("swiggy" to "Food"), store.learned())
        store.learn("SWIGGY", "Groceries"); assertEquals(mapOf("swiggy" to "Groceries"), store.learned(), "the correction sticks")
        store.learn("SWIGGY", "Groceries"); assertEquals(2L, db.schemaQueries.allMerchantMap().executeAsList().single().uses, "agreeing again counts")
        store.learn("SWIGGY", "Food"); assertEquals(1L, db.schemaQueries.allMerchantMap().executeAsList().single().uses, "a change starts the count again")
        store.learn(null, "Food"); store.learn("a", "Food"); store.learn("Zomato", ""); assertEquals(setOf("swiggy"), store.learned().keys)
        store.forget("Swiggy"); assertEquals(emptyMap(), store.learned())
    }

    @Test
    fun testingARuleAgainstHistoryCountsPastExpensesAndIncomesMentioningEveryWord() {
        fun spend(id: String, merchant: String?, note: String? = null, amount: Long = 100) = ledger.save(Expense(EntryId(id), 1, AccountId("bank"), Money(amount, Currency.INR)), EntryDetails(merchant = merchant, note = note))
        spend("1", "Uber India"); spend("2", "UBER Eats"); spend("3", "Swiggy", note = "dinner via uber eats voucher"); spend("4", "Zomato"); spend("5", "100% fresh_mart"); spend("7", "1005 Stores")
        ledger.save(Expense(EntryId("6"), 1, AccountId("bank"), Money(5, Currency.INR)), EntryDetails(merchant = "uber deleted")); ledger.delete(EntryId("6"))
        assertEquals(3, store.matchesInHistory("uber"))
        assertEquals(2, store.matchesInHistory("uber eats"), "every word, in the payee or the note")
        ledger.save(Expense(EntryId("8"), 1, AccountId("bank"), Money(5, Currency.INR)), EntryDetails(merchant = "uber eats gone")); ledger.delete(EntryId("8"))
        assertEquals(2, store.matchesInHistory("uber eats"), "a deleted entry does not count with several words either")
        assertEquals(0, store.matchesInHistory("lyft")); assertEquals(0, store.matchesInHistory(" a "))
        assertEquals(1, store.matchesInHistory("100%"), "a percent sign is text, not a wildcard ('1005 Stores' must not match)"); assertEquals(1, store.matchesInHistory("fresh_mart"))
        assertEquals(0, store.matchesInHistory("fresh-mart"), "and an underscore is not a wildcard")
    }

    @Test
    fun whatIsLearnedForMoneyInIsKeptApartFromWhatIsLearnedForTheSameNameAsAPayee() {
        store.learn("RAVI KUMAR", "Food", "Ravi Kumar")
        store.learn("ravi.kumar@okaxis", "income:salary", "Ravi Kumar", income = true)
        assertEquals(mapOf("ravikumar" to "Food", "in:ravikumar" to "income:salary"), store.learned(), "two keys for one name: one as a payee, one as a payer")
        assertEquals(1, store.learnedUses().getValue("in:ravikumar"))
        store.learn("RAVI KUMAR", "income:salary", income = true); assertEquals(2, store.learnedUses().getValue("in:ravikumar"), "agreeing again counts")
        store.learn("RAVI KUMAR", "income:interest", income = true); assertEquals(1, store.learnedUses().getValue("in:ravikumar"), "a different choice starts again")
        assertEquals(2, store.learnedRows().size)
        assertEquals(listOf(true), store.learnedRows().filter { it.income }.map { it.income }); assertEquals("RAVI KUMAR", store.learnedRows().first { it.income }.label)
        store.forget("RAVI KUMAR", income = true); assertEquals(setOf("ravikumar"), store.learned().keys, "forgetting the payer leaves the payee")
        store.learn(null, "income:salary", income = true); store.learn("a", "income:salary", income = true); assertEquals(setOf("ravikumar"), store.learned().keys)
        store.forget("RAVI KUMAR"); assertEquals(emptyMap(), store.learned(), "and the other way round")
    }

    @Test
    fun aRemovedRuleComesBackWhereItWasAndAForgottenOrChangedChoiceComesBackWithItsCountAndLabel() {
        store.add("a", "uber", "Transport"); store.add("b", "swiggy", "Food"); store.add("c", "netflix", "Fun")
        val snap = store.snapshotRule("b")!!
        store.remove("b"); assertEquals(listOf("a", "c"), store.rules().map { it.id })
        store.restoreRule(snap); assertEquals(listOf("a", "b", "c"), store.rules().map { it.id }, "back in its place")
        assertEquals(listOf("swiggy" to "Food"), store.rules().filter { it.id == "b" }.map { it.pattern to it.categoryId })
        // a rule the person made since with the same words stays, and the restore is refused without changing anything
        store.remove("b"); store.add("n", "Swiggy", "Groceries")
        assertTrue(assertFailsWith<IllegalArgumentException> { store.restoreRule(snap) }.message!!.contains("already have a rule"))
        assertEquals(listOf("a", "c", "n"), store.rules().map { it.id })
        // a rule whose category was removed meanwhile cannot come back
        store.remove("n"); db.schemaQueries.softDeleteCategory("Food")
        assertTrue(assertFailsWith<IllegalArgumentException> { store.restoreRule(snap) }.message!!.contains("no longer exists"))
        assertEquals(null, store.snapshotRule("nope"))
        // learned choices: forgetting, then changing, each restored exactly (category, count in a row, label)
        store.learn("Zomato", "Transport", "Zomato"); store.learn("ZOMATO", "Transport"); store.learn("zomato@icici", "Transport")
        val before = store.snapshotLearned("zomato")!!; assertEquals(3, before.uses); assertEquals("ZOMATO", before.label, "the label is how the payee was last written")
        store.forgetKey("zomato"); assertEquals(null, store.snapshotLearned("zomato"))
        store.restoreLearned(before); assertEquals(before, store.snapshotLearned("zomato"), "forget undone: the same category, count and label")
        store.setLearned("zomato", "Fun"); assertEquals(1, store.snapshotLearned("zomato")!!.uses)
        store.restoreLearned(before); assertEquals(before, store.snapshotLearned("zomato"), "change undone")
        store.learn("Zomato", "Fun") // the person chose again since: Undo of the earlier forget replaces it, as the person asked
        store.forgetKey("zomato"); store.learn("Zomato", "Fun"); store.restoreLearned(before); assertEquals(before, store.snapshotLearned("zomato"))
        db.schemaQueries.softDeleteCategory("Transport")
        assertTrue(assertFailsWith<IllegalArgumentException> { store.restoreLearned(before) }.message!!.contains("no longer exists"))
    }
}
