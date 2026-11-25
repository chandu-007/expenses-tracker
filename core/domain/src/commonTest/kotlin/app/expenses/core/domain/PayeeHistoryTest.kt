package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PayeeHistoryTest {
    private fun c(id: String, label: String, minor: Long, at: Long, currency: String = "INR", category: String = "Food") = SimilarCandidate(id, label, category, minor, at, currency)

    private val year = 1_000L until 2_000L

    @Test
    fun theYearsVisitsCountTheSamePayeeWrittenDifferentlyAndTheEntryItselfAndAddUpExactly() {
        val all = listOf(
            c("now", "Barbeque Nation", 1_240_00, 1_900), c("a", "BARBEQUE NATION", 1_100_00, 1_500), c("b", "Barbeque Nation Koramangala", 1_320_00, 1_200),
            c("c", "Barbeque Nation", 1_200_00, 1_000, category = ""), // no category still counts as a visit
            c("old", "Barbeque Nation", 999_00, 999), // last year
            c("next", "Barbeque Nation", 1_00, 2_000), // the year after (the end is exclusive)
            c("usd", "Barbeque Nation", 50_00, 1_300, currency = "USD"), // another currency is not added to rupees
            c("other", "Nation Cafe", 300_00, 1_400),
        )
        assertEquals(PayeeHistory.Visits(4, 1_240_00 + 1_100_00 + 1_320_00 + 1_200_00), PayeeHistory.visits("Barbeque Nation", "INR", year.first, year.last + 1, all))
    }

    @Test
    fun oneVisitABlankNameOrADuplicateCandidateSayNothingMore() {
        val one = listOf(c("now", "Zomato", 640_00, 1_500))
        assertNull(PayeeHistory.visits("Zomato", "INR", year.first, year.last + 1, one), "the entry alone is not worth a line")
        assertNull(PayeeHistory.visits("Zomato", "INR", year.first, year.last + 1, one + one), "the same row twice is still one visit")
        assertNull(PayeeHistory.visits("  ", "INR", year.first, year.last + 1, one + c("x", "", 1_00, 1_500)))
    }

    @Test
    fun earlierEntriesOfAPayeeAreCountedWhateverTheirDateCurrencyOrCategory() {
        val all = listOf(c("a", "Zomato", 1_00, 1), c("b", "zomato order", 2_00, 2_000, currency = "USD"), c("c", "ZOMATO", 3_00, 3, category = ""), c("d", "Swiggy", 4_00, 4), c("a", "Zomato", 1_00, 1))
        assertEquals(3, PayeeHistory.earlier("Zomato", all))
        assertEquals(0, PayeeHistory.earlier("Airtel", all))
        assertEquals(0, PayeeHistory.earlier(" ", all))
    }

    @Test
    fun aSameShopGroupTheyMadeCountsAsOnePayee() {
        val aliases = PayeeAliases(listOf(PayeeAlias(PayeeKey.of("Third Wave")!!, PayeeKey.of("TWC")!!)))
        val all = listOf(c("a", "Third Wave", 300_00, 1_100), c("b", "TWC", 250_00, 1_200))
        assertEquals(PayeeHistory.Visits(2, 550_00), PayeeHistory.visits("TWC", "INR", year.first, year.last + 1, all, aliases))
        assertEquals(2, PayeeHistory.earlier("Third Wave", all, aliases))
    }
}
