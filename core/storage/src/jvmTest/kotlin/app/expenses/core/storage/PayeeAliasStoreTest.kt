package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DatePeriod
import app.expenses.core.domain.Money
import app.expenses.core.domain.PayeeAliases
import app.expenses.core.domain.PayeeKey
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Same shop as": the overlay is a device-local setting, never a rewrite of an entry. What it changes: what is learned (one row for the shop), what a search finds, and what the Merchant report adds
 * together; removing it puts everything back. Invented names and amounts.
 */
class PayeeAliasStoreTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n")
    private val ledger = LedgerStore(db, clock) { 1L }
    private val store = CaptureRuleStore(db)
    private val reports = ReportStore(db, ledger)
    private val inr = Currency.INR
    private fun m(v: Long) = Money(v, inr)
    private val zone = TimeZone.UTC
    private val month = DatePeriod(LocalDate(2026, 10, 1), LocalDate(2026, 11, 1))
    private fun at(day: Int) = LocalDateTime(2026, 10, day, 12, 0).toInstant(zone).toEpochMilliseconds()

    init { ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr)) }

    private fun spend(id: String, day: Int, minor: Long, merchant: String?, note: String? = null) = ledger.save(Expense(EntryId(id), at(day), AccountId("cash"), m(minor)), EntryDetails(merchant = merchant, note = note))

    @Test
    fun anAliasIsStoredReadBackReplacedAndRemovedAndAnUndoPutsItBackExactly() {
        assertTrue(store.aliases().isEmpty)
        val a = store.addAlias("Swiggy Instamart", "Swiggy")
        assertEquals("swiggyinstamart", a.from); assertEquals("swiggy", a.to); assertEquals("Swiggy Instamart", a.fromLabel); assertEquals("Swiggy", a.toLabel)
        assertEquals(listOf(a), store.aliases().all)
        assertEquals(a, store.snapshotAlias("swiggyinstamart"))
        assertEquals("swiggy", store.keyOf("SWIGGY INSTAMART BANGALORE KA IN")); assertEquals("in:swiggy", store.keyOf("Swiggy Instamart", income = true))
        // replacing what a name counts as is allowed, and the snapshot taken before gives the old one back
        val before = store.snapshotAlias("swiggyinstamart")
        val b = store.addAlias("Swiggy Instamart", "Zomato")
        assertEquals("zomato", store.aliases().resolve("swiggyinstamart"))
        store.restoreAlias("swiggyinstamart", before); assertEquals(listOf(a), store.aliases().all)
        store.removeAlias("swiggyinstamart"); assertTrue(store.aliases().isEmpty)
        store.restoreAlias("swiggyinstamart", a); assertEquals(listOf(a), store.aliases().all, "Undo of a removal")
        store.restoreAlias("swiggyinstamart", null); assertTrue(store.aliases().isEmpty, "Undo of an add that had nothing before removes it")
        assertEquals("zomato", b.to)
    }

    @Test
    fun aNothingNameOneShopWithItselfAndACycleAreRefusedWithAPlainMessageAndNothingChanges() {
        assertTrue(assertFailsWith<IllegalArgumentException> { store.addAlias("  ", "Swiggy") }.message!!.contains("name of a shop"))
        assertTrue(assertFailsWith<IllegalArgumentException> { store.addAlias("Swiggy", "swiggy@icici") }.message!!.contains("already count as one shop"))
        store.addAlias("Swiggy Instamart", "Swiggy")
        assertTrue(assertFailsWith<IllegalArgumentException> { store.addAlias("Swiggy", "Swiggy Instamart") }.message!!.contains("already count as one shop"), "a cycle")
        assertTrue(assertFailsWith<IllegalArgumentException> { store.addAlias("Swiggy Instamart", "Swiggy") }.message!!.contains("already counts as"))
        assertEquals(1, store.aliases().all.size)
        // an undo that would now make a cycle is refused and changes nothing
        val old = store.snapshotAlias("swiggyinstamart")
        store.removeAlias("swiggyinstamart"); store.addAlias("Swiggy", "Swiggy Instamart")
        assertFailsWith<IllegalArgumentException> { store.restoreAlias("swiggyinstamart", old) }
        assertEquals("swiggyinstamart", store.aliases().resolve("swiggy"))
    }

    @Test
    fun learningThroughANameOfAJoinedShopWritesOneRowForTheShopAndLeavesTheNamesOwnRowAlone() {
        store.learn("Swiggy Instamart", "Groceries", "Swiggy Instamart")
        assertEquals(mapOf("swiggyinstamart" to "Groceries"), store.learned())
        store.addAlias("Swiggy Instamart", "Swiggy")
        assertEquals("Groceries", PayeeKey.lookup("Swiggy", store.learned(), store.aliases()), "a choice made for the member is used for the shop that has none")
        store.learn("SWIGGY INSTAMART", "Food"); store.learn("Swiggy", "Food")
        assertEquals(mapOf("swiggyinstamart" to "Groceries", "swiggy" to "Food"), store.learned(), "one row for the shop; the member's own row is not deleted")
        assertEquals(2, store.learnedUses()["swiggy"], "both names counted for the one shop")
        assertEquals("Swiggy", store.learnedRows().single { it.key == "swiggy" }.label, "the list keeps the shop's own name, not the joined name")
        assertEquals("Food", PayeeKey.lookup("Swiggy Instamart", store.learned(), store.aliases()), "the shop's choice wins")
        store.removeAlias("swiggyinstamart")
        assertEquals("Groceries", PayeeKey.lookup("Swiggy Instamart", store.learned(), store.aliases()), "removing the alias gives the name its own choice back")
        assertEquals("Food", PayeeKey.lookup("Swiggy", store.learned(), store.aliases()))
    }

    @Test
    fun anIncomeChoiceForAJoinedPayerIsOneRowAndKeptApartFromExpenseRows() {
        store.addAlias("Ravi K", "Ravi Kumar")
        store.learn("Ravi K", "Rent received", income = true); store.learn("Ravi Kumar", "Gifts")
        assertEquals(mapOf("in:ravikumar" to "Rent received", "ravikumar" to "Gifts"), store.learned())
        assertEquals("Rent received", PayeeKey.lookupIncome("RAVI K", store.learned(), store.aliases()))
    }

    @Test
    fun forgettingThroughAJoinedNameForgetsTheShopsRowAndTheOldKeys() {
        store.addAlias("Swiggy Instamart", "Swiggy")
        store.learn("Swiggy Instamart", "Food")
        assertEquals(setOf("swiggy"), store.learned().keys)
        store.forget("Swiggy Instamart")
        assertEquals(emptyMap(), store.learned())
    }

    @Test
    fun aRowLearnedBeforeTwoNamesWereMergedByTheBuiltInTableIsCarriedOverWithItsCountWhenItAgreesAndDroppedWhenItDoesNot() {
        db.schemaQueries.learnMerchant("amazonpay", "Shopping"); db.schemaQueries.learnMerchant("amazonpay", "Shopping")
        assertEquals(2L, db.schemaQueries.allMerchantMap().executeAsList().single().uses)
        assertEquals("Shopping", PayeeKey.lookup("AMAZON PAY INDIA", store.learned()), "found under the key before the merge")
        assertEquals("Shopping", PayeeKey.lookup("Amazon", store.learned()), "and for the merchant it is now merged into")
        store.learn("AMAZON PAY INDIA", "Shopping")
        assertEquals(mapOf("amazon" to "Shopping"), store.learned()); assertEquals(3, store.learnedUses()["amazon"], "the old count carried over")
        db.schemaQueries.learnMerchant("amazonpay", "Bills")
        store.learn("Amazon Pay", "Shopping")
        assertEquals(mapOf("amazon" to "Shopping"), store.learned(), "an old row of the same merchant under the other name does not linger")
        assertEquals(4, store.learnedUses()["amazon"], "Shopping again counts once more; the older row of a different category is dropped, as an older key always was")
    }

    // ---- the Merchant report and search ----

    @Test
    fun theMerchantReportAddsJoinedNamesTogetherAndDrillingIntoTheRowReturnsExactlyItsTotal() {
        spend("a", 2, 50_000, "Swiggy"); spend("b", 3, 30_000, "Swiggy Instamart"); spend("c", 4, 20_000, "Zomato"); spend("d", 5, 10_000, "swiggy instamart "); spend("e", 6, 5_000, null)
        val plain = reports.groups(ReportGroup.MERCHANT, month, zone, inr)
        assertEquals(listOf("swiggy", "swiggy instamart", "zomato", ""), plain.map { it.key }, "as typed, as before")
        store.addAlias("Swiggy Instamart", "Swiggy")
        val rows = reports.groups(ReportGroup.MERCHANT, month, zone, inr)
        assertEquals(listOf("Swiggy", "Zomato", "No merchant"), rows.map { it.name })
        assertEquals(listOf(m(90_000), m(20_000), m(5_000)), rows.map { it.total })
        assertEquals(reports.spent(month, zone, inr), rows.fold(m(0)) { s, r -> s + r.total }, "the rows still add up to what was spent")
        val lines = reports.drill(month, zone, inr, DrillTarget.AtMerchant(rows.first().key!!))
        assertEquals(listOf("d", "b", "a"), lines.map { it.entryId }, "every name of the shop, newest first")
        assertEquals(rows.first().total, lines.fold(m(0)) { s, l -> s + l.amount })
        // the entries themselves are untouched
        assertEquals(listOf("Swiggy Instamart", "Swiggy", "swiggy instamart "), listOf("b", "a", "d").map { id -> db.schemaQueries.entryById(id).executeAsOne().merchant })
        store.removeAlias("swiggyinstamart")
        assertEquals(plain.map { it.key to it.total }, reports.groups(ReportGroup.MERCHANT, month, zone, inr).map { it.key to it.total }, "removing the alias gives the plain report back")
    }

    @Test
    fun aSearchForOneNameOfAJoinedShopFindsTheOtherAndOnlyThenAndNeverFromALongerText() {
        spend("a", 2, 1_000, "Instamart"); spend("b", 3, 2_000, "Swiggy"); spend("c", 4, 3_000, "Zomato"); spend("d", 5, 4_000, null, note = "swiggy food for the team")
        val ids = { q: String, a: PayeeAliases -> db.searchFiltered(SearchQuery(text = q), a, 50).map { it.id }.toSet() }
        assertEquals(setOf("b", "d"), ids("swiggy", PayeeAliases.NONE))
        assertEquals(setOf("a"), ids("instamart", PayeeAliases.NONE), "no alias: a word finds only that word")
        store.addAlias("Instamart", "Swiggy")
        val joined = store.aliases()
        assertEquals(setOf("a", "b", "d"), ids("swiggy", joined), "Swiggy finds Instamart too")
        assertEquals(setOf("a", "b", "d"), ids("Instamart", joined), "and the reverse")
        assertEquals(setOf("c"), ids("zomato", joined), "a name in no alias is searched as typed")
        assertEquals(emptyList(), AliasSearch.otherNames("swiggy food order", joined), "three words name no shop")
        assertEquals(listOf("Instamart"), AliasSearch.otherNames("Swiggy", joined)); assertEquals(emptyList(), AliasSearch.otherNames("swiggy food order extra", joined), "a long text names no shop")
        // filters still apply to the extra names
        assertEquals(setOf("a"), db.searchFiltered(SearchQuery(text = "swiggy", minMinor = 500, maxMinor = 1_500, currency = "INR"), joined, 50).map { it.id }.toSet())
        // the newest first order and the limit hold across the names
        assertEquals(listOf("d", "b", "a"), db.searchFiltered(SearchQuery(text = "swiggy"), joined, 50).map { it.id })
        assertEquals(listOf("d", "b"), db.searchFiltered(SearchQuery(text = "swiggy"), joined, 2).map { it.id })
        assertNull(db.schemaQueries.entryById("a").executeAsOne().note, "no entry was rewritten")
    }

    @Test
    fun withoutAliasesSearchAndTheMerchantReportAreExactlyWhatTheyWereAndAnUnrelatedAliasChangesNeitherForOtherNames() {
        val rnd = kotlin.random.Random(33)
        val shops = listOf("Swiggy", "swiggy ", "Swiggy Instamart", "Uber", "Big Basket", "Zomato", "Amazon", "Amazon Pay", "Cafe Corner", null, "")
        repeat(300) { i -> spend("r$i", 1 + rnd.nextInt(28), 100L + rnd.nextLong(50_000), shops[rnd.nextInt(shops.size)], if (rnd.nextInt(4) == 0) "note ${rnd.nextInt(5)} swiggy food" else null) }
        val queries = listOf("swiggy", "Swiggy Instamart", "instamart", "uber", "big basket", "zomato", "amazon", "ab", "cafe", "swiggy food", "note 3", "zzz", "", "swiggy food for ten").map { SearchQuery(text = it) } +
            SearchQuery(text = "swiggy", minMinor = 5_000, maxMinor = 20_000, currency = "INR") + SearchQuery(text = "uber", fromMillis = at(5), untilMillis = at(20)) + SearchQuery(text = "", kind = "EXPENSE")
        for (q in queries) assertEquals(db.searchFiltered(q, 50).map { it.id }, db.searchFiltered(q, PayeeAliases.NONE, 50).map { it.id }, "no alias: '${q.text}' finds exactly what it found")
        // the merchant report is the same rows the query gives, ranked as before
        val old = db.schemaQueries.spendByMerchant("INR", month.startMillis(zone), month.endMillis(zone)).executeAsList()
            .map { Triple<String?, String, Money>(it.key, it.display?.trim()?.takeIf { d -> d.isNotEmpty() } ?: "No merchant", Money(it.mine ?: 0L, inr)) }
        val expected = app.expenses.core.domain.ReportMath.groups(old, inr)
        assertEquals(expected, reports.groups(ReportGroup.MERCHANT, month, zone, inr), "no alias: the report is what it was")
        assertEquals(reports.spent(month, zone, inr), expected.fold(m(0)) { s, r -> s + r.total })
        // an alias between two names that are not in these queries or rows changes nothing else
        store.addAlias("Zepto Cafe", "Zepto")
        for (q in queries) assertEquals(db.searchFiltered(q, 50).map { it.id }, db.searchFiltered(q, store.aliases(), 50).map { it.id }, "an unrelated alias: '${q.text}'")
        assertEquals(expected, reports.groups(ReportGroup.MERCHANT, month, zone, inr), "an unrelated alias: the report is what it was")
    }
}
