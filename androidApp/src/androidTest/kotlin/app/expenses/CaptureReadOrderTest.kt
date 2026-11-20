package app.expenses

import android.app.Notification
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Telephony
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.parsers.CaptureGate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Serializable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Privacy order of operations: with a source's switch off, the notification listener and the SMS receiver must not read one word of what arrives, not even into
 * memory. The notification's words are [Counted] char sequences and the SMS broadcast is a [CountingIntent], so any read of them is counted. Each case calls the real
 * entry point (`onNotificationPosted`, `onReceive`) and then the same path awaited; the switch-on cases are the control that the counting sees a read.
 * The posts are built here and never shown, so no notification access or SMS permission is needed.
 */
@RunWith(AndroidJUnit4::class)
class CaptureReadOrderTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val container get() = (context as ExpensesApp).container
    private val reads = AtomicInteger()

    @Before fun setUp() { runBlocking { container.startFresh() } }
    @After fun tearDown() { runBlocking { container.startFresh() } }

    private fun setting(key: String, on: Boolean) = runBlocking { container.write { it.templates.putSetting(key, if (on) "1" else "0") } }

    /** A char sequence that counts every look at its contents. */
    private inner class Counted(private val s: String) : CharSequence {
        override val length: Int get() = s.length.also { reads.incrementAndGet() }
        override fun get(index: Int): Char = s[index].also { reads.incrementAndGet() }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = s.subSequence(startIndex, endIndex).also { reads.incrementAndGet() }
        override fun toString(): String = s.also { reads.incrementAndGet() }
    }

    /** An SMS broadcast that counts every attempt to take the messages (or any extra) out of it. It carries no messages: the count is what matters. */
    private inner class CountingIntent : Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
        override fun getExtras(): Bundle? = super.getExtras().also { reads.incrementAndGet() }
        override fun getSerializableExtra(name: String?): Serializable? = super.getSerializableExtra(name).also { reads.incrementAndGet() }
        override fun <T : Serializable?> getSerializableExtra(name: String?, clazz: Class<T>): T? = super.getSerializableExtra(name, clazz).also { reads.incrementAndGet() }
        override fun getStringExtra(name: String?): String? = super.getStringExtra(name).also { reads.incrementAndGet() }
        override fun getBundleExtra(name: String?): Bundle? = super.getBundleExtra(name).also { reads.incrementAndGet() }
    }

    @Suppress("DEPRECATION")
    private fun posted(pkg: String): StatusBarNotification {
        val n = Notification()
        n.extras.putCharSequence(Notification.EXTRA_TITLE, Counted("Paid ₹250"))
        n.extras.putCharSequence(Notification.EXTRA_TEXT, Counted("to Test Cafe"))
        n.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, Counted("Paid ₹250 to Test Cafe"))
        n.extras.putCharSequence(Notification.EXTRA_SUB_TEXT, Counted("someone@example.com"))
        n.extras.putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf<CharSequence>(Counted("Test Bank: Rs 250 debited")))
        return StatusBarNotification(pkg, pkg, 1, null, Process.myUid(), Process.myPid(), 0, n, Process.myUserHandle(), System.currentTimeMillis())
    }

    private fun service(): NotificationCaptureService = NotificationCaptureService().also { s ->
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply { isAccessible = true }.invoke(s, context)
    }

    /** The real entry point, then a moment for anything it started; on the old order the words were read before it even returned. */
    private fun postToService(sbn: StatusBarNotification) { service().onNotificationPosted(sbn); Thread.sleep(1_500) }

    private fun awaited(sbn: StatusBarNotification) = runBlocking {
        NotificationCaptureService.capture(context, sbn, assertNotNull(NotificationCaptureService.sourceOf(sbn, context.packageName)))
    }

    @Test
    fun aPaymentAppNotificationIsNotReadWhileItsSwitchIsOff() {
        setting(Capture.SETTING_NOTIF_ON, false); setting(Capture.SETTING_EMAIL_ON, true)
        val sbn = posted("com.phonepe.app")
        assertEquals(CaptureGate.Kind.PAYMENT_APP, NotificationCaptureService.sourceOf(sbn, context.packageName)?.kind)
        assertEquals(0, reads.get(), "who posted it is judged without its words")
        postToService(sbn)
        assertEquals(0, reads.get(), "switch off: onNotificationPosted read no word")
        awaited(sbn)
        assertEquals(0, reads.get(), "switch off: the capture path read no word")
        setting(Capture.SETTING_NOTIF_ON, true)
        awaited(sbn)
        assertTrue(reads.get() > 0, "switch on: the words are read (the counting works)")
    }

    @Test
    fun anEmailAppNotificationIsNotReadWhileTheEmailSwitchIsOffEvenWithPaymentAppsOn() {
        setting(Capture.SETTING_NOTIF_ON, true); setting(Capture.SETTING_EMAIL_ON, false)
        val sbn = posted("com.google.android.gm")
        assertEquals(CaptureGate.Kind.EMAIL_APP, NotificationCaptureService.sourceOf(sbn, context.packageName)?.kind)
        postToService(sbn)
        assertEquals(0, reads.get(), "email switch off: onNotificationPosted read no word")
        awaited(sbn)
        assertEquals(0, reads.get(), "email switch off: the capture path read no word")
        setting(Capture.SETTING_EMAIL_ON, true)
        awaited(sbn)
        assertTrue(reads.get() > 0, "email switch on: the words are read (the counting works)")
    }

    @Test
    fun anUnlistedAppOrThisAppsOwnNotificationIsNeverRead() {
        setting(Capture.SETTING_NOTIF_ON, true); setting(Capture.SETTING_EMAIL_ON, true)
        for (pkg in listOf("com.example.chat", context.packageName)) {
            val sbn = posted(pkg)
            assertEquals(null, NotificationCaptureService.sourceOf(sbn, context.packageName), pkg)
            postToService(sbn)
        }
        assertEquals(0, reads.get())
    }

    @Test
    fun anSmsIsNotTakenOutOfTheBroadcastWhileTheSmsSwitchIsOff() {
        setting(Capture.SETTING_SMS_ON, false)
        val intent = CountingIntent()
        SmsReceiver().onReceive(context, intent)
        Thread.sleep(1_500)
        assertEquals(0, reads.get(), "switch off: onReceive read nothing of the message")
        runBlocking { SmsReceiver.capture(context, intent) }
        assertEquals(0, reads.get(), "switch off: the capture path read nothing of the message")
        setting(Capture.SETTING_SMS_ON, true)
        runBlocking { SmsReceiver.capture(context, intent) }
        assertTrue(reads.get() > 0, "switch on: the message is read (the counting works)")
    }
}
