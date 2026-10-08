package app.expenses

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.EncryptedDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The listener service end to end on the device: a notification is posted (by this app, which measurement and test builds treat as one of the
 * read payment apps), the system delivers it to the service, and it ends up as a pending item. Needs the system to bind the service, which is
 * granted here through the secure setting the phone's own screen writes.
 */
@RunWith(AndroidJUnit4::class)
class NotificationCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var before: String? = null

    private fun shell(cmd: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }

    @Before fun setUp() {
        resetApp()
        before = shell("settings get secure enabled_notification_listeners").trim().takeIf { it != "null" && it.isNotEmpty() }
        shell("settings put secure enabled_notification_listeners ${context.packageName}/${NotificationCaptureService::class.java.name}")
        manager.createNotificationChannel(NotificationChannel("t", "test", NotificationManager.IMPORTANCE_DEFAULT))
        compose(10_000) { NotificationCaptureService.accessGranted(context) }
    }

    @After fun tearDown() {
        if (before == null) shell("settings delete secure enabled_notification_listeners") else shell("settings put secure enabled_notification_listeners $before")
        manager.cancelAll(); resetApp()
    }

    private fun compose(ms: Long, cond: () -> Boolean) { val end = System.currentTimeMillis() + ms; while (!cond() && System.currentTimeMillis() < end) Thread.sleep(100) }
    private fun post(id: Int, title: String, text: String) = manager.notify(id, NotificationCompat.Builder(context, "t").setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text).build())
    private fun pending() = runBlocking { container.write { it.inbox.list() } }!!
    private fun switchOn(on: Boolean) = runBlocking { container.write { it.templates.putSetting(Capture.SETTING_NOTIF_ON, if (on) "1" else "0") } }

    @Test
    fun aPaymentAppNotificationBecomesAPendingItemOnlyWhileTheSwitchIsOn() {
        switchOn(false)
        post(1, "Paid ₹250", "to Swiggy")
        Thread.sleep(2_500)
        assertEquals(emptyList(), pending(), "switch off: nothing is read")
        switchOn(true)
        post(2, "Paid ₹1,250", "to Swiggy")
        compose(10_000) { pending().isNotEmpty() }
        val i = pending().single()
        assertEquals("NOTIFICATION", i.source); assertEquals("Google Pay", i.issuer); assertEquals("DEBIT", i.direction); assertEquals(125_000, i.amountMinor)
        assertTrue(i.body!!.contains("Swiggy"))
    }

    @Test
    fun moneyReceivedIsACreditAndAdvertsAndOtpsAreCountedButNeverKept() {
        switchOn(true)
        post(3, "Received ₹500", "from Ravi Kumar")
        post(4, "Get 10% cashback", "on your next recharge. Offer ends tonight")
        post(5, "Your OTP", "123456 is the OTP for your payment of ₹500")
        compose(10_000) { pending().isNotEmpty() }
        Thread.sleep(2_000)
        val items = pending(); assertEquals(1, items.size, "only the payment")
        assertEquals("CREDIT", items.single().direction); assertEquals(50_000, items.single().amountMinor)
        val counts = runBlocking { container.write { ok -> ok.db.schemaQueries.captureEventCounts(0).executeAsList().associate { it.outcome to it.n } } }!!
        assertEquals(1L, counts["CAPTURED"]); assertEquals(1L, counts["OTP"]); assertEquals(1L, counts["NOT_A_TRANSACTION"])
    }

    @Test
    fun theSameNotificationPostedAgainWithTheSameTimeIsOneItem() {
        switchOn(true)
        post(6, "Paid ₹75", "to Chai Point")
        compose(10_000) { pending().isNotEmpty() }
        val first = pending().single()
        runBlocking { Capture.ingest(container, Capture.NOTIFICATION, "GPAY", first.body!!, first.receivedAt) }
        assertEquals(1, pending().size)
    }

    @Test
    fun aGroupSummaryNotificationIsNotReadSoABundleOfPaymentsIsNotCountedTwice() {
        switchOn(true)
        manager.notify(7, NotificationCompat.Builder(context, "t").setSmallIcon(R.drawable.ic_notification).setContentTitle("Paid ₹90").setContentText("to Metro").setGroup("g").setGroupSummary(true).build())
        Thread.sleep(2_500)
        assertEquals(emptyList(), pending())
        post(8, "Paid ₹91", "to Metro")
        compose(10_000) { pending().isNotEmpty() }
        assertEquals(9_100L, pending().single().amountMinor)
    }
}
