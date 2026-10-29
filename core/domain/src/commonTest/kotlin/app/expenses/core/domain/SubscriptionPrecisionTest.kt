package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The finder must not invent subscriptions for merchants that people simply use a lot. Each test builds 400 random profiles with fixed seeds (the kinds an independent review measured: a first
 * version of the finder that split merchants into price groups invented rows in 331 of 400 profiles of the first kind, 60% of the second, 16% of the third, 10% of the fourth, and 105 rows on a
 * 20,000-entry ledger; the finder before it, none) and requires exactly zero false rows in every one. Everything is random orders at random amounts: nothing here is a subscription.
 */
class SubscriptionPrecisionTest {
    private val today = LocalDate(2026, 10, 29).toEpochDays().toInt()
    private val shops = listOf("Swiggy", "Zomato", "BigBasket", "Blinkit", "Amazon", "Flipkart", "Myntra", "Zepto", "Dmart", "Croma", "Cafe Coffee Day", "Local Kirana", "Fresh Milk Dairy", "Pharma Plus")

    private fun orders(r: Random, name: String, count: Int, window: Int, amount: () -> Long) = List(count) { SubscriptionSample(name, today - 1 - r.nextInt(window), amount()) }
    private fun random100to1000(r: Random): () -> Long = { (100 + r.nextInt(901)) * 100L }

    private fun rowsIn(profiles: Int, seed: Int, build: (Random) -> List<SubscriptionSample>): Pair<Int, List<String>> {
        var falseRows = 0; val seen = ArrayList<String>(); var profilesWithRows = 0
        for (p in 0 until profiles) {
            val rows = SubscriptionFinder.find(build(Random(seed * 100_000 + p)), today)
            if (rows.isNotEmpty()) { profilesWithRows++; seen += "profile $p: " + rows.joinToString { "${it.name} ${it.cadenceDays}d n=${it.count}" } }
            falseRows += rows.size
        }
        println("PRECISION seed=$seed profiles=$profiles falseRows=$falseRows profilesWithRows=$profilesWithRows")
        return falseRows to seen.take(5)
    }

    @Test
    fun eightDeliveryAndShoppingMerchantsWith30To180OrdersAYearAtRandomAmountsGiveNoRow() {
        val (n, seen) = rowsIn(400, 1) { r -> shops.shuffled(r).take(8).flatMap { m -> orders(r, m, 30 + r.nextInt(151), 365, random100to1000(r)) } }
        assertEquals(0, n, seen.toString())
    }

    @Test
    fun threeMerchantsWith180OrdersIn400DaysGiveNoRow() {
        val (n, seen) = rowsIn(400, 2) { r -> shops.shuffled(r).take(3).flatMap { m -> orders(r, m, 180, 400, random100to1000(r)) } }
        assertEquals(0, n, seen.toString())
    }

    @Test
    fun twelveOrdersAYearAtOneAppGiveNoRow() {
        val (n, seen) = rowsIn(400, 3) { r -> orders(r, shops[r.nextInt(shops.size)], 12, 365, random100to1000(r)) }
        assertEquals(0, n, seen.toString())
    }

    @Test
    fun sixMerchantsAtRoundPricesGiveNoRow() {
        val prices = listOf(20, 40, 60, 80, 120, 150).map { it * 100L }
        for (orderCount in listOf(12, 24, 48)) {
            val (n, seen) = rowsIn(400, 4 + orderCount) { r -> shops.shuffled(r).take(6).mapIndexed { i, m -> orders(r, m, orderCount, 365) { prices[i] } }.flatten() }
            assertEquals(0, n, "$orderCount orders a year: $seen")
        }
    }

    @Test
    fun aTwentyThousandEntryLedgerGivesNoRowFromRandomShopping() {
        val (n, seen) = rowsIn(20, 5) { r ->
            val names = shops + List(11) { "Shop $it" }
            names.flatMap { m -> orders(r, m, 800, 3 * 365, random100to1000(r)) }.also { assertTrue(it.size == 20_000) }
        }
        assertEquals(0, n, seen.toString())
    }

    @Test
    fun twoVisitsAboutSixMonthsApartAtOnePriceAreNotAPatternAndTwoUnrelatedYearApartOrdersAreNot() {
        assertEquals(emptyList(), SubscriptionFinder.find(listOf(SubscriptionSample("City Clinic", today - 190, 80_000), SubscriptionSample("City Clinic", today - 8, 80_000)), today))
        // a yearly pair at an unknown merchant needs the same price, 200 rupees or more, and nothing else charged between
        val year = listOf(SubscriptionSample("Domain Shop", today - 380, 120_000), SubscriptionSample("Domain Shop", today - 15, 120_000))
        assertEquals(listOf("Domain Shop"), SubscriptionFinder.find(year, today).map { it.name })
        assertEquals(emptyList(), SubscriptionFinder.find(listOf(SubscriptionSample("Domain Shop", today - 380, 120_000), SubscriptionSample("Domain Shop", today - 15, 150_000)), today), "different prices")
        assertEquals(emptyList(), SubscriptionFinder.find(listOf(SubscriptionSample("Domain Shop", today - 380, 15_000), SubscriptionSample("Domain Shop", today - 15, 15_000)), today), "under 200 rupees")
        assertEquals(emptyList(), SubscriptionFinder.find(year + SubscriptionSample("Domain Shop", today - 200, 50_000), today), "another charge in between")
        // a known service with two yearly charges is enough, at any price
        assertEquals(listOf("Amazon Prime"), SubscriptionFinder.find(listOf(SubscriptionSample("AMAZON PRIME", today - 380, 14_900), SubscriptionSample("AMZN PRIME", today - 15, 14_900)), today).map { it.name })
    }

    @Test
    fun aMerchantChargedMoreThanSixTimesIn30DaysIsNeverOneUnlessItIsAKnownService() {
        // eight orders in a month at a price, repeated monthly: a delivery app, not a subscription
        val busy = (0 until 5).flatMap { m -> (0 until 8).map { SubscriptionSample("Corner Cafe", today - 20 - m * 30 - it, 25_000) } }
        assertEquals(emptyList(), SubscriptionFinder.find(busy, today))
        // the same shape at a known recurring service is read as it was
        val known = (0 until 5).flatMap { m -> (0 until 8).map { SubscriptionSample("Jio Prepaid", today - 20 - m * 30 - it, 25_000) } }
        assertTrue(SubscriptionFinder.find(known, today).isNotEmpty())
        // cash withdrawals are never one
        assertEquals(emptyList(), SubscriptionFinder.find((0 until 8).map { SubscriptionSample("ATM cash", today - 5 - it * 30, 1_000_000) }, today))
        assertEquals(emptyList(), SubscriptionFinder.find((0 until 8).map { SubscriptionSample("ATM-CASH", today - 5 - it * 30, 1_000_000) }, today))
    }

    // ---- the months and the 28-day plans ----

    private fun on(y: Int, m: Int, d: Int) = LocalDate(y, m, d).toEpochDays().toInt()
    private fun find(vararg s: SubscriptionSample) = SubscriptionFinder.find(s.toList(), today)

    @Test
    fun aMonthlyChargeThatCrossesFebruaryIsMonthlyWhateverTheNumberOfCharges() {
        val feb = LocalDate(2027, 5, 20).toEpochDays().toInt() // "today" for the series below
        fun monthlyOn(day: Int, from: LocalDate, n: Int) = (0 until n).map { SubscriptionSample("Gym Club", from.plus(it, DateTimeUnit.MONTH).let { d -> LocalDate(d.year, d.monthNumber, day) }.toEpochDays().toInt(), 90_000) }
        // on the 1st, February to May (gaps 28, 31, 30)
        assertEquals(listOf(30), SubscriptionFinder.find(monthlyOn(1, LocalDate(2027, 2, 1), 4), feb).map { it.cadenceDays })
        // on the 5th, January to March: three charges with a 28-day gap among them
        assertEquals(listOf(30), SubscriptionFinder.find(monthlyOn(5, LocalDate(2027, 1, 5), 3), LocalDate(2027, 4, 1).toEpochDays().toInt()).map { it.cadenceDays })
        // Jan to Mar on the 28th and 31st-ish days
        assertEquals(listOf(30), SubscriptionFinder.find(monthlyOn(28, LocalDate(2027, 1, 28), 4), LocalDate(2027, 5, 15).toEpochDays().toInt()).map { it.cadenceDays })
        // a leap year: Feb 29 appears as a 29-day gap
        assertEquals(listOf(30), SubscriptionFinder.find(monthlyOn(1, LocalDate(2028, 1, 1), 5), LocalDate(2028, 6, 10).toEpochDays().toInt()).map { it.cadenceDays })
    }

    @Test
    fun aPlanEvery28DaysIsToldFromAMonthlyOneByTheDayOfTheMonthItFallsOn() {
        val plan = (0 until 6).map { SubscriptionSample("Airtel", today - 3 - (5 - it) * 28, 29_900) }
        assertEquals(listOf(28), SubscriptionFinder.find(plan, today).map { it.cadenceDays })
        // three charges 28 days apart are too few to say (two gaps could be February and a short month): monthly, not 28
        assertEquals(listOf(30), SubscriptionFinder.find(plan.takeLast(3), today).map { it.cadenceDays })
        // four charges whose gaps are 28, 29, 28 but that fall on the same day of the month are monthly
        val sameDay = listOf(on(2027, 1, 30), on(2027, 2, 27), on(2027, 3, 27), on(2027, 4, 27)).map { SubscriptionSample("Jio", it, 29_900) }
        assertEquals(listOf(30), SubscriptionFinder.find(sameDay, on(2027, 5, 10)).map { it.cadenceDays })
    }

    // ---- ignoring ----

    @Test
    fun aRowThePersonHidesStaysHiddenAfterItsTypicalPriceChanges() {
        fun series(price: (Int) -> Long) = (0 until 14).map { SubscriptionSample("Pocket Fitness", LocalDate(2025, 9, 5).plus(it, DateTimeUnit.MONTH).toEpochDays().toInt(), price(it)) }
        val before = SubscriptionFinder.find(series { 49_900 }, today).single()
        assertEquals("pocket fitness|30", before.key)
        val ignored = setOf(before.key)
        assertTrue(before.ignoredBy(ignored))
        // the price rises by 60% and the typical price (the median of the history) moves: still the same row, still hidden
        val after = SubscriptionFinder.find(series { if (it < 5) 49_900 else 79_900 }, today).single()
        assertTrue(after.ignoredBy(ignored), "the key is the merchant and cadence, not the price: ${after.key}")
        // a different merchant, or the same merchant at another cadence, is not hidden
        assertTrue(!after.copy(key = "other|30", merchantKey = "other").ignoredBy(ignored)); assertTrue(!after.copy(cadenceDays = 91).ignoredBy(ignored))
        // what an older version stored (the merchant on its own) still hides it
        assertTrue(after.ignoredBy(setOf("pocket fitness")))
    }

    @Test
    fun oneOfSeveralPricesIsHiddenByItsOwnPriceWithinAQuarterAndTheOthersStay() {
        val apple = (0 until 14).flatMap { listOf(SubscriptionSample("APPLE.COM/BILL", LocalDate(2025, 9, 5).plus(it, DateTimeUnit.MONTH).toEpochDays().toInt(), 7_500), SubscriptionSample("APPLE.COM/BILL", LocalDate(2025, 9, 20).plus(it, DateTimeUnit.MONTH).toEpochDays().toInt(), 21_900)) }
        val rows = SubscriptionFinder.find(apple, today)
        assertEquals(listOf("apple services|30|21900", "apple services|30|7500"), rows.map { it.key })
        val hide = setOf(rows.first { it.priceKey == 7_500L }.key)
        assertEquals(listOf("apple services|30|21900"), rows.filter { !it.ignoredBy(hide) }.map { it.key })
        assertTrue(rows.first { it.priceKey == 7_500L }.copy(priceKey = 8_900, amountMinor = 8_900).ignoredBy(hide), "the price moved by under a quarter")
        assertTrue(!rows.first { it.priceKey == 7_500L }.copy(priceKey = 12_000, amountMinor = 12_000).ignoredBy(hide), "a price that far away is another subscription")
    }

    // ---- the price note ----

    private fun note(vararg prices: Long) = SubscriptionFinder.find(prices.indices.map { SubscriptionSample("Pocket Fitness", LocalDate(2025, 9, 5).plus(it, DateTimeUnit.MONTH).toEpochDays().toInt(), prices[it]) }.let { s ->
        // end the series at the last charge being recent
        val shift = today - 10 - s.last().day; s.map { it.copy(day = it.day + shift) }
    }, today).singleOrNull()?.previousAmountMinor

    @Test
    fun aChangeOfPriceIsSaidOnlyWhenTheNewPriceRepeatedAfterAPriceThatHeld() {
        assertEquals(7_500L, note(7_500, 7_500, 7_500, 9_900, 9_900))
        assertEquals(9_900L, note(9_900, 9_900, 9_900, 7_500, 7_500, 7_500))
        assertEquals(7_500L, note(7_500, 7_500, 7_500, 7_500, 7_500, 7_500, 7_500, 7_500, 9_900, 9_900, 9_900, 9_900, 9_900, 9_900))
        assertNull(note(9_900, 9_900, 9_900, 14_900, 9_900), "one odd charge")
        assertNull(note(9_900, 9_900, 7_900, 9_900, 9_900), "a month's discount")
        assertNull(note(9_900, 9_900, 9_900, 9_900, 14_900), "a new price charged once is not yet a change")
        assertNull(note(9_900, 9_900, 9_900, 9_900, 9_900), "no change")
        assertNull(note(165_000, 166_500, 168_000, 170_500, 172_500, 176_000), "a dollar price drifting by under 5% a month")
    }
}
