package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

/** Savings goals: made, put towards by hand, following an account, edited and removed. */
@RunWith(AndroidJUnit4::class)
class GoalsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.waitForIdle(); runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }; compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
    private fun open() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add"); compose.openMore(); tap("open_goals"); waitFor("goals_list") }

    @Test
    fun aGoalShowsHowFarAlongItIsAndWhatToPutAsideEachMonthAndMoneyCanBePutInAndTakenOut() {
        open(); waitFor("goals_empty")
        tap("goal_new"); type("goal_name", "Trip"); type("goal_target", "50000"); type("goal_months", "5"); tap("goal_save")
        waitFor("goal_Trip")
        assertTrue(textOf("goal_line_Trip").startsWith("₹0.00 of ₹50,000.00 · ₹50,000.00 to go · by "), textOf("goal_line_Trip"))
        assertEquals("₹10,000.00 a month to get there", textOf("goal_monthly_Trip"))
        tap("goal_add_Trip"); type("goal_amount", "12000"); tap("goal_put")
        compose.waitUntil(10_000) { textOf("goal_percent_Trip") == "24%" }
        tap("goal_add_Trip"); type("goal_amount", "2000"); tap("goal_take")
        compose.waitUntil(10_000) { textOf("goal_percent_Trip") == "20%" }
        // a refused entry says why and keeps the dialog
        tap("goal_add_Trip"); tap("goal_put"); waitFor("goal_problem")
        compose.onNodeWithTag("goal_amount", useUnmergedTree = true).performTextInput("1"); tap("goal_take"); compose.waitUntil(10_000) { !has("goal_problem") }
    }

    @Test
    fun aGoalThatFollowsAnAccountReadsItsBalanceAndEditingKeepsWhatWasPutAside() {
        runBlocking { container.write<Unit> { it.store.saveAccount(Account(AccountId("sav"), "Savings pot", AccountType.BANK, Currency.INR, 2_000_000)) } }
        open()
        tap("goal_new"); type("goal_name", "Laptop"); type("goal_target", "80000"); tap("goal_account_Savings pot"); tap("goal_save")
        waitFor("goal_Laptop")
        compose.waitUntil(10_000) { textOf("goal_percent_Laptop") == "25%" }
        assertTrue(!has("goal_add_Laptop"), "an account's money moves with the account: no hand adjustments")
        tap("goal_edit_Laptop"); compose.onNodeWithTag("goal_target", useUnmergedTree = true).performTextClearance(); type("goal_target", "40000"); tap("goal_save")
        compose.waitUntil(10_000) { textOf("goal_percent_Laptop") == "50%" }
    }

    @Test
    fun aBlankNameOrTargetIsRefusedAndAGoalCanBeRemovedWithoutTouchingAnythingElse() {
        open()
        tap("goal_new"); tap("goal_save"); waitFor("goal_problem")
        type("goal_name", "X"); tap("goal_save"); compose.waitUntil(10_000) { textOf("goal_problem").contains("how much") }
        type("goal_target", "100"); tap("goal_save"); waitFor("goal_X")
        tap("goal_remove_X"); tap("goal_delete_ok"); compose.waitUntil(10_000) { !has("goal_X") && has("goals_empty") }
    }
}
