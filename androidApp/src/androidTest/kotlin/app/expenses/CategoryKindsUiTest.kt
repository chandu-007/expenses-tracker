package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Categories screen keeps expense and income categories on separate tabs, and a category's kind can change only while it is unused by the other kind. */
@RunWith(AndroidJUnit4::class)
class CategoryKindsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 10_000) = compose.waitUntil(ms) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun kinds() = runBlocking { container.write { ok -> ok.categories.list().associate { it.name to it.kind } } } ?: emptyMap()

    private fun openManager() {
        compose.waitForIdle(); scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); tap("manage_categories"); waitFor("new_category")
    }

    private fun spend(category: String) = runBlocking {
        container.write { ok ->
            val m = Money(5_000, Currency.INR)
            ok.store.save(Expense(EntryId("kind-spend"), System.currentTimeMillis(), AccountId("cash"), m, listOf(CategorySplit(CategoryId(category), m))), EntryDetails(merchant = "Lunch"))
        }
    }

    @Test fun tabsListOnlyTheirOwnKind() {
        openManager()
        waitFor("cat_row_Food")
        assertFalse(has("cat_row_income:salary"))
        tap("cat_tab_income")
        waitFor("cat_row_income:salary")
        listOf("salary", "interest", "giftsreceived", "refundsandcashback", "otherincome").forEach { assertTrue(has("cat_row_income:$it"), it) }
        assertFalse(has("cat_row_Food"))
        assertFalse(has("suggested_categories"))
        tap("cat_tab_expense")
        waitFor("cat_row_Food")
        assertFalse(has("cat_row_income:salary"))
        assertTrue(has("suggested_categories"))
    }

    @Test fun creatingOnTheIncomeTabMakesAnIncomeCategory() {
        openManager()
        tap("cat_tab_income"); tap("new_category"); waitFor("cat_name")
        node("cat_name").performTextInput("Side gigs")
        tap("cat_confirm")
        compose.waitUntil(10_000) { kinds().containsKey("Side gigs") }
        assertEquals(CategoryKind.INCOME, kinds()["Side gigs"])
        tap("cat_tab_expense"); waitFor("cat_row_Food")
        assertFalse(has("cat_row_Side gigs"))
    }

    @Test fun changingAnUnusedCategoryMovesItBetweenTabs() {
        val id = runBlocking { container.write { it.categories.create("Pets").id } }
        openManager()
        tap("cat_row_$id"); tap("cat_kind_INCOME")
        compose.waitUntil(10_000) { kinds()["Pets"] == CategoryKind.INCOME }
        assertFalse(has("cat_kind_problem"))
        tap("cat_cancel"); waitFor("cat_row_Food")
        assertFalse(has("cat_row_$id"))
        tap("cat_tab_income")
        waitFor("cat_row_$id")
    }

    @Test fun aCategoryInUseRefusesTheOtherKind() {
        spend("Food")
        openManager()
        tap("cat_row_Food"); tap("cat_kind_INCOME")
        waitFor("cat_kind_problem")
        assertEquals(CategoryKind.EXPENSE, kinds()["Food"])
    }

    @Test fun mergeOffersNoCrossKindTarget() {
        openManager()
        tap("cat_row_Food"); tap("cat_merge")
        waitFor("merge_into_Groceries")
        assertFalse(has("merge_into_income:salary"))
    }
}
