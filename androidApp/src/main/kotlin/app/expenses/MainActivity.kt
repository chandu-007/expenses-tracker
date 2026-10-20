package app.expenses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.updateAll
import app.expenses.core.domain.LockSettings
import app.expenses.core.domain.LockTimeout
import app.expenses.ui.LockAvailability
import app.expenses.ui.PermissionRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val viewModel: LedgerViewModel by viewModels()

    /**
     * The screen's view model takes the shared container and platform, so it is built by the app's factory. Every other view model of this activity
     * (the system fingerprint/PIN prompt and the fragment manager each keep one) is built by the activity's usual factory: giving the app's factory
     * to them made turning on the app lock crash with "No initializer set for given class".
     */
    override val defaultViewModelProviderFactory: androidx.lifecycle.ViewModelProvider.Factory
        get() {
            val app = (application as ExpensesApp).viewModelFactory
            val usual = super.defaultViewModelProviderFactory
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
                    if (modelClass == LedgerViewModel::class.java) app.create(modelClass, extras) else usual.create(modelClass, extras)
            }
        }

    /** Counts requests to open the add screen from the launcher shortcut or the Quick Settings tile. */
    private val addRequests = MutableStateFlow(0)
    private val billsRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("onCreate: begin")
        super.onCreate(savedInstanceState)
        (application as ExpensesApp).platform.activity = this
        if (savedInstanceState == null && intent.action == ACTION_ADD_EXPENSE) addRequests.value = 1
        if (savedInstanceState == null && intent.action == ACTION_OPEN_BILLS) billsRequests.value = 1
        if (savedInstanceState == null && (intent.action == ACTION_ADD_EXPENSE || intent.action == ACTION_OPEN_BILLS)) viewModel.deferGuide()
        // Measurement builds only: `am start -n app.expenses/.MainActivity --ei seed_rows 100000`
        intent.getIntExtra("seed_rows", 0).takeIf { it > 0 && BuildConfig.SEEDING_ENABLED && savedInstanceState == null }?.let { viewModel.seed(it, full = intent.getBooleanExtra("seed_full", false)) }

        val host = ActivityRootHost()
        setContent { ExpensesRoot(viewModel, host) }
    }

    override fun onDestroy() {
        val platform = (application as ExpensesApp).platform
        if (platform.activity === this) platform.activity = null
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        (application as ExpensesApp).lock.onStart(this)
        viewModel.postAutomatic()
    }

    /** When the screen leaves the foreground the user is not waiting on anything: a good moment to tidy the search index. */
    override fun onStop() {
        super.onStop()
        (application as ExpensesApp).lock.onStop(this)
        // the widget shows this month's spending: bring it up to date when the person leaves the app
        val appContext = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { runCatching { app.expenses.widget.QuickAddWidget().updateAll(appContext) } }
        val app = application as ExpensesApp
        app.appScope.launch { runCatching { app.container.maintain() } }
    }

    private inner class ActivityRootHost : RootHost {
        private val app = application as ExpensesApp
        override val platform get() = app.platform
        override val addRequests get() = this@MainActivity.addRequests
        override val billsRequests get() = this@MainActivity.billsRequests
        // read on each use: tests replace ExpensesApp.lock
        private var port: ActivityLockPort? = null
        override val lock: AppLockPort get() = port?.takeIf { it.lock === app.lock } ?: ActivityLockPort(app.lock, this@MainActivity).also { port = it }

        override fun openAttachment(uri: String, mime: String) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { startActivity(Intent.createChooser(view, "Open with")) } catch (e: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(this@MainActivity, "No app on this phone can open that file.", android.widget.Toast.LENGTH_LONG).show() }
        }

        // On Android 15 and later the app draws under the system bars, which keep light icons unless told otherwise: white clock and battery on a pale screen.
        override fun onDarkChanged(dark: Boolean) {
            val view = window.decorView
            // posted to the view's own thread: a side effect normally runs on the main thread, but under the test harness's clock it has run on a worker, and touching the window from there throws
            view.post {
                val bars = WindowCompat.getInsetsController(window, view)
                bars.isAppearanceLightStatusBars = !dark; bars.isAppearanceLightNavigationBars = !dark
            }
        }

        // While the lock is on, the app is hidden from the recent-apps view and from screenshots.
        override fun onSecureChanged(secure: Boolean) {
            if (secure) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }

        // Lets `am start` and tracing measure cold start up to the first screen with data, not just the spinner.
        override fun onReady() = reportFullyDrawn()

        @Composable
        override fun rememberSmsPermission(onResult: (Boolean) -> Unit): SmsPermission {
            var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; onResult(ok) }
            return object : SmsPermission {
                override val granted get() = granted
                override fun request() = launcher.launch(android.Manifest.permission.RECEIVE_SMS)
            }
        }

        override fun notificationAccessGranted() = NotificationCaptureService.accessGranted(this@MainActivity)
        override fun openNotificationAccessSettings() = startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        override fun privacyRows(): List<PermissionRow> = PermissionNotes.rows(this@MainActivity)
        override val canUseInternet get() = PermissionNotes.canUseInternet(this@MainActivity)
    }

    /** The activity is `singleTop`, so a shortcut or tile tap while the app is open arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_ADD_EXPENSE) addRequests.value += 1
        if (intent.action == ACTION_OPEN_BILLS) billsRequests.value += 1
        if (intent.action == ACTION_ADD_EXPENSE || intent.action == ACTION_OPEN_BILLS) viewModel.deferGuide()
    }

    companion object {
        const val ACTION_ADD_EXPENSE = "app.expenses.action.ADD_EXPENSE"
        /** A bill notification: open the bills page. */
        const val ACTION_OPEN_BILLS = "app.expenses.action.OPEN_BILLS"
    }
}

/** The app lock as the shared UI sees it: [AppLock] bound to the activity that shows the system prompt. */
private class ActivityLockPort(val lock: AppLock, private val activity: FragmentActivity) : AppLockPort {
    override val locked: StateFlow<Boolean> get() = lock.locked
    override val settings: StateFlow<LockSettings> get() = lock.settings
    override val message: StateFlow<String?> get() = lock.message
    override fun requestUnlock() = lock.requestUnlock(activity)
    override fun enable(timeout: LockTimeout) = lock.enable(activity, timeout)
    override fun disable() = lock.disable(activity)
    override fun setTimeout(timeout: LockTimeout) = lock.setTimeout(activity, timeout)
    override fun availability(): LockAvailability = LockAvailability.valueOf(lock.availability().name)
    override fun dismissMessage() = lock.dismissMessage()
}
