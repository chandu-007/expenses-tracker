package app.expenses.core.domain

/** How long the app may stay in the background before it asks again. [IMMEDIATELY] asks every time the app is left. */
enum class LockTimeout(val millis: Long) {
    IMMEDIATELY(0), ONE_MINUTE(60_000), FIVE_MINUTES(300_000), FIFTEEN_MINUTES(900_000),
}

data class LockSettings(val enabled: Boolean = false, val timeout: LockTimeout = LockTimeout.ONE_MINUTE)

/**
 * Decides when the app is locked. It only tracks time, so it can be tested without a phone; the platform asks the user to prove who they
 * are and then calls [unlocked].
 *
 * Time comes from a monotonic clock (milliseconds since boot on Android), so changing the phone's date cannot unlock the app. The rules:
 * - A cold start is locked when the lock is on.
 * - Leaving the app starts a count; coming back locks it if the app was away for at least the timeout (so "immediately" always locks).
 * - Once locked it stays locked until [unlocked], however many times the app is left and re-entered.
 * - If the clock seems to have gone backwards the app locks: when in doubt, ask.
 * - Turning the lock off unlocks; turning it on does not lock the screen the user is using.
 */
class LockGate(settings: LockSettings, startLocked: Boolean = settings.enabled) {
    var settings: LockSettings = settings
        private set
    var isLocked: Boolean = startLocked && settings.enabled
        private set
    private var leftAt: Long? = null

    fun configure(new: LockSettings) {
        settings = new
        if (!new.enabled) { isLocked = false; leftAt = null }
    }

    /** The app left the foreground at [elapsedMillis]. The first time counts: leaving twice without coming back does not restart the clock. */
    fun onBackgrounded(elapsedMillis: Long) {
        if (leftAt == null) leftAt = elapsedMillis
    }

    /** The app came back at [elapsedMillis]. Returns whether it is now locked. */
    fun onForegrounded(elapsedMillis: Long): Boolean {
        val left = leftAt
        leftAt = null
        if (settings.enabled && left != null) {
            val away = elapsedMillis - left
            if (away < 0 || away >= settings.timeout.millis) isLocked = true
        }
        return isLocked
    }

    /** The user proved who they are. */
    fun unlocked() {
        isLocked = false
        leftAt = null
    }
}
