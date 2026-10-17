package app.expenses

import app.expenses.core.domain.LockSettings
import app.expenses.core.domain.LockTimeout
import app.expenses.ui.LockAvailability
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared UI needs from the app lock. The host owns the lock itself (Android: [AppLock] with the biometric prompt of the
 * activity; iPhone: its own); the UI only shows [locked] and asks for the things below. Turning the lock on or off, or changing its timeout,
 * asks the person to authenticate first.
 */
interface AppLockPort {
    /** While true the screens are not drawn at all. */
    val locked: StateFlow<Boolean>
    val settings: StateFlow<LockSettings>

    /** What the last attempt said, for the screens to show. */
    val message: StateFlow<String?>

    /** Asks for authentication now if the app is locked. */
    fun requestUnlock()
    fun enable(timeout: LockTimeout)
    fun disable()
    fun setTimeout(timeout: LockTimeout)
    fun availability(): LockAvailability
    fun dismissMessage()
}
