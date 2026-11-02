package app.expenses.core.parsers

import app.expenses.core.domain.CaptureRule
import app.expenses.core.domain.Certainty
import app.expenses.core.domain.HistoryProfile
import app.expenses.core.domain.StarterCategories
import app.expenses.core.domain.SuggestionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureCategoryTest {
    private val names = StarterCategories.expenseOrder
    private val usable = names.map { StarterCategories.expenseId(it) }.toSet()
    private val byName = names.associate { it.lowercase() to StarterCategories.expenseId(it) }
    private fun id(n: String) = StarterCategories.expenseId(n)
    private fun advise(payee: String?, taught: CaptureCategory.Taught = CaptureCategory.Taught(), history: HistoryProfile? = null, rules: List<CaptureRule> = emptyList(), person: Boolean = false, personLike: Boolean = person) =
        CaptureCategory.advise(payee, "x", rules, taught, usable, byName, history, person, personLike)

    @Test
    fun aStarterMerchantIsSureAndSaysSo() {
        val a = advise("SWIGGY BANGALORE KA IN")!!
        assertEquals(id("Food"), a.categoryId); assertEquals(SuggestionSource.STARTER, a.source); assertEquals(Certainty.SURE, a.certainty); assertEquals("a common merchant", a.note)
    }

    @Test
    fun aMarketplaceIsOnlyAGuessUntilThePersonHasChosenTwice() {
        assertEquals(Certainty.CHECK, advise("AMAZON PAY INDIA")!!.certainty)
        val once = advise("AMAZON PAY INDIA", CaptureCategory.Taught(mapOf("amazonpay" to id("Bills")), mapOf("amazonpay" to 1)))!!
        assertEquals(SuggestionSource.LEARNED, once.source); assertEquals(Certainty.CHECK, once.certainty); assertEquals("what you chose last time", once.note)
        val twice = advise("AMAZON PAY INDIA", CaptureCategory.Taught(mapOf("amazonpay" to id("Bills")), mapOf("amazonpay" to 2)))!!
        assertEquals(Certainty.SURE, twice.certainty); assertEquals("you chose it 2 times for this payee", twice.note)
    }

    @Test
    fun whatThePersonChoseIsFoundWhicheverWayTheBankWritesThePayeeAndIsSureForAShop() {
        val taught = CaptureCategory.Taught(mapOf("freshpik" to id("Groceries")), mapOf("freshpik" to 1))
        for (p in listOf("FRESHPIK BANGALORE IN", "Freshpik", "freshpik@ybl", "FRESHPIK 2231")) {
            val a = advise(p, taught)!!
            assertEquals(id("Groceries"), a.categoryId, p); assertEquals(SuggestionSource.LEARNED, a.source, p); assertEquals(Certainty.SURE, a.certainty, p)
        }
        assertNull(advise("FRESHPIK FARMS", taught), "another place with a similar start")
    }

    @Test
    fun aChoiceLearnedUnderTheOldKeyStillWorks() {
        val a = advise("FRESHPIK BANGALORE IN", CaptureCategory.Taught(mapOf("freshpikbangaloreIN".lowercase() to id("Groceries")), emptyMap()))!!
        assertEquals(SuggestionSource.LEARNED, a.source); assertEquals(id("Groceries"), a.categoryId)
    }

    @Test
    fun aPaymentToAPersonIsOnlyAGuessEvenWhenChosenOnceButSureWhenChosenTwice() {
        val once = advise("RAVI KUMAR", CaptureCategory.Taught(mapOf("ravikumar" to id("Rent")), mapOf("ravikumar" to 1)), personLike = PayeeKinds.looksLikePerson("RAVI KUMAR"))!!
        assertEquals(Certainty.CHECK, once.certainty)
        val twice = advise("RAVI KUMAR", CaptureCategory.Taught(mapOf("ravikumar" to id("Rent")), mapOf("ravikumar" to 2)), personLike = true)!!
        assertEquals(Certainty.SURE, twice.certainty)
        assertEquals(Certainty.CHECK, advise("9876501234@ybl", person = true)!!.certainty, "a number's payment defaults to Other, to be looked at")
    }

    @Test
    fun theHistoryOfTheSamePayeeIsUsedAndSaysHowManyEntriesBackIt() {
        val a = advise("Sagar Cafe Pvt", history = HistoryProfile(id("Food"), 3, 0))!!
        assertEquals(SuggestionSource.HISTORY, a.source); assertEquals("3 of your earlier entries", a.note); assertEquals(Certainty.SURE, a.certainty)
        assertEquals(Certainty.CHECK, advise("Sagar Cafe Pvt", history = HistoryProfile(id("Food"), 1, 0))!!.certainty)
        assertEquals(Certainty.CHECK, advise("Sagar Pvt", history = HistoryProfile(id("Food"), 3, 1))!!.certainty, "history that disagrees with itself")
    }

    @Test
    fun otherCategoriesThePayeeHasBeenInAreOfferedAsOneTapAlternatives() {
        val a = advise("Sagar Cafe Pvt", CaptureCategory.Taught(mapOf("sagarcafe" to id("Food")), mapOf("sagarcafe" to 1)), history = HistoryProfile(id("Groceries"), 3, 2, listOf(id("Food"), id("Fun"), id("Other"))))!!
        assertEquals(id("Food"), a.categoryId)
        assertEquals(listOf(id("Groceries"), id("Fun")), a.alternatives, "the history's leader first, then the next, never the one already chosen")
        assertEquals(Certainty.CHECK, a.certainty, "their choice and their history disagree")
    }

    @Test
    fun aRuleBeatsEverythingAndIsAlwaysSure() {
        val a = advise("Swiggy", CaptureCategory.Taught(mapOf("swiggy" to id("Groceries")), mapOf("swiggy" to 9)), rules = listOf(CaptureRule("r", "swiggy", id("Fun"))))!!
        assertEquals(SuggestionSource.RULE, a.source); assertEquals(id("Fun"), a.categoryId); assertEquals(Certainty.SURE, a.certainty); assertEquals("your rule", a.note)
    }

    @Test
    fun theWordsOfANameAreAGuessToCheck() {
        val a = advise("SAI BABA TEA STALL")!!
        assertEquals(SuggestionSource.KEYWORD, a.source); assertEquals(id("Food"), a.categoryId); assertEquals(Certainty.CHECK, a.certainty); assertEquals("a guess from the name", a.note)
    }

    @Test
    fun nothingKnownGivesNothing() {
        assertNull(advise("FRESHPIK BANGALORE IN")); assertNull(advise(null))
    }

    @Test
    fun aCategoryTheyHideOrDeleteIsNeverSuggestedEvenFromWhatTheyChose() {
        assertNull(CaptureCategory.advise("Freshpik", "x", emptyList(), CaptureCategory.Taught(mapOf("freshpik" to "gone"), mapOf("freshpik" to 5)), usable, byName, null))
    }

    @Test
    fun looksLikePersonIsCarefulAboutShops() {
        for (n in listOf("RAVI KUMAR", "Anita Rao", "Ravi K Sharma")) assertEquals(true, PayeeKinds.looksLikePerson(n), n)
        for (n in listOf("SRI LAKSHMI STORES", "RAMESH TIFFIN CENTER", "Swiggy", "Uber", "ravi@ybl", "FRESHPIK", "NEW BOMBAY MEDICALS", null)) assertEquals(false, PayeeKinds.looksLikePerson(n), n.toString())
    }
}
