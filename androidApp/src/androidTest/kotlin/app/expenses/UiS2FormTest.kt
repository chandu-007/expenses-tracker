package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
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
 * UI S2, the Add form: it opens amount first with the app's pad up and no keyboard, the pad does maths (450+800 is worked out as it is typed and saved as what it comes to), the category is a row of six
 * tiles (the most used first) and "More", "Usually Food for Chai" is offered from earlier entries, the big button says what it will do, and what is asked for only now and then is behind one "More".
 */
@RunWith(AndroidJUnit4::class)
class UiS2FormTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun spend(id: String, rupees: Long, category: String, note: String) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis(), AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))), EntryDetails(merchant = note)) }
    }
    private fun rows() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!
    private fun left(tag: String) = node(tag).fetchSemanticsNode().positionInRoot.x
    private fun pad(vararg keys: String) = keys.forEach { tap("pad_$it") }

    @Test
    fun addOpensAmountFirstWithThePadUpAndNoKeyboardAndTheButtonSaysWhatItWillDo() {
        launch(); tap("add"); waitFor("amount"); waitFor("number_pad")
        node("save").assertIsNotEnabled()
        compose.onNodeWithTag("save").assertTextContains("Add")
        pad("4", "5", "0")
        node("amount").assertTextContains("450", substring = true)
        tap("category_chip_Food")
        node("save").assertIsEnabled(); compose.onNodeWithTag("save").assertTextContains("Add ₹450.00 · Food", substring = true)
        tap("save"); waitFor("add"); waitGone("save")
        assertEquals(45_000L, rows().single().amount_minor)
    }

    @Test
    fun theAmountIsWorkedOutOnThePadAndWhatIsSavedIsWhatItComesTo() {
        launch(); tap("add"); waitFor("number_pad")
        pad("4", "5", "0", "plus", "8", "0", "0")
        node("amount").assertTextContains("450+800", substring = true)
        waitFor("amount_result"); node("amount_result").assertTextContains("₹1,250.00", substring = true)
        compose.onNodeWithTag("save").assertTextContains("Add ₹1,250.00", substring = true)
        tap("category_chip_Food"); tap("save"); waitGone("save")
        assertEquals(125_000L, rows().single().amount_minor)
    }

    @Test
    fun timesDivideAndMinusAndAnExpressionThatIsNotFinishedCannotBeSaved() {
        launch(); tap("add"); waitFor("number_pad")
        pad("4", "5", "0", "times", "1", "dot", "1", "8")
        node("amount_result").assertTextContains("₹531.00", substring = true)
        pad("minus"); node("save").assertIsNotEnabled() // it ends on an operator
        pad("1", "3", "1"); node("amount_result").assertTextContains("₹400.00", substring = true); node("save").assertIsEnabled()
        pad("divide", "0"); waitFor("amount_problem"); node("save").assertIsNotEnabled() // divides by zero
        pad("back"); pad("4"); node("amount_result").assertTextContains("₹498.25", substring = true) // 531 - 131 / 4: divide first
        tap("category_chip_Food"); tap("save"); waitGone("save")
        assertEquals(49_825L, rows().single().amount_minor)
    }

    @Test
    fun theKeyboardIsOneTapAwayAndTypingInTheFieldDoesTheSameMaths() {
        launch(); tap("add"); waitFor("number_pad")
        node("amount").performTextInput("120+45")
        waitGone("number_pad") // a field that is typed in brings the keyboard, which takes the pad's place
        node("amount_result").assertTextContains("₹165.00", substring = true)
        tap("amount_pad_toggle"); waitFor("number_pad")
        node("amount").assertTextContains("120+45", substring = true)
    }

    @Test
    fun theSevenMostUsedCategoriesAreTheTilesInOrderAndAllHoldsTheRestAndNewOnes() {
        // Transport is used most, then Bills; the rest keep the person's order
        repeat(3) { spend("t$it", 10, "Transport", "Cab $it") }; repeat(2) { spend("b$it", 20, "Bills", "Power $it") }
        launch(); tap("add"); waitFor("number_pad")
        val order = listOf("Transport", "Bills", "Food", "Groceries", "Shopping", "Health", "Fun")
        order.forEach { waitFor("category_chip_$it") }
        // the grid is read row by row (four to a row, or three from 130% text): the top first, then the left
        fun place(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot.let { it.top.toInt() / 20 * 100_000 + it.left.toInt() }
        assertTrue(order.zipWithNext().all { (a, b) -> place("category_chip_$a") < place("category_chip_$b") }, "most used first, then the arranged order, row by row")
        assertFalse(has("category_chip_Other"), "the eighth category is behind All")
        tap("more_categories"); waitFor("category_option_Other"); tap("category_option_Other")
        waitGone("category_option_Other"); waitFor("category_chip_Other"); node("category_chip_Other").assertIsSelected() // the chosen one is never hidden
        tap("more_categories"); waitFor("new_category_chip"); tap("new_category_chip"); waitFor("cat_name")
    }

    @Test
    fun moreHoldsSplitAnotherCurrencyFriendsExpectBackTagsReceiptsAndTheQuickEntry() {
        launch(); tap("add"); waitFor("number_pad")
        assertFalse(has("expect_toggle") || has("tag_input") || has("attachment_add") || has("foreign_toggle") || has("split_toggle") || has("split_start"), "none of it is composed until More is opened")
        compose.openFormMore()
        listOf("split_start", "foreign_toggle", "split_toggle", "expect_toggle", "tag_input", "attachment_add", "save_template").forEach { waitFor(it) }
        tap("form_more"); waitGone("form_more_panel")
    }

    @Test
    fun anExpenseSharedWithFriendsAndAnIncomeAndATransferStillSave() {
        launch(); tap("add"); waitFor("number_pad")
        pad("2", "5", "0", "0"); tap("kind_INCOME"); waitFor("category_chip_income:salary")
        compose.onNodeWithTag("save").assertTextContains("Add ₹2,500.00", substring = true); tap("save"); waitGone("save")
        tap("add"); waitFor("number_pad"); pad("3", "0", "0"); tap("kind_TRANSFER"); waitFor("to_chip_Bank")
        compose.onNodeWithTag("save").assertTextContains("Move ₹300.00", substring = true)
    }

    @Test
    fun anEarlierPatternIsOfferedAsUsuallyFoodForChaiAndOneTapPutsItOn() {
        repeat(3) { spend("c$it", 20, "Fun", "Chai") }
        launch(); tap("add"); waitFor("number_pad")
        node("note").performTextInput("Chai")
        waitFor("usual_category"); compose.onNodeWithTag("usual_category").assertTextContains("Usually Fun for Chai", substring = true)
        tap("usual_category"); waitFor("category_chip_Fun"); node("category_chip_Fun").assertIsSelected()
        waitGone("usual_category") // it is on now: nothing to offer
    }

    @Test
    fun anEntryBeingEditedOpensWithItsValuesNoPadAndSave() {
        spend("e1", 100, "Food", "Cafe")
        launch(); compose.openEntryEditor("row_Cafe")
        node("amount").assertTextContains("100.00", substring = true)
        assertFalse(has("number_pad"), "an existing amount opens without the pad")
        compose.onNodeWithTag("save").assertTextContains("Save", substring = true)
        node("amount").performTextClearance(); node("amount").performTextInput("1+2")
        waitFor("amount_result"); node("save").assertIsEnabled(); tap("save"); waitGone("save")
        assertEquals(300L, rows().single().amount_minor)
    }

    @Test
    fun hideAmountsHidesTheWorkedOutResultTheWordsAndTheAmountOnTheSaveButton() {
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        launch(); tap("add"); waitFor("number_pad")
        pad("4", "5", "0", "plus", "8", "0", "0", "0", "0")
        tap("category_chip_Food")
        compose.waitForIdle()
        assertFalse(has("amount_result"), "the worked-out result (an amount) is not shown while amounts are hidden")
        assertFalse(has("amount_words"), "nor the amount in words")
        val shown = compose.onNodeWithTag("save").fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertFalse(shown.any { it.isDigit() }, "no digit of the amount is on the Save button: '$shown'")
        node("save").assertIsEnabled()
    }
}
