package app.expenses

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.expenses.core.parsers.AppSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reads the notifications of a few payment apps (see [AppSources]) **only while the person has turned notification capture on** and given the
 * system's notification access. Every other app's notifications are dropped on the first line, without their words being read.
 */
class NotificationCaptureService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // never this app's own notifications (bill and card reminders, the weekly recap carry amounts): they must not feed back into capture
        val n = sbn.notification
        val testPost = BuildConfig.SEEDING_ENABLED && n.extras.getBoolean(TEST_EXTRA)
        if (sbn.packageName == packageName && !testPost) return
        val name = (if (testPost) MEASUREMENT_NAME else AppSources.nameOf(sbn.packageName)) ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val e = n.extras
        val words = AppSources.words(e.getCharSequence(Notification.EXTRA_TITLE), e.getCharSequence(Notification.EXTRA_TEXT), e.getCharSequence(Notification.EXTRA_BIG_TEXT))
        if (words.isBlank()) return
        val container = (applicationContext as ExpensesApp).container
        CoroutineScope(Dispatchers.IO).launch {
            val on = container.write { it.templates.setting(Capture.SETTING_NOTIF_ON) == "1" } == true
            if (on && Capture.ingest(container, Capture.NOTIFICATION, name, words, sbn.postTime) == app.expenses.core.storage.CaptureEventOutcome.BILL) NotificationWork.checkNow(applicationContext, container)
        }
    }

    companion object {
        /**
         * Test and measurement builds read a notification this app posts **only if it carries this marker**, so a test can exercise the service end to end
         * (a process cannot post as another package). The app's real notifications never carry it. Not read at all in a release build.
         */
        const val TEST_EXTRA = "app.expenses.capture_test"
        private const val MEASUREMENT_NAME = "GPAY"

        /** Whether the system has given this app notification access (the person turns that on in the phone's settings, not here). */
        fun accessGranted(context: android.content.Context): Boolean = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
