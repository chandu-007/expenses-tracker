package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.CardBilling
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.CardText
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * The credit card view: what was charged since the statement, the limit used (a bar and its words) and what is left to pay with the due date, on the full tile
 * and in the account dialog. Figures are invented; the layout was not seen on a device when this was written.
 */
@RunWith(AndroidJUnit4::class)
class CardSummaryUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }

    private val dayMs = 86_400_000L
    private lateinit var statement: LocalDate
    private lateinit var due: LocalDate
    private lateinit var dueIn: String

    /** An Amex with a statement cut a few days ago (₹400 owed then, none paid), ₹250 charged since, and a ₹4,000 limit; the billing day is one that exists in every month. */
    private fun seedCard(limit: Boolean = true) {
        var s = java.time.LocalDate.now().minusDays(2)
        while (s.dayOfMonth > 28) s = s.minusDays(1)
        val terms = CardTerms("amex", statementDay = s.dayOfMonth, dueDay = (s.dayOfMonth + 9) % 28 + 1)
        val today = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
        val cycle = CardBilling.cycleOn(today, terms)
        statement = cycle.statementDate; due = cycle.dueDate
        val days = (due.toEpochDays() - today.toEpochDays()).toInt()
        dueIn = app.expenses.core.domain.CardSummaryText.days(days)
        val cut = java.time.LocalDate.of(statement.year, statement.monthNumber, statement.dayOfMonth).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking { container.write { ok ->
            ok.store.saveAccount(Account(AccountId("amex"), "Amex", AccountType.CREDIT_CARD, Currency.INR))
            ok.store.save(Expense(EntryId("old"), cut - 30 * dayMs, AccountId("amex"), Money(40_000, Currency.INR)), EntryDetails(merchant = "Phone"))
            ok.store.save(Expense(EntryId("new"), System.currentTimeMillis() - dayMs, AccountId("amex"), Money(25_000, Currency.INR)), EntryDetails(merchant = "Shoes"))
            ok.cards.save(terms)
            if (limit) ok.cards.setCreditLimit("amex", 400_000)
        } }
    }

    private fun openAccounts() { compose.openMore(); tap("manage_accounts"); waitFor("accounts_stack"); tap("accounts_stack"); waitFor("accounts_stack_close") }

    @Test
    fun theFullTileAndTheDialogShowTheCyclesSpendingTheLimitUseAndWhatIsLeftToPay() {
        seedCard()
        launch(); openAccounts()
        waitFor("tile_cycle_spend_Amex")
        val since = "${CardText.day(statement)} statement"
        assertTrue(textOf("tile_cycle_spend_Amex").startsWith("₹250.00 spent since the $since"), textOf("tile_cycle_spend_Amex"))
        assertTrue(textOf("tile_limit_use_Amex").startsWith("₹650.00 of ₹4,000.00 used · 16% · ₹3,350.00 left"), textOf("tile_limit_use_Amex"))
        assertTrue(has("tile_limit_use_Amex_bar"), "the limit bar is drawn")
        assertTrue(textOf("tile_due_line_Amex").startsWith("₹400.00 left to pay · minimum about ₹20.00 · due ${CardText.day(due)} ($dueIn)"), textOf("tile_due_line_Amex"))
        assertTrue(has("card_status_Amex"), "the existing status line is still there")
        tap("account_Amex")
        waitFor("card_summary_title")
        assertTrue(textOf("card_cycle_spend").startsWith("₹250.00 spent since the $since"), textOf("card_cycle_spend"))
        assertTrue(textOf("card_limit_use").startsWith("₹650.00 of ₹4,000.00 used · 16%"), textOf("card_limit_use"))
        assertTrue(textOf("card_due_line").contains("₹400.00 left to pay") && textOf("card_due_line").contains("due ${CardText.day(due)}"), textOf("card_due_line"))
    }

    @Test
    fun withoutALimitThereIsNoBarAndWithoutBillingDatesNoSummary() {
        seedCard(limit = false)
        launch(); openAccounts()
        waitFor("tile_cycle_spend_Amex"); waitFor("tile_due_line_Amex")
        assertTrue(!has("tile_limit_use_Amex") && !has("tile_limit_use_Amex_bar"), "no limit was entered")
        runBlocking { container.write { it.cards.clear("amex") } }
        tap("back"); openAccounts()
        compose.waitUntil(10_000) { !has("tile_cycle_spend_Amex") }
        tap("account_Amex"); waitFor("card_dates")
        assertTrue(!has("card_summary_title") && !has("card_cycle_spend"), "a card with no billing dates has no summary")
    }
}
