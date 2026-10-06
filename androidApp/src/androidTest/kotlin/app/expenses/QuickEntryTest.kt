package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** The fast-entry paths on the real screens and the encrypted database. */
@RunWith(AndroidJUnit4::class)
class QuickEntryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The database is shared by the whole app process, so close it before deleting its files. */
    private fun resetApp() {
        kotlinx.coroutines.runBlocking { (context.applicationContext as ExpensesApp).container.close() }
        EncryptedDriver.reset(context)
    }
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshInstall() = resetApp()

    @After fun cleanUp() {
        scenario?.close()
        resetApp()
    }

    private fun launch(intent: Intent = Intent(context, MainActivity::class.java)) {
        scenario = ActivityScenario.launch(intent)
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun addExpense(amount: String, category: String, account: String, note: String, asTemplate: Boolean = false) {
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("amount").performTextInput(amount)
        compose.onNodeWithText(category).performClick()
        compose.onNodeWithText(account).performClick()
        compose.onNodeWithTag("note").performTextInput(note)
        // The form scrolls; with the keyboard open the checkbox can be below the visible area.
        if (asTemplate) compose.onNodeWithTag("save_template").performScrollTo().performClick()
        compose.onNodeWithTag("save").performClick()
    }

    @Test
    fun theNextEntryStartsWithTheCategoryAndAccountUsedLast() {
        launch(); waitFor("add")
        addExpense("75", "Transport", "Bank", "Auto")
        waitForText("₹75.00")
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithText("Transport").assertIsSelected()
        compose.onNodeWithText("Bank").assertIsSelected()
    }

    @Test
    fun aSavedQuickEntryRecordsAnExpenseInOneTapAndUndoTakesItBack() {
        launch(); waitFor("add")
        addExpense("20", "Food", "Cash", "Chai", asTemplate = true)
        waitFor("template_Chai")
        compose.onNodeWithTag("month_spend").assertTextEquals("₹20.00")

        compose.onNodeWithTag("template_Chai").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("month_spend").fetchSemanticsNode().config.toString().contains("₹40.00") }
        compose.onNodeWithTag("month_spend").assertTextEquals("₹40.00")

        waitForText("Undo")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("month_spend").fetchSemanticsNode().config.toString().contains("₹20.00") }
        compose.onNodeWithTag("month_spend").assertTextEquals("₹20.00")
    }

    @Test
    fun theLauncherShortcutActionOpensTheAddScreenDirectly() {
        launch(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_EXPENSE))
        waitFor("amount")
        compose.onNodeWithTag("save").assertExists()
    }
}
