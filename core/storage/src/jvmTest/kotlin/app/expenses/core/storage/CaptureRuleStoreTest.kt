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
}
