@file:OptIn(ExperimentalForeignApi::class)

package app.expenses

import app.expenses.core.domain.FrameStats
import app.expenses.ui.FrameProbe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CADisplayLink
import platform.darwin.NSObject

/**
 * The frame-time logger of the iPhone, the counterpart of Android's `FrameLog`: a `CADisplayLink` whose callbacks, one per frame the display asked for, are timed against each
 * other (a callback that comes late is a frame that was late). It runs ONLY when the app was started with the launch argument `-frame-log` (Xcode scheme or `xcrun simctl launch ... -frame-log`),
 * so a normal run never creates the link. Each change of screen opens a window of [WINDOW_MS] and writes one `NSLog` line (`FrameLog: Home to Add frames=.. p50=.. p90=.. p99=.. max=.. >16.7=.. >8.3=.. >100=.. >700=.. target=8.3ms`),
 * the same fields as Android's; `begin`/`end` give one window by hand. The Kotlin framework is built in release mode only, so this cannot be left out at compile time as Android's is: it is inert without the argument.
 *
 * Nothing of this was run on a phone (docs/REAL_DEVICE_CHECKLIST.md has the steps).
 */
internal object IosFrameLog {
    private const val WINDOW_MS = 700.0
    private var link: CADisplayLink? = null
    private var stats: FrameStats? = null
    private var label = ""
    private var openedAt = 0.0
    private var lastAt = 0.0
    private var nominalMs = 0.0
    private var byHand = false

    private class Ticker(val onTick: (CADisplayLink) -> Unit) : NSObject() {
        @ObjCAction fun tick(displayLink: CADisplayLink) = onTick(displayLink)
    }

    private var ticker: Ticker? = null

    /** Starts logging when the launch arguments ask for it; does nothing otherwise. Call once, on the main thread. */
    fun installIfRequested() {
        if (link != null || !NSProcessInfo.processInfo.arguments.contains("-frame-log")) return
        val t = Ticker { tick(it) }
        ticker = t
        val l = CADisplayLink.displayLinkWithTarget(t, NSSelectorFromString("tick:"))
        l.addToRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        link = l
        FrameProbe.onScreenChange = { from, to -> open("${FrameProbe.nameOf(from)} to ${FrameProbe.nameOf(to)}", hand = false) }
        NSLog("FrameLog: on (launch argument -frame-log)")
    }

    /** One window by hand: every frame until [end] goes to it. */
    fun begin(name: String) { if (link != null) open(name, hand = true) }

    /** Ends the window [begin] opened and writes (and returns) its line. */
    fun end(): String { val line = close(); return line ?: "" }

    private fun open(name: String, hand: Boolean) {
        close()
        stats = FrameStats(); label = name; byHand = hand; openedAt = lastAt
    }

    private fun close(): String? {
        val s = stats ?: return null
        stats = null
        val line = s.line(label) + " target=${(nominalMs * 10).toLong() / 10.0}ms"
        NSLog("FrameLog: %@", line)
        return line
    }

    private fun tick(l: CADisplayLink) {
        val t = l.timestamp
        nominalMs = (l.targetTimestamp - t) * 1000.0
        val s = stats
        if (s != null && lastAt != 0.0) s.add((t - lastAt) * 1000.0)
        lastAt = t
        if (s != null && !byHand && (t - openedAt) * 1000.0 >= WINDOW_MS) close()
    }
}
