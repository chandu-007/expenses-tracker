package app.expenses.core.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the subscription finder detects and what it misses, on invented 14-month histories (today is 29 Oct 2026; the finder looks back about 400 days, as the app does). Every merchant string
 * is the way a card or bank prints it; every amount is invented. Each row of [theTable] is one real-world shape.
 */
class SubscriptionDetectionTest {
    private val today = LocalDate(2026, 10, 29)
    private val todayN = today.toEpochDays().toInt()
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).toEpochDays().toInt()

    /** [times] charges by the calendar month, the first on [from]. [amount] gives the amount of the i-th. */
    private fun monthly(name: String, from: LocalDate, times: Int, every: Int = 1, amount: (Int) -> Long) =
        (0 until times).map { SubscriptionSample(name, from.plus(it * every, DateTimeUnit.MONTH).toEpochDays().toInt(), amount(it)) }
    private fun monthly(name: String, from: LocalDate, times: Int, amount: Long, every: Int = 1) = monthly(name, from, times, every) { amount }
    /** Every [gap] days, [times] charges, the last [lastAgo] days before today. */
    private fun everyDays(name: String, gap: Int, times: Int, amount: Long, lastAgo: Int = 3) = (0 until times).map { SubscriptionSample(name, todayN - lastAgo - (times - 1 - it) * gap, amount) }
    private val start = LocalDate(2025, 9, 5) // fourteen monthly charges: Sep 2025 to Oct 2026

    private fun found(samples: List<SubscriptionSample>) = SubscriptionFinder.find(samples, todayN)

    @Test
    fun theTable() {
        class Case(val what: String, val samples: List<SubscriptionSample>, val expect: List<Triple<String, Int, Long>>) // name, cadence, latest amount
        val cases = listOf(
            Case("Netflix, 14 months", monthly("NETFLIX.COM", start, 14, 64_900), listOf(Triple("Netflix", 30, 64_900L))),
            Case("Spotify with the bank's suffix", monthly("SPOTIFY IN", start, 14, 11_900), listOf(Triple("Spotify", 30, 11_900L))),
            Case("YouTube Premium billed by Google", monthly("GOOGLE *YouTubePremium", start, 14, 12_900), listOf(Triple("YouTube Premium", 30, 12_900L))),
            Case("Google One", monthly("GOOGLE *Google One", start, 14, 13_000), listOf(Triple("Google One", 30, 13_000L))),
            Case("Amazon Prime, yearly, written two ways", listOf(SubscriptionSample("AMAZON PRIME", day(2025, 10, 12), 149_900), SubscriptionSample("AMZN PRIME", day(2026, 10, 11), 149_900)), listOf(Triple("Amazon Prime", 365, 149_900L))),
            Case("Disney+ Hotstar", monthly("Disney+ Hotstar", start, 14, 29_900), listOf(Triple("Disney+ Hotstar", 30, 29_900L))),
            Case("Zee5 yearly", listOf(SubscriptionSample("ZEE5", day(2024, 11, 4), 39_900), SubscriptionSample("ZEE5", day(2025, 11, 3), 39_900)), listOf(Triple("ZEE5", 365, 39_900L))),
            Case("SonyLIV", monthly("SONYLIV", start, 14, 29_900), listOf(Triple("SonyLIV", 30, 29_900L))),
            Case("Microsoft 365 yearly", listOf(SubscriptionSample("MICROSOFT 365", day(2025, 8, 20), 489_900), SubscriptionSample("MICROSOFT 365", day(2026, 8, 19), 489_900)), listOf(Triple("Microsoft 365", 365, 489_900L))),
            Case("Adobe", monthly("ADOBE SYSTEMS SOFTWARE", start, 14, 167_500), listOf(Triple("Adobe", 30, 167_500L))),
            Case("ChatGPT billed in dollars: the rupees drift by a few percent", monthly("OPENAI *CHATGPT SUBSCR", start, 14) { 194_000L + (it % 5) * 2_500 }, listOf(Triple("ChatGPT", 30, 194_000L + (13 % 5) * 2_500))),
            Case("a gym, monthly", monthly("Cult.fit", start, 14, 250_000), listOf(Triple("Cult.fit", 30, 250_000L))),
            Case("a newspaper that is no known merchant", monthly("Times Agency", start, 14, 33_000), listOf(Triple("Times Agency", 30, 33_000L))),
            Case("a jio prepaid plan every 28 days", everyDays("Jio", 28, 14, 29_900), listOf(Triple("Jio", 28, 29_900L))),
            Case("an airtel prepaid plan, monthly", monthly("AIRTEL PREPAID", start, 14, 39_900), listOf(Triple("Airtel", 30, 39_900L))),
            Case("a 12-week plan", everyDays("Airtel", 84, 5, 79_900), listOf(Triple("Airtel", 84, 79_900L))),
            Case("a premium that is yearly (the last one 319 days ago: still going)", listOf(SubscriptionSample("LIC PREMIUM", day(2024, 12, 15), 2_400_000), SubscriptionSample("LIC PREMIUM", day(2025, 12, 14), 2_400_000)), listOf(Triple("LIC", 365, 2_400_000L))),
            Case("a SIP, the same amount every month", monthly("Groww", start, 14, 500_000), listOf(Triple("Groww", 30, 500_000L))),
            Case("rent, to a landlord's name", monthly("Landlord R Rao", start, 14, 2_500_000), listOf(Triple("Landlord R Rao", 30, 2_500_000L))),
            Case("an EMI taken by mandate (ACH D- narration)", monthly("Bajaj Finance", start, 14, 1_250_000), listOf(Triple("Bajaj Finance", 30, 1_250_000L))),
            Case("quarterly maintenance", monthly("Apna Complex", LocalDate(2025, 7, 10), 5, 300_000, every = 3), listOf(Triple("ApnaComplex", 91, 300_000L))),
            Case("half-yearly insurance", monthly("HDFC Life", LocalDate(2025, 4, 1), 3, 1_800_000, every = 6), listOf(Triple("HDFC Life", 182, 1_800_000L))),
        )
        val problems = ArrayList<String>()
        for (c in cases) {
            val got = found(c.samples).map { Triple(it.name, it.cadenceDays, it.amountMinor) }
            if (got != c.expect) problems += "${c.what}: expected ${c.expect}, found $got"
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun appleBillsThreeThingsUnderOneMerchantAndTheyAreToldApartByPrice() {
        val apple = monthly("APPLE.COM/BILL", start, 14, 7_500) + monthly("APPLE.COM/BILL", LocalDate(2025, 9, 20), 14, 21_900) +
            listOf(SubscriptionSample("APPLE.COM/BILL", day(2024, 11, 8), 150_000), SubscriptionSample("APPLE SERVICES", day(2025, 11, 9), 150_000))
        val subs = found(apple)
        assertEquals(listOf("Apple Services (219)", "Apple Services (1500)", "Apple Services (75)"), subs.map { it.name }, "by what they cost a year: 2,628, 1,500, 900")
        assertEquals(listOf(30, 365, 30), subs.map { it.cadenceDays })
    }

    @Test
    fun aPriceChangeIsDetectedAndSaidWithWhatItWasBefore() {
        val rise = monthly("Netflix", start, 14) { if (it < 8) 7_500L else 9_900L }
        val s = found(rise).single()
        assertEquals(9_900, s.amountMinor); assertEquals(7_500L, s.previousAmountMinor); assertTrue(s.priceWentUp && !s.priceWentDown)
        val cut = monthly("Spotify", start, 14) { if (it < 10) 11_900L else 9_900L }
        val c = found(cut).single(); assertTrue(c.priceWentDown); assertEquals(11_900L, c.previousAmountMinor)
        val gentle = monthly("Apple Music", start, 14) { 6_500L + it * 20 }
        assertNull(found(gentle).single().previousAmountMinor, "a drift of a few rupees is not a price change")
        assertNull(found(monthly("Netflix", start, 14, 64_900)).single().previousAmountMinor)
        // a new price charged once is not yet called a change (see SubscriptionPrecisionTest for the rule)
        val last = monthly("Hotstar", start, 14) { if (it < 13) 64_900L else 69_900L }
        assertEquals(null, found(last).single().previousAmountMinor)
    }

    @Test
    fun aSkippedMonthAndADoubleChargeDoNotBreakAPatternAndTheNextDateIsRight() {
        val skipped = monthly("Netflix", start, 14, 64_900).filterIndexed { i, _ -> i != 6 }
        assertEquals(1, found(skipped).size, "one skipped month")
        val double = monthly("Spotify", start, 14, 11_900) + SubscriptionSample("Spotify", day(2026, 3, 7), 11_900) // the March charge was taken again two days later
        val d = found(double).single()
        assertEquals(14, d.count, "the double charge counts once")
        val s = found(monthly("Netflix", start, 14, 64_900)).single()
        assertEquals(day(2026, 10, 5), s.lastDay); assertTrue(s.nextDay in day(2026, 11, 3)..day(2026, 11, 6), "next about 5 Nov: ${LocalDate.fromEpochDays(s.nextDay)}")
    }

    @Test
    fun theSameServiceWrittenSeveralWaysIsOneSubscription() {
        val mixed = listOf("NETFLIX.COM", "Netflix", "NETFLIX", "netflix india", "NETFLIX.COM", "Netflix", "Netflix").flatMapIndexed { i, n -> listOf(SubscriptionSample(n, start.plus(i * 2, DateTimeUnit.MONTH).toEpochDays().toInt(), 64_900), SubscriptionSample(n, start.plus(i * 2 + 1, DateTimeUnit.MONTH).toEpochDays().toInt(), 64_900)) }
        assertEquals(listOf("Netflix"), found(mixed).map { it.name })
        // a service in a UPI narration with a handle
        assertEquals(listOf("Spotify"), found(monthly("SPOTIFY@ybl", start, 14, 11_900)).map { it.name })
    }

    @Test
    fun whatIsNotASubscriptionIsNotFoundAndWhatStoppedIsLeftOut() {
        val groceries = (0 until 14).map { SubscriptionSample("BigBasket", day(2025, 9, 3) + it * 27 + (it % 4) * 3, 90_000L + (it * 37 % 11) * 25_000) }
        val cafe = (0 until 30).map { SubscriptionSample("Cafe Coffee Day", day(2026, 5, 1) + it * 4 + (it % 3), 24_000) }
        val electricity = monthly("BESCOM", start, 14) { listOf(120_000L, 310_000L, 90_000L, 250_000L, 180_000L)[it % 5] }
        val oneOff = listOf(SubscriptionSample("Croma", day(2026, 2, 3), 4_500_000))
        val stopped = monthly("Old Gym", LocalDate(2025, 9, 1), 5, 100_000) // last charge 1 Jan 2026
        assertEquals(emptyList(), found(groceries + cafe + electricity + oneOff + stopped).map { it.name })
        assertNotNull(found(monthly("Netflix", start, 14, 64_900)).singleOrNull())
    }

    @Test
    fun theWordingTheScreenNeedsIsThere() {
        val s = found(monthly("Netflix", start, 14) { if (it < 8) 7_500L else 9_900L }).single()
        assertEquals("Netflix", s.name); assertEquals(9_900L * 12, s.yearlyMinor); assertEquals(9_900L, s.monthlyMinor)
        val jio = found(everyDays("Jio", 28, 14, 29_900)).single()
        assertEquals(29_900L * 365 / 28, jio.yearlyMinor, "a 28-day plan is paid about 13 times a year, not 12")
        assertEquals(jio.lastDay + 28, jio.nextDay)
    }
}
