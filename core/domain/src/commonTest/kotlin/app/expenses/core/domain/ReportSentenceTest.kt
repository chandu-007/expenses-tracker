package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ReportSentenceTest {
    @Test fun aCategoryIsNamedWithItsAmountAndShareAndTheTotalIsNotRepeated() {
        assertEquals("Bills took ₹24,649.00, 70% of what you spent.", ReportSentence.headline("category", "Bills", 70, "₹24,649.00"))
        assertEquals("Rent took ₹18,000.00, 66% of what you spent.", ReportSentence.headline("category", "Rent", 66, "₹18,000.00"), "a plural or a singular name reads the same")
    }
    @Test fun oneCategoryTakingEverythingSaysAll() {
        assertEquals("All of what you spent went to Food (₹500.00).", ReportSentence.headline("category", "Food", 100, "₹500.00"))
    }
    @Test fun aShareUnderOnePercentIsNotZero() {
        assertEquals("Chai took ₹12.00, less than 1% of what you spent.", ReportSentence.headline("merchant", "Chai", 0, "₹12.00"))
    }
    @Test fun aTagIsNotAShareOfTheWholeBecauseAnEntryCountsUnderEveryTag() {
        assertEquals("The most is under the tag trip: ₹900.00.", ReportSentence.headline("tag", "trip", 90, "₹900.00"))
    }
    @Test fun anAccountAndAMerchantReadNaturally() {
        assertEquals("60% of what you spent (₹600.00) was paid from Cash.", ReportSentence.headline("account", "Cash", 60, "₹600.00"))
        assertEquals("Swiggy took ₹250.00, 25% of what you spent.", ReportSentence.headline("merchant", "Swiggy", 25, "₹250.00"))
    }
    @Test fun noNameOrNoAmountGivesNothingOrTheShortForm() {
        assertEquals("", ReportSentence.headline("category", null, 0, ""))
        assertEquals("", ReportSentence.headline("category", "  ", 10, ""))
        assertEquals("Fun is 10% of what you spent.", ReportSentence.headline("category", "Fun", 10, ""))
        assertEquals("All of what you spent went to Fun.", ReportSentence.headline("category", "Fun", 100, ""))
    }
    private val rupees = { m: Long -> Money(m, Currency.INR).formatted() }
    @Test fun aComparisonOfAnOpenPeriodSaysSoFarAndHasHomesPercent() {
        val less = ReportSentence.comparison(4_930_500, 4_979_700, "Oct", true, rupees)!!
        assertEquals("▼ ₹492.00 less than Oct so far (<1%)", less.text); assertEquals(false, less.more)
        val more = ReportSentence.comparison(50_000, 30_000, "Oct", true, rupees)!!
        assertEquals("▲ ₹200.00 more than Oct so far (+66%)", more.text); assertEquals(true, more.more)
        assertEquals("▼ ₹300.00 less than Oct so far (−30%)", ReportSentence.comparison(70_000, 100_000, "Oct", true, rupees)!!.text)
    }
    @Test fun aFinishedPeriodIsComparedWithoutSoFar() {
        assertEquals("▲ ₹200.00 more than 2025 (+66%)", ReportSentence.comparison(50_000, 30_000, "2025", false, rupees)!!.text)
    }
    @Test fun refundsThatTakeTheTotalBelowNothingAreCappedAtAHundredPercent() {
        assertEquals("▼ ₹600.00 less than Oct so far (−100%)", ReportSentence.comparison(-30_000, 30_000, "Oct", true, rupees)!!.text)
    }
    @Test fun theSameOrNothingToCompareWith() {
        val same = ReportSentence.comparison(100, 100, "Oct", true, rupees)!!
        assertEquals("Same as Oct so far", same.text); assertEquals(null, same.more)
        assertEquals("Same as Oct", ReportSentence.comparison(100, 100, "Oct", false, rupees)!!.text)
        assertEquals(null, ReportSentence.comparison(100, 0, "Oct", true, rupees))
        assertEquals(null, ReportSentence.comparison(100, -5, "Oct", false, rupees))
    }
    @Test fun aTrendBarReadsWithItsChange() {
        assertEquals("Nov: ₹34,780.00. ₹22,350.00 more than Oct.", ReportSentence.trendBar("Nov", "₹34,780.00", ReportSentence.change("₹22,350.00", 1, "Oct") + "."))
        assertEquals("Oct: ₹1.00", ReportSentence.trendBar("Oct", "₹1.00", null))
        assertEquals("₹5.00 less than Sep", ReportSentence.change("₹5.00", -1, "Sep"))
        assertEquals("The same as Sep", ReportSentence.change("₹0.00", 0, "Sep"))
        assertEquals(null, ReportSentence.change("₹0.00", 1, ""))
    }
}
