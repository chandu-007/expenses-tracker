package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The newer starter categories and the merchants that now have a better home in them, without changing what the table always said for a person who has not got those categories. */
class MerchantHomesTest {
    private val more = StarterCategories.EXPENSE_MORE.toSet()
    private val first = StarterCategories.EXPENSE_FIRST

    @Test
    fun everyHomeIsANewerStarterCategoryEveryFallbackIsAnOriginalOneAndEveryExistingMerchantNamedExists() {
        val names = MerchantTable.all.mapNotNull { it.display }.toSet()
        assertEquals(emptyList(), MerchantTable.HOMES.keys.filter { it !in names }, "a home is given to a merchant the table does not have")
        val homes = MerchantTable.all.filter { it.home != null }
        assertTrue(homes.size >= 150, "only ${homes.size} merchants have a home")
        assertEquals(emptyList(), homes.filter { it.home !in more }.map { it.display ?: it.aliases.first() }, "a home outside the newer starter categories")
        assertEquals(emptyList(), MerchantTable.all.filter { it.category != null && it.category !in MerchantTable.CATEGORIES }.map { it.display }, "a fallback outside the original starters")
        // every newer starter category is the home of something, so a person who has it sees it suggested
        for (c in StarterCategories.EXPENSE_MORE) if (c != "Fees and charges" && c != "Cash withdrawal") assertTrue(homes.any { it.home == c }, "nothing is filed under $c")
    }

    @Test
    fun everyExistingMerchantKeepsItsOldCategoryForAPersonWithoutTheNewerOnesAndGetsTheBetterHomeWithThem() {
        val table = mapOf(
            "NETFLIX" to ("Fun" to "Subscriptions"), "Spotify" to ("Fun" to "Subscriptions"), "iCloud" to ("Fun" to "Subscriptions"), "Indian Oil" to ("Transport" to "Fuel"), "HPCL" to ("Transport" to "Fuel"),
            "IndiGo" to ("Transport" to "Travel"), "IRCTC" to ("Transport" to "Travel"), "MakeMyTrip" to ("Transport" to "Travel"), "FASTag" to ("Transport" to null), "Uber" to ("Transport" to null),
            "BESCOM" to ("Bills" to "Utilities"), "Bharat Gas" to ("Bills" to "Utilities"), "Jio" to ("Bills" to "Internet and mobile"), "Airtel" to ("Bills" to "Internet and mobile"), "ACT Fibernet" to ("Bills" to "Internet and mobile"),
            "LIC" to ("Bills" to "Insurance"), "HDFC Life" to ("Bills" to "Insurance"), "Star Health" to ("Health" to "Insurance"), "Swiggy" to ("Food" to null), "Amazon" to ("Shopping" to null), "Dream11" to ("Fun" to null),
        )
        for ((payee, want) in table) {
            val (old, home) = want
            assertEquals(old, DefaultRules.categoryNameFor(payee), "$payee: the old mapping is unchanged")
            assertEquals(listOfNotNull(home, old), DefaultRules.categoryNamesFor(payee), "$payee: the better home first, then the old one")
        }
        assertEquals(emptyList(), DefaultRules.categoryNamesFor("Chandu"))
    }

    private val originals = first.associateBy { it.lowercase() }
    private val all = originals + StarterCategories.EXPENSE_MORE.associate { it.lowercase() to "id:$it" }
    private fun suggest(payee: String, byName: Map<String, String>) =
        CategorySuggest.suggest(payee, "x", emptyList(), emptyMap(), byName.values.toSet(), byName)?.categoryId

    @Test
    fun theSuggestionTakesTheBetterHomeWhenThePersonHasItAndTheOldOneWhenNot() {
        val oldLedger = originals.mapValues { it.value }                    // an existing install: the eight originals only
        val newLedger = all                                                  // a new install: the originals and the newer ones
        assertEquals("Fun", suggest("Netflix", oldLedger), "an existing ledger is suggested what it always was")
        assertEquals("id:Subscriptions", suggest("Netflix", newLedger))
        assertEquals("Transport", suggest("Indian Oil", oldLedger)); assertEquals("id:Fuel", suggest("Indian Oil", newLedger))
        assertEquals("Bills", suggest("BESCOM", oldLedger)); assertEquals("id:Utilities", suggest("BESCOM", newLedger))
        // a person who added only Subscriptions from the Suggested screen gets only that one moved
        val some = oldLedger + ("subscriptions" to "Subscriptions")
        assertEquals("Subscriptions", suggest("Spotify", some)); assertEquals("Bills", suggest("Airtel", some))
        // a hidden or removed newer category is not in the set the caller passes, so it falls back; a new-only merchant with no fallback suggests nothing
        assertEquals("Fun", suggest("Netflix", newLedger - "subscriptions"))
        assertNull(suggest("Zerodha", oldLedger)); assertEquals("id:Investments", suggest("Zerodha", newLedger))
    }

    @Test
    fun theNewMerchantsAreFoundByTheNamesBanksPrintAndGetACleanName() {
        val table = listOf(
            // payee as a bank prints it, the clean name, the home
            Triple("APPLE.COM/BILL", "Apple Services", "Subscriptions"), Triple("APPLE SERVICES", "Apple Services", "Subscriptions"), Triple("ITUNES.COM/BILL", "Apple Services", "Subscriptions"),
            Triple("GOOGLE *YouTubePremium", "YouTube Premium", "Subscriptions"), Triple("GOOGLE *Google One", "Google One", "Subscriptions"), Triple("NETFLIX.COM", "Netflix", "Subscriptions"),
            Triple("SPOTIFY IN", "Spotify", "Subscriptions"), Triple("AMAZON PRIME", "Amazon Prime", "Subscriptions"), Triple("AMZN PRIME", "Amazon Prime", "Subscriptions"),
            Triple("OPENAI *CHATGPT SUBSCR", "ChatGPT", "Subscriptions"), Triple("ADOBE SYSTEMS SOFTWARE", "Adobe", "Subscriptions"), Triple("Disney+ Hotstar", "Disney+ Hotstar", "Subscriptions"),
            Triple("Zerodha Broking Ltd", "Zerodha", "Investments"), Triple("Bajaj Finance Limited", "Bajaj Finance", "Loan EMI"), Triple("Income Tax Department", "Income Tax Department", "Taxes"),
            Triple("Urban Company", "Urban Company", "Personal care"), Triple("BYJU'S", "BYJU'S", "Education"), Triple("NoBrokerHood", "NoBrokerHood", "Rent"), Triple("Indian Oil Corporation", "Indian Oil", "Fuel"),
        )
        for ((payee, display, home) in table) {
            assertEquals(display, DefaultRules.displayNameFor(payee), payee)
            assertEquals(home, DefaultRules.categoryNamesFor(payee).first(), payee)
        }
    }

    @Test
    fun aPersonWhoseNameMerelyStartsWithANewMerchantIsStillAPerson() {
        for (p in listOf("Adobe Sharma", "Notion Rao", "Yatra Singh", "Agoda Kumar", "Cry Baby Toys", "Canva Reddy", "Scaler Joshi")) assertEquals(emptyList(), DefaultRules.categoryNamesFor(p), p)
    }
}
