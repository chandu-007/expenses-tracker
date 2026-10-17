package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Income and expenses each choose from their own categories: in the add form, repeating items, budgets, the inbox and capture rules. */
@RunWith(AndroidJUnit4::class)
class IncomeCategoriesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private val incomeIds = listOf("income:salary", "income:interest", "income:giftsreceived", "income:refundsandcashback", "income:otherincome")
    private val expenseIds = listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other")

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 10_000) = compose.waitUntil(ms) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun firstVisible(income: Boolean) = db { ok -> ok.categories.list().first { !it.hidden && it.kind.serves(income) }.id }
    // The keyboard shrinks the form, so a control may be below the visible part: scroll to it first (dialogs and menus do not scroll).
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun openAdd() { tap("add"); waitFor("amount") }
    private fun openFromMenu(tag: String) { tap("more_menu"); tap(tag) }

    private fun assertOffers(prefix: String, shown: List<String>, notShown: List<String>) {
        shown.forEach { assertTrue(has("$prefix$it"), "$prefix$it should be offered") }
        notShown.forEach { assertTrue(!has("$prefix$it"), "$prefix$it must not be offered") }
    }

    @Test
    fun theFormOffersIncomeCategoriesForIncomeAndExpenseOnesForAnExpenseAndSwitchingSwapsTheChoice() {
        launch(); openAdd()
        assertOffers("category_chip_", expenseIds, incomeIds)
        tap("kind_INCOME")
        compose.waitUntil(10_000) { has("category_chip_income:salary") }
        assertOffers("category_chip_", incomeIds, expenseIds)
        tap("kind_EXPENSE")
        compose.waitUntil(10_000) { has("category_chip_Food") }
        assertOffers("category_chip_", expenseIds, incomeIds)
        compose.onNodeWithTag("category_chip_${firstVisible(false)}").assertIsSelected() // an income category is not left on an expense
    }

    @Test
    fun anIncomeIsStoredWithTheChosenIncomeCategoryAndItIsRememberedForNext() {
        db { ok -> ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, Currency.INR, 0, false)) }
        launch(); openAdd()
        tap("kind_INCOME"); waitFor("category_chip_income:salary")
        node("amount").performTextInput("50000"); tap("account_chip_Bank")
        // Salary is already chosen for a new income; tapping it again would clear it (an income may have no category)
        compose.waitUntil(10_000) { compose.onNodeWithTag("category_chip_income:salary", useUnmergedTree = true).fetchSemanticsNode().config.let { c -> c.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected) && c[androidx.compose.ui.semantics.SemanticsProperties.Selected] } }
        tap("save"); waitFor("add")
        compose.waitUntil(10_000) { db { ok -> ok.db.schemaQueries.recentEntries(10).executeAsList().size } == 1 }
        val e = db { ok -> ok.db.schemaQueries.recentEntries(10).executeAsList().single() }
        assertEquals("INCOME", e.kind)
        assertEquals("income:salary", e.category_id)
        assertEquals("income:salary", runBlocking { container.write { it.templates.setting("last_income_category") } })
        // the expense default is untouched by it
        assertEquals(null, runBlocking { container.write { it.templates.setting("last_category") } })
    }

    @Test
    fun aNewIncomeStartsOnTheLastIncomeCategoryUsedAndAnExpenseOnTheLastExpenseOne() {
        db { ok -> ok.templates.putSetting("last_income_category", "income:interest"); ok.templates.putSetting("last_category", "Fun") }
        launch(); openAdd()
        compose.onNodeWithTag("category_chip_Fun").assertIsSelected()
        tap("kind_INCOME"); waitFor("category_chip_income:interest")
        compose.onNodeWithTag("category_chip_income:interest").assertIsSelected()
        tap("kind_EXPENSE"); waitFor("category_chip_Fun")
        compose.onNodeWithTag("category_chip_Fun").assertIsSelected()
    }

    @Test
    fun withNothingUsedYetANewIncomeStartsOnTheFirstIncomeCategory() {
        launch(); openAdd()
        tap("kind_INCOME"); waitFor("category_chip_income:salary")
        compose.onNodeWithTag("category_chip_${firstVisible(true)}").assertIsSelected()
    }

    @Test
    fun aCategoryCreatedFromTheIncomeFormIsAnIncomeCategoryAndFromTheExpenseFormAnExpenseOne() {
        launch(); openAdd()
        tap("kind_INCOME"); waitFor("category_chip_income:salary")
        tap("new_category_chip"); waitFor("cat_name")
        node("cat_name").performTextInput("Freelance"); tap("cat_confirm")
        compose.waitUntil(10_000) { db { ok -> ok.categories.list().any { it.name == "Freelance" } } }
        val freelance = db { ok -> ok.categories.list().first { it.name == "Freelance" } }
        assertEquals(CategoryKind.INCOME, freelance.kind)
        waitFor("category_chip_${freelance.id}"); compose.onNodeWithTag("category_chip_${freelance.id}").assertIsSelected()

        tap("kind_EXPENSE"); waitFor("category_chip_Food")
        assertTrue(!has("category_chip_${freelance.id}"), "an income category is not offered for an expense")
        tap("new_category_chip"); waitFor("cat_name")
        node("cat_name").performTextClearance(); node("cat_name").performTextInput("Gadgets"); tap("cat_confirm")
        compose.waitUntil(10_000) { db { ok -> ok.categories.list().any { it.name == "Gadgets" } } }
        assertEquals(CategoryKind.EXPENSE, db { ok -> ok.categories.list().first { it.name == "Gadgets" }.kind })
    }

    @Test
    fun aRepeatingIncomeOffersIncomeCategoriesAndARepeatingExpenseExpenseOnes() {
        launch(); openFromMenu("open_recurring"); tap("new_recurring"); waitFor("rec_name")
        assertOffers("rec_cat_", expenseIds, incomeIds)
        tap("rec_kind_INCOME"); waitFor("rec_cat_income:salary")
        assertOffers("rec_cat_", incomeIds, expenseIds)
        compose.onNodeWithTag("rec_cat_${firstVisible(true)}").assertIsSelected()
        tap("rec_kind_EXPENSE"); waitFor("rec_cat_Food")
        assertOffers("rec_cat_", expenseIds, incomeIds)
        compose.onNodeWithTag("rec_cat_${firstVisible(false)}").assertIsSelected()
    }

    @Test
    fun theBudgetScreenOffersNoIncomeCategoryForALimit() {
        launch(); tap("budget_summary"); waitFor("budget_row_overall")
        for (id in expenseIds) {
            compose.onNode(hasScrollAction() and hasAnyDescendant(hasTestTag("budget_row_overall")), useUnmergedTree = true).performScrollToNode(hasTestTag("budget_row_$id"))
            waitFor("budget_row_$id")
            assertOffers("budget_row_", listOf(id), incomeIds) // the income rows would sit among these, if they were there at all
        }
    }

    @Test
    fun theInboxAndCaptureRulesOfferExpenseCategoriesOnly() {
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 111111111111). Not you? Call 18002586161", 5_000) }
        launch(); openFromMenu("open_capture"); tap("open_rules"); waitFor("rule_pattern")
        waitFor("rule_category_Food")
        assertOffers("rule_category_", expenseIds, incomeIds)
        tap("rules_back"); tap("capture_back"); openFromMenu("open_inbox"); waitFor("inbox_back")
        val id = db { ok -> ok.inbox.list().first().id }
        tap("inbox_change_$id"); waitFor("inbox_category_${id}_Food")
        assertOffers("inbox_category_${id}_", expenseIds, incomeIds)
    }
}
