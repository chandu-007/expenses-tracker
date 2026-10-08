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
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
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
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PairingUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { scenario?.close(); resetApp() }

    private val zone = ZoneId.systemDefault()
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
    private fun ingest(sender: String, body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun card() = runBlocking { container.write { it.store.saveAccount(Account(AccountId("amex"), "Amex", AccountType.CREDIT_CARD, Currency.INR)) } }
    private fun launch() { scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun openInbox() { tap("more_menu"); tap("open_inbox"); waitFor("inbox_back") }
    private fun idAt(t: Long) = runBlocking { container.write { it.inbox.list().first { i -> i.receivedAt == t }.id } }!!
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.recentEntries(50).executeAsList() } }!!
    private fun balance(id: String) = runBlocking { container.write { it.store.balances().getValue(AccountId(id)).minorUnits } }!!

    @Test
    fun anAtmWithdrawalIsRecordedAsMoneyMovedToCashNotAsSpending() {
        ingest("AX-HDFCBK-S", "Rs.5,000.00 withdrawn at ATM 12345 on 05-10-26 from A/c XX1234", 5_000)
        launch(); openInbox(); val id = idAt(5_000); waitFor("inbox_pair_$id")
        assertTrue(textOf("inbox_pair_$id").contains("cash withdrawal: from Bank to Cash"), textOf("inbox_pair_$id"))
        tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        val e = entries().single()
        assertEquals("TRANSFER", e.kind); assertEquals(500_000, e.amount_minor); assertEquals("bank", e.account_id); assertEquals("cash", e.counter_account_id)
        assertEquals(-500_000L, balance("bank")); assertEquals(500_000L, balance("cash"))
    }

    @Test
    fun aCardPaymentSeenFromBothSidesIsOneTransferAndTheSecondTextIsFlaggedAsTheSame() {
        card()
        ingest("AX-HDFCBK-S", "Rs.15,000.00 debited from A/c XX1234 on 05-10-26 towards credit card bill payment", 5_000)
        ingest("AX-AXISBK-S", "Payment of INR 15000 has been received towards your Axis Bank Credit Card XX0000 on 05-10-26 - Axis Bank", 1_000_000)
        launch(); openInbox(); val bank = idAt(5_000); val cardSide = idAt(1_000_000)
        waitFor("inbox_pair_$bank")
        assertTrue(textOf("inbox_pair_$bank").contains("credit card payment: from Bank to Amex"), textOf("inbox_pair_$bank"))
        tap("inbox_add_$bank"); compose.waitUntil(10_000) { !has("inbox_$bank") }
        val e = entries().single(); assertEquals("TRANSFER", e.kind); assertEquals("bank", e.account_id); assertEquals("amex", e.counter_account_id)
        waitFor("inbox_duplicate_$cardSide")
        assertTrue(textOf("inbox_duplicate_$cardSide").startsWith("Looks like one you already added"))
        tap("inbox_same_$cardSide"); compose.waitUntil(10_000) { has("inbox_empty") }
        assertEquals(1, entries().size); assertEquals(1_500_000L, balance("amex"), "the card's debt is down by what was paid, once"); assertEquals(-1_500_000L, balance("bank"))
    }

    @Test
    fun aRefundIsLinkedToTheMatchingPurchaseSoSpendingGoesDownInsteadOfIncomeGoingUp() {
        card()
        runBlocking { container.write { it.store.save(Expense(EntryId("buy"), at(2026, 10, 2), AccountId("amex"), Money(254_000, Currency.INR)), EntryDetails(merchant = "AMAZON")) } }
        ingest("AX-AXISBK-S", "Refund of INR 2540.00 credited to your Axis Bank Credit Card XX0000 on 05-10-26 from AMAZON", 5_000)
        launch(); openInbox(); val id = idAt(5_000); waitFor("inbox_pair_$id")
        assertTrue(textOf("inbox_pair_$id").contains("refund of AMAZON"), textOf("inbox_pair_$id"))
        tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        val refund = entries().first { it.kind == "REFUND" }
        assertEquals("buy", refund.refunds_entry); assertEquals(254_000, refund.amount_minor); assertEquals("amex", refund.account_id)
        assertEquals(0L, balance("amex"), "the purchase and its refund cancel out on the card")
        assertEquals(1, entries().count { it.kind == "EXPENSE" } )
    }

    @Test
    fun withNoCardAccountACardPaymentIsJustAPaymentAndAPairedItemCanStillBeAddedPlainly() {
        ingest("AX-AXISBK-S", "Payment of INR 15000 has been received towards your Axis Bank Credit Card XX0000 on 05-10-26 - Axis Bank", 5_000)
        ingest("AX-HDFCBK-S", "Rs.5,000.00 withdrawn at ATM 12345 on 05-10-26 from A/c XX1234", 9_000_000)
        launch(); openInbox(); val card = idAt(5_000); val atm = idAt(9_000_000)
        waitFor("inbox_$card"); assertTrue(!has("inbox_pair_$card"), "no card account to pair with")
        waitFor("inbox_pair_$atm"); tap("inbox_plain_$atm")
        compose.waitUntil(10_000) { !has("inbox_pair_$atm") }
        tap("inbox_add_$atm"); compose.waitUntil(10_000) { !has("inbox_$atm") }
        val e = entries().single(); assertEquals("EXPENSE", e.kind); assertEquals(500_000, e.amount_minor)
    }

    @Test
    fun aRefundWithNoMatchingPurchaseIsPlainIncomeAndAPurchaseTooOldOrTooSmallIsNotMatched() {
        card()
        runBlocking { container.write { it.store.save(Expense(EntryId("old"), at(2026, 5, 1), AccountId("amex"), Money(254_000, Currency.INR)), EntryDetails(merchant = "AMAZON")); it.store.save(Expense(EntryId("small"), at(2026, 10, 2), AccountId("amex"), Money(100_000, Currency.INR)), EntryDetails(merchant = "AMAZON")) } }
        ingest("AX-AXISBK-S", "Refund of INR 2540.00 credited to your Axis Bank Credit Card XX0000 on 05-10-26 from AMAZON", 5_000)
        launch(); openInbox(); val id = idAt(5_000); waitFor("inbox_$id")
        assertTrue(!has("inbox_pair_$id"))
    }

    @Test
    fun aPurchaseAlreadyRefundedInFullIsNotMatchedAgain() {
        card()
        runBlocking { container.write { ok ->
            ok.store.save(Expense(EntryId("buy"), at(2026, 10, 2), AccountId("amex"), Money(254_000, Currency.INR)), EntryDetails(merchant = "AMAZON"))
            ok.store.save(app.expenses.core.domain.ledger.Refund(EntryId("r1"), at(2026, 10, 3), AccountId("amex"), Money(254_000, Currency.INR), EntryId("buy")))
        } }
        ingest("AX-AXISBK-S", "Refund of INR 2540.00 credited to your Axis Bank Credit Card XX0000 on 05-10-26 from AMAZON", 5_000)
        launch(); openInbox(); val id = idAt(5_000); waitFor("inbox_$id")
        assertTrue(!has("inbox_pair_$id"), "nothing left to refund on that purchase")
    }
}
