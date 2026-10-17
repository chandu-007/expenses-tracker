package app.expenses

import app.expenses.core.domain.LockGate
import app.expenses.core.domain.LockSettings
import app.expenses.core.domain.LockTimeout
import app.expenses.ui.LockAvailability
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffectView
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

/**
 * The app lock on iPhone: the same rules as Android's (the shared [LockGate]), proved with [IosAuthentication] (Face ID, Touch ID or the device
 * passcode). It gates what is *shown*; the data is protected by iOS Data Protection either way. Leaving the app means entering the background (the
 * Face ID prompt itself only makes the app inactive, so it does not count). While the lock is on, the app switcher's snapshot is covered.
 * Time is the system's continuous clock, which keeps counting while the phone sleeps and cannot be changed in Settings.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAppLock(
    private val prompt: (title: String, onResult: (success: Boolean, message: String?, cancelled: Boolean) -> Unit) -> Unit = { title, done -> IosAuthentication.authenticate(title, done) },
    private val elapsed: () -> Long = { (clock_gettime_nsec_np(CLOCK_MONOTONIC.convert()) / 1_000_000uL).toLong() },
) : AppLockPort {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val gate = LockGate(load())

    private val _locked = MutableStateFlow(gate.isLocked)
    override val locked: StateFlow<Boolean> = _locked
    private val _settings = MutableStateFlow(gate.settings)
    override val settings: StateFlow<LockSettings> = _settings
    private val _message = MutableStateFlow<String?>(null)
    override val message: StateFlow<String?> = _message

    private var authenticating = false
    private var cover: UIView? = null

    init {
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, main) { if (!authenticating) gate.onBackgrounded(elapsed()) }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, main) { _locked.value = gate.onForegrounded(elapsed()) }
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) { removeCover(); requestUnlock() }
        center.addObserverForName(UIApplicationWillResignActiveNotification, null, main) { if (_settings.value.enabled) addCover() }
    }

    private fun load() = LockSettings(
        enabled = defaults.boolForKey(ENABLED),
        timeout = runCatching { LockTimeout.valueOf(defaults.stringForKey(TIMEOUT) ?: "") }.getOrDefault(LockTimeout.ONE_MINUTE),
    )

    private fun apply(new: LockSettings) {
        defaults.setBool(new.enabled, forKey = ENABLED)
        defaults.setObject(new.timeout.name, forKey = TIMEOUT)
        gate.configure(new)
        _settings.value = new
        _locked.value = gate.isLocked
    }

    private fun addCover() {
        if (cover != null) return
        val window = keyWindow() ?: return
        val view = UIVisualEffectView(UIBlurEffect.effectWithStyle(UIBlurEffectStyle.UIBlurEffectStyleSystemMaterial))
        view.setFrame(window.bounds)
        window.addSubview(view)
        cover = view
    }

    private fun removeCover() {
        cover?.removeFromSuperview()
        cover = null
    }

    override fun availability(): LockAvailability = IosAuthentication.availability()

    override fun requestUnlock() {
        if (!_locked.value) return
        authenticate("Unlock") { ok -> if (ok) { gate.unlocked(); _locked.value = false } }
    }

    override fun enable(timeout: LockTimeout) {
        if (availability() != LockAvailability.READY) { _message.value = NEEDS_PASSCODE; return }
        authenticate("Turn on app lock") { ok -> if (ok) apply(LockSettings(true, timeout)) }
    }

    override fun disable() = authenticate("Turn off app lock") { ok -> if (ok) apply(LockSettings(false, gate.settings.timeout)) }

    override fun setTimeout(timeout: LockTimeout) = authenticate("Change lock timing") { ok -> if (ok) apply(gate.settings.copy(timeout = timeout)) }

    override fun dismissMessage() { _message.value = null }

    private fun authenticate(title: String, done: (Boolean) -> Unit) {
        if (authenticating) return
        authenticating = true
        _message.value = null
        prompt(title) { ok, message, cancelled ->
            authenticating = false
            if (!ok && !cancelled) _message.value = message
            done(ok)
        }
    }

    private companion object {
        const val NEEDS_PASSCODE = "Set a passcode in the iPhone's Settings first. The app lock uses it (with Face ID or Touch ID when you have them)."
        const val ENABLED = "lock_enabled"
        const val TIMEOUT = "lock_timeout"
    }
}
