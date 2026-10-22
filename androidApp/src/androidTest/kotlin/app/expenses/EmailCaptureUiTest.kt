package app.expenses

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bank alert emails read from a mail app's notification, end to end on the device. A test cannot post as another package, so a test or measurement build reads a
 * notification this app posts that carries [NotificationCaptureService.TEST_EXTRA] and names the package it stands in for ([NotificationCaptureService.TEST_PACKAGE_EXTRA]);
 * here that is Gmail's. **The email wordings are plausible, not copied from real emails, and the way real Gmail fills a notification's fields was not checked.**
 * The phone's notification access is granted through the secure setting the phone's own screen writes, as in [NotificationCaptureTest].
 */
@RunWith(AndroidJUnit4::class)
class EmailCaptureUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var before: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private val gmail = "com.google.android.gm"

    private fun shell(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun resetApp() { runBlocking { container.startFresh() } }

    @Before fun setUp() {
        resetApp()
        before = shell("settings get secure enabled_notification_listeners").trim().takeIf { it != "null" && it.isNotEmpty() }
        shell("settings put secure enabled_notification_listeners ${context.packageName}/${NotificationCaptureService::class.java.name}")
        manager.createNotificationChannel(NotificationChannel("t", "test", NotificationManager.IMPORTANCE_DEFAULT))
        wait(10_000) { NotificationCaptureService.accessGranted(context) }
    }

    @After fun tearDown() {
        compose.waitForIdle(); scenario?.close()
        if (before == null) shell("settings delete secure enabled_notification_listeners") else shell("settings put secure enabled_notification_listeners $before")
        manager.cancelAll(); resetApp()
    }

    private fun wait(ms: Long, cond: () -> Boolean) { val end = System.currentTimeMillis() + ms; while (!cond() && System.currentTimeMillis() < end) Thread.sleep(100) }
    private fun pending() = runBlocking { container.write { it.inbox.list() } }!!
    private fun setting(key: String, on: Boolean) = runBlocking { container.write { it.templates.putSetting(key, if (on) "1" else "0") } }
    private fun emailOn(on: Boolean) = setting(Capture.SETTING_EMAIL_ON, on)
    private fun counts() = runBlocking { container.write { ok -> ok.db.schemaQueries.captureEventCounts(0).executeAsList().associate { it.outcome to it.n } } }!!

    /** A notification as the test stands in for Gmail posting it: the sender's display name as the title, the subject as the text, and the start of the message as the big text. */
    private fun builder(sender: String, subject: String, bigText: String? = null, pkg: String = gmail) = NotificationCompat.Builder(context, "t").setSmallIcon(R.drawable.ic_notification)
        .addExtras(android.os.Bundle().apply { putBoolean(NotificationCaptureService.TEST_EXTRA, true); putString(NotificationCaptureService.TEST_PACKAGE_EXTRA, pkg) })
        .setContentTitle(sender).setContentText(subject).apply { if (bigText != null) setStyle(NotificationCompat.BigTextStyle().bigText(bigText)) }
    private fun post(id: Int, sender: String, subject: String, bigText: String? = null) = manager.notify(id, builder(sender, subject, bigText).build())

    private val debitMail = "Dear Customer, Rs. 1,250.00 is debited from your account XX1234 on 21-10-2026 to VPA shop@okhdfc. UPI Ref No 629312345678."

    @Test
    fun withTheEmailSwitchOffNothingIsReadEvenWhenPaymentAppCaptureIsOn() {
        setting(Capture.SETTING_NOTIF_ON, true); emailOn(false)
        post(1, "HDFC Bank", "Transaction alert", debitMail)
        Thread.sleep(2_500)
        assertEquals(emptyList(), pending(), "the email switch is separate from the payment app switch")
        assertEquals(emptyMap(), counts(), "not even a count while it is off")
    }

    @Test
    fun withTheSwitchOnABankEmailBecomesAPendingItemInTheRightDirection() {
        emailOn(true)
        post(2, "HDFC Bank", "Transaction alert", debitMail)
        wait(10_000) { pending().isNotEmpty() }
        val debit = pending().single()
        assertEquals("NOTIFICATION", debit.source); assertEquals("GMAIL", debit.sender); assertEquals("HDFC Bank", debit.issuer)
        assertEquals("DEBIT", debit.direction); assertEquals(125_000, debit.amountMinor); assertEquals("1234", debit.accountLast4)
        assertTrue(debit.body!!.contains("Rs. 1,250.00 is debited from your account XX1234"), "the original words are kept")

        post(3, "ICICI Bank Alerts", "Credit alert", "Your a/c XX5678 has been credited with INR 5,000.00 on 21-Oct-26. Info: NEFT-SALARY.")
        wait(10_000) { pending().size == 2 }
        val credit = pending().first { it.amountMinor == 500_000L }
        assertEquals("CREDIT", credit.direction); assertEquals("ICICI Bank", credit.issuer)
    }

    @Test
    fun theWholeLongEmailIsKeptUpToTwoThousandCharacters() {
        emailOn(true)
        val long = debitMail + " " + "Please keep this email for your records. ".repeat(80)
        post(4, "HDFC Bank", "Transaction alert", long)
        wait(10_000) { pending().isNotEmpty() }
        val body = pending().single().body!!
        assertTrue(body.length > 600, "more than the old cap of 600: ${body.length}")
        assertTrue(body.length <= 2000, "and no more than 2000: ${body.length}")
    }

    @Test
    fun anEmailThatIsNotFromAListedBankIsIgnoredAndOtpsAndPromotionsToo() {
        emailOn(true)
        post(5, "Ravi Kumar", "Lunch", "Rs. 500 sent to you by Ravi via UPI")
        post(6, "Swiggy", "Your order", "Rs. 250.00 debited for your order")
        post(7, "HDFC Bank", "OTP", "Your OTP for the transaction of Rs. 1,250.00 at AMAZON is 482913")
        post(8, "HDFC Bank", "New savings offer", "Open an account today and earn higher interest")
        Thread.sleep(3_000)
        assertEquals(emptyList(), pending())
        val c = counts()
        assertEquals(null, c["CAPTURED"]); assertEquals(1L, c["OTP"]); assertEquals(1L, c["NOT_A_TRANSACTION"])
        assertEquals(null, c["NOT_ALLOWLISTED"], "an email from nobody on the list leaves no trace, not even a count")
    }

    @Test
    fun aGroupSummaryIsNotReadSoABundleOfEmailsIsNotCountedTwice() {
        emailOn(true)
        val summary = builder("HDFC Bank", "2 new messages")
            .setStyle(NotificationCompat.InboxStyle().addLine("HDFC Bank  Rs. 1,250.00 is debited from your account XX1234").addLine("HDFC Bank  Rs. 99.00 is debited from your account XX1234"))
            .setGroup("mail").setGroupSummary(true).build()
        manager.notify(9, summary)
        Thread.sleep(2_500)
        assertEquals(emptyList(), pending(), "the summary lists emails that post their own notifications")
        post(10, "HDFC Bank", "Transaction alert", debitMail)
        wait(10_000) { pending().isNotEmpty() }
        Thread.sleep(1_500)
        assertEquals(125_000L, pending().single().amountMinor)
    }

    @Test
    fun aNotificationThatItselfListsSeveralEmailsIsReadLineByLine() {
        emailOn(true)
        val inbox = builder("3 new messages", "3 new messages")
            .setStyle(NotificationCompat.InboxStyle().addLine("HDFC Bank  Rs. 120.00 is debited from your account XX1234").addLine("Zomato  Your order is on the way").addLine("ICICI Bank  Rs 500 has been credited to your a/c XX5678"))
            .build()
        manager.notify(11, inbox)
        wait(10_000) { pending().size == 2 }
        Thread.sleep(1_500)
        assertEquals(setOf("DEBIT" to 12_000L, "CREDIT" to 50_000L), pending().map { it.direction to it.amountMinor }.toSet())
    }

    @Test
    fun theSameEmailPostedAgainIsOneItem() {
        emailOn(true)
        post(12, "HDFC Bank", "Transaction alert", debitMail)
        wait(10_000) { pending().isNotEmpty() }
        Thread.sleep(1_200)
        post(13, "HDFC Bank", "Transaction alert", debitMail)
        Thread.sleep(2_500)
        assertEquals(1, pending().size, "a mail app re-posts a notification when its group changes")
    }

    @Test
    fun aPaymentAppAlertAndABankEmailForOneSpendAreBothPendingSoTheInboxCanFlagTheLaterOne() {
        emailOn(true); setting(Capture.SETTING_NOTIF_ON, true)
        val at = System.currentTimeMillis()
        runBlocking { Capture.ingest(container, Capture.NOTIFICATION, "PHONEPE", "Paid ₹1,250 to Shop", at) }
        post(14, "HDFC Bank", "Transaction alert", debitMail)
        wait(10_000) { pending().size == 2 }
        assertEquals(setOf("PHONEPE", "GMAIL"), pending().map { it.sender }.toSet())
        assertEquals(setOf(125_000L), pending().map { it.amountMinor }.toSet())
    }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }

    @Test
    fun theCaptureScreenHasAnEmailSwitchThatIsOffUntilTheyTurnItOnAndSaysWhatItReads() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); tap("open_capture"); waitFor("email_capture_switch")
        runCatching { compose.onNodeWithTag("email_capture_switch", useUnmergedTree = true).performScrollTo() }
        fun state() = compose.onNodeWithTag("email_capture_switch", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        assertEquals(ToggleableState.Off, state(), "off by default")
        val note = compose.onNodeWithTag("email_capture_note", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }
        assertTrue(note.contains("Nothing is sent anywhere") && note.contains("notification"), note)
        tap("email_capture_switch")
        compose.waitUntil(10_000) { runBlocking { container.write { it.templates.setting(Capture.SETTING_EMAIL_ON) } } == "1" }
        compose.waitUntil(10_000) { state() == ToggleableState.On }
        assertEquals(null, runBlocking { container.write { it.templates.setting(Capture.SETTING_NOTIF_ON) } }, "the payment app switch is untouched")
    }
}
