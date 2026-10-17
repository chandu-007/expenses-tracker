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
import kotlin.test.assertTrue

/** After a category is chosen for an entry, earlier entries that read alike are offered the same category; the person decides. Captured texts start from history. */
@RunWith(AndroidJUnit4::class)
class SimilarUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 10_000) = compose.waitUntil(ms) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun textOf(tag: String): String { waitFor(tag); return node(tag).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(" ") { it.text } else "" } }

    private fun past(id: String, merchant: String, category: String, daysAgo: Long, minor: Long = 89_900) = runBlocking {
        container.write { ok ->
            val m = Money(minor, Currency.INR)
            ok.store.save(Expense(EntryId(id), System.currentTimeMillis() - daysAgo * 86_400_000L, AccountId("bank"), m, listOf(CategorySplit(CategoryId(category), m))), EntryDetails(merchant = merchant))
        }
    }
    private fun inCategory(id: String): String = runBlocking {
        container.write { ok -> ok.db.schemaQueries.similarCandidates("EXPENSE", "\"icloud\"").executeAsList().firstOrNull { it.id == id }?.category_id.orEmpty() }
    } ?: ""

    /** Files a new expense through the real form. */
    private fun fileNew(note: String, category: String) {
        tap("add"); waitFor("amount")
        node("amount").performTextInput("899"); node("note").performTextInput(note)
        tap("category_chip_$category"); tap("save"); waitFor("add")
    }

    private fun threeEarlierICloudsInOther() { past("i1", "iCloud", "Other", 90); past("i2", "iCloud+ 50GB", "Other", 60); past("i3", "Apple iCloud", "Other", 30); past("n1", "Netflix", "Other", 20) }

    @Test
    fun filingOneAsBillsOffersTheEarlierOnesAndMoveAllChangesOnlyThoseAndNothingElse() {
        threeEarlierICloudsInOther(); launch()
        fileNew("iCloud", "Bills")
        waitFor("similar_dialog", 15_000)
        assertTrue(textOf("similar_text").contains("3 earlier entries"), textOf("similar_text"))
        assertTrue(textOf("similar_text").contains("Bills"), textOf("similar_text"))
        assertEquals("Other", inCategory("i1"), "nothing moves until the person agrees")
        tap("similar_all")
        compose.waitUntil(10_000) { inCategory("i1") == "Bills" && inCategory("i2") == "Bills" && inCategory("i3") == "Bills" }
        assertEquals("Other", runBlocking { container.write { ok -> ok.db.schemaQueries.similarCandidates("EXPENSE", "\"netflix\"").executeAsList().first().category_id } }, "Netflix is another payee and stays")
    }

    @Test
    fun reviewingOneByOneMovesOnlyTheOnesAcceptedNewestFirst() {
        threeEarlierICloudsInOther(); launch()
        fileNew("iCloud", "Bills")
        waitFor("similar_review", 15_000); tap("similar_review")
        waitFor("similar_progress"); assertEquals("1 of 3", textOf("similar_progress"))
        assertEquals("Apple iCloud", textOf("similar_row_title"), "the newest first")
        tap("similar_yes")
        compose.waitUntil(10_000) { textOf("similar_progress") == "2 of 3" }
        tap("similar_skip")
        compose.waitUntil(10_000) { textOf("similar_progress") == "3 of 3" }
        tap("similar_yes")
        compose.waitUntil(10_000) { !has("similar_dialog") }
        compose.waitUntil(10_000) { inCategory("i3") == "Bills" && inCategory("i1") == "Bills" }
        assertEquals("Other", inCategory("i2"), "the one skipped stays where it was")
    }

    @Test
    fun notNowChangesNothingAndDoesNotAskAgainForTheSameNameThisSession() {
        threeEarlierICloudsInOther(); launch()
        fileNew("iCloud", "Bills")
        waitFor("similar_not_now", 15_000); tap("similar_not_now")
        compose.waitUntil(10_000) { !has("similar_dialog") }
        assertEquals("Other", inCategory("i1"))
        fileNew("iCloud", "Bills")
        compose.waitUntil(10_000) { has("add") }
        Thread.sleep(2_500); compose.waitForIdle()
        assertTrue(!has("similar_dialog"), "asked once is enough")
    }

    @Test
    fun noOfferWhenEveryEarlierOneIsAlreadyThere() {
        past("i1", "iCloud", "Bills", 90); past("i2", "iCloud", "Bills", 60); launch()
        fileNew("iCloud", "Bills")
        Thread.sleep(2_500); compose.waitForIdle()
        assertTrue(!has("similar_dialog"), "nothing to offer")
    }

    @Test
    fun aCapturedTextStartsFromTheCategoryOfTheEarlierEntriesOfThatPayee() {
        past("nf1", "Netflix", "Bills", 40); past("nf2", "Netflix", "Bills", 10)
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Rs.649.00 debited from A/c XX1234 on 05-10-26 to VPA netflix@okaxis (UPI Ref 444444444444). Not you? Call 18002586161", 5_000) }
        launch(); tap("more_menu"); tap("open_inbox"); waitFor("inbox_back")
        val id = runBlocking { container.write { it.inbox.list().first().id } }!!
        waitFor("inbox_goes_$id")
        assertTrue(textOf("inbox_goes_$id").contains("Bills (your earlier entries)"), textOf("inbox_goes_$id"))
    }
}
