package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
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
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.CategoryId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A new expense shared equally with friends: one entry, your share is the spending, and each friend owes theirs on the Lent and borrowed page. */
@RunWith(AndroidJUnit4::class)
class SplitBillUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.waitForIdle(); runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo() }; compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }

    @Test
    fun aBillSharedWithTwoFriendsIsOneEntryWhoseSpendingIsYourShareAndEachFriendOwesTheirs() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        tap("add"); waitFor("amount")
        type("amount", "900"); tap("category_chip_Food")
        tap("split_toggle")
        for (name in listOf("Asha", "Ravi")) { tap("split_new_person"); type("new_person_name", name); tap("new_person_ok"); waitFor("split_person_$name") }
        compose.waitUntil(10_000) { has("split_summary") && textOf("split_summary").startsWith("You pay ₹300.00 of it; each of the 2 owes ₹300.00") }
        tap("save"); waitFor("add")
        runBlocking { container.write { ok ->
            val entries = ok.db.schemaQueries.recentEntries(10).executeAsList()
            assertEquals(1, entries.size, "one entry, not one per friend")
            assertEquals(90_000L, entries.single().amount_minor, "the whole bill")
            assertEquals(3, ok.db.schemaQueries.sharesOf(entries.single().id).executeAsList().size, "you and two friends")
            assertEquals(30_000L, ok.store.spendByCategory(Currency.INR)[CategoryId("Food")]?.minorUnits, "spending counts only your share")
        } }
        compose.openMore(); tap("open_loans"); waitFor("loans_list")
        waitFor("loan_row_Asha"); waitFor("loan_row_Ravi")
        assertEquals("owes you", textOf("loan_state_Asha"))
        compose.onNodeWithTag("loans_owed").assertTextContains("600.00", substring = true) // the box keeps its text in children: read merged
        tap("loan_row_Asha"); compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Their share of", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun newPerson(name: String, tag: String) { tap(tag); type("new_person_name", name); tap("new_person_ok") }

    @Test
    fun aBillSplitByTypedAmountsLeavesYouTheRestAndAFriendWhoPaidIsOwedYourShareWithNoAccountMoving() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        tap("add"); waitFor("amount"); type("amount", "1000"); tap("category_chip_Food"); tap("split_toggle"); tap("split_mode_1")
        newPerson("Asha", "split_new_person"); waitFor("split_amount_Asha"); type("split_amount_Asha", "250")
        newPerson("Ravi", "split_new_person"); waitFor("split_amount_Ravi"); type("split_amount_Ravi", "400")
        compose.waitUntil(10_000) { textOf("split_summary").startsWith("You pay ₹350.00 of it") }
        tap("save"); waitFor("add")
        runBlocking { container.write { ok ->
            val e = ok.db.schemaQueries.recentEntries(5).executeAsList().single()
            assertEquals(100_000L, e.amount_minor)
            assertEquals(listOf(35_000L, 25_000L, 40_000L), ok.db.schemaQueries.sharesOf(e.id).executeAsList().map { it.amount_minor })
        } }
        // a different bill that Asha paid: the amount is your share, she is owed it, and no account of yours moves
        tap("add"); waitFor("amount"); type("amount", "300"); tap("category_chip_Food"); tap("split_toggle"); tap("split_mode_2"); tap("split_payer_Asha")
        compose.waitUntil(10_000) { textOf("split_summary").startsWith("You owe Asha") }
        tap("save"); waitFor("add")
        runBlocking { container.write { ok ->
            assertEquals(-100_000L, ok.store.balances().values.sumOf { it.minorUnits }, "only the first bill moved money out of your accounts; the one Asha paid moved none")
            assertEquals(2, ok.db.schemaQueries.recentEntries(5).executeAsList().size)
        } }
        compose.openMore(); tap("open_loans"); waitFor("loan_row_Asha")
        // 250 owed to you less 300 owed to her: net she is owed 50
        compose.waitUntil(10_000) { textOf("loan_state_Asha") == "you owe" }
        compose.onNodeWithTag("loans_owe").assertTextContains("50.00", substring = true)
    }
}
