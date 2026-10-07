package app.expenses

import android.content.Context
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import app.expenses.core.domain.LockGate
import app.expenses.core.domain.LockSettings
import app.expenses.core.domain.LockTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The app lock for this process. It gates what is *shown*: while locked, the screens are not drawn at all. It does not gate the home
 * screen widget, the Quick Settings tile or notifications, which only add an expense or show a short message, and it does not change how
 * the data is stored (the database is always encrypted with a key kept in the Android Keystore).
 *
 * Settings live in this app's private preferences (not synced, not backed up). Turning the lock on or off, or changing its timeout,
 * asks for authentication first, so it cannot be switched off by someone who picks up the phone while the app is open, and cannot be
 * switched on with no way to get back in.
 */
class AppLock(
    context: Context,
    var authenticator: Authenticator = BiometricAuthenticator(),
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    private val gate = LockGate(load())

    private val _locked = MutableStateFlow(gate.isLocked)
    val locked: StateFlow<Boolean> = _locked

    private val _settings = MutableStateFlow(gate.settings)
    val settings: StateFlow<LockSettings> = _settings

    /** What the last attempt said, for the screens to show. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /**
     * While the system prompt is up the app may be paused (older phones open a separate screen for the PIN); that must not count as
     * leaving, so [onStop] ignores it, and a second prompt must not open on top of the first, so [authenticate] ignores it too.
     */
    @Volatile private var authenticating = false

    private fun load() = LockSettings(
        enabled = prefs.getBoolean(ENABLED, false),
        timeout = runCatching { LockTimeout.valueOf(prefs.getString(TIMEOUT, null) ?: "") }.getOrDefault(LockTimeout.ONE_MINUTE),
    )

    private fun apply(new: LockSettings) {
        prefs.edit().putBoolean(ENABLED, new.enabled).putString(TIMEOUT, new.timeout.name).apply()
        gate.configure(new)
        _settings.value = new
        _locked.value = gate.isLocked
    }

    fun availability(): Authenticator.Availability = authenticator.availability(appContext)

    /** The screen went away. A rotation is not leaving. */
    fun onStop(activity: FragmentActivity) {
        if (!authenticating && !activity.isChangingConfigurations) gate.onBackgrounded(elapsed())
    }

    /** The screen is back. Asks for authentication straight away if the app is locked. */
    fun onStart(activity: FragmentActivity) {
        _locked.value = gate.onForegrounded(elapsed())
        if (_locked.value) requestUnlock(activity)
    }

    fun requestUnlock(activity: FragmentActivity) {
        if (!_locked.value) return
        authenticate(activity, "Unlock", null) { ok -> if (ok) { gate.unlocked(); _locked.value = false } }
    }

    fun enable(activity: FragmentActivity, timeout: LockTimeout) {
        if (availability() != Authenticator.Availability.READY) { _message.value = NEEDS_SCREEN_LOCK; return }
        authenticate(activity, "Turn on app lock", "Confirm it's you so you can get back in") { ok -> if (ok) apply(LockSettings(true, timeout)) }
    }

    fun disable(activity: FragmentActivity) = authenticate(activity, "Turn off app lock", null) { ok -> if (ok) apply(LockSettings(false, gate.settings.timeout)) }

    fun setTimeout(activity: FragmentActivity, timeout: LockTimeout) =
        authenticate(activity, "Change lock timing", null) { ok -> if (ok) apply(gate.settings.copy(timeout = timeout)) }

    fun dismissMessage() { _message.value = null }

    private fun authenticate(activity: FragmentActivity, title: String, subtitle: String?, done: (Boolean) -> Unit) {
        if (authenticating) return
        authenticating = true
        _message.value = null
        authenticator.authenticate(activity, title, subtitle) { result ->
            authenticating = false
            when (result) {
                Authenticator.Result.Success -> done(true)
                is Authenticator.Result.Failed -> { if (!result.cancelled) _message.value = result.message; done(false) }
            }
        }
    }

    companion object {
        const val NEEDS_SCREEN_LOCK = "Set a screen lock (PIN, pattern, password or fingerprint) in your phone's settings first. The app lock uses it."
        private const val ENABLED = "enabled"
        private const val TIMEOUT = "timeout"
    }
}
