package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Credit wording (all texts invented) and the income half of [CategorySuggest]: what is a guess, what the person's own choices do, and that income and expense choices never mix. */
class IncomeWordsTest {
    private val names = (StarterCategories.INCOME_FIRST + StarterCategories.INCOME_MORE)
    private val byName = names.associate { it.lowercase() to StarterCategories.incomeId(it) }
    private val usable = byName.values.toSet()
    private fun id(n: String) = StarterCategories.incomeId(n)
    private fun name(body: String) = IncomeWords.categoryNameFor(body)

    @Test
    fun clearWordingPointsAtAnIncomeCategory() {
        assertEquals("Interest", name("Monthly interest of INR.1,000.00 earned on your Savings A/c XX0000 has been credited to your A/C on 31/08/26."))
        assertEquals("Interest", name("Your A/c XX1234 credited by Rs.512.00 on 30-09-26. Int.Pd:01-07-26 to 30-09-26"))
        assertEquals("Interest", name("INR 840.00 credited to A/c XX1234 on 30-09-26 for SB INT CR"))
        assertEquals("Salary", name("Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 01-OCT-26 for NEFT Cr-HDFC0000001-ACME SOFTWARE PVT LTD-RAVI KUMAR-SALARY OCT"))
        assertEquals("Salary", name("Rs.52,000.00 credited to A/c XX1234 on 01-10-26. Info: NEFT/SAL/ACME TRADERS"))
        assertEquals("Salary", name("Your account has been credited with INR 61,500 -SAL- OCT 2026"))
        assertEquals("Dividends", name("INR 1,250.00 credited to A/c XX1234 on 12-09-26 towards dividend of SAMPLE LTD"))
        assertEquals("Refunds and cashback", name("Cashback of Rs.35.00 credited to A/c XX1234"))
        assertEquals("Refunds and cashback", name("Refund of Rs.499.00 from SAMPLE SHOP credited to your account"))
        assertEquals("Reimbursement", name("INR 3,200.00 credited to A/c XX1234 for travel reimbursement"))
        assertEquals("Rent received", name("Rs.18,000.00 credited by VPA tenant@ybl UPI note: Rent Oct"))
        assertEquals("Gifts received", name("Rs.500.00 credited by VPA aunt@ybl UPI note: birthday gift"))
    }

    @Test
    fun everyCategoryTheWordsCanPointAtIsAStarterIncomeCategoryANewInstallHas() {
        val samples = listOf("interest credited", "salary credited", "dividend credited", "cashback credited", "reimbursement credited", "rent credited", "gift credited")
        val named = samples.map { checkNotNull(name(it)) { it } }
        assertEquals(7, named.toSet().size)
        for (n in named) assertTrue(n in names, "$n is a starter income category (new installs get them all; an older install adds the missing ones from Suggested categories)")
    }

    @Test
    fun aCreditFromAPersonOrABankFooterSaysNothing() {
        assertNull(name("Rs.5,000.00 credited to A/c XX1234 on 06-10-26 by VPA ravi@ybl (UPI Ref 628374659999)."))
        assertNull(name("Your A/c *1234 is credited with Rs.2000.00 on 13-09-26 by RAHUL SHARMA. RRN 000000000000. Available balance is Rs. 2154.17 - Indian Bank"))
        assertNull(name("Rs. 45,000 received in A/c xx0000 on 11-Jun-26 from SENDER NAME via IMPS (Ref ID: 000000000000). Avl. Bal. Rs. 1,23,456.78"))
    }

    @Test
    fun wordsInsideOtherWordsAreNotRead() {
        assertNull(name("Credited Rs.500 by VPA salman@ybl"), "salman is not salary")
        assertNull(name("Credited Rs.500 by VPA current@ybl"), "current is not rent")
        assertNull(name("Credited Rs.500 from SALAD BAR"), "salad is not sal")
        assertNull(name("Credited Rs.500 by VPA divya@ybl"), "no dividend")
        assertNull(name("Credited Rs.500 from INTERNATIONAL TRADING CO"), "international is not interest")
    }

    @Test
    fun twoDifferentCategoriesInOneTextGiveNothing() {
        assertNull(name("Interest on your refund of Rs.10 credited"))
        assertNull(name("Salary and travel reimbursement credited"))
        assertEquals("Salary", name("SALARY SALARY credited for the salary of October"), "the same category twice is still one")
    }

    private fun suggest(
        payee: String?, body: String = "x", rules: List<CaptureRule> = emptyList(), learned: Map<String, String> = emptyMap(), used: Set<String> = usable, by: Map<String, String> = byName, history: String? = null,
    ) = CategorySuggest.suggestIncome(payee, body, rules, learned, used, by, history)

    @Test
    fun ownRuleThenOwnChoiceThenHistoryThenWordsInThatOrder() {
        val rule = CaptureRule("r", "acme", id("Freelance and business"))
        val learned = mapOf(PayeeKey.incomeOf("ACME SOFTWARE PVT LTD")!! to id("Salary"))
        assertEquals(CategorySuggestion(id("Freelance and business"), SuggestionSource.RULE), suggest("ACME SOFTWARE PVT LTD", "salary", listOf(rule), learned, history = id("Interest")))
        assertEquals(CategorySuggestion(id("Salary"), SuggestionSource.LEARNED), suggest("ACME SOFTWARE PVT LTD", "interest", learned = learned, history = id("Interest")))
        assertEquals(CategorySuggestion(id("Interest"), SuggestionSource.HISTORY), suggest("ACME SOFTWARE PVT LTD", "salary", history = id("Interest")))
        assertEquals(CategorySuggestion(id("Salary"), SuggestionSource.CREDIT_WORDS), suggest("ACME SOFTWARE PVT LTD", "NEFT-SALARY OCT"))
        assertNull(suggest("RAVI KUMAR", "credited by RAVI KUMAR"))
    }

    @Test
    fun anExpenseRuleOrAMissingOrHiddenCategoryNeverFiresOnMoneyIn() {
        val expenseRule = CaptureRule("r", "swiggy", StarterCategories.expenseId("Food"))
        assertNull(suggest("Swiggy", "credited", listOf(expenseRule), used = usable), "a rule for an expense category is not an income rule")
        assertNull(suggest("X Ltd", "salary", used = usable - id("Salary")), "a hidden or deleted category is not suggested")
        assertNull(suggest("X Ltd", "dividend", by = byName - "dividends"), "no category by that name: no guess")
        assertNull(suggest("X Ltd", "x", learned = mapOf(PayeeKey.incomeOf("X Ltd")!! to "gone")), "a learned category that is gone is skipped")
    }

    @Test
    fun anIncomeChoiceAndAnExpenseChoiceForOneNameNeverMeet() {
        val both = mapOf("ravikumar" to StarterCategories.expenseId("Gifts and donations"), PayeeKey.incomeOf("Ravi Kumar")!! to id("Rent received"))
        assertEquals(CategorySuggestion(id("Rent received"), SuggestionSource.LEARNED), suggest("RAVI KUMAR", learned = both))
        assertEquals(CategorySuggestion(StarterCategories.expenseId("Gifts and donations"), SuggestionSource.LEARNED),
            CategorySuggest.suggest("RAVI KUMAR", "x", emptyList(), both, StarterCategories.expenseOrder.map { StarterCategories.expenseId(it) }.toSet(), StarterCategories.expenseOrder.associate { it.lowercase() to StarterCategories.expenseId(it) }))
        assertNull(suggest("RAVI KUMAR", learned = mapOf("ravikumar" to id("Salary"))), "the old expense-style key is not an income choice")
        assertNotEquals(PayeeKey.of("Ravi Kumar"), PayeeKey.incomeOf("Ravi Kumar"))
        assertEquals(true, PayeeKey.isIncomeKey(PayeeKey.incomeOf("Ravi Kumar")!!)); assertEquals(false, PayeeKey.isIncomeKey(PayeeKey.of("Ravi Kumar")!!))
        assertEquals(PayeeKey.incomeOf("RAVI KUMAR"), PayeeKey.incomeOf("ravi.kumar@okaxis".replace(".", " ").substringBefore('@')), "the payer's name is keyed as a payee's is")
    }

    @Test
    fun wordsOfACreditAreNeverSure() {
        for (p in listOf(true, false)) assertEquals(Certainty.CHECK, SuggestionConfidence.of(SuggestionSource.CREDIT_WORDS, mixed = false, person = p, uses = 9, history = HistoryProfile(id("Salary"), 9, 0), suggested = id("Salary")))
    }
}
