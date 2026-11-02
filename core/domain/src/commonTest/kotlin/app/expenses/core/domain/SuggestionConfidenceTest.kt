package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestionConfidenceTest {
    private fun c(source: SuggestionSource, mixed: Boolean = false, person: Boolean = false, uses: Int = 1, history: HistoryProfile? = null, suggested: String = "Food") =
        SuggestionConfidence.of(source, mixed, person, uses, history, suggested)

    @Test
    fun theirOwnRuleAndABanksChargeWordsAreSure() {
        assertEquals(Certainty.SURE, c(SuggestionSource.RULE, mixed = true, person = true))
        assertEquals(Certainty.SURE, c(SuggestionSource.WORDS))
    }

    @Test
    fun aGuessFromANameAndAPaymentToAPersonAreAlwaysLookedAt() {
        assertEquals(Certainty.CHECK, c(SuggestionSource.KEYWORD))
        assertEquals(Certainty.CHECK, c(SuggestionSource.PERSON))
        assertEquals(Certainty.CHECK, c(SuggestionSource.KEYWORD, history = HistoryProfile("Food", 5, 0)))
    }

    @Test
    fun aStarterMerchantIsSureUnlessItSellsManyThings() {
        assertEquals(Certainty.SURE, c(SuggestionSource.STARTER))
        assertEquals(Certainty.CHECK, c(SuggestionSource.STARTER, mixed = true))
    }

    @Test
    fun aChoiceTheyMadeIsSureForAShopAndNeedsTwoForAPersonOrAMarketplace() {
        assertEquals(Certainty.SURE, c(SuggestionSource.LEARNED))
        assertEquals(Certainty.CHECK, c(SuggestionSource.LEARNED, person = true))
        assertEquals(Certainty.CHECK, c(SuggestionSource.LEARNED, mixed = true))
        assertEquals(Certainty.SURE, c(SuggestionSource.LEARNED, person = true, uses = 2))
        assertEquals(Certainty.SURE, c(SuggestionSource.LEARNED, mixed = true, history = HistoryProfile("Food", 2, 0)))
    }

    @Test
    fun aChoiceThatTheirHistoryContradictsIsLookedAt() {
        assertEquals(Certainty.CHECK, c(SuggestionSource.LEARNED, history = HistoryProfile("Groceries", 3, 1), suggested = "Food"))
        assertEquals(Certainty.CHECK, c(SuggestionSource.LEARNED, uses = 4, history = HistoryProfile("Food", 2, 2), suggested = "Food"), "as many against as for")
        assertEquals(Certainty.SURE, c(SuggestionSource.LEARNED, history = HistoryProfile("Food", 5, 1), suggested = "Food"), "one stray entry among five does not undo it")
    }

    @Test
    fun theirHistoryAloneIsSureWithTwoAgreeingEntriesAndNoneAgainst() {
        assertEquals(Certainty.CHECK, c(SuggestionSource.HISTORY, history = HistoryProfile("Food", 1, 0)))
        assertEquals(Certainty.SURE, c(SuggestionSource.HISTORY, history = HistoryProfile("Food", 2, 0)))
        assertEquals(Certainty.CHECK, c(SuggestionSource.HISTORY, history = HistoryProfile("Food", 4, 1)))
        assertEquals(Certainty.CHECK, c(SuggestionSource.HISTORY, person = true, history = HistoryProfile("Food", 2, 0)))
        assertEquals(Certainty.SURE, c(SuggestionSource.HISTORY, person = true, history = HistoryProfile("Food", 3, 0)))
        assertEquals(Certainty.CHECK, c(SuggestionSource.HISTORY, history = null))
    }
}
