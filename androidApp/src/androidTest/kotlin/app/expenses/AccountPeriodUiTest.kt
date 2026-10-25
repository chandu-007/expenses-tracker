package app.expenses

import androidx.compose.ui.test.performScrollToNode
import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertTrue

/** Home narrowed to one account: the month chips, and opening Search inside that account. Written without being run on a device. */
@RunWith(AndroidJUnit4::class)
class AccountPeriodUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    /** The Home list is lazy and the account bar takes room: a row below the fold is not composed until the list is scrolled to it. */
    private fun seeRow(tag: String) { compose.waitUntil(10_000) { has("list") }; if (!has(tag)) runCatching { compose.onNodeWithTag("list").performScrollToNode(hasTestTag(tag)) }; waitFor(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }

    private fun spend(id: String, merchant: String, account: String, at: Long) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), at, AccountId(account), Money(10_000, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(10_000, Currency.INR)))), EntryDetails(merchant = merchant)) }
    }
    private fun noon(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun seed() {
        val firstOfThis = LocalDate.now().withDayOfMonth(1)
        spend("n", "CashNow", "cash", System.currentTimeMillis() - 1_000)
        spend("l", "CashLast", "cash", noon(firstOfThis.minusMonths(1).plusDays(9)))
        spend("o", "CashOld", "cash", noon(firstOfThis.minusMonths(3).plusDays(4)))
        spend("b", "BankNow", "bank", System.currentTimeMillis() - 2_000)
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
    }

    @Test
    fun theMonthChipsNarrowAnAccountsListAndAllBringsEverythingBack() {
        seed(); tap("balance_chip_Cash"); waitFor("account_period_this")
        waitFor("row_CashNow"); waitFor("row_CashLast"); seeRow("row_CashOld")
        tap("account_period_this"); waitGone("row_CashLast")
        assertTrue(has("row_CashNow") && !has("row_CashOld") && !has("row_BankNow"), "this month: only this month's cash entry")
        tap("account_period_last"); waitFor("row_CashLast"); waitGone("row_CashNow")
        assertTrue(!has("row_CashOld"), "last month: only last month's")
        tap("account_period_all"); seeRow("row_CashOld"); assertTrue(has("row_CashNow") && has("row_CashLast"))
        tap("account_period_last"); waitGone("row_CashNow")
        tap("account_filter_clear"); waitFor("row_BankNow"); waitFor("row_CashNow"); seeRow("row_CashOld") // a period belongs to the account view
    }

    @Test
    fun searchThisAccountOpensSearchInsideItAndTheAccountCanBeChangedThere() {
        seed(); tap("balance_chip_Cash"); tap("account_search")
        waitFor("search_text"); waitFor("result_n"); waitFor("result_o")
        assertTrue(!has("result_b"), "only the cash account is searched")
        tap("search_account_Bank"); waitFor("result_b"); waitGone("result_n")
        tap("search_account_any"); waitFor("search_hint") // nothing else is chosen, so the search is back at its start
        assertTrue(!has("result_n") && !has("result_b"))
    }
}
