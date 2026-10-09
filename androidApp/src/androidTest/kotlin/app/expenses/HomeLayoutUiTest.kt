package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.HomeLayout
import app.expenses.core.domain.HomeSection
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EncryptedDriver
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
class HomeLayoutUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun saved() = db { HomeLayout.decode(it.templates.setting("home_layout")) }
    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
    private fun customise() { tap("more_menu"); tap("open_home_layout"); waitFor("home_row_BUDGET") }
    private fun seed() = db { ok ->
        ok.store.save(Expense(EntryId("e"), System.currentTimeMillis(), AccountId("cash"), Money(10_000, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(10_000, Currency.INR)))), EntryDetails(merchant = "Cafe"))
        ok.templates.save(app.expenses.core.domain.Template("t", "Chai", Money(2_000, Currency.INR), CategoryId("Food"), AccountId("cash"), null))
    }

    /** What a balance card says: its name line, then its amount. */
    private fun chip(name: String): String = compose.onNodeWithTag("balance_chip_$name").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(" ") { it.text }

    @Test
    fun everyBlockIsShownByDefaultAndBalancesListEachActiveAccountWithItsBalance() {
        seed(); launch()
        waitFor("budget_summary"); waitFor("templates"); waitFor("balances")
        waitFor("balance_chip_Cash"); waitFor("balance_chip_Bank")
        assertEquals("Cash -₹100.00", chip("Cash"))
    }

    @Test
    fun balancesFollowAWriteAfterAMomentWithoutBeingPartOfTheWriteItself() {
        launch(); waitFor("balance_chip_Bank")
        tap("add"); waitFor("amount"); compose.onNodeWithTag("amount").performTextInput("400"); tap("save"); waitFor("add")
        // the entry is on the list at once; the balances catch up within a couple of seconds
        compose.waitUntil(15_000) { chip("Bank") == "Bank -₹400.00" }
    }

    @Test
    fun anArchivedAccountIsNotOnTheHomeStripAndACardShowsWhatIsOwed() {
        db { ok ->
            ok.store.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("old"), "OldCard", app.expenses.core.domain.ledger.AccountType.CREDIT_CARD, Currency.INR, -500_000, true))
            ok.store.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("amex"), "Amex", app.expenses.core.domain.ledger.AccountType.CREDIT_CARD, Currency.INR, -250_000, false))
        }
        launch(); waitFor("balance_chip_Amex")
        assertTrue(!has("balance_chip_OldCard"), "archived accounts are not shown")
        assertEquals("Amex · owed -₹2,500.00", chip("Amex"))
    }

    @Test
    fun hidingABlockRemovesItAtOnceKeepsItHiddenAfterARestartAndShowingItBringsItBack() {
        seed(); launch(); customise()
        tap("home_toggle_BALANCES")
        compose.waitUntil(10_000) { HomeSection.BALANCES in saved().hidden }
        tap("home_layout_back"); waitFor("add"); waitGone("balances"); assertTrue(has("templates"))
        launch(); waitFor("templates"); assertTrue(!has("balances"), "still hidden after the app was reopened")
        customise(); tap("home_toggle_BALANCES"); compose.waitUntil(10_000) { saved().hidden.isEmpty() }
        tap("home_layout_back"); waitFor("balances")
    }

    @Test
    fun movingABlockChangesTheOrderOnTheHomeScreen() {
        seed(); launch(); waitFor("templates"); waitFor("budget_summary")
        assertTrue(top("budget_summary") < top("templates"), "by default the budget is above the quick entries")
        customise()
        repeat(3) { i -> tap("home_down_BUDGET"); compose.waitUntil(10_000) { saved().order.indexOf(HomeSection.BUDGET) == i + 1 } }
        compose.waitUntil(10_000) { saved().order.last() == HomeSection.BUDGET }
        tap("home_layout_back"); waitFor("templates"); waitFor("budget_summary")
        assertTrue(top("templates") < top("budget_summary"), "now the quick entries are above the budget")
        assertEquals(listOf(HomeSection.BILLS, HomeSection.BALANCES, HomeSection.QUICK_ENTRIES, HomeSection.BUDGET), saved().order)
    }

    @Test
    fun theEndsOfTheListCannotBeMovedFurther() {
        launch(); customise()
        compose.onNodeWithTag("home_up_BUDGET", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("home_down_QUICK_ENTRIES", useUnmergedTree = true).assertIsNotEnabled()
    }
}
