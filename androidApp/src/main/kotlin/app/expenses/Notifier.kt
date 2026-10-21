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
    const val CHANNEL_RECAP = "recap"
    const val CHANNEL_BILLS = "bills"
    const val CHANNEL_BUDGETS = "budgets"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_BILLS, "Bill reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A repeating expense or a credit card bill that is due" })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_RECAP, "Weekly recap", NotificationManager.IMPORTANCE_LOW).apply { description = "A short summary of last week's spending" })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_BUDGETS, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "A budget that is near or over its limit" })
    }

    /** Whether the system will show our notifications at all (Android 13+ asks for permission; older versions only have the switch in settings). */
    fun canShow(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    internal fun channelOf(kind: NoticeKind) = when (kind) {
        NoticeKind.BILL_DUE, NoticeKind.CARD_DUE, NoticeKind.BILL_FOUND, NoticeKind.BILL_TRACKED, NoticeKind.CARD_STATEMENT, NoticeKind.CARD_LIMIT, NoticeKind.CARD_CLEARED -> CHANNEL_BILLS
        NoticeKind.BUDGET_NEAR, NoticeKind.BUDGET_OVER, NoticeKind.BUDGET_PACE, NoticeKind.BUDGET_WRAPUP -> CHANNEL_BUDGETS
        NoticeKind.WEEKLY_RECAP -> CHANNEL_RECAP
    }

    /**
     * The buttons a notice carries: a tracked bill can be marked paid or reminded about again in three hours without opening the app; a card notice
     * has Pay now, which opens the add form as a transfer to that card with what is left to pay.
     */
    private fun addButtons(context: Context, builder: NotificationCompat.Builder, n: Notice) {
        val ref = n.ref ?: return
        val id = n.key.hashCode()
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        when (n.kind) {
            NoticeKind.BILL_TRACKED -> {
                val paid = Intent(context, NoticeActionReceiver::class.java).setAction(NoticeActionReceiver.ACTION_MARK_PAID).putExtra(NoticeActionReceiver.EXTRA_REF, ref).putExtra(NoticeActionReceiver.EXTRA_NOTIFICATION_ID, id)
                val later = Intent(context, NoticeActionReceiver::class.java).setAction(NoticeActionReceiver.ACTION_SNOOZE).putExtra(NoticeActionReceiver.EXTRA_REF, ref)
                    .putExtra(NoticeActionReceiver.EXTRA_TITLE, n.title).putExtra(NoticeActionReceiver.EXTRA_TEXT, n.text).putExtra(NoticeActionReceiver.EXTRA_KIND, n.kind.name).putExtra(NoticeActionReceiver.EXTRA_NOTIFICATION_ID, id)
                builder.addAction(0, "Mark paid", PendingIntent.getBroadcast(context, id * 3 + 1, paid, flags))
                builder.addAction(0, "In 3 hours", PendingIntent.getBroadcast(context, id * 3 + 2, later, flags))
            }
            NoticeKind.CARD_DUE, NoticeKind.CARD_STATEMENT, NoticeKind.CARD_LIMIT -> {
                val pay = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_PAY_CARD).putExtra(MainActivity.EXTRA_CARD_ID, ref).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                builder.addAction(0, "Pay now", PendingIntent.getActivity(context, id * 3 + 3, pay, flags))
            }
            else -> {}
        }
    }

    /** Shows each notice that can be shown and returns those that were. */
    fun show(context: Context, notices: List<Notice>): List<Notice> {
        if (notices.isEmpty() || !canShow(context)) return emptyList()
        createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val openBills = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_BILLS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return notices.filter { n ->
            val channel = channelOf(n.kind)
            if (manager.getNotificationChannel(channel)?.importance == NotificationManager.IMPORTANCE_NONE) return@filter false // the user turned this channel off in system settings
            val isBill = n.kind == NoticeKind.BILL_FOUND || n.kind == NoticeKind.BILL_TRACKED
            val builder = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(n.title).setContentText(n.text)
                .setContentIntent(if (isBill) openBills else open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            // a bill's amount is not shown on a locked screen: the lock-screen version says only that there is a bill
            if (isBill) builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
                NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(if (n.kind == NoticeKind.BILL_FOUND) "A bill was found" else "A bill needs attention").setContentText("Open the app to see it").build(),
            )
            addButtons(context, builder, n)
            val notification = builder.build()
            @Suppress("MissingPermission") // checked in canShow
            NotificationManagerCompat.from(context).notify(n.key.hashCode(), notification)
            true
        }
    }
}
