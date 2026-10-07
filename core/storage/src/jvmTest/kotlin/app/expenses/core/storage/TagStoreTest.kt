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
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagStoreTest {
    private val inr = Currency.INR
    private var now = 1_000L
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val ledger = LedgerStore(db, HlcClock("n")) { now }
    private fun ex(id: String, at: Long = 1, cat: String = "Food", amount: Long = 100) = Expense(EntryId(id), at, AccountId("cash"), Money(amount, inr), listOf(CategorySplit(CategoryId(cat), Money(amount, inr))))

    init { ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr)) }

    @Test
    fun tagsAreTidiedKeepTheFirstSpellingAndNullLeavesThemAloneWhileEmptyClearsThem() {
        ledger.save(ex("a"), EntryDetails(tags = listOf(" #Goa Trip", "tax", "TAX", "")))
        ledger.save(ex("b"), EntryDetails(tags = listOf("goa trip")))
        assertEquals(listOf("Goa Trip", "tax"), ledger.tagsOf(EntryId("a")))
        assertEquals(listOf("Goa Trip"), ledger.tagsOf(EntryId("b")), "the second entry uses the spelling the first one set")
        ledger.save(ex("a", amount = 200), EntryDetails(merchant = "edited"))   // tags null: an import or a restore that knows nothing of tags
        assertEquals(listOf("Goa Trip", "tax"), ledger.tagsOf(EntryId("a")), "untouched")
        ledger.save(ex("a", amount = 200), EntryDetails(tags = emptyList()))
        assertEquals(emptyList(), ledger.tagsOf(EntryId("a")), "cleared")
        assertEquals(listOf("Goa Trip" to 1L), ledger.tagsInUse())
    }

    @Test
    fun anEntryHoldsAtMostTenTagsAndDeletedEntriesStopCountingTowardsUse() {
        ledger.save(ex("a"), EntryDetails(tags = (1..15).map { "t$it" }))
        assertEquals(10, ledger.tagsOf(EntryId("a")).size)
        ledger.save(ex("b"), EntryDetails(tags = listOf("t1"))); ledger.save(ex("c"), EntryDetails(tags = listOf("t1")))
        assertEquals("t1" to 3L, ledger.tagsInUse().first(), "most used first")
        ledger.delete(EntryId("b")); ledger.delete(EntryId("c"))
        assertEquals(1L, ledger.tagsInUse().first { it.first == "t1" }.second)
    }

    @Test
    fun aTagChangeIsOneEditInTheHistoryWithBeforeAndAfter() {
        now = 1_000; ledger.save(ex("a"), EntryDetails(tags = listOf("trip")))
        now = 2_000; ledger.save(ex("a"), EntryDetails(tags = listOf("trip", "tax")))
        now = 3_000; ledger.save(ex("a"), EntryDetails(tags = emptyList()))
        val h = ledger.history(EntryId("a"))
        assertEquals(listOf(RevisionKind.CREATED, RevisionKind.EDITED, RevisionKind.EDITED), h.map { it.kind })
        assertEquals(listOf(app.expenses.core.domain.FieldChange("tags", "trip", "tax,trip")), h[1].changes)
        assertEquals(listOf(app.expenses.core.domain.FieldChange("tags", "tax,trip", null)), h[2].changes)
    }

    @Test
    fun searchingByTagGivesExactlyWhatTheSameSearchGivesRestrictedToTaggedEntriesForAnyFilters() {
        val rnd = Random(5)
        val merchants = listOf("Swiggy", "Zomato", "Uber", "Ola Cabs", "Big Basket", "Netflix", "Chai Point")
        val cats = listOf("Food", "Bills", "Fun")
        val tagged = HashSet<String>()
        repeat(400) { i ->
            val id = "e$i"; val amount = rnd.nextLong(1, 300) * 100
            val tag = rnd.nextInt(3) == 0
            if (tag) tagged += id
            ledger.save(ex(id, 1_700_000_000_000L + rnd.nextLong(0, 90) * 86_400_000L, cats[rnd.nextInt(3)], amount), EntryDetails(merchant = merchants[rnd.nextInt(merchants.size)], note = if (rnd.nextBoolean()) "lunch with team" else null, tags = if (tag) listOf("Trip") else null))
        }
        fun base(text: String, cats: List<String>?, min: Long?, kind: String?) = SearchQuery(text, min, null, if (min != null) "INR" else null, cats, kind, 1_700_000_000_000L + 10 * 86_400_000L, 1_700_000_000_000L + 80 * 86_400_000L)
        var compared = 0
        for (text in listOf("", "swiggy", "ola", "lunch team", "uber lunch", "ch", "zz")) for (c in listOf(null, listOf("Food"), listOf("Bills", "Fun"))) for (min in listOf(null, 10_000L)) {
            val q = base(text, c, min, "EXPENSE")
            val expected = db.searchFiltered(q, 1_000).map { it.id }.filter { it in tagged }
            val actual = db.searchFiltered(q.copy(tag = "trip"), 1_000).map { it.id }   // note: a different case
            assertEquals(expected, actual, "text='$text' cats=$c min=$min"); compared++
        }
        assertTrue(compared == 7 * 3 * 2)
        assertEquals(emptyList(), db.searchFiltered(SearchQuery(tag = "nothing"), 10).map { it.id })
        assertEquals(5, db.searchFiltered(SearchQuery(tag = "Trip"), 5).size, "the limit applies")
    }
}
