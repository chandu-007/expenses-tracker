package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
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
import app.expenses.core.storage.EntryDetails
import app.expenses.ui.WideLayoutTestHook
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * The two-pane layout. The test display cannot be resized from inside a test, so [WideLayoutTestHook] stands in for the window width: the decision to use
 * two panes follows the forced width, and the panes are laid out inside the real (narrow) window, squeezed but complete. What this proves is the wiring
 * (placeholder, editor beside the list, Back, save, the narrow window unchanged); it does not prove how the panes look on a real tablet.
 */
@RunWith(AndroidJUnit4::class)
class WidePaneUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); WideLayoutTestHook.widthDp = null; runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun back() { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } }

    private fun launch(widthDp: Int?) {
        WideLayoutTestHook.widthDp = widthDp
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
    }

    private fun seed(id: String, merchant: String, minor: Long) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis(), AccountId("cash"), Money(minor, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(minor, Currency.INR)))), EntryDetails(merchant = merchant)) }
    }

    private fun left(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
    private fun right(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.right

    @Test
    fun aWideWindowShowsHomeWithAPlaceholderBesideItUntilAnEntryIsOpened() {
        seed("e1", "Cafe", 10_000)
        launch(1000); waitFor("row_Cafe")
        waitFor("wide_placeholder"); waitFor("wide_list_pane"); waitFor("wide_detail_pane")
        assertTrue(has("add"), "Home is still whole on the left")
        assertTrue(!has("amount"), "no editor while nothing is open")
        assertTrue(right("wide_list_pane") <= left("wide_detail_pane") + 1f, "the list pane is left of the detail pane")
    }

    @Test
    fun openingARowShowsTheEditorBesideTheListAndCancelOrBackClearsIt() {
        seed("e1", "Cafe", 10_000)
        launch(1000); waitFor("row_Cafe")
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick()
        waitFor("amount"); waitGone("wide_placeholder")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "100.00")
        assertTrue(has("row_Cafe") && has("add"), "the list stays up while the entry is open")
        assertTrue(left("amount") >= right("row_Cafe") - 1f, "the editor is to the right of the row, not over it")

        compose.onNodeWithTag("cancel").performClick()
        waitGone("amount"); waitFor("wide_placeholder")

        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick()
        waitFor("amount")
        back() // Back closes the entry first, it does not leave the app
        waitGone("amount"); waitFor("wide_placeholder")
        assertTrue(has("row_Cafe"), "Home is still there after Back")
    }

    @Test
    fun savingFromThePaneUpdatesTheListAndEmptiesThePane() {
        seed("e1", "Cafe", 10_000)
        launch(1000); waitFor("row_Cafe")
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick()
        waitFor("amount")
        compose.onNodeWithTag("amount").performTextClearance()
        compose.onNodeWithTag("amount").performTextInput("150.5")
        compose.onNodeWithTag("save").performClick()
        waitGone("amount"); waitFor("wide_placeholder")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("month_spend")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("month_spend").assertTextEquals("₹150.50")
    }

    @Test
    fun aNarrowWindowIsUnchangedTheEditorCoversTheListAndThereIsNoPlaceholder() {
        seed("e1", "Cafe", 10_000)
        launch(500); waitFor("row_Cafe")
        assertTrue(!has("wide_placeholder") && !has("wide_detail_pane") && !has("wide_list_pane"), "no panes on a narrow window")
        compose.onNodeWithTag("row_Cafe", useUnmergedTree = true).performClick()
        waitFor("amount"); waitGone("row_Cafe")
        compose.onNodeWithTag("cancel").performClick()
        waitFor("row_Cafe"); assertTrue(!has("wide_placeholder"))
    }

    @Test
    fun otherScreensKeepTheirFullWidthOnAWideWindow() {
        launch(1000)
        compose.openMore(); waitFor("manage_accounts")
        compose.onNodeWithTag("manage_accounts", useUnmergedTree = true).performClick()
        waitFor("account_Cash")
        assertTrue(!has("wide_placeholder"), "a sub-screen is one pane, with no entry placeholder beside it")
        back(); compose.waitUntil(10_000) { !has("account_Cash") } // More stays open under the screen, so Back lands on More
    }
}
