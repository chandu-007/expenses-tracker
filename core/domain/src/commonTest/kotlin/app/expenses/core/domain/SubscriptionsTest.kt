package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionsTest {
    private fun monthly(name: String, amount: Long, firstDay: Int, times: Int, step: Int = 30) = (0 until times).map { SubscriptionSample(name, firstDay + it * step, amount) }
    private val today = 1000

    @Test
    fun aSteadyMonthlyChargeIsFoundWithItsNextDateAndWhatItCostsAYear() {
        val s = SubscriptionFinder.find(monthly("Netflix", 64_900, 880, 4), today).single()
        assertEquals("Netflix", s.name); assertEquals(30, s.cadenceDays); assertEquals(4, s.count)
        assertEquals(970, s.lastDay); assertEquals(1000, s.nextDay)
        assertEquals(64_900L * 12, s.yearlyMinor, "a monthly charge is paid twelve times a year"); assertEquals(64_900L, s.monthlyMinor)
    }

    @Test
    fun theSameMerchantInOtherLettersCountsAsOneAndAGapOfThirtyOneDaysStillFits() {
        val samples = listOf(SubscriptionSample("Spotify", 880, 11_900), SubscriptionSample(" spotify ", 911, 11_900), SubscriptionSample("SPOTIFY", 942, 11_900), SubscriptionSample("Spotify", 972, 11_900))
        assertEquals(1, SubscriptionFinder.find(samples, today).size)
    }

    @Test
    fun billsThatVaryAndPurchasesThatAreNotSteadyAndTooFewAreNotSubscriptions() {
        assertTrue(SubscriptionFinder.find(listOf(SubscriptionSample("BESCOM", 880, 120_000), SubscriptionSample("BESCOM", 910, 310_000), SubscriptionSample("BESCOM", 940, 90_000), SubscriptionSample("BESCOM", 970, 250_000)), today).isEmpty(), "the amount changes too much")
        assertTrue(SubscriptionFinder.find(listOf(SubscriptionSample("Cafe", 900, 20_000), SubscriptionSample("Cafe", 903, 20_000), SubscriptionSample("Cafe", 951, 20_000), SubscriptionSample("Cafe", 999, 20_000)), today).isEmpty(), "the gaps are not steady")
        assertTrue(SubscriptionFinder.find(monthly("Gym", 100_000, 940, 2), today).isEmpty(), "two charges are not yet a pattern")
        assertTrue(SubscriptionFinder.find(listOf(SubscriptionSample("", 910, 1), SubscriptionSample("", 940, 1), SubscriptionSample("", 970, 1)), today).isEmpty(), "no merchant")
    }

    @Test
    fun aChargeThatStoppedIsLeftOutAndAPriceRiseWithinFifteenPercentIsKept() {
        assertTrue(SubscriptionFinder.find(monthly("Old gym", 100_000, 700, 4), today).isEmpty(), "last paid 190 days ago")
        val rise = listOf(SubscriptionSample("Prime", 880, 100_000), SubscriptionSample("Prime", 910, 100_000), SubscriptionSample("Prime", 940, 100_000), SubscriptionSample("Prime", 970, 110_000))
        assertEquals(110_000, SubscriptionFinder.find(rise, today).single().amountMinor, "the latest price is shown")
    }

    @Test
    fun everyThreeMonthsAndYearlyAreFoundAndTheDearestComesFirst() {
        val quarterly = monthly("Insurance", 500_000, 727, 4, 91)
        val yearly = listOf(SubscriptionSample("Domain", 270, 120_000), SubscriptionSample("Domain", 635, 120_000))
        val found = SubscriptionFinder.find(quarterly + yearly + monthly("Netflix", 64_900, 880, 4), 1000)
        assertEquals(listOf("Insurance", "Netflix", "Domain"), found.map { it.name }, "by what they cost a year: 20,000, 7,788, 1,200")
        assertEquals(listOf(91, 30, 365), found.map { it.cadenceDays })
    }

    @Test
    fun aQuarterlyChargeCostsFourTimesAYearAndAThirdOfItEachMonth() {
        val q = SubscriptionFinder.find(monthly("Insurance", 600_000, 727, 4, 91), 1000).single()
        assertEquals(2_400_000L, q.yearlyMinor); assertEquals(200_000L, q.monthlyMinor)
        val y = SubscriptionFinder.find(listOf(SubscriptionSample("Domain", 270, 120_000), SubscriptionSample("Domain", 635, 120_000)), 1000).single()
        assertEquals(120_000L, y.yearlyMinor); assertEquals(10_000L, y.monthlyMinor)
    }
}
