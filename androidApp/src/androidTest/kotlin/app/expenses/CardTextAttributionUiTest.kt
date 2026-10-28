package app.expenses

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Flow
import app.expenses.core.domain.StatementTxn
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasTestTag
import java.time.LocalDate as JLocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

/**
 * A spend on a credit card, including a UPI payment made on the card, is suggested on the CARD account (the digits in the text are the card's), not on the bank
 * account, and the later statement line of the card pairs with the entry made from the text instead of being added again. Texts and amounts are invented.
 */
@RunWith(AndroidJUnit4::class)
class CardTextAttributionUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() {
        resetApp()
        runBlocking {
            container.write {
                it.store.saveAccount(Account(AccountId("hdfccard"), "HDFC Regalia", AccountType.CREDIT_CARD, Currency.INR))
                it.looks.save(AccountLook("hdfccard", last4 = "1234")); it.looks.save(AccountLook("bank", last4 = "5678"))
            }
        }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val zone = ZoneId.systemDefault()
    private fun at(d: Int) = JLocalDate.of(2026, 10, d).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); compose.waitUntil(10_000) { has("add") } }
    private fun vm(): LedgerViewModel { var m: LedgerViewModel? = null; scenario!!.onActivity { m = ViewModelProvider(it)[LedgerViewModel::class.java] }; return m!! }
    private fun rowFor(): InboxRowUi { val v = vm(); v.loadInbox(); compose.waitUntil(10_000) { v.inbox.value.isNotEmpty() }; return v.inbox.value.first() }

    @Test
    fun aCardSpendTextIsSuggestedOnTheCard() {
        ingest("Rs 500.00 spent on HDFC Bank Card XX1234 at SWIGGY on 05-10-26. Avl limit Rs 90,000.00", at(5))
        launch()
        assertEquals("hdfccard", rowFor().accountId, "a card spend lands on the card")
    }

    @Test
    fun aUpiPaymentMadeOnTheCreditCardIsSuggestedOnTheCardNotTheBank() {
        ingest("Rs.500.00 spent on HDFC Bank Credit Card XX1234 via UPI to swiggy@okhdfc on 05-10-26. UPI Ref 628374659283", at(5))
        launch()
        assertEquals("hdfccard", rowFor().accountId)
    }

    @Test
    fun theSameUpiPaymentMadeFromTheBankAccountIsSuggestedOnTheBank() {
        ingest("Rs.500.00 debited from A/c XX5678 on 05-10-26 to VPA swiggy@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161", at(5))
        launch()
        assertEquals("bank", rowFor().accountId)
    }

    @Test
    fun theStatementLineOfTheCardPairsWithTheEntryMadeFromTheText() {
        ingest("Rs 500.00 spent on HDFC Bank Card XX1234 at SWIGGY on 05-10-26. Avl limit Rs 90,000.00", at(5))
        launch()
        val v = vm(); val row = rowFor()
        var done: String? = "pending"
        v.confirmCapture(row.id, row.accountId, "", { done = it })
        compose.waitUntil(10_000) { done != "pending" }
        assertEquals(null, done)
        val r = runBlocking { container.write { ok ->
            ok.statements.import(listOf(StatementTxn(1, LocalDate(2026, 10, 5), Flow.SPENDING, 50_000, "SWIGGY BANGALORE IN")), AccountId("hdfccard"), Currency.INR, TimeZone.currentSystemDefault())
        } }!!
        assertEquals(0, r.imported, "the same purchase is not added again"); assertEquals(1, r.matchedExisting)
        assertEquals(1L, runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } })
    }
}
