package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Some ICICI Bank account texts print only THREE digits of the account ("Acct XX123"), cards four. The account that carries those digits (saved with three or with four that end the same, at the
 * same bank) is the text's account; with two that fit, or none, the card asks "Which account is this?", remembers the answer for that bank and those digits, lets the waiting cards of it follow with
 * Undo, and can make the account ("New account ending 123"). Every name, digit and amount is invented.
 */
@RunWith(AndroidJUnit4::class)
class AccountAskUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)).uppercase()
    private val now = System.currentTimeMillis() - 3_600_000

    /** ICICI's account alert as a tester described it: three digits of the account. */
    private fun icici(amount: String, payee: String, ref: String, digits: String = "123") =
        "ICICI Bank Acct XX$digits debited for Rs $amount on $today; $payee credited. UPI:$ref. Call 18002662 for dispute. SMS BLOCK $digits to 9215676766."
    private fun ingest(body: String, at: Long, sender: String = "AX-ICICIB-S") = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun mergedText(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun account(id: String, name: String, digits: String?, type: AccountType = AccountType.BANK) = runBlocking { container.write { ok ->
        ok.store.saveAccount(Account(AccountId(id), name, type, Currency.INR)); if (digits != null) ok.looks.save(AccountLook(id, last4 = digits))
    } }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun hasWords(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return node(tag).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun typed(tag: String) = node(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back") }
    private fun idAt(t: Long) = runBlocking { container.write { it.inbox.list().first { i -> i.receivedAt == t }.id } }!!
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.recentEntries(50).executeAsList() } }!!
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun accounts() = runBlocking { container.write { it.db.schemaQueries.selectAccounts().executeAsList() } }!!
    private fun look(id: String) = runBlocking { container.write { it.looks.get(id) } }
    private fun reveal(id: String) { compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_$id")) }
    private fun goes(id: String): String { reveal(id); return textOf("inbox_goes_$id") }
    private fun tapUndo() { compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }; compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick() }

    // ---- the account is found ----

    @Test
    fun aThreeDigitTextGoesToTheAccountSavedWithThoseThreeDigitsNotToAnotherBanksAndNeedsNoQuestion() {
        account("icici", "ICICI Savings", "123"); account("hdfc", "HDFC Salary", "4123")
        ingest(icici("500.00", "XQZ KLM", "628374659281"), now)
        launch(); openInbox(); val id = idAt(now)
        assertTrue(goes(id).startsWith("Goes to ICICI Savings"), goes(id))
        assertTrue(!has("inbox_account_ask_$id"), "the account is known: no question")
        assertEquals("ICICI Bank", runBlocking { container.write { it.inbox.get(id)!!.issuer } })
        assertEquals("123", runBlocking { container.write { it.inbox.get(id)!!.accountLast4 } }, "the three digits are kept as three")
        tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        assertEquals("icici", entries().single().account_id)
    }

    @Test
    fun anAccountSavedWithFourDigitsThatEndTheSameIsFoundByAThreeDigitText() {
        account("icici", "ICICI Savings", "4123")
        ingest(icici("500.00", "XQZ KLM", "628374659282"), now)
        launch(); openInbox(); val id = idAt(now)
        assertTrue(goes(id).startsWith("Goes to ICICI Savings"), goes(id)); assertTrue(!has("inbox_account_ask_$id"))
    }

    @Test
    fun theSameLastDigitsAtAnotherBankAreNeverTakenAndTheCardAsksInstead() {
        account("hdfc", "HDFC Salary", "123")
        ingest(icici("500.00", "XQZ KLM", "628374659283"), now)
        launch(); openInbox(); val id = idAt(now)
        waitFor("inbox_account_ask_$id")
        assertEquals("I have not seen ICICI Bank ending 123 before.", textOf("inbox_account_why_$id"))
        assertTrue(!goes(id).contains("HDFC Salary"), goes(id))
    }

    // ---- two accounts fit: nothing is guessed ----

    @Test
    fun twoAccountsThatEndInTheSameDigitsAreAskedAboutTheAnswerIsRememberedTheOtherCardFollowsAndUndoAsksAgain() {
        account("icici1", "ICICI Savings", "123"); account("icici2", "ICICI Joint", "7123")
        ingest(icici("500.00", "XQZ KLM", "628374659284"), now); ingest(icici("700.00", "XQZ KLM", "628374659285"), now + 60_000)
        launch(); openInbox(); val a = idAt(now + 60_000); val b = idAt(now) // the newest text is the first card: it carries the question, the other follows it
        reveal(a); waitFor("inbox_account_ask_$a") // the newest text is the first card: the other one is below the screen until it is scrolled to
        val why = textOf("inbox_account_why_$a"); assertTrue(why.contains("ICICI Savings") && why.contains("ICICI Joint") && why.contains("end in 123") && why.contains("cannot tell"), why)
        assertTrue(has("inbox_pick_${a}_icici1") && has("inbox_pick_${a}_icici2"), "the two that fit are offered")
        assertTrue(!has("inbox_pick_${a}_cash") && !has("inbox_pick_${a}_bank"), "no other account is offered")
        node("inbox_add_$a").performScrollTo().assertIsNotEnabled() // no account, no guess: Add waits for the answer
        reveal(a); tap("inbox_pick_${a}_icici2")
        compose.waitUntil(10_000) { !has("inbox_account_ask_$a") }
        assertTrue(goes(a).startsWith("Goes to ICICI Joint"), goes(a)); node("inbox_add_$a").performScrollTo().assertIsEnabled()
        // the other waiting text of the same bank and digits took the answer at once
        assertTrue(goes(b).startsWith("Goes to ICICI Joint"), goes(b)); assertTrue(!has("inbox_account_ask_$b"))
        assertEquals("icici2", setting("capture_acct:ICICI Bank:123"))
        compose.waitUntil(10_000) { hasWords("Using ICICI Joint for ICICI Bank ending 123 · 1 more waiting text follows") }
        tapUndo()
        reveal(a); waitFor("inbox_account_ask_$a"); reveal(b); waitFor("inbox_account_follow_$b"); assertTrue(!has("inbox_account_ask_$b"), "one question per bank card")
        assertNull(setting("capture_acct:ICICI Bank:123"), "undone: nothing remembered")
        reveal(a); tap("inbox_pick_${a}_icici1"); waitGone("inbox_account_ask_$a")
        tap("inbox_add_$a"); compose.waitUntil(10_000) { entries().size == 1 }
        assertEquals("icici1", entries().single().account_id)
        // a text that comes later with the same digits starts with the answer
        ingest(icici("900.00", "XQZ KLM", "628374659286"), now + 120_000)
        tap("inbox_back"); openInbox(); val c = idAt(now + 120_000)
        assertTrue(goes(c).startsWith("Goes to ICICI Savings"), goes(c)); assertTrue(!has("inbox_account_ask_$c"))
    }

    // ---- no account fits: ask once per bank card, Add all waits, and the answer unlocks it ----

    @Test
    fun withNoAccountKnownAddAllLeavesTheTextsOutSaysHowManyAndOneAnswerMakesThemClear() {
        account("icici", "ICICI Savings", null)
        ingest(icici("500.00", "XQZ KLM", "628374659287"), now); ingest(icici("700.00", "XQZ KLM", "628374659288"), now + 60_000)
        launch(); openInbox(); val a = idAt(now + 60_000); val b = idAt(now) // the newest text is the first card: it carries the question, the other follows it
        waitFor("inbox_needs_account")
        assertTrue(textOf("inbox_needs_account").startsWith("2 waiting texts need an account first"), textOf("inbox_needs_account"))
        assertTrue(mergedText("inbox_fix_accounts").contains("(1)"), "two texts, one bank card: one question")
        assertTrue(!has("inbox_add_all"), "nothing is clear yet")
        assertTrue(!has("inbox_swipe_hint"), "and nothing can be swiped")
        tap("inbox_fix_accounts")
        reveal(a); tap("inbox_pick_${a}_icici")
        compose.waitUntil(10_000) { has("inbox_add_all") }
        assertTrue(!has("inbox_needs_account"), "answered")
        assertEquals("icici", setting("capture_acct:ICICI Bank:123"))
        tap("inbox_add_all"); waitFor("inbox_add_all_ok"); tap("inbox_add_all_ok")
        compose.waitUntil(10_000) { entries().size == 2 }
        assertEquals(setOf("icici"), entries().map { it.account_id }.toSet())
        assertEquals(setOf(a, b).size, 2)
    }

    // ---- no account fits: make one ----

    @Test
    fun newAccountEndingOneTwoThreeMakesTheAccountWithThoseDigitsUsesItRemembersItAndUndoTakesItAllBack() {
        ingest(icici("500.00", "XQZ KLM", "628374659289", digits = "999"), now)
        launch(); openInbox(); val id = idAt(now)
        waitFor("inbox_account_ask_$id")
        assertEquals("New account ending 999", mergedText("inbox_new_account_$id"))
        tap("inbox_new_account_$id"); waitFor("inbox_new_name_$id")
        assertEquals("ICICI Bank 999", typed("inbox_new_name_$id"), "named from the sender and the digits"); node("inbox_new_type_${id}_BANK").assertIsSelected()
        tap("inbox_new_create_$id")
        compose.waitUntil(10_000) { accounts().any { it.name == "ICICI Bank 999" } }
        val made = accounts().single { it.name == "ICICI Bank 999" }
        assertEquals("BANK", made.type); assertEquals("999", look(made.id)?.last4, "it carries the digits the text printed")
        assertEquals(made.id, setting("capture_acct:ICICI Bank:999"))
        waitGone("inbox_account_ask_$id"); assertTrue(goes(id).startsWith("Goes to ICICI Bank 999"), goes(id))
        compose.waitUntil(10_000) { hasWords("Created ICICI Bank 999 and using it for ICICI Bank ending 999") }
        tapUndo()
        compose.waitUntil(10_000) { accounts().none { it.name == "ICICI Bank 999" } }
        assertNull(setting("capture_acct:ICICI Bank:999")); waitFor("inbox_account_ask_$id")
        // once more, and keep it: the next text with the digits needs no question
        tap("inbox_new_account_$id"); tap("inbox_new_create_$id"); compose.waitUntil(10_000) { accounts().any { it.name == "ICICI Bank 999" } }
        waitGone("inbox_account_ask_$id"); tap("inbox_add_$id"); compose.waitUntil(10_000) { entries().size == 1 }
        val again = accounts().single { it.name == "ICICI Bank 999" }
        assertEquals(again.id, entries().single().account_id)
        ingest(icici("800.00", "XQZ KLM", "628374659290", digits = "999"), now + 60_000)
        tap("inbox_back"); openInbox(); val next = idAt(now + 60_000)
        assertTrue(goes(next).startsWith("Goes to ICICI Bank 999"), goes(next)); assertTrue(!has("inbox_account_ask_$next"))
    }

    @Test
    fun aTextThatSaysCreditCardStartsTheNewAccountAsACreditCardAndAnythingElseAsABankAccount() {
        ingest("INR 482.35 spent using ICICI Bank Credit Card XX3025 on $today on XQZ KLM. Avl Limit: INR 90,000.00. If not you, call 18002662/SMS BLOCK 3025 to 9215676766.", now, "BP-ICICIT-S")
        launch(); openInbox(); val id = idAt(now)
        waitFor("inbox_account_ask_$id"); tap("inbox_new_account_$id"); waitFor("inbox_new_type_${id}_CREDIT_CARD")
        node("inbox_new_type_${id}_CREDIT_CARD").assertIsSelected()
        assertEquals("ICICI Bank 3025", typed("inbox_new_name_$id"))
        tap("inbox_new_create_$id"); compose.waitUntil(10_000) { accounts().any { it.name == "ICICI Bank 3025" } }
        assertEquals("CREDIT_CARD", accounts().single { it.name == "ICICI Bank 3025" }.type)
    }

    @Test
    fun aNameAlreadyInUseIsRefusedOnTheFormAndNothingIsMade() {
        account("x", "ICICI Bank 999", null)
        ingest(icici("500.00", "XQZ KLM", "628374659291", digits = "999"), now)
        launch(); openInbox(); val id = idAt(now)
        waitFor("inbox_account_ask_$id"); tap("inbox_new_account_$id"); tap("inbox_new_create_$id")
        waitFor("inbox_new_problem_$id"); assertEquals(1, accounts().count { it.name == "ICICI Bank 999" })
        assertNull(setting("capture_acct:ICICI Bank:999"))
    }

    // ---- own-account transfers on three digits ----

    private val out get() = "ICICI Bank Acct XX123 debited with Rs 10,000.00 on $today & Acct XX456 credited.IMPS:000000000001. Call 18002662 for dispute or SMS BLOCK 123 to 9215676766"
    private val into get() = "ICICI Bank Account XX456 is credited with Rs 10,000.00 on $today by Account linked to mobile number XXXXX12345. IMPS Ref. no. 000000000001."

    @Test
    fun theTwoThreeDigitTextsOfOneOwnAccountTransferAreOneCardAndAddMakesOneTransfer() {
        account("sav", "ICICI Savings", "123"); account("sal", "ICICI Salary", "456")
        ingest(out, now); ingest(into, now + 40_000)
        launch(); openInbox(); val o = idAt(now)
        waitFor("inbox_transfer_$o")
        assertTrue(textOf("inbox_transfer_$o").startsWith("Transfer from ICICI Savings to ICICI Salary"), textOf("inbox_transfer_$o"))
        tap("inbox_transfer_add_$o"); compose.waitUntil(10_000) { has("inbox_empty") }
        val e = entries().single(); assertEquals("TRANSFER", e.kind); assertEquals("sav", e.account_id); assertEquals("sal", e.counter_account_id); assertEquals(1_000_000, e.amount_minor)
    }

    @Test
    fun withTwoAccountsEndingInTheSameThreeDigitsNothingIsPairedOnAGuessAndTheDebitAsksWhichAccount() {
        account("sav", "ICICI Savings", "123"); account("joint", "ICICI Joint", "7123"); account("sal", "ICICI Salary", "456")
        ingest(out, now); ingest(into, now + 40_000)
        launch(); openInbox(); val o = idAt(now); val i = idAt(now + 40_000)
        waitFor("inbox_$o"); waitFor("inbox_$i")
        assertTrue(!has("inbox_transfer_$o"), "no combined card: the first account could be either")
        waitFor("inbox_account_ask_$o"); assertTrue(textOf("inbox_account_why_$o").contains("end in 123"), textOf("inbox_account_why_$o"))
        // the person answers: the pair is found at once
        tap("inbox_pick_${o}_sav")
        compose.waitUntil(10_000) { has("inbox_transfer_$o") }
        assertTrue(textOf("inbox_transfer_$o").startsWith("Transfer from ICICI Savings to ICICI Salary"), textOf("inbox_transfer_$o"))
    }
}
