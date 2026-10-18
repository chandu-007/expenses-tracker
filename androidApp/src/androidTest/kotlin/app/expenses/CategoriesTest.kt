package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CategoriesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() {
        runBlocking { container.startFresh() }
    }

    /** The less frequent places are in the "More" menu on the list. */
    private fun openFromMenu(tag: String) {
        compose.onNodeWithTag("more_menu").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }
    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun seed(id: String, amount: Long, category: String, note: String) = runBlocking {
        container.write { ok ->
            ok.store.save(
                Expense(EntryId(id), System.currentTimeMillis(), AccountId("cash"), Money(amount, inr), listOf(CategorySplit(CategoryId(category), Money(amount, inr)))),
                EntryDetails(merchant = note),
            )
        }
    }

    private fun openManager() {
        openFromMenu("manage_categories")
        waitFor("new_category")
    }

    private fun names(): List<String> = runBlocking { container.write { it.categories.list().map { c -> c.name } } }!!

    @Test
    fun aCategoryCreatedInTheAddFormIsSelectedAndShowsOnTheSavedRow() {
        launch()
        compose.onNodeWithTag("add").performClick()
        waitFor("new_category_chip")
        // The form moves as the keyboard opens, so a tap placed before it settles can miss the chip: tap again until the dialog is there.
        compose.waitUntil(15_000) {
            if (compose.onAllNodes(hasTestTag("cat_name")).fetchSemanticsNodes().isEmpty()) runCatching { compose.onNodeWithTag("new_category_chip").performScrollTo().performClick() }
            compose.onAllNodes(hasTestTag("cat_name")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("cat_name").performTextInput("Pets")
        compose.onNodeWithTag("cat_confirm").assertIsEnabled().performClick()
        waitForText("Pets")
        compose.onNodeWithTag("amount").performTextInput("450")
        compose.onNodeWithTag("note").performTextInput("Dog food")
        compose.onNodeWithTag("save").performClick()
        waitForText("· Pets")
        assertTrue("Pets" in names())
    }

    @Test
    fun aDuplicateNameIsRefusedWhileTypingAndSaveStaysDisabled() {
        launch()
        compose.onNodeWithTag("add").performClick()
        waitFor("new_category_chip")
        compose.onNodeWithTag("new_category_chip").performScrollTo().performClick()
        waitFor("cat_name") // the dialog opens a moment after the tap
        compose.onNodeWithTag("cat_name").performTextInput("food")
        waitForText("There is already a category called")
        compose.onNodeWithTag("cat_confirm").assertIsNotEnabled()
    }

    @Test
    fun renamingKeepsTheEntriesAndShowsTheNewNameOnTheirRows() {
        seed("e1", 25_000, "Food", "Lunch")
        launch(); waitForText("· Food")
        openManager()
        compose.onNodeWithTag("cat_row_Food").performClick()
        compose.onNodeWithTag("cat_name").performTextClearance()
        compose.onNodeWithTag("cat_name").performTextInput("Dining")
        compose.onNodeWithTag("cat_save").performClick()
        waitForText("Dining")
        compose.onNodeWithTag("categories_back").performClick()
        waitForText("· Dining")
        val spend = runBlocking { container.write { it.store.spendByCategory(inr).entries.single().let { e -> e.key!!.value to e.value.minorUnits } } }
        assertEquals("Food" to 25_000L, spend, "the id is unchanged, so the entry still counts under it")
    }

    @Test
    fun aHiddenCategoryIsNotOfferedWhenAddingButAnEditedEntryKeepsIt() {
        seed("e1", 10_000, "Fun", "Movie")
        launch(); waitForText("· Fun")
        openManager()
        compose.onNodeWithTag("cat_row_Fun").performClick()
        compose.onNodeWithTag("cat_hidden").performClick()
        compose.onNodeWithTag("cat_save").performClick()
        waitForText("hidden")
        compose.onNodeWithTag("categories_back").performClick()
        waitFor("add")
        compose.onNodeWithTag("add").performClick()
        waitFor("new_category_chip")
        compose.onAllNodes(hasTestTag("category_chip_Fun")).fetchSemanticsNodes().let { assertTrue(it.isEmpty(), "a hidden category must not be offered") }
        compose.onNodeWithTag("cancel").performClick()
        waitFor("add")
        compose.onNodeWithTag("row_Movie").performClick()
        waitFor("category_chip_Fun")
        compose.onNodeWithTag("category_chip_Fun").assertIsSelected()
    }

    @Test
    fun mergingMovesEverythingAndRemovesTheSourceCategory() {
        seed("e1", 10_000, "Food", "Lunch")
        seed("e2", 5_000, "Groceries", "Veg")
        launch(); waitForText("· Groceries")
        openManager()
        compose.onNodeWithTag("cat_row_Groceries").performClick()
        compose.onNodeWithTag("cat_merge").performScrollTo().performClick()
        compose.onNodeWithTag("merge_into_Food").performClick()
        waitGone("cat_row_Groceries")
        waitForText("2 items")
        val totals = runBlocking { container.write { it.store.spendByCategory(inr).entries.associate { e -> e.key!!.value to e.value.minorUnits } } }
        assertEquals(mapOf("Food" to 15_000L), totals)
        compose.onNodeWithTag("categories_back").performClick()
        waitForText("· Food")
    }

    @Test
    fun aCategoryInUseCannotBeDeletedButAnUnusedOneCan() {
        seed("e1", 10_000, "Food", "Lunch")
        launch(); waitFor("add")
        openManager()
        compose.onNodeWithTag("cat_row_Food").performClick()
        compose.onNodeWithTag("cat_delete").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("cat_delete_reason").performScrollTo().assertExists()
        compose.onNodeWithTag("cat_cancel").performClick()
        compose.onNodeWithTag("cat_row_Other").performClick()
        compose.onNodeWithTag("cat_delete").performScrollTo().assertIsEnabled().performClick()
        waitGone("cat_row_Other")
        assertTrue("Other" !in names())
    }

    @Test
    fun reorderingChangesTheOrderAndTheEndsCannotMoveFurther() {
        launch(); waitFor("add")
        openManager()
        compose.onNodeWithTag("up_Food").assertIsNotEnabled()
        compose.onNodeWithTag("down_Other").assertIsNotEnabled()
        compose.onNodeWithTag("down_Food").performClick()
        compose.waitUntil(10_000) { names().take(2) == listOf("Groceries", "Food") }
        assertEquals(listOf("Groceries", "Food", "Transport"), names().take(3))
    }

    @Test
    fun aSubcategoryCanBeCreatedInsideAParentAndIsShownIndented() {
        launch(); waitFor("add")
        openManager()
        compose.onNodeWithTag("new_category").performClick()
        compose.onNodeWithTag("cat_name").performTextInput("Cafe")
        compose.onNodeWithTag("parent_menu").performClick()
        compose.onNodeWithTag("parent_item_Food").performClick()
        compose.onNodeWithTag("cat_confirm").performClick()
        waitForText("Cafe")
        val cafe = runBlocking { container.write { it.categories.list().first { c -> c.name == "Cafe" } } }!!
        assertEquals("Food", cafe.parentId)
        val order = names()
        assertEquals(order.indexOf("Food") + 1, order.indexOf("Cafe"), "a subcategory is listed right after its parent")
    }
}
