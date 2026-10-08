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
}
