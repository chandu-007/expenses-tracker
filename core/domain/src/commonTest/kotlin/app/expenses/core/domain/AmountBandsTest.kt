package app.expenses.core.domain

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Amount-aware suggestions: what the person's earlier entries of AMOUNTS LIKE THIS ONE say for a payee that is paid for different things. Every payee, amount and category is invented.
 */
class AmountBandsTest {
    private val sub = "Subscriptions"; private val shop = "Shopping"; private val bills = "Bills"; private val food = "Food"
    private var n = 0
    private fun c(label: String, rupees: Long, cat: String, currency: String = "INR") = SimilarCandidate("e${n++}", label, cat, rupees * 100, n.toLong(), currency)
    private fun band(label: String, rupees: Long, rows: List<SimilarCandidate>, aliases: PayeeAliases = PayeeAliases.NONE, currency: String = "INR") = AmountBands.of(label, rupees * 100, currency, rows, aliases)

    private val amazon = listOf(
        c("Amazon", 199, sub), c("Amazon", 179, sub), c("Amazon", 219, sub), c("Amazon", 199, sub),
        c("Amazon", 2_499, shop), c("Amazon", 2_299, shop), c("Amazon", 2_799, shop), c("Amazon", 1_999, shop), c("Amazon", 3_199, shop),
        c("Amazon", 599, bills), c("Amazon", 299, bills), c("Amazon", 310, bills),
    )

    @Test
    fun theWindowIsAQuarterAroundTheAmountWithNoEdgeToFallOn() {
        assertTrue(AmountBands.near(199_00, 248_00)); assertTrue(AmountBands.near(248_00, 199_00)); assertTrue(AmountBands.near(199_00, 160_00))
        assertTrue(!AmountBands.near(199_00, 249_00)); assertTrue(!AmountBands.near(199_00, 159_00)); assertTrue(AmountBands.near(199_00, 199_00))
        assertTrue(!AmountBands.near(0, 100) && !AmountBands.near(100, -5))
        // a subscription that went 179 -> 199 -> 219 is one band, with no bucket edge between 199 and 201
        for (a in listOf(179L, 199L, 201L, 219L)) assertTrue(AmountBands.near(a * 100, 199_00), "$a")
    }

    @Test
    fun anAmountLikeTheSubscriptionIsASubscriptionAndAnAmountLikeAPurchaseIsAPurchase() {
        val s = band("Amazon", 199, amazon)!!
        assertEquals(sub, s.top); assertEquals(4, s.agree); assertEquals(0, s.other)
        assertEquals("₹199", s.like())
        val p = band("Amazon", 2_499, amazon)!!
        assertEquals(shop, p.top); assertEquals(3, p.agree, "2,499 2,299 and 2,799; 1,999 is just over a quarter away"); assertEquals(0, p.other)
    }

    @Test
    fun anAmountNobodyHasPaidBeforeHasNoBandSoThePayeeLevelAnswerStands() {
        for (r in listOf(12L, 40L, 850L, 5_000L, 12_000L, 99_999L)) assertNull(band("Amazon", r, amazon), "₹$r")
    }

    @Test
    fun oneNearbyEntryATieOrAPayeeWithOneCategoryIsNotABand() {
        assertNull(band("Amazon", 199, listOf(c("Amazon", 199, sub), c("Amazon", 2_499, shop))), "one nearby entry says nothing")
        assertNull(band("Amazon", 199, listOf(c("Amazon", 199, sub), c("Amazon", 190, sub), c("Amazon", 205, shop), c("Amazon", 200, shop))), "two against two: a tie says nothing")
        assertNull(band("Amazon", 199, listOf(c("Amazon", 199, sub), c("Amazon", 190, sub), c("Amazon", 205, sub))), "a payee filed under one category is answered by the plain history")
        assertNull(band("Amazon", 0, amazon)); assertNull(band("Amazon", -5, amazon)); assertNull(band("", 199, amazon)); assertNull(AmountBands.of("Amazon", 199_00, "INR", emptyList()))
    }

    @Test
    fun aBandThatLeadsButIsNotUnanimousIsStillReportedWithItsCountsForTheCheckCard() {
        val rows = amazon + listOf(c("Amazon", 205, shop))
        val b = band("Amazon", 199, rows)!!
        assertEquals(sub, b.top); assertEquals(4, b.agree); assertEquals(1, b.other); assertEquals(listOf(shop), b.runnersUp)
        assertEquals(Certainty.CHECK, SuggestionConfidence.of(SuggestionSource.AMOUNT, true, false, 0, b.profile(), b.top))
    }

    @Test
    fun theThresholdsTwoAgreeingIsACheckThreeWithNoConflictIsSure() {
        fun cert(agreeing: Int, against: Int): Certainty {
            val rows = (1..agreeing).map { c("Corner Shop", 150L + it, "Groceries") } + (1..against).map { c("Corner Shop", 160L + it, "Food") } + listOf(c("Corner Shop", 5_000, "Bills"))
            val b = band("Corner Shop", 155, rows) ?: return Certainty.CHECK
            return SuggestionConfidence.of(SuggestionSource.AMOUNT, false, false, 0, b.profile(), b.top)
        }
        assertEquals(Certainty.CHECK, cert(2, 0)); assertEquals(Certainty.SURE, cert(3, 0)); assertEquals(Certainty.SURE, cert(6, 0))
        assertEquals(Certainty.CHECK, cert(3, 1), "one nearby entry in another category: look at it"); assertEquals(Certainty.CHECK, cert(5, 2))
        assertEquals(SuggestionConfidence.AGREE_MIXED, 3)
    }

    @Test
    fun aDifferentCurrencyAndADifferentPayeeNeverCount() {
        val rows = amazon + listOf(c("Amazon", 199, "Fun", "USD"), c("Amazon", 190, "Fun", "USD"), c("Amazon", 205, "Fun", "USD"))
        assertEquals(sub, band("Amazon", 199, rows)!!.top, "dollar entries are not rupee amounts")
        assertEquals(setOf(sub), setOf(band("Amazon", 199, amazon + listOf(c("Amazon Fresh", 199, "Groceries"), c("Amazon Fresh", 190, "Groceries"), c("Amazon Fresh", 205, "Groceries"), c("Amazon Fresh", 200, "Groceries")))!!.top), "Amazon Fresh is another payee")
        assertEquals(sub, band("amazon.in", 199, amazon)!!.top, "the payee written another way is the same payee")
        assertEquals(sub, band("AMAZON PAY INDIA", 199, amazon)!!.top, "built in: Amazon Pay is Amazon")
    }

    @Test
    fun theOtherNamesOfAShopTheyJoinedCountAsOnePayee() {
        val rows = listOf(c("Instamart", 450, "Groceries"), c("Instamart", 480, "Groceries"), c("Instamart", 430, "Groceries"), c("Swiggy", 440, food), c("Swiggy", 470, food), c("Swiggy", 460, food), c("Swiggy", 2_000, food))
        val joined = PayeeAliases(listOf(PayeeAlias(PayeeKey.of("Instamart")!!, PayeeKey.of("Swiggy")!!, "Instamart", "Swiggy")))
        assertNull(band("Swiggy", 450, rows), "two different shops: Swiggy has one category")
        val b = band("Swiggy", 450, rows, joined)
        assertNull(b, "joined, the nearby entries are three and three: a tie says nothing")
        val more = rows + c("Instamart", 455, "Groceries")
        assertEquals("Groceries", band("Swiggy", 450, more, joined)!!.top)
    }

    @Test
    fun anIncomePayerIsReadTheSameWay() {
        val rows = listOf(c("Acme Ltd", 52_000, "Salary"), c("Acme Ltd", 52_000, "Salary"), c("Acme Ltd", 55_000, "Salary"), c("Acme Ltd", 1_200, "Reimbursement"), c("Acme Ltd", 1_000, "Reimbursement"))
        assertEquals("Salary", band("Acme Ltd", 53_000, rows)!!.top)
        assertEquals("Reimbursement", band("Acme Ltd", 1_100, rows)!!.top)
        assertEquals("₹53,000", band("Acme Ltd", 53_000, rows)!!.like(), "Indian grouping")
    }

    @Test
    fun theSentenceNamesWhatTheyChoseHowOftenAndForWhichAmount() {
        val b = band("Amazon", 199, amazon)!!
        assertEquals("₹199", b.like())
        assertEquals("₹1,249.50", AmountBand("x", 2, 0, emptyList(), 124_950, "INR").like())
        assertEquals("USD 5.99", AmountBand("x", 2, 0, emptyList(), 599, "USD").like())
        assertEquals("JPY 500", AmountBand("x", 2, 0, emptyList(), 500, "JPY").like())
    }

    @Test
    fun theBandOverridesWhatWasChosenLastTimeButNeverARule() {
        val usable = setOf(sub, shop, bills)
        val learned = mapOf("amazon" to shop)
        val b = band("Amazon", 199, amazon)!!
        val s = CategorySuggest.suggest("Amazon", "x", emptyList(), learned, usable, band = b)!!
        assertEquals(CategorySuggestion(sub, SuggestionSource.AMOUNT), s)
        assertEquals(CategorySuggestion(shop, SuggestionSource.LEARNED), CategorySuggest.suggest("Amazon", "x", emptyList(), learned, usable, band = null), "no band: what they chose last time")
        assertEquals(CategorySuggestion(bills, SuggestionSource.RULE), CategorySuggest.suggest("Amazon", "x", listOf(CaptureRule("r", "amazon", bills)), learned, usable, band = b), "their own rule comes first")
        assertNull(CategorySuggest.suggest("Amazon", "x", emptyList(), emptyMap(), setOf(shop), band = b)?.takeIf { it.source == SuggestionSource.AMOUNT }, "a category that is gone is never suggested")
        assertEquals(CategorySuggestion(sub, SuggestionSource.AMOUNT), CategorySuggest.suggestIncome("Acme Ltd", "x", emptyList(), emptyMap(), usable, band = band("Acme Ltd", 199, listOf(c("Acme Ltd", 199, sub), c("Acme Ltd", 190, sub), c("Acme Ltd", 20_000, shop)))))
    }

    // ---- invented simulation: a mixed merchant, the amounts a person really sees, and 12 new ones ----

    @Test
    fun twelveNewAmountsOfAMixedMerchantAreSuggestedByAmountOrLeftToThePayeeLevel() {
        // the invented history above: 199-ish is Subscriptions, 2,000-3,200 is Shopping, 300 and 600 are Bills
        val cases: List<Triple<Long, String?, Boolean>> = listOf(
            Triple(199, sub, true), Triple(209, sub, true), Triple(2_499, shop, true), Triple(2_650, shop, true), Triple(3_000, shop, true), Triple(299, bills, false),
            Triple(12, null, false), Triple(850, null, false), Triple(1_200, null, false), Triple(5_500, null, false), Triple(60_000, null, false), Triple(450, null, false),
        )
        var banded = 0; var right = 0; var wrong = 0; var sure = 0; var sureRight = 0
        for ((amount, want, wantSure) in cases) {
            val b = band("Amazon", amount, amazon)
            if (want == null) { assertNull(b, "₹$amount has no earlier amount like it"); continue }
            assertNotNull(b, "₹$amount"); banded++
            if (b.top == want) right++ else wrong++
            val certain = SuggestionConfidence.of(SuggestionSource.AMOUNT, true, false, 0, b.profile(), b.top) == Certainty.SURE
            if (certain) { sure++; if (b.top == want) sureRight++ }
            assertEquals(wantSure, certain, "₹$amount sure?")
        }
        println("AMOUNT-BANDS 12 new amounts for one mixed payee: banded $banded, right $right, wrong $wrong, sure $sure (right $sureRight); the other ${12 - banded} fall back to the payee-level suggestion")
        assertEquals(0, wrong); assertEquals(sure, sureRight)
    }

    /** The category by amount for the invented merchants: clusters with some overlap at their edges, as real prices have. */
    private class Merchant(val label: String, val clusters: List<Triple<Long, Long, String>>)
    private val merchants = listOf(
        Merchant("Amazon", listOf(Triple(149L, 249L, "Subscriptions"), Triple(250L, 899L, "Bills"), Triple(900L, 9_000L, "Shopping"))),
        Merchant("Paytm", listOf(Triple(10L, 90L, "Food"), Triple(200L, 1_500L, "Bills"), Triple(2_000L, 20_000L, "Rent"))),
        Merchant("Google Play", listOf(Triple(29L, 129L, "Subscriptions"), Triple(130L, 800L, "Fun"))),
        Merchant("Apple", listOf(Triple(75L, 299L, "Subscriptions"), Triple(10_000L, 150_000L, "Shopping"))),
        Merchant("Flipkart", listOf(Triple(300L, 1_200L, "Shopping"), Triple(1_500L, 6_000L, "Home"), Triple(10_000L, 80_000L, "Electronics"))),
        Merchant("Kirana Corner", listOf(Triple(20L, 150L, "Groceries"), Triple(400L, 3_000L, "Bills"))),
    )

    private fun draw(m: Merchant, rnd: Random, noise: Int): Pair<Long, String> {
        val (lo, hi, cat) = m.clusters[rnd.nextInt(m.clusters.size)]
        val span = hi - lo
        // log-uniform inside the cluster, so low amounts are as common as high ones
        val a = (lo + span * (kotlin.math.exp(rnd.nextDouble() * kotlin.math.ln(1.0 + 20.0)) - 1.0) / 20.0).toLong().coerceIn(lo, hi)
        val label = if (noise > 0 && rnd.nextInt(100) < noise) m.clusters[rnd.nextInt(m.clusters.size)].third else cat
        return a to label
    }

    /** The hard case: the categories share a range of amounts (a Rs 400 Amazon order is as likely a bill as a purchase), so the amount alone cannot always tell them apart. */
    private val overlapping = listOf(
        Merchant("Corner Mart", listOf(Triple(40L, 600L, "Groceries"), Triple(300L, 1_200L, "Bills"))),
        Merchant("Online Store", listOf(Triple(100L, 3_000L, "Shopping"), Triple(500L, 5_000L, "Gifts"))),
    )

    private fun simulate(noise: Int, seed: Int, earlier: Int, set: List<Merchant> = merchants, floorRight: Int = if (noise == 0) 90 else 80, floorSure: Int = if (noise == 0) 98 else 90): String {
        val rnd = Random(seed)
        var asked = 0; var banded = 0; var right = 0; var sure = 0; var sureRight = 0; var baseRight = 0; var combinedRight = 0
        for (m in set) {
            val history = (0 until earlier).map { val (a, cat) = draw(m, rnd, noise); c(m.label, a, cat) }
            val payeeLevel = SimilarEntries.topCategory(m.label, history)
            repeat(300) {
                val (a, truth) = draw(m, rnd, 0)
                asked++
                if (payeeLevel == truth) baseRight++
                val b = band(m.label, a, history)
                if ((b?.top ?: payeeLevel) == truth) combinedRight++
                if (b == null) return@repeat
                banded++
                if (b.top == truth) right++
                if (SuggestionConfidence.of(SuggestionSource.AMOUNT, true, false, 0, b.profile(), b.top) == Certainty.SURE) { sure++; if (b.top == truth) sureRight++ }
            }
        }
        val line = "AMOUNT-BANDS simulation (invented, ${set.size} mixed payees${if (set === overlapping) " with overlapping amounts" else ""}, $earlier earlier entries each, $noise% of earlier choices random): $asked new amounts; payee-level suggestion alone right ${100 * baseRight / asked}%; " +
            "a band speaks for $banded (${100 * banded / asked}%) and is right ${100 * right / maxOf(1, banded)}% of those; amount-aware (band, else payee-level) right ${100 * combinedRight / asked}%; sure $sure, sure right ${100 * sureRight / maxOf(1, sure)}%"
        println(line)
        check(banded > 0) { line }
        assertTrue(right * 100 >= banded * floorRight, "precision of the band: $line")
        assertTrue(sure == 0 || sureRight * 100 >= sure * floorSure, "precision of the sure ones: $line")
        assertTrue(combinedRight > baseRight, "the amount helps: $line")
        return line
    }

    @Test
    fun onInventedMixedMerchantsTheBandIsRightAlmostAlwaysAndSureIsRightEvenWithNoisyChoicesAndShortHistories() {
        simulate(noise = 0, seed = 7, earlier = 120)
        simulate(noise = 10, seed = 11, earlier = 120)
        simulate(noise = 0, seed = 5, earlier = 30)
        simulate(noise = 0, seed = 3, earlier = 10)
        simulate(noise = 0, seed = 9, earlier = 120, set = overlapping, floorRight = 65, floorSure = 85)
    }
}
