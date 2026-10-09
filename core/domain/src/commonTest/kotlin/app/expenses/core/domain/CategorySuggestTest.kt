package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategorySuggestTest {
    private val usable = setOf("Food", "Transport", "Fun", "Bills")
    private fun rule(id: String, p: String, c: String) = CaptureRule(id, p, c)

    @Test
    fun theSamePayeeWrittenDifferentlyHasOneKeyAndAUpiHandleIsDropped() {
        for (p in listOf("Swiggy", " SWIGGY ", "swiggy.", "Swig-gy", "swiggy@icici", "SWIGGY@ybl")) assertEquals("swiggy", CategorySuggest.merchantKey(p), p)
        assertEquals("ravik", CategorySuggest.merchantKey("ravi.k@ybl"))
        assertEquals("", CategorySuggest.merchantKey("@@"))
    }

    @Test
    fun aRuleMatchesWhenEveryWordIsPresentInAnyOrderAndCase() {
        assertTrue(rule("r", "uber trip", "Transport").matches("UBER INDIA payment for your trip"))
        assertTrue(!rule("r", "uber trip", "Transport").matches("Uber payment"))
        assertTrue(rule("r", "zomato, swiggy", "Food").matches("Paid to zomato and swiggy"))
        assertFailsWith<IllegalArgumentException> { rule("r", "  a ", "Food") }
        assertFailsWith<IllegalArgumentException> { rule("r", "uber", "") }
    }

    @Test
    fun theFirstMatchingRuleWinsInTheOrderGivenAndRulesBeatWhatWasLearned() {
        val rules = listOf(rule("1", "uber", "Transport"), rule("2", "uber eats", "Food"))
        assertEquals(CategorySuggestion("Transport", SuggestionSource.RULE), CategorySuggest.suggest("Uber Eats", "debited", rules, emptyMap(), usable), "order matters: the person put the broad rule first")
        assertEquals(CategorySuggestion("Food", SuggestionSource.RULE), CategorySuggest.suggest("Uber Eats", "debited", rules.reversed(), emptyMap(), usable))
        assertEquals(CategorySuggestion("Transport", SuggestionSource.RULE), CategorySuggest.suggest("uber", "x", rules, mapOf("uber" to "Fun"), usable), "a rule beats the learned choice")
    }

    @Test
    fun aLearnedPayeeIsSuggestedUntilNoRuleAppliesAndOnlyWithARealKey() {
        val learned = mapOf("swiggy" to "Food")
        assertEquals(CategorySuggestion("Food", SuggestionSource.LEARNED), CategorySuggest.suggest("SWIGGY@icici", "x", emptyList(), learned, usable))
        assertNull(CategorySuggest.suggest("Dominos", "x", emptyList(), learned, usable))
        assertNull(CategorySuggest.suggest(null, "swiggy order", emptyList(), learned, usable), "no payee, no learned lookup: the body is boilerplate")
        assertNull(CategorySuggest.suggest("a", "x", emptyList(), mapOf("a" to "Food"), usable), "a one-letter key is too weak to trust")
    }

    @Test
    fun aCategoryThatIsGoneOrHiddenIsNeverSuggestedEvenIfARuleOrTheMapNamesIt() {
        val rules = listOf(rule("1", "uber", "Deleted"), rule("2", "cab", "Transport"))
        assertEquals(CategorySuggestion("Transport", SuggestionSource.RULE), CategorySuggest.suggest("uber cab", "x", rules, emptyMap(), usable), "the dead rule is skipped, the next one applies")
        assertNull(CategorySuggest.suggest("Uber", "x", listOf(rule("1", "uber", "Deleted")), mapOf("uber" to "Deleted"), usable))
    }

    private val names = mapOf("food" to "F", "groceries" to "G", "transport" to "T", "bills" to "B", "shopping" to "S", "other" to "O")
    private val allUsable = names.values.toSet()
    private fun starter(payee: String?, body: String = "", rules: List<CaptureRule> = emptyList(), learned: Map<String, String> = emptyMap(), person: Boolean = false, used: Set<String> = allUsable, by: Map<String, String> = names) =
        CategorySuggest.suggest(payee, body, rules, learned, used, by, person)

    @Test
    fun aCommonMerchantGetsItsStarterCategoryWholeWordsOnly() {
        assertEquals(CategorySuggestion("F", SuggestionSource.STARTER), starter("swiggy@icici"))
        assertEquals(CategorySuggestion("F", SuggestionSource.STARTER), starter("SWIGGY"))
        assertEquals(CategorySuggestion("G", SuggestionSource.STARTER), starter("Big Basket"))
        assertEquals(CategorySuggestion("T", SuggestionSource.STARTER), starter("OLA"))
        assertEquals(CategorySuggestion("T", SuggestionSource.STARTER), starter("ola@paytm"))
        assertNull(starter("Coca Cola Stall"), "ola inside cola is not Ola")
        assertNull(starter("Olaf Traders"), "a word that only starts like Ola is not Ola")
        assertNull(starter("Chandu"))
        assertNull(starter(null))
    }

    @Test
    fun theStarterListSitsBelowTheOwnRulesAndWhatWasLearnedAndNeverUsesTheBodyOrAMissingCategory() {
        assertEquals(CategorySuggestion("S", SuggestionSource.RULE), starter("swiggy", rules = listOf(CaptureRule("r", "swiggy", "S"))))
        assertEquals(CategorySuggestion("G", SuggestionSource.LEARNED), starter("swiggy", learned = mapOf("swiggy" to "G")))
        assertEquals(null, starter("9704849321@yescred", body = "Rs.100 paid to 9704849321@yescred via Airtel Payments Bank swiggy"), "only the payee is looked at, not the footer")
        assertNull(starter("swiggy", used = allUsable - "F"), "a hidden or deleted category is not suggested")
        assertNull(starter("swiggy", by = names - "food"), "no category with that name: no suggestion")
    }

    @Test
    fun aPaymentToAPersonSuggestsOtherOnlyWhenNothingElseApplies() {
        assertEquals(CategorySuggestion("O", SuggestionSource.PERSON), starter("9704849321@yescred", person = true))
        assertNull(starter("9704849321@yescred", person = false))
        assertEquals(CategorySuggestion("T", SuggestionSource.LEARNED), starter("9704849321@yescred", learned = mapOf("9704849321" to "T"), person = true), "what was chosen for this person wins")
        assertNull(starter("9704849321@yescred", person = true, by = names - "other"))
    }
}
