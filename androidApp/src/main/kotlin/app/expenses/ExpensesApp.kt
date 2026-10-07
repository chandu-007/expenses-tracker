package app.expenses

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ExpensesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        NotificationWork.schedule(this)
    }

    /** Created on first use, so nothing heavy runs before the first screen. */
    val container: AppContainer by lazy { AppContainer(this) }

    /** For work that must outlive a screen, such as index maintenance after the activity has stopped. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
