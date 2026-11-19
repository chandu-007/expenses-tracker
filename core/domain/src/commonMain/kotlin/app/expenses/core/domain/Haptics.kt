package app.expenses.core.domain

/**
 * UI S7 (U-14, owner decision D14): the in-app Haptics switch and the rule that keeps a scrub from buzzing. The setting is "0" when the person switched haptics off under Appearance; anything else (no
 * setting) is on. The phone's own touch-feedback setting still applies on top of this (the app never asks the system to ignore it).
 */
object Haptics {
    const val SETTING = "haptics"

    fun isOn(setting: String?): Boolean = setting != "0"

    /** At most one scrub tick per this many milliseconds (a finger crossing bars quickly would otherwise hum). */
    const val STEP_GAP_MS = 40L
}

/** Lets one event through per [minGapMs] and drops the ones in between. Read with a monotonic clock in milliseconds. */
class HapticThrottle(private val minGapMs: Long = Haptics.STEP_GAP_MS) {
    private var last = Long.MIN_VALUE / 2

    fun allow(nowMs: Long): Boolean {
        if (nowMs - last < minGapMs) return false
        last = nowMs
        return true
    }
}
