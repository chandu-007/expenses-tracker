package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryHintsTest {
    @Test
    fun aBanksOwnChargesAreFeesAndOrdinaryPaymentsAreNot() {
        for (t in listOf(
            "Rs.590.00 debited from A/c XX1234 on 05-10-26 towards annual fee for your debit card",
            "Rs.17.70 debited from A/c XX1234: SMS charges for the quarter",
            "ATM charges of Rs.21.00 debited from A/c XX1234 on 05-10-26",
            "Rs.236.00 debited: Non-maintenance charges for minimum balance",
            "GST on charges Rs.3.18 debited from A/c XX1234",
            "Late payment fee of Rs.500 charged on your card",
            "Fee debited Rs.100 from A/c XX1234",
        )) assertEquals("Fees and charges", CategoryHints.categoryNameFor(t), t)
        for (t in listOf("Rs.250.00 debited from A/c XX1234 on 05-10-26 to VPA swiggy@icici (UPI Ref 100000000101)", "Spent Rs.100 On HDFC Bank Card 0000 At URBAN BISTRO", "Rs.5,000.00 credited to A/c XX1234 for the service you did", "Your fee payment for school of Rs 5000 is due"))
            assertNull(CategoryHints.categoryNameFor(t), t)
    }

    @Test
    fun theFeeCategoryIsSuggestedOnlyWhenThePersonHasItAndAfterEverythingTheyChoseOrMerchantsKnow() {
        val byName = mapOf("fees and charges" to "F", "other" to "O", "food" to "Food")
        fun s(payee: String?, body: String, usable: Set<String> = byName.values.toSet(), learned: Map<String, String> = emptyMap()) = CategorySuggest.suggest(payee, body, emptyList(), learned, usable, byName)
        assertEquals(CategorySuggestion("F", SuggestionSource.WORDS), s(null, "Annual fee of Rs.500 debited"))
        assertNull(s(null, "Annual fee of Rs.500 debited", usable = setOf("O", "Food")), "no such category: no suggestion")
        assertEquals(CategorySuggestion("Food", SuggestionSource.STARTER), s("Swiggy", "Annual fee of Rs.500 debited"), "a merchant is judged before the words")
        assertEquals(CategorySuggestion("X", SuggestionSource.LEARNED), s("Some Bank", "Annual fee debited", learned = mapOf("somebank" to "X"), usable = setOf("F", "X")), "what the person chose wins")
    }
}
