package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.storage.EntryDetails
import app.expenses.ui.Flow3
import app.expenses.ui.flowOf
import app.expenses.ui.signedAmount
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Money out shows with a minus, money in with a plus, and a move between your own accounts with neither. */
@RunWith(AndroidJUnit4::class)
class AmountSignTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    @Test
    fun theKindDecidesTheFlowAndTheSign() {
        assertEquals(Flow3.OUT, flowOf("EXPENSE", false)); assertEquals(Flow3.IN, flowOf("INCOME", true)); assertEquals(Flow3.IN, flowOf("REFUND", true))
        assertEquals(Flow3.MOVE, flowOf("TRANSFER", false)); assertEquals(Flow3.MOVE, flowOf("TRANSFER", true), "a transfer is a move whichever way the flag points")
        assertEquals("-₹5.00", signedAmount("₹5.00", Flow3.OUT)); assertEquals("-₹5.00", signedAmount("-₹5.00", Flow3.OUT), "no double minus")
        assertEquals("+₹5.00", signedAmount("₹5.00", Flow3.IN)); assertEquals("+₹5.00", signedAmount("+₹5.00", Flow3.IN)); assertEquals("₹5.00", signedAmount("₹5.00", Flow3.MOVE))
    }

    @Test
    fun theListShowsAMinusOnExpensesAPlusOnIncomeAndNoSignOnATransfer() {
        val now = System.currentTimeMillis()
        runBlocking { container.write { ok ->
            ok.store.save(Expense(EntryId("a"), now - 3000, AccountId("bank"), Money(12_500, Currency.INR)), EntryDetails(merchant = "Lunch"))
            ok.store.save(Income(EntryId("b"), now - 2000, AccountId("bank"), Money(500_000, Currency.INR)), EntryDetails(merchant = "Salary"))
            ok.store.save(Transfer(EntryId("c"), now - 1000, AccountId("bank"), AccountId("cash"), Money(20_000, Currency.INR)), EntryDetails(merchant = "ATM"))
        } }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("amount_ATM"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        assertEquals("-₹125.00", text("amount_Lunch")); assertEquals("+₹5,000.00", text("amount_Salary")); assertEquals("₹200.00", text("amount_ATM"))
    }
}
