package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import kotlin.test.assertTrue

/**
 * Home is one scrolling page. Choosing an account scrolls it to the account bar, and only choosing does: coming back from Search, or the activity being
 * recreated (a rotation), keeps where the person was. The bar itself stays in view while the account's entries scroll under it.
 */
@RunWith(AndroidJUnit4::class)
class HomeFilterScrollTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun settle() { compose.waitForIdle(); Thread.sleep(900); compose.waitForIdle() }
    /** How far the page is scrolled, in pixels. */
    private fun scrollPx(): Float = compose.onNodeWithTag("list", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun note(): String = compose.onNodeWithTag("account_filter_note", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun seed() = runBlocking {
        container.write { ok ->
            for (i in 0 until 14) for ((n, acc) in listOf("Cash" to "cash", "Bank" to "bank")) {
                ok.store.save(Expense(EntryId("$acc$i"), System.currentTimeMillis() - i * 3_600_000L - (if (acc == "bank") 1_000L else 0L), AccountId(acc), Money(10_000, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(10_000, Currency.INR)))), EntryDetails(merchant = "$n$i"))
            }
        }
    }
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add"); waitFor("balance_chip_Cash") }

    @Test
    fun comingBackFromSearchOrRecreatingTheActivityDoesNotScrollBackToTheFilterBarButChoosingAnAccountDoes() {
        seed(); launch()
        tap("balance_chip_Cash"); waitFor("account_filter_note"); settle()
        val barPx = scrollPx()
        assertTrue(barPx > 0f, "choosing an account scrolled the page to its bar: $barPx")
        compose.onNodeWithTag("list").performScrollToIndex(9); settle()
        val deepPx = scrollPx()
        assertTrue(deepPx > barPx + 100f, "scrolled on into the account's entries: $deepPx vs $barPx")
        assertTrue(has("account_filter_note") && has("account_filter_clear") && has("account_period_all"), "the bar stays in view while its entries scroll under it")

        tap("account_search"); waitFor("search_text")
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("account_filter_note"); settle()
        val afterSearch = scrollPx()
        assertTrue(kotlin.math.abs(afterSearch - barPx) > 50f, "coming back from Search must not snap to the filter bar: $afterSearch vs bar $barPx")

        compose.onNodeWithTag("list").performScrollToIndex(9); settle()
        val before = scrollPx()
        scenario!!.recreate(); waitFor("account_filter_note"); settle()
        val afterRecreate = scrollPx()
        assertTrue(kotlin.math.abs(afterRecreate - barPx) > 50f, "recreating the activity must not snap to the filter bar: $afterRecreate vs bar $barPx (before $before)")
    }

    @Test
    fun choosingADifferentAccountScrollsToTheBarAndShowAllGoesBackToTheTop() {
        seed(); launch()
        tap("balance_chip_Cash"); waitFor("account_filter_note"); settle()
        compose.onNodeWithTag("list").performScrollToIndex(0); settle()
        assertTrue(scrollPx() < 5f, "back at the top: ${scrollPx()}")
        tap("balance_chip_Bank"); compose.waitUntil(10_000) { has("account_filter_note") && note().contains("Bank") }; settle()
        assertTrue(scrollPx() > 100f, "a different account scrolls to the bar: ${scrollPx()}")
        tap("account_filter_clear"); compose.waitUntil(10_000) { !has("account_filter_note") }; settle()
        assertTrue(scrollPx() < 5f, "Show all goes back to the top: ${scrollPx()}")
    }
}
