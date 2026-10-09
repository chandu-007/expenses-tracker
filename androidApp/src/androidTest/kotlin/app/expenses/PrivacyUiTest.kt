package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PrivacyUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun text(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun anyText(s: String) = compose.onAllNodes(hasText(s, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun hidden(): String? = runBlocking { container.write { it.templates.setting("hide_amounts") } } // null until the setting is first written
    private fun seed() = db { ok ->
        ok.store.save(Expense(EntryId("e"), System.currentTimeMillis(), AccountId("cash"), Money(123_456, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(123_456, Currency.INR)))), EntryDetails(merchant = "Cafe"))
    }
    private fun toggle() { tap("more_menu"); tap("toggle_privacy") }

    @Test
    fun hidingAmountsCoversTheHeadlineTheRowsAndTheBalancesAndShowingBringsThemBack() {
        seed(); launch()
        text("month_spend").assertTextEquals("₹1,234.56")
        toggle()
        compose.waitUntil(10_000) { hidden() == "1" }
        text("month_spend").assertTextEquals("₹••••")
        assertTrue(!anyText("1,234.56"), "no amount is left anywhere on the home screen")
        assertTrue(anyText("Cafe"), "names are not hidden")
        toggle(); compose.waitUntil(10_000) { hidden() == "0" }
        text("month_spend").assertTextEquals("₹1,234.56")
    }

    @Test
    fun theChoiceSurvivesARestartAndTheMenuOfferToShowThemAgain() {
        seed(); launch(); toggle(); compose.waitUntil(10_000) { hidden() == "1" }
        launch(); text("month_spend").assertTextEquals("₹••••")
        tap("more_menu"); waitFor("toggle_privacy")
        assertTrue(anyText("Show amounts"))
    }

    @Test
    fun whatYouTypeIsNeverHiddenSoEntryStillWorks() {
        launch(); toggle(); compose.waitUntil(10_000) { hidden() == "1" }
        tap("add"); waitFor("amount")
        compose.onNodeWithTag("amount").performTextInput("250")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "250")
        tap("save"); waitFor("add")
        text("month_spend").assertTextEquals("₹••••")
        assertEquals(25_000L, db { it.db.schemaQueries.recentEntries(1).executeAsList().single().amount_minor })
    }

    @Test
    fun reportsAndTheCalendarHideTheirAmountsToo() {
        seed(); launch(); toggle(); compose.waitUntil(10_000) { hidden() == "1" }
        tap("open_reports"); waitFor("report_spent"); text("report_spent").assertTextEquals("₹••••")
        assertTrue(!anyText("1,234.56"))
        tap("reports_back"); waitFor("add"); tap("more_menu"); tap("open_calendar"); waitFor("cal_month")
        val day = java.time.LocalDate.now().dayOfMonth
        text("cal_amount_$day").assertTextEquals("₹••••")
        text("cal_total").assertTextEquals("Spent ₹••••")
        val spoken = text("cal_day_$day").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
        assertTrue(!spoken.contains("1.2K") && !spoken.contains("1,234"), "a screen reader is not told the amount either: $spoken")
    }
}
