package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PayeeWordsTest {
    private val byName = StarterCategories.expenseOrder.associate { it.lowercase() to StarterCategories.expenseId(it) }
    private val usable = byName.values.toSet()
    private fun guess(payee: String, have: Set<String> = usable, names: Map<String, String> = byName) = CategorySuggest.suggest(payee, "x", emptyList(), emptyMap(), have, names)

    @Test
    fun theWordsOfAShopNameSayWhatItIs() {
        assertEquals("Food", PayeeWords.categoryNamesFor("SAI BABA TEA STALL").first())
        assertEquals("Health", PayeeWords.categoryNamesFor("NEW BOMBAY MEDICALS").first())
        assertEquals("Health", PayeeWords.categoryNamesFor("SUNRISE HOSPITAL PHARMACY").first())
        assertEquals("Groceries", PayeeWords.categoryNamesFor("VIJAYA FRUITS AND VEG").first())
        assertEquals("Insurance", PayeeWords.categoryNamesFor("HDFC ERGO GENERAL INSURANCE").first())
        assertEquals("Loan EMI", PayeeWords.categoryNamesFor("BAJAJ FINSERV EMI").first())
        assertEquals("Fuel", PayeeWords.categoryNamesFor("SHELL PETROL PUMP").first())
    }

    @Test
    fun wordsOfTwoCategoriesGiveNothingAndWordsInsideWordsAreNotRead() {
        assertNull(PayeeWords.match("TEA ESTATE HOSPITAL"))
        assertNull(PayeeWords.match("COFFEEHOUSE"), "whole words only")
        assertNull(PayeeWords.match("RAVI KUMAR"))
        assertNull(PayeeWords.match("FRESHPIK"))
    }

    @Test
    fun itIsOnlyAGuessAfterEverythingTheStarterListAndThePersonKnow() {
        assertEquals(CategorySuggestion("Food", SuggestionSource.KEYWORD), guess("SAI BABA TEA STALL", names = mapOf("food" to "Food"), have = setOf("Food")))
        assertEquals(CategorySuggestion("Food", SuggestionSource.STARTER), guess("Starbucks", names = mapOf("food" to "Food"), have = setOf("Food")), "a merchant the list knows is not a guess")
        assertEquals(CategorySuggestion("X", SuggestionSource.RULE), CategorySuggest.suggest("SAI BABA TEA STALL", "x", listOf(CaptureRule("r", "sai baba", "X")), emptyMap(), setOf("X", "Food"), mapOf("food" to "Food")))
        assertEquals(CategorySuggestion("T", SuggestionSource.LEARNED), CategorySuggest.suggest("SAI BABA TEA STALL", "x", emptyList(), mapOf("saibabateastall" to "T"), setOf("T", "Food"), mapOf("food" to "Food")))
    }

    @Test
    fun aPersonWithoutTheNewerCategoryGetsTheCoarseOneOrNothing() {
        val old = mapOf("food" to "Food", "bills" to "Bills", "transport" to "Transport", "health" to "Health")
        assertEquals("Bills", guess("HDFC ERGO GENERAL INSURANCE", old.values.toSet(), old)?.categoryId)
        assertEquals("Transport", guess("SHELL PETROL PUMP", old.values.toSet(), old)?.categoryId)
        assertNull(guess("SAI BABA SCHOOL", old.values.toSet(), old), "no Education and no Other: nothing")
    }
}
