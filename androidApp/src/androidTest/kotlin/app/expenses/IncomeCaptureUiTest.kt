package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
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
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.OriginKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Money received in To review gets an income category with the same trust model as money out: chips on the card, a guess from clear wording that is marked and never added by "Add all", the
 * person's own choice learned for the payer only (never for the same name as an expense payee), a refund of an earlier expense still a refund, and a suggestion in the editor. Invented texts only.
 */
@RunWith(AndroidJUnit4::class)
class IncomeCaptureUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() { resetApp(); runBlocking { container.write { it.templates.putSetting("capture_acct:HDFC Bank:1234", "bank"); it.templates.putSetting("first_week_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val salary = "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 01-OCT-26 for NEFT Cr-HDFC0000001-ACME SOFTWARE PVT LTD-RAVI KUMAR-SALARY OCT-SAMPLEH00000000000.Avl bal INR 1,20,000.00. Cheque deposits in A/C are subject to clearing"
    private val interest = "Update! INR 421.00 deposited in HDFC Bank A/c XX1234 on 30-SEP-26 for CREDIT INTEREST CAPITALISED.Avl bal INR 12,310.00. Cheque deposits in A/C are subject to clearing"
    private fun fromTenant(ref: String) = "Credit Alert!\nRs.1250.00 credited to HDFC Bank A/c XX1234 on 09-10-26 from VPA tenant@ybl (UPI $ref)"
    private fun toTenant(ref: String) = "Rs.200.00 debited from A/c XX1234 on 05-10-26 to VPA tenant@ybl (UPI Ref $ref). Not you? Call 18002586161"
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun tapOn(tag: String) { compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)); tap(tag) }
    private fun openInbox() { compose.openMore(); tap("open_inbox"); waitFor("inbox_back"); waitFor("inbox_list") }
    private fun idAt(at: Long) = runBlocking { container.write { ok -> ok.inbox.list().first { it.receivedAt == at }.id } }!!
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    // the card may not be composed yet right after the screen opens (a slow emulator): scroll to it again until it is there
    private fun goes(id: String): String { compose.waitUntil(10_000) { has("inbox_goes_$id") || run { runCatching { compose.onNodeWithTag("inbox_list", useUnmergedTree = true).performScrollToNode(hasTestTag("inbox_goes_$id")) }; has("inbox_goes_$id") } }; return textOf("inbox_goes_$id") }
    private fun learned() = runBlocking { container.write { it.captureRules.learned() } }!!
    private fun entries() = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(50).executeAsList() } }!!

    @Test
    fun aCreditWorthSalaryIsAGuessToCheckLeftOutOfAddAllAndAcceptingItAsItStandsTeachesNothing() {
        ingest("Rs.200.00 debited from A/c XX1234 on 05-10-26 to VPA swiggy@icici (UPI Ref 100000000011). Not you? Call 18002586161", 1_000)
        ingest(fromTenant("628374659001"), 2_000)
        ingest(salary, 3_000)
        launch(); openInbox()
        val swiggy = idAt(1_000); val tenant = idAt(2_000); val pay = idAt(3_000)
        assertTrue(goes(pay).contains("Salary (the words of the text)"), goes(pay))
        waitFor("inbox_check_$pay")
        assertFalse(has("inbox_check_$tenant"), "no guess, nothing to check")
        tap("inbox_add_all"); waitFor("inbox_add_all_ok")
        compose.onNodeWithTag("inbox_add_all_text", useUnmergedTree = true).assertTextContains("Items that look like repeats", substring = true)
        tap("inbox_add_all_ok")
        compose.waitUntil(10_000) { entries().size == 2 }
        assertEquals(setOf("INCOME", "EXPENSE"), entries().map { it.kind }.toSet())
        assertEquals(listOf(pay), runBlocking { container.write { it.inbox.list().map { i -> i.id } } }, "the salary guess was left for the person: only it is still waiting")
        assertFalse(swiggy.isEmpty() || tenant.isEmpty())
        tap("inbox_add_$pay"); compose.waitUntil(10_000) { entries().size == 3 }
        val e = entries().first { it.merchant?.contains("RAVI") == true || it.amount_minor == 8_500_000L }
        assertEquals("INCOME", e.kind); assertEquals(8_500_000L, e.amount_minor); assertEquals("income:salary", e.category_id)
        assertEquals(emptyMap(), learned().filterKeys { it.startsWith("in:") }, "a guess accepted as it stood is not what the person chose")
    }

    @Test
    fun aCreditFromAPersonOffersIncomeChipsAndOneChoiceIsLearnedForThePayerAsIncomeOnly() {
        ingest(fromTenant("628374659001"), 1_000)
        launch(); openInbox()
        val first = idAt(1_000)
        assertTrue(goes(first).contains("no category"), goes(first))
        tapOn("inbox_income_${first}_income:giftsreceived")
        compose.waitUntil(10_000) { goes(first).contains("Gifts received") }
        tap("inbox_add_$first"); compose.waitUntil(10_000) { entries().size == 1 }
        assertEquals("INCOME", entries().single().kind); assertEquals("income:giftsreceived", entries().single().category_id)
        assertEquals(mapOf("in:tenant" to "income:giftsreceived"), learned(), "learned as money in from this payer")
        // the next credit from the same payer starts there: once chosen by a person-like payer it is still a check
        ingest(fromTenant("628374659002"), 2_000)
        waitFor("inbox_list"); val second = idAt(2_000)
        compose.waitUntil(10_000) { runCatching { goes(second) }.getOrDefault("").contains("Gifts received (what you chose last time)") }
        waitFor("inbox_check_$second")
        tap("inbox_add_$second"); compose.waitUntil(10_000) { entries().size == 2 }
        assertEquals(2, runBlocking { container.write { it.captureRules.learnedUses()["in:tenant"] } }, "chosen again: it counts")
        ingest(fromTenant("628374659003"), 3_000)
        val third = idAt(3_000)
        compose.waitUntil(10_000) { runCatching { goes(third) }.getOrDefault("").contains("Gifts received (you chose it 2 times for this payer)") }
        assertFalse(has("inbox_check_$third"), "chosen twice: no longer a guess")
        // the same name as a PAYEE of money out has nothing of this
        ingest(toTenant("100000000099"), 4_000)
        val out = idAt(4_000)
        assertTrue(goes(out).contains("no category"), "what was learned for a payer is not a payee's category: " + goes(out))
    }

    @Test
    fun interestInTheWordsIsAGuessAndAddingItKeepsTheCategory() {
        ingest(interest, 1_000)
        launch(); openInbox()
        val id = idAt(1_000)
        assertTrue(goes(id).contains("Interest (the words of the text)"), goes(id))
        waitFor("inbox_check_$id")
        tap("inbox_add_$id"); compose.waitUntil(10_000) { entries().size == 1 }
        assertEquals("income:interest", entries().single().category_id); assertEquals("INCOME", entries().single().kind)
    }

    @Test
    fun aRefundOfAnEarlierExpenseIsStillARefundAndNotIncomeWithACategory() {
        val shopped = java.time.LocalDate.of(2026, 10, 10).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking { container.write { ok -> ok.store.save(Expense(EntryId("shop"), shopped, AccountId("bank"), Money(49_900, Currency.INR), listOf(CategorySplit(CategoryId("Shopping"), Money(49_900, Currency.INR)))), EntryDetails(merchant = "Sample Shop")) } }
        ingest("Update! INR 499.00 deposited in HDFC Bank A/c XX1234 on 15-OCT-26 for UPI-REFUND-SAMPLE SHOP-sample@ybl-000000000003.Avl bal INR 5,310.00. Cheque deposits in A/C are subject to clearing", 1_000)
        launch(); openInbox()
        val id = idAt(1_000)
        compose.waitUntil(10_000) { has("inbox_pair_$id") }
        assertTrue(textOf("inbox_pair_$id").contains("refund of Sample Shop"), textOf("inbox_pair_$id"))
        assertFalse(has("inbox_income_${id}_income:salary"), "a refund of an expense has no income chips")
        tap("inbox_add_$id"); compose.waitUntil(10_000) { entries().size == 2 }
        assertEquals(setOf("EXPENSE", "REFUND"), entries().map { it.kind }.toSet(), "no income was made: the expense is reduced, nothing is counted twice")
        assertEquals(emptyMap(), learned(), "nothing learned from a refund")
    }

    @Test
    fun anIncomeEntryWithoutACategoryOffersTheAppsSuggestionInTheEditorAndSavingItTeachesThePayer() {
        runBlocking {
            container.write { ok ->
                ok.captureRules.learn("tenant@ybl", "income:giftsreceived", "tenant@ybl", income = true); ok.captureRules.learn("tenant@ybl", "income:giftsreceived", "tenant@ybl", income = true)
                ok.store.save(Income(EntryId("in1"), System.currentTimeMillis() - 1_000, AccountId("bank"), Money(125_000, Currency.INR)), EntryDetails(merchant = "tenant@ybl", source = "sms", sourceRef = "628374659001"))
                ok.origins.put("in1", OriginKind.SMS, "AX-HDFCBK-S", fromTenant("628374659001"), 900)
            }
        }
        launch()
        scenario!!.onActivity { androidx.lifecycle.ViewModelProvider(it)[LedgerViewModel::class.java].openEditor("in1") }
        waitFor("income_suggestion")
        compose.onNodeWithTag("income_suggestion").assertTextContains("Suggested: Gifts received", substring = true)
        tap("income_suggestion")
        compose.onNodeWithTag("category_chip_income:giftsreceived", useUnmergedTree = true).assertIsSelected()
        tap("save"); waitFor("add")
        compose.waitUntil(10_000) { runBlocking { container.write { ok -> ok.db.schemaQueries.entryById("in1").executeAsOne().category_id } } == "income:giftsreceived" }
        assertEquals(3, runBlocking { container.write { it.captureRules.learnedUses()["in:tenant"] } }, "choosing it in the editor counts like choosing it in To review")
    }

    @Test
    fun aRuleForMoneyInTakesAnIncomeCategoryAndFiresOnlyOnACredit() {
        launch(); compose.openMore(); tap("open_capture"); tap("open_rules"); waitFor("rule_pattern")
        compose.onNodeWithTag("rule_pattern", useUnmergedTree = true).performTextInput("acme")
        tap("rule_kind_in"); tap("rule_category_income:salary"); tap("rule_add")
        compose.waitUntil(10_000) { runBlocking { container.write { it.captureRules.rules().size } } == 1 }
        assertEquals("income:salary", runBlocking { container.write { it.captureRules.rules().single().categoryId } })
        tap("rules_back"); tap("capture_back")
        ingest("Credit Alert!\nRs.5000.00 credited to HDFC Bank A/c XX1234 on 09-10-26 from VPA acme@ybl (UPI 628374659201)", 1_000)
        ingest("Rs.200.00 debited from A/c XX1234 on 05-10-26 to VPA acme@ybl (UPI Ref 100000000098). Not you? Call 18002586161", 2_000)
        openInbox()
        val credit = idAt(1_000); val debit = idAt(2_000)
        assertTrue(goes(credit).contains("Salary (your rule)"), goes(credit))
        assertFalse(has("inbox_check_$credit"), "the person's own rule is sure")
        assertTrue(goes(debit).contains("no category"), "an income rule never fires on money out: " + goes(debit))
    }
}
