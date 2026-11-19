package app.expenses.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/** True inside a screen that is leaving: it is still drawn for its exit, but must no longer answer the Back button. */
val LocalLeavingScreen = androidx.compose.runtime.compositionLocalOf { false }

/** The platform back handler (Android Back, iOS edge swipe) that stands down while its screen is animating out, so a Back press goes to the screen that is now showing. For a state inside a screen (a selection, an open panel). */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = PlatformBackHandler(enabled && !LocalLeavingScreen.current, onBack)

/**
 * The finger-following preview of the back gesture (UI S7, U-20): while the gesture runs, [progress] (0 to 1) is how far it has gone and [key] is the screen it is leaving, so [ScreenHost] can scale that screen down and
 * shift it a little, as the system does between apps. It is read only in the graphics layer, so following the finger recomposes nothing.
 */
@Stable
class BackPreview {
    var progress by mutableFloatStateOf(0f)
    /** The screen the gesture is on; [Int.MIN_VALUE] for none. */
    var key by mutableIntStateOf(Int.MIN_VALUE)
    /** True when the gesture began at the left edge: the screen is pushed to the right (to the left for the right edge). */
    var fromLeft by mutableStateOf(true)
}

val LocalBackPreview = compositionLocalOf<BackPreview?> { null }

/** The key of the screen this part of the composition belongs to (set by [ScreenHost] around each screen); [Int.MIN_VALUE] outside one. */
val LocalScreenKey = compositionLocalOf { Int.MIN_VALUE }

/**
 * The back handler of a whole SCREEN (Back goes to the screen below). Like [BackHandler], and with the finger-following preview where the platform reports it (Android 14 and later through the system's predictive back;
 * the iPhone's edge swipe): the screen shrinks and shifts as the finger moves, springs back if the person lets go, and leaves from where it is when they go back. Under Reduce motion there is no preview, only the Back.
 * The Back button, and a Back from the three-button bar, always work as before: they are a gesture that is all at once.
 */
@Composable
fun ScreenBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val preview = LocalBackPreview.current
    val key = LocalScreenKey.current
    val on = enabled && !LocalLeavingScreen.current
    if (preview == null || LocalReduceMotion.current) { PlatformBackHandler(on, onBack); return }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    PlatformPredictiveBackHandler(
        enabled = on,
        onProgress = { p, fromLeft -> preview.key = key; preview.fromLeft = fromLeft; preview.progress = p },
        onCancel = { scope.launch { androidx.compose.animation.core.animate(preview.progress, 0f, animationSpec = tween(Motion.FEEDBACK * 2)) { v, _ -> preview.progress = v } } },
        onBack = onBack,
    )
}

/** The screen's own preview while the back gesture is on it: a little smaller, a little aside, a little fainter. Drawn in the graphics layer. */
private fun Modifier.backPreview(preview: BackPreview?, key: Int): Modifier = if (preview == null) this else graphicsLayer {
    if (preview.key == key) {
        val p = preview.progress
        if (p > 0f) {
            val s = 1f - 0.08f * p
            scaleX = s; scaleY = s
            translationX = (if (preview.fromLeft) 1f else -1f) * 24.dp.toPx() * p
            alpha = 1f - 0.12f * p
        }
    }
}

private val TABS = setOf(ScreenKeys.HOME, ScreenKeys.SEARCH, 16)

/** The keys of the screens the host treats specially; every other screen is an ordinary push. */
object ScreenKeys {
    const val HOME = -1
    /** Search: on a two-pane window the results are in the list pane and the entry opened from them beside it, as on Home. */
    const val SEARCH = 14
    /** The edit form: it grows out of the row that was tapped. */
    const val EDIT_FORM = 1
    /** The add form: it rises over home like a sheet. */
    const val ADD_FORM = 21
}

/**
 * Shows the one screen for [screenKey] and moves between screens with the app's motion language (see [Motion]):
 * - opening a screen: it comes forward (a short scale and fade) over the one it covers;
 * - the add form: rises from the bottom over a home that settles back;
 * - the edit form: the tapped row grows into it (a container transform, through [sharedContainer]), and shrinks back on return;
 * - going back to home: the screen leaving shrinks away and home returns.
 * Under Reduce motion every change is a short cross-fade (nothing travels). The durations are the tiers of [Motion]: a tab change at most 250 ms, a screen pushed at most 400 ms, the add form at most 380 ms
 * (the keyboard rises with it: the amount takes focus at once), whatever the speed chosen under Appearance. While a screen is leaving it is still drawn but hidden from accessibility and the tests, so there is never
 * a second copy of a screen's controls to find.
 *
 * [content] is given the key it should draw: the leaving screen draws its own key, not the new one.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScreenHost(screenKey: Int, content: @Composable (key: Int) -> Unit) {
    val reduce = LocalReduceMotion.current
    // the debug-only frame logger starts a measuring window at each change of screen (FrameProbe.onScreenChange is null in every build without one)
    val previousKey = androidx.compose.runtime.remember { intArrayOf(screenKey) }
    androidx.compose.runtime.LaunchedEffect(screenKey) {
        if (previousKey[0] != screenKey) { FrameProbe.onScreenChange?.invoke(previousKey[0], screenKey); previousKey[0] = screenKey }
    }
    val preview = remember { BackPreview() }
    CompositionLocalProvider(LocalBackPreview provides preview) {
    SharedTransitionLayout {
        AnimatedContent(
            targetState = screenKey,
            modifier = Modifier.fillMaxSize(),
            label = "screen",
            transitionSpec = {
                val from = initialState; val to = targetState
                if (reduce) {
                    fadeIn(tween(Motion.REDUCED_MS)) togetherWith fadeOut(tween(Motion.REDUCED_MS))
                } else when {
                    from in TABS && to in TABS -> fadeIn(tween(Motion.TAB)) togetherWith fadeOut(tween(Motion.TAB)) // the bar stays put while the page changes
                    to == ScreenKeys.ADD_FORM ->
                        (slideInVertically(tween(Motion.ADD_FORM, easing = Motion.EaseOut)) { it } + fadeIn(tween(Motion.TAB))) togetherWith
                            (scaleOut(tween(Motion.ADD_FORM, easing = Motion.EaseOut), targetScale = 0.94f) + fadeOut(tween(Motion.ADD_FORM)))
                    from == ScreenKeys.ADD_FORM ->
                        (scaleIn(tween(Motion.ADD_FORM, easing = Motion.EaseOut), initialScale = 0.94f) + fadeIn(tween(Motion.ADD_FORM))) togetherWith
                            (slideOutVertically(tween(Motion.ADD_FORM, easing = Motion.EaseOut)) { it } + fadeOut(tween(Motion.ADD_FORM)))
                    to == ScreenKeys.EDIT_FORM || from == ScreenKeys.EDIT_FORM ->
                        fadeIn(tween(Motion.TAB)) togetherWith fadeOut(tween(Motion.TAB)) // the shared container carries the movement
                    to == ScreenKeys.HOME ->
                        (scaleIn(tween(Motion.PUSH, easing = Motion.EaseOut), initialScale = 1.04f) + fadeIn(tween(Motion.TAB))) togetherWith
                            (scaleOut(tween(Motion.TAB, easing = Motion.EaseIn), targetScale = 0.94f) + fadeOut(tween(Motion.TAB)))
                    else ->
                        (scaleIn(tween(Motion.PUSH, easing = Motion.EaseOut), initialScale = 0.94f) + fadeIn(tween(Motion.TAB))) togetherWith
                            fadeOut(tween(Motion.CHIP))
                }.also {
                    // the screen being opened is drawn above; the screen being left, on the way back, is drawn above
                    it.targetContentZIndex = if (to == ScreenKeys.HOME && from != ScreenKeys.EDIT_FORM) -1f else 1f
                }
            },
        ) { key ->
            val leaving = transition.targetState == EnterExitState.PostExit
            // when this screen has gone (its exit has finished), the preview it was left in is cleared for the next time
            DisposableEffect(key) { onDispose { if (preview.key == key) { preview.key = Int.MIN_VALUE; preview.progress = 0f } } }
            CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout, LocalAnimatedVisibilityScope provides this, LocalLeavingScreen provides leaving, LocalScreenKey provides key) {
                Box(Modifier.fillMaxSize().backPreview(preview, key).then(if (leaving) Modifier.clearAndSetSemantics { } else Modifier)) { content(key) }
            }
        }
    }
    }
}
