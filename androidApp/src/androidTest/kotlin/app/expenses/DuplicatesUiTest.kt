package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DuplicatesUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val zone = ZoneId.systemDefault()
    private fun at(y: Int, m: Int, d: Int, h: Int = 14) = LocalDate.of(y, m, d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val oct5 = at(2026, 10, 5)
    private fun debit(ref: String, amount: String = "1,250.00", vpa: String = "shop@okhdfc") = "Rs.$amount debited from A/c XX1234 on 05-10-26 to VPA $vpa (UPI Ref $ref). Not you? Call 18002586161"
    private fun credit(ref: String) = "Rs.1,250.00 credited to A/c XX1234 on 05-10-26 by VPA ravi@ybl (UPI Ref $ref)."
    private fun ingest(sender: String, body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, sender, body, at) }
    private fun save(block: (AppContainer.Opened.Ok) -> Unit) = runBlocking { container.write { block(it) } }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" }
    private fun openInbox() { tap("more_menu"); tap("open_inbox"); waitFor("inbox_back") }
    private fun ids() = runBlocking { container.write { it.inbox.list().associate { i -> i.receivedAt to i.id } } }!!
    private fun entryCount() = runBlocking { container.write { it.db.schemaQueries.recentEntries(50).executeAsList().size } }!!

    @Test
    fun aDebitMatchingAnEntryAddedByHandOnTheSameDayIsFlaggedAndSameOneSkipsItWithoutAddingAnything() {
        save { it.store.save(Expense(EntryId("mine"), oct5, AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "Cafe")) }
        ingest("AX-HDFCBK-S", debit("111111111111"), 5_000)
        launch(); openInbox(); val id = ids().getValue(5_000); waitFor("inbox_duplicate_$id")
        assertEquals("Looks like one you already added: Cafe on 5 Oct", textOf("inbox_duplicate_$id"))
        tap("inbox_same_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        assertEquals(1, entryCount())
    }

    @Test
    fun theNextDayCountsButTwoDaysApartAnAmountThatDiffersOrAnotherKindDoNot() {
        save {
            it.store.save(Expense(EntryId("next"), at(2026, 10, 6), AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "NextDay"))
            it.store.save(Expense(EntryId("far"), at(2026, 10, 8), AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "TwoDaysOn"))
            it.store.save(Expense(EntryId("amt"), oct5, AccountId("bank"), Money(125_001, Currency.INR)), EntryDetails(merchant = "Different amount"))
            it.store.save(Income(EntryId("inc"), oct5, AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "Salary"))
        }
        ingest("AX-HDFCBK-S", debit("222222222222"), 5_000)
        launch(); openInbox(); val id = ids().getValue(5_000); waitFor("inbox_duplicate_$id")
        assertEquals("Looks like one you already added: NextDay on 6 Oct", textOf("inbox_duplicate_$id"), "the next day is near enough; the income of the same amount is not a debit")
    }

    @Test
    fun theSameBankReferenceIsSaidToBeAlreadyInTheLedgerEvenOnAnotherDate() {
        save { it.store.save(Expense(EntryId("old"), at(2026, 9, 1), AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "Earlier", source = "sms", sourceRef = "333333333333")) }
        ingest("AX-HDFCBK-S", debit("333333333333"), 5_000)
        launch(); openInbox(); val id = ids().getValue(5_000); waitFor("inbox_duplicate_$id")
        assertEquals("Already in your ledger: Earlier on 1 Sep", textOf("inbox_duplicate_$id"))
    }

    @Test
    fun aCreditMatchesIncomeAndATransferInButNotAnExpense() {
        save {
            it.store.save(Expense(EntryId("exp"), oct5, AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "NotThis"))
            it.store.save(Transfer(EntryId("tin"), oct5, AccountId("cash"), AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "Own transfer"))
        }
        ingest("AX-HDFCBK-S", credit("444444444444"), 5_000)
        launch(); openInbox(); val id = ids().getValue(5_000); waitFor("inbox_duplicate_$id")
        assertEquals("Looks like one you already added: Own transfer on 5 Oct", textOf("inbox_duplicate_$id"))
    }

    @Test
    fun twoTextsAboutOnePaymentFromTwoBanksAreMarkedAndAnAnotherPaymentIsNot() {
        ingest("AX-HDFCBK-S", debit("555555555555"), 1_000_000)
        ingest("VM-ICICIB-S", "Rs.1,250.00 debited from A/c XX7777 on 05-10-26 to shop@okhdfc.", 1_000_000 + 4 * 60_000)
        ingest("AX-HDFCBK-S", debit("666666666666", vpa = "other@ybl"), 1_000_000 + 60 * 60_000) // an hour later with its own reference: a second payment
        launch(); openInbox(); val byTime = ids()
        val first = byTime.getValue(1_000_000); val second = byTime.getValue(1_000_000 + 4 * 60_000); val third = byTime.getValue(1_000_000 + 60 * 60_000)
        waitFor("inbox_duplicate_$second")
        assertTrue(textOf("inbox_duplicate_$second").contains("same payment as the text from HDFC Bank"))
        assertTrue(!has("inbox_duplicate_$first") && !has("inbox_duplicate_$third"))
    }

    @Test
    fun aReferenceSharedWithAnEntryOfAnotherAmountIsNotTheSameTransaction() {
        save { it.store.save(Expense(EntryId("other"), at(2026, 9, 1), AccountId("bank"), Money(99_900, Currency.INR)), EntryDetails(merchant = "Other", source = "sms", sourceRef = "777777777777")) }
        ingest("AX-HDFCBK-S", debit("777777777777"), 5_000)
        launch(); openInbox(); val id = ids().getValue(5_000); waitFor("inbox_$id")
        assertTrue(!has("inbox_duplicate_$id"))
    }

    @Test
    fun aCreditCarryingTheReferenceOfTheDebitItUndoesIsNotCalledADuplicateAndCanBeAdded() {
        save { it.store.save(Expense(EntryId("pay"), oct5, AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "Shop", source = "sms", sourceRef = "888888888888")) }
        ingest("AX-HDFCBK-S", "Rs.1,250.00 credited to A/c XX1234 on 05-10-26 as reversal of UPI Ref 888888888888 (failed payment).", 5_000)
        launch(); openInbox(); val id = ids().getValue(5_000); waitFor("inbox_related_$id")
        assertTrue(!has("inbox_duplicate_$id") && !has("inbox_same_$id"), "no 'same one' warning")
        assertTrue(textOf("inbox_related_$id").contains("refund or reversal"), textOf("inbox_related_$id"))
        tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        assertEquals(2, entryCount())
    }

    @Test
    fun aReversalTextDeliveredAgainAfterItWasAddedIsAlreadyInTheLedgerAndAddingItAgainIsRefusedWithoutACrash() {
        val debitText = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 999999999999)."
        val reversal = "Rs.1,250.00 credited to A/c XX1234 on 05-10-26 as reversal of UPI Ref 999999999999 (failed payment)."
        ingest("AX-HDFCBK-S", debitText, 1_000); ingest("AX-HDFCBK-S", reversal, 2_000)
        launch(); openInbox()
        val first = ids().getValue(1_000); val second = ids().getValue(2_000)
        waitFor("inbox_$first"); tap("inbox_add_$first"); compose.waitUntil(10_000) { !has("inbox_$first") }
        tap("inbox_add_$second"); compose.waitUntil(10_000) { has("inbox_empty") }
        ingest("AX-HDFCBK-S", reversal, 3_000_000) // the same words again, stamped later: another delivery
        tap("inbox_back"); openInbox(); val again = ids().getValue(3_000_000); waitFor("inbox_duplicate_$again")
        assertTrue(textOf("inbox_duplicate_$again").startsWith("Already in your ledger"), textOf("inbox_duplicate_$again"))
        tap("inbox_add_$again"); waitFor("inbox_problem_$again")
        assertEquals(2, entryCount())
    }
}
