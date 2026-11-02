package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The calm card on Home for expenses with no category, and the one-at-a-time review it opens: shown from three, "Not now" for a week, a choice saved and learned, Skip, "N left", the done
 * state and Undo for the last choice. Invented shops and amounts only.
 */
@RunWith(AndroidJUnit4::class)
class CategoriseUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun db(block: (AppContainer.Opened.Ok) -> Unit) { runBlocking { container.write { block(it) } } }

    private fun bare(id: String, at: Long, merchant: String, source: String? = null) = db { ok ->
        ok.store.save(Expense(EntryId(id), at, AccountId("bank"), Money(10_000 + at, Currency.INR)), EntryDetails(merchant = merchant, source = source ?: "manual"))
    }
    private fun categoryOf(id: String) = runBlocking { container.write { ok -> ok.db.schemaQueries.splitsOf(id).executeAsList().singleOrNull()?.category_id } }
    private fun learned() = runBlocking { container.write { it.captureRules.learned() } }!!

    private fun freshpikAndZorbo() { bare("u0", 1_000, "FRESHPIK BANGALORE IN"); bare("u1", 1_001, "freshpik@ybl"); bare("u2", 1_002, "Freshpik"); bare("u3", 1_003, "Zorbo Stores") }

    @Test
    fun theCardShowsFromThreeExpensesWithNoCategoryAndNeverBeforeAndSaysOnlyACount() {
        bare("a", 1_000, "One"); bare("b", 1_001, "Two")
        db { ok -> ok.store.save(Expense(EntryId("c"), 1_002, AccountId("bank"), Money(500, Currency.INR), listOf(CategorySplit(CategoryId("Food"), Money(500, Currency.INR)))), EntryDetails(merchant = "Has a category")) }
        launch(); Thread.sleep(2_500)
        assertFalse(has("categorise_nudge"), "two without a category: no card")
        bare("d", 1_003, "Three")
        launch(); waitFor("categorise_nudge")
        assertEquals("3 expenses have no category", textOf("categorise_nudge_text"))
    }

    @Test
    fun notNowHidesTheCardForAWeekAndItComesBackAfterwards() {
        freshpikAndZorbo()
        launch(); waitFor("categorise_nudge"); assertEquals("4 expenses have no category", textOf("categorise_nudge_text"))
        tap("categorise_nudge_later")
        compose.waitUntil(10_000) { !has("categorise_nudge") }
        assertTrue(runBlocking { container.write { it.templates.setting("categorise_nudge_snoozed_at") } }?.toLongOrNull() != null, "the choice is kept as a setting")
        launch(); Thread.sleep(2_500); assertFalse(has("categorise_nudge"), "still quiet after a restart")
        db { ok -> ok.templates.putSetting("categorise_nudge_snoozed_at", (System.currentTimeMillis() - 8L * 86_400_000).toString()) }
        launch(); waitFor("categorise_nudge")
    }

    @Test
    fun reviewShowsOneAtATimeFilesWhatIsChosenLearnsItSuggestsItForThePayeesOthersThenTheEndSaysWhatWasFiled() {
        freshpikAndZorbo()
        launch(); tap("categorise_nudge_review"); waitFor("categorise_screen")
        waitFor("categorise_item_u2")
        assertEquals("Freshpik", textOf("categorise_title"), "the payee with the most waiting first, its newest entry first")
        assertEquals("4 left", textOf("categorise_left"))
        assertFalse(has("categorise_suggested_label"), "nothing known about this payee yet")
        tap("categorise_chip_Groceries")
        waitFor("categorise_item_u1"); assertEquals("3 left", textOf("categorise_left"))
        assertEquals("Groceries", categoryOf("u2"))
        assertEquals(mapOf("freshpik" to "Groceries"), learned(), "the choice is learned for the payee")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() }
        // the same payee written another way is suggested what was just chosen, as a tap, never applied
        compose.onNodeWithTag("categorise_suggest_Groceries").assertTextContains("what you chose last time", substring = true)
        assertEquals(null, categoryOf("u1"), "a suggestion is not applied by itself")
        tap("categorise_suggest_Groceries")
        waitFor("categorise_item_u0"); assertEquals("2 left", textOf("categorise_left"))
        compose.onNodeWithTag("categorise_suggest_Groceries").assertTextContains("you chose it 2 times", substring = true)
        tap("categorise_suggest_Groceries")
        waitFor("categorise_item_u3"); assertEquals("1 left", textOf("categorise_left"))
        assertEquals(listOf("Groceries", "Groceries", "Groceries"), listOf("u0", "u1", "u2").map { categoryOf(it) })
        tap("categorise_chip_Fun")
        waitFor("categorise_done"); assertTrue(textOf("categorise_done_text").contains("Filed 4 expenses"), textOf("categorise_done_text"))
        assertEquals("Fun", categoryOf("u3")); assertEquals(mapOf("freshpik" to "Groceries", "zorbo" to "Fun"), learned())
        // (Undo of the last choice: see the note in docs/PHASE2_PROGRESS.md, Slice 100: its device test did not pass and is not claimed)
        tap("categorise_finish")
        waitFor("add")
    }

    @Test
    fun skipLeavesAnExpenseAsItWasAndTheEndSaysWhatWasFiledAndWhatWasLeft() {
        bare("a", 1_000, "Alpha Traders"); bare("b", 1_001, "Beta Traders"); bare("c", 1_002, "Gamma Traders")
        launch(); tap("categorise_nudge_review"); waitFor("categorise_screen")
        waitFor("categorise_item_c"); tap("categorise_skip")
        waitFor("categorise_item_b"); assertEquals("2 left", textOf("categorise_left")); tap("categorise_chip_Fun")
        waitFor("categorise_item_a"); assertEquals("1 left", textOf("categorise_left")); tap("categorise_skip")
        waitFor("categorise_done")
        assertEquals("Filed 1 expense. Left 2 as they were.", textOf("categorise_done_text"))
        assertEquals(listOf(null, "Fun", null), listOf("a", "b", "c").map { categoryOf(it) })
        tap("categorise_finish"); waitFor("add")
        Thread.sleep(2_500)
        assertFalse(has("categorise_nudge"), "two left: below three, no card")
    }

    @Test
    fun moreCategoriesAreBehindMoreAndACapturedEntryIsLearnedLikeAnEditOfIt() {
        db { ok ->
            ok.store.save(Expense(EntryId("s1"), 1_000, AccountId("bank"), Money(20_000, Currency.INR)), EntryDetails(merchant = "swiggy@icici", source = "sms", sourceRef = "100000000011"))
            ok.origins.put("s1", app.expenses.core.storage.OriginKind.SMS, "AX-HDFCBK-S", "Rs.200.00 debited from A/c XX1234 on 05-10-26 to VPA swiggy@icici (UPI Ref 100000000011).", 900)
            bare("x", 1_001, "Xylo One"); bare("y", 1_002, "Yarn Two")
        }
        launch(); tap("categorise_nudge_review"); waitFor("categorise_screen")
        waitFor("categorise_item_y"); tap("categorise_skip"); waitFor("categorise_item_x"); tap("categorise_skip")
        waitFor("categorise_item_s1")
        // the starter list knows Swiggy: a suggestion to tap, with where it comes from
        compose.onNodeWithTag("categorise_suggest_Food").assertTextContains("a common merchant", substring = true)
        tap("categorise_more"); waitFor("category_search")
        compose.onNodeWithTag("category_search", useUnmergedTree = true).performTextInput("Rent")
        val target = runBlocking { container.write { ok -> ok.categories.list().first { it.name == "Rent" }.id } }!!
        tap("category_option_$target")
        waitFor("categorise_done")
        assertEquals(target, categoryOf("s1"))
        assertEquals(mapOf("swiggy" to target), learned(), "a captured entry is taught under the key of its text's payee, like an edit")
    }
}
