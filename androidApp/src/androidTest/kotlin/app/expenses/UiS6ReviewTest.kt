package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UI S6, U-11: To review as a ritual that ends. The card's primary pair and its Change, the progress that counts down, the closing state that only appears at zero (never while texts wait),
 * the "not reviewed" dot on Home and Search rows, and the capture heartbeat chip.
 */
@RunWith(AndroidJUnit4::class)
class UiS6ReviewTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) { try { compose.waitUntil(10_000) { has(tag) } } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        val tags = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.TestTag), useUnmergedTree = true).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        throw AssertionError("no node tagged $tag; the screen has: $tags", e)
    } }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun stateOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.StateDescription)) c[SemanticsProperties.StateDescription] else "" } }
    private fun descriptionOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.ContentDescription)) c[SemanticsProperties.ContentDescription].joinToString() else "" } }
    // the list is lazy: a card below the screen is not there until it is scrolled to
    private fun reveal(tag: String) { waitFor("inbox_list"); compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) }
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back") }
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun put(key: String, value: String) { runBlocking { container.write { it.templates.putSetting(key, value) } } }

    // ---- To review ----
    private fun to(vpa: String, ref: String) = "Rs.200.00 debited from A/c XX1234 on 05-10-26 to VPA $vpa (UPI Ref $ref). Not you? Call 18002586161"
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }
    private fun idAt(at: Long) = runBlocking { container.write { ok -> ok.inbox.list().first { it.receivedAt == at }.id } }!!
    private fun three(): Triple<String, String, String> {
        ingest(to("randomshop@okaxis", "100000000001"), 1_000); ingest(to("othershop@okaxis", "100000000002"), 2_000); ingest(to("thirdshop@okaxis", "100000000003"), 3_000)
        return Triple(idAt(1_000), idAt(2_000), idAt(3_000))
    }

    @Test
    fun aCardLeadsWithAddAndSkipAndKeepsTheTypeAndTheOtherChoicesUnderChange() {
        ingest(to("randomshop@okaxis", "100000000001"), 1_000); launch(); openInbox()
        val id = idAt(1_000)
        waitFor("inbox_add_$id"); waitFor("inbox_skip_$id"); waitFor("inbox_change_$id")
        assertTrue(!has("inbox_kinds_$id") && !has("inbox_flip_$id") && !has("inbox_text_toggle_$id"), "Expense / Income / Transfer, the other direction and the message are not on the card until Change is opened")
        tap("inbox_change_$id")
        waitFor("inbox_kinds_$id"); waitFor("inbox_kind_${id}_EXPENSE"); waitFor("inbox_kind_${id}_INCOME"); waitFor("inbox_kind_${id}_TRANSFER"); waitFor("inbox_flip_$id"); waitFor("inbox_text_toggle_$id")
        tap("inbox_change_$id")
        compose.waitUntil(10_000) { !has("inbox_kinds_$id") }
        assertTrue(has("inbox_add_$id") && has("inbox_skip_$id"), "Add and Skip stay")
    }

    @Test
    fun theProgressCountsDownAsCardsAreHandledAndTheClosingStateAppearsOnlyAtZero() {
        val (a, b, c) = three(); launch(); openInbox()
        assertTrue(!has("review_all_cleared"), "nothing is cleared while texts wait (or before the list has loaded)")
        waitFor("inbox_count"); waitFor("review_togo")
        assertEquals("3 to go", textOf("review_togo")); assertEquals("0 of 3 reviewed, 3 to go", stateOf("review_progress"))
        assertTrue(!has("review_all_cleared"))
        reveal("inbox_add_$a"); tap("inbox_add_$a")
        compose.waitUntil(10_000) { has("review_togo") && textOf("review_togo") == "2 to go" }
        assertEquals("1 of 3 reviewed, 2 to go", stateOf("review_progress")); assertTrue(!has("review_all_cleared"))
        reveal("inbox_add_$b"); tap("inbox_add_$b")
        compose.waitUntil(10_000) { has("review_togo") && textOf("review_togo") == "1 to go" }
        assertEquals("2 of 3 reviewed, 1 to go", stateOf("review_progress")); assertTrue(!has("review_all_cleared"))
        reveal("inbox_skip_$c"); tap("inbox_skip_$c")
        waitFor("review_all_cleared")
        assertEquals("All cleared. The next text will land here.", textOf("review_all_cleared"))
        assertTrue(!has("review_togo") && !has("review_progress") && has("inbox_empty"), "the count and the bar are gone with the last card")
    }

    @Test
    fun theClosingStateIsNeverShownWhileATextIsPending() {
        ingest(to("randomshop@okaxis", "100000000001"), 1_000); launch(); openInbox()
        assertTrue(!has("review_all_cleared"), "not before the list has loaded")
        waitFor("inbox_count"); assertTrue(!has("review_all_cleared"), "not once it has: one text waits")
        assertEquals("1 to go", textOf("review_togo"))
        compose.waitForIdle(); assertTrue(!has("review_all_cleared"))
    }

    @Test
    fun anEmptyListThatHasLoadedIsTheClosingState() {
        launch(); openInbox()
        waitFor("review_all_cleared"); assertEquals("All cleared. The next text will land here.", textOf("review_all_cleared")); assertTrue(has("inbox_empty") && has("inbox_empty_checkup"))
    }

    // ---- the dot ----
    private fun save(id: String, at: Long, merchant: String, source: String) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), at, AccountId("bank"), Money(10_000 + at, Currency.INR)), EntryDetails(merchant = merchant, source = source)) }
    }
    private fun categoryOf(id: String) = runBlocking { container.write { ok -> ok.db.schemaQueries.splitsOf(id).executeAsList().singleOrNull()?.category_id } }

    @Test
    fun theDotMarksACapturedExpenseWithNoCategoryOnHomeAndGoesWhenItIsReviewed() {
        save("m", 1_000, "Typed by hand", "manual"); save("u0", 1_001, "Alpha Stores", "sms"); save("u1", 1_002, "Beta Stores", "sms"); save("u2", 1_003, "Gamma Stores", "sms")
        launch()
        waitFor("row_unreviewed_u0"); waitFor("row_unreviewed_u1"); waitFor("row_unreviewed_u2")
        assertTrue(!has("row_unreviewed_m"), "an entry the person typed is not marked")
        assertEquals("not reviewed", descriptionOf("row_unreviewed_u0"), "the dot says what it is in words")
        tap("categorise_nudge_review"); waitFor("categorise_screen"); waitFor("categorise_left")
        assertEquals("4 to go", textOf("categorise_left")); assertEquals("0 of 4 reviewed, 4 to go", stateOf("review_progress"))
        tap("categorise_chip_Groceries")
        compose.waitUntil(10_000) { listOf("m", "u0", "u1", "u2").any { categoryOf(it) != null } }
        val filed = listOf("m", "u0", "u1", "u2").first { categoryOf(it) != null }
        assertTrue(filed != "m", "the newest captured entry comes first")
        compose.waitUntil(10_000) { has("categorise_left") && textOf("categorise_left") == "3 to go" }
        assertEquals("1 of 4 reviewed, 3 to go", stateOf("review_progress"))
        tap("categorise_back")
        compose.waitUntil(10_000) { has("home_top") && !has("row_unreviewed_$filed") }
        listOf("u0", "u1", "u2").filter { it != filed }.forEach { assertTrue(has("row_unreviewed_$it"), "$it is still not reviewed") }
    }

    @Test
    fun theDotShowsOnSearchResultsToo() {
        save("m", 1_000, "Zorbo typed", "manual"); save("u1", 1_001, "Zorbo captured", "sms")
        launch(); tap("open_search"); waitFor("search_text")
        compose.onNodeWithTag("search_text").performTextInput("Zorbo")
        waitFor("result_m"); waitFor("result_u1")
        waitFor("row_unreviewed_u1"); assertTrue(!has("row_unreviewed_m"))
        assertEquals("not reviewed", descriptionOf("row_unreviewed_u1"))
    }

    // ---- the heartbeat ----
    private fun yesterdayEvening(): Long = java.time.LocalDate.now().minusDays(1).atTime(20, 14).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun theHeartbeatSaysWhenTheLastBankTextWasSeenAndOneTapOpensTheCheckup() {
        put("capture_sms_on", "1"); put("capture_last_seen_at", yesterdayEvening().toString())
        launch(); waitFor("capture_heartbeat")
        assertEquals("Last bank text seen yesterday 8:14 pm", textOf("capture_heartbeat_text"))
        assertEquals("Seen recently", stateOf("capture_heartbeat")); assertTrue(!has("capture_heartbeat_note"), "a recent text needs no second line")
        tap("capture_heartbeat"); waitFor("capture_checkup_headline")
    }

    @Test
    fun afterAQuietStretchTheHeartbeatSaysSoInWordsAndStillOpensTheCheckup() {
        put("capture_sms_on", "1"); put("capture_last_seen_at", java.time.LocalDate.now().minusDays(8).atTime(8, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli().toString())
        launch(); waitFor("capture_heartbeat")
        assertTrue(stateOf("capture_heartbeat").startsWith("Quiet: no bank text for "), stateOf("capture_heartbeat"))
        waitFor("capture_heartbeat_note"); assertTrue(textOf("capture_heartbeat_note").startsWith("No bank text for "), textOf("capture_heartbeat_note"))
        tap("capture_heartbeat"); waitFor("capture_checkup_headline")
    }

    @Test
    fun thereIsNoHeartbeatWhenCaptureIsOffOrNothingWasEverSeen() {
        put("capture_last_seen_at", yesterdayEvening().toString())
        launch(); waitFor("home_top"); compose.waitForIdle()
        assertTrue(!has("capture_heartbeat"), "capture is off")
        put("capture_sms_on", "1"); put("capture_last_seen_at", ""); // nothing readable stored
        launch(); waitFor("home_top"); compose.waitForIdle()
        assertTrue(!has("capture_heartbeat"), "nothing was ever seen")
    }

    @Test
    fun aBankTextWritesTheTimestampAndATextFromAPersonDoesNot() {
        runBlocking { Capture.ingest(container, Capture.SMS, "+919812345678", "dinner at 8?", System.currentTimeMillis()) }
        assertNull(setting("capture_last_seen_at"), "a text from a person is not a bank text")
        val before = System.currentTimeMillis()
        ingest(to("randomshop@okaxis", "100000000001"), 1_000)
        val seen = assertNotNull(setting("capture_last_seen_at")?.toLongOrNull(), "a bank text leaves its time")
        assertTrue(seen >= before, "the time is when it arrived")
        put("capture_sms_on", "1"); launch(); waitFor("capture_heartbeat")
        assertTrue(textOf("capture_heartbeat_text").startsWith("Last bank text seen today"), textOf("capture_heartbeat_text"))
    }
}
