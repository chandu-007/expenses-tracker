package app.expenses

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.EncryptedDriver
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real screens on the encrypted database: add an expense, see it in the list and in the month total. */
@RunWith(AndroidJUnit4::class)
class AddExpenseFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The database is shared by the whole app process, so close it before deleting its files. */
    private fun resetApp() {
        kotlinx.coroutines.runBlocking { (context.applicationContext as ExpensesApp).container.close() }
        EncryptedDriver.reset(context)
    }
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshInstall() {
        resetApp()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("add")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
    }

    @Test
    fun addingAnExpenseShowsItInTheListAndTheMonthTotalWithIndianGrouping() {
        compose.onNodeWithTag("month_spend").assertTextEquals("₹0.00")
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("save").assertIsNotEnabled()
        compose.onNodeWithTag("amount").performTextInput("1234567.5")
        compose.onNodeWithText("Groceries").performClick()
        compose.onNodeWithTag("note").performTextInput("Test Mart")
        compose.onNodeWithTag("save").assertIsEnabled().performClick()

        // The amount appears twice once saved: in the list row and in the month total. (The note text alone would match
        // the add screen's text field before it closes.)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("₹12,34,567.50")).fetchSemanticsNodes().size >= 2 }
        compose.onNodeWithTag("month_spend").assertTextEquals("₹12,34,567.50")
        compose.onNodeWithText("Test Mart").assertExists()
        compose.onNodeWithTag("list").assertExists()
    }

    @Test
    fun anAmountWithTooManyDecimalsCannotBeSaved() {
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("amount").performTextInput("12.345")
        compose.onNodeWithTag("save").assertIsNotEnabled()
    }
}
