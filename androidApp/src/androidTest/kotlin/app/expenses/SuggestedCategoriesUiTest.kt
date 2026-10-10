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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ready-made categories: ticked ones are added in one step under the right parents, nothing is added twice, and the all-categories list groups them. */
@RunWith(AndroidJUnit4::class)
class SuggestedCategoriesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun cats() = runBlocking { container.write { it.categories.list() } }!!

    @Test
    fun tickedSuggestionsAreAddedUnderTheRightParentsAndNothingIsDuplicated() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        val before = cats().size
        tap("more_menu"); tap("manage_categories"); tap("suggested_categories")
        waitFor("suggested_add")
        tap("suggested_pick_Rent")                       // a whole new group: the group and both of its subcategories
        tap("suggested_child_Transport_Fuel")            // one subcategory under a category the app starts with
        tap("suggested_child_Transport_Cab and auto")
        tap("suggested_add")
        waitFor("categories_back")
        compose.waitUntil(10_000) { cats().size > before }
        val all = cats()
        assertEquals(before + 5, all.size, "Rent and its two subcategories, plus Fuel and Cab and auto")
        val rent = all.single { it.parentId == null && it.name == "Rent" }
        assertEquals(setOf("House rent", "Maintenance charges"), all.filter { it.parentId == rent.id }.map { it.name }.toSet())
        assertEquals(1, all.count { it.parentId == null && it.name == "Transport" }, "Transport is the one the app started with, not a second one")
        assertEquals(setOf("Fuel", "Cab and auto"), all.filter { it.parentId == "Transport" }.map { it.name }.toSet(), "added under the existing Transport (its id is its name)")
        // asking again for what is now there adds nothing: the add button has nothing to do
        tap("suggested_categories"); waitFor("suggested_add")
        tap("suggested_pick_Rent"); compose.waitForIdle()
        assertEquals(before + 5, cats().size)
    }

    @Test
    fun theAllCategoriesListGroupsSubcategoriesUnderTheirParentUntilSomethingIsTyped() {
        runBlocking { container.write { ok ->
            val food = ok.categories.list().first { it.name == "Food" }
            ok.categories.create("Restaurants", food.id)
            // enough categories that the form's chips overflow into "More"
            repeat(30) { ok.categories.create("Extra $it") }
        } }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        tap("add"); tap("more_categories"); waitFor("category_list")
        val restaurants = cats().first { it.name == "Restaurants" }
        val food = cats().first { it.name == "Food" }
        // grouped: the child is listed by its own name, straight after its parent
        val texts = compose.onAllNodes(hasTestTag("category_list"), useUnmergedTree = true).fetchSemanticsNodes().single()
        assertTrue(has("category_option_${restaurants.id}") && has("category_option_${food.id}"))
        fun textOf(id: String) = compose.onNodeWithTag("category_option_$id", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        assertEquals("Restaurants", textOf(restaurants.id), "no \"Food › \" prefix while grouped")
        // searching gives the flat, fully named list
        compose.onNodeWithTag("category_search", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("category_search", useUnmergedTree = true).performTextInput("restaur")
        compose.waitUntil(10_000) { runCatching { textOf(restaurants.id) }.getOrNull() == "Food › Restaurants" }
    }
}
