package app.expenses

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpensesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        NotificationWork.schedule(this)
        AutoExportWorker.schedule(this)
    }

    /**
     * Before Android 12 a widget's text colours are decided when it is drawn, so light or dark mode changing (or the language) leaves it with the old ones until it is drawn again. While the app's
     * process is alive that is done here; otherwise the hourly check and the next time the app opens do it. Widgets drawn on Android 12 and later follow the phone by themselves.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (android.os.Build.VERSION.SDK_INT < 31) appScope.launch { runCatching { app.expenses.widget.refreshWidgets(this@ExpensesApp) } }
    }

    /** What the shared code needs from the phone. */
    val platform: AndroidPlatform by lazy { AndroidPlatform(this) }

    /** Created on first use, so nothing heavy runs before the first screen. */
    val container: AppContainer by lazy { AppContainer(platform) }

    /** Builds the screen's view model. The activity's default factory, so `ViewModelProvider(activity)[LedgerViewModel::class.java]` works. */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory { initializer { LedgerViewModel(container, platform) } }

    private var appLock: AppLock? = null

    /** The app lock for this process. Tests replace it to control the authenticator and the clock. */
    var lock: AppLock
        get() = appLock ?: AppLock(this).also { appLock = it }
        set(value) { appLock = value }

    /** For work that must outlive a screen, such as index maintenance after the activity has stopped. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
