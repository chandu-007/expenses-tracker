package app.expenses.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlin.concurrent.Volatile

/**
 * The app's one motion language, in three tiers so that what is acted on is quick and what arrives is calm:
 *
 * - **Feedback** ([FEEDBACK], 90 ms, never scaled): a press, a tick, a colour flash. Answering a touch is not something to slow down.
 * - **Actions**, capped whatever the speed: a chip or switch [CHIP] (150 ms), a change of tab [TAB] (250 ms), a screen pushed [PUSH] (400 ms), the add form [ADD_FORM] (380 ms; it is the most used
 *   thing in the app). At Quick they are as short as the speed makes them; the cap only matters at Relaxed (the default) and above. At Slow, which the person chose to see slowly, the caps grow with it.
 * - **Arrivals**, scaled by the speed ([QUICK], [STANDARD], [EMPHASIZED], [GLIDE]): the hero figure, sheets, charts drawing, a number rolling. Relaxed (1.8x) is the default there.
 *
 * Two curves: movement arriving eases out (fast, then settling); movement leaving eases in. Under Reduce motion nothing travels, grows or springs: a change is a short cross-fade ([REDUCED_MS]).
 */
object Motion {
    /** How much longer than the base durations everything takes: 1 is brisk, larger is calmer. Set from the person's choice under Appearance. */
    @Volatile var scale = 1.8f
    val QUICK get() = (120 * scale).toInt()
    val STANDARD get() = (220 * scale).toInt()
    val EMPHASIZED get() = (320 * scale).toInt()
    /** A sheet or page gliding the whole way across the screen: long enough to be followed, with a long soft landing. */
    val GLIDE get() = (460 * scale).toInt()

    /** Feedback: a press, a tick, a colour flash. Never multiplied by [scale]. */
    const val FEEDBACK = 90
    /** What a change is under Reduce motion: a cross-fade, no travel. */
    const val REDUCED_MS = 100

    /** A tier cap ([ms] at the default speed) at [speed]: the same at Quick and Relaxed, growing with the speed only above Relaxed (Slow). */
    fun capAt(ms: Int, speed: Float): Int = (ms * maxOf(1f, speed / DEFAULT_SCALE)).toInt()
    private fun cap(ms: Int) = capAt(ms, scale)

    /** A chip or a switch changing: at most 150 ms. */
    val CHIP get() = minOf(QUICK, cap(150))
    /** A small sheet or panel coming in (Filters): at most 220 ms; going out it takes [CHIP]. */
    val SHEET_IN get() = minOf(STANDARD, cap(220))
    /** One tab replacing another: at most 250 ms. */
    val TAB get() = minOf(STANDARD, cap(250))
    /** A screen pushed over another, or a container growing into one: at most 400 ms. */
    val PUSH get() = minOf(EMPHASIZED, cap(400))
    /** The add form rising and leaving: at most 380 ms. The keyboard rises with it (the amount takes focus at once). */
    val ADD_FORM get() = minOf(GLIDE, cap(380))
    /** The sweep of a ring when a chart is drawn: an arrival, 390 ms at the base speed (700 at Relaxed, as the ring always was). */
    val CHART get() = (390 * scale).toInt()
    /** A bar of the trend chart growing to its height: 280 ms at the base speed (about 500 at Relaxed, as it always was). */
    val BAR get() = (280 * scale).toInt()
    /** The flash on a row that was just added or changed: it fades for about a second at the default speed. */
    val FLASH get() = (610 * scale).toInt()

    /** The scale for a speed name: "quick" 1, "relaxed" 1.8, "slow" 2.8 (and "off" is handled by Reduce motion). */
    fun scaleOf(speed: String): Float = when (speed) { "quick" -> 1f; "slow" -> 2.8f; else -> DEFAULT_SCALE }
    private const val DEFAULT_SCALE = 1.8f

    /** A spring that takes [scale] times as long (stiffness falls with the square of the time). */
    fun <T> spring(dampingRatio: Float = 0.8f, stiffness: Float = 400f): androidx.compose.animation.core.SpringSpec<T> =
        androidx.compose.animation.core.spring(dampingRatio, stiffness / (scale * scale))

    /** Movement that is acted on or arrives, scaled like the rest: the default spatial spring, or the fast one (it may overshoot a little: that is what a spatial spring is for). */
    fun <T> spatial(fast: Boolean = false): androidx.compose.animation.core.SpringSpec<T> =
        if (fast) androidx.compose.animation.core.spring(0.6f, 800f / (scale * scale)) else androidx.compose.animation.core.spring(0.8f, 380f / (scale * scale))

    /** Colour and transparency: a spring that never overshoots and is never scaled (an alpha beyond 1 or a colour past its target would look like a glitch). */
    fun <T> effects(): androidx.compose.animation.core.SpringSpec<T> = androidx.compose.animation.core.spring(1f, 1600f)

    /** The little spring a pressed button or tile shrinks and returns with: feedback, so never scaled (and it bounces: a press is felt). */
    fun <T> press(): androidx.compose.animation.core.SpringSpec<T> = androidx.compose.animation.core.spring(0.6f, 600f)

    /** For things arriving: quick start, long settle. */
    val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** For things leaving: a slow start, then away. */
    val EaseIn = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** A tween of [ms], or under Reduce motion the short cross-fade [REDUCED_MS] (the change is still seen, nothing travels). */
    fun <T> tween(reduce: Boolean, ms: Int = STANDARD, easing: androidx.compose.animation.core.Easing = EaseOut): FiniteAnimationSpec<T> =
        if (reduce) tween(REDUCED_MS) else tween(ms, easing = easing)
}

/** True when the person asked for less motion (the switch under Appearance) or the phone's animations are off (Developer options, or Remove animations). */
val LocalReduceMotion = compositionLocalOf { false }

/** The few moments the app answers with a touch. Each is a different feel, so a person learns what happened without looking. */
enum class Feel { SAVE, UNDO, LIMIT, PAID, TICK, SELECT }

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
            boundsTransform = BoundsTransform { _, _ -> tween(Motion.PUSH, easing = Motion.EaseOut) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
