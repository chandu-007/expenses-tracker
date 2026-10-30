package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.OriginKind
import app.expenses.ui.WideLayoutTestHook
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Transfer candidates": old entries that are really money moving between the person's own accounts, found for the person to convert in one step with one Undo. */
@RunWith(AndroidJUnit4::class)
class TransferCandidatesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR
    private val day = 86_400_000L

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); WideLayoutTestHook.widthDp = null; resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); node(tag).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun textOf(tag: String): String = node(tag).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString(" ") { it.text }
    private fun shown(text: String) = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun back() { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
    @Suppress("UNCHECKED_CAST") private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) } as T
    private fun kinds() = db { ok -> ok.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.id to it.kind }.toMap() }
    private fun balance(id: String) = db { ok -> ok.store.balances().getValue(AccountId(id)).minorUnits }
    private fun spendTotal() = db { ok -> ok.store.spendByCategory(inr).values.sumOf { it.minorUnits } }
    private fun incomeTotal() = db { ok -> ok.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().filter { it.kind == "INCOME" }.sumOf { it.amount_minor } }

    private fun openScreen() { compose.openMore(); tap("open_transfer_candidates"); waitFor("transfer_candidates_list"); compose.waitUntil(10_000) { !has("transfer_candidates_loading") } }

    private fun expense(id: String, title: String, amount: Long = 200_000, account: String = "bank", agoDays: Int = 3, minutesLater: Long = 0, note: String? = null, origin: String? = null, tags: List<String> = emptyList()) = db { ok ->
        val at = System.currentTimeMillis() - agoDays * day + minutesLater * 60_000
        ok.store.save(Expense(EntryId(id), at, AccountId(account), Money(amount, inr), listOf(CategorySplit(CategoryId("Food"), Money(amount, inr)))), EntryDetails(merchant = title, note = note, source = if (origin != null) "sms" else "manual", sourceRef = if (origin != null) "REF-$id" else null, tags = tags))
        if (origin != null) ok.origins.put(id, OriginKind.SMS, "AX-BANK", origin, at)
    }

    private fun income(id: String, title: String, amount: Long = 200_000, account: String = "savings", agoDays: Int = 3, minutesLater: Long = 0, tags: List<String> = emptyList(), note: String? = null, ref: String? = null) = db { ok ->
        ok.store.save(Income(EntryId(id), System.currentTimeMillis() - agoDays * day + minutesLater * 60_000, AccountId(account), Money(amount, inr), CategoryId("income:salary")), EntryDetails(merchant = title, note = note, tags = tags, source = if (ref != null) "sms" else "manual", sourceRef = ref))
    }

    private fun savings() = db { ok -> ok.store.saveAccount(Account(AccountId("savings"), "Savings", AccountType.BANK, inr, 0, false)) }
    private fun tapUndo() { compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }; compose.onAllNodes(hasText("Undo"), useUnmergedTree = true)[0].performClick() }

    private fun seedWithdrawals() {
        savings()
        expense("w1", "ATM cash", 300_000, agoDays = 5, origin = "Rs.3,000.00 withdrawn at ATM 12345 on 05-10-26 from A/c XX1234")
        expense("w2", "Bank", 500_000, agoDays = 4, note = "ATM WDL")
        expense("atmos", "ATMOS cafe", 150_000, agoDays = 3)
        expense("batman", "Batman store", 90_000, agoDays = 2)
        expense("lunch", "Lunch", 25_000, agoDays = 1)
    }

    @Test
    fun theScreenShowsTheCashWithdrawalsPreselectedForTheOnlyCashAccountAndNothingElse() {
        seedWithdrawals(); launch(); openScreen()
        waitFor("transfer_candidates_group_cash")
        assertTrue(!has("transfer_candidates_group_own") && !has("transfer_candidates_group_self"))
        assertTrue(!has("transfer_candidates_row_atmos") && !has("transfer_candidates_row_batman") && !has("transfer_candidates_row_lunch"), "false friends and ordinary spending are not suggested")
        for (id in listOf("w1", "w2")) { waitFor("transfer_candidates_row_$id"); node("transfer_candidates_row_$id").assertIsOn() }
        assertTrue(textOf("transfer_candidates_route_w1").contains("Bank → Cash"), textOf("transfer_candidates_route_w1"))
        assertEquals("₹3,000.00", textOf("transfer_candidates_amount_w1"))
        node("transfer_candidates_make_cash").assertIsEnabled(); assertTrue(shown("Make 2 transfers"))
        // nothing has been changed by looking
        assertEquals(setOf("EXPENSE"), kinds().values.toSet())
    }

    @Test
    fun makingTransfersMovesTheMoneyToCashChangesTheTotalsAndOneUndoPutsEverythingBack() {
        seedWithdrawals(); launch(); openScreen(); waitFor("transfer_candidates_row_w1")
        val bankBefore = balance("bank"); val cashBefore = balance("cash"); val spendBefore = spendTotal()
        tap("transfer_candidates_make_cash")
        compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertEquals("Made 2 transfers.", textOf("transfer_candidates_notice"))
        waitGone("transfer_candidates_group_cash"); waitFor("transfer_candidates_empty")
        assertEquals(2, kinds().values.count { it == "TRANSFER" })
        assertEquals(bankBefore, balance("bank"), "the money still left the bank account"); assertEquals(cashBefore + 800_000, balance("cash"), "and it is in Cash now")
        assertEquals(spendBefore - 800_000, spendTotal(), "it is no longer spending")
        tapUndo()
        waitFor("transfer_candidates_row_w1"); waitFor("transfer_candidates_row_w2")
        assertEquals(setOf("EXPENSE"), kinds().values.toSet()); assertEquals(5, kinds().size)
        assertEquals(bankBefore, balance("bank")); assertEquals(cashBefore, balance("cash")); assertEquals(spendBefore, spendTotal())
        // Home shows the entries again too
        assertEquals(setOf("w1", "w2", "atmos", "batman", "lunch"), kinds().keys)
    }

    @Test
    fun aRowCanBeUntickedAndOnlyTheTickedOnesAreChanged() {
        seedWithdrawals(); launch(); openScreen(); waitFor("transfer_candidates_row_w2")
        tap("transfer_candidates_row_w2"); node("transfer_candidates_row_w2").assertIsOff()
        compose.waitUntil(10_000) { shown("Make 1 transfer") }
        tap("transfer_candidates_make_cash"); compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertEquals("TRANSFER", kinds().entries.single { it.key != "w2" && it.value == "TRANSFER" }.value)
        assertEquals("EXPENSE", kinds().getValue("w2")); waitFor("transfer_candidates_row_w2")
    }

    @Test
    fun notATransferHidesTheRowAndItStaysHiddenAfterReopeningUntilBroughtBack() {
        seedWithdrawals(); launch(); openScreen(); waitFor("transfer_candidates_row_w1")
        tap("transfer_candidates_not_w1")
        waitGone("transfer_candidates_row_w1"); waitFor("transfer_candidates_row_w2")
        assertTrue(textOf("transfer_candidates_notice").startsWith("Marked 1 as not a transfer"), textOf("transfer_candidates_notice"))
        assertEquals("EXPENSE", kinds().getValue("w1"), "the entry itself is untouched")
        // leave and come back, and even reopen the app
        back(); compose.waitUntil(10_000) { !has("transfer_candidates_list") }
        launch(); openScreen(); waitFor("transfer_candidates_row_w2"); assertTrue(!has("transfer_candidates_row_w1"), "still hidden")
        tap("transfer_candidates_show_dismissed"); waitFor("transfer_candidates_row_w1")
    }

    @Test
    fun notATransferForTheTickedRowsDismissesThemTogether() {
        seedWithdrawals(); launch(); openScreen(); waitFor("transfer_candidates_row_w1")
        tap("transfer_candidates_not_selected_cash")
        waitFor("transfer_candidates_empty"); assertTrue(!has("transfer_candidates_row_w1") && !has("transfer_candidates_row_w2"))
    }

    @Test
    fun withNothingToSuggestThePageSaysSo() {
        expense("a", "Lunch", 25_000); expense("b", "ATMOS cafe", 90_000)
        launch(); openScreen(); waitFor("transfer_candidates_empty"); assertTrue(!has("transfer_candidates_group_cash"))
    }

    @Test
    fun withTwoCashAccountsNothingIsPreselectedAndTheDestinationIsChosenOnce() {
        seedWithdrawals()
        db { ok -> ok.store.saveAccount(Account(AccountId("tin"), "Cash tin", AccountType.CASH, inr, 0, false)) }
        launch(); openScreen(); waitFor("transfer_candidates_row_w1")
        node("transfer_candidates_row_w1").assertIsOff(); node("transfer_candidates_row_w2").assertIsOff()
        node("transfer_candidates_make_cash").assertIsNotEnabled()
        tap("transfer_candidates_row_w1")
        node("transfer_candidates_make_cash").assertIsNotEnabled() // ticked, but no account to move the money to has been chosen
        tap("transfer_candidates_dest_cash"); tap("transfer_candidates_to_cash_Cash tin")
        node("transfer_candidates_make_cash").assertIsEnabled(); assertTrue(textOf("transfer_candidates_route_w1").contains("Bank → Cash tin"), textOf("transfer_candidates_route_w1"))
        tap("transfer_candidates_make_cash"); compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertEquals("TRANSFER", kinds().entries.single { it.key !in setOf("w1", "w2", "atmos", "batman", "lunch") }.value)
        assertEquals(300_000L, balance("tin"), "the money went to the chosen account")
        assertEquals(0L, balance("cash"))
    }

    @Test
    fun withNoCashAccountTheScreenOffersToCreateOneOnlyAfterConfirmation() {
        seedWithdrawals()
        db { ok -> ok.store.deleteAccount(AccountId("cash")) }
        launch(); openScreen(); waitFor("transfer_candidates_row_w1")
        node("transfer_candidates_row_w1").assertIsOff()
        tap("transfer_candidates_create_cash"); waitFor("transfer_candidates_create_cash_confirm")
        tap("transfer_candidates_create_cash_cancel"); waitGone("transfer_candidates_create_cash_confirm")
        assertTrue(db { ok -> ok.db.schemaQueries.selectAccounts().executeAsList().none { it.type == "CASH" } }, "not created until confirmed")
        tap("transfer_candidates_create_cash"); tap("transfer_candidates_create_cash_confirm")
        compose.waitUntil(10_000) { !has("transfer_candidates_create_cash") && has("transfer_candidates_row_w1") }
        assertEquals(listOf("Cash"), db { ok -> ok.db.schemaQueries.selectAccounts().executeAsList().filter { it.type == "CASH" }.map { it.name } })
        // with exactly one Cash account the withdrawals are ticked, as they are for anyone who has one
        node("transfer_candidates_row_w1").assertIsOn()
    }

    @Test
    fun anExpenseAndAnIncomeOfTheSameAmountMinutesApartBecomeOneTransferAndUndoBringsBothBack() {
        savings()
        expense("x", "Funds transfer to savings", 1_200_000, agoDays = 2)
        income("i", "Transfer received", 1_200_000, agoDays = 2, minutesLater = 2)
        launch(); openScreen(); waitFor("transfer_candidates_group_own")
        node("transfer_candidates_row_x").assertIsOff() // moves between accounts are never preselected
        assertTrue(textOf("transfer_candidates_route_x").contains("Bank → Savings"), textOf("transfer_candidates_route_x"))
        node("transfer_candidates_make_own").assertIsNotEnabled()
        val bank = balance("bank"); val savings = balance("savings"); val spend = spendTotal(); val income = incomeTotal()
        tap("transfer_candidates_row_x"); tap("transfer_candidates_make_own")
        compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertEquals("Made 1 transfer.", textOf("transfer_candidates_notice"))
        assertEquals(listOf("TRANSFER"), kinds().values.toList(), "one transfer replaces both")
        assertEquals(bank, balance("bank")); assertEquals(savings, balance("savings"))
        assertEquals(spend - 1_200_000, spendTotal()); assertEquals(income - 1_200_000, incomeTotal())
        tapUndo(); waitFor("transfer_candidates_row_x")
        assertEquals(mapOf("x" to "EXPENSE", "i" to "INCOME"), kinds())
        assertEquals(bank, balance("bank")); assertEquals(savings, balance("savings")); assertEquals(spend, spendTotal()); assertEquals(income, incomeTotal())
    }

    @Test
    fun sameAmountPairsWithoutTransferWordsOrOnDifferentDaysAreNotOffered() {
        savings()
        expense("a", "Ravi", 700_000, agoDays = 3); income("b", "Anita", 700_000, agoDays = 3, minutesLater = 3)
        expense("c", "Transfer to savings", 900_000, agoDays = 6); income("d", "Transfer received", 900_000, agoDays = 5)
        launch(); openScreen(); waitFor("transfer_candidates_empty")
    }

    @Test
    fun aSelfTransferNeedsAnAccountToBeChosenAndIsNotPreselected() {
        savings()
        expense("s", "Self transfer", 450_000)
        income("t", "From self", 120_000, account = "bank")
        launch(); openScreen(); waitFor("transfer_candidates_group_self")
        node("transfer_candidates_row_s").assertIsOff(); node("transfer_candidates_row_t").assertIsOff()
        tap("transfer_candidates_row_s"); tap("transfer_candidates_dest_self"); tap("transfer_candidates_to_self_Savings")
        assertTrue(textOf("transfer_candidates_route_s").contains("Bank → Savings"))
        tap("transfer_candidates_make_self"); compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertEquals(1, kinds().values.count { it == "TRANSFER" }); assertTrue(!kinds().containsKey("s"), "the expense was replaced by a transfer")
        assertEquals("INCOME", kinds().getValue("t"), "the one not ticked is untouched")
    }

    @Test
    fun whatCannotBeChangedAnyMoreIsSkippedAndTheNoticeSaysHowManyAndWhy() {
        seedWithdrawals(); launch(); openScreen(); waitFor("transfer_candidates_row_w1")
        // between reading the ledger and pressing the button, one of them gets an "I expect this back" (the screen's list is a moment old)
        db { ok -> ok.expects.set("w2", "Ravi", null, 500_000) }
        tap("transfer_candidates_make_cash"); compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        val notice = textOf("transfer_candidates_notice")
        assertTrue(notice.startsWith("Made 1 transfer, 1 skipped: "), notice)
        assertTrue(notice.contains("I expect this back"), notice)
        assertEquals("EXPENSE", kinds().getValue("w2"))
    }

    @Test
    fun onAWideWindowTheScreenIsOnePaneLikeItsSiblings() {
        seedWithdrawals(); WideLayoutTestHook.widthDp = 1000
        launch(); openScreen(); waitFor("transfer_candidates_row_w1")
        assertTrue(!has("wide_placeholder"), "a sub-screen is one pane, with no entry placeholder beside it")
        back(); compose.waitUntil(10_000) { !has("transfer_candidates_list") }
    }

    @Test
    fun aWithdrawalWhoseReversalIsAlreadyInTheLedgerIsSkippedAtTheButtonEvenIfItAppearedAfterTheListWasRead() {
        seedWithdrawals(); launch(); openScreen(); waitFor("transfer_candidates_row_w1")
        // the bank's reversal of the first withdrawal is captured while the page is open
        income("rev", "Reversal", 300_000, account = "bank", agoDays = 5, minutesLater = 30, ref = "REF-w1/reversal")
        val cashBefore = balance("cash")
        tap("transfer_candidates_make_cash"); compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertEquals("Made 1 transfer, 1 skipped: its matching money-in is already in your ledger.", textOf("transfer_candidates_notice"))
        assertEquals("EXPENSE", kinds().getValue("w1")); assertEquals("INCOME", kinds().getValue("rev"))
        assertEquals(cashBefore + 500_000, balance("cash"), "only the withdrawal that was not reversed moved money to Cash")
    }

    @Test
    fun aSelfTransferWhoseCreditIsAlreadyOnTheOtherAccountIsSkippedAndOneThatWasNeverCapturedIsStillOffered() {
        savings()
        expense("s", "Self transfer", 450_000, agoDays = 2)
        launch(); openScreen(); waitFor("transfer_candidates_row_s")
        income("credit", "Credit", 450_000, account = "savings", agoDays = 2, minutesLater = 90)
        tap("transfer_candidates_row_s"); tap("transfer_candidates_dest_self"); tap("transfer_candidates_to_self_Savings"); tap("transfer_candidates_make_self")
        compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertTrue(textOf("transfer_candidates_notice").startsWith("Made no transfers, 1 skipped: its matching money-in is already in your ledger"), textOf("transfer_candidates_notice"))
        assertEquals(mapOf("s" to "EXPENSE", "credit" to "INCOME"), kinds())
        assertTrue(!has("transfer_candidates_row_s"), "and the page, reading the ledger anew, no longer offers it")
    }

    @Test
    fun thePairsIncomeLeavesItsTagsAndNoteOnTheTransferAndUndoRestoresBothEntriesExactly() {
        savings()
        expense("x", "Funds transfer to savings", 1_200_000, agoDays = 2, tags = listOf("Trip", "Rent"))
        income("i", "Transfer received", 1_200_000, agoDays = 2, minutesLater = 2, tags = listOf("rent", "Savings goal"), note = "for the flat")
        launch(); openScreen(); waitFor("transfer_candidates_row_x")
        fun tagsOf(id: String) = db { ok -> ok.store.tagsOf(EntryId(id)).sorted() }
        val xBefore = tagsOf("x"); val iBefore = tagsOf("i")
        tap("transfer_candidates_row_x"); tap("transfer_candidates_make_own"); compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        val t = kinds().entries.single { it.value == "TRANSFER" }.key
        assertEquals(listOf("Rent", "Savings goal", "Trip"), tagsOf(t), "the union, each tag once whatever its capitals")
        assertEquals("for the flat", db { ok -> ok.db.schemaQueries.entryById(t).executeAsOne().note }, "the transfer had no note of its own")
        tapUndo(); waitFor("transfer_candidates_row_x")
        assertEquals(xBefore, tagsOf("x")); assertEquals(iBefore, tagsOf("i"))
        assertEquals("for the flat", db { ok -> ok.db.schemaQueries.entryById("i").executeAsOne().note })
        assertEquals(mapOf("x" to "EXPENSE", "i" to "INCOME"), kinds())
    }

    @Test
    fun aPairWhoseIncomeGotAnAttachmentAfterTheListWasReadIsSkipped() {
        savings()
        expense("x", "Funds transfer to savings", 1_200_000, agoDays = 2); income("i", "Transfer received", 1_200_000, agoDays = 2, minutesLater = 2)
        launch(); openScreen(); waitFor("transfer_candidates_row_x")
        db { ok -> ok.attachments.add("photo", "i", "slip.jpg", "image/jpeg", 100, "h") }
        tap("transfer_candidates_row_x"); tap("transfer_candidates_make_own"); compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertTrue(textOf("transfer_candidates_notice").contains("attachments"), textOf("transfer_candidates_notice"))
        assertEquals(mapOf("x" to "EXPENSE", "i" to "INCOME"), kinds())
    }

    @Test
    fun theUndoBarSurvivesNotATransferAndASecondBatchIsAddedToIt() {
        seedWithdrawals()
        expense("w3", "ATM cash", 100_000, agoDays = 6)
        launch(); openScreen(); waitFor("transfer_candidates_row_w3")
        // make one, then answer "Not a transfer" for another: the bar of the first stays and its Undo still works
        tap("transfer_candidates_row_w2"); tap("transfer_candidates_row_w3") // untick two, leaving w1
        tap("transfer_candidates_make_cash"); compose.waitUntil(10_000) { has("transfer_candidates_notice") }
        assertEquals(1, kinds().values.count { it == "TRANSFER" })
        waitFor("transfer_candidates_row_w2"); tap("transfer_candidates_not_w3"); waitGone("transfer_candidates_row_w3")
        assertTrue(compose.onAllNodes(hasText("Undo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the bar is still there after Not a transfer")
        // a second batch is added to the first and says so
        tap("transfer_candidates_row_w2"); tap("transfer_candidates_make_cash")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Undo puts back all 2 made so far", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, kinds().values.count { it == "TRANSFER" })
        tapUndo()
        waitFor("transfer_candidates_row_w1"); waitFor("transfer_candidates_row_w2")
        assertEquals(setOf("EXPENSE"), kinds().values.toSet())
    }
}
