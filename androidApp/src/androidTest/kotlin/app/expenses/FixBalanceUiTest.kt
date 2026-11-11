package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AmountMask
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Transfer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Fix this balance" on the real app: the dialog, its live difference, the balance and Undo, the history line, and the hint on a card that shows extra money. Invented accounts and amounts. */
@RunWith(AndroidJUnit4::class)
class FixBalanceUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun waitText(tag: String, text: String) = compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun balanceText(name: String) = compose.onNodeWithTag("balance_$name", useUnmergedTree = true)

    /** The accounts page; with several accounts the cards start stacked like a wallet, so one tap spreads them before any card is tapped. */
    private fun openAccounts() {
        compose.openMore(); tap("manage_accounts"); waitFor("accounts_list"); waitFor("accounts_stack")
        val several = (runBlocking { container.write { it.db.schemaQueries.selectAccounts().executeAsList().count { a -> a.archived == 0L } } } ?: 0) > 1
        if (several) { tap("accounts_stack"); waitFor("accounts_stack_close") }
    }

    private fun openFix(name: String) { tap("account_$name"); waitFor("fix_balance_open"); tap("fix_balance_open"); waitFor("fix_balance_title"); waitFor("fix_balance_amount") }

    private fun seed(block: (AppContainer.Opened.Ok) -> Unit) { runBlocking { container.write(block) } }
    private fun at(offsetMillis: Long) = System.currentTimeMillis() - offsetMillis
    private fun spend(ok: AppContainer.Opened.Ok, id: String, account: String, minor: Long, ago: Long = 60_000) =
        ok.store.save(Expense(EntryId(id), at(ago), AccountId(account), Money(minor, inr), listOf(CategorySplit(CategoryId("Food"), Money(minor, inr)))))

    /** A card that the entries say owes 3,000. */
    private fun cardOwing3000() = seed { ok ->
        ok.store.saveAccount(Account(AccountId("card1"), "Axis Card", AccountType.CREDIT_CARD, inr))
        spend(ok, "c1", "card1", 3_000_00)
    }

    private fun opening(id: String) = runBlocking { container.write { it.db.schemaQueries.accountById(id).executeAsOne().opening_minor } }!!
    private fun history(id: String) = runBlocking { container.write { it.balanceFixes.history(id) } }!!
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }!!
    private fun balance(id: String) = runBlocking { container.write { it.store.balances().getValue(AccountId(id)).minorUnits } }!!

    @Test
    fun aCardThatOwesMoreThanTheAppThinksIsFixedWithALiveDifferenceAndUndoPutsItBackExactly() {
        cardOwing3000(); launch(); openAccounts()
        openFix("Axis Card")
        waitText("fix_balance_current", "The app thinks you owe ₹3,000.00")
        compose.onNodeWithTag("fix_balance_confirm", useUnmergedTree = true).assertIsNotEnabled()
        waitText("fix_balance_diff", "Type the amount to see the difference.")
        assertTrue(compose.onNodeWithTag("fix_balance_amount", useUnmergedTree = true).getUnclippedBoundsInRoot().width <= 560.dp, "the dialog keeps to its maximum width")
        type("fix_balance_amount", "5500")
        waitText("fix_balance_diff", "Difference: ₹2,500.00 (the app is missing spending of ₹2,500.00)")
        waitText("fix_balance_effect", "After fixing, Axis Card will show ₹5,500.00 owed.")
        compose.onNodeWithTag("fix_balance_confirm", useUnmergedTree = true).assertIsEnabled()
        tap("fix_balance_confirm")
        waitGone("fix_balance_title"); waitGone("account_name")
        balanceText("Axis Card").assertTextEquals("-₹5,500.00")
        assertEquals(-2_500_00L, opening("card1"), "the opening balance moved by the difference")
        assertEquals(1L, entries(), "no entry was made: nothing counts as spending")
        assertEquals(1, history("card1").size)
        waitText("message_text", "Balance fixed: ₹3,000.00 owed → ₹5,500.00 owed")
        tap("message_action") // Undo
        waitText("message_text", "Put the balance back.")
        compose.waitUntil(15_000) { runCatching { balanceText("Axis Card").assertTextEquals("-₹3,000.00") }.isSuccess }
        assertEquals(0L, opening("card1")); assertTrue(history("card1").isEmpty(), "the line went with the fix")
    }

    @Test
    fun emptyZeroDifferenceAndNonsenseAreRefusedInPlainWordsAndNothingChanges() {
        cardOwing3000(); launch(); openAccounts()
        openFix("Axis Card")
        compose.onNodeWithTag("fix_balance_confirm", useUnmergedTree = true).assertIsNotEnabled()
        type("fix_balance_amount", "3000")
        waitText("fix_balance_diff", "Already matches: the app shows the same amount.")
        compose.onNodeWithTag("fix_balance_confirm", useUnmergedTree = true).assertIsNotEnabled()
        type("fix_balance_amount", "$50"); waitText("fix_balance_problem", "This ledger is in ₹, so only ₹ amounts can be used here.")
        compose.onNodeWithTag("fix_balance_confirm", useUnmergedTree = true).assertIsNotEnabled()
        type("fix_balance_amount", "USD 50"); waitText("fix_balance_problem", "only ₹ amounts")
        type("fix_balance_amount", "-500"); waitText("fix_balance_problem", "without a minus sign")
        type("fix_balance_amount", "12abc"); waitText("fix_balance_problem", "Enter the amount as a number, like 25000 or 1250.50")
        type("fix_balance_amount", "99999999999999.99"); waitText("fix_balance_problem", "too large")
        type("fix_balance_amount", "₹4,200"); waitText("fix_balance_diff", "Difference: ₹1,200.00") // the ledger's own sign is welcome
        compose.onNodeWithTag("fix_balance_confirm", useUnmergedTree = true).assertIsEnabled()
        tap("fix_balance_cancel"); waitGone("fix_balance_title")
        assertEquals(0L, opening("card1")); assertTrue(history("card1").isEmpty())
    }

    @Test
    fun aCardCanBeFixedToInCreditAndALargeAmountKeepsLakhGrouping() {
        cardOwing3000(); launch(); openAccounts()
        openFix("Axis Card")
        type("fix_balance_amount", "250"); tap("fix_balance_credit")
        waitText("fix_balance_diff", "Difference: ₹3,250.00 (the app is missing payments or refunds of ₹3,250.00)")
        waitText("fix_balance_effect", "will show ₹250.00 in credit")
        tap("fix_balance_confirm"); waitGone("fix_balance_title")
        compose.waitUntil(15_000) { runCatching { balanceText("Axis Card").assertTextEquals("₹250.00") }.isSuccess }
        assertEquals(250_00L, balance("card1"))
        // and a big statement: 1,26,000 owed
        openFix("Axis Card")
        type("fix_balance_amount", "126000"); tap("fix_balance_owe")
        waitText("fix_balance_diff", "Difference: ₹1,26,250.00 (the app is missing spending of ₹1,26,250.00)")
        waitText("fix_balance_effect", "will show ₹1,26,000.00 owed")
    }

    @Test
    fun aBankAccountIsFixedToWhatTheBankAppSaysTheNetWorthFollowsAndTheEditPageKeepsTheHistory() {
        seed { ok -> ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, 25_000_00)) }
        launch(); openAccounts()
        openFix("Bank")
        waitText("fix_balance_current", "The app thinks Bank has ₹25,000.00")
        waitFor("fix_balance_has"); waitFor("fix_balance_overdrawn") // a bank account's two choices: it has money, or it is overdrawn
        type("fix_balance_amount", "24100.50")
        waitText("fix_balance_diff", "Difference: ₹899.50 (the app is missing spending of ₹899.50)")
        tap("fix_balance_confirm"); waitGone("fix_balance_title")
        compose.waitUntil(15_000) { runCatching { balanceText("Bank").assertTextEquals("₹24,100.50") }.isSuccess }
        compose.onNodeWithTag("net_worth", useUnmergedTree = true).assertTextEquals("₹24,100.50")
        assertEquals(24_100_50L, balance("bank")); assertEquals(0L, entries())
        val day = DatePattern("d MMM yyyy").format(System.currentTimeMillis())
        tap("account_Bank"); waitFor("fix_balance_history_0")
        compose.onNodeWithTag("fix_balance_history_0", useUnmergedTree = true).assertTextEquals("Balance fixed: ₹25,000.00 → ₹24,100.50 on $day")
        waitText("fix_balance_now", "₹24,100.50")
        // fixed again from the same page: the newest line is first
        tap("fix_balance_open"); waitFor("fix_balance_title")
        type("fix_balance_amount", "500"); tap("fix_balance_overdrawn")
        waitText("fix_balance_current", "The app thinks Bank has ₹24,100.50")
        waitText("fix_balance_effect", "will show ₹500.00 overdrawn")
        tap("fix_balance_confirm"); waitGone("fix_balance_title")
        assertEquals(-500_00L, balance("bank"))
        tap("account_Bank"); waitFor("fix_balance_history_1")
        waitText("fix_balance_history_0", "→ ₹500.00 overdrawn on $day"); waitText("fix_balance_history_1", "₹25,000.00 → ₹24,100.50")
        // saving the page without touching the balance keeps an opening balance the form could not take back as typed (it is below zero now)
        tap("account_save"); waitGone("account_name")
        assertEquals(-500_00L, balance("bank"), "an opening balance the form shows as it is stored is kept when the name is saved")
    }

    @Test
    fun cashWithNoEntriesIsFixedToWhatIsInThePurseAndAnAccountPageOpensForEachKind() {
        launch(); openAccounts()
        openFix("Cash")
        waitText("fix_balance_current", "The app thinks Cash has ₹0.00")
        type("fix_balance_amount", "850"); waitText("fix_balance_diff", "the app is missing money that came in: ₹850.00")
        tap("fix_balance_confirm"); waitGone("fix_balance_title")
        assertEquals(850_00L, balance("cash"))
    }

    @Test
    fun aCardThatShowsExtraMoneyGetsAOneLineHintThatOpensTheFixAndDismissesForGood() {
        seed { ok ->
            ok.store.saveAccount(Account(AccountId("card1"), "Axis Card", AccountType.CREDIT_CARD, inr))
            spend(ok, "c1", "card1", 1_000_00)
            ok.store.save(Transfer(EntryId("pay"), at(30_000), AccountId("bank"), AccountId("card1"), Money(3_000_00, inr)))
        }
        launch(); openAccounts()
        waitFor("fix_balance_hint")
        waitText("fix_balance_hint_text", "Looks like some earlier spending is missing.")
        // it opens the same dialog: the card shows 2,000 extra and the statement says nothing is owed
        tap("fix_balance_hint_open"); waitFor("fix_balance_title")
        waitText("fix_balance_current", "The app thinks you owe nothing and have ₹2,000.00 extra in credit")
        type("fix_balance_amount", "0")
        waitText("fix_balance_diff", "Difference: ₹2,000.00 (the app is missing spending of ₹2,000.00)")
        tap("fix_balance_confirm"); waitGone("fix_balance_title")
        waitGone("fix_balance_hint")
        compose.waitUntil(15_000) { runCatching { balanceText("Axis Card").assertTextEquals("₹0.00") }.isSuccess }
        assertEquals(0L, balance("card1")); assertEquals(-2_000_00L, opening("card1"))
    }

    @Test
    fun theHintIsDismissedOnceAndStaysGoneAfterARestartAndACardThatOwesNeverGetsIt() {
        seed { ok ->
            ok.store.saveAccount(Account(AccountId("card1"), "Axis Card", AccountType.CREDIT_CARD, inr))
            ok.store.saveAccount(Account(AccountId("card2"), "Amex", AccountType.CREDIT_CARD, inr))
            spend(ok, "c2", "card2", 2_000_00) // owes: no hint
            ok.store.save(Transfer(EntryId("pay"), at(30_000), AccountId("bank"), AccountId("card1"), Money(500_00, inr))) // 500 extra
        }
        launch(); openAccounts()
        waitFor("fix_balance_hint")
        tap("fix_balance_hint_open"); waitText("fix_balance_current", "extra in credit"); tap("fix_balance_cancel"); waitGone("fix_balance_title") // it is the card in credit, not the one that owes
        tap("fix_balance_hint_dismiss"); waitGone("fix_balance_hint")
        assertTrue("card1" in runBlocking { container.write { it.balanceFixes.dismissedHints() } }!!, "persisted as a setting")
        launch(); openAccounts()
        compose.waitUntil(15_000) { has("balance_Axis Card") }
        assertTrue(!has("fix_balance_hint"), "not again after a restart")
        // the button on the account's page is still there
        tap("account_Axis Card"); waitFor("fix_balance_open")
    }

    @Test
    fun withHideAmountsOnTheDialogHidesEveryAmountItWritesAndTheTypedFieldStaysAsTyped() {
        cardOwing3000(); seed { it.templates.putSetting("hide_amounts", "1") }
        launch(); openAccounts()
        openFix("Axis Card")
        compose.onNodeWithTag("fix_balance_current", useUnmergedTree = true).assertTextEquals(AmountMask.mask("The app thinks you owe ₹3,000.00"))
        type("fix_balance_amount", "5500")
        waitText("fix_balance_diff", AmountMask.mask("Difference: ₹2,500.00 (the app is missing spending of ₹2,500.00)"))
        assertTrue(compose.onAllNodes(hasText("2,500", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "no amount of the difference is drawn")
    }
}
