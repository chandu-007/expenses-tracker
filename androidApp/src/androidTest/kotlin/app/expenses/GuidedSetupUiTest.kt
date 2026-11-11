package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.ui.WideLayoutTestHook
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
 * "Set up reading bank texts": four short steps (permission, banks, accounts with their digits, a summary and the checkup), skippable at every step, resumable from Home's card, persisted, and
 * the same at twice the font size and in a two-pane window. The permission is stood in for by [CheckupTestHook]. Names, digits and texts are invented.
 */
@RunWith(AndroidJUnit4::class)
class GuidedSetupUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun shell(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() {
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        CheckupTestHook.smsPermission = null; CheckupTestHook.notifAccess = null; CheckupTestHook.phone = null
        resetApp()
    }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        CheckupTestHook.smsPermission = null; CheckupTestHook.notifAccess = null; CheckupTestHook.phone = null
        WideLayoutTestHook.widthDp = null; WideLayoutTestHook.heightDp = null
        shell("settings put system font_scale 1.0")
        resetApp()
    }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String {
        waitFor(tag)
        // a tag on a Text is read as it is; a tag on a row or a button that merges its texts is read from the merged tree
        fun read(unmerged: Boolean) = runCatching { compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.Text)) c[SemanticsProperties.Text].joinToString("") { it.text } else "" } }.getOrDefault("")
        return read(true).ifEmpty { read(false) }
    }
    private fun waitText(tag: String, expected: String) = compose.waitUntil(10_000) { has(tag) && textOf(tag) == expected }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun put(key: String, value: String) { runBlocking { container.write { it.templates.putSetting(key, value) } } }
    private fun hook(sms: Boolean?) { compose.runOnIdle { CheckupTestHook.smsPermission = sms } }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().performTextInput(text) }
    private fun back() { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
    private fun openSetupFromMore() { compose.openMore(); tap("more_capture_setup"); waitFor("setup_screen") }
    private fun step(name: String) { waitFor("setup_step_$name") }
    private fun progress() = textOf("setup_progress")

    private fun seedAccount() = runBlocking { container.write { ok -> ok.store.saveAccount(Account(AccountId("hdfc"), "HDFC Savings", AccountType.BANK, Currency.INR)) } }

    @Test
    fun theFourStepsRunFromThePermissionToTheSummaryAndEachOneIsSavedAsItIsLeft() {
        hook(sms = false); seedAccount()
        launch(); openSetupFromMore()
        // 1: permission. Declining the disclosure changes nothing; the step can be skipped
        step("permission"); assertEquals("Step 1 of 4", progress())
        assertEquals("Not allowed yet", textOf("setup_permission_state"))
        tap("setup_allow_texts"); waitFor("sms_disclosure"); tap("sms_disclosure_decline"); gone("sms_disclosure")
        assertEquals("Not allowed yet", textOf("setup_permission_state"))
        assertFalse(has("setup_back_step"), "nothing to go back to on the first step")
        tap("setup_skip_step")
        // 2: banks
        step("banks"); assertEquals("Step 2 of 4", progress()); assertEquals("IN_PROGRESS:BANKS", setting("capture_setup"))
        tap("setup_bank_HDFC Bank"); compose.waitUntil(10_000) { setting("capture_setup_banks") == "HDFC Bank" }
        assertEquals("HDFC Bank texts from HDFCBK or HDFCBN", textOf("setup_bank_names"))
        tap("setup_bank_HDFC Bank"); compose.waitUntil(10_000) { setting("capture_setup_banks").isNullOrEmpty() }; gone("setup_bank_names")
        tap("setup_bank_HDFC Bank"); compose.waitUntil(10_000) { setting("capture_setup_banks") == "HDFC Bank" }
        // a bank that is not listed: type its sender name in the same box Capture has
        tap("setup_bank_not_listed"); waitFor("setup_not_listed_box")
        type("capture_headers", "MYBANK"); tap("capture_headers_save")
        waitFor("sender_chip_MYBANK"); compose.waitUntil(10_000) { setting("capture_extra_roots") == "MYBANK" }
        tap("setup_next")
        // 3: accounts and their digits (3 or 4)
        step("accounts"); assertEquals("Step 3 of 4", progress()); assertEquals("No digits yet", textOf("setup_account_state_hdfc"))
        compose.onNodeWithTag("setup_digits_save_hdfc", useUnmergedTree = true).performScrollTo().assertIsNotEnabled() // Save is off until there are 3 digits
        type("setup_digits_hdfc", "12")
        compose.onNodeWithTag("setup_digits_save_hdfc", useUnmergedTree = true).performScrollTo().assertIsNotEnabled()
        type("setup_digits_hdfc", "3")
        compose.onNodeWithTag("setup_digits_save_hdfc", useUnmergedTree = true).performScrollTo().assertIsEnabled()
        tap("setup_digits_save_hdfc")
        waitText("setup_account_state_hdfc", "Digits ending 123")
        assertEquals("123", runBlocking { container.write { it.looks.all().first { l -> l.accountId == "hdfc" }.last4 } })
        // back to the banks and forward again keeps what was done
        tap("setup_back_step"); step("banks"); assertEquals("HDFC Bank", setting("capture_setup_banks")); tap("setup_next"); step("accounts")
        tap("setup_next")
        // 4: the summary and the checkup
        step("done"); assertEquals("Step 4 of 4", progress()); assertEquals("FINISHED:DONE", setting("capture_setup"))
        assertEquals("Banks: HDFC Bank", textOf("setup_summary_banks")); assertEquals("Accounts: 1 of 1 have their digits", textOf("setup_summary_accounts"))
        assertEquals("Names you added: MYBANK", textOf("setup_summary_names")); assertEquals("Reading bank texts is not on yet", textOf("setup_summary_reading"))
        tap("setup_open_checkup"); waitFor("capture_checkup_headline")
        tap("capture_checkup_back")
        // a finished setup puts no card on Home
        waitFor("add"); assertFalse(has("setup_nudge"))
    }

    @Test
    fun allowedByAndroidButSwitchedOffOneTapTurnsItOnAndTheStepSaysSo() {
        hook(sms = true)
        launch(); openSetupFromMore(); step("permission")
        assertEquals("Allowed by Android, but the switch is off", textOf("setup_permission_state"))
        tap("setup_allow_texts")
        waitText("setup_permission_state", "Reading bank texts is on"); assertEquals("1", setting("capture_sms_on"))
        gone("setup_allow_texts")
    }

    @Test
    fun leavingAtAnyStepKeepsThePlaceAndHomeCardContinuesThere() {
        hook(sms = false)
        launch(); openSetupFromMore(); tap("setup_skip_step"); step("banks")
        tap("setup_close"); waitFor("add")
        assertEquals("IN_PROGRESS:BANKS", setting("capture_setup"))
        // Home's card says where the person is, and carries on there
        waitFor("setup_nudge"); assertEquals("Step 2 of 4. Carry on where you stopped.", textOf("setup_nudge_line"))
        assertEquals("Continue", compose.onNodeWithTag("setup_nudge_open").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text })
        tap("setup_nudge_open"); step("banks"); assertEquals("Step 2 of 4", progress())
        // Back (the system's) leaves it as well, at the step it is on
        tap("setup_next"); step("accounts"); back(); waitFor("add")
        assertEquals("IN_PROGRESS:ACCOUNTS", setting("capture_setup"))
        launch(); waitFor("setup_nudge"); tap("setup_nudge_open"); step("accounts")
    }

    @Test
    fun theCardCanBePutOffForADayOrHiddenForGoodAndTheSetupStaysInMore() {
        put("capture_setup", "OFFERED:PERMISSION")
        launch(); waitFor("setup_nudge")
        assertEquals("4 short steps. You can stop at any step.", textOf("setup_nudge_line"))
        tap("nudge_notnow_setup"); gone("setup_nudge")
        assertTrue(setting("nudge_snoozed:setup").orEmpty().endsWith("|1"), setting("nudge_snoozed:setup").orEmpty())
        launch(); waitFor("add"); compose.waitForIdle(); assertFalse(has("setup_nudge"), "put off for the day")
        // a day later it is back
        put("nudge_snoozed:setup", "${System.currentTimeMillis() - 25 * 3_600_000L}|1")
        launch(); waitFor("setup_nudge")
        tap("setup_nudge_hide"); gone("setup_nudge")
        assertEquals("HIDDEN:PERMISSION", setting("capture_setup"))
        launch(); waitFor("add"); compose.waitForIdle(); assertFalse(has("setup_nudge"))
        compose.openMore(); tap("more_capture_setup"); waitFor("setup_screen"); step("permission")
    }

    @Test
    fun noCardOnceCaptureIsRunningOrTheSetupWasNeverOffered() {
        put("capture_setup", "OFFERED:PERMISSION"); put("capture_sms_on", "1")
        launch(); waitFor("add"); compose.waitForIdle(); assertFalse(has("setup_nudge"), "something is already reading texts")
        put("capture_sms_on", "0"); put("capture_setup", "NONE:PERMISSION")
        launch(); waitFor("add"); compose.waitForIdle(); assertFalse(has("setup_nudge"), "an install that was never offered it (an update) is left alone")
        put("capture_setup", "garbage")
        launch(); waitFor("add"); compose.waitForIdle(); assertFalse(has("setup_nudge"))
    }

    @Test
    fun oneNudgeAtATimeToReviewComesFirstAndTheSetupIsNamedInTheLineUnderIt() {
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161", System.currentTimeMillis() - 60_000) }
        put("capture_setup", "OFFERED:PERMISSION")
        launch(); waitFor("inbox_banner")
        assertFalse(has("setup_nudge"), "one card at a time: To review outranks the setup")
        waitFor("home_more_nudges"); assertTrue(textOf("home_more_nudges").contains("Set up reading bank texts"), textOf("home_more_nudges"))
    }

    @Test
    fun theSetupIsReachableFromCaptureAndSkipsToTheCapturePageItNeeds() {
        hook(sms = false); seedAccount()
        launch(); compose.openMore(); tap("open_capture"); tap("open_capture_setup"); waitFor("setup_screen"); step("permission")
        // the accounts step opens the real Accounts page, and Back from there returns to the setup
        tap("setup_skip_step"); tap("setup_next"); step("accounts")
        tap("setup_open_accounts"); waitFor("accounts_list"); tap("back"); step("accounts")
    }

    @Test
    fun atTwiceTheFontSizeAndInATwoPaneWindowEveryStepIsWholeAndItsButtonsWork() {
        shell("settings put system font_scale 2.0"); Thread.sleep(2_000)
        hook(sms = false); seedAccount()
        launch(); openSetupFromMore()
        for (name in listOf("permission", "banks", "accounts", "done")) {
            step(name)
            // the title and the way on are reachable by scrolling, and nothing is wider than the screen
            waitFor("setup_title")
            val screen = compose.onNodeWithTag("setup_screen", useUnmergedTree = true).fetchSemanticsNode().size
            val body = compose.onNodeWithTag("setup_step_$name", useUnmergedTree = true).fetchSemanticsNode().size
            assertTrue(body.width <= screen.width, "step $name fits the width")
            if (name != "done") { tap("setup_next") } else { tap("setup_done") }
        }
        waitFor("add")
        WideLayoutTestHook.widthDp = 1000; WideLayoutTestHook.heightDp = 800
        launch(); openSetupFromMore(); step("permission"); tap("setup_skip_step"); step("banks")
        tap("setup_bank_HDFC Bank"); tap("setup_next"); step("accounts"); tap("setup_next"); step("done"); tap("setup_done"); waitFor("add")
    }
}
