package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.getOrNull
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Slice 135 (P4), the Add layout of the board: "New entry", the answer big over the small sum, ONE note field that is also the smart line, a grid of seven categories and "All N" (no sideways
 * scroll), ONE row of chips (account menu, date, trip, Details), the keypad in four columns with divide kept as a fifth key, and the vertical budget: at normal text on an ordinary phone the note,
 * the grid and the chips all sit above the pad without scrolling.
 */
@RunWith(AndroidJUnit4::class)
class UiS135AddLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun bounds(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot
    private fun pad(vararg keys: String) = keys.forEach { tap("pad_$it") }
    private fun dp(px: Float) = px / context.resources.displayMetrics.density

    @Test
    fun theFormIsCalledNewEntryAndTheAmountFieldIsStillCalledAmountForAScreenReader() {
        launch(); tap("add"); waitFor("amount"); waitFor("number_pad")
        compose.onNodeWithTag("sheet_handle", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("New entry"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the title")
        node("amount").assertAmountIs("")
        assertFalse(compose.onAllNodes(androidx.compose.ui.test.hasText("Amount (₹)"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "no 'Amount' line is drawn")
    }

    @Test
    fun theSumIsSmallAboveAndTheAnswerIsBigBelowAndNothingMovesWhenAnOperatorIsTyped() {
        launch(); tap("add"); waitFor("number_pad")
        pad("4", "5", "0"); assertFalse(has("amount_result"), "a plain amount is the big field itself")
        val before = bounds("category_chip_Food").top
        pad("plus", "1", "2", "0")
        waitFor("amount_result"); node("amount_result").assertTextContains("₹570.00", substring = true)
        node("amount").assertAmountIs("450+120")
        assertTrue(bounds("amount").height < bounds("amount_result").height, "the sum is the smaller line")
        assertEquals(before, bounds("category_chip_Food").top, "the grid does not move when the sum appears")
        compose.onNodeWithTag("save").assertTextContains("Add ₹570.00", substring = true) // Food is the category already chosen
    }

    @Test
    fun theKeypadIsFourColumnsWithBackspaceOnItAndDivideAFifthKeyEveryKeyAtLeast48dp() {
        launch(); tap("add"); waitFor("number_pad")
        // the board: 7 8 9 back / 4 5 6 + / 1 2 3 - / 00 0 . x, and divide after the times
        val grid = listOf(listOf("7", "8", "9", "back"), listOf("4", "5", "6", "plus"), listOf("1", "2", "3", "minus"), listOf("00", "0", "dot", "times", "divide"))
        grid.forEachIndexed { r, row ->
            val tops = row.map { bounds("pad_$it").top }; assertTrue(tops.distinct().size == 1, "row ${r + 1} is one line")
            assertTrue(row.map { bounds("pad_$it").left }.zipWithNext().all { (a, b) -> a < b }, "row ${r + 1} reads left to right")
            row.forEach { k -> val b = bounds("pad_$k"); assertTrue(dp(b.width) >= 47.9f && dp(b.height) >= 47.9f, "pad_$k is ${dp(b.width)} x ${dp(b.height)} dp, a target is 48") }
        }
        grid.zipWithNext().forEach { (a, b) -> assertTrue(bounds("pad_${a.first()}").top < bounds("pad_${b.first()}").top, "the rows go down") }
        assertTrue(bounds("pad_back").right <= bounds("number_pad").right, "backspace is inside the pad")
        pad("1", "2", "back"); node("amount").assertAmountIs("1")
    }

    @Test
    fun oneNoteFieldIsTheSmartLineAndTheCategoryGridIsSevenAndAllWithNoSidewaysScroll() {
        launch(); tap("add"); waitFor("number_pad"); waitFor("note")
        waitFor("smart_entry"); assertEquals(1, compose.onAllNodes(hasTestTag("note"), useUnmergedTree = true).fetchSemanticsNodes().size, "one field")
        assertTrue(has("more_categories"), "All N")
        val shown = listOf("Food", "Transport", "Groceries", "Bills", "Fun", "Shopping", "Health").filter { has("category_chip_$it") }
        assertEquals(7, shown.size, "seven tiles, the starter order")
        // 4 x 2: two rows, four columns
        val tops = shown.map { bounds("category_chip_$it").top }.distinct().sorted()
        assertEquals(2, tops.size, "two rows (seven tiles and All N)"); compose.onNodeWithTag("more_categories").assertTextContains("All ", substring = true)
        assertTrue(bounds("more_categories").top == tops[1], "All N is the eighth tile, on the second row")
        node("note").performTextInput("chai 20 yesterday"); waitFor("smart_preview")
    }

    @Test
    fun theChipRowHoldsTheAccountMenuTheDateAndDetailsAndTheNoteTheGridAndTheChipsAreAboveThePadWithoutScrolling() {
        launch(); tap("add"); waitFor("number_pad"); waitFor("form_more")
        val padTop = bounds("number_pad").top
        listOf("note", "category_chip_Food", "category_chip_Health", "more_categories", "account_menu", "date", "form_more").forEach { t ->
            assertTrue(bounds(t).bottom <= padTop, "$t ends at ${bounds(t).bottom} and the pad begins at $padTop: it must show without scrolling")
            assertTrue(bounds(t).top >= 0f, "$t is on the screen")
        }
        // the whole form content fits above the pad's fade: nothing needs a scroll
        val scroll = bounds("form_scroll")
        assertTrue(bounds("form_more").bottom <= scroll.bottom, "the last chip is inside the scrolling area")
        assertEquals(1, listOf("account_menu", "date", "form_more").map { bounds(it).top }.distinct().size, "one row of chips")
        listOf("account_menu", "date", "form_more", "amount_pad_toggle").forEach { t -> assertTrue(dp(bounds(t).height) >= 47.9f, "$t is ${dp(bounds(t).height)} dp high, a target is 48") }
        tap("form_more"); waitFor("form_more_panel")
    }

    @Test
    fun theSwitchBetweenThePadAndTheKeyboardIsAnIconAtTheEndOfTheHeader() {
        launch(); tap("add"); waitFor("number_pad"); waitFor("amount_pad_toggle")
        assertTrue(bounds("amount_pad_toggle").top < bounds("amount").top, "it is in the header, not under the amount")
        tap("amount_pad_toggle"); compose.waitUntil(15_000) { !has("number_pad") }
        tap("amount_pad_toggle"); waitFor("number_pad")
    }

    /** The board's row is "Bank, Today, Goa trip, Details": with a trip on, the four chips share one line at normal text on a 393 dp phone (the trip is seeded like the accessibility test does). */
    @Test
    fun withATripOnTheAccountTheDayTheTripAndDetailsStillShareOneRowAtNormalText() {
        val today = java.time.LocalDate.now().toEpochDay()
        runBlocking { container.write { ok -> ok.trips.save(app.expenses.core.domain.Trip("s135-goa", "Goa", "Trip: Goa", today - 1, today + 2, true, 100_000)) } }
        launch(); tap("add"); waitFor("number_pad"); waitFor("trip_chip"); waitFor("form_more")
        val tops = listOf("account_menu", "date", "trip_chip", "form_more").map { bounds(it).top }.distinct()
        assertEquals(1, tops.size, "one row of chips: $tops (a phone narrower than about 390 dp may wrap it; this check is for the 393 dp the board is drawn for)")
        assertTrue(bounds("form_more").bottom <= bounds("number_pad").top, "Details shows above the pad")
        assertTrue(node("trip_chip").fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty().joinToString(" ").startsWith("Tagged for Goa trip"))
    }
}
