package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Currency
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CurrencyUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() }; BaseCurrency.current = Currency.INR }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun openCurrency() { tap("more_menu"); tap("open_currency"); waitFor("currency_current") }
    private fun accountCurrencies() = runBlocking { container.write { ok -> ok.db.schemaQueries.allAccounts().executeAsList().map { it.currency } } }!!

    @Test
    fun beforeTheFirstEntryTheCurrencyChangesEverywhereThenIsLockedWithTheReasonShownOnScreen() {
        launch(); openCurrency()
        tap("currency_USD")
        compose.waitUntil(10_000) { textOf("currency_current").contains("USD") }
        assertEquals(listOf("USD", "USD"), accountCurrencies())
        tap("currency_back"); tap("add"); waitFor("amount")
        compose.onNodeWithTag("amount").performTextInput("1250.50"); tap("save")
        compose.waitUntil(10_000) { has("add") && compose.onAllNodes(androidx.compose.ui.test.hasText("USD 1,250.50", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        openCurrency()
        compose.waitUntil(10_000) { has("currency_blocker") }
        assertTrue(textOf("currency_blocker").contains("before the first entry"))
        tap("currency_EUR")
        compose.waitUntil(5_000) { textOf("currency_current").contains("USD") }
        assertEquals(listOf("USD", "USD"), accountCurrencies())
    }

    @Test
    fun theChoiceSurvivesARestartAndYenTakesNoDecimals() {
        launch(); openCurrency(); tap("currency_JPY")
        compose.waitUntil(10_000) { textOf("currency_current").contains("JPY") }
        compose.waitForIdle(); scenario?.close(); runBlocking { container.close() }; BaseCurrency.current = Currency.INR // as after the process was killed
        launch(); openCurrency()
        assertTrue(textOf("currency_current").contains("JPY"))
        assertEquals("JPY", BaseCurrency.current.code, "the database open loaded it")
        compose.onNodeWithTag("currency_JPY", useUnmergedTree = true).assertIsSelected()
        tap("currency_back"); tap("add"); waitFor("amount")
        compose.onNodeWithTag("amount").performTextInput("1250.5"); tap("save")
        Thread.sleep(1500)
        assertEquals(false, runBlocking { container.write { it.db.schemaQueries.entryCountEver().executeAsOne() } }, "yen with decimals is refused, nothing saved")
        compose.onNodeWithTag("amount").performTextInput("0") // "1250.50": still two decimals
        compose.onNodeWithTag("amount").performTextClearance(); compose.onNodeWithTag("amount").performTextInput("1250"); tap("save")
        compose.waitUntil(10_000) { has("add") && compose.onAllNodes(androidx.compose.ui.test.hasText("JPY 1,250", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
