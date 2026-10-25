package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Paying in another currency while travelling: the rate is the person's own, the foreign amount is kept beside what left the account. */
@RunWith(AndroidJUnit4::class)
class ForeignExpenseUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.waitForIdle(); runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }; compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.EditableText)) c[SemanticsProperties.EditableText].text else c[SemanticsProperties.Text].joinToString(" ") { it.text } }

    @Test
    fun anExpensePaidInDollarsLeavesTheAccountAtTheChosenRateKeepsTheDollarsAndTheRateIsOfferedNextTime() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        tap("add"); waitFor("amount")
        tap("category_chip_Food"); type("note", "Taxi")
        tap("foreign_toggle"); tap("foreign_cur_USD"); type("foreign_amount", "25"); type("foreign_rate", "83.20")
        compose.waitUntil(10_000) { textOf("foreign_summary").startsWith("That is ₹2,080.00 from your account") }
        assertEquals("2080.00", textOf("amount"), "the amount follows what is worked out")
        tap("save"); waitFor("add")
        runBlocking { container.write { ok ->
            val e = ok.db.schemaQueries.recentEntries(5).executeAsList().single()
            assertEquals(208_000L, e.amount_minor, "what left the account"); assertEquals("INR", e.currency)
            assertEquals(2_500L, e.original_amount_minor); assertEquals("USD", e.original_currency)
        } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("USD 25.00", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } // the row says what was paid there
        // the next entry in dollars starts from the same rate
        tap("add"); waitFor("amount"); tap("foreign_toggle"); tap("foreign_cur_USD")
        compose.waitUntil(10_000) { textOf("foreign_rate") == "83.20" }
    }

    @Test
    fun withoutAnAmountAndARateTheFormExplainsAndKeepsSaveOffAndAnotherCurrencyEntryOpensReadOnlyWithAnExplanation() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        tap("add"); waitFor("amount"); tap("category_chip_Food"); type("note", "Hotel")
        tap("foreign_toggle"); tap("foreign_cur_EUR"); type("foreign_amount", "100")
        assertTrue(textOf("foreign_summary").contains("does not look rates up"), textOf("foreign_summary"))
        type("foreign_rate", "90"); compose.waitUntil(10_000) { textOf("foreign_summary").startsWith("That is ₹9,000.00") }
        tap("save"); waitFor("add")
        tap("row_Hotel"); compose.waitUntil(10_000) { compose.onAllNodes(hasText("paid in EUR", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
