package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
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

/** Lending and borrowing through the real form and pages: who owes what, part payments, settling up, and that none of it counts as spending. */
@RunWith(AndroidJUnit4::class)
class LoansUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    // the three summary boxes hold their text in children, so they are read merged
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = tag !in setOf("loans_owed", "loans_owe", "loan_balance"))
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { runCatching { node(tag).assertTextContains(text, substring = true) }.isSuccess }
    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun openLoans() { tap("more_menu"); tap("open_loans"); waitFor("loans_list") }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!

    @Test
    fun lendingToANewPersonThenPartAndFullRepaymentKeepsTheBalanceRightAndIsNeverSpending() {
        launch()
        // lend 5,000 to a new person, through the add form
        tap("add"); tap("kind_LOAN"); type("amount", "5000")
        tap("new_person_chip"); type("new_person_name", "Chandu"); tap("new_person_ok")
        waitFor("person_chip_Chandu"); tap("account_chip_Bank"); tap("save")
        waitFor("add")
        openLoans()
        waitFor("loan_row_Chandu")
        node("loan_state_Chandu").assertTextEquals("owes you"); node("loan_amount_Chandu").assertTextEquals("₹5,000.00")
        node("loans_owed").assertTextContains("₹5,000.00", substring = true)
        // he pays 1,500 back: part payment
        tap("loan_row_Chandu"); waitFor("loan_balance")
        tap("loan_got_btn"); type("amount", "1500"); tap("save")
        waitText("loan_balance", "₹3,500.00")
        // settle up: the button fills in the rest, the other way round
        tap("loan_settle"); waitFor("save"); node("amount").assertTextContains("3500", substring = true); tap("save")
        waitText("loan_balance", "No balance")
        // the entries, as the ledger holds them: two ways of money moving, no category, titled by the person
        val e = entries()
        assertEquals(listOf("SETTLEMENT", "SETTLEMENT", "SETTLEMENT"), e.map { it.kind })
        assertEquals(listOf("PAID_TO_PERSON", "RECEIVED_FROM_PERSON", "RECEIVED_FROM_PERSON"), e.map { it.direction })
        assertEquals(listOf(500_000L, 150_000L, 350_000L), e.map { it.amount_minor }); assertEquals(setOf("Chandu"), e.map { it.merchant }.toSet())
        // back at home: spending is untouched, and each row has its own sign
        tap("loans_back"); tap("loans_back"); waitFor("add")
        tap("nav_home"); waitFor("month_spend")
        node("month_spend").assertTextEquals("₹0.00")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("amount_Chandu"), useUnmergedTree = true).fetchSemanticsNodes().size == 3 }
        val signs = compose.onAllNodes(hasTestTag("amount_Chandu"), useUnmergedTree = true).fetchSemanticsNodes().map { n -> n.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(listOf("+₹3,500.00", "+₹1,500.00", "-₹5,000.00"), signs, "newest first: money got is green and plus, money given is red and minus")
    }

    @Test
    fun borrowingShowsWhatIsOwedTheOtherWayAndAnUnusedPersonCanBeRemoved() {
        launch()
        tap("add"); tap("kind_LOAN"); tap("loan_got"); type("amount", "800"); tap("new_person_chip"); type("new_person_name", "Asha"); tap("new_person_ok")
        waitFor("person_chip_Asha"); tap("account_chip_Cash"); tap("save"); waitFor("add")
        openLoans()
        node("loan_state_Asha").assertTextEquals("you owe"); node("loans_owe").assertTextContains("₹800.00", substring = true)
        // a person made in the form but never used can be taken off the list
        tap("loan_give"); tap("new_person_chip"); type("new_person_name", "Typo Name"); tap("new_person_ok"); waitFor("person_chip_Typo Name"); tap("cancel")
        waitFor("loans_list"); waitFor("loan_row_Typo Name")
        tap("loan_row_Typo Name"); tap("loan_remove")
        compose.waitUntil(10_000) { !has("loan_row_Typo Name") && has("loan_row_Asha") }
    }

    @Test
    fun aLentEntryCanBeEditedFromThePersonsHistory() {
        launch()
        tap("add"); tap("kind_LOAN"); type("amount", "1000"); tap("new_person_chip"); type("new_person_name", "Ravi"); tap("new_person_ok")
        waitFor("person_chip_Ravi"); tap("account_chip_Bank"); tap("save"); waitFor("add")
        openLoans(); tap("loan_row_Ravi"); waitFor("loan_balance")
        val id = entries().single().id
        tap("loan_history_$id"); type("amount", "1200"); tap("save")
        waitText("loan_balance", "₹1,200.00")
        assertEquals(listOf(120_000L), entries().map { it.amount_minor }, "edited in place, not added")
    }
}
