package app.expenses

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView
import app.expenses.core.domain.FrameStats
import app.expenses.ui.FrameProbe

/**
 * The frame-time logger (debug builds only: [BuildConfig.FRAME_LOG] is a constant false in every other build type, so [install] returns at once and R8 removes the rest). It uses
 * the platform's own `Window.addOnFrameMetricsAvailableListener` (no library, no permission) and reads each frame's TOTAL_DURATION.
 *
 * What it writes: one `Log.i("FrameLog", ...)` line per burst of frames, `<what> frames=N p50=.. p90=.. p99=.. max=.. >16.7=a >8.3=b >100=c >700=d` (milliseconds; the counts are of
 * frames over 16.7 ms (60 Hz), 8.3 ms (120 Hz), 100 ms and 700 ms (frozen)). A burst is the frames that follow one another with less than [GAP_MS] between them (a scroll, a
 * transition); it is named after the screen change that started it ("Home to Add"), or "scroll or animation on Search" when no screen changed. An activity started with the boolean
 * extra `frame_overlay` also shows the last line in a small box at the top of the window.
 *
 * Tests and scripts that want the frames of one interaction use [begin] and [end]: between them every frame goes to that window only.
 *
 * It is a tool for comparing one build with another on the same device (an emulator draws in software): see docs/PERFORMANCE.md for how its numbers were taken.
 */
object FrameLog {
    private const val TAG = "FrameLog"
    /** Frames closer together than this belong to the same burst. */
    private const val GAP_MS = 250L
    /** A screen change names the burst that starts within this long after it. */
    private const val NAME_MS = 400L

    private val lock = Any()
    private var handler: Handler? = null
    private var overlay: TextView? = null
    private var burst: FrameStats? = null
    private var burstLabel = ""
    private var lastFrameAt = 0L
    private var markLabel: String? = null
    private var markAt = 0L
    private var screenName = "Home"
    private var manual: FrameStats? = null
    private var manualLabel = ""
    private val finish = Runnable { synchronized(lock) { closeBurst() } }

    /** Starts logging for [activity]'s window. Does nothing in a build without the logger. */
    fun install(activity: Activity, showOverlay: Boolean = false) {
        if (!BuildConfig.FRAME_LOG) return
        val h = synchronized(lock) {
            handler ?: Handler(HandlerThread("FrameLog").also { it.start() }.looper).also { handler = it }
        }
        FrameProbe.onScreenChange = { from, to ->
            synchronized(lock) { markLabel = "${FrameProbe.nameOf(from)} to ${FrameProbe.nameOf(to)}"; markAt = now(); screenName = FrameProbe.nameOf(to) }
        }
        if (showOverlay) {
            val box = TextView(activity).apply { textSize = 10f; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xAA000000.toInt()); setPadding(8, 2, 8, 2); text = "FrameLog" }
            (activity.window.decorView as ViewGroup).addView(box, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
            overlay = box
        }
        activity.window.addOnFrameMetricsAvailableListener(
            Window.OnFrameMetricsAvailableListener { _, metrics, _ -> frame(metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0) },
            h,
        )
    }

    /** Starts one measuring window named [label]: until [end], every frame goes to it and to no burst. */
    fun begin(label: String) {
        synchronized(lock) { closeBurst(); manual = FrameStats(); manualLabel = label }
    }

    /** Ends the window [begin] started and returns its frames (none in a build without the logger). */
    fun end(): FrameStats = synchronized(lock) {
        val s = manual ?: FrameStats()
        manual = null
        Log.i(TAG, s.line(manualLabel))
        s
    }

    private fun now() = android.os.SystemClock.uptimeMillis()

    private fun frame(ms: Double) {
        synchronized(lock) {
            manual?.let { it.add(ms); return }
            val t = now()
            if (burst != null && t - lastFrameAt > GAP_MS) closeBurst()
            val b = burst ?: FrameStats().also {
                burst = it
                burstLabel = markLabel?.takeIf { _ -> t - markAt <= NAME_MS } ?: "scroll or animation on $screenName"
            }
            b.add(ms)
            lastFrameAt = t
            handler?.removeCallbacks(finish)
            handler?.postDelayed(finish, GAP_MS + 50)
        }
    }

    private fun closeBurst() {
        val b = burst ?: return
        burst = null
        val line = b.line(burstLabel)
        Log.i(TAG, line)
        overlay?.let { v -> v.post { v.text = line.replace(" frames=", "\nframes=") } }
    }
}
