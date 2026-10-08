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
        val name = AppSources.nameOf(sbn.packageName, if (BuildConfig.SEEDING_ENABLED) MEASUREMENT_SOURCES else emptyMap()) ?: return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val e = n.extras
        val words = AppSources.words(e.getCharSequence(Notification.EXTRA_TITLE), e.getCharSequence(Notification.EXTRA_TEXT), e.getCharSequence(Notification.EXTRA_BIG_TEXT))
        if (words.isBlank()) return
        val container = (applicationContext as ExpensesApp).container
        CoroutineScope(Dispatchers.IO).launch {
            val on = container.write { it.templates.setting(Capture.SETTING_NOTIF_ON) == "1" } == true
            if (on) Capture.ingest(container, Capture.NOTIFICATION, name, words, sbn.postTime)
        }
    }

    companion object {
        /** Measurement and test builds also read this app's own notifications, so a test can post one and exercise this service end to end. Never in a release build. */
        private val MEASUREMENT_SOURCES = mapOf("app.expenses" to "GPAY")

        /** Whether the system has given this app notification access (the person turns that on in the phone's settings, not here). */
        fun accessGranted(context: android.content.Context): Boolean = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
