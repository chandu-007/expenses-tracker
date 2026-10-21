package app.expenses

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticeKind
import app.expenses.core.storage.BillStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** The buttons on a bill notice: "Mark paid" (marks the tracked bill paid, nothing is recorded in the ledger) and "In 3 hours" (the same notice again later). */
class NoticeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ref = intent.getStringExtra(EXTRA_REF) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_MARK_PAID -> (app as ExpensesApp).container.write { ok ->
                        val bill = ok.bills.get(ref)
                        if (bill != null && bill.status == BillStatus.TRACKED) ok.bills.markPaid(ref, null, System.currentTimeMillis())
                    }
                    ACTION_SNOOZE -> schedule(app, ref, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_TEXT).orEmpty(), intent.getStringExtra(EXTRA_KIND).orEmpty())
                    else -> return@launch
                }
                NotificationManagerCompat.from(app).cancel(notificationId)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_MARK_PAID = "app.expenses.action.MARK_PAID"
        const val ACTION_SNOOZE = "app.expenses.action.SNOOZE"
        const val EXTRA_REF = "ref"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_KIND = "kind"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val SNOOZE_HOURS = 3L
        const val SNOOZE_TAG = "notice-snooze"

        fun schedule(context: Context, ref: String, title: String, text: String, kind: String) {
            val data = Data.Builder().putString(EXTRA_REF, ref).putString(EXTRA_TITLE, title).putString(EXTRA_TEXT, text).putString(EXTRA_KIND, kind).build()
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<SnoozedNoticeWorker>().setInitialDelay(SNOOZE_HOURS, TimeUnit.HOURS).setInputData(data).addTag(SNOOZE_TAG).build())
        }
    }
}

/** Shows a notice again after "In 3 hours". It is a repeat of one already recorded as shown, so it is not recorded again. */
class SnoozedNoticeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ref = inputData.getString(NoticeActionReceiver.EXTRA_REF) ?: return Result.success()
        val kind = runCatching { NoticeKind.valueOf(inputData.getString(NoticeActionReceiver.EXTRA_KIND).orEmpty()) }.getOrNull() ?: return Result.success()
        // a bill paid or removed in the meantime is not reminded about
        val stillOpen = (applicationContext as ExpensesApp).container.write { ok -> ok.bills.get(ref)?.status == BillStatus.TRACKED } ?: false
        if (!stillOpen) return Result.success()
        Notifier.show(applicationContext, listOf(Notice("snooze:$ref:${System.currentTimeMillis()}", kind, inputData.getString(NoticeActionReceiver.EXTRA_TITLE).orEmpty(), inputData.getString(NoticeActionReceiver.EXTRA_TEXT).orEmpty(), ref)))
        return Result.success()
    }
}
