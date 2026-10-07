package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val day = 86_400_000L

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        scenario?.close()
        resetApp()
    }

    private fun spend(id: String, rupees: Long, category: String, merchant: String, daysAgo: Long = 0, note: String? = null) = runBlocking {
        container.write { ok ->
            ok.store.save(
                Expense(EntryId(id), System.currentTimeMillis() - daysAgo * day, AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))),
                EntryDetails(merchant = merchant, note = note),
            )
        }
    }

    private fun open() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        compose.onNodeWithTag("open_search").performClick()
        waitFor("search_text")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun resultCount() = compose.onAllNodes(hasTestTag("search_results"), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.children?.size ?: 0

    private fun type(tag: String, text: String) = compose.onNodeWithTag(tag).performTextInput(text)

    @Test
    fun typingFindsMatchesAsYouGoAndSaysWhenThereAreNone() {
        spend("a", 250, "Food", "Swiggy"); spend("b", 400, "Food", "Zomato"); spend("c", 90, "Transport", "Uber", note = "to the airport")
        open()
        waitFor("search_hint")
        type("search_text", "swig")
        waitFor("result_a"); waitGone("result_b")
        compose.onNodeWithTag("search_text").performTextClearance()
        type("search_text", "airport")
        waitFor("result_c"); waitGone("result_a")
        compose.onNodeWithTag("search_text").performTextClearance()
        type("search_text", "nothingmatchesthis")
        waitFor("search_none")
        compose.onNodeWithTag("search_text").performTextClearance()
        waitFor("search_hint")
    }

    @Test
    fun aShortWordNextToALongOneStillNarrowsTheResults() {
        spend("a", 100, "Food", "Merchant 44"); spend("b", 100, "Food", "Merchant 31")
        open()
        type("search_text", "merchant")
        waitFor("result_a"); waitFor("result_b")
        type("search_text", " 4")
        waitFor("result_a"); waitGone("result_b") // the 4 is not ignored just because it is short
    }

    @Test
    fun anAmountRangeIsInclusiveAndBadAmountsAreExplained() {
        spend("a", 100, "Food", "Cafe"); spend("b", 250, "Food", "Cafe"); spend("c", 400, "Food", "Cafe")
        open()
        type("search_min", "100"); type("search_max", "250")
        waitFor("result_a"); waitFor("result_b"); waitGone("result_c")
        compose.onNodeWithTag("search_min").performTextClearance(); type("search_min", "250.01")
        waitText("search_problem", "above the largest") // 250.01 is above the largest, 250
        compose.onNodeWithTag("search_max").performTextClearance()
        waitFor("result_c"); waitGone("result_b") // with no upper limit, only the 400 is above 250.01
        compose.onNodeWithTag("search_min").performTextClearance(); type("search_min", "900")
        waitFor("search_none")
        compose.onNodeWithTag("search_min").performTextClearance(); type("search_min", "1.2.3")
        waitText("search_problem", "numbers")
    }

    @Test
    fun aCategoryFilterIncludesItsSubcategoriesAndKindSeparatesIncomeFromSpending() {
        val cafe = runBlocking { container.write { it.categories.create("Cafe", parentId = "Food").id } }!!
        spend("food", 100, "Food", "Biryani"); spend("cafe", 50, cafe, "Chai"); spend("fun", 70, "Fun", "Movie")
        runBlocking { container.write { it.store.save(Income(EntryId("pay"), System.currentTimeMillis(), AccountId("bank"), Money(500_000, inr), CategoryId("Food")), EntryDetails(merchant = "Refund from restaurant")) } }
        open()
        compose.onNodeWithTag("search_category").performClick()
        waitFor("search_cat_Food"); compose.onNodeWithTag("search_cat_Food").performClick()
        waitFor("result_food"); waitFor("result_cafe"); waitFor("result_pay"); waitGone("result_fun")
        compose.onNodeWithTag("search_kind_income").performClick()
        waitFor("result_pay"); waitGone("result_food"); waitGone("result_cafe")
        compose.onNodeWithTag("search_kind_income").performClick()
        compose.onNodeWithTag("search_kind_expense").performClick()
        waitGone("result_pay"); waitFor("result_food")
    }

    @Test
    fun theDateChipsKeepOnlyWhatIsInRange() {
        spend("new", 100, "Food", "Cafe", daysAgo = 2); spend("old", 100, "Food", "Cafe", daysAgo = 90)
        open()
        type("search_text", "cafe")
        waitFor("result_new"); waitFor("result_old")
        compose.onNodeWithTag("search_range_LAST_30_DAYS").performClick()
        waitFor("result_new"); waitGone("result_old")
        compose.onNodeWithTag("search_range_LAST_30_DAYS").performClick() // choosing it again clears it
        waitFor("result_old")
    }

    @Test
    fun anEntryEditedFromTheResultsIsShownAsItNowIs() {
        spend("a", 100, "Food", "Swiggy"); spend("b", 200, "Food", "Swiggy")
        open()
        type("search_text", "swiggy")
        waitFor("result_a"); waitFor("result_b")
        compose.onNodeWithTag("result_a").performClick()
        waitFor("note")
        compose.onNodeWithTag("note").performTextClearance(); compose.onNodeWithTag("note").performTextInput("Zomato")
        compose.onNodeWithTag("save").performClick()
        waitFor("search_text")
        waitGone("result_a") // it no longer matches "swiggy"
        waitFor("result_b")
    }

    @Test
    fun moreThanAHundredMatchesShowTheMostRecentAndSaySo() {
        runBlocking { container.write { ok -> repeat(120) { i -> ok.store.save(Expense(EntryId("e$i"), System.currentTimeMillis() - i * 1_000L, AccountId("cash"), Money(100, inr)), EntryDetails(merchant = "Chai stall")) } } }
        open()
        type("search_text", "chai")
        waitText("search_count", "100 results, the most recent")
        waitFor("result_e0"); waitGone("result_e119")
    }

    @Test
    fun backLeavesTheSearchAndTheNextVisitStartsClean() {
        spend("a", 100, "Food", "Swiggy")
        open()
        type("search_text", "swiggy"); waitFor("result_a")
        compose.onNodeWithTag("search_back").performClick()
        waitFor("add")
        compose.onNodeWithTag("open_search").performClick()
        waitFor("search_hint")
    }
}
