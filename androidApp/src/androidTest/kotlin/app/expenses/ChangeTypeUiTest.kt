package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.Money
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.SettlementDirection
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.OriginKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Change the type" of an entry from its edit form: an expense that was a cash withdrawal, an income that was an expense, and what is refused. */
@RunWith(AndroidJUnit4::class)
class ChangeTypeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = dumpOnTimeout { compose.waitUntil(10_000) { has(tag) } }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); node(tag).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun waitText(t: String) = dumpOnTimeout { compose.waitUntil(10_000) { compose.onAllNodes(hasText(t, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }
    private fun dumpOnTimeout(block: () -> Unit) = try { block() } catch (e: Throwable) { compose.onRoot(useUnmergedTree = true).printToLog("DUMP"); throw e }
    @Suppress("UNCHECKED_CAST") private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) } as T // a result may be null
    private fun liveRows() = db { ok -> ok.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() }
    private fun kinds() = liveRows().map { it.kind }
    private fun balance(id: String) = db { ok -> ok.store.balances().getValue(AccountId(id)).minorUnits }
    private fun spendTotal() = db { ok -> ok.store.spendByCategory(inr).values.sumOf { it.minorUnits } }
    private fun waitKinds(vararg k: String) = dumpOnTimeout { compose.waitUntil(10_000) { kinds() == k.toList() } }
    private fun edit(id: String) { launch(); scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java].openEditor(id) }; waitFor("kind_TRANSFER") }
    private val amount = 200_000L
    private val long get() = System.currentTimeMillis() - 1_000

    private fun accounts() = db { ok ->
        ok.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, 1_000_000, false))
        ok.store.saveAccount(Account(AccountId("savings"), "Savings", AccountType.BANK, inr, 0, false))
    }

    private fun seedExpense(id: String = "e1", title: String = "ATM Indiranagar", account: String = "bank", category: String = "Food", source: String = "manual", ref: String? = null) = db { ok ->
        ok.store.save(Expense(EntryId(id), long, AccountId(account), Money(amount, inr), listOf(CategorySplit(CategoryId(category), Money(amount, inr)))), EntryDetails(merchant = title, note = "for the trip", source = source, sourceRef = ref, tags = listOf("Trip")))
    }

    // ---- the cash withdrawal that was recorded as an expense ----

    @Test
    fun anExpenseBecomesACashWithdrawalToTheOnlyCashAccountAndUndoBringsTheExpenseBack() {
        accounts(); seedExpense(source = "sms", ref = "UPI-55")
        db { ok -> ok.origins.put("e1", OriginKind.SMS, "AX-BANK", "Rs.2,000.00 debited at ATM Indiranagar", 900) }
        assertEquals(800_000L, balance("bank"), "set-up check: the opening 10,000.00 less the expense")
        val spendBefore = spendTotal(); assertEquals(amount, spendBefore)
        edit("e1")
        assertFalse(has("kind_change_note"), "no note while the type is as saved")
        tap("kind_TRANSFER")
        waitFor("kind_change_note")
        node("kind_change_note").assertTextContains("Saving changes this expense into a transfer. You can undo it right after.", substring = true)
        node("kind_change_note").assertTextContains("Cash is chosen as the account the money went to", substring = true)
        node("to_chip_Cash").assertIsSelected()
        compose.onNodeWithTag("save").assertIsEnabled()
        tap("save"); waitFor("add")
        waitKinds("TRANSFER")
        val t = liveRows().single()
        assertEquals("bank", t.account_id); assertEquals("cash", t.counter_account_id); assertEquals(amount, t.amount_minor)
        assertEquals("ATM Indiranagar", t.merchant); assertEquals("for the trip", t.note); assertEquals("sms", t.source); assertEquals("UPI-55", t.source_ref)
        assertEquals(800_000L, balance("bank")); assertEquals(amount, balance("cash")); assertEquals(0L, spendTotal(), "a cash withdrawal is not spending")
        assertEquals(listOf("Trip"), db { ok -> ok.store.tagsOf(EntryId(t.id)) }); assertEquals("AX-BANK", db { ok -> ok.origins.get(t.id)?.sender })
        waitText("Changed to a transfer")
        compose.onNodeWithText("Undo").performClick()
        waitKinds("EXPENSE")
        assertEquals("e1", liveRows().single().id); assertEquals(800_000L, balance("bank")); assertEquals(0L, balance("cash")); assertEquals(amount, spendTotal())
        assertEquals("UPI-55", liveRows().single().source_ref); assertEquals("AX-BANK", db { ok -> ok.origins.get("e1")?.sender })
        waitText("Indiranagar")
    }

    @Test
    fun pickingAnotherToAccountDropsTheCashSentenceAndTheTransferGoesThere() {
        accounts(); seedExpense()
        edit("e1"); tap("kind_TRANSFER"); waitFor("kind_change_note")
        node("kind_change_note").assertTextContains("Cash is chosen", substring = true)
        tap("to_chip_Savings")
        compose.waitUntil(10_000) { !compose.onAllNodes(hasText("Cash is chosen", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        node("kind_change_note").assertTextContains("Saving changes this expense into a transfer.", substring = true)
        tap("save"); waitFor("add"); waitKinds("TRANSFER")
        assertEquals("savings", liveRows().single().counter_account_id); assertEquals(amount, balance("savings")); assertEquals(0L, balance("cash"))
    }

    @Test
    fun withTwoCashAccountsNoCashSentenceIsShown() {
        accounts(); db { ok -> ok.store.saveAccount(Account(AccountId("wallet"), "Wallet", AccountType.CASH, inr, 0, false)) }
        seedExpense()
        edit("e1"); tap("kind_TRANSFER"); waitFor("kind_change_note")
        assertFalse(compose.onAllNodes(hasText("cash withdrawal", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun anExpenseOnTheCashAccountItselfGetsNoCashPreselection() {
        accounts(); seedExpense(account = "cash")
        edit("e1"); tap("kind_TRANSFER"); waitFor("kind_change_note")
        assertFalse(compose.onAllNodes(hasText("cash withdrawal", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        node("to_chip_Cash").assertIsNotSelected()
    }

    // ---- other pairs ----

    @Test
    fun anIncomeBecomesAnExpenseWithACategoryAndUndoRestoresTheIncome() {
        accounts()
        db { ok -> ok.store.save(Income(EntryId("i1"), long, AccountId("bank"), Money(amount, inr), CategoryId("income:salary")), EntryDetails(merchant = "Acme", note = "oct")) }
        assertEquals(1_200_000L, balance("bank")); assertEquals(0L, spendTotal())
        edit("i1"); tap("kind_EXPENSE"); waitFor("kind_change_note")
        node("kind_change_note").assertTextContains("Saving changes this income into an expense.", substring = true)
        assertFalse(has("expect_toggle"), "'I expect this back' is for an expense that already exists")
        tap("category_chip_Food")
        tap("save"); waitFor("add"); waitKinds("EXPENSE")
        assertEquals(amount, spendTotal()); assertEquals(800_000L, balance("bank")); assertEquals("Acme", liveRows().single().merchant); assertEquals("oct", liveRows().single().note)
        waitText("Changed to an expense")
        compose.onNodeWithText("Undo").performClick()
        waitKinds("INCOME")
        assertEquals("i1", liveRows().single().id); assertEquals(0L, spendTotal()); assertEquals(1_200_000L, balance("bank"))
    }

    @Test
    fun anIncomeBecomesATransferIntoItsOwnAccount() {
        accounts()
        db { ok -> ok.store.save(Income(EntryId("i1"), long, AccountId("bank"), Money(amount, inr), CategoryId("income:salary")), EntryDetails(merchant = "From savings")) }
        edit("i1"); tap("kind_TRANSFER"); waitFor("kind_change_note")
        // the money arrived in the bank: the bank is the "To", and the person picks where it came from
        node("to_chip_Bank").assertIsSelected()
        tap("account_chip_Savings")
        tap("save"); waitFor("add"); waitKinds("TRANSFER")
        assertEquals(1_200_000L, balance("bank"), "the bank still gained it"); assertEquals(-amount, balance("savings"))
    }

    @Test
    fun aTransferBecomesAnExpenseAndAnIncomeAndFlippingThroughTheChipsLosesNothing() {
        accounts()
        db { ok -> ok.store.save(app.expenses.core.domain.ledger.Transfer(EntryId("t1"), long, AccountId("bank"), AccountId("savings"), Money(amount, inr)), EntryDetails(merchant = "Moved")) }
        edit("t1"); tap("kind_EXPENSE"); tap("kind_INCOME"); tap("kind_TRANSFER")
        node("to_chip_Savings").assertIsSelected(); node("account_chip_Bank").assertIsSelected()
        waitGone("kind_change_note")
        tap("kind_EXPENSE"); tap("category_chip_Bills")
        tap("save"); waitFor("add"); waitKinds("EXPENSE")
        assertEquals(amount, spendTotal()); assertEquals(800_000L, balance("bank")); assertEquals(0L, balance("savings"))
        compose.onNodeWithText("Undo").performClick()
        waitKinds("TRANSFER")
        assertEquals(800_000L, balance("bank")); assertEquals(amount, balance("savings")); assertEquals("t1", liveRows().single().id)
    }

    @Test
    fun aTransferBecomesIncomeInItsToAccount() {
        accounts()
        db { ok -> ok.store.save(app.expenses.core.domain.ledger.Transfer(EntryId("t1"), long, AccountId("bank"), AccountId("savings"), Money(amount, inr)), EntryDetails(merchant = "Moved")) }
        edit("t1"); tap("kind_INCOME"); waitFor("account_chip_Savings")
        node("account_chip_Savings").assertIsSelected()
        tap("save"); waitFor("add"); waitKinds("INCOME")
        assertEquals(1_000_000L, balance("bank")); assertEquals(amount, balance("savings"))
    }

    // ---- nothing is lost by looking ----

    @Test
    fun flippingAnExpenseThroughTheChipsAndBackKeepsItsCategoryAndASplitExpenseKeepsItsParts() {
        accounts(); seedExpense()
        db { ok -> ok.store.save(Expense(EntryId("sp"), long - 5_000, AccountId("bank"), Money(amount, inr), listOf(CategorySplit(CategoryId("Food"), Money(150_000, inr)), CategorySplit(CategoryId("Bills"), Money(50_000, inr)))), EntryDetails(merchant = "Mall")) }
        edit("e1"); tap("kind_INCOME"); tap("kind_EXPENSE"); waitGone("kind_change_note")
        node("category_chip_Food").assertIsSelected()
        tap("cancel"); waitFor("add")
        edit("sp"); tap("kind_TRANSFER"); waitFor("kind_change_note")
        node("kind_change_note").assertTextContains("The category split will be removed.", substring = true)
        tap("kind_EXPENSE"); waitGone("kind_change_note"); waitFor("split_amount_0")
        tap("save"); waitFor("add")
        // saved as an expense again: the ordinary edit, with both parts
        assertEquals(setOf("e1", "sp"), liveRows().map { it.id }.toSet())
        assertEquals(listOf("Food" to 150_000L, "Bills" to 50_000L), db { ok -> ok.db.schemaQueries.splitsOf("sp").executeAsList().map { it.category_id to it.amount_minor } })
    }

    @Test
    fun theNoteAppearsOnlyWhileTheTypeIsNotTheSavedOneAndTheAddFormIsUnchanged() {
        accounts(); seedExpense()
        edit("e1"); node("kind_EXPENSE").assertIsSelected(); assertFalse(has("kind_change_note"))
        tap("kind_INCOME"); waitFor("kind_change_note"); node("kind_change_note").assertTextContains("Saving changes this expense into income.", substring = true)
        tap("kind_EXPENSE"); waitGone("kind_change_note")
        tap("cancel"); waitFor("add")
        tap("add"); waitFor("kind_EXPENSE")
        assertTrue(has("kind_INCOME") && has("kind_TRANSFER") && has("kind_LOAN")); assertFalse(has("kind_REFUND")); assertFalse(has("kind_change_note"))
        tap("kind_TRANSFER"); assertFalse(has("kind_change_note")); assertFalse(has("kind_change_problem"))
    }

    // ---- refused, with a reason, and nothing changes ----

    @Test
    fun anExpenseExpectedBackIsRefusedInTheFormWithWhatToDoAndChangesNothing() {
        accounts(); seedExpense()
        db { ok -> ok.expects.set("e1", "Raj", null, amount) }
        edit("e1"); node("expect_toggle").assertIsSelected()
        tap("kind_TRANSFER"); waitGone("expect_toggle")
        tap("save"); waitFor("kind_change_problem")
        node("kind_change_problem").assertTextEquals("Turn off \"I expect this back\" first: save the expense with it off, then change its type.")
        assertEquals(listOf("EXPENSE"), kinds()); assertEquals("e1", liveRows().single().id); assertTrue(db { ok -> ok.expects.get("e1") } != null)
        // back to expense: the message goes; switch the expectation off and save as it is; now the type can be changed
        tap("kind_EXPENSE"); waitGone("kind_change_problem"); node("expect_toggle").assertIsSelected()
        tap("expect_toggle"); tap("save"); waitFor("add")
        compose.waitUntil(10_000) { db { ok -> ok.expects.get("e1") } == null }
        edit("e1"); tap("kind_TRANSFER"); tap("save"); waitFor("add"); waitKinds("TRANSFER")
    }

    @Test
    fun refundAndLentOrBorrowedFormsHaveNoTypeChips() {
        accounts(); seedExpense()
        val pid = db { ok -> ok.people.create("Asha").id }
        db { ok ->
            ok.store.save(Refund(EntryId("r1"), long, AccountId("bank"), Money(10_000, inr), EntryId("e1")), EntryDetails(merchant = "Got a refund"))
            ok.store.save(Settlement(EntryId("l1"), long - 9_000, PersonId(pid), AccountId("bank"), Money(50_000, inr), SettlementDirection.PAID_TO_PERSON))
        }
        launch(); scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java].openEditor("r1") }
        waitFor("amount"); assertFalse(has("kind_TRANSFER") || has("kind_EXPENSE") || has("kind_INCOME") || has("kind_REFUND"))
        tap("cancel"); waitFor("add")
        scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java].openEditor("l1") }
        waitFor("loan_gave"); assertFalse(has("kind_TRANSFER") || has("kind_EXPENSE") || has("kind_INCOME") || has("kind_LOAN"))
    }

    @Test
    fun anExpenseWithRefundsStaysReadOnlyWithNoChips() {
        accounts(); seedExpense()
        db { ok -> ok.store.save(Refund(EntryId("r1"), long, AccountId("bank"), Money(10_000, inr), EntryId("e1"))) }
        launch(); scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java].openEditor("e1") }
        waitFor("read_only_reason"); assertFalse(has("kind_TRANSFER"))
    }

    // ---- the action underneath ----

    private fun draftOf(id: String, kind: DraftKind, account: String = "bank", to: String = "", category: String = "", note: String = "ATM Indiranagar", tags: List<String> = listOf("Trip")) =
        EntryDraft(id, kind, Money(amount, inr).toPlainString(), category, emptyList(), account, to, note, long, tags = tags)

    @Test
    fun changingTheTypeLearnsNothingAndRemembersNoLastCategoryOrAccount() {
        accounts()
        // a captured income made an expense is the case a plain save would learn from ("Zomato" is a Food payee from now on)
        db { ok -> ok.store.save(Income(EntryId("i1"), long, AccountId("bank"), Money(amount, inr), CategoryId("income:salary")), EntryDetails(merchant = "Zomato", source = "sms", sourceRef = "R-1")) }
        fun remembered() = db { ok -> listOf(ok.captureRules.learned(), ok.templates.setting("last_category"), ok.templates.setting("last_account"), ok.templates.setting("last_income_category")) }
        val before = remembered()
        db { ok -> EntryActions(ok).changeKind(draftOf("i1", DraftKind.EXPENSE, category = "Food", note = "Zomato")) }
        assertEquals(before, remembered())
        assertEquals(listOf("EXPENSE"), kinds())
    }

    @Test
    fun theActionRefusesWhatCannotChangeAndLeavesTheLedgerAsItWas() {
        accounts(); seedExpense(); db { ok -> ok.store.save(Refund(EntryId("r1"), long, AccountId("bank"), Money(10_000, inr), EntryId("e1"))) }
        seedExpense("e2", title = "Plain")
        db { ok ->
            val a = EntryActions(ok)
            val e = assertFailsWith<IllegalArgumentException> { a.changeKind(draftOf("e1", DraftKind.INCOME, category = "income:salary")) }
            assertEquals("This entry's type can't be changed here.", e.message) // it has refunds: read-only
            assertEquals("Choose another type.", assertFailsWith<IllegalArgumentException> { a.changeKind(draftOf("e2", DraftKind.EXPENSE, category = "Food")) }.message)
            assertEquals("Choose another type.", assertFailsWith<IllegalArgumentException> { a.changeKind(draftOf("e2", DraftKind.LOAN)) }.message)
            assertEquals("Choose two different accounts.", assertFailsWith<IllegalArgumentException> { a.changeKind(draftOf("e2", DraftKind.TRANSFER, to = "bank")) }.message)
            assertEquals("Choose the account the money goes to.", assertFailsWith<IllegalArgumentException> { a.changeKind(draftOf("e2", DraftKind.TRANSFER)) }.message)
            assertEquals("Only an entry that exists can change its type.", assertFailsWith<IllegalArgumentException> { a.changeKind(draftOf("e2", DraftKind.INCOME).copy(id = null)) }.message)
        }
        assertEquals(setOf("e1", "r1", "e2"), liveRows().map { it.id }.toSet()); assertEquals(listOf("EXPENSE", "REFUND", "EXPENSE"), kinds())
    }

    // ---- several at once, from Search ----

    private fun searchBy(text: String, count: Int) {
        tap("open_search"); waitFor("search_text")
        compose.onNodeWithTag("search_text").performTextInput(text)
        waitText("$count results")
    }

    @Test
    fun severalExpensesBecomeCashWithdrawalsFromSearchWithOneUndoAndTheOnesThatCannotAreSkippedWithAReason() {
        accounts()
        seedExpense("a", "ATM Alpha"); seedExpense("b", "ATM Bravo"); seedExpense("c", "ATM Cash one", account = "cash"); seedExpense("d", "ATM Dina")
        db { ok -> ok.expects.set("d", "Raj", null, amount) }
        val before = db { ok -> Triple(ok.store.balances(), ok.store.spendByCategory(inr), ok.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList()) }
        launch(); searchBy("ATM", 4)
        tap("search_select_all"); waitText("4 selected")
        tap("bulk_transfer"); tap("bulk_transfer_to_Cash")
        waitFor("search_notice")
        waitText("Changed 2 expenses to transfers to Cash.")
        waitText("2 skipped")
        assertEquals(listOf("EXPENSE", "EXPENSE", "TRANSFER", "TRANSFER"), kinds().sorted())
        assertEquals(setOf("c", "d"), liveRows().filter { it.kind == "EXPENSE" }.map { it.id }.toSet(), "the cash expense and the one expected back are untouched")
        assertEquals(amount, balance("cash"), "two withdrawals in, and 'c' itself was paid out of cash")
        tap("search_undo")
        compose.waitUntil(10_000) { liveRows().all { it.kind == "EXPENSE" } && liveRows().size == 4 }
        assertEquals(before.first, db { ok -> ok.store.balances() }); assertEquals(before.second, db { ok -> ok.store.spendByCategory(inr) }); assertEquals(before.third, liveRows(), "every row exactly as it was")
    }

    @Test
    fun theBulkActionSkipsIncomeSharedAndOtherAccountCasesAndOneUndoRestoresEverything() {
        accounts(); seedExpense("a", "A"); seedExpense("b", "B")
        db { ok -> ok.store.save(Income(EntryId("i"), long, AccountId("bank"), Money(amount, inr), CategoryId("income:salary")), EntryDetails(merchant = "Pay")) }
        val before = db { ok -> ok.store.balances() }
        val r = db { ok -> EntryActions(ok).bulkMakeTransfer(listOf("a", "b", "i", "nope"), "savings") }
        assertEquals(2, r.changed); assertEquals(2, r.skipped); assertEquals(2, r.undoKinds.size)
        assertTrue(r.why!!.contains("only expenses can be made transfers"), r.why)
        assertEquals(setOf("TRANSFER", "INCOME"), kinds().toSet()); assertEquals(0L, spendTotal()); assertEquals(2 * amount, balance("savings"))
        db { ok -> ok.store.atomically { r.undoKinds.asReversed().forEach { EntryActions(ok).undoKindChange(it) } } }
        assertEquals(listOf("EXPENSE", "EXPENSE", "INCOME"), kinds()); assertEquals(before, db { ok -> ok.store.balances() })
        assertEquals(setOf("a", "b", "i"), liveRows().map { it.id }.toSet())
    }

    @Test
    fun aRefundedExpenseIsSkippedForItsOwnReasonAndWhenNothingChangesHomeSaysWhy() {
        accounts(); seedExpense("r", "Shoes")
        db { ok -> ok.store.save(Refund(EntryId("rf"), long, AccountId("bank"), Money(10_000, inr), EntryId("r")), EntryDetails(merchant = "Shoe return")) }
        val r = db { ok -> EntryActions(ok).bulkMakeTransfer(listOf("r"), "savings") }
        assertEquals(0, r.changed); assertEquals(1, r.skipped); assertEquals("this expense has refunds, so its type can't be changed here", r.why, "it is an expense: not 'only expenses can be made transfers'")
        // from Home: nothing changes, no Undo bar, so the message bar says why
        launch(); compose.onNodeWithTag("row_Shoes", useUnmergedTree = true).performTouchInput { longClick() }; waitText("1 selected")
        tap("bulk_transfer"); tap("bulk_transfer_to_Savings")
        waitText("Nothing changed. 1 skipped: this expense has refunds, so its type can't be changed here.")
        assertEquals(setOf("r", "rf"), liveRows().map { it.id }.toSet()); assertEquals("EXPENSE", liveRows().first { it.id == "r" }.kind)
    }

    @Test
    fun undoingAnEarlierChangeAfterALaterOneIsRefusedWithAMessageNotACrash() {
        accounts(); seedExpense(source = "sms", ref = "UPI-3")
        val first = db { ok -> EntryActions(ok).changeKind(draftOf("e1", DraftKind.TRANSFER, to = "savings")) }
        db { ok -> EntryActions(ok).changeKind(draftOf(first.newId, DraftKind.INCOME, category = "income:salary")) }
        val e = assertFailsWith<IllegalArgumentException> { db { ok -> EntryActions(ok).undoKindChange(first) } }
        assertEquals("This change can't be undone any more.", e.message)
    }
}
