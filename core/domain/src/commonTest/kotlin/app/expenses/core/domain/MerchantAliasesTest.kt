package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The built-in same-merchant table. All names are public brand names; nothing here is from anyone's transactions. It must merge what is one merchant and never what only looks alike:
 * positives (every group's wordings reach ONE key) and 40+ false friends (names that share words with a merchant and must stay apart).
 */
class MerchantAliasesTest {
    private fun key(p: String) = PayeeKey.of(p)

    @Test
    fun everyGroupMemberIsInTheStarterListWithTheSameCategoryAndTheSameHomeAsItsCanonicalMerchant() {
        val byDisplay = MerchantTable.all.filter { it.display != null }.associateBy { it.display!! }
        for ((head, others) in MerchantAliases.groups) {
            val h = byDisplay[head] ?: error("$head is not a merchant of the starter list")
            for (o in others) {
                val m = byDisplay[o] ?: error("$o is not a merchant of the starter list")
                assertEquals(h.category, m.category, "$o must be filed like $head")
                assertEquals(h.home, m.home, "$o must have the same better home as $head")
            }
        }
        assertTrue(MerchantAliases.groups.size >= 6, "the table has its groups")
    }

    @Test
    fun noMemberIsInTwoGroupsAndNoCanonicalMerchantIsAlsoAMember() {
        val all = MerchantAliases.groups.flatMap { (h, o) -> listOf(h) + o }
        assertEquals(all.size, all.distinct().size, "a name is in one group only")
        for ((head, _) in MerchantAliases.groups) assertEquals(CategorySuggest.merchantKey(head), MerchantAliases.canonicalKey(CategorySuggest.merchantKey(head)), "$head is its own canonical")
    }

    @Test
    fun everyWordingOfOneMerchantReachesOneKey() {
        val sets = listOf(
            listOf("Amazon", "AMZN", "AMZN Mktp IN", "amazon.in", "Amazon Seller Services", "Amazon Pay", "AMAZON PAY INDIA PRIVATE LIMITED", "amazonpay@apl", "Amazon Payments"),
            listOf("Amazon Prime", "AMAZON PRIME", "Prime Video", "Amazon Prime Video", "amzn prime"),
            listOf("JioHotstar", "Jio Hotstar", "JioCinema", "Jio Cinema", "Hotstar", "Disney+ Hotstar", "disney plus hotstar", "Voot"),
            listOf("FASTag", "NETC FASTAG", "Paytm FASTag", "paytm fastag"),
            listOf("PVR", "PVR INOX", "PVR Cinemas", "INOX", "INOX Leisure", "inox movies"),
            listOf("Air India", "airindia", "Air India Express", "Vistara", "Tata SIA Airlines"),
            listOf("ixigo", "IXIGO Trains"),
        )
        for (s in sets) {
            val keys = s.map { it to key(it) }
            assertTrue(keys.all { it.second != null && it.second == keys.first().second }, "one key for all of: $keys")
        }
        assertEquals("amazon", key("Amazon Pay")); assertEquals("jiohotstar", key("JioCinema")); assertEquals("pvr", key("INOX")); assertEquals("airindia", key("Vistara"))
    }

    @Test
    fun theStarterCategoryOfAMergedMerchantIsUnchanged() {
        // merging pools what is learned; it never moves what the starter list suggests
        for (n in listOf("Amazon Pay", "Prime Video", "JioCinema", "Disney+ Hotstar", "Voot", "Paytm FASTag", "INOX", "Vistara", "IXIGO Trains")) {
            val head = MerchantAliases.groups.first { (h, o) -> n == h || n in o }.first
            assertEquals(DefaultRules.categoryNamesFor(head), DefaultRules.categoryNamesFor(n), "$n suggests what $head suggests")
        }
    }

    /** Two names that share a word, a company or a sound with a merchant and are NOT the same merchant (or not that merchant at all). */
    private val falseFriends = listOf(
        "Swiggy" to "Swiggy Instamart", "Swiggy" to "Swiggy Dineout", "Swiggy" to "Swiggy Kumar", "Amazon" to "Amazon Prime", "Amazon" to "Amazon Fresh", "Amazon" to "Prime Video",
        "Amazon Pay" to "Amazon Prime", "Amazon" to "Amazonian Spa", "Amazon" to "Amazon Music", "Uber" to "Uber Eats", "Uber" to "Uber Singh", "Uber" to "Uberti Shoes",
        "Jio" to "Jio Mart", "Jio" to "JioMart", "Jio" to "Jio-bp", "Jio" to "JioFiber", "Jio" to "Jio Rao", "Airtel" to "Airtel Xstream", "Airtel" to "Airtel Payments Bank",
        "Apollo" to "Apollo Tyres Ltd", "Apollo" to "Apollo Tyres", "Tata Power" to "Tata Play", "Tata Play" to "Tata AIA", "Max Life" to "Max Healthcare", "Max Healthcare" to "Max Fashion",
        "Star Health" to "Star Bazaar", "Care Health Insurance" to "Care Hospitals", "Indian Oil" to "Indane", "Zepto" to "Zepto Cafe", "Apple Music" to "Apple Store",
        "Google Play" to "Google Pay", "Paytm" to "Paytm FASTag", "Paytm" to "Paytm Insider", "Paytm" to "Paytm Money", "Ola" to "Ola Sharma", "Ola" to "Olaf Traders",
        "PVR" to "Pvr Tyres", "PVR" to "Pvr Kumar", "INOX" to "Inox Steel", "Air India" to "Air Indiana", "Vistara" to "Vistara Constructions", "FASTag" to "Fastag Kumar",
        "JioHotstar" to "Jio Hotstar Kumar", "JioCinema" to "Cinepolis", "Disney+ Hotstar" to "Disneyland Tours", "ixigo" to "ixigo Singh", "Voot" to "Voot Kumar",
        "Prime Video" to "Primevideo Hotel Kumar", "Amazon Prime" to "Amazon Prime Kumar", "Amazon Pay" to "Pay Amazonas", "Hotstar" to "Star Health",
    )

    @Test
    fun fortyFalseFriendsAreNeverMerged() {
        assertTrue(falseFriends.size >= 40, "at least 40 pairs, have ${falseFriends.size}")
        val merged = falseFriends.filter { (a, b) -> key(a) != null && key(a) == key(b) }
        println("MERCHANT-ALIASES false friends: ${falseFriends.size} pairs, merged wrongly ${merged.size}${if (merged.isEmpty()) "" else ": $merged"}")
        assertEquals(emptyList(), merged, "these must never share a key")
    }

    @Test
    fun aNameThatOnlyStartsWithAMergedMerchantIsStillNobodyElsesKey() {
        // Uber Singh is a person: no merchant key of Uber, and no built-in group either
        assertNotEquals(key("Uber"), key("Uber Singh"))
        assertEquals(false, MerchantAliases.inGroup(key("Uber Singh")!!))
        assertEquals(false, MerchantAliases.inGroup(key("Apollo Tyres")!!))
        assertEquals(false, MerchantAliases.inGroup(key("Jio Mart")!!))
    }

    @Test
    fun theLegalNamesAddedToTheTableReadAsTheirMerchantAndNothingElseDoes() {
        val cases = mapOf(
            "INNOVATIVE RETAIL CONCEPTS PVT LTD" to "BigBasket", "Delightful Gourmet Pvt Ltd" to "Licious", "FASHNEAR TECHNOLOGIES PRIVATE LIMITED" to "Meesho",
            "Oravel Stays Limited" to "OYO", "AMZN Mktp IN" to "Amazon",
        )
        for ((p, d) in cases) assertEquals(d, DefaultRules.displayNameFor(p), p)
        for (p in listOf("Innovative Retail Concepts Kumar", "Delightful Gourmet Sharma", "Oravel Hotels Sharma", "Mktp Kumar", "AMZN Sharma")) assertEquals(null, DefaultRules.displayNameFor(p), p)
    }
}
