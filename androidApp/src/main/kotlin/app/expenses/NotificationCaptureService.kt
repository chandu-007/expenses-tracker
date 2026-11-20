package app.expenses

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.expenses.core.domain.NotificationAccess
import app.expenses.core.parsers.AppSources
import app.expenses.core.parsers.CaptureGate
import app.expenses.core.storage.CaptureEventOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reads the notifications of a few payment apps (see [AppSources]) **only while the person has turned notification capture on**, and those of the email apps
 * (Gmail, Outlook, Yahoo Mail) **only while the separate email switch is on**, in both cases given the system's notification access. The order is fixed
 * ([CaptureGate]): who posted it is judged first, from the package name alone, and every other app's notification is dropped there; then that source's switch is
 * read; **only when it is on are the notification's title and text pulled out of it**. With the switch off no word of it is read, not even into memory.
 */
class NotificationCaptureService : NotificationListenerService() {
    /**
     * The system drops a listener's binding now and then (the process was killed and restarted, a phone maker's memory manager) and does not always bind it again by itself.
     * While access is still given, ask for the binding back (Android's documented remedy); when the person has turned access off this does nothing.
     */
    override fun onListenerDisconnected() {
        connected = false
        if (accessGranted(applicationContext)) requestRebind(android.content.ComponentName(this, NotificationCaptureService::class.java))
    }

    override fun onListenerConnected() { connected = true }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val source = sourceOf(sbn, packageName) ?: return
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch { capture(app, sbn, source) }
    }

    companion object {
        /** Whether the system has bound this listener right now (it is told of notifications only while it is). Written by the service; read by the tests, which must not post before it is true. */
        @Volatile var connected: Boolean = false

        /**
         * Test and measurement builds read a notification this app posts **only if it carries this marker**, so a test can exercise the service end to end
         * (a process cannot post as another package). The app's real notifications never carry it. Not read at all in a release build.
         */
        const val TEST_EXTRA = "app.expenses.capture_test"

        /** With [TEST_EXTRA]: the package name a test post stands in for (an email app's, to exercise email capture), as a string extra. Absent: a payment app, as before. */
        const val TEST_PACKAGE_EXTRA = "app.expenses.capture_test_package"

        /**
         * The source [sbn] would be read as, or null when it is never read: an unlisted app, this app's own notifications (bill and card reminders, the weekly recap carry
         * amounts; they must not feed back into capture), or a group summary ("3 new emails": each email posts a notification of its own, and reading the summary too would
         * count the same one twice). Looks at the package name, the flags and, in a test build, the test marker; **never at the notification's words**.
         */
        internal fun sourceOf(sbn: StatusBarNotification, ownPackage: String): CaptureGate.Source? {
            val n = sbn.notification
            val testPost = BuildConfig.SEEDING_ENABLED && n.extras.getBoolean(TEST_EXTRA)
            // a test post may stand in for an email app by naming its package; it is never read in a release build
            val source = CaptureGate.notificationSource(sbn.packageName, ownPackage, testPost, if (testPost) n.extras.getString(TEST_PACKAGE_EXTRA) else null) ?: return null
            return if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) null else source
        }

        /** Reads [source]'s switch and, **only when it is on**, the words of [sbn], and hands them to capture. */
        internal suspend fun capture(context: Context, sbn: StatusBarNotification, source: CaptureGate.Source) {
            val app = context.applicationContext
            val container = (app as ExpensesApp).container
            val (paymentAppsOn, emailOn) = container.write { Pair(it.templates.setting(Capture.SETTING_NOTIF_ON) == "1", it.templates.setting(Capture.SETTING_EMAIL_ON) == "1") } ?: return
            if (!CaptureGate.mayRead(source, paymentAppsOn, emailOn)) return
            when (source.kind) {
                CaptureGate.Kind.EMAIL_APP -> readEmail(app, container, sbn, source.name)
                CaptureGate.Kind.PAYMENT_APP -> {
                    val e = sbn.notification.extras
                    val words = AppSources.words(e.getCharSequence(Notification.EXTRA_TITLE), e.getCharSequence(Notification.EXTRA_TEXT), e.getCharSequence(Notification.EXTRA_BIG_TEXT))
                    if (words.isBlank()) return
                    if (Capture.ingest(container, Capture.NOTIFICATION, source.name, words, sbn.postTime) == CaptureEventOutcome.BILL) NotificationWork.checkNow(app, container)
                }
            }
        }

        /**
         * One email app's notification, read only after the email switch was found on. A notification that itself lists several emails as inbox-style lines (older Gmail)
         * is read line by line. Only an email from a listed bank is kept (see [Capture.ingestEmail]).
         */
        private suspend fun readEmail(app: Context, container: AppContainer, sbn: StatusBarNotification, emailApp: String) {
            val e = sbn.notification.extras
            val notes = AppSources.emailNotes(
                e.getCharSequence(Notification.EXTRA_TITLE), e.getCharSequence(Notification.EXTRA_TEXT), e.getCharSequence(Notification.EXTRA_BIG_TEXT),
                e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.toList(), e.getCharSequence(Notification.EXTRA_SUB_TEXT),
            )
            if (notes.isEmpty()) return
            var bill = false
            for (note in notes) if (Capture.ingestEmail(container, emailApp, note.senderNames, note.words, sbn.postTime) == CaptureEventOutcome.BILL) bill = true
            if (bill) NotificationWork.checkNow(app, container)
        }

        /**
         * Whether the system has given this app notification access (the person turns that on in the phone's settings, not here). Read straight from the setting on each call:
         * `NotificationManagerCompat.getEnabledListenerPackages` keeps the last answer and never refreshes it when the setting has become null, so a screen that looked again on
         * returning to the app could still be told "yes". The list is parsed by [NotificationAccess] (every valid component format, several entries, null).
         */
        fun accessGranted(context: android.content.Context): Boolean =
            NotificationAccess.granted(android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners"), context.packageName)
    }
}
