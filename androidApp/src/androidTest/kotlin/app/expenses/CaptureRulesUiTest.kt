package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
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
class CaptureRulesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { scenario?.close(); resetApp() }

    private fun text(vpa: String, ref: String) = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA $vpa (UPI Ref $ref). Not you? Call 18002586161"
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }
    private fun launch() { scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun openRules() { tap("more_menu"); tap("open_capture"); tap("open_rules"); waitFor("rule_pattern") }
    private fun openInbox() { tap("more_menu"); tap("open_inbox"); waitFor("inbox_back") }
    private fun pendingId() = runBlocking { container.write { it.inbox.list().first().id } }!!
    private fun addRule(pattern: String, category: String) { compose.onNodeWithTag("rule_pattern", useUnmergedTree = true).performTextInput(pattern); tap("rule_category_$category"); tap("rule_add"); compose.waitUntil(10_000) { has("rule_$pattern") } }

    @Test
    fun aRuleIsTestedAgainstHistoryAddedAndThenSuggestedOnTheMatchingInboxItem() {
        runBlocking { container.write { ok -> repeat(3) { ok.store.save(Expense(EntryId("h$it"), 1_000L + it, AccountId("bank"), Money(100, Currency.INR)), EntryDetails(merchant = "Shop number $it")) } } }
        ingest(text("shop@okhdfc", "111111111111"), 5_000)
        launch(); openRules()
        compose.onNodeWithTag("rule_pattern", useUnmergedTree = true).performTextInput("shop"); tap("rule_test")
        compose.waitUntil(10_000) { has("rule_tested") }
        assertTrue(textOf("rule_tested").contains("matched 3 of your past entries"), textOf("rule_tested"))
        tap("rule_category_Food"); tap("rule_add"); compose.waitUntil(10_000) { has("rule_shop") }
        tap("rules_back"); tap("capture_back"); openInbox()
        val id = pendingId(); waitFor("inbox_goes_$id")
        assertTrue(textOf("inbox_goes_$id").contains("Food (your rule)"), textOf("inbox_goes_$id"))
    }

    @Test
    fun theFirstRuleWinsAndMovingARuleUpChangesTheSuggestion() {
        ingest(text("uber.eats@icici", "222222222222"), 5_000)
        launch(); openRules()
        addRule("uber", "Transport"); addRule("eats", "Food")
        tap("rules_back"); tap("capture_back"); openInbox()
        val id = pendingId(); waitFor("inbox_goes_$id")
        assertTrue(textOf("inbox_goes_$id").contains("Transport (your rule)"), "uber is first")
        tap("inbox_back"); tap("more_menu"); tap("open_capture"); tap("open_rules")
        tap("rule_up_eats")
        compose.waitUntil(10_000) { runBlocking { container.write { it.captureRules.rules().first().pattern } } == "eats" }
        tap("rules_back"); tap("capture_back"); openInbox(); waitFor("inbox_goes_$id")
        assertTrue(textOf("inbox_goes_$id").contains("Food (your rule)"), textOf("inbox_goes_$id"))
        tap("inbox_back"); tap("more_menu"); tap("open_capture"); tap("open_rules"); tap("rule_remove_eats")
        compose.waitUntil(10_000) { !has("rule_eats") }
        assertEquals(listOf("uber"), runBlocking { container.write { it.captureRules.rules().map { r -> r.pattern } } })
    }

    @Test
    fun whatYouChooseForAPayeeIsSuggestedNextTimeAndACorrectionSticks() {
        ingest(text("dosa@okaxis", "333333333331"), 5_000)
        launch(); openInbox()
        var id = pendingId(); waitFor("inbox_goes_$id")
        assertTrue(textOf("inbox_goes_$id").contains("no category"))
        tap("inbox_change_$id"); tap("inbox_category_${id}_Food"); tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        ingest(text("dosa@okaxis", "333333333332"), 6_000); tap("inbox_back"); openInbox()
        id = pendingId(); waitFor("inbox_goes_$id")
        assertTrue(textOf("inbox_goes_$id").contains("Food (what you chose last time)"), textOf("inbox_goes_$id"))
        tap("inbox_change_$id"); tap("inbox_category_${id}_Fun"); tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        ingest(text("dosa@okaxis", "333333333333"), 7_000); tap("inbox_back"); openInbox()
        id = pendingId(); waitFor("inbox_goes_$id")
        assertTrue(textOf("inbox_goes_$id").contains("Fun (what you chose last time)"), "the correction replaced the earlier choice: " + textOf("inbox_goes_$id"))
    }
}
