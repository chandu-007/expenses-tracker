package app.expenses.core.parsers

import app.expenses.core.domain.AmountBands
import app.expenses.core.domain.Certainty
import app.expenses.core.domain.PayeeAlias
import app.expenses.core.domain.PayeeAliases
import app.expenses.core.domain.PayeeKey
import app.expenses.core.domain.SimilarCandidate
import app.expenses.core.domain.SimilarEntries
import app.expenses.core.domain.StarterCategories
import app.expenses.core.domain.SuggestionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What To review starts a card with when the payee has been filed under more than one category and when the person joined two names into one shop. Names, amounts and choices are invented.
 */
class CaptureCategoryAmountTest {
    private val usable = StarterCategories.expenseOrder.map { StarterCategories.expenseId(it) }.toSet()
    private val byName = StarterCategories.expenseOrder.associate { it.lowercase() to StarterCategories.expenseId(it) }
    private fun id(n: String) = StarterCategories.expenseId(n)
    private var k = 0
    private fun row(label: String, rupees: Long, cat: String) = SimilarCandidate("r${k++}", label, id(cat), rupees * 100, k.toLong(), "INR")

    private val amazonHistory = listOf(
        row("Amazon", 199, "Subscriptions"), row("Amazon", 199, "Subscriptions"), row("Amazon", 179, "Subscriptions"), row("Amazon", 219, "Subscriptions"),
        row("Amazon", 2_499, "Shopping"), row("Amazon", 2_299, "Shopping"), row("Amazon", 2_799, "Shopping"), row("Amazon", 3_199, "Shopping"),
    )

    private fun advise(payee: String, rupees: Long, history: List<SimilarCandidate>, taught: CaptureCategory.Taught = CaptureCategory.Taught()) = CaptureCategory.advise(
        payee, "x", emptyList(), taught, usable, byName, SimilarEntries.profile(payee, history, taught.aliases), band = AmountBands.of(payee, rupees * 100, "INR", history, taught.aliases),
    )

    @Test
    fun anAmountLikeTheSubscriptionIsSuggestedAsASubscriptionEvenThoughTheLastChoiceWasAPurchase() {
        val taught = CaptureCategory.Taught(mapOf("amazon" to id("Shopping")), mapOf("amazon" to 1))
        val a = advise("AMAZON PAY INDIA", 199, amazonHistory, taught)!!
        assertEquals(id("Subscriptions"), a.categoryId); assertEquals(SuggestionSource.AMOUNT, a.source)
        assertEquals(Certainty.SURE, a.certainty, "four of four nearby entries agree and none is in another category")
        assertEquals("4 of your earlier entries for amounts like ₹199", a.note)
        assertEquals(listOf(id("Shopping")), a.alternatives, "the payee's other category is one tap away")
        val p = advise("Amazon", 2_499, amazonHistory, taught)!!
        assertEquals(id("Shopping"), p.categoryId); assertEquals(SuggestionSource.AMOUNT, p.source); assertEquals("3 of your earlier entries for amounts like ₹2,499", p.note)
    }

    @Test
    fun anAmountWithNoEarlierAmountLikeItFallsBackToThePayeeLevelAnswerAndIsAlwaysACheckForAMarketplace() {
        val taught = CaptureCategory.Taught(mapOf("amazon" to id("Shopping")), mapOf("amazon" to 1))
        val a = advise("Amazon", 850, amazonHistory, taught)!!
        assertEquals(SuggestionSource.LEARNED, a.source); assertEquals(id("Shopping"), a.categoryId); assertEquals(Certainty.CHECK, a.certainty, "a marketplace chosen once, with a history that disagrees")
        assertEquals("what you chose last time", a.note)
    }

    @Test
    fun twoAgreeingNearbyEntriesAreOnlyACheckAndOneAgainstKeepsItACheck() {
        val few = listOf(row("Chai Corner", 40, "Food"), row("Chai Corner", 45, "Food"), row("Chai Corner", 4_000, "Rent"))
        val a = advise("Chai Corner", 42, few)!!
        assertEquals(SuggestionSource.AMOUNT, a.source); assertEquals(Certainty.CHECK, a.certainty)
        val against = amazonHistory + row("Amazon", 205, "Shopping")
        assertEquals(Certainty.CHECK, advise("Amazon", 199, against)!!.certainty)
    }

    @Test
    fun aPayeeFiledUnderOneCategoryKeepsTheAnswerItAlwaysHad() {
        val plain = listOf(row("Chai Corner", 40, "Food"), row("Chai Corner", 45, "Food"), row("Chai Corner", 42, "Food"))
        val a = advise("Chai Corner", 42, plain)!!
        assertEquals(SuggestionSource.HISTORY, a.source, "no amount signal where the payee has one category"); assertEquals("3 of your earlier entries", a.note)
        assertNull(AmountBands.of("Chai Corner", 4_200, "INR", plain))
    }

    @Test
    fun aRuleStillBeatsTheBandAndSoDoesAnIncomeBandForMoneyIn() {
        val rule = app.expenses.core.domain.CaptureRule("r", "amazon", id("Bills"))
        val a = CaptureCategory.advise("Amazon", "x", listOf(rule), CaptureCategory.Taught(), usable, byName, null, band = AmountBands.of("Amazon", 199_00, "INR", amazonHistory))!!
        assertEquals(SuggestionSource.RULE, a.source); assertEquals(id("Bills"), a.categoryId)
        val income = (StarterCategories.INCOME_FIRST + StarterCategories.INCOME_MORE).associate { it.lowercase() to StarterCategories.incomeId(it) }
        val rows = listOf(
            SimilarCandidate("i1", "Acme Software Pvt Ltd", StarterCategories.incomeId("Salary"), 52_000_00, 1, "INR"), SimilarCandidate("i2", "ACME SOFTWARE", StarterCategories.incomeId("Salary"), 52_000_00, 2, "INR"),
            SimilarCandidate("i3", "Acme Software", StarterCategories.incomeId("Salary"), 55_000_00, 3, "INR"), SimilarCandidate("i4", "Acme Software", StarterCategories.incomeId("Reimbursement"), 1_200_00, 4, "INR"),
            SimilarCandidate("i5", "Acme Software", StarterCategories.incomeId("Reimbursement"), 1_000_00, 5, "INR"),
        )
        val usableIncome = income.values.toSet()
        fun inc(rupees: Long) = CaptureCategory.adviseIncome("ACME SOFTWARE PVT LTD", "credited", emptyList(), CaptureCategory.Taught(), usableIncome, income, SimilarEntries.profile("Acme Software", rows), band = AmountBands.of("Acme Software", rupees * 100, "INR", rows))!!
        val salary = inc(53_000)
        assertEquals(StarterCategories.incomeId("Salary"), salary.categoryId); assertEquals(SuggestionSource.AMOUNT, salary.source); assertEquals(Certainty.SURE, salary.certainty)
        assertEquals("3 of your earlier income entries for amounts like ₹53,000", salary.note)
        val back = inc(1_100)
        assertEquals(StarterCategories.incomeId("Reimbursement"), back.categoryId); assertEquals(Certainty.CHECK, back.certainty, "two nearby entries: a check")
    }

    @Test
    fun aChoiceForOneNameOfAJoinedShopIsUsedForTheOtherAndTheRowCountIsTheShops() {
        val joined = PayeeAliases(listOf(PayeeAlias(PayeeKey.of("Swiggy Instamart")!!, PayeeKey.of("Swiggy")!!, "Swiggy Instamart", "Swiggy")))
        val taught = CaptureCategory.Taught(mapOf("swiggy" to id("Groceries")), mapOf("swiggy" to 3), joined)
        val a = CaptureCategory.advise("Swiggy Instamart", "x", emptyList(), taught, usable, byName, null)!!
        assertEquals(SuggestionSource.LEARNED, a.source); assertEquals(id("Groceries"), a.categoryId)
        assertEquals("you chose it 3 times for this payee", a.note, "the shop's count")
        val without = CaptureCategory.advise("Swiggy Instamart", "x", emptyList(), CaptureCategory.Taught(taught.category, taught.uses), usable, byName, null)!!
        assertEquals(SuggestionSource.STARTER, without.source, "without the alias Instamart is its own shop")
    }

    @Test
    fun anAliasToAMarketplaceMakesTheNameAMixedPayeeThatNeedsTwoChoices() {
        val joined = PayeeAliases(listOf(PayeeAlias(PayeeKey.of("Amazon Biz")!!, PayeeKey.of("Amazon")!!, "Amazon Biz", "Amazon")))
        val once = CaptureCategory.Taught(mapOf("amazon" to id("Bills")), mapOf("amazon" to 1), joined)
        assertEquals(Certainty.CHECK, CaptureCategory.advise("Amazon Biz", "x", emptyList(), once, usable, byName, null)!!.certainty)
        val twice = CaptureCategory.Taught(mapOf("amazon" to id("Bills")), mapOf("amazon" to 2), joined)
        assertEquals(Certainty.SURE, CaptureCategory.advise("Amazon Biz", "x", emptyList(), twice, usable, byName, null)!!.certainty)
        assertTrue(PayeeKey.isMixed("Amazon Biz", joined))
    }
}
