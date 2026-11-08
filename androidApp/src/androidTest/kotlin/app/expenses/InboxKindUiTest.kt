package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.PayeeKey
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.InboxStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The type switch on To review cards (Expense / Income / Transfer), what a transfer chosen there records and how Undo takes it back, a transfer the app learns from what the person did,
 * Add all and swipe wording and limits, and the second text of a move of money joining the transfer that is already in the ledger. Invented texts: 1234 is the Main account, 5678 Savings.
 */
@RunWith(AndroidJUnit4::class)
class InboxKindUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        resetApp()
        runBlocking { container.write { ok ->
            ok.templates.putSetting("first_week_dismissed", "1")
            ok.store.saveAccount(Account(AccountId("bank"), "Main", AccountType.BANK, Currency.INR))
            ok.store.saveAccount(Account(AccountId("savings"), "Savings", AccountType.BANK, Currency.INR))
            ok.looks.save(AccountLook("bank", last4 = "1234")); ok.looks.save(AccountLook("savings", last4 = "5678"))
        } }
    }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    // ---- texts and data ----
    private val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yy"))
    private val t0 = System.currentTimeMillis() - 7_200_000
    private var seq = 0
    private fun nextRef() = "6283746${50_000 + ++seq}"
    private fun out(payee: String, amount: String, ref: String = nextRef()) = "Rs.$amount debited from A/c XX1234 on $today to VPA $payee (UPI Ref $ref). Not you? Call 18002586161"
    private fun inn(payer: String, amount: String, ref: String = nextRef(), acct: String = "1234") = "Rs.$amount credited to A/c XX$acct on $today by VPA $payer (UPI Ref $ref)."
    private val atm = "Rs.2,000.00 debited from A/c XX1234 on 05-10-26. Info: ATM-CASH. Avl Bal Rs.100.00"
    private fun ingest(body: String, at: Long = t0 + 60_000L * ++seq): Long { runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }; return at }
    private fun idAt(t: Long) = runBlocking { container.write { it.inbox.list().first { i -> i.receivedAt == t }.id } }!!
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.recentEntries(50).executeAsList() } }!!
    private fun status(id: String) = runBlocking { container.write { it.inbox.get(id)?.status } }
    private fun body(id: String) = runBlocking { container.write { it.inbox.get(id)?.body } }
    private fun entryOf(id: String) = runBlocking { container.write { it.inbox.get(id)?.entryId } }
    private fun balance(id: String) = runBlocking { container.write { it.store.balances().getValue(AccountId(id)).minorUnits } }!!
    private fun originText(entryId: String) = runBlocking { container.write { it.origins.get(entryId)?.text } }
    private fun rules() = runBlocking { container.write { it.templates.settingsWithPrefix("transfer_rule:") } }!!
    private val ruleKey = "transfer_rule:p:out:${PayeeKey.of("rahul@okbank")}"
    private fun seedRule(uses: Int, against: Int = 0) = runBlocking { container.write { it.templates.putSetting(ruleKey, "savings|$uses|$against") } }

    // ---- screen ----
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    /** The list is lazy: a card below the fold is not composed until the list is scrolled to it. */
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) || run { runCatching { compose.onNodeWithTag("inbox_list").performScrollToNode(hasTestTag(tag)) }; has(tag) } }
    private fun click(tag: String) { if (has(tag)) { val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() } }
    private fun tap(tag: String) { waitFor(tag); click(tag) }
    /** A tap can be lost to a race: tap again every ~2 s until [done]. */
    private fun tapUntil(tag: String, done: () -> Boolean) { var last = 0L; waitFor(tag); compose.waitUntil(20_000) { done() || run { if (System.currentTimeMillis() - last > 2_000) { last = System.currentTimeMillis(); runCatching { click(tag) } }; false } } }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun selected(tag: String) = has(tag) && compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> c.contains(SemanticsProperties.Selected) && c[SemanticsProperties.Selected] }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); compose.waitUntil(10_000) { has("add") } }
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back") }
    private fun show() { launch(); openInbox() }
    private fun tapUndo() { compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }; compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick() }
    private fun toTransfer(id: String, other: String) { tapUntil("inbox_kind_${id}_TRANSFER") { selected("inbox_kind_${id}_TRANSFER") }; tapUntil("inbox_other_${id}_$other") { selected("inbox_other_${id}_$other") } }
    private fun addAndWait(id: String) { tapUntil("inbox_add_$id") { status(id) == InboxStatus.CONFIRMED } }
    /** The Add all dialog's text, with the dialog left open. */
    private fun addAllText(): String { tapUntil("inbox_add_all") { has("inbox_add_all_text") }; return textOf("inbox_add_all_text") }
    private fun card(id: String): androidx.compose.ui.test.SemanticsNodeInteraction { waitFor("inbox_list"); compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_$id")); return compose.onNodeWithTag("inbox_$id", useUnmergedTree = true) }

    @Test
    fun everyOrdinaryCardHasTheTypeChipsWithTheTextsOwnTypeSelectedAndTransferAsksForTheOtherAccount() {
        val d = idAt(ingest(out("randomshop@okaxis", "450.00"))); val c = idAt(ingest(inn("ravi@ybl", "3,000.00")))
        show()
        waitFor("inbox_kinds_$d")
        assertTrue(selected("inbox_kind_${d}_EXPENSE") && !selected("inbox_kind_${d}_INCOME") && !selected("inbox_kind_${d}_TRANSFER"))
        tapUntil("inbox_kind_${d}_TRANSFER") { selected("inbox_kind_${d}_TRANSFER") }
        assertTrue(!selected("inbox_kind_${d}_EXPENSE")); assertEquals("To account", textOf("inbox_other_label_$d"))
        assertTrue(has("inbox_other_${d}_savings") && !has("inbox_other_${d}_bank"), "the text's own account is not offered as the other end")
        waitFor("inbox_kinds_$c")
        assertTrue(selected("inbox_kind_${c}_INCOME") && !selected("inbox_kind_${c}_EXPENSE") && !selected("inbox_kind_${c}_TRANSFER"))
        tapUntil("inbox_kind_${c}_TRANSFER") { selected("inbox_kind_${c}_TRANSFER") }
        assertEquals("From account", textOf("inbox_other_label_$c"))
    }

    @Test
    fun aDebitMadeATransferIsOneTransferFromMainToSavingsWithItsTextKeptAndUndoPutsEverythingBack() {
        val ref = "628374659001"
        val id = idAt(ingest(out("rahul@okbank", "2,500.00", ref))); show()
        toTransfer(id, "savings")
        compose.onNodeWithTag("inbox_add_$id").assertTextEquals("Add as transfer")
        addAndWait(id)
        val e = entries().single()
        assertEquals("TRANSFER", e.kind); assertEquals("bank", e.account_id); assertEquals("savings", e.counter_account_id); assertEquals(250_000, e.amount_minor)
        assertEquals(ref, e.source_ref); assertEquals(e.id, entryOf(id))
        assertTrue(originText(e.id)!!.contains("rahul@okbank"), "the text is kept as where it came from")
        assertEquals(-250_000L, balance("bank")); assertEquals(250_000L, balance("savings"))
        assertEquals(1, rules().size, "the choice is remembered")
        tapUndo(); compose.waitUntil(10_000) { status(id) == InboxStatus.PENDING }
        assertEquals(emptyList(), entries()); assertEquals(0L, balance("bank")); assertEquals(0L, balance("savings"))
        assertTrue(body(id)!!.contains("rahul@okbank"), "the text waits again"); assertEquals(emptyList(), rules(), "what Undo took back is not learned")
        assertEquals(null, originText(e.id))
    }

    @Test
    fun aCreditMadeATransferComesFromTheChosenAccountIntoTheTextsOwnAccount() {
        val id = idAt(ingest(inn("ravi@ybl", "3,000.00"))); show()
        tapUntil("inbox_kind_${id}_TRANSFER") { selected("inbox_kind_${id}_TRANSFER") }
        assertEquals("From account", textOf("inbox_other_label_$id"))
        toTransfer(id, "savings"); compose.onNodeWithTag("inbox_add_$id").assertTextEquals("Add as transfer")
        addAndWait(id)
        val e = entries().single()
        assertEquals("TRANSFER", e.kind); assertEquals("savings", e.account_id); assertEquals("bank", e.counter_account_id); assertEquals(300_000, e.amount_minor)
        assertEquals(300_000L, balance("bank")); assertEquals(-300_000L, balance("savings"))
    }

    @Test
    fun anAtmTextStartsAsACashWithdrawalWithOneCashAccountAndWithTwoItStartsAsAnExpenseThatPicksNothingForYou() {
        val one = idAt(ingest(atm)); show()
        assertTrue(textOf("inbox_pair_$one").contains("cash withdrawal"), textOf("inbox_pair_$one"))
        assertTrue(selected("inbox_kind_${one}_TRANSFER"))
        addAndWait(one)
        val e = entries().single(); assertEquals("TRANSFER", e.kind); assertEquals("bank", e.account_id); assertEquals("cash", e.counter_account_id)
        assertEquals(emptyList(), rules(), "a transfer read from the words is not something the person taught")
        runBlocking { container.write { it.store.saveAccount(Account(AccountId("cash2"), "Wallet", AccountType.CASH, Currency.INR)) } }
        val two = idAt(ingest(atm.replace("2,000.00", "2,100.00"))); show()
        waitFor("inbox_kinds_$two")
        assertTrue(selected("inbox_kind_${two}_EXPENSE") && !has("inbox_pair_$two"), "with two Cash accounts it is an expense until the person says")
        tapUntil("inbox_kind_${two}_TRANSFER") { selected("inbox_kind_${two}_TRANSFER") }
        waitFor("inbox_other_${two}_cash"); waitFor("inbox_other_${two}_cash2")
        assertTrue(!selected("inbox_other_${two}_cash") && !selected("inbox_other_${two}_cash2") && !has("inbox_other_why_$two"))
        compose.onNodeWithTag("inbox_add_$two").assertIsNotEnabled()
    }

    /** One transfer to Savings made by tapping: the chips, the account, Add. */
    private fun makeTransferByTaps(payee: String, amount: String) {
        val id = idAt(ingest(out(payee, amount))); show(); toTransfer(id, "savings"); addAndWait(id)
    }

    @Test
    fun theSamePayeeMadeATransferTwiceStartsTheThirdAsATransferToCheckThatAddAllLeavesOut() {
        makeTransferByTaps("rahul@okbank", "1,100.00")
        assertEquals("savings|1|0", rules().single().second, "learned from one tap")
        // one choice is enough to START the next text as a transfer to check; adding it as it stands is a second consistent choice
        val second = idAt(ingest(out("rahul@okbank", "1,200.00"))); show(); waitFor("inbox_transfer_learned_$second"); addAndWait(second)
        assertEquals("savings|2|0", rules().single().second, "learned from two choices")
        val third = idAt(ingest(out("rahul@okbank", "1,300.00")))
        val p1 = idAt(ingest(out("swiggy@icici", "310.00"))); val p2 = idAt(ingest(out("randomshop@okaxis", "320.00")))
        show(); waitFor("inbox_pair_$third")
        assertTrue(textOf("inbox_transfer_learned_$third").contains("2 times"), textOf("inbox_transfer_learned_$third"))
        assertTrue(textOf("inbox_pair_$third").contains("Savings"))
        val dialog = addAllText()
        assertTrue(!dialog.contains("between your own accounts"), "a transfer made twice is looked at, not added in bulk: $dialog")
        tapUntil("inbox_add_all_ok") { status(p1) == InboxStatus.CONFIRMED && status(p2) == InboxStatus.CONFIRMED }
        assertEquals(InboxStatus.PENDING, status(third)); assertEquals(4, entries().size)
    }

    @Test
    fun aThirdConsistentTransferMakesTheNextTwoClearEnoughForAddAll() {
        seedRule(2)
        val third = idAt(ingest(out("rahul@okbank", "1,300.00"))); show()
        waitFor("inbox_transfer_learned_$third"); addAndWait(third)
        assertEquals("savings|3|0", rules().single().second)
        val a = idAt(ingest(out("rahul@okbank", "1,400.00"))); val b = idAt(ingest(out("rahul@okbank", "1,500.00")))
        show(); waitFor("inbox_pair_$a")
        val dialog = addAllText()
        assertTrue(dialog.contains("2 are transfers between your own accounts"), dialog)
        tapUntil("inbox_add_all_ok") { status(a) == InboxStatus.CONFIRMED && status(b) == InboxStatus.CONFIRMED }
        assertEquals(listOf("TRANSFER", "TRANSFER", "TRANSFER"), entries().map { it.kind })
        assertTrue(entries().all { it.account_id == "bank" && it.counter_account_id == "savings" })
    }

    @Test
    fun recordingALearnedTransferAsAnExpenseCountsAgainstTheRuleAndItIsNoLongerAddedInBulk() {
        seedRule(3)
        val first = idAt(ingest(out("rahul@okbank", "1,300.00"))); show()
        waitFor("inbox_transfer_learned_$first")
        tapUntil("inbox_kind_${first}_EXPENSE") { selected("inbox_kind_${first}_EXPENSE") }
        addAndWait(first)
        assertEquals("EXPENSE", entries().single().kind); assertEquals("savings|3|1", rules().single().second)
        val next = idAt(ingest(out("rahul@okbank", "1,600.00")))
        val p1 = idAt(ingest(out("swiggy@icici", "310.00"))); val p2 = idAt(ingest(out("randomshop@okaxis", "320.00")))
        show(); waitFor("inbox_transfer_learned_$next")
        assertTrue(!addAllText().contains("between your own accounts"), "an exception was recorded: the card is checked every time")
        tapUntil("inbox_add_all_ok") { status(p1) == InboxStatus.CONFIRMED && status(p2) == InboxStatus.CONFIRMED }
        assertEquals(InboxStatus.PENDING, status(next))
    }

    @Test
    fun aCardSwitchedToTransferIsNotApprovedByARightSwipeButAClearDefaultCardIs() {
        val a = idAt(ingest(out("randomshop@okaxis", "320.00"))); val b = idAt(ingest(out("swiggy@icici", "310.00")))
        show(); toTransfer(a, "savings")
        repeat(2) { card(a).performTouchInput { swipeRight() }; compose.waitForIdle() }
        var tries = 0
        compose.waitUntil(20_000) { status(b) == InboxStatus.CONFIRMED || run { if (tries++ % 20 == 0) runCatching { card(b).performTouchInput { swipeRight() } }; false } }
        assertEquals(InboxStatus.PENDING, status(a), "its type was changed: only its Add button records it")
        val e = entries().single(); assertEquals("EXPENSE", e.kind); assertEquals(31_000, e.amount_minor)
    }

    /** The debit text added as a transfer Main to Savings, returns the entry's id. */
    private fun debitAddedAsTransfer(at: Long): String {
        val d = idAt(ingest(out("rahul@okbank", "2,500.00"), at)); show(); toTransfer(d, "savings"); addAndWait(d)
        return entryOf(d)!!
    }

    @Test
    fun theCreditTextOfAddedTransferJoinsItAndUndoRestoresTheTextAndTheOldOrigin() {
        val at = t0; val entry = debitAddedAsTransfer(at)
        val first = originText(entry)!!
        val c = idAt(ingest(inn("rahul@okbank", "2,500.00", acct = "5678"), at + 600_000)); show()
        waitFor("inbox_otherleg_$c"); waitFor("inbox_join_$c")
        assertTrue(!has("inbox_add_$c"), "the other leg has no Add of its own")
        tapUntil("inbox_join_$c") { status(c) == InboxStatus.CONFIRMED }
        assertEquals(entry, entryOf(c)); assertEquals(listOf(entry), entries().map { it.id })
        assertTrue(originText(entry)!!.contains("debited from") && originText(entry)!!.contains("credited to"), "both texts are kept")
        assertEquals(-250_000L, balance("bank")); assertEquals(250_000L, balance("savings"))
        tapUndo(); compose.waitUntil(10_000) { status(c) == InboxStatus.PENDING }
        assertEquals(first, originText(entry)); assertTrue(body(c)!!.contains("credited to")); assertEquals(1, entries().size)
        assertEquals(-250_000L, balance("bank")); assertEquals(250_000L, balance("savings"))
    }

    @Test
    fun itIsADifferentPaymentTurnsTheOtherLegCardIntoAnOrdinaryOne() {
        val at = t0; debitAddedAsTransfer(at)
        val c = idAt(ingest(inn("rahul@okbank", "2,500.00", acct = "5678"), at + 600_000)); show()
        waitFor("inbox_otherleg_$c"); tapUntil("inbox_leg_apart_$c") { !has("inbox_otherleg_$c") }
        waitFor("inbox_add_$c"); assertTrue(!has("inbox_join_$c") && has("inbox_kinds_$c"))
    }

    @Test
    fun aCreditThreeHoursLaterIsNotTakenForTheOtherLegOfTheTransfer() {
        val at = t0; debitAddedAsTransfer(at)
        val c = idAt(ingest(inn("rahul@okbank", "2,500.00", acct = "5678"), at + 3 * 3_600_000)); show()
        waitFor("inbox_add_$c")
        assertTrue(!has("inbox_otherleg_$c") && !has("inbox_join_$c"), "three hours is past the window and the text does not name the other account")
        assertNotEquals(null, body(c))
    }

    @Test
    fun theAddAllDialogSaysWhatGoesInAndOutAndHowManyAreTransfers() {
        seedRule(3)
        val t = idAt(ingest(out("rahul@okbank", "2,000.00"))); val p = idAt(ingest(out("swiggy@icici", "450.00")))
        show(); waitFor("inbox_pair_$t")
        val dialog = addAllText()
        assertTrue(dialog.contains("₹450.00 out and") && dialog.contains(" in."), dialog)
        assertTrue(dialog.contains("1 is a transfer between your own accounts (₹2,000.00)"), dialog)
        tapUntil("inbox_add_all_ok") { status(t) == InboxStatus.CONFIRMED && status(p) == InboxStatus.CONFIRMED }
        assertEquals(setOf("TRANSFER", "EXPENSE"), entries().map { it.kind }.toSet())
    }
}
