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
        compose.openMore(); tap("manage_categories"); tap("suggested_categories")
        waitFor("suggested_add")
        tap("suggested_pick_Home")                       // a whole group the install does not start with: the group and its four subcategories
        tap("suggested_child_Transport_Fuel")            // one subcategory under a category the app starts with
        tap("suggested_child_Transport_Cab and auto")
        tap("suggested_add")
        waitFor("categories_back")
        compose.waitUntil(10_000) { cats().size > before }
        val all = cats()
        assertEquals(before + 7, all.size, "Home and its four subcategories, plus Fuel and Cab and auto")
        val home = all.single { it.parentId == null && it.name == "Home" }
        assertEquals(setOf("Furniture and appliances", "Repairs", "Maid and help", "Decor"), all.filter { it.parentId == home.id }.map { it.name }.toSet())
        assertEquals(1, all.count { it.parentId == null && it.name == "Transport" }, "Transport is the one the app started with, not a second one")
        assertEquals(setOf("Fuel", "Cab and auto"), all.filter { it.parentId == "Transport" }.map { it.name }.toSet(), "added under the existing Transport (its id is its name)")
        // asking again for what is now there adds nothing: the add button has nothing to do
        tap("suggested_categories"); waitFor("suggested_add")
        tap("suggested_pick_Home"); compose.waitForIdle()
        assertEquals(before + 7, cats().size)
    }

    @Test
    fun aNewInstallHasTheNewerStartersAndTheScreenSaysSoWhileAnOlderOneAddsThemInOneStep() {
        // a new install: the newer starters are there and marked as such on the screen
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        assertTrue(cats().any { it.name == "Subscriptions" } && cats().any { it.name == "Dividends" && it.kind == app.expenses.core.domain.CategoryKind.INCOME })
        compose.openMore(); tap("manage_categories"); tap("suggested_categories"); waitFor("suggested_add")
        assertTrue(has("suggested_group_Subscriptions") && has("suggested_pick_Dividends"))
        scenario?.close()
        // an install from before them: take them away (as it never had them), then add two in one go
        runBlocking { container.write { ok -> app.expenses.core.domain.StarterCategories.newExpense.forEach { ok.categories.delete(it.first) }; app.expenses.core.domain.StarterCategories.newIncome.forEach { ok.categories.delete(it.first) } } }
        assertTrue(cats().none { it.name == "Subscriptions" || it.name == "Dividends" })
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); tap("manage_categories"); tap("suggested_categories"); waitFor("suggested_add")
        tap("suggested_pick_Subscriptions"); tap("suggested_pick_Dividends"); tap("suggested_pick_Fuel")
        tap("suggested_add"); waitFor("categories_back")
        compose.waitUntil(10_000) { cats().any { it.name == "Dividends" } }
        val all = cats()
        assertEquals(app.expenses.core.domain.CategoryKind.EXPENSE, all.single { it.name == "Subscriptions" && it.parentId == null }.kind)
        assertEquals(app.expenses.core.domain.CategoryKind.INCOME, all.single { it.name == "Dividends" && it.parentId == null }.kind, "an income group is added as income")
        assertEquals(1, all.count { it.name == "Fuel" && it.parentId == null })
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
