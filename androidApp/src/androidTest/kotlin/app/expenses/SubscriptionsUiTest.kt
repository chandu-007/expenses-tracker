package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
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
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Subscriptions page: charges that keep coming, found from the person's own entries. */
@RunWith(AndroidJUnit4::class)
class SubscriptionsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR
    private val day = 86_400_000L

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }; compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
    private fun spend(id: String, merchant: String, minor: Long, daysAgo: Int) = runBlocking { container.write<Unit> { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis() - daysAgo * day, AccountId("cash"), Money(minor, inr)), EntryDetails(merchant = merchant)) } }
    private fun open() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add"); compose.openMore(); tap("open_subscriptions"); waitFor("subscriptions_list") }

    @Test
    fun aMonthlyChargeIsListedWithWhatItCostsAndCanBeMarkedNotOneAndBroughtBack() {
        for ((i, ago) in listOf(95, 65, 35, 5).withIndex()) spend("n$i", "Netflix", 64_900, ago)
        for ((i, ago) in listOf(100, 71, 38, 6).withIndex()) spend("e$i", "Electricity", listOf(120_000L, 310_000L, 90_000L, 250_000L)[i], ago)
        open()
        waitFor("subscription_Netflix")
        assertTrue(!has("subscription_Electricity"), "a bill that changes every month is not a subscription")
        // 649.00 a month is paid twelve times a year: 7,788.00
        assertEquals("About ₹649.00 a month", textOf("subscriptions_monthly"))
        assertTrue(textOf("subscriptions_yearly").startsWith("₹7,788.00 a year"), textOf("subscriptions_yearly"))
        tap("subscription_not_Netflix")
        compose.waitUntil(10_000) { !has("subscription_Netflix") && has("subscriptions_empty") }
        tap("subscriptions_show_ignored"); waitFor("subscription_Netflix")
    }

    @Test
    fun withNothingRepeatingThePageSaysSoAndHowToGetOne() {
        spend("a", "Cafe", 20_000, 3)
        open(); waitFor("subscriptions_empty")
    }
}
