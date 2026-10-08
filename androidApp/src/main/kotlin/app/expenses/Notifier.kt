package app.expenses

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticeKind

/**
 * Shows the notices the check decided on. Two channels, one per kind of notice the user can switch off; the app sends nothing else
 * and never sends promotions. A notice is reported as shown only if the system will actually display it, so one that was blocked
 * (permission or channel off) is offered again later instead of being lost.
 */
object Notifier {
    const val CHANNEL_BILLS = "bills"
    const val CHANNEL_BUDGETS = "budgets"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A repeating expense or a credit card bill that is due" })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_BUDGETS, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A budget that is near or over its limit" })
    }

    /** Whether the system will show our notifications at all (Android 13+ asks for permission; older versions only have the switch in settings). */
    fun canShow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    internal fun channelOf(kind: NoticeKind) = when (kind) {
        NoticeKind.BILL_DUE, NoticeKind.CARD_DUE -> CHANNEL_BILLS
        NoticeKind.BUDGET_NEAR, NoticeKind.BUDGET_OVER -> CHANNEL_BUDGETS
    }

    /** Shows each notice that can be shown and returns those that were. */
    fun show(context: Context, notices: List<Notice>): List<Notice> {
        if (notices.isEmpty() || !canShow(context)) return emptyList()
        createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return notices.filter { n ->
            val channel = channelOf(n.kind)
            if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return@filter false // the user turned this channel off in system settings
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(n.title).setContentText(n.text)
                .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER).build()
            @Suppress("MissingPermission") // checked in canShow
            NotificationManagerCompat.from(context).notify(n.key.hashCode(), notification)
            true
        }
    }
}
