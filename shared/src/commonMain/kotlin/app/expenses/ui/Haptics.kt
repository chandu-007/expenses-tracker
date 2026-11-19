package app.expenses.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import app.expenses.core.domain.HapticThrottle
import kotlin.time.TimeSource

/** Whether haptics are on (UI S7: the Haptics switch under Appearance; on by default). Provided once by the app root from the stored setting. */
val LocalHapticsEnabled = compositionLocalOf { true }

private val clockStart = TimeSource.Monotonic.markNow()

/**
 * A function that performs a [Feel]. It does nothing while the person has switched haptics off ([LocalHapticsEnabled]), and lets a [Feel.STEP] through at most once per 40 ms. The patterns themselves are the
 * platform's ([rememberPlatformFeel]); the phone's own touch-feedback setting applies on top, since the app never asks the system to ignore it.
 */
@Composable
fun rememberFeel(): (Feel) -> Unit {
    val on = LocalHapticsEnabled.current
    val perform = rememberPlatformFeel()
    return remember(on, perform) {
        val step = HapticThrottle()
        val gate: (Feel) -> Unit = { feel -> if (on && (feel != Feel.STEP || step.allow(clockStart.elapsedNow().inWholeMilliseconds))) perform(feel) }
        gate
    }
}
