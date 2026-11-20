package app.expenses.core.domain

/**
 * UI S7 (U-21, owner decision D15): the app simplifies itself on a weak phone. "Simplified" means Quick motion instead of Relaxed, no blurred glow behind the month card, no draw-in of the 14-day line, no
 * spring on the account stack and no rolling digits (a change is simply there). It is switched on by itself for a phone the system calls low-RAM, or (on a phone that is not an emulator) after the frame monitor has
 * seen [FrameHealth] sustained slow frames; the person can force it on or off under Appearance, which wins over both. Nothing here is a setting of Reduce motion: that is the person's own and stricter.
 */
object Simplify {
    const val SETTING = "simplify_mode"

    enum class Mode(val key: String) { AUTO("auto"), ON("on"), OFF("off") }

    fun mode(setting: String?): Mode = Mode.entries.firstOrNull { it.key == setting } ?: Mode.AUTO

    /** Whether the app is simplified now. */
    fun active(mode: Mode, lowRamDevice: Boolean, slowFrames: Boolean): Boolean = when (mode) {
        Mode.ON -> true
        Mode.OFF -> false
        Mode.AUTO -> lowRamDevice || slowFrames
    }

    /** The line under the choice: what is happening and why, in words ("Simplified for this phone"). */
    fun line(mode: Mode, lowRamDevice: Boolean, slowFrames: Boolean): String = when {
        mode == Mode.ON -> "Simplified: you switched it on."
        mode == Mode.OFF -> "Full motion: you switched simplifying off."
        lowRamDevice -> "Simplified for this phone: it has little memory, so movement is quicker and lighter."
        slowFrames -> "Simplified for this phone: it was drawing slowly, so movement is quicker and lighter."
        else -> "Automatic: the app simplifies itself only if this phone needs it."
    }
}

/**
 * Judges whether a phone is drawing too slowly, from the time each frame took (milliseconds), in windows of [windowFrames] frames. A window is slow when at least [slowShare] of its frames took longer than
 * [slowMs]; after [windowsNeeded] slow windows in a row the phone counts as slow, and stays so for this run of the app. The first [warmupFrames] frames are not counted (the app starting is slow on any phone).
 * Frames over [frozenMs] are left out as well: a frozen frame is the app being paused or a dialog from the system, not drawing speed. Pure and host-tested; the Android frame monitor feeds it.
 */
class FrameHealth(
    private val windowFrames: Int = 90,
    private val slowMs: Double = 50.0,
    private val slowShare: Double = 0.30,
    private val windowsNeeded: Int = 3,
    private val warmupFrames: Int = 120,
    private val frozenMs: Double = 700.0,
) {
    private var seen = 0
    private var inWindow = 0
    private var slowInWindow = 0
    private var slowWindowsInARow = 0

    /** True once the phone has been judged slow. */
    var slow = false
        private set

    /** Adds one frame; returns true on the frame that makes the phone count as slow. */
    fun add(ms: Double): Boolean {
        if (slow) return false
        if (seen < warmupFrames) { seen++; return false }
        if (ms > frozenMs) return false
        inWindow++
        if (ms > slowMs) slowInWindow++
        if (inWindow < windowFrames) return false
        val slowWindow = slowInWindow >= slowShare * windowFrames
        inWindow = 0; slowInWindow = 0
        slowWindowsInARow = if (slowWindow) slowWindowsInARow + 1 else 0
        if (slowWindowsInARow >= windowsNeeded) { slow = true; return true }
        return false
    }
}
