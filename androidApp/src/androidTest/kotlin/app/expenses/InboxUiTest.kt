package app.expenses

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.InboxStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class InboxUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val debit = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"
    private val credit = "Rs.5,000.00 credited to A/c XX1234 on 06-10-26 by VPA ravi@ybl (UPI Ref 628374659999)."
    private fun ingest(body: String, at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", body, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() } // scrolls only where the node sits in a scrolling list
    private fun openInbox() { tap("more_menu"); tap("open_inbox"); waitFor("inbox_back") }
    private fun entries() = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(50).executeAsList() } }!!

    @Test
    fun aCapturedDebitShowsABannerAndAddingItCreatesTheExpenseAndClearsItFromTheInbox() {
        ingest(debit, 5_000); launch()
        waitFor("inbox_banner"); tap("inbox_banner"); waitFor("inbox_back")
        val id = runBlocking { container.write { it.inbox.list().single().id } }!!
        waitFor("inbox_$id")
        tap("inbox_add_$id")
        compose.waitUntil(10_000) { has("inbox_empty") }
        val e = entries().single()
        assertEquals(java.time.LocalDate.of(2026, 10, 5).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), e.occurred_at, "dated by the bank's date, not by when the text arrived")
        assertEquals(125_000, e.amount_minor); assertEquals("EXPENSE", e.kind); assertEquals("shop@okhdfc", e.merchant); assertEquals("sms", e.source); assertEquals("628374659283", e.source_ref)
        val item = runBlocking { container.write { it.inbox.get(id) } }!!
        assertEquals(InboxStatus.CONFIRMED, item.status); assertEquals(e.id, item.entryId); assertEquals(null, item.body)
    }

    private fun to(vpa: String, ref: String) = "Rs.200.00 debited from A/c XX1234 on 05-10-26 to VPA $vpa (UPI Ref $ref). Not you? Call 18002586161"
    private fun idAt(at: Long) = runBlocking { container.write { ok -> ok.inbox.list().first { it.receivedAt == at }.id } }!!
    private fun goes(id: String) = compose.onNodeWithTag("inbox_goes_$id", useUnmergedTree = true)

    @Test
    fun aCommonMerchantAndAPaymentToAPersonGetSuggestedCategoriesWithTheirReason() {
        ingest(to("swiggy@icici", "100000000001"), 1_000); ingest(to("9704849321@yescred", "100000000002"), 2_000); ingest(to("randomshop@okaxis", "100000000003"), 3_000)
        launch(); openInbox()
        val shop = idAt(1_000); val person = idAt(2_000); val other = idAt(3_000)
        waitFor("inbox_goes_$shop")
        goes(shop).assertTextContains("Food", substring = true).assertTextContains("a common merchant", substring = true)
        goes(person).assertTextContains("Other", substring = true).assertTextContains("a payment to a person", substring = true)
        goes(other).assertTextContains("no category", substring = true)
    }

    @Test
    fun aPayeeNamedOnceIsShownByThatNameOnLaterTextsAndOnTheEntry() {
        ingest(to("9704849321@yescred", "100000000004"), 1_000)
        launch(); openInbox()
        val first = idAt(1_000)
        waitFor("inbox_$first"); tap("inbox_name_btn_$first")
        compose.onNodeWithTag("inbox_name_$first", useUnmergedTree = true).performTextInput("Chandu"); tap("inbox_name_ok_$first")
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Chandu"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        tap("inbox_add_$first"); compose.waitUntil(10_000) { has("inbox_empty") }
        assertEquals("Chandu", entries().single().merchant)
        ingest(to("9704849321@yescred", "100000000005"), 9_000_000)
        tap("inbox_back"); openInbox()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Chandu"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aTextReadTheWrongWayRoundCanBeTurnedBeforeItIsAddedAndThenBecomesIncome() {
        ingest(debit, 5_000); launch(); openInbox()
        val id = runBlocking { container.write { it.inbox.list().single().id } }!!
        waitFor("inbox_$id"); assertTrue(has("inbox_flip_$id"))
        assertTrue(compose.onAllNodes(hasTestTag("inbox_amount_$id") and androidx.compose.ui.test.hasText("-₹1,250.00"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "money out shows its minus before it is turned")
        tap("inbox_flip_$id")
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("inbox_amount_$id") and androidx.compose.ui.test.hasText("+", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        val e = entries().single()
        assertEquals("INCOME", e.kind); assertEquals(125_000, e.amount_minor)
    }

    @Test
    fun aCreditBecomesIncomeWithoutACategoryAndTheAccountChoiceIsRememberedForTheNextOne() {
        ingest(credit, 6_000); launch(); openInbox()
        val id = runBlocking { container.write { it.inbox.list().single().id } }!!
        waitFor("inbox_$id")
        tap("inbox_change_$id"); tap("inbox_account_${id}_cash")
        tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        val e = entries().single()
        assertEquals("INCOME", e.kind); assertEquals(500_000, e.amount_minor); assertEquals("cash", e.account_id)
        ingest(debit, 7_000)
        runBlocking { container.write { ok -> ok.inbox.list().single().let { assertTrue(it.accountLast4 == "1234") } } }
        val row = runBlocking { container.write { ok -> ok.templates.setting("capture_acct:HDFC Bank:1234") } }
        assertEquals("cash", row, "the same card goes to the same account next time")
    }

    @Test
    fun skippingDismissesAndAddsNothingAndARepeatedReferenceIsRefusedWithAReason() {
        ingest(debit, 5_000); ingest(credit, 6_000); launch(); openInbox()
        val items = runBlocking { container.write { it.inbox.list() } }!!
        val debitId = items.first { it.direction == "DEBIT" }.id; val creditId = items.first { it.direction == "CREDIT" }.id
        waitFor("inbox_$debitId"); tap("inbox_skip_$creditId")
        compose.waitUntil(10_000) { !has("inbox_$creditId") }
        assertEquals(emptyList(), entries())
        // the same bank reference arriving again as a different text is refused, not added twice
        tap("inbox_add_$debitId"); compose.waitUntil(10_000) { has("inbox_empty") }
        ingest("$debit Thank you.", 8_000); launch(); openInbox()
        val again = runBlocking { container.write { it.inbox.list().single().id } }!!
        waitFor("inbox_$again"); tap("inbox_add_$again"); waitFor("inbox_problem_$again")
        assertEquals(1, entries().size)
    }

    @Test
    fun smsCaptureShowsTheMissingPermissionAndOnceGrantedTheSwitchTurnsItOnAndOff() {
        val granted = { androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        val on = { runBlocking { container.write { it.templates.setting(Capture.SETTING_SMS_ON) } } }
        if (!granted()) {
            // switched on in the settings but Android never allowed it (or the person took it back): the screen says so and the switch is off
            runBlocking { container.write { it.templates.putSetting(Capture.SETTING_SMS_ON, "1") } }
            launch(); tap("more_menu"); tap("open_capture"); waitFor("capture_permission_missing")
            compose.onNodeWithTag("capture_sms_switch", useUnmergedTree = true).assertIsOff()
            runBlocking { container.write { it.templates.putSetting(Capture.SETTING_SMS_ON, "0") } }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.RECEIVE_SMS}")).use { it.readBytes() } // grantRuntimePermission needs API 28
        }
        launch(); tap("more_menu"); tap("open_capture"); tap("capture_sms_switch")
        compose.waitUntil(10_000) { on() == "1" }
        compose.onNodeWithTag("capture_sms_switch", useUnmergedTree = true).assertIsOn()
        tap("capture_sms_switch"); compose.waitUntil(10_000) { on() == "0" }
    }

    @Test
    fun anAmountInAnotherCurrencyThanTheLedgersIsRefusedWithAReasonAndNothingIsAdded() {
        runBlocking { container.write { ok -> assertEquals(null, ok.currency.change(app.expenses.core.domain.Currency.USD)); app.expenses.core.domain.BaseCurrency.current = ok.currency.current() } }
        ingest(debit, 5_000); launch(); openInbox()
        val id = runBlocking { container.write { it.inbox.list().single().id } }!!
        waitFor("inbox_$id"); tap("inbox_add_$id"); waitFor("inbox_problem_$id")
        assertEquals(emptyList(), entries())
        app.expenses.core.domain.BaseCurrency.current = app.expenses.core.domain.Currency.INR
    }

    @Test
    fun theRowSaysWhenTheUpiAddressIsAPersonOrABusinessAndNothingWhenItCannotTell() {
        fun text(vpa: String, ref: String) = "Rs.100.00 debited from A/c XX1234 on 05-10-26 to VPA $vpa (UPI Ref $ref). Not you? Call 18002586161"
        ingest(text("9876543210@ybl", "100000000001"), 1_000); ingest(text("q123456789@ybl", "100000000002"), 2_000_000); ingest(text("shop@okhdfc", "100000000003"), 4_000_000)
        launch(); openInbox()
        val byTime = runBlocking { container.write { it.inbox.list().associate { i -> i.receivedAt to i.id } } }!!
        val meta = { t: Long -> compose.onNodeWithTag("inbox_meta_" + byTime.getValue(t), useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } }
        waitFor("inbox_meta_" + byTime.getValue(1_000))
        assertTrue(meta(1_000).endsWith("to a person"), meta(1_000)); assertTrue(meta(2_000_000).endsWith("to a business"), meta(2_000_000)); assertTrue(meta(4_000_000).endsWith("ending 1234"), meta(4_000_000))
    }

    @Test
    fun aTextArrivingOnTheBanksDateKeepsItsTimeOfDayOnTheEntry() {
        val arrived = java.time.LocalDate.of(2026, 10, 5).atTime(9, 41).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        ingest(debit, arrived); launch(); openInbox()
        val id = runBlocking { container.write { it.inbox.list().single().id } }!!
        waitFor("inbox_$id"); tap("inbox_add_$id"); compose.waitUntil(10_000) { has("inbox_empty") }
        assertEquals(arrived, entries().single().occurred_at)
    }
}
