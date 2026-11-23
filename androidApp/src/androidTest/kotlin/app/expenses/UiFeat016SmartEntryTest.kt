package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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

/** N-01 (feat-016): the smart line on the Add form. Its preview fills the form when tapped and never saves; the amount-first pad is unchanged. Invented data only. */
@RunWith(AndroidJUnit4::class)
class UiFeat016SmartEntryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1"); it.templates.putSetting("category_look_notice", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 15_000) = compose.waitUntil(ms) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun rows() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!
    private fun textsOf(tag: String): String = compose.onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode().let { n ->
        fun walk(x: androidx.compose.ui.semantics.SemanticsNode): List<String> = (x.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + x.children.flatMap { walk(it) }
        walk(n).joinToString(" ")
    }
    private fun type(text: String) { waitFor("smart_entry"); node("smart_entry").performScrollTo(); node("smart_entry").performTextReplacement(text) }
    private fun spend(id: String, category: String, note: String) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis() - 86_400_000L, AccountId("cash"), Money(3_000, inr), listOf(CategorySplit(CategoryId(category), Money(3_000, inr)))), EntryDetails(merchant = note)) }
    }

    @Test
    fun aTypedLineShowsAPreviewThatFillsTheFormWhenTappedAndNothingIsSavedUntilSave() {
        launch(); tap("add"); waitFor("number_pad")
        type("chai 20")
        waitFor("smart_preview")
        val chip = textsOf("smart_preview")
        assertTrue(chip.contains("₹20.00 · Chai · Food · Today"), "the preview says what it will fill: $chip")
        assertTrue(rows().isEmpty(), "typing never saves")
        tap("smart_preview")
        waitGone("smart_preview")
        node("amount").assertTextContains("20", substring = true)
        node("note").assertTextContains("Chai", substring = true)
        node("category_chip_Food").assertIsSelected()
        assertTrue(rows().isEmpty(), "filling the form never saves either")
        compose.onNodeWithTag("save").assertTextContains("Add ₹20.00 · Food", substring = true)
        tap("save"); waitGone("save")
        val saved = rows().single()
        assertEquals(2_000L, saved.amount_minor)
        assertEquals("Chai", saved.merchant)
    }

    @Test
    fun hinglishWithKalAndAPastWordFillsYesterday() {
        launch(); tap("add"); waitFor("number_pad")
        type("kal auto ke 80 diye")
        waitFor("smart_preview")
        assertTrue(textsOf("smart_preview").contains("₹80.00 · Auto · Transport · Yesterday"), textsOf("smart_preview"))
        tap("smart_preview")
        assertTrue(textsOf("date").contains("Yesterday"), "the date chip: " + textsOf("date"))
        node("category_chip_Transport").assertIsSelected()
    }

    @Test
    fun anAccountNamedInTheLineIsChosen() {
        launch(); tap("add"); waitFor("number_pad")
        type("rent 18,000 bank")
        waitFor("smart_preview")
        assertTrue(textsOf("smart_preview").contains("Bank"), textsOf("smart_preview"))
        tap("smart_preview")
        node("account_chip_Bank").assertIsSelected()
        compose.onNodeWithTag("save").assertTextContains("Add ₹18,000.00 · Rent", substring = true)
    }

    @Test
    fun whatThePersonUsuallyDoesBeatsTheBuiltInWord() {
        repeat(3) { spend("c$it", "Groceries", "Chai") }
        launch(); tap("add"); waitFor("number_pad")
        type("chai 20")
        compose.waitUntil(15_000) { has("smart_preview") && textsOf("smart_preview").contains("Groceries") }
    }

    @Test
    fun anAmbiguousLineSaysWhyAndOffersNothingToTap() {
        launch(); tap("add"); waitFor("number_pad")
        type("2 samosa 40")
        waitFor("smart_hint"); assertFalse(has("smart_preview"))
        type("chai 12,34")
        waitFor("smart_hint"); assertFalse(has("smart_preview"))
        type("kal chai 20")
        waitFor("smart_date_hint"); waitFor("smart_preview")
        assertTrue(textsOf("smart_preview").contains("Today"), "an unclear day leaves the form's date")
    }

    @Test
    fun hideAmountsMasksThePreview() {
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        launch(); tap("add"); waitFor("number_pad")
        type("chai 20")
        waitFor("smart_preview")
        val chip = textsOf("smart_preview")
        assertFalse(chip.contains("20.00"), "no figure while amounts are hidden: $chip")
        assertTrue(chip.contains("₹••••"), chip)
    }

    @Test
    fun theLineIsOnlyOnANewExpenseAndThePadFlowIsUnchanged() {
        launch(); tap("add"); waitFor("number_pad")
        assertTrue(has("amount") && has("number_pad"), "amount first, pad up")
        tap("pad_4"); tap("pad_5"); tap("pad_0")
        node("amount").assertTextContains("450", substring = true)
        waitFor("smart_entry")
        tap("kind_INCOME"); waitGone("smart_entry")
        tap("kind_EXPENSE"); waitFor("smart_entry")
        tap("category_chip_Food"); tap("save"); waitGone("save")
        assertEquals(45_000L, rows().single().amount_minor)
    }
}
