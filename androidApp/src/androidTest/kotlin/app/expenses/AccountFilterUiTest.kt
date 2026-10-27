package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AccountFilterUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    /** Home is one scrolling page and choosing an account scrolls it to the account bar: bring the page back to a control above that (the cards) before tapping it. */
    private fun tap(tag: String) {
        compose.waitUntil(10_000) { if (!has(tag)) runCatching { compose.onNodeWithTag("list").performScrollToNode(hasTestTag(tag)) }; has(tag) }
        compose.waitForIdle(); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick()
    }

    private fun amountOf(title: String): String = compose.onNodeWithTag("amount_$title", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }

    private fun spend(id: String, merchant: String, account: String, ago: Long) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis() - ago, AccountId(account), Money(10_000, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(10_000, Currency.INR)))), EntryDetails(merchant = merchant)) }
    }

    @Test
    fun tappingAnAccountOnHomeShowsOnlyItsEntriesAndTransfersBothWaysAndTappingAgainShowsAll() {
        spend("a", "FromCash", "cash", 1_000); spend("b", "FromBank", "bank", 2_000)
        runBlocking { container.write { it.store.save(Transfer(EntryId("t"), System.currentTimeMillis() - 3_000, AccountId("bank"), AccountId("cash"), Money(5_000, Currency.INR)), EntryDetails(merchant = "BankToCash")) } }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        waitFor("row_FromCash"); waitFor("row_FromBank")
        tap("balance_chip_Cash")
        waitFor("account_filter_note"); waitGone("row_FromBank")
        assertTrue(has("row_FromCash") && has("row_BankToCash"), "cash shows its own entry and the transfer into it")
        assertTrue(amountOf("BankToCash").startsWith("+"), "into Cash it is money in: ${amountOf("BankToCash")}")
        tap("balance_chip_Cash") // again: back to everything
        waitFor("row_FromBank"); waitGone("account_filter_note")
        tap("balance_chip_Bank"); waitGone("row_FromCash"); assertTrue(has("row_FromBank") && has("row_BankToCash"))
        assertTrue(amountOf("BankToCash").startsWith("-"), "out of Bank it is money out: ${amountOf("BankToCash")}")
        tap("account_filter_clear"); waitFor("row_FromCash")
        assertTrue(!amountOf("BankToCash").startsWith("+") && !amountOf("BankToCash").startsWith("-"), "unfiltered, a transfer has no sign: ${amountOf("BankToCash")}")
        tap("balance_chip_Bank")
        tap("account_filter_clear"); waitFor("row_FromCash"); waitFor("row_FromBank")
    }

    @Test
    fun anAccountsCardOffersItsTransactionsOnHome() {
        spend("a", "FromCash", "cash", 1_000); spend("b", "FromBank", "bank", 2_000)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); tap("manage_accounts"); waitFor("account_Bank")
        tap("account_Bank") // the stack of cards spreads out first
        compose.waitForIdle(); tap("account_Bank"); tap("account_show_entries")
        waitFor("account_filter_note"); waitFor("row_FromBank"); waitGone("row_FromCash")
    }
}
