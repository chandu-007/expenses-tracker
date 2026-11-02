package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
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

/**
 * What the app learns from the person's choices, seen on the phone: a choice for one spelling of a payee is found under another, the other waiting cards follow at once, a guess is looked
 * at and left out of "Add all", and the list of what was learned can forget, and file earlier expenses with Undo. Invented payees and amounts only.
 */
@RunWith(AndroidJUnit4::class)
class LearnedChoicesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() { resetApp(); runBlocking { container.write { it.templates.putSetting("capture_acct:HDFC Bank:1234", "bank") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun upi(payee: String, ref: String) = "Rs.200.00 debited from A/c XX1234 on 05-10-26 to $payee (UPI Ref $ref). Not you? Call 18002586161"
    private fun card(shop: String) = "Spent Rs.450 On HDFC Bank Card 1234 At $shop On 2026-10-05:19:22:01.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 1234 to 7308080808"
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back") }
    private fun openRules() { compose.openMore(); tap("open_capture"); tap("open_rules"); waitFor("rule_pattern") }
    private fun idAt(at: Long) = runBlocking { container.write { ok -> ok.inbox.list().first { it.receivedAt == at }.id } }!!
    private fun goes(id: String): androidx.compose.ui.test.SemanticsNodeInteraction {
        compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_goes_$id"))
        return compose.onNodeWithTag("inbox_goes_$id", useUnmergedTree = true)
    }
    /** The list is lazy: a card below the screen is not there until it is scrolled to. */
    private fun tapOn(id: String, tag: String) { compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)); tap(tag) }
    private fun categoryOf(entryId: String) = runBlocking { container.write { ok -> ok.db.schemaQueries.splitsOf(entryId).executeAsList().singleOrNull()?.category_id } }

    @Test
    fun aChoiceForOneSpellingIsFoundUnderAnotherAndTheOtherWaitingCardFollowsAtOnce() {
        ingest(upi("freshpik@ybl", "100000000001"), 1_000)
        ingest(card("FRESHPIK BANGALORE IN"), 2_000)
        ingest(upi("zorbo@okaxis", "100000000003"), 3_000)
        launch(); openInbox(); waitFor("inbox_list")
        val first = idAt(1_000); val second = idAt(2_000); val third = idAt(3_000)
        goes(second).assertTextContains("no category", substring = true)
        tapOn(first, "inbox_change_$first"); tap("inbox_category_${first}_Groceries"); tap("inbox_add_$first")
        compose.waitUntil(10_000) { runBlocking { container.write { it.captureRules.learned() } } == mapOf("freshpik" to "Groceries") }
        compose.waitUntil(10_000) { runCatching { textOf("inbox_goes_$second") }.getOrDefault("").contains("Groceries (what you chose last time)") }
        assertTrue(textOf("inbox_goes_$third").contains("no category"), "another payee is not touched")
    }

    @Test
    fun aCardTheyHavePickedForStaysAsPickedWhenAnotherCardTeachesTheApp() {
        ingest(card("FRESHPIK BANGALORE IN"), 1_000)
        ingest(upi("freshpik@ybl", "100000000002"), 2_000)
        launch(); openInbox(); waitFor("inbox_list")
        val first = idAt(1_000); val second = idAt(2_000)
        tapOn(second, "inbox_change_$second"); tap("inbox_category_${second}_Fun")
        tapOn(first, "inbox_change_$first"); tap("inbox_category_${first}_Groceries"); tap("inbox_add_$first")
        compose.waitUntil(10_000) { runBlocking { container.write { it.captureRules.learned() } } == mapOf("freshpik" to "Groceries") }
        compose.waitUntil(10_000) { runBlocking { container.write { it.inbox.list().size } } == 1 }
        val goes = textOf("inbox_goes_$second")
        assertTrue(goes.contains("Fun") && !goes.contains("Groceries"), "what they picked on this card stays: $goes")
    }

    @Test
    fun aGuessFromANameAndAPaymentToAPersonAreLookedAtAndLeftOutOfAddAll() {
        ingest(upi("VPA swiggy@icici", "100000000011"), 1_000); ingest(upi("VPA randomshop@okaxis", "100000000012"), 2_000)
        ingest(upi("SAI BABA TEA STALL", "100000000013"), 3_000); ingest(upi("VPA 9704849321@yescred", "100000000014"), 4_000)
        launch(); openInbox(); waitFor("inbox_list")
        val swiggy = idAt(1_000); val shop = idAt(2_000); val tea = idAt(3_000); val person = idAt(4_000)
        goes(tea).assertTextContains("a guess from the name", substring = true)
        goes(person).assertTextContains("a payment to a person", substring = true)
        compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_check_$tea"))
        assertTrue(has("inbox_check_$tea"), "a guess says so")
        compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_check_$person"))
        assertTrue(has("inbox_check_$person"))
        assertTrue(!has("inbox_check_$swiggy") && !has("inbox_check_$shop"), "a sure suggestion or none is not marked")
        compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_count"))
        tap("inbox_add_all"); waitFor("inbox_add_all_ok")
        assertTrue(textOf("inbox_add_all_text").contains("1 of these have no category yet"), textOf("inbox_add_all_text"))
        tap("inbox_add_all_ok")
        compose.waitUntil(10_000) { runBlocking { container.write { it.inbox.list().map { i -> i.id }.toSet() } } == setOf(tea, person) }
        assertEquals(2, runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(50).executeAsList().size } })
    }

    @Test
    fun theListOfWhatWasLearnedShowsItAndForgetRemovesIt() {
        ingest(card("FRESHPIK BANGALORE IN"), 2_000)
        launch(); openInbox(); waitFor("inbox_list")
        val id = idAt(2_000)
        tap("inbox_change_$id"); tap("inbox_category_${id}_Groceries"); tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        tap("inbox_back"); openRules()
        waitFor("learned_freshpik")
        assertTrue(textOf("learned_what_freshpik").contains("Groceries") && textOf("learned_what_freshpik").contains("chosen once"), textOf("learned_what_freshpik"))
        tap("learned_forget_freshpik")
        compose.waitUntil(10_000) { !has("learned_freshpik") && has("learned_empty") }
        assertEquals(emptyMap(), runBlocking { container.write { it.captureRules.learned() } })
    }

    @Test
    fun earlierExpensesWithNoCategoryAreFiledUnderWhatWasLearnedAndUndoPutsThemBack() {
        runBlocking {
            container.write { ok ->
                for ((i, name) in listOf("FRESHPIK BANGALORE IN", "freshpik@ybl", "Freshpik", "Other shop").withIndex())
                    ok.store.save(Expense(EntryId("u$i"), 1_000L + i, AccountId("bank"), Money(100, Currency.INR)), EntryDetails(merchant = name))
                ok.captureRules.learn("Freshpik", "Groceries")
            }
        }
        launch(); openRules()
        waitFor("learned_file_freshpik")
        compose.onNodeWithTag("learned_file_freshpik").assertTextContains("File 3 earlier expenses", substring = true)
        tap("learned_file_freshpik")
        compose.waitUntil(10_000) { categoryOf("u0") == "Groceries" && categoryOf("u1") == "Groceries" && categoryOf("u2") == "Groceries" }
        assertEquals(null, categoryOf("u3"), "another payee is not touched")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) { categoryOf("u0") == null && categoryOf("u1") == null && categoryOf("u2") == null }
        assertEquals(mapOf("freshpik" to "Groceries"), runBlocking { container.write { it.captureRules.learned() } }, "undoing the filing does not unlearn or relearn anything")
    }
}
