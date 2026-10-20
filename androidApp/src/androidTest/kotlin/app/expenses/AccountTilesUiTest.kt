package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.CardNetwork
import app.expenses.core.domain.CardTier
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.BillStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Accounts as cards: the network, tier and last four digits, and the digits finding the account for a bill or a bank text. */
@RunWith(AndroidJUnit4::class)
class AccountTilesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performTextClearance(); node(tag).performTextInput(text) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)); waitFor("add") }
    /** Opens the Accounts page; with several accounts the cards start stacked like a wallet, so one tap spreads them out before any card is tapped. */
    private fun openAccounts() {
        compose.openMore(); tap("manage_accounts"); waitFor("accounts_list"); waitFor("accounts_stack")
        val several = (runBlocking { container.write { it.db.schemaQueries.selectAccounts().executeAsList().count { a -> a.archived == 0L } } } ?: 0) > 1
        if (several) { tap("accounts_stack"); waitFor("accounts_stack_close") }
    }
    private fun look(id: String) = runBlocking { container.write { it.looks.get(id) } }
    private fun idOf(name: String) = runBlocking { container.write { it.db.schemaQueries.selectAccounts().executeAsList().first { a -> a.name == name }.id } }!!
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!
    private fun save(vararg a: Account) = runBlocking { container.write { ok -> a.forEach { ok.store.saveAccount(it) } } }
    private val inr = Currency.INR

    @Test
    fun aCreditCardIsCreatedWithItsNetworkTierAndDigitsAndShownAsATileWithThem() {
        launch(); openAccounts()
        tap("add_account"); type("account_name", "Axis Flipkart"); tap("type_CREDIT_CARD"); tap("network_VISA"); tap("tier_PREMIUM"); type("account_last4", "7710"); tap("account_save")
        waitFor("account_Axis Flipkart")
        assertEquals(AccountLook(idOf("Axis Flipkart"), CardNetwork.VISA, CardTier.PREMIUM, "7710"), look(idOf("Axis Flipkart")))
        compose.onNodeWithTag("account_Axis Flipkart").assertTextContains("7710", substring = true) // the tile reads as one: its parts merged
        compose.onNodeWithTag("account_Axis Flipkart").assertTextContains("Visa", substring = true)
    }

    @Test
    fun tappingTheChosenNetworkAgainClearsItAndTheDigitsFieldTakesOnlyFourDigits() {
        save(Account(AccountId("c"), "Card", AccountType.CREDIT_CARD, inr)); runBlocking { container.write { it.looks.save(AccountLook("c", CardNetwork.AMEX, CardTier.CLASSIC, "1005")) } }
        launch(); openAccounts(); tap("account_Card")
        tap("network_AMEX"); type("account_last4", ""); for (c in "12a3456") node("account_last4").performTextInput(c.toString()) // typed one key at a time, as a person does
        tap("account_save"); waitFor("account_Card")
        assertEquals(AccountLook("c", null, CardTier.CLASSIC, "1234"), look("c"), "the network is cleared, and letters and extra digits never got in")
    }

    @Test
    fun aBankAccountKeepsItsDigitsButNeverANetworkOrTierAndSwitchingAwayFromCardDropsThem() {
        save(Account(AccountId("c"), "Card", AccountType.CREDIT_CARD, inr)); runBlocking { container.write { it.looks.save(AccountLook("c", CardNetwork.RUPAY, CardTier.PLATINUM, "3302")) } }
        launch(); openAccounts(); tap("account_Card"); tap("type_BANK")
        assertTrue(!has("network_VISA") && !has("tier_PREMIUM"), "network and tier are a credit card's")
        tap("account_save"); waitFor("account_Card")
        assertEquals(AccountLook("c", null, null, "3302"), look("c"))
    }

    @Test
    fun theAccountsPageListsWhereMoneySitsFirstThenCardsRegardlessOfTheirNames() {
        save(Account(AccountId("a"), "Amex", AccountType.CREDIT_CARD, inr), Account(AccountId("z"), "Zeta Savings", AccountType.BANK, inr))
        launch(); openAccounts()
        val bank = node("account_Zeta Savings").getBoundsInRoot().top; val card = node("account_Amex").getBoundsInRoot().top
        assertTrue(bank < card, "the bank account comes before the card although 'Amex' sorts first by name")
    }

    @Test
    fun aBillsCardIsPickedByItsLastFourDigitsWhenThereAreTwoCards() {
        save(Account(AccountId("amex"), "Amex", AccountType.CREDIT_CARD, inr), Account(AccountId("visa"), "Visa Card", AccountType.CREDIT_CARD, inr))
        runBlocking { container.write { it.looks.save(AccountLook("visa", CardNetwork.VISA, null, "0862")) } }
        val due = LocalDate.now().plusDays(8).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Your HDFC Bank Credit Card ending 0862 statement is generated. Total Amt Due Rs.5,000.00, Min Amt Due Rs.250.00, Due Date $due.", System.currentTimeMillis()) }
        val id = runBlocking { container.write { it.bills.list(BillStatus.FOUND).single().id.also { b -> it.bills.track(b) } } }!!
        launch(); compose.openMore(); tap("open_bills"); waitFor("bills_list")
        tap("bill_pay_$id"); waitFor("bill_pay_amount"); tap("bill_pay_ok") // no card tapped: the one carrying 0862 is already chosen
        compose.waitUntil(10_000) { entries().isNotEmpty() }
        assertEquals("visa", entries().single().counter_account_id, "the Visa, not the Amex")
    }

    @Test
    fun aBankTextIsSuggestedTheAccountThatCarriesItsDigits() {
        save(Account(AccountId("sav"), "Savings", AccountType.BANK, inr))
        runBlocking { container.write { it.looks.save(AccountLook("sav", last4 = "7777")) } }
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Rs.500.00 debited from A/c XX7777 on ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yy"))} to VPA cafe@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161", System.currentTimeMillis()) }
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_list")
        val inboxId = runBlocking { container.write { it.inbox.list().single().id } }!!
        tap("inbox_add_$inboxId"); compose.waitUntil(10_000) { has("inbox_empty") }
        assertEquals("sav", entries().single().account_id, "not the default Bank account")
    }

    @Test
    fun aPastedValueKeepsItsLastFourDigitsAndAFifthKeyTypedAfterFourIsIgnored() {
        save(Account(AccountId("b"), "Savings", AccountType.BANK, inr))
        launch(); openAccounts(); tap("account_Savings")
        type("account_last4", "XX 7710"); tap("account_save"); waitFor("account_Savings")
        assertEquals("7710", look("b")?.last4, "pasted with letters and a space")
        tap("account_Savings"); type("account_last4", "**** 4821 "); tap("account_save"); waitFor("account_Savings")
        assertEquals("4821", look("b")?.last4)
        tap("account_Savings"); type("account_last4", ""); for (c in "123456") node("account_last4").performTextInput(c.toString()); tap("account_save"); waitFor("account_Savings")
        assertEquals("1234", look("b")?.last4, "typing a fifth digit does nothing")
    }

    @Test
    fun severalAccountsStartStackedLikeAWalletAndOneTapSpreadsThemApartAndAnotherStacksThemAgain() {
        launch(); compose.openMore(); tap("manage_accounts"); waitFor("accounts_list"); waitFor("accounts_stack")
        fun y(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        fun gap() = kotlin.math.abs(y("account_Bank") - y("account_Cash"))
        val stackedGap = gap()
        assertTrue(stackedGap in 20f..200f, "stacked: each card shows only its edge ($stackedGap px apart)")
        assertTrue(!has("accounts_stack_close"), "nothing to close while stacked")
        tap("accounts_stack"); waitFor("accounts_stack_close")
        compose.waitUntil(10_000) { gap() > 400f }
        tap("accounts_stack_close"); compose.waitUntil(10_000) { !has("accounts_stack_close") }
        compose.waitUntil(10_000) { gap() < 200f }
    }
}
