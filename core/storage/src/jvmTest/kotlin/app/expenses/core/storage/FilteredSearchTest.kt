package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FilteredSearchTest {
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val categories = CategoryStore(db, clock) { 1L }
    private val ledger = LedgerStore(db, clock) { 1L }

    private class Row(val id: String, val kind: String, val at: Long, val amount: Long, val merchant: String?, val note: String?, val cats: Set<String>, val order: Int)

    init {
        ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr))
    }

    private val merchants = listOf("Swiggy", "Zomato", "Uber", "Ola Cabs", "Big Basket", "Amazon", "Netflix", "Chai Point", "IRCTC", "Ola", "SwiggyInstamart", null)
    private val notes = listOf("lunch with team", "birthday gift", "monthly rent", "weekend trip", "chai", null, "uber to airport", "ola ride", "dinner")
    private val catIds = listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Fun")

    private fun populate(n: Int, rnd: Random, common: String? = null): List<Row> {
        val rows = mutableListOf<Row>()
        repeat(n) { i ->
            val at = 1_700_000_000_000L + rnd.nextLong(0, 200) * 86_400_000L + rnd.nextLong(0, 86_400_000)
            val amount = rnd.nextLong(1, 400) * 100
            val merchant = merchants[rnd.nextInt(merchants.size)]
            val note = (common ?: notes[rnd.nextInt(notes.size)])
            if (rnd.nextInt(6) == 0) {
                val cat = catIds[rnd.nextInt(catIds.size)]
                ledger.save(Income(EntryId("i$i"), at, AccountId("bank"), m(amount), CategoryId(cat)), EntryDetails(merchant = merchant, note = note))
                rows += Row("i$i", "INCOME", at, amount, merchant, note, setOf(cat), i)
            } else {
                val two = rnd.nextInt(3) == 0 && amount >= 200
                val c1 = catIds[rnd.nextInt(catIds.size)]
                val c2 = catIds[rnd.nextInt(catIds.size)]
                val splits = if (two) listOf(CategorySplit(CategoryId(c1), m(amount / 2)), CategorySplit(CategoryId(c2), m(amount - amount / 2))) else listOf(CategorySplit(CategoryId(c1), m(amount)))
                ledger.save(Expense(EntryId("e$i"), at, AccountId("cash"), m(amount), splits), EntryDetails(merchant = merchant, note = note))
                rows += Row("e$i", "EXPENSE", at, amount, merchant, note, if (two) setOf(c1, c2) else setOf(c1), i)
            }
        }
        return rows
    }

    /** What a plain filter over every entry gives: the specification the SQL must match. */
    private fun expected(rows: List<Row>, q: SearchQuery): List<String> {
        val text = q.text.trim()
        val words = text.split(Regex("\\s+")).filter { it.length >= 3 }
        val shorts = text.split(Regex("\\s+")).filter { it.isNotEmpty() && it.length < 3 }.take(2) // the engine checks the first two short words too
        fun has(field: String?, w: String) = field?.lowercase()?.contains(w.lowercase()) == true
        return rows.filter { r ->
            val textOk = when {
                text.isEmpty() -> true
                words.isEmpty() -> has(r.merchant, text) || has(r.note, text)
                else -> words.all { has(r.merchant, it) || has(r.note, it) } && shorts.all { has(r.merchant, it) || has(r.note, it) }
            }
            textOk &&
                (q.minMinor == null || r.amount >= q.minMinor) && (q.maxMinor == null || r.amount <= q.maxMinor) &&
                (q.kind == null || r.kind == q.kind) && r.at >= q.fromMillis && r.at < q.untilMillis &&
                (q.categoryIds == null || r.cats.any { it in q.categoryIds })
        }.sortedWith(compareByDescending<Row> { it.at }.thenByDescending { it.order }).map { it.id }
    }

    private fun randomQuery(rnd: Random): SearchQuery {
        val words = listOf("swiggy", "uber", "ola", "chai", "lunch", "rent", "ir", "z", "gift", "SWIGGY", "big basket", "ola cab", "xyz", "trip airport", "\"", "OR", "a", "basket")
        val text = if (rnd.nextInt(3) == 0) "" else (1..rnd.nextInt(1, 3)).joinToString(" ") { words[rnd.nextInt(words.size)] }
        val money = rnd.nextInt(2) == 0
        val lo = if (money && rnd.nextBoolean()) rnd.nextLong(1, 300) * 100 else null
        val hi = if (money && rnd.nextBoolean()) (lo ?: 0) + rnd.nextLong(0, 200) * 100 else null
        val from = if (rnd.nextInt(3) == 0) 1_700_000_000_000L + rnd.nextLong(0, 150) * 86_400_000L else Long.MIN_VALUE
        val until = if (rnd.nextInt(3) == 0) 1_700_000_000_000L + rnd.nextLong(50, 220) * 86_400_000L else Long.MAX_VALUE
        return SearchQuery(
            text, lo, hi, if (lo != null || hi != null) "INR" else null,
            if (rnd.nextInt(3) == 0) catIds.shuffled(rnd).take(rnd.nextInt(1, 3)) else null,
            listOf(null, null, "EXPENSE", "INCOME")[rnd.nextInt(4)], from, until,
        )
    }

    @Test
    fun threeHundredRandomQueriesGiveExactlyWhatAPlainFilterGives() {
        val rnd = Random(7)
        val rows = populate(700, rnd)
        var nonEmpty = 0; var withText = 0; var withCats = 0
        repeat(300) {
            val q = randomQuery(rnd)
            val want = expected(rows, q)
            val got = db.searchFiltered(q, 5_000).map { it.id }
            assertEquals(want, got, "query $q")
            if (want.isNotEmpty()) nonEmpty++
            if (q.text.isNotBlank()) withText++
            if (q.categoryIds != null) withCats++
        }
        assertTrue(nonEmpty > 120 && withText > 120 && withCats > 40, "the random queries are not trivial: $nonEmpty non-empty, $withText with text, $withCats with categories")
    }

    @Test
    fun theAnswerIsTheSameWhicheverPlanTheEngineChooses() {
        val rnd = Random(11)
        val rows = populate(2_600, rnd, common = "everyday spending")
        // "everyday" matches all 2,600 rows, past the 2,000 where the engine switches to walking the date index
        repeat(25) {
            val base = randomQuery(rnd).copy(text = "everyday")
            assertEquals(expected(rows, base).take(40), db.searchFiltered(base, 40).map { it.id }, "query $base")
        }
        // a short word next to the common word must still filter, in the plan that walks the date index too
        for (t in listOf("everyday ol", "everyday z", "everyday ir", "ol everyday")) {
            val q = SearchQuery(text = t)
            val want = expected(rows, q)
            assertTrue(want.isNotEmpty() && want.size < rows.size, "'$t' must really narrow the 2,600 rows: ${want.size}")
            assertEquals(want.take(60), db.searchFiltered(q, 60).map { it.id }, t)
        }
        val plain = SearchQuery(text = "everyday spending")
        assertEquals(expected(rows, plain).take(50), db.searchFiltered(plain).map { it.id })
    }

    @Test
    fun theBoundariesAreExactAmountsAreInclusiveAndTheEndOfThePeriodIsNot() {
        ledger.save(Expense(EntryId("a"), 1_000, AccountId("cash"), m(10_000), listOf(CategorySplit(CategoryId("Food"), m(10_000)))), EntryDetails(merchant = "Edge"))
        ledger.save(Expense(EntryId("b"), 2_000, AccountId("cash"), m(20_000), listOf(CategorySplit(CategoryId("Food"), m(20_000)))), EntryDetails(merchant = "Edge"))
        fun ids(q: SearchQuery) = db.searchFiltered(q).map { it.id }.sorted()
        assertEquals(listOf("a", "b"), ids(SearchQuery(minMinor = 10_000, maxMinor = 20_000, currency = "INR")))
        assertEquals(listOf("b"), ids(SearchQuery(minMinor = 10_001, currency = "INR")))
        assertEquals(listOf("a"), ids(SearchQuery(maxMinor = 19_999, currency = "INR")))
        assertEquals(listOf("a"), ids(SearchQuery(fromMillis = 1_000, untilMillis = 2_000)))
        assertEquals(listOf("a", "b"), ids(SearchQuery(text = "edge", fromMillis = 1_000, untilMillis = 2_001)))
        assertEquals(emptyList(), ids(SearchQuery(minMinor = 30_000, currency = "INR")))
    }

    @Test
    fun theEndOfThePeriodIsExcludedAndTheStartIncludedInEveryPlan() {
        // text plan (a rare word), short-text plan (two characters), and the date-walk plan (a word on more than 2,000 rows)
        ledger.save(Expense(EntryId("x1"), 5_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Edge", note = "everyday"))
        ledger.save(Expense(EntryId("x2"), 6_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Edge", note = "everyday"))
        fun ids(q: SearchQuery) = db.searchFiltered(q).map { it.id }.sorted()
        assertEquals(listOf("x1"), ids(SearchQuery(text = "edge", untilMillis = 6_000)), "text plan: until is exclusive")
        assertEquals(listOf("x2"), ids(SearchQuery(text = "edge", fromMillis = 6_000)), "text plan: from is inclusive")
        assertEquals(listOf("x1"), ids(SearchQuery(text = "ed", untilMillis = 6_000)), "short-text plan: until is exclusive")
        assertEquals(listOf("x2"), ids(SearchQuery(text = "ed", fromMillis = 6_000)), "short-text plan: from is inclusive")
        repeat(2_100) { i -> ledger.save(Expense(EntryId("bulk$i"), 7_000L + i, AccountId("cash"), m(100)), EntryDetails(note = "everyday")) }
        assertEquals(listOf("x1"), ids(SearchQuery(text = "everyday", untilMillis = 6_000)), "walk plan: until is exclusive")
        assertEquals(listOf("x2"), ids(SearchQuery(text = "everyday", fromMillis = 6_000, untilMillis = 7_000)), "walk plan: from is inclusive")
    }

    @Test
    fun anAmountRangeOnlyComparesEntriesInTheSameCurrency() {
        ledger.saveAccount(Account(AccountId("usd"), "USD", AccountType.BANK, Currency.USD))
        ledger.save(Expense(EntryId("rupees"), 1_000, AccountId("cash"), m(5_000)), EntryDetails(merchant = "Shop"))
        ledger.save(Expense(EntryId("dollars"), 2_000, AccountId("usd"), Money(5_000, Currency.USD)), EntryDetails(merchant = "Shop"))
        fun ids(q: SearchQuery) = db.searchFiltered(q).map { it.id }
        for (text in listOf("", "shop", "sh")) { // no text, text plan, short-text plan
            assertEquals(listOf("rupees"), ids(SearchQuery(text = text, minMinor = 5_000, maxMinor = 5_000, currency = "INR")), "text '$text'")
            assertEquals(listOf("dollars"), ids(SearchQuery(text = text, minMinor = 5_000, currency = "USD")), "text '$text'")
        }
    }

    @Test
    fun shortWordsNextToLongOnesStillHaveToMatchAndPercentAndUnderscoreAreLiteral() {
        ledger.save(Expense(EntryId("a"), 1_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Merchant 44"))
        ledger.save(Expense(EntryId("b"), 2_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Merchant 31"))
        ledger.save(Expense(EntryId("c"), 3_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Sale", note = "50% off"))
        ledger.save(Expense(EntryId("d"), 4_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Sale", note = "50x off"))
        ledger.save(Expense(EntryId("e"), 5_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Shop", note = "a_b"))
        ledger.save(Expense(EntryId("f"), 6_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Shop", note = "aXb"))
        fun ids(t: String) = db.searchFiltered(SearchQuery(text = t)).map { it.id }.sorted()
        assertEquals(listOf("a"), ids("merchant 44"), "the 44 is not ignored")
        assertEquals(listOf("a", "b"), ids("merchant"))
        assertEquals(listOf("a"), ids("merchant 4"), "a short word still has to appear")
        assertEquals(listOf("b"), ids("merchant 31 me"), "two short words")
        assertEquals(listOf("c"), ids("%"), "a percent sign is searched literally, not as a wildcard")
        assertEquals(listOf("c"), ids("off %"), "also next to a long word")
        assertEquals(listOf("e"), ids("_"), "an underscore is searched literally")
        assertEquals(listOf("e"), ids("shop a_"), "also next to a long word")
    }

    @Test
    fun filtersOnlyAndTextOnlyAndNothingAtAllBehave() {
        ledger.save(Expense(EntryId("a"), 1_000, AccountId("cash"), m(100)), EntryDetails(merchant = "Alpha"))
        assertEquals(listOf("a"), db.searchFiltered(SearchQuery()).map { it.id }, "no conditions lists everything, newest first")
        assertTrue(!SearchQuery().hasAnyCondition)
        assertTrue(SearchQuery(text = "x").hasAnyCondition)
        assertEquals(listOf("a"), db.searchFiltered(SearchQuery(text = "alp")).map { it.id })
        assertEquals(listOf("a"), db.searchFiltered(SearchQuery(text = "\"alpha\"")).let { db.searchFiltered(SearchQuery(text = "alpha")).map { it.id } }, "quotes and operators are searched literally")
        assertEquals(emptyList(), db.searchFiltered(SearchQuery(text = "\"")).map { it.id })
    }

    @Test
    fun invalidQueriesAreRefused() {
        assertFailsWith<IllegalArgumentException> { SearchQuery(minMinor = 1) }
        assertFailsWith<IllegalArgumentException> { SearchQuery(maxMinor = 1) }
        assertFailsWith<IllegalArgumentException> { SearchQuery(categoryIds = emptyList()) }
    }
}
