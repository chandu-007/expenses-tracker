package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.StarterCategories
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

/**
 * "Same shop as…" and amount-aware suggestions, on the phone: two names joined as one shop from the learned list and from a To review card (each with Undo), removed with Undo, the learned list
 * saying which name counts as another, and a To review card saying why a suggestion rests on the amount, with its counts ("once something else" included), also at twice the font size.
 * Invented shops and amounts only.
 */
@RunWith(AndroidJUnit4::class)
class SameShopUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun shell(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() { shell("settings put system font_scale 1.0"); resetApp(); runBlocking { container.write { it.templates.putSetting("capture_acct:HDFC Bank:1234", "bank") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); shell("settings put system font_scale 1.0"); resetApp() }

    private fun card(rupees: Long, shop: String) = "Spent Rs.$rupees On HDFC Bank Card 1234 At $shop On 2026-10-05:19:22:01.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 1234 to 7308080808"
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    /** A tap that closes something can be lost on a loaded emulator: press again until [closes] is gone. */
    private fun tapUntilGone(tag: String, closes: String) { repeat(4) { if (has(closes)) { runCatching { tap(tag) }; compose.waitForIdle(); Thread.sleep(400) } }; gone(closes) }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text } else "" } }
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back") }
    private fun openRules() { compose.openMore(); tap("open_capture"); tap("open_rules"); waitFor("rule_pattern") }
    private fun idAt(at: Long) = runBlocking { container.write { ok -> ok.inbox.list().first { it.receivedAt == at }.id } }!!
    private fun scrollRules(tag: String) { compose.onNodeWithTag("rules_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) }
    private fun scrollInbox(tag: String) { compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) }
    private fun waitUndo() = FailureShot.around("SameShop_waitUndo") { compose.waitUntil(15_000) { compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty() } }
    private fun undo() { waitUndo(); repeat(3) { if (compose.onAllNodes(hasText("Undo")).fetchSemanticsNodes().isNotEmpty()) runCatching { compose.onNodeWithText("Undo").performClick() }; compose.waitForIdle() } }
    private fun aliases(): List<Pair<String, String>> = runBlocking { container.write { it.captureRules.aliases().all.map { a -> a.from to a.to } } } ?: emptyList()

    private fun spend(id: String, daysAgo: Long, rupees: Long, category: String, merchant: String) = runBlocking {
        container.write { ok ->
            val m = Money(rupees * 100, Currency.INR)
            ok.store.save(Expense(EntryId(id), System.currentTimeMillis() - daysAgo * 86_400_000L, AccountId("bank"), m, listOf(CategorySplit(CategoryId(category), m))), EntryDetails(merchant = merchant))
        }
    }
    private val subs = StarterCategories.expenseId("Subscriptions")

    /** Amazon, filed by amount: three subscriptions near 199, one purchase at 205, three purchases at 2,499. */
    private fun amazonHistory() {
        spend("h1", 90, 199, subs, "Amazon"); spend("h2", 60, 179, subs, "Amazon"); spend("h3", 30, 219, subs, "Amazon"); spend("h4", 20, 205, "Shopping", "Amazon")
        spend("h5", 80, 2_499, "Shopping", "Amazon"); spend("h6", 50, 2_499, "Shopping", "Amazon"); spend("h7", 10, 2_499, "Shopping", "Amazon")
    }

    @Test
    fun twoNamesAreJoinedFromTheLearnedListAndRemovedBothWithUndoAndNothingElseChanges() {
        runBlocking { container.write { it.captureRules.learn("Swiggy", "Food", "Swiggy"); it.captureRules.learn("Swiggy Instamart", "Groceries", "Swiggy Instamart") } }
        launch(); openRules(); scrollRules("learned_same_swiggyinstamart")
        assertTrue(has("alias_title") && has("alias_add"), "the block for joined names is there")
        tap("learned_same_swiggyinstamart"); waitFor("same_shop_to")
        assertTrue(has("same_shop_suggest_0"), "the other learned name is offered"); tap("same_shop_suggest_0")
        tapUntilGone("same_shop_ok", "same_shop_to")
        compose.waitUntil(15_000) { aliases() == listOf("swiggyinstamart" to "swiggy") }
        waitFor("alias_row_swiggyinstamart")
        assertEquals("Swiggy Instamart counts as Swiggy", textOf("alias_text_swiggyinstamart"))
        compose.waitUntil(15_000) { has("learned_sameas_swiggyinstamart") }
        assertTrue(textOf("learned_sameas_swiggyinstamart").startsWith("Counts as Swiggy"), "the learned line says so")
        // Undo takes it back
        undo(); compose.waitUntil(15_000) { aliases().isEmpty() }
        gone("alias_row_swiggyinstamart"); gone("learned_sameas_swiggyinstamart")
        // join again, then Remove, then Undo the removal
        scrollRules("learned_same_swiggyinstamart"); tap("learned_same_swiggyinstamart"); waitFor("same_shop_to"); tap("same_shop_suggest_0"); tapUntilGone("same_shop_ok", "same_shop_to")
        compose.waitUntil(15_000) { aliases().size == 1 }
        // the bar of the join stays and covers the foot of the list on a tall screen: a touch on Remove would land on its Undo, so press Remove by its action instead of by a touch
        scrollRules("alias_remove_swiggyinstamart"); compose.onNodeWithTag("alias_remove_swiggyinstamart", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitUntil(15_000) { aliases().isEmpty() }
        waitUndo(); assertTrue(compose.onAllNodes(hasText("Swiggy Instamart is its own shop again")).fetchSemanticsNodes().isNotEmpty(), "the bar says what happened")
        undo(); compose.waitUntil(15_000) { aliases() == listOf("swiggyinstamart" to "swiggy") }
        // what was learned for each name and the entries were never touched
        assertEquals(mapOf("swiggy" to "Food", "swiggyinstamart" to "Groceries"), runBlocking { container.write { it.captureRules.learned() } })
    }

    @Test
    fun aNameIsTypedIntoTheDialogAndAnAliasThatWouldBeACycleOrNothingIsRefusedOnTheScreen() {
        launch(); openRules(); scrollRules("alias_add"); tap("alias_add"); waitFor("same_shop_from")
        compose.onNodeWithTag("same_shop_from", useUnmergedTree = true).performTextInput("Zepto Cafe")
        compose.onNodeWithTag("same_shop_to", useUnmergedTree = true).performTextInput("Zepto Cafe")
        tap("same_shop_ok"); waitFor("same_shop_problem")
        assertTrue(textOf("same_shop_problem").contains("already count as one shop"), "one shop with itself: ${textOf("same_shop_problem")}")
        assertEquals(emptyList(), aliases())
        compose.onNodeWithTag("same_shop_to", useUnmergedTree = true).performTextInput(" Cart")
        tapUntilGone("same_shop_ok", "same_shop_to")
        compose.waitUntil(15_000) { aliases().size == 1 }
        tap("alias_remove_${aliases().single().first}")
        compose.waitUntil(15_000) { aliases().isEmpty() }
    }

    @Test
    fun aCardOfAShopWithAnotherNameIsJoinedFromChangeWithUndoOnTheToReviewBar() {
        ingest(card(450, "FRESHPIK BANGALORE IN"), 1_000); ingest(card(300, "GREEN GROCER MART"), 2_000)
        launch(); openInbox(); waitFor("inbox_list")
        val first = idAt(1_000)
        scrollInbox("inbox_change_$first"); tap("inbox_change_$first")
        scrollInbox("inbox_same_shop_$first"); tap("inbox_same_shop_$first"); waitFor("same_shop_to")
        assertTrue(has("same_shop_suggest_0"), "the other waiting payee is offered"); tap("same_shop_suggest_0")
        tapUntilGone("same_shop_ok", "same_shop_to")
        compose.waitUntil(15_000) { aliases().size == 1 }
        assertEquals("freshpik", aliases().single().first)
        waitUndo(); assertTrue(compose.onAllNodes(hasText("now counts as", substring = true)).fetchSemanticsNodes().isNotEmpty())
        undo(); compose.waitUntil(15_000) { aliases().isEmpty() }
    }

    @Test
    fun anAmountLikeTheSubscriptionsSaysWhyWithItsCountsAndSaysWhenOneEntryWasSomethingElse() {
        amazonHistory()
        ingest(card(199, "AMAZON"), 1_000); ingest(card(2_499, "AMAZON"), 2_000)
        launch(); openInbox(); waitFor("inbox_list")
        val sub = idAt(1_000); val shop = idAt(2_000)
        scrollInbox("inbox_why_$sub")
        assertEquals("You chose Subscriptions for Amazon amounts like ₹199 (3 times; once something else)", textOf("inbox_why_$sub"))
        assertTrue(has("inbox_check_$sub"), "one nearby entry is a purchase: a check")
        assertTrue(textOf("inbox_goes_$sub").contains("Subscriptions") && !textOf("inbox_goes_$sub").contains("("), "the line says the category; the reason is below it")
        scrollInbox("inbox_why_$shop")
        assertEquals("You chose Shopping for Amazon amounts like ₹2,499 (3 times)", textOf("inbox_why_$shop"))
        assertTrue(!has("inbox_check_$shop"), "three of three agree: no 'guess' line")
        // nothing is added by itself: both are still waiting
        assertEquals(2, runBlocking { container.write { it.inbox.list().size } })
    }

    @Test
    fun theReasonAndTheButtonsStayReadableAtTwiceTheFontSize() {
        amazonHistory()
        ingest(card(199, "AMAZON"), 1_000)
        shell("settings put system font_scale 2.0"); Thread.sleep(2_000)
        launch(); openInbox(); waitFor("inbox_list")
        val id = idAt(1_000)
        scrollInbox("inbox_why_$id")
        val why = compose.onNodeWithTag("inbox_why_$id", useUnmergedTree = true)
        why.assertTextContains("amounts like ₹199 (3 times; once something else)", substring = true)
        assertTrue(why.fetchSemanticsNode().size.width > 0 && why.fetchSemanticsNode().size.height > 0)
        tap("inbox_change_$id"); scrollInbox("inbox_same_shop_$id"); tap("inbox_same_shop_$id"); waitFor("same_shop_to")
        assertTrue(has("same_shop_ok") && has("same_shop_cancel"), "the buttons of the dialog are reachable")
        tapUntilGone("same_shop_cancel", "same_shop_to")
        scrollInbox("inbox_add_$id"); assertTrue(has("inbox_add_$id"))
    }
}
