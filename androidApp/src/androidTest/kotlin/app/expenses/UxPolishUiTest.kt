package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The 0.10.6 UX pass (Slice 101): one Home nudge at a time, To review cards with the progress and one toggle for the rest, the Capture screen in sections, the bulk bar without
 * sideways scrolling, Search from an account with every chip in sight and no keyboard over the list, a line on screens that had no way to say what an action did, and the empty
 * states' first actions.
 */
@RunWith(AndroidJUnit4::class)
class UxPolishUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun hasWords(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun ingest(sender: String, body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun db(block: (AppContainer.Opened.Ok) -> Unit) { runBlocking { container.write { block(it) } } }
    private fun bare(id: String, at: Long, merchant: String, account: String = "bank") = db { ok -> ok.store.save(Expense(EntryId(id), at, AccountId(account), Money(10_000 + at, Currency.INR)), EntryDetails(merchant = merchant)) }
    private val debit = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"
    private val debit2 = "Rs.300.00 debited from A/c XX1234 on 05-10-26 to VPA other@okhdfc (UPI Ref 628374650011). Not you? Call 18002586161"

    // ---- 5. one nudge on Home ----

    @Test
    fun homeShowsOneCardAtATimeAndNamesWhatElseIsWaitingAndNotNowBringsTheNextOne() {
        for (i in 0 until 4) bare("u$i", 1_000L + i, "Plain $i") // four with no category: the categorise card would show
        ingest("AX-HDFCBK-S", debit, System.currentTimeMillis() - 5_000) // and one text waiting: it outranks the categorise card
        launch(); waitFor("inbox_banner")
        Thread.sleep(2_500) // the slow figures (the no-category count) arrive after the page
        assertFalse(has("categorise_nudge"), "one card at a time: the review card is the one shown")
        waitFor("home_more_nudges")
        compose.onNodeWithTag("home_more_nudges", useUnmergedTree = true).assertTextContains("Expenses with no category", substring = true)
        tap("nudge_notnow_inbox"); waitGone("inbox_banner"); waitFor("categorise_nudge")
        assertFalse(has("home_more_nudges"), "nothing else is waiting now")
        // a new text brings the put-off card back at once
        ingest("AX-HDFCBK-S", debit2, System.currentTimeMillis() - 3_000)
        launch(); waitFor("inbox_banner")
    }

    @Test
    fun theMorePageCarriesTheSameNudgesWhateverHomeShows() {
        for (i in 0 until 4) bare("u$i", 1_000L + i, "Plain $i")
        ingest("AX-HDFCBK-S", debit, System.currentTimeMillis() - 5_000)
        launch(); waitFor("inbox_banner"); Thread.sleep(2_500)
        tap("nudge_notnow_inbox"); waitFor("categorise_nudge")
        compose.openMore()
        waitFor("open_inbox"); waitFor("open_categorise")
        assertTrue(hasWords("1 waiting for you"), "a card put off on Home is still on the More page")
    }

    // ---- 3. To review: progress and one toggle ----

    @Test
    fun everyToReviewCardSaysWhereItIsAndTheAccountQuestionKeepsItsExplanationBehindMoreOptions() {
        db { ok ->
            ok.store.saveAccount(Account(AccountId("icici"), "ICICI Savings", AccountType.BANK, Currency.INR)); ok.looks.save(AccountLook("icici", last4 = "999"))
            ok.store.saveAccount(Account(AccountId("hdfc"), "HDFC Salary", AccountType.BANK, Currency.INR)); ok.looks.save(AccountLook("hdfc", last4 = "888"))
        }
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yy", java.util.Locale.ENGLISH)).uppercase()
        val a = System.currentTimeMillis() - 3_600_000
        ingest("AX-ICICIB-S", "ICICI Bank Acct XX123 debited for Rs 500.00 on $today; XQZ KLM credited. UPI:628374659281. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.", a)
        ingest("AX-HDFCBK-S", "Rs.1,250.00 debited from A/c XX888 on 05-10-26 to VPA zorbo@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161", a + 1_000)
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_list")
        val ids = runBlocking { container.write { it.inbox.list().sortedBy { i -> i.receivedAt }.map { i -> i.id } } }!!
        val list = compose.onNodeWithTag("inbox_list", useUnmergedTree = true)
        val shown = ids.map { id -> list.performScrollToNode(hasTestTag("inbox_progress_$id")); compose.onNodeWithTag("inbox_progress_$id", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } }
        assertEquals(setOf("1 of 2", "2 of 2"), shown.toSet(), "every card says where it is: $shown")
        val asking = ids.first { id -> list.performScrollToNode(hasTestTag("inbox_$id")); has("inbox_account_ask_$id") }
        assertTrue(has("inbox_account_title_$asking") && !has("inbox_account_why_$asking"), "the question shows, its explanation is folded")
        assertTrue(has("inbox_add_$asking"), "Add is on the card")
        tap("inbox_more_options_$asking"); waitFor("inbox_account_why_$asking")
        tap("inbox_more_options_$asking"); waitGone("inbox_account_why_$asking")
    }

    // ---- 4. Capture in sections ----

    @Test
    fun theCaptureScreenHasSetUpRulesAndHealthSectionsWithEveryControlReachable() {
        launch(); compose.openMore(); tap("open_capture"); waitFor("capture_back")
        listOf("setup", "rules", "health").forEach { waitFor("section_$it") }
        listOf("capture_sms_switch", "capture_notif_switch", "email_capture_switch", "capture_headers", "capture_headers_save", "open_rules", "open_health").forEach { tag ->
            waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
        assertFalse(hasWords("What it does not do"), "the long explanation is folded")
        tap("capture_details_sms"); waitFor("capture_details_sms"); assertTrue(hasWords("What it does not do"))
        tap("capture_group_senders") // fold the sender names
        waitGone("capture_headers")
        tap("capture_group_senders"); waitFor("capture_headers")
    }

    // ---- 2. the bulk bar ----

    @Test
    fun theBulkBarHasThreeActionsAndMoreWhollyOnScreenAndTagIsUnderMore() {
        bare("a", 1_000, "Alpha Cafe"); bare("b", 2_000, "Beta Cafe")
        launch(); waitFor("row_Alpha Cafe")
        compose.onNodeWithTag("row_Alpha Cafe", useUnmergedTree = true).performTouchInput { longClick() }
        waitFor("bulk_bar")
        listOf("bulk_category", "bulk_transfer", "bulk_delete", "bulk_more", "bulk_cancel").forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed() }
        assertFalse(has("bulk_tag"), "Tag is under More")
        tap("bulk_more"); waitFor("bulk_tag"); waitFor("bulk_account")
    }

    // ---- 1. Search from an account ----

    @Test
    fun searchFromAnAccountShowsEveryAccountChipWithNoKeyboardOverTheList() {
        db { ok -> ok.store.saveAccount(Account(AccountId("wallet"), "Wallet", AccountType.CASH, Currency.INR)) }
        bare("a", System.currentTimeMillis() - 1_000, "CashNow", "cash"); bare("b", System.currentTimeMillis() - 2_000, "BankNow", "bank")
        launch(); tap("balance_chip_Cash"); tap("account_search")
        waitFor("search_text"); waitFor("result_a")
        compose.onNodeWithTag("search_active_chip_account", useUnmergedTree = true).assertIsDisplayed() // the account searched in is a chip under the box
        tap("search_filters_button"); waitFor("filters_apply") // every account is a choice in the Filters panel
        listOf("search_account_any", "search_account_Bank", "search_account_Cash", "search_account_Wallet").forEach { compose.onNodeWithTag(it, useUnmergedTree = true).assertExists() }
        assertFalse(androidx.core.view.ViewCompat.getRootWindowInsets(scenarioView())?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true, "the keyboard is not up over the account's list")
    }

    private fun scenarioView(): android.view.View { var v: android.view.View? = null; scenario!!.onActivity { v = it.window.decorView }; return v!! }

    // ---- 6. say what was done, 7. empty states ----

    @Test
    fun billsSayWhatAnActionDidOnTheirOwnScreenAndAnEmptyBillsPageOffersTheSetupStep() {
        launch(); compose.openMore(); tap("open_bills"); waitFor("bills_empty"); waitFor("bills_empty_setup")
        tap("bills_empty_setup"); waitFor("capture_back")
        tap("capture_back")
        val due = LocalDate.now().plusDays(12).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        ingest("AX-HDFCBK-S", "Your HDFC Bank Credit Card ending 0862 statement is generated. Total Amt Due Rs.12,345.00, Min Amt Due Rs.617.00, Due Date $due.", System.currentTimeMillis())
        val id = runBlocking { container.write { it.bills.list(BillStatus.FOUND).single().id } }!!
        compose.openMore(); tap("open_bills"); tap("bill_add_$id")
        compose.waitUntil(10_000) { hasWords("Added to your bills") } // said on the Bills screen, not left for Home
        assertTrue(has("bill_pay_$id") || has("bills_coming_title"))
    }

    @Test
    fun searchWithNothingFoundSaysWhatToDoAndClearsOnTheButton() {
        bare("a", System.currentTimeMillis() - 1_000, "Alpha")
        launch(); tap("open_search"); waitFor("search_text")
        compose.onNodeWithTag("search_text", useUnmergedTree = true).performTextInput("zzzqqq")
        waitFor("search_none"); compose.onNodeWithTag("search_none", useUnmergedTree = true).assertTextContains("No entries match", substring = true)
        tap("search_none_clear"); waitFor("search_hint")
    }

    @Test
    fun emptyInboxHasAFirstStepAndLeadsToCapture() {
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_empty"); waitFor("inbox_empty_setup")
        tap("inbox_empty_setup"); waitFor("capture_back")
    }

    /** The "Rule added" bar sits under the screen: it covers nothing, so the next tap lands (drawn over the screen it swallowed the tap on the next category chip, and the second rule got the first one's category). */
    @Test
    fun theBarThatSaysARuleWasAddedIsUnderTheScreenAndDoesNotCatchTheNextTap() {
        launch(); compose.openMore(); tap("open_capture"); tap("open_rules"); waitFor("rules_list")
        fun reveal(tag: String) { compose.onNodeWithTag("rules_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)); waitFor(tag) }
        fun add(word: String, category: String) {
            reveal("rule_pattern"); compose.onNodeWithTag("rule_pattern", useUnmergedTree = true).performTextInput(word)
            reveal("rule_category_$category"); compose.onNodeWithTag("rule_category_$category", useUnmergedTree = true).performClick()
            reveal("rule_add"); compose.onNodeWithTag("rule_add", useUnmergedTree = true).performClick()
        }
        add("uber", "Transport"); waitFor("message_text")
        compose.onNodeWithTag("message_text", useUnmergedTree = true).assertTextContains("Rule added", substring = true)
        val list = compose.onNodeWithTag("rules_list", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("message_host", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(list.bottom <= bar.top + 1f, "the list ($list) ends where the bar ($bar) begins: nothing is under it")
        add("eats", "Food") // while the bar is still up
        compose.waitUntil(10_000) { runBlocking { container.write { it.captureRules.rules().map { r -> r.pattern to r.categoryId } } } == listOf("uber" to "Transport", "eats" to "Food") }
    }
}
