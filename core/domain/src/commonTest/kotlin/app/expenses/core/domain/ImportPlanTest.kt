package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImportPlanTest {
    private fun txn(i: Int, text: String, flow: Flow = Flow.SPENDING, category: String? = null, sub: String? = null, account: String? = null) =
        StatementTxn(i, LocalDate(2026, 10, 1 + i), flow, 10_000, text, category = category, subcategory = sub, accountName = account)

    private val food = CategoryRef("food", "Food", null, forExpense = true, forIncome = false)
    private val groceries = CategoryRef("groc", "Groceries", "food", forExpense = true, forIncome = false)
    private val salary = CategoryRef("sal", "Salary", null, forExpense = false, forIncome = true)
    private val other = CategoryRef("oth", "Other", null, forExpense = true, forIncome = false)
    private val cats = listOf(food, groceries, salary, other)

    @Test
    fun aCategoryIsMatchedByNameWhateverTheCaseAndASubCategoryUnderItsParentFirst() {
        assertEquals("food", ImportCategories.match("FOOD ", null, false, cats))
        assertEquals("groc", ImportCategories.match("Food", "groceries", false, cats), "the sub-category under its parent")
        assertEquals("groc", ImportCategories.match("Shopping", "Groceries", false, cats), "a sub-category found even when the parent is not the same")
        assertEquals("food", ImportCategories.match("Food", "Takeaway", false, cats), "an unknown sub-category falls back to the category")
        assertNull(ImportCategories.match("Travel", null, false, cats))
        assertNull(ImportCategories.match("Salary", null, false, cats), "an income category is not offered for spending")
        assertEquals("sal", ImportCategories.match("salary", null, true, cats))
        assertNull(ImportCategories.match(null, null, false, cats))
        assertNull(ImportCategories.match(" ", "", false, cats))
    }

    @Test
    fun theCategoriesTheFileHasThatThePersonLacksAreListedOnceEach() {
        val txns = listOf(txn(0, "a", category = "Food"), txn(1, "b", category = "Travel"), txn(2, "c", category = "travel "), txn(3, "d", Flow.INCOME, category = "Travel"), txn(4, "e", sub = "Fuel"), txn(5, "f"))
        assertEquals(
            listOf(ImportCategories.NewCategory("Travel", false), ImportCategories.NewCategory("Travel", true), ImportCategories.NewCategory("Fuel", false)),
            ImportCategories.unmatched(txns, cats), "Travel for spending and for income are two; a row with no category names none",
        )
    }

    private val bank = ImportAccount("bank", "Bank")
    private val cash = ImportAccount("cash", "Cash")
    private val card = ImportAccount("card", "HDFC Regalia", isCard = true, last4 = "1234")

    @Test
    fun anotherAppsRowsFindTheirCategoriesAndAccountsAndWhatIsLeftGoesToOtherUnlessTheyAreToBeCreated() {
        val txns = listOf(txn(0, "a", category = "Food", account = "cash"), txn(1, "b", category = "Travel", account = "Unknown"), txn(2, "c"), txn(3, "d", Flow.INCOME, category = "Salary", account = "Bank"))
        val plan = ImportPlanner.plan(txns, bank, listOf(bank, cash, card), cats, fromOtherApp = true)
        val out = assertNotNull(plan.txns)
        assertEquals(listOf("food", "oth", null, "sal"), out.map { it.categoryId }, "Travel has no match and goes to Other; no category stays none")
        assertEquals(listOf("cash", null, null, "bank"), out.map { it.accountId }, "an account the file names that the person has is used, else the chosen one")
        assertEquals(listOf(ImportCategories.NewCategory("Travel", false)), plan.newCategories)
        assertEquals(3, plan.withCategory); assertEquals(1, plan.onOwnAccount, "the row that names the import's own account is not counted")
        val create = ImportPlanner.plan(txns, bank, listOf(bank, cash, card), cats, fromOtherApp = true, createCategories = true)
        assertEquals(listOf("food", null, null, "sal"), create.txns!!.map { it.categoryId }, "to be created: left for the creation to fill")
        val noOther = ImportPlanner.plan(txns, bank, listOf(bank, cash), cats - other, fromOtherApp = true)
        assertEquals(listOf("food", null, null, "sal"), noOther.txns!!.map { it.categoryId }, "without an Other the row has no category")
    }

    @Test
    fun twoAccountsWithTheSameNameAreNotGuessedBetween() {
        val twin = ImportAccount("cash2", "CASH")
        val plan = ImportPlanner.plan(listOf(txn(0, "a", account = "Cash")), bank, listOf(bank, cash, twin), cats, fromOtherApp = true)
        assertNull(plan.txns!!.single().accountId)
    }

    @Test
    fun aBankStatementsCardPaymentsAreHeldBackUntilTheChoiceIsMadeWhenTwoCardsCannotBeToldApart() {
        val second = ImportAccount("card2", "ICICI Amazon", isCard = true)
        val txns = listOf(txn(0, "UPI SWIGGY"), txn(1, "CREDIT CARD PAYMENT"))
        val plan = ImportPlanner.plan(txns, bank, listOf(bank, card, second), cats, fromOtherApp = false)
        assertNull(plan.txns, "no choice yet")
        assertEquals(listOf("UPI SWIGGY"), plan.shown.map { it.description }, "the preview counts what is not in doubt")
        val chosen = ImportPlanner.plan(txns, bank, listOf(bank, card, second), cats, false, pick = CardPayments.Side.BANK to CardPayments.Pick(CardPaymentChoice.TRANSFER, "card2"))
        assertEquals(listOf(null, "card2"), chosen.txns!!.map { it.counterAccount })
        assertEquals(1, chosen.transfers)
        val stale = ImportPlanner.plan(txns, bank, listOf(bank, card, second), cats, false, pick = CardPayments.Side.CARD to CardPayments.Pick(CardPaymentChoice.SKIP))
        assertNull(stale.txns, "a choice made for the other kind of statement does not carry over")
        val gone = ImportPlanner.plan(txns, bank, listOf(bank, card, second), cats, false, pick = CardPayments.Side.BANK to CardPayments.Pick(CardPaymentChoice.TRANSFER, "deleted"))
        assertEquals(null, gone.txns, "an account that is no longer there is not used")
    }

    @Test
    fun withOneCardTheTransferIsTheStartingChoiceAndWithNoneItIsSpending() {
        val txns = listOf(txn(0, "CC PAYMENT"))
        assertEquals(listOf("card"), ImportPlanner.plan(txns, bank, listOf(bank, card), cats, false).txns!!.map { it.counterAccount })
        val none = ImportPlanner.plan(txns, bank, listOf(bank, cash), cats, false)
        assertEquals(listOf(null), none.txns!!.map { it.counterAccount }); assertEquals(1, none.txns!!.size)
        assertNotNull(none.found)
    }

    @Test
    fun onACardsOwnStatementThePaymentReceivedIsSkippedByDefaultAndMayBeATransferFromTheBank() {
        val txns = listOf(txn(0, "SWIGGY"), txn(1, "PAYMENT RECEIVED - THANK YOU", Flow.INCOME))
        val plan = ImportPlanner.plan(txns, card, listOf(bank, card), cats, false)
        assertEquals(listOf("SWIGGY"), plan.txns!!.map { it.description }); assertEquals(1, plan.skippedCardPayments)
        val transfer = ImportPlanner.plan(txns, card, listOf(bank, card), cats, false, pick = CardPayments.Side.CARD to CardPayments.Pick(CardPaymentChoice.TRANSFER, "bank"))
        assertEquals(listOf(null, "bank"), transfer.txns!!.map { it.counterAccount })
        assertEquals(Flow.INCOME, transfer.txns!![1].flow, "from the bank into the card: the importer reads the direction from the flow")
    }

    @Test
    fun aFileWithNoCardPaymentsIsPassedThroughUntouched() {
        val txns = listOf(txn(0, "SWIGGY"), txn(1, "SALARY", Flow.INCOME))
        val plan = ImportPlanner.plan(txns, bank, listOf(bank, card), cats, false)
        assertEquals(txns, plan.txns); assertEquals(txns, plan.shown); assertNull(plan.found)
    }

    @Test
    fun withOneCardALineForOtherDigitsOrAnotherBankKeepsTheImportWaitingForAChoice() {
        val only = ImportAccount("card", "HDFC Regalia", isCard = true, last4 = "1234")
        fun plan(line: String) = ImportPlanner.plan(listOf(txn(0, line)), bank, listOf(bank, only), cats, false)
        assertNotNull(plan("CREDIT CARD PAYMENT").txns); assertNotNull(plan("CC PAYMENT XX1234").txns)
        assertNull(plan("CREDIT CARD PAYMENT XX9999").txns, "someone else's card"); assertNull(plan("BILLDESK ICICI CREDIT CARD").txns); assertNull(plan("CRED").txns)
        assertEquals(emptyList(), plan("CRED").shown, "a line in doubt is not counted in the preview")
    }
}
