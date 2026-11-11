package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.BatteryState
import app.expenses.core.domain.Currency
import app.expenses.core.domain.PhoneMaker
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.skippedCounters
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
 * "Why isn't capture working?" (More, Capture): every link of the chain checked from what the app can see, in words, with a button for what can be fixed, and "Check a message", which shows how a
 * pasted text would be read and saves nothing. What the PHONE says (the text permission, notification access, reminders, battery, the maker) is stood in for by [CheckupTestHook], so each state can be
 * shown on any emulator whatever its permissions are; the real permission and access flows, with the app stopped and resumed, are in [ZzCheckupResumeUiTest]. All names, digits and texts are invented.
 */
@RunWith(AndroidJUnit4::class)
class CaptureCheckupUiTest {
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
    private fun hook(sms: Boolean? = null, access: Boolean? = null, phone: PhoneStatus? = null) { compose.runOnIdle { CheckupTestHook.smsPermission = sms; CheckupTestHook.notifAccess = access; CheckupTestHook.phone = phone } }

    /** The checkup, from Home: More, then its row. */
    private fun openCheckup() { launch(); compose.openMore(); tap("more_capture_checkup"); waitFor("capture_checkup_headline"); gone("capture_checkup_loading") }
    private fun level(id: String) = textOf("capture_checkup_level_$id")
    private fun headline() = textOf("capture_checkup_headline")
    private fun awayAnd(change: () -> Unit = {}) {
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED); change(); scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
    }

    private val payment = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"

    @Test
    fun theTextPermissionAndTheSwitchShowEachOfTheirStatesInWordsWithTheRightButton() {
        hook(sms = false)
        openCheckup()
        assertEquals("Fix this", level("sms")); assertEquals("Reading bank texts is not allowed", textOf("capture_checkup_title_sms"))
        assertTrue(has("capture_checkup_action_sms_ALLOW_TEXTS") && has("capture_checkup_action_sms_OPEN_APP_SETTINGS"))
        assertTrue(headline().endsWith("need attention") || headline().endsWith("needs attention"), headline())
        // allowed by Android, but the switch is off: one tap turns it on
        hook(sms = true)
        waitText("capture_checkup_title_sms", "Reading bank texts is switched off")
        assertTrue(has("capture_checkup_action_sms_TURN_ON_TEXTS") && !has("capture_checkup_action_sms_ALLOW_TEXTS"))
        tap("capture_checkup_action_sms_TURN_ON_TEXTS")
        waitText("capture_checkup_title_sms", "Reading bank texts is on")
        assertEquals("OK", level("sms")); assertEquals("1", setting("capture_sms_on"))
        assertFalse(has("capture_checkup_action_sms_TURN_ON_TEXTS"))
    }

    @Test
    fun tappingAllowReadingTextsShowsTheSameDisclosureAsTheCaptureScreenBeforeAndroidAsks() {
        hook(sms = false)
        openCheckup()
        tap("capture_checkup_action_sms_ALLOW_TEXTS")
        waitFor("sms_disclosure"); assertTrue(has("sms_disclosure_agree") && has("sms_disclosure_decline"))
        tap("sms_disclosure_decline"); gone("sms_disclosure")
        assertEquals("Fix this", level("sms"), "declining changes nothing")
    }

    @Test
    fun notificationAccessIsOffMissingNotConnectedOrConnected() {
        hook(sms = true, access = false)
        openCheckup()
        assertEquals("Note", level("listener"), "payment apps are optional while texts are read")
        put("capture_notif_on", "1")
        openCheckup()
        assertEquals("Fix this", level("listener")); assertEquals("Notification access is not given", textOf("capture_checkup_title_listener"))
        assertTrue(has("capture_checkup_action_listener_OPEN_NOTIFICATION_ACCESS"))
        hook(sms = true, access = true, phone = PhoneStatus(listenerConnected = false))
        waitText("capture_checkup_title_listener", "Notification access is given, but the app is not connected")
        assertTrue(has("capture_checkup_action_listener_RECONNECT_LISTENER") && has("capture_checkup_action_listener_OPEN_NOTIFICATION_ACCESS"))
        hook(sms = true, access = true, phone = PhoneStatus(listenerConnected = true))
        waitText("capture_checkup_level_listener", "OK")
    }

    @Test
    fun remindersBatteryAndASamsungsDeepSleepListAreRowsTheUserCanTick() {
        hook(sms = true, phone = PhoneStatus(postNotifications = false, battery = BatteryState.RESTRICTED, maker = PhoneMaker.SAMSUNG))
        put("capture_sms_on", "1")
        openCheckup()
        assertEquals("Fix this", level("reminders")); assertTrue(has("capture_checkup_action_reminders_ALLOW_REMINDERS"))
        assertEquals("Fix this", level("battery")); assertTrue(has("capture_checkup_action_battery_OPEN_BATTERY"))
        assertEquals("Fix this", level("battery_list"))
        assertTrue(textOf("capture_checkup_detail_battery_list").contains("Settings, Battery, Background usage limits, Deep sleeping apps"), "the exact path, as a checklist line")
        val before = headline()
        tap("capture_checkup_tick_checkup_battery_ticked")
        waitText("capture_checkup_level_battery_list", "OK")
        assertEquals("1", setting("checkup_battery_ticked"))
        assertTrue(headline() != before, "the overall line followed the tick: $before -> ${headline()}")
        // untick: back to something to fix
        tap("capture_checkup_tick_checkup_battery_ticked"); waitText("capture_checkup_level_battery_list", "Fix this")
        hook(sms = true, phone = PhoneStatus(postNotifications = true, battery = BatteryState.OPTIMISED, maker = PhoneMaker.OTHER))
        waitText("capture_checkup_level_reminders", "OK"); waitText("capture_checkup_level_battery", "Note"); gone("capture_checkup_row_battery_list")
        hook(sms = true, phone = PhoneStatus(postNotifications = null, battery = BatteryState.UNRESTRICTED))
        gone("capture_checkup_row_reminders"); waitText("capture_checkup_level_battery", "OK")
    }

    @Test
    fun theBanksTheirDigitsSkippedNamesAndRecentActivityComeFromTheOwnersData() {
        runBlocking { container.write { ok ->
            ok.store.saveAccount(Account(AccountId("hdfc"), "HDFC Savings", AccountType.BANK, Currency.INR))
            ok.store.saveAccount(Account(AccountId("icici"), "ICICI Card", AccountType.CREDIT_CARD, Currency.INR))
            ok.looks.save(AccountLook("icici", last4 = "4321"))
        } }
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", payment, System.currentTimeMillis() - 3_600_000) }
        runBlocking { Capture.ingest(container, Capture.SMS, "VM-MYCOOP-S", payment, System.currentTimeMillis() - 1_000) }
        hook(sms = true); put("capture_sms_on", "1")
        openCheckup()
        // a text from HDFC's sender name reached the app; none from ICICI's
        assertEquals("OK", level("bank_HDFC Bank")); assertEquals("HDFC Bank: texts from HDFCBK or HDFCBN recognised", textOf("capture_checkup_title_bank_HDFC Bank"))
        assertEquals("Note", level("bank_ICICI Bank")); assertEquals("No text from ICICI Bank seen yet", textOf("capture_checkup_title_bank_ICICI Bank"))
        // the HDFC account has no digits, the card has: one line, naming the account that needs them
        assertEquals("Fix this", level("digits")); assertEquals("1 account needs its last digits", textOf("capture_checkup_title_digits"))
        assertTrue(textOf("capture_checkup_detail_digits").contains("HDFC Savings"))
        assertTrue(has("capture_checkup_action_digits_OPEN_ACCOUNTS"))
        // one payment found, with the reasons for what was skipped
        assertEquals("1 payment found in the last 7 days", textOf("capture_checkup_title_activity")); assertEquals("OK", level("activity"))
        // the skipped name has its own one-tap button, and trusting it takes the row away
        assertEquals("1 sender name was skipped", textOf("capture_checkup_title_skipped"))
        tap("capture_checkup_action_skipped_TRUST_SENDER_MYCOOP")
        waitText("capture_checkup_title_skipped", "No sender names were skipped")
        compose.waitUntil(10_000) { setting("capture_extra_roots") == "MYCOOP" }
        // digits added on the Accounts page show as done after coming back from it
        tap("capture_checkup_action_digits_OPEN_ACCOUNTS"); waitFor("accounts_list")
        runBlocking { container.write { it.looks.save(AccountLook("hdfc", last4 = "1234")) } }
        tap("back"); waitFor("capture_checkup_headline")
        waitText("capture_checkup_level_digits", "OK")
    }

    @Test
    fun checkAMessageShowsHowItWouldBeReadMasksTheDigitsAndSavesNothing() {
        runBlocking { container.write { ok ->
            ok.store.saveAccount(Account(AccountId("hdfc"), "HDFC Savings", AccountType.BANK, Currency.INR))
            ok.looks.save(AccountLook("hdfc", last4 = "1234"))
        } }
        hook(sms = true)
        openCheckup()
        val before = stored()
        compose.onNodeWithTag("capture_checkup_sender", useUnmergedTree = true).performScrollTo().performTextInput("AX-HDFCBK-S")
        compose.onNodeWithTag("capture_checkup_message", useUnmergedTree = true).performScrollTo().performTextInput(payment)
        tap("capture_checkup_check")
        waitFor("capture_checkup_result")
        assertEquals("The app would put this in To review", textOf("capture_checkup_result_headline"))
        assertTrue(has("capture_checkup_result_sender") && has("capture_checkup_result_bank") && has("capture_checkup_result_direction") && has("capture_checkup_result_amount") && has("capture_checkup_result_payee") && has("capture_checkup_result_your_account") && has("capture_checkup_result_account_digits"))
        waitFor("capture_checkup_result_category")
        // what it says: the bank from the sender name, money out, the amount, and the account whose digits match, with the digits masked
        assertTrue(textOf("capture_checkup_result_sender").contains("HDFCBK"), textOf("capture_checkup_result_sender"))
        assertTrue(textOf("capture_checkup_result_bank").endsWith("HDFC Bank"), textOf("capture_checkup_result_bank"))
        assertTrue(textOf("capture_checkup_result_direction").endsWith("money out"))
        assertTrue(textOf("capture_checkup_result_amount").contains("1,250"), textOf("capture_checkup_result_amount"))
        assertTrue(textOf("capture_checkup_result_your_account").contains("HDFC Savings"), textOf("capture_checkup_result_your_account"))
        assertTrue(textOf("capture_checkup_result_account_digits").endsWith("ending ••34"), textOf("capture_checkup_result_account_digits"))
        // nothing of the text's numbers is on the screen outside the box the person typed in
        for (digits in listOf("1234", "628374659283", "18002586161")) {
            val shown = compose.onAllNodes(hasText(digits, substring = true), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { n -> !n.config.contains(androidx.compose.ui.semantics.SemanticsActions.SetText) }
            assertTrue(shown.isEmpty(), "the result shows $digits")
        }
        // and nothing was saved, counted or kept: the same as before
        assertEquals(before, stored(), "no inbox item, no capture count, no skipped counter, no bill")
        // Clear takes the answer and the typed words away
        tap("capture_checkup_clear"); gone("capture_checkup_result")
        assertEquals(before, stored())
        // leaving the screen forgets the answer
        compose.onNodeWithTag("capture_checkup_message", useUnmergedTree = true).performScrollTo().performTextInput(payment)
        tap("capture_checkup_check"); waitFor("capture_checkup_result")
        tap("capture_checkup_back"); gone("capture_checkup_headline")
        compose.openMore(); tap("more_capture_checkup"); waitFor("capture_checkup_headline")
        assertFalse(has("capture_checkup_result"), "the result is not kept")
        assertEquals(before, stored())
    }

    @Test
    fun aSenderNameOnNoListIsSkippedUnreadAndTheCheckOffersToTrustItShowingWhatItWouldBe() {
        hook(sms = true)
        openCheckup()
        compose.onNodeWithTag("capture_checkup_sender", useUnmergedTree = true).performScrollTo().performTextInput("AX-SHOPXX-S")
        compose.onNodeWithTag("capture_checkup_message", useUnmergedTree = true).performScrollTo().performTextInput(payment)
        tap("capture_checkup_check"); waitFor("capture_checkup_result")
        assertEquals("The app would ignore this text", textOf("capture_checkup_result_headline"))
        assertTrue(textOf("capture_checkup_result_explain").contains("not on the list of banks"))
        assertTrue(textOf("capture_checkup_result_sender").contains("SHOPXX") && textOf("capture_checkup_result_sender").contains("not on any list"))
        assertFalse(has("capture_checkup_result_amount"), "nothing of the words is read from a name that is not trusted")
        waitFor("capture_checkup_result_if"); assertTrue(textOf("capture_checkup_result_if").contains("SHOPXX"))
        assertTrue(textOf("capture_checkup_result_if_amount").contains("1,250"))
        assertEquals(0, stored().third, "checking counted nothing for the skipped name")
        tap("capture_checkup_result_trust")
        compose.waitUntil(10_000) { setting("capture_extra_roots") == "SHOPXX" }
        // a phone number is a person: no offer
        tap("capture_checkup_clear")
        compose.onNodeWithTag("capture_checkup_sender", useUnmergedTree = true).performScrollTo().performTextInput("+919876543210")
        compose.onNodeWithTag("capture_checkup_message", useUnmergedTree = true).performScrollTo().performTextInput(payment)
        tap("capture_checkup_check"); waitFor("capture_checkup_result")
        assertTrue(textOf("capture_checkup_result_explain").contains("phone number")); assertFalse(has("capture_checkup_result_trust"))
        // with no sender, a text that names no bank and looks like no alert is ignored with its reason
        tap("capture_checkup_clear")
        compose.onNodeWithTag("capture_checkup_message", useUnmergedTree = true).performScrollTo().performTextInput("see you at lunch, I will pay Rs 500 later")
        tap("capture_checkup_check"); waitFor("capture_checkup_result")
        assertEquals("The app would ignore this text", textOf("capture_checkup_result_headline")); assertTrue(textOf("capture_checkup_result_explain").contains("no sender name"))
    }

    @Test
    fun theCheckupCanBeReachedFromCaptureFromAnEmptyToReviewAndFromGettingStarted() {
        hook(sms = true)
        launch(); compose.openMore(); tap("open_capture"); tap("open_capture_checkup"); waitFor("capture_checkup_headline")
        assertTrue(textOf("capture_checkup_headline").isNotEmpty())
        tap("capture_checkup_back"); compose.openMore(); tap("open_inbox"); waitFor("inbox_empty"); tap("inbox_empty_checkup"); waitFor("capture_checkup_headline")
        // Home's Getting started card, while the capture step is not done
        runBlocking { container.write { ok ->
            ok.templates.putSetting("first_week_dismissed", "0")
            ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId("e"), System.currentTimeMillis(), AccountId("cash"), app.expenses.core.domain.Money(10_000, Currency.INR), listOf(app.expenses.core.domain.ledger.CategorySplit(app.expenses.core.domain.ledger.CategoryId("Food"), app.expenses.core.domain.Money(10_000, Currency.INR)))))
            ok.templates.putSetting("first_week_started", (System.currentTimeMillis() - 86_400_000L).toString())
        } }
        launch(); waitFor("first_week_card"); tap("first_week_toggle")
        waitFor("first_week_checkup"); waitFor("first_week_earlier")
        tap("first_week_checkup"); waitFor("capture_checkup_headline")
    }

    @Test
    fun atTwiceTheFontSizeEveryLineAndButtonIsStillThereAndWorks() {
        shell("settings put system font_scale 2.0"); Thread.sleep(2_000)
        hook(sms = false, phone = PhoneStatus(postNotifications = false, battery = BatteryState.RESTRICTED, maker = PhoneMaker.SAMSUNG))
        openCheckup()
        for (id in listOf("sms", "reminders", "battery", "battery_list", "activity")) waitFor("capture_checkup_row_$id")
        assertTrue(has("capture_checkup_status"))
        tap("capture_checkup_action_sms_ALLOW_TEXTS"); waitFor("sms_disclosure"); tap("sms_disclosure_decline"); gone("sms_disclosure")
        compose.onNodeWithTag("capture_checkup_message", useUnmergedTree = true).performScrollTo().performTextInput(payment)
        tap("capture_checkup_check"); waitFor("capture_checkup_result")
        // every row is as wide as the screen and as tall as its words: nothing is cut off
        val root = compose.onNodeWithTag("capture_checkup", useUnmergedTree = true).fetchSemanticsNode().size
        val row = compose.onNodeWithTag("capture_checkup_row_sms", useUnmergedTree = true).fetchSemanticsNode().size
        assertTrue(row.width <= root.width && row.height > 0)
    }

    @Test
    fun inATwoPaneWindowTheCheckupIsOneCentredPageAndWorksTheSame() {
        WideLayoutTestHook.widthDp = 1000; WideLayoutTestHook.heightDp = 800
        hook(sms = false)
        openCheckup()
        assertEquals("Fix this", level("sms")); assertTrue(has("capture_checkup_status"))
        tap("capture_checkup_action_sms_ALLOW_TEXTS"); waitFor("sms_disclosure"); tap("sms_disclosure_decline")
        compose.onNodeWithTag("capture_checkup_message", useUnmergedTree = true).performScrollTo().performTextInput(payment)
        tap("capture_checkup_check"); waitFor("capture_checkup_result")
    }

    @Test
    fun textSharedFromAnotherAppOpensCheckAMessageWithItAlreadyCheckedAndSavesNothing() {
        hook(sms = true)
        val before = stored()
        compose.waitForIdle(); scenario?.close()
        scenario = ActivityScenario.launch(Intent(Intent.ACTION_SEND).setClassName(context.packageName, MainActivity::class.java.name).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "HDFC Bank: $payment"))
        waitFor("capture_checkup_result")
        assertEquals("The app would put this in To review", textOf("capture_checkup_result_headline"))
        assertTrue(textOf("capture_checkup_result_bank").endsWith("HDFC Bank"), textOf("capture_checkup_result_bank"))
        assertTrue(textOf("capture_checkup_result_sender").contains("none given"), "a shared text has no sender name: ${textOf("capture_checkup_result_sender")}")
        assertEquals(before, stored(), "nothing was saved, counted or kept")
        // the words are in the box, and leaving and coming back does not bring the result back
        tap("capture_checkup_back"); gone("capture_checkup_headline")
        compose.openMore(); tap("more_capture_checkup"); waitFor("capture_checkup_headline"); assertFalse(has("capture_checkup_result"))
    }

    /** What a check must not change: the number of waiting texts, capture log rows and skipped-sender counters, and the bills. */
    private fun stored(): Triple<Int, Int, Int> = runBlocking { container.write { ok -> Triple(ok.inbox.list().size + ok.bills.list(app.expenses.core.storage.BillStatus.FOUND, 50).size, ok.db.schemaQueries.captureEventCounts(0).executeAsList().sumOf { it.n }.toInt(), ok.templates.skippedCounters().size) } }!!
}
