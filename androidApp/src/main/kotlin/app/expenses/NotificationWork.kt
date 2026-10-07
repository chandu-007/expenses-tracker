package app.expenses

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Runs the notification check in the background, about once an hour: it posts repeating expenses set to post by themselves, then shows what is due. */
class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { NotificationWork.checkNow(applicationContext, (applicationContext as ExpensesApp).container) }
            .onFailure { return Result.retry() }
        return Result.success()
    }
}

object NotificationWork {
    private const val NAME = "notification-check"

    /** Schedules the hourly check once; later calls keep the existing schedule. The first run is an hour away, the app checks itself when it opens. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS).setInitialDelay(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Posts automatic items, shows the notices that are due and records those the system displayed. Returns how many were shown. */
    suspend fun checkNow(context: Context, container: AppContainer): Int {
        val now = LocalTime.now()
        val today = LocalDate.now()
        return container.write { ok ->
            val result = ok.notifications.check(
                kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth), now.hour * 60 + now.minute,
                kotlinx.datetime.TimeZone.currentSystemDefault(),
            )
            val shown = Notifier.show(context, result.notices)
            ok.notifications.markShown(shown)
            shown.size
        } ?: 0
    }
}
