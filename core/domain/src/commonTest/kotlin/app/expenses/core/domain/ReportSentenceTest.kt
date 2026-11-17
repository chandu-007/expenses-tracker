package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ReportSentenceTest {
    @Test fun aCategoryIsNamedWithItsShareAndTheAmounts() {
        assertEquals("Bills is 70% of what you spent (₹24,649.00 of ₹34,780.00).", ReportSentence.headline("category", "Bills", 70, "₹24,649.00", "₹34,780.00"))
    }
    @Test fun oneCategoryTakingEverythingSaysAll() {
        assertEquals("All of what you spent went to Food (₹500.00 of ₹500.00).", ReportSentence.headline("category", "Food", 100, "₹500.00", "₹500.00"))
    }
    @Test fun aTagIsNotAShareOfTheWholeBecauseAnEntryCountsUnderEveryTag() {
        assertEquals("The most is under the tag trip: ₹900.00.", ReportSentence.headline("tag", "trip", 90, "₹900.00", "₹1,000.00"))
    }
    @Test fun anAccountAndAMerchantReadNaturally() {
        assertEquals("60% of what you spent was paid from Cash (₹600.00 of ₹1,000.00).", ReportSentence.headline("account", "Cash", 60, "₹600.00", "₹1,000.00"))
        assertEquals("Swiggy is 25% of what you spent (₹250.00 of ₹1,000.00).", ReportSentence.headline("merchant", "Swiggy", 25, "₹250.00", "₹1,000.00"))
    }
    @Test fun noNameOrNoAmountsGivesNothingOrTheShortForm() {
        assertEquals("", ReportSentence.headline("category", null, 0, "", ""))
        assertEquals("", ReportSentence.headline("category", "  ", 10, "", ""))
        assertEquals("Fun is 10% of what you spent.", ReportSentence.headline("category", "Fun", 10, "", ""))
    }
    @Test fun aTrendBarReadsWithItsChange() {
        assertEquals("Nov: ₹34,780.00. ₹22,350.00 more than Oct.", ReportSentence.trendBar("Nov", "₹34,780.00", ReportSentence.change("₹22,350.00", 1, "Oct") + "."))
        assertEquals("Oct: ₹1.00", ReportSentence.trendBar("Oct", "₹1.00", null))
        assertEquals("₹5.00 less than Sep", ReportSentence.change("₹5.00", -1, "Sep"))
        assertEquals("The same as Sep", ReportSentence.change("₹0.00", 0, "Sep"))
        assertEquals(null, ReportSentence.change("₹0.00", 1, ""))
    }
}
