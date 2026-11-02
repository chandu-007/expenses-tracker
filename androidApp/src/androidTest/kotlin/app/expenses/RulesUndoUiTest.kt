package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** The Undo bar on More > Capture > Category rules: removing a rule, forgetting a learned choice and changing one each come back exactly (place in the order, category, count in a row, label). */
@RunWith(AndroidJUnit4::class)
class RulesUndoUiTest {
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
    private fun openRules() { launch(); compose.openMore(); tap("open_capture"); tap("open_rules"); waitFor("rule_pattern") }
    private fun waitUndo() = compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() }
    private fun rules() = runBlocking { container.write { it.captureRules.rules().map { r -> r.id to r.pattern } } }!!
    private fun snapshot(key: String) = runBlocking { container.write { it.captureRules.snapshotLearned(key) } }

    @Test
    fun aRemovedRuleComesBackInItsPlaceWhenUndoIsPressed() {
        runBlocking { container.write { ok -> ok.captureRules.add("a", "uber", "Transport"); ok.captureRules.add("b", "swiggy", "Food"); ok.captureRules.add("c", "netflix", "Fun") } }
        openRules()
        tap("rule_remove_swiggy")
        compose.waitUntil(10_000) { rules().map { it.first } == listOf("a", "c") }
        waitUndo()
        assertEquals(true, compose.onAllNodes(hasText("Removed the rule \"swiggy\"", substring = true)).fetchSemanticsNodes().isNotEmpty(), "the bar says what was removed")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) { rules().map { it.first } == listOf("a", "b", "c") }
        waitFor("rule_swiggy")
        assertEquals(listOf("uber", "swiggy", "netflix"), rules().map { it.second })
        assertEquals("Food", runBlocking { container.write { it.captureRules.rules().first { r -> r.id == "b" }.categoryId } })
    }

    @Test
    fun aForgottenLearnedChoiceComesBackWithItsCountAndLabel() {
        runBlocking { container.write { ok -> ok.captureRules.learn("Freshpik", "Groceries", "Freshpik"); ok.captureRules.learn("FRESHPIK 2231", "Groceries"); ok.captureRules.learn("freshpik@ybl", "Groceries") } }
        val before = snapshot("freshpik")!!
        assertEquals(3, before.uses)
        openRules(); waitFor("learned_freshpik")
        tap("learned_forget_freshpik")
        compose.waitUntil(10_000) { !has("learned_freshpik") }
        waitUndo()
        compose.onNodeWithText("Undo").performClick()
        waitFor("learned_freshpik")
        assertEquals(before, snapshot("freshpik"), "category, count in a row and label are exactly as they were")
    }

    @Test
    fun aChangedLearnedChoiceGoesBackToWhatItWasWhenUndoIsPressed() {
        runBlocking { container.write { ok -> ok.captureRules.learn("Freshpik", "Groceries", "Freshpik"); ok.captureRules.learn("Freshpik", "Groceries") } }
        val before = snapshot("freshpik")!!
        openRules(); waitFor("learned_freshpik")
        tap("learned_change_freshpik"); waitFor("category_search")
        tap("category_option_Food")
        compose.waitUntil(10_000) { snapshot("freshpik")?.categoryId == "Food" }
        assertEquals(1, snapshot("freshpik")!!.uses, "a change counts as one choice")
        waitUndo()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) { snapshot("freshpik") == before }
        assertEquals(2, snapshot("freshpik")!!.uses)
    }
}
