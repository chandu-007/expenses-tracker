package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The person's own "same shop as" overlay: how names resolve, what it refuses, and what it changes (learned choices, history, the subscription finder). Invented names only. */
class PayeeAliasesTest {
    private fun alias(from: String, to: String) = PayeeAlias(PayeeKey.of(from)!!, PayeeKey.of(to)!!, from, to)
    private val instamartIsSwiggy = PayeeAliases(listOf(alias("Swiggy Instamart", "Swiggy")))

    @Test
    fun aNameResolvesToTheShopItCountsAsAndAShopWithNoAliasToItself() {
        assertEquals("swiggy", instamartIsSwiggy.resolve("swiggyinstamart"))
        assertEquals("swiggy", instamartIsSwiggy.resolve("swiggy"))
        assertEquals("zomato", instamartIsSwiggy.resolve("zomato"))
        assertEquals(PayeeKey.of("Swiggy"), PayeeKey.of("SWIGGY INSTAMART BANGALORE", instamartIsSwiggy), "written any way, with the alias followed")
        assertEquals("swiggyinstamart", PayeeKey.of("Swiggy Instamart"), "without the alias the two stay two names (one company, filed differently)")
        assertTrue(PayeeAliases.NONE.isEmpty)
    }

    @Test
    fun aChainIsFollowedAndTheLabelsAndMembersOfAShopAreKnown() {
        val chain = PayeeAliases(listOf(alias("Instamart Express", "Swiggy Instamart"), alias("Swiggy Instamart", "Swiggy")))
        assertEquals("swiggy", chain.resolve(PayeeKey.of("Instamart Express")!!))
        assertEquals(listOf("swiggy", "instamartexpress", "swiggyinstamart"), chain.group("swiggy"))
        assertEquals(listOf("swiggy", "swiggyinstamart"), chain.membersOf("instamartexpress"), "the others of the shop, the shop first")
        assertEquals("Swiggy", chain.labelOf("instamartexpress"))
        assertEquals(listOf("Swiggy", "Instamart Express", "Swiggy Instamart"), chain.labelsOf("swiggy"), "the shop's own name first, then the others by key")
        assertTrue(chain.sameShop("instamartexpress", "swiggyinstamart") && chain.sameShop("swiggyinstamart", "swiggy") && !chain.sameShop("swiggy", "zomato"))
        assertFalse(chain.inGroup("zomato")); assertTrue(chain.inGroup("swiggy") && chain.inGroup("swiggyinstamart"))
    }

    @Test
    fun aCycleOrOneShopWithItselfIsRefusedAndReplacingAnAliasIsAllowed() {
        val a = alias("Swiggy Instamart", "Swiggy")
        val t = PayeeAliases(listOf(a))
        assertNull(t.problemFor(PayeeKey.of("Zomato")!!, PayeeKey.of("Swiggy")!!))
        assertEquals("Swiggy and Swiggy already count as one shop.", t.problemFor("swiggy", "swiggy", "Swiggy", "Swiggy"))
        assertNotNull(t.problemFor("swiggy", "swiggyinstamart"), "the shop cannot count as its own member: a cycle")
        assertNotNull(t.problemFor("swiggyinstamart", "swiggy", "Swiggy Instamart", "Swiggy"), "already so")
        assertNull(t.problemFor("swiggyinstamart", "zomato"), "an alias is replaced by another")
        assertNotNull(t.problemFor("a", "swiggy"), "not a name")
        // a hand-made cycle still ends
        val cyc = PayeeAliases(listOf(PayeeAlias("aa", "bb"), PayeeAlias("bb", "aa")))
        assertTrue(cyc.resolve("aa") in setOf("aa", "bb"))
    }

    @Test
    fun withAndWithoutBuildNewTablesAndTheOldOneIsUntouched() {
        val two = instamartIsSwiggy.with(alias("Zepto Cafe", "Zepto"))
        assertEquals(2, two.all.size); assertEquals(1, instamartIsSwiggy.all.size)
        assertEquals(1, two.without(PayeeKey.of("Zepto Cafe")!!).all.size)
        assertEquals("swiggy", two.without(PayeeKey.of("Zepto Cafe")!!).resolve("swiggyinstamart"))
        assertEquals(two, two.without("nothing").let { two }, "removing what is not there changes nothing")
    }

    @Test
    fun aStoredValueReadsBackAndABadOneIsSkipped() {
        val a = PayeeAlias("swiggyinstamart", "swiggy", "Swiggy\tInstamart", "Swiggy")
        val back = PayeeAliases.decode(PayeeAliases.PREFIX + a.from, PayeeAliases.encode(a))!!
        assertEquals("swiggyinstamart", back.from); assertEquals("swiggy", back.to); assertEquals("Swiggy Instamart", back.fromLabel, "a tab in a name is a space"); assertEquals("Swiggy", back.toLabel)
        assertNull(PayeeAliases.decode(PayeeAliases.PREFIX + "x", "swiggy"), "a key that is no key")
        assertNull(PayeeAliases.decode(PayeeAliases.PREFIX + "swiggy", "swiggy"), "a shop counting as itself")
        assertNull(PayeeAliases.decode(PayeeAliases.PREFIX + "swiggy", ""))
        assertEquals("swiggyinstamart", PayeeAliases.decode(PayeeAliases.PREFIX + "swiggyinstamart", "swiggy")!!.fromLabel, "labels are optional")
    }

    // ---- what it changes ----

    @Test
    fun aChoiceForOneNameIsFoundForTheOtherAndTheShopsOwnChoiceWins() {
        val a = instamartIsSwiggy
        assertEquals("Food", PayeeKey.lookup("Swiggy Instamart", mapOf("swiggy" to "Food"), a))
        assertNull(PayeeKey.lookup("Swiggy Instamart", mapOf("swiggy" to "Food")), "without the alias it is another shop")
        assertEquals("Groceries", PayeeKey.lookup("Swiggy", mapOf("swiggyinstamart" to "Groceries"), a), "a choice made for the member is used for the shop when it has none")
        assertEquals("Food", PayeeKey.lookup("Swiggy Instamart", mapOf("swiggy" to "Food", "swiggyinstamart" to "Groceries"), a), "the shop's own choice wins")
        assertEquals("Groceries", PayeeKey.lookup("Swiggy Instamart", mapOf("swiggyinstamart" to "Groceries")), "the name's own choice without an alias")
        assertEquals("swiggy", PayeeKey.lookupKey("swiggy@icici", mapOf("swiggy" to "Food"), a))
    }

    @Test
    fun anIncomeChoiceFollowsTheAliasAndStaysApartFromExpenseChoices() {
        val a = PayeeAliases(listOf(alias("Ravi K", "Ravi Kumar")))
        assertEquals("in:ravikumar", PayeeKey.incomeOf("Ravi K", a))
        assertEquals("Rent received", PayeeKey.lookupIncome("Ravi K", mapOf("in:ravikumar" to "Rent received"), a))
        assertNull(PayeeKey.lookupIncome("Ravi K", mapOf("ravikumar" to "Gifts"), a), "an expense choice is never an income choice")
        assertEquals("Rent received", PayeeKey.lookupIncome("Ravi Kumar", mapOf("in:ravik" to "Rent received"), a), "a member's income row is used for the shop")
    }

    @Test
    fun aNameJoinedToAMarketplaceIsMixedToo() {
        assertFalse(PayeeKey.isMixed("Amazon Biz Shop"))
        assertTrue(PayeeKey.isMixed("Amazon Biz Shop", PayeeAliases(listOf(alias("Amazon Biz Shop", "Amazon")))))
        assertTrue(PayeeKey.isMixed("Amazon Pay"), "built in: Amazon Pay is Amazon")
    }

    @Test
    fun theHistoryOfBothNamesCountsForEachOnceTheyAreOneShop() {
        fun c(id: String, label: String, cat: String) = SimilarCandidate(id, label, cat, 10_000, 1)
        val rows = listOf(c("1", "Instamart", "Groceries"), c("2", "Instamart", "Groceries"), c("3", "Swiggy", "Food"), c("4", "Zomato", "Food"))
        val joined = PayeeAliases(listOf(PayeeAlias(PayeeKey.of("Instamart")!!, PayeeKey.of("Swiggy")!!, "Instamart", "Swiggy")))
        assertEquals(HistoryProfile("Groceries", 2, 1, listOf("Food")), SimilarEntries.profile("Swiggy", rows, joined))
        assertNull(SimilarEntries.profile("Swiggy", rows.take(2)), "without the alias a shop with a different name has no history")
        assertEquals(HistoryProfile("Groceries", 2, 1, listOf("Food")), SimilarEntries.profile("Instamart", rows, joined), "and the reverse")
        assertEquals("Groceries", SimilarEntries.topCategory("Instamart", rows, joined))
    }

    @Test
    fun theBuiltInTableMakesTheHistoryOfTheNamesOfOneMerchantOne() {
        fun c(id: String, label: String, cat: String) = SimilarCandidate(id, label, cat, 10_000, 1)
        val rows = listOf(c("1", "Prime Video", "Subscriptions"), c("2", "Amazon Prime", "Subscriptions"), c("3", "Amazon Prime Video", "Subscriptions"), c("4", "Zomato", "Food"))
        assertEquals(HistoryProfile("Subscriptions", 3, 0), SimilarEntries.profile("Amazon Prime", rows))
        assertEquals(HistoryProfile("Subscriptions", 3, 0), SimilarEntries.profile("Prime Video", rows))
    }

    @Test
    fun theSubscriptionFinderCountsJoinedNamesAsOneMerchantAndOnlyThen() {
        // charged every 30 days, the bank writing the name two ways in turn: each name alone is charged every 60 days (no pattern)
        val samples = (0 until 6).map { SubscriptionSample(if (it % 2 == 0) "FitZone" else "FitZone Club Online", 700 + it * 30, 99_900) }
        assertTrue(SubscriptionFinder.find(samples, 880).isEmpty(), "not joined: two names with three charges 60 days apart each")
        val joined = PayeeAliases(listOf(PayeeAlias(PayeeKey.of("FitZone Club Online")!!, PayeeKey.of("FitZone")!!, "FitZone Club Online", "FitZone")))
        val found = SubscriptionFinder.find(samples, 880, joined)
        assertEquals(1, found.size, "joined: six charges 30 days apart")
        assertEquals(6, found.single().count); assertEquals(30, found.single().cadenceDays)
        assertEquals("fitzone", found.single().merchantKey, "counted under the name the person gave the shop")
        assertEquals(SubscriptionFinder.keyOf("FitZone"), SubscriptionFinder.keyOf("FitZone Club Online", joined))
        assertTrue(SubscriptionFinder.keyOf("FitZone Club Online") != SubscriptionFinder.keyOf("FitZone"))
    }

    @Test
    fun theBuiltInTableIsNotUsedByTheFinderSoAHiddenSubscriptionDoesNotComeBack() {
        assertTrue(SubscriptionFinder.keyOf("Prime Video") != SubscriptionFinder.keyOf("Amazon Prime"))
        assertEquals("prime video", SubscriptionFinder.keyOf("Prime Video"))
    }
}
