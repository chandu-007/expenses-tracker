package app.expenses

import android.os.Process
import android.os.SystemClock
import android.util.Log

/** Logs milliseconds since process start at named points, in measurement builds only (see BuildConfig.SEEDING_ENABLED). */
object StartupTrace {
    fun mark(name: String) {
        if (BuildConfig.SEEDING_ENABLED) Log.i("Startup", "$name +${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()}ms")
    }
}
