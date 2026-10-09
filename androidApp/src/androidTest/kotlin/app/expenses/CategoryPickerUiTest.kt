package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
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

@RunWith(AndroidJUnit4::class)
class CategoryPickerUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun chips() = runBlocking { container.write { it.categories.list().map { c -> c.id } } }!!.filter { has("category_chip_$it") }
    private fun makeCategories(n: Int) = runBlocking { container.write { ok -> repeat(n) { ok.categories.create("Extra %03d".format(it)) } } }

    @Test
    fun aLongCategoryListShowsTwentyFourChipsAndMoreOpensASearchableListWhoseChoiceIsThenShownAndSaved() {
        makeCategories(52); launch() // 8 defaults + 52, in the database before the screen loads it
        tap("add"); waitFor("amount")
        compose.waitUntil(10_000) { has("more_categories") }
        assertEquals(24, chips().size, "chips shown")
        compose.onNodeWithTag("more_categories").assertTextContains("More (36)")
        compose.onNodeWithTag("more_categories", useUnmergedTree = true).performScrollTo().performClick(); waitFor("category_search")
        compose.onNodeWithTag("category_search", useUnmergedTree = true).performTextInput("extra 05")
        compose.waitUntil(10_000) { has("category_option_" + runBlocking { container.write { ok -> ok.categories.list().first { it.name == "Extra 051" }.id } }) }
        val id = runBlocking { container.write { ok -> ok.categories.list().first { it.name == "Extra 051" }.id } }!!
        assertTrue(!has("category_option_Food"), "the search filtered the list")
        tap("category_option_$id")
        compose.waitUntil(10_000) { has("category_chip_$id") && !has("category_search") }
        assertEquals(25, chips().size, "the chosen one is shown beyond the 24, never hidden")
        compose.onNodeWithTag("amount").performTextInput("75"); tap("save")
        compose.waitUntil(10_000) { has("add") && !has("amount") }
        val saved = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(1).executeAsList().single().let { e -> ok.db.schemaQueries.splitsOf(e.id).executeAsList().map { it.category_id } } } }
        assertEquals(listOf(id), saved)
    }

    @Test
    fun withFewCategoriesThereIsNoMoreChipAndEveryCategoryIsAChip() {
        launch(); tap("add"); waitFor("amount")
        assertEquals(8, chips().size)
        assertTrue(!has("more_categories"))
    }
}
