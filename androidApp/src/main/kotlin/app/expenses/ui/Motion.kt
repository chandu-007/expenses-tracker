package app.expenses.ui

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * The app's one motion language. Three speeds and two curves, so every screen moves the same way: a tap is acknowledged in [QUICK], things appear and
 * disappear in [STANDARD], and screens and big shapes travel in [EMPHASIZED]. Movement arriving eases out (fast, then settling); movement leaving eases in.
 * Everything here is skipped when [LocalReduceMotion] is true: the app works the same, instantly.
 */
object Motion {
    /** How much longer than the base durations everything takes: 1 is brisk, larger is calmer. Set from the person's choice under Appearance. */
    @Volatile var scale = 1.8f
    val QUICK get() = (120 * scale).toInt()
    val STANDARD get() = (220 * scale).toInt()
    val EMPHASIZED get() = (320 * scale).toInt()
    /** A sheet or page gliding the whole way across the screen: long enough to be followed, with a long soft landing. */
    val GLIDE get() = (460 * scale).toInt()

    /** The scale for a speed name: "quick" 1, "relaxed" 1.8, "slow" 2.8 (and "off" is handled by Reduce motion). */
    fun scaleOf(speed: String): Float = when (speed) { "quick" -> 1f; "slow" -> 2.8f; else -> 1.8f }

    /** A spring that takes [scale] times as long (stiffness falls with the square of the time). */
    fun <T> spring(dampingRatio: Float = 0.8f, stiffness: Float = 400f): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingRatio, stiffness / (scale * scale))

    /** For things arriving: quick start, long settle. */
    val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** For things leaving: a slow start, then away. */
    val EaseIn = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** A tween of [ms] that becomes instant under Reduce motion. */
    fun <T> tween(reduce: Boolean, ms: Int = STANDARD, easing: androidx.compose.animation.core.Easing = EaseOut): FiniteAnimationSpec<T> =
        if (reduce) tween(0) else tween(ms, easing = easing)
}

/** True when the person asked for less motion (the switch under Appearance) or the phone's animations are off (Developer options, or Remove animations). */
val LocalReduceMotion = compositionLocalOf { false }

/** Whether the phone itself has animations turned off. */
@Composable
fun systemAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/** The few moments the app answers with a touch. Each is a different feel, so a person learns what happened without looking. */
enum class Feel { SAVE, UNDO, LIMIT, PAID, TICK }

/** A function that performs a [Feel]: a crisp confirm on saving, a soft reject on Undo, a firmer one when a budget limit is crossed, a double tap for paid. */
@Composable
fun rememberFeel(): (Feel) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { feel ->
            val constant = when (feel) {
                Feel.SAVE, Feel.PAID -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                Feel.UNDO -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.KEYBOARD_TAP
                Feel.LIMIT -> HapticFeedbackConstants.LONG_PRESS
                Feel.TICK -> HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(constant)
        }
    }
}

// ---- shared-element ("container transform") transitions ----

/** Provided by the screen host: the scope a shared element needs, and the one of the screen being shown. Null outside the host (tests, previews). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this as one end of a container transform under [key]: the same key on a list row and on the screen that opens from it makes the row grow into
 * the screen, and shrink back on return. Outside a screen host it does nothing, so a screen can always use it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedContainer(key: Any): Modifier = composed {
    val shared = LocalSharedTransitionScope.current
    val visibility = LocalAnimatedVisibilityScope.current
    val reduce = LocalReduceMotion.current
    if (shared == null || visibility == null || reduce) this
    else with(shared) {
        this@composed.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = BoundsTransform { _, _ -> tween(Motion.EMPHASIZED, easing = Motion.EaseOut) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
